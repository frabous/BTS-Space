package fr.frabous.stellar.client;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/** Original textured cinema geometry. Local origin is the body's center; age is in ticks. */
@SideOnly(Side.CLIENT)
public final class CelestialModels {
    private static final ResourceLocation EARTH = texture("earth");
    private static final ResourceLocation CLOUDS = texture("earth_clouds");
    private static final ResourceLocation SATURN = texture("saturn");
    private static final ResourceLocation RINGS = texture("saturn_rings");
    private static final ResourceLocation GALAXY = texture("galaxy");
    private static final double[][] SPHERE = sphereGrid(128, 64);
    private static final double[][] ATMOSPHERE = sphereGrid(64, 32);

    private CelestialModels() { }

    /** Resolve the orbital backdrop before the short carrier-release shot starts. Render thread only. */
    static void prepareEarth() {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try { bind(EARTH,true); bind(CLOUDS,true); }
        finally { GlStateManager.bindTexture(binding); GlStateManager.setActiveTexture(active); }
    }

    /** Surface radius; north is +Y. Includes drifting clouds and a soft emissive cyan limb. */
    public static void earth(double radius, double age) {
        earth(radius, age, 1);
    }

    public static void earth(double radius, double age, float opacity) {
        if (radius <= 0) return;
        State state = new State();
        try {
            GL11.glRotated(-88 + age * 0.045, 0, 1, 0);
            bind(EARTH, true);
            if (opacity < 1) translucent(false);
            sphere(SPHERE, 128, 64, radius, 1, opacity, false);
            translucent(false);
            GL11.glRotated(age * 0.013, 0, 1, 0);
            bind(CLOUDS, true);
            sphere(SPHERE, 128, 64, radius * 1.006, 1, 0.93F * opacity, false);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            translucent(true);
            for (int shell = 0; shell < 5; shell++) {
                double scale = 1.010 + shell * 0.010;
                sphere(ATMOSPHERE, 64, 32, radius * scale, 1, (0.105F - shell * 0.017F) * opacity, true);
            }
        } finally {
            state.restore();
        }
    }

    /** Equatorial body radius; rings lie in local XZ and extend to 2.23 times that radius. */
    public static void saturn(double radius, double age) {
        if (radius <= 0) return;
        State state = new State();
        try {
            GL11.glRotated(age * 0.035, 0, 1, 0);
            bind(SATURN, true);
            sphere(SPHERE, 128, 64, radius, 0.90, 1, false);
            translucent(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            bind(RINGS, false);
            plane(radius * 2.30, 0, 1);
        } finally {
            state.restore();
        }
    }

    /** Face-on luminous disk in XZ (y=0), with supplied outer radius and no depth writes. */
    public static void galaxy(double radius, double age) {
        galaxy(radius, age, 1);
    }

    public static void galaxy(double radius, double age, float opacity) {
        if (radius <= 0) return;
        State state = new State();
        try {
            GL11.glRotated(age * 0.012, 0, 1, 0);
            translucent(true);
            GL11.glDisable(GL11.GL_CULL_FACE);
            bind(GALAXY, false);
            plane(radius, 0, 0.92F * opacity);
        } finally {
            state.restore();
        }
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stellar", "textures/cinema/" + name + ".png");
    }

    private static void bind(ResourceLocation texture, boolean wrap) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        // These parameters belong only to our own atlases; resource reloads reapply them.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void translucent(boolean emissive) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, emissive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
    }

    private static double[][] sphereGrid(int slices, int stacks) {
        double[][] grid = new double[(slices + 1) * (stacks + 1)][5];
        for (int row = 0; row <= stacks; row++) {
            double v = (double) row / stacks;
            double latitude = Math.PI * (0.5 - v);
            for (int column = 0; column <= slices; column++) {
                double u = (double) column / slices;
                double longitude = Math.PI * 2 * (u - 0.5);
                double[] p = grid[row * (slices + 1) + column];
                p[0] = Math.sin(longitude) * Math.cos(latitude);
                p[1] = Math.sin(latitude);
                p[2] = Math.cos(longitude) * Math.cos(latitude);
                p[3] = u;
                p[4] = v;
            }
        }
        return grid;
    }

    private static void sphere(double[][] grid, int slices, int stacks, double radius,
                               double flatten, float opacity, boolean atmosphere) {
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        // Bake smooth analytic surface-normal lighting into POSITION_TEX_COLOR vertices.
        // Inverse-transpose handles the caller's nonuniform scale as well as Saturn's shape.
        double m00 = matrix.get(0), m01 = matrix.get(4), m02 = matrix.get(8);
        double m10 = matrix.get(1), m11 = matrix.get(5), m12 = matrix.get(9);
        double m20 = matrix.get(2), m21 = matrix.get(6), m22 = matrix.get(10);
        double determinant = m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
        double normal0 = (m11 * m22 - m12 * m21) / determinant, normal1 = (m02 * m21 - m01 * m22) / determinant;
        double normal2 = (m01 * m12 - m02 * m11) / determinant, normal3 = (m12 * m20 - m10 * m22) / determinant;
        double normal4 = (m00 * m22 - m02 * m20) / determinant, normal5 = (m02 * m10 - m00 * m12) / determinant;
        double normal6 = (m10 * m21 - m11 * m20) / determinant, normal7 = (m01 * m20 - m00 * m21) / determinant;
        double normal8 = (m00 * m11 - m01 * m10) / determinant;
        float[] colors = new float[grid.length * 4];
        for (int i = 0; i < grid.length; i++) {
            double[] p = grid[i];
            double nx = normal0 * p[0] + normal3 * p[1] / flatten + normal6 * p[2];
            double ny = normal1 * p[0] + normal4 * p[1] / flatten + normal7 * p[2];
            double nz = normal2 * p[0] + normal5 * p[1] / flatten + normal8 * p[2];
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length; ny /= length; nz /= length;
            float light = (float) (0.19 + 0.81 * Math.max(0, -0.43 * nx + 0.52 * ny + 0.738 * nz));
            int colorIndex = i * 4;
            colors[colorIndex] = colors[colorIndex + 1] = colors[colorIndex + 2] = light;
            colors[colorIndex + 3] = opacity;
            if (atmosphere) {
                double x = p[0] * radius, y = p[1] * radius * flatten, z = p[2] * radius;
                double ex = matrix.get(0) * x + matrix.get(4) * y + matrix.get(8) * z + matrix.get(12);
                double ey = matrix.get(1) * x + matrix.get(5) * y + matrix.get(9) * z + matrix.get(13);
                double ez = matrix.get(2) * x + matrix.get(6) * y + matrix.get(10) * z + matrix.get(14);
                double distance = Math.sqrt(ex * ex + ey * ey + ez * ez);
                double facing = Math.abs((nx * ex + ny * ey + nz * ez) / Math.max(1.0E-8, distance));
                colors[colorIndex] = 0.24F; colors[colorIndex + 1] = 0.60F; colors[colorIndex + 2] = 1;
                colors[colorIndex + 3] *= (float) Math.pow(Math.max(0, 1 - facing), 3);
            }
        }
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int row = 0; row < stacks; row++) {
            for (int column = 0; column < slices; column++) {
                int a = row * (slices + 1) + column;
                int c = a + slices + 1;
                if (row > 0) {
                    sphereVertex(b, grid[a], colors, a * 4, radius, flatten);
                    sphereVertex(b, grid[c], colors, c * 4, radius, flatten);
                    sphereVertex(b, grid[a + 1], colors, (a + 1) * 4, radius, flatten);
                }
                if (row < stacks - 1) {
                    sphereVertex(b, grid[a + 1], colors, (a + 1) * 4, radius, flatten);
                    sphereVertex(b, grid[c], colors, c * 4, radius, flatten);
                    sphereVertex(b, grid[c + 1], colors, (c + 1) * 4, radius, flatten);
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void sphereVertex(BufferBuilder b, double[] p, float[] colors, int colorIndex,
                                    double radius, double flatten) {
        b.pos(p[0] * radius, p[1] * radius * flatten, p[2] * radius).tex(p[3], p[4])
                .color(colors[colorIndex], colors[colorIndex + 1], colors[colorIndex + 2], colors[colorIndex + 3])
                .endVertex();
    }

    private static void plane(double radius, double y, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
        planeVertex(b, -radius, y, -radius, 0, 0, alpha);
        planeVertex(b, -radius, y, radius, 0, 1, alpha);
        planeVertex(b, radius, y, -radius, 1, 0, alpha);
        planeVertex(b, radius, y, -radius, 1, 0, alpha);
        planeVertex(b, -radius, y, radius, 0, 1, alpha);
        planeVertex(b, radius, y, radius, 1, 1, alpha);
        Tessellator.getInstance().draw();
    }

    private static void planeVertex(BufferBuilder b, double x, double y, double z, double u, double v, float alpha) {
        b.pos(x, y, z).tex(u, v).color(1F, 1F, 1F, alpha).endVertex();
    }

    /** Raw GL enables are bracketed; texture binding/active-unit caches are restored explicitly. */
    private static final class State {
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int binding;

        private State() {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
        }

        private void restore() {
            GlStateManager.bindTexture(binding);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GlStateManager.setActiveTexture(activeTexture);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            // Tessellator's color-array cleanup invalidates the cached current color.
            GlStateManager.resetColor();
        }
    }
}
