package fr.frabous.stellar.client;

import java.nio.FloatBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Non-linear spaghettification of one living model. Only the vertex stage is replaced: texturing,
 * lightmap, overlays, alpha test, blending and fog stay fixed-function. Mirrors BlackholeVisualMath.
 */
@SideOnly(Side.CLIENT)
final class TidalShader {
    private static final String VERTEX = "#version 120\n"
            + "uniform vec3 anchor; uniform vec3 axis; uniform vec3 drift; uniform mat3 align;\n"
            + "uniform vec4 shape; uniform vec3 thread;\n"
            + "void main() {\n"
            + " vec4 eye = gl_ModelViewMatrix * gl_Vertex;\n"
            + " vec3 d = align * (eye.xyz / eye.w - anchor);\n"
            + " float s = dot(d, axis) / shape.x;\n"
            + " float c = clamp(s, -1.0, " + BlackholeVisualMath.SPAGHETTI_LEAD + ");\n"
            + " float u = (c + 1.0) * 0.5;\n"
            + " float j = 1.0 + shape.y * (0.3 + 0.7 * u * u);\n"
            + " float f = c + shape.y * (0.3 * c + 0.7 / 12.0 * ((c + 1.0) * (c + 1.0) * (c + 1.0) - 1.0)) + j * (s - c);\n"
            + " float w = max(thread.x, inversesqrt(j));\n"
            + " vec3 across = d - axis * dot(d, axis);\n"
            + " vec3 p = anchor + axis * (f * shape.x) + across * w + drift * (shape.z * f * abs(f))\n"
            + "  + cross(axis, drift) * (thread.y * sin(f * 2.4 - shape.w * 0.45) * min(1.0, abs(f) * 0.5));\n"
            + " gl_Position = gl_ProjectionMatrix * vec4(p, 1.0);\n"
            + " gl_FogFragCoord = length(p);\n"
            + " gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;\n"
            + " gl_TexCoord[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;\n"
            + " vec4 color = gl_Color;\n"
            + " vec3 n = align * (gl_NormalMatrix * gl_Normal);\n"
            + " if (thread.z > 0.5 && dot(n, n) > 1e-8) {\n"
            // Inverse-transpose of the local stretch keeps shading on the thinned, elongated surface.
            + "  float na = dot(n, axis);\n"
            + "  n = normalize(axis * (na / j) + (n - axis * na) / w);\n"
            + "  vec3 light = gl_LightModel.ambient.rgb + gl_LightSource[0].ambient.rgb + gl_LightSource[1].ambient.rgb\n"
            + "   + gl_LightSource[0].diffuse.rgb * max(dot(n, normalize(gl_LightSource[0].position.xyz)), 0.0)\n"
            + "   + gl_LightSource[1].diffuse.rgb * max(dot(n, normalize(gl_LightSource[1].position.xyz)), 0.0);\n"
            + "  color.rgb = min(gl_FrontMaterial.emission.rgb + color.rgb * light, vec3(1.0)); }\n"
            + " gl_FrontColor = color; gl_BackColor = color; }";
    private static final FloatBuffer rotation = BufferUtils.createFloatBuffer(9);
    private static int program, anchor, axis, drift, align, shape, thread;
    private static long binds;
    private static boolean failed;

    private TidalShader() { }

    static boolean isReady() { return !failed && program != 0; }

    /** Number of model scopes drawn through the vertex stage, for smoke evidence. */
    static long getBindCount() { return binds; }

    static boolean isOwnProgram(int name) { return name != 0 && name == program; }

    /**
     * All vectors are eye-space; alignment is a column-major 3x3 rotation about the anchor. Returns the
     * program to restore after the model, or -1 when another program (shader pack, nested scope) owns
     * the pipeline or GLSL is unavailable: the caller then keeps its fixed-function fallback.
     */
    static int bind(double[] anchorEye, double[] axisEye, double[] driftEye, double[] alignment, double size,
                    double amount, double bend, double floor, double wobble, double clock) {
        if (failed || !GLContext.getCapabilities().OpenGL20) return -1;
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (previous != 0) return -1;
        if (program == 0 && !create()) return -1;
        GL20.glUseProgram(program);
        GL20.glUniform3f(anchor, (float) anchorEye[0], (float) anchorEye[1], (float) anchorEye[2]);
        GL20.glUniform3f(axis, (float) axisEye[0], (float) axisEye[1], (float) axisEye[2]);
        GL20.glUniform3f(drift, (float) driftEye[0], (float) driftEye[1], (float) driftEye[2]);
        rotation.clear();
        for (double value : alignment) rotation.put((float) value);
        rotation.flip();
        GL20.glUniformMatrix3(align, false, rotation);
        GL20.glUniform4f(shape, (float) size, (float) amount, (float) bend, (float) (clock % 100000));
        // Layers that disable lighting later (glints, charge auras) keep this model-wide choice.
        GL20.glUniform3f(thread, (float) floor, (float) wobble, GL11.glIsEnabled(GL11.GL_LIGHTING) ? 1 : 0);
        binds++;
        return previous;
    }

    static void unbind(int previous) {
        GL20.glUseProgram(previous);
    }

    /** Compiles ahead of the first stretched model; never binds the program. */
    static void warmUp() {
        if (failed || program != 0 || !GLContext.getCapabilities().OpenGL20) return;
        create();
    }

    private static boolean create() {
        int vertex = 0, linked = 0;
        boolean success = false;
        try {
            vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            if (vertex == 0) return false;
            GL20.glShaderSource(vertex, VERTEX); GL20.glCompileShader(vertex);
            if (GL20.glGetShaderi(vertex, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) return false;
            linked = GL20.glCreateProgram();
            if (linked == 0) return false;
            GL20.glAttachShader(linked, vertex);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) return false;
            GL20.glDetachShader(linked, vertex);
            anchor = GL20.glGetUniformLocation(linked, "anchor");
            axis = GL20.glGetUniformLocation(linked, "axis");
            drift = GL20.glGetUniformLocation(linked, "drift");
            align = GL20.glGetUniformLocation(linked, "align");
            shape = GL20.glGetUniformLocation(linked, "shape");
            thread = GL20.glGetUniformLocation(linked, "thread");
            program = linked;
            success = true;
            return true;
        } catch (RuntimeException unsupported) {
            return false;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (!success) {
                failed = true;
                if (linked != 0) GL20.glDeleteProgram(linked);
            }
        }
    }
}
