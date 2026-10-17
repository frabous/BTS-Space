package fr.frabous.stellar.client;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GLContext;

/** Selective glow; all owned GL objects are private and all state changes bypass Minecraft's caches. */
@SideOnly(Side.CLIENT)
final class EmissionBloom {
    private static final String VERTEX = "#version 120\n"
            + "varying vec2 uv; void main() { uv = gl_MultiTexCoord0.xy; gl_Position = gl_Vertex; }";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform sampler2D source; uniform vec2 stepSize; uniform float gain; varying vec2 uv;\n"
            + "uniform bool downsample; uniform vec2 sourceSize;\n"
            + "void main() {\n"
            + " if (downsample) {\n"
            + "  vec2 first = floor(gl_FragCoord.xy) * 4.0; vec3 c = vec3(0.0); float count = 0.0;\n"
            + "  for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {\n"
            + "   vec2 p = first + vec2(float(x), float(y));\n"
            + "   if (p.x < sourceSize.x && p.y < sourceSize.y) {\n"
            + "    c += texture2D(source, (p + vec2(0.5)) / sourceSize).rgb; count += 1.0; } }\n"
            + "  gl_FragColor = vec4(c / count, 0.0); return; }\n"
            + " vec3 c = texture2D(source, uv).rgb * 0.227027027;\n"
            + " c += (texture2D(source, uv + stepSize * 1.384615385).rgb"
            + " + texture2D(source, uv - stepSize * 1.384615385).rgb) * 0.316216216;\n"
            + " c += (texture2D(source, uv + stepSize * 3.230769231).rgb"
            + " + texture2D(source, uv - stepSize * 3.230769231).rgb) * 0.070270270;\n"
            + " gl_FragColor = vec4(c * gain, 0.0); }";
    private static final int[] buffers = new int[3];
    private static final int[] textures = new int[3];
    private static final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private static final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
    private static int width, height, blurWidth, blurHeight, program, stepUniform, gainUniform;
    private static int downsampleUniform, sourceSizeUniform;
    private static long completedPasses;
    private static boolean failed;

    private EmissionBloom() { }

    /** Resource readiness only; smoke tests must also check pass count and GL errors on the render thread. */
    static boolean isReady() {
        return !failed && program != 0 && buffers[0] != 0 && buffers[1] != 0 && buffers[2] != 0;
    }

    /** Number of complete bloom submissions, not individual fullscreen draws. Does not consume GL errors. */
    static long getCompletedPassCount() { return completedPasses; }

    /**
     * Call on the render thread after normal geometry, before HUD, with the main framebuffer bound
     * and the scene matrices active. The callback draws ONLY emissive geometry in those coordinates.
     * It must use raw GL (POSITION_COLOR Tessellator is supported), leave our blend/depth masks in
     * place, and must not clear, switch framebuffers, or change cached GlStateManager render settings.
     * Unsupported setups skip the callback; normal geometry must already have been drawn separately.
     */
    static void render(Runnable emitters) {
        if (failed || !OpenGlHelper.isFramebufferEnabled()) return;
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL20 || !(caps.OpenGL30 || caps.GL_ARB_framebuffer_object)) return;
        Framebuffer scene = Minecraft.getMinecraft().getFramebuffer();
        if (scene == null || !scene.useDepth || scene.framebufferObject <= 0
                || scene.framebufferTextureWidth <= 0 || scene.framebufferTextureHeight <= 0) return;
        int draw = GL11.glGetInteger(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING);
        if (draw != scene.framebufferObject) return;
        int read = GL11.glGetInteger(ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        colorMask.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            // Query the live attachment, not depthBuffer: Forge can replace it with packed stencil.
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, draw);
            int type = ARBFramebufferObject.glGetFramebufferAttachmentParameteri(
                    ARBFramebufferObject.GL_READ_FRAMEBUFFER, ARBFramebufferObject.GL_DEPTH_ATTACHMENT,
                    ARBFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != ARBFramebufferObject.GL_RENDERBUFFER) return;
            int depth = ARBFramebufferObject.glGetFramebufferAttachmentParameteri(
                    ARBFramebufferObject.GL_READ_FRAMEBUFFER, ARBFramebufferObject.GL_DEPTH_ATTACHMENT,
                    ARBFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (depth == 0 || !prepare(scene.framebufferTextureWidth, scene.framebufferTextureHeight, caps)) return;
            bind(buffers[0]);
            ARBFramebufferObject.glFramebufferRenderbuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                    ARBFramebufferObject.GL_DEPTH_ATTACHMENT, ARBFramebufferObject.GL_RENDERBUFFER, depth);
            if (!complete()) return; // Multisampled or mismatched depth attachments cannot be shared.

            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            int units = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            for (int unit = 0; unit < units; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glDisable(GL11.GL_TEXTURE_1D); GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL12.GL_TEXTURE_3D); GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); // Never clear the borrowed depth/stencil.
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColorMask(true, true, true, false);
            emitters.run(); // RGB is already alpha-weighted against black; do not multiply again.

            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            for (int plane = 0; plane < 6; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            GL11.glColorMask(true, true, true, false);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL20.glUseProgram(program); GL20.glUniform1f(gainUniform, 1);
            bind(buffers[1]); GL11.glViewport(0, 0, blurWidth, blurHeight);
            // Average every source texel in each 4x4 tile, including partial tiles at odd-sized edges.
            GL20.glUniform1i(downsampleUniform, 1);
            GL20.glUniform2f(sourceSizeUniform, width, height); quad(textures[0]);
            GL20.glUniform1i(downsampleUniform, 0);
            bind(buffers[2]);
            GL20.glUniform2f(stepUniform, 1F / blurWidth, 0); quad(textures[1]);
            bind(buffers[1]);
            GL20.glUniform2f(stepUniform, 0, 1F / blurHeight); quad(textures[2]);

            bind(draw); // Do not change the parent's draw buffer selection.
            GL11.glViewport(0, 0, scene.framebufferWidth, scene.framebufferHeight);
            GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, false);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GL20.glUniform2f(stepUniform, 0, 0); GL20.glUniform1f(gainUniform, 1.25F);
            quad(textures[1]); // Respect each anaglyph eye's RGB mask; never write scene alpha.
            completedPasses++;
        } finally {
            // A resize may delete Minecraft's depth attachment; never retain or own its handle.
            if (buffers[0] != 0) {
                bind(buffers[0]);
                ARBFramebufferObject.glFramebufferRenderbuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                        ARBFramebufferObject.GL_DEPTH_ATTACHMENT, ARBFramebufferObject.GL_RENDERBUFFER, 0);
            }
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, read);
            bind(draw);
            GL20.glUseProgram(oldProgram);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static boolean prepare(int w, int h, ContextCapabilities caps) {
        if (program != 0 && width == w && height == h && buffers[0] != 0) return true;
        boolean pbo = caps.OpenGL21 || caps.GL_ARB_pixel_buffer_object;
        int unpack = pbo ? GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) : 0;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (pbo) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (program == 0) {
                program = createProgram();
                if (program == 0) { failed = true; return false; }
                stepUniform = GL20.glGetUniformLocation(program, "stepSize");
                gainUniform = GL20.glGetUniformLocation(program, "gain");
                downsampleUniform = GL20.glGetUniformLocation(program, "downsample");
                sourceSizeUniform = GL20.glGetUniformLocation(program, "sourceSize");
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "source"), 0);
            }
            if (width == w && height == h && buffers[0] != 0) return true;
            deleteTargets();
            width = w; height = h;
            blurWidth = Math.max(1, (w + 3) / 4); blurHeight = Math.max(1, (h + 3) / 4);
            for (int i = 0; i < buffers.length; i++) {
                textures[i] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, i == 0 ? w : blurWidth,
                        i == 0 ? h : blurHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                buffers[i] = ARBFramebufferObject.glGenFramebuffers(); bind(buffers[i]);
                ARBFramebufferObject.glFramebufferTexture2D(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER,
                        ARBFramebufferObject.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[i], 0);
                GL11.glDrawBuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0);
                if (!complete()) { disable(); return false; }
            }
            return true;
        } catch (RuntimeException unsupported) {
            disable();
            return false;
        } finally {
            if (pbo) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        }
    }

    private static int createProgram() {
        int vertex = 0, fragment = 0, linked = 0;
        boolean success = false;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            if (vertex == 0 || fragment == 0) return 0;
            linked = GL20.glCreateProgram();
            if (linked == 0) return 0;
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            success = GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) != GL11.GL_FALSE;
            return success ? linked : 0;
        } finally {
            if (success) {
                GL20.glDetachShader(linked, vertex); GL20.glDetachShader(linked, fragment);
            }
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (!success && linked != 0) GL20.glDeleteProgram(linked);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) return 0;
        boolean success = false;
        try {
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
            success = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE;
            return success ? shader : 0;
        } finally {
            if (!success) GL20.glDeleteShader(shader);
        }
    }

    private static void disable() {
        failed = true;
        deleteTargets();
        if (program != 0) { GL20.glUseProgram(0); GL20.glDeleteProgram(program); program = 0; }
    }

    private static void deleteTargets() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != 0) ARBFramebufferObject.glDeleteFramebuffers(buffers[i]);
            if (textures[i] != 0) GL11.glDeleteTextures(textures[i]);
            buffers[i] = 0; textures[i] = 0;
        }
    }

    private static void bind(int framebuffer) {
        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, framebuffer);
    }

    private static boolean complete() {
        return ARBFramebufferObject.glCheckFramebufferStatus(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER)
                == ARBFramebufferObject.GL_FRAMEBUFFER_COMPLETE;
    }

    private static void quad(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }
}
