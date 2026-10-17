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

/**
 * Point-lens bending of the finished scene around the hole: Einstein ring, inverted inner image,
 * frame dragging, gravitational-wave pulses and a slight chromatic fringe. Depth-aware: only pixels
 * beyond the hole centre are bent, so bodies in front of it stay intact. Owned GL objects are private.
 */
@SideOnly(Side.CLIENT)
final class SpacetimeLens {
    private static final String VERTEX = "#version 120\n"
            + "varying vec2 uv; void main() { uv = gl_MultiTexCoord0.xy; gl_Position = gl_Vertex; }";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform sampler2D scene; uniform sampler2D depth;\n"
            + "uniform vec4 projection; uniform vec2 depthTerms; uniform vec3 hole;\n"
            + "uniform vec4 lens; uniform vec4 extra; varying vec2 uv;\n"
            + "vec3 ray(vec2 p) { vec2 ndc = p * 2.0 - 1.0;\n"
            + " return normalize(vec3((ndc.x + projection.z) / projection.x, (ndc.y + projection.w) / projection.y, -1.0)); }\n"
            + "vec2 project(vec3 dir) { vec2 t = dir.xy / -dir.z;\n"
            + " return vec2(t.x * projection.x - projection.z, t.y * projection.y - projection.w) * 0.5 + 0.5; }\n"
            // Sky has no depth; otherwise compare linear view depth with the hole centre.
            + "float behind(vec2 p) { float d = texture2D(depth, p).r; if (d >= 0.999999) return 1.0;\n"
            + " return step(extra.x, depthTerms.y / (d * 2.0 - 1.0 + depthTerms.x)); }\n"
            // Off-screen or foreground sources are unknown behind the hole: keep the unbent pixel.
            + "vec3 fetch(vec3 u, float beta, vec3 base) {\n"
            + " vec3 dir = hole * cos(beta) + u * sin(beta);\n"
            + " if (dir.z > -0.02) return base;\n"
            + " vec2 p = project(dir);\n"
            + " float valid = smoothstep(0.0, 0.02, min(min(p.x, p.y), min(1.0 - p.x, 1.0 - p.y)));\n"
            + " if (valid <= 0.0) return base;\n"
            + " return mix(base, texture2D(scene, p).rgb, valid * behind(p)); }\n"
            + "void main() {\n"
            + " vec3 base = texture2D(scene, uv).rgb;\n"
            + " float weight = lens.w * behind(uv);\n"
            + " if (weight <= 0.001) { gl_FragColor = vec4(base, 0.0); return; }\n"
            + " vec3 v = ray(uv); float c = clamp(dot(v, hole), -1.0, 1.0);\n"
            + " float th = max(acos(c), 0.0001), shadow = lens.x, einstein = lens.y;\n"
            // Deflection itself fades out before the side of the sky facing away from the hole.
            + " float reach = 1.0 - smoothstep(min(einstein * 2.5, 1.1), min(einstein * 4.5, 1.6), th);\n"
            + " vec3 perp = v - hole * c; float pl = length(perp);\n"
            + " vec3 u = pl > 0.000001 ? perp / pl : vec3(1.0, 0.0, 0.0);\n"
            + " float twist = reach * lens.z * shadow * shadow / (th * th + shadow * shadow * 0.25);\n"
            + " u = u * cos(twist) + cross(hole, u) * sin(twist);\n"
            + " float bend = reach * einstein * einstein / th;\n"
            + " float wave = th - extra.w;\n"
            + " float beta = clamp(th - bend + extra.z * sin(wave * 45.0) * exp(-wave * wave * 196.0), -3.0, 3.0);\n"
            + " float fringe = 0.025 * bend;\n"
            + " vec3 color = vec3(fetch(u, beta + fringe, base).r, fetch(u, beta, base).g, fetch(u, beta - fringe, base).b);\n"
            + " color *= 1.0 - (1.0 - smoothstep(shadow * 0.94, shadow * 1.06, th)) * extra.y;\n"
            + " gl_FragColor = vec4(mix(base, color, weight), 0.0); }";
    private static final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private static final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
    private static int program, colorTexture, depthTexture, width, height;
    private static int projectionUniform, depthTermsUniform, holeUniform, lensUniform, extraUniform;
    private static long completedPasses;
    private static boolean failed;

    private SpacetimeLens() { }

    static boolean isReady() { return !failed && program != 0 && colorTexture != 0 && depthTexture != 0; }

    /** Number of complete lens submissions. Does not consume GL errors. */
    static long getCompletedPassCount() { return completedPasses; }

    /**
     * Call on the render thread after the world and sky, before the hole geometry, with the main
     * framebuffer bound. projection is the live column-major matrix; hole is a unit eye-space direction.
     * Angles are radians; frontDepth is the hole centre's linear view depth.
     */
    static void render(float[] projection, double[] hole, double shadow, double einstein, double swirl, double strength,
                       double frontDepth, double darkness, double rippleAmplitude, double rippleAngle) {
        if (failed || strength <= 0.001 || einstein <= 0 || !OpenGlHelper.isFramebufferEnabled()) return;
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL20 || !caps.OpenGL14 || !(caps.OpenGL30 || caps.GL_ARB_framebuffer_object)) return;
        Framebuffer scene = Minecraft.getMinecraft().getFramebuffer();
        if (scene == null || !scene.useDepth || scene.framebufferObject <= 0
                || scene.framebufferWidth <= 0 || scene.framebufferHeight <= 0) return;
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
            int w = scene.framebufferWidth, h = scene.framebufferHeight;
            if (!prepare(w, h, caps)) return;
            // Snapshot colour and depth: the pass reads the scene while overwriting it.
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, draw);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            for (int plane = 0; plane < 6; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glViewport(0, 0, w, h);
            // Respect each anaglyph eye's RGB mask; never write scene alpha.
            GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, false);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL20.glUseProgram(program);
            GL20.glUniform4f(projectionUniform, projection[0], projection[5], projection[8], projection[9]);
            GL20.glUniform2f(depthTermsUniform, projection[10], projection[14]);
            GL20.glUniform3f(holeUniform, (float) hole[0], (float) hole[1], (float) hole[2]);
            GL20.glUniform4f(lensUniform, (float) shadow, (float) einstein, (float) swirl, (float) Math.min(1, strength));
            GL20.glUniform4f(extraUniform, (float) frontDepth, (float) darkness, (float) rippleAmplitude, (float) rippleAngle);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
            GL11.glEnd();
            completedPasses++;
        } finally {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, read);
            GL20.glUseProgram(oldProgram);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    /** Compiles and allocates ahead of the first lens pass, restoring the program and texture state. */
    static void warmUp() {
        if (failed || program != 0 || !OpenGlHelper.isFramebufferEnabled()) return;
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL20 || !caps.OpenGL14 || !(caps.OpenGL30 || caps.GL_ARB_framebuffer_object)) return;
        Framebuffer scene = Minecraft.getMinecraft().getFramebuffer();
        if (scene == null || scene.framebufferWidth <= 0 || scene.framebufferHeight <= 0) return;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            prepare(scene.framebufferWidth, scene.framebufferHeight, caps);
        } finally {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static boolean prepare(int w, int h, ContextCapabilities caps) {
        if (program != 0 && width == w && height == h && colorTexture != 0) return true;
        boolean pbo = caps.OpenGL21 || caps.GL_ARB_pixel_buffer_object;
        int unpack = pbo ? GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) : 0;
        try {
            if (pbo) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (program == 0) {
                program = createProgram();
                if (program == 0) { disable(); return false; }
                projectionUniform = GL20.glGetUniformLocation(program, "projection");
                depthTermsUniform = GL20.glGetUniformLocation(program, "depthTerms");
                holeUniform = GL20.glGetUniformLocation(program, "hole");
                lensUniform = GL20.glGetUniformLocation(program, "lens");
                extraUniform = GL20.glGetUniformLocation(program, "extra");
                GL20.glUseProgram(program);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "scene"), 0);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "depth"), 1);
            }
            deleteTextures();
            width = w; height = h;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            colorTexture = texture(GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            depthTexture = texture(GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, w, h, 0, GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            return true;
        } catch (RuntimeException unsupported) {
            disable();
            return false;
        } finally {
            if (pbo) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        }
    }

    private static int texture(int filter) {
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return name;
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
        deleteTextures();
        if (program != 0) { GL20.glUseProgram(0); GL20.glDeleteProgram(program); program = 0; }
    }

    private static void deleteTextures() {
        if (colorTexture != 0) GL11.glDeleteTextures(colorTexture);
        if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
        colorTexture = depthTexture = 0;
    }
}
