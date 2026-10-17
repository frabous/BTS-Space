package fr.frabous.stellar.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Small untextured geometry primitives shared by world effects and local space scenes. */
final class EffectMesh {
    private EffectMesh() { }

    private static final float[] COLOR_CHANNELS = new float[256];
    private static final double[] RING_COS = new double[97];
    private static final double[] RING_SIN = new double[97];
    private static final double[] COLUMN_COS = new double[49];
    private static final double[] COLUMN_SIN = new double[49];
    private static final double[] GLOW_RING_COS = new double[129];
    private static final double[] GLOW_RING_SIN = new double[129];
    private static final double[] CRYSTAL_COS = new double[9];
    private static final double[] CRYSTAL_SIN = new double[9];
    private static final double[] GLOW_OFFSETS = {-4, -2, -0.5, 0, 0.5, 2, 4};
    private static final float[] GLOW_OPACITY = {0, 0.08F, 0.4F, 1, 0.4F, 0.08F, 0};
    private static final java.nio.FloatBuffer VIEW = org.lwjgl.BufferUtils.createFloatBuffer(16);

    // Keep the original angle and channel expressions while moving their work out of render loops.
    static {
        for (int i = 0; i < COLOR_CHANNELS.length; i++) COLOR_CHANNELS[i] = i / 255F;
        for (int i = 0; i < RING_COS.length; i++) {
            double a = i * Math.PI * 2 / 96;
            RING_COS[i] = Math.cos(a);
            RING_SIN[i] = Math.sin(a);
        }
        for (int i = 0; i < COLUMN_COS.length; i++) {
            double a = i * Math.PI * 2 / 48;
            COLUMN_COS[i] = Math.cos(a);
            COLUMN_SIN[i] = Math.sin(a);
        }
        for (int i = 0; i < GLOW_RING_COS.length; i++) {
            double a = i * Math.PI / 64;
            GLOW_RING_COS[i] = Math.cos(a);
            GLOW_RING_SIN[i] = Math.sin(a);
        }
        for (int i = 0; i < CRYSTAL_COS.length; i++) {
            double a = i * Math.PI / 4;
            CRYSTAL_COS[i] = Math.cos(a);
            CRYSTAL_SIN[i] = Math.sin(a);
        }
    }

    static void vertex(BufferBuilder buffer, double x, double y, double z, int rgb, float alpha) {
        buffer.pos(x, y, z).color(COLOR_CHANNELS[rgb >> 16 & 255], COLOR_CHANNELS[rgb >> 8 & 255],
                COLOR_CHANNELS[rgb & 255], alpha).endVertex();
    }
    static void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x1, y1, z1, rgb, alpha); vertex(b, x2, y2, z2, rgb, alpha); Tessellator.getInstance().draw();
    }
    static void ring(double x, double y, double z, double radius, double thickness, int rgb, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 96; i++) {
            double c = RING_COS[i], s = RING_SIN[i];
            vertex(b, x + c * radius, y, z + s * radius, rgb, alpha);
            vertex(b, x + c * (radius + thickness), y, z + s * (radius + thickness), rgb, alpha);
        }
        Tessellator.getInstance().draw();
    }
    static void column(double x, double z, double bottom, double top, double radius, int rgb, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            double c = COLUMN_COS[i] * radius, s = COLUMN_SIN[i] * radius;
            vertex(b, x + c, bottom, z + s, rgb, alpha); vertex(b, x + c, top, z + s, rgb, alpha);
        }
        Tessellator.getInstance().draw();
    }
    static void glowRing(double x, double y, double z, double radius, double width, int rgb, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int band = 0; band < GLOW_OFFSETS.length - 1; band++) {
            double inner = Math.max(0, radius + GLOW_OFFSETS[band] * width);
            double outer = Math.max(0, radius + GLOW_OFFSETS[band + 1] * width);
            int c0 = band == 3 ? 0xFFF3F8 : rgb, c1 = band == 2 ? 0xFFF3F8 : rgb;
            float innerAlpha = GLOW_OPACITY[band] * alpha, outerAlpha = GLOW_OPACITY[band + 1] * alpha;
            for (int i = 0; i < 128; i++) {
                double c = GLOW_RING_COS[i], s = GLOW_RING_SIN[i];
                double nc = GLOW_RING_COS[i + 1], ns = GLOW_RING_SIN[i + 1];
                vertex(b, x + c * inner, y, z + s * inner, c0, innerAlpha);
                vertex(b, x + nc * inner, y, z + ns * inner, c0, innerAlpha);
                vertex(b, x + nc * outer, y, z + ns * outer, c1, outerAlpha);
                vertex(b, x + c * outer, y, z + s * outer, c1, outerAlpha);
            }
        }
        Tessellator.getInstance().draw();
    }
    /** Horizontal glowing arc from angle a0 to a1 (radians), sharing glowRing's soft profile. */
    static void arc(double x, double y, double z, double radius, double width, double a0, double a1, int rgb, float alpha) {
        if (alpha <= 0 || a1 <= a0) return;
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / (Math.PI / 64)));
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int band = 0; band < GLOW_OFFSETS.length - 1; band++) {
            double inner = Math.max(0, radius + GLOW_OFFSETS[band] * width);
            double outer = Math.max(0, radius + GLOW_OFFSETS[band + 1] * width);
            int c0 = band == 3 ? 0xFFF3F8 : rgb, c1 = band == 2 ? 0xFFF3F8 : rgb;
            float innerAlpha = GLOW_OPACITY[band] * alpha, outerAlpha = GLOW_OPACITY[band + 1] * alpha;
            for (int i = 0; i < steps; i++) {
                double a = a0 + (a1 - a0) * i / steps, n = a0 + (a1 - a0) * (i + 1) / steps;
                double c = Math.cos(a), s = Math.sin(a), nc = Math.cos(n), ns = Math.sin(n);
                vertex(b, x + c * inner, y, z + s * inner, c0, innerAlpha);
                vertex(b, x + nc * inner, y, z + ns * inner, c0, innerAlpha);
                vertex(b, x + nc * outer, y, z + ns * outer, c1, outerAlpha);
                vertex(b, x + c * outer, y, z + s * outer, c1, outerAlpha);
            }
        }
        Tessellator.getInstance().draw();
    }

    /** Camera-facing radial glow: white-hot centre fading to a transparent coloured edge. */
    static void billboard(double x, double y, double z, double radius, int rgb, float alpha) {
        if (alpha <= 0 || radius <= 0) return;
        VIEW.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, VIEW);
        // Rows of the view rotation are the camera axes expressed in the current model space.
        double rx = VIEW.get(0), ry = VIEW.get(4), rz = VIEW.get(8);
        double ux = VIEW.get(1), uy = VIEW.get(5), uz = VIEW.get(9);
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz), ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (rl < 1e-6 || ul < 1e-6) return;
        rx /= rl; ry /= rl; rz /= rl; ux /= ul; uy /= ul; uz /= ul;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        double mid = radius * 0.28;
        float midAlpha = alpha * 0.75F;
        for (int i = 0; i < 32; i++) {
            double c = GLOW_RING_COS[i * 4], s = GLOW_RING_SIN[i * 4];
            double nc = GLOW_RING_COS[(i + 1) * 4], ns = GLOW_RING_SIN[(i + 1) * 4];
            double ax = rx * c + ux * s, ay = ry * c + uy * s, az = rz * c + uz * s;
            double bx = rx * nc + ux * ns, by = ry * nc + uy * ns, bz = rz * nc + uz * ns;
            // Hot inner disc, then a soft coloured falloff to a transparent edge.
            vertex(b, x, y, z, 0xFFFFFF, alpha);
            vertex(b, x + ax * mid, y + ay * mid, z + az * mid, 0xFFE6EE, midAlpha);
            vertex(b, x + bx * mid, y + by * mid, z + bz * mid, 0xFFE6EE, midAlpha);
            vertex(b, x + ax * mid, y + ay * mid, z + az * mid, 0xFFE6EE, midAlpha);
            vertex(b, x + ax * radius, y + ay * radius, z + az * radius, rgb, 0);
            vertex(b, x + bx * radius, y + by * radius, z + bz * radius, rgb, 0);
            vertex(b, x + ax * mid, y + ay * mid, z + az * mid, 0xFFE6EE, midAlpha);
            vertex(b, x + bx * radius, y + by * radius, z + bz * radius, rgb, 0);
            vertex(b, x + bx * mid, y + by * mid, z + bz * mid, 0xFFE6EE, midAlpha);
        }
        Tessellator.getInstance().draw();
    }

    static void crystal(double x, double y, double z, double radius, double height, int rgb, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            double c = CRYSTAL_COS[i], s = CRYSTAL_SIN[i];
            double nc = CRYSTAL_COS[i + 1], ns = CRYSTAL_SIN[i + 1];
            vertex(b, x, y + height, z, 0xFFFFFF, alpha);
            vertex(b, x + c * radius, y + height * 0.45, z + s * radius, rgb, alpha * 0.75F);
            vertex(b, x + nc * radius, y + height * 0.45, z + ns * radius, rgb, alpha * 0.55F);
            vertex(b, x, y, z, rgb, alpha * 0.5F);
            vertex(b, x + nc * radius, y + height * 0.45, z + ns * radius, rgb, alpha * 0.55F);
            vertex(b, x + c * radius, y + height * 0.45, z + s * radius, rgb, alpha * 0.75F);
        }
        Tessellator.getInstance().draw();
    }
}
