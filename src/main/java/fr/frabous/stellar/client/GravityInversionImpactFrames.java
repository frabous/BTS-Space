package fr.frabous.stellar.client;

import fr.frabous.stellar.api.GravityInversionRules;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * SS-05 ink frames. The collapse cel is a crater seen edge-on: a flat horizon split by the impact, slabs
 * hanging upside-down above it and hard rays. Flip cels are a single skewed slash of speed lines.
 */
final class GravityInversionImpactFrames {
    private static final int INK = 0x07020B, PAPER = 0xFFF4FC;
    private GravityInversionImpactFrames() { }

    /** cx/cy: impact point on screen, already clamped inside the frame. */
    static void draw(long seed, double age, int width, int height, double cx, double cy) {
        float alpha = (float) GravityInversionVisuals.impactFrame(age);
        if (alpha <= 0) return;
        boolean negative = GravityInversionVisuals.impactNegative(age);
        int ground = negative ? PAPER : INK, ink = negative ? INK : PAPER;
        Gui.drawRect(0, 0, width, height, (int) (alpha * 250) << 24 | ground);
        begin(width, height);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            if (age >= GravityInversionRules.IMPACT) collapse(b, seed, age, width, height, cx, cy, ink, ground, negative, alpha);
            else slash(b, seed, age, width, height, ink, alpha);
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    private static void collapse(BufferBuilder b, long seed, double age, int width, int height, double cx, double cy,
                                 int ink, int ground, boolean negative, float alpha) {
        int cel = (int) ((age - GravityInversionRules.IMPACT) / 1.5), salt = cel * 997;
        double t = Math.min(1, (age - GravityInversionRules.IMPACT) / 6);
        // Horizon slab: the ground the world was slammed into.
        quad(b, 0, cy + height * 0.02, width, cy - height * 0.01, width, height, 0, height, ink, alpha);
        double radius = height * (0.08 + 0.1 * t);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI + i * Math.PI / 64, next = Math.PI + (i + 1) * Math.PI / 64;
            double r0 = radius * (1 + random(seed, 100 + salt + i) * 0.5), r1 = radius * (1 + random(seed, 100 + salt + (i + 1) % 64) * 0.5);
            vertex(b, cx, cy, ink, alpha);
            vertex(b, cx + Math.cos(a) * r0 * 1.8, cy + Math.sin(a) * r0, ink, alpha);
            vertex(b, cx + Math.cos(next) * r1 * 1.8, cy + Math.sin(next) * r1, ink, alpha);
        }
        int rays = negative ? 30 : 44;
        for (int i = 0; i < rays; i++) {
            // Upper half-plane only: the blast is thrown up off the ground.
            double a = Math.PI + (i + random(seed, 300 + salt + i)) * Math.PI / rays;
            double reach = radius * (2 + random(seed, 500 + salt + i) * (negative ? 6 : 4.5));
            double half = 0.008 + random(seed, 700 + salt + i) * 0.02;
            triangle(b, cx, cy, a - half, radius * 0.9, a, reach, a + half, radius * 0.9, ink, alpha * 0.9F);
        }
        // Ground cracks racing outward along the horizon.
        for (int side = -1; side <= 1; side += 2) for (int i = 0; i < 9; i++) {
            double x0 = cx + side * radius * (0.6 + i * 0.5), x1 = x0 + side * width * (0.04 + random(seed, 900 + salt + i) * 0.08);
            double y = cy + height * (0.005 + random(seed, 1100 + salt + i + side) * 0.03);
            vertex(b, x0, y - 1.5, ground, alpha); vertex(b, x1, y, ground, alpha); vertex(b, x0, y + 1.5, ground, alpha);
        }
        // Slabs caught falling: inverted wedges hanging over the crater.
        for (int i = 0; i < 9; i++) {
            double fx = width * (0.08 + 0.84 * random(seed, 1300 + i));
            double fy = cy - height * (0.12 + 0.5 * random(seed, 1500 + i)) * (1 - 0.5 * t);
            double s = height * (0.025 + 0.05 * random(seed, 1700 + i));
            double tilt = (random(seed, 1900 + i) - 0.5) * 0.6;
            double ux = Math.cos(tilt) * s, uy = Math.sin(tilt) * s;
            vertex(b, fx - ux, fy - uy, ink, alpha); vertex(b, fx + ux, fy + uy, ink, alpha);
            vertex(b, fx - uy * 1.6, fy + ux * 1.6, ink, alpha);
            // Their fall streak.
            vertex(b, fx - s * 0.12, fy - s * 0.2, ink, alpha * 0.8F); vertex(b, fx + s * 0.12, fy - s * 0.2, ink, alpha * 0.8F);
            vertex(b, fx, fy - s * (3 + 4 * random(seed, 2100 + i)), ink, 0);
        }
    }

    /** Single slash cel at a gravity flip or the slam: thick speed lines skewed along the new pull. */
    private static void slash(BufferBuilder b, long seed, double age, int width, int height, int ink, float alpha) {
        int k = Math.max(0, GravityInversionRules.flip(age));
        double angle = age >= GravityInversionRules.SLAM && age < GravityInversionRules.IMPACT ? Math.PI / 2
                : Math.atan2(-GravityInversionRules.gravity(seed, k)[1], (k % 2 == 0 ? 1 : -1) * 0.8);
        double dx = Math.cos(angle), dy = Math.sin(angle), nx = -dy, ny = dx, span = Math.hypot(width, height);
        for (int i = 0; i < 40; i++) {
            double offset = (random(seed, 2300 + k * 61 + i) - 0.5) * span;
            double along = (random(seed, 2500 + k * 61 + i) - 0.5) * span * 0.6, length = span * (0.2 + 0.5 * random(seed, 2700 + k * 61 + i));
            double half = 1 + random(seed, 2900 + k * 61 + i) * 6;
            double ox = width * 0.5 + nx * offset + dx * along, oy = height * 0.5 + ny * offset + dy * along;
            vertex(b, ox - nx * half, oy - ny * half, ink, alpha);
            vertex(b, ox + nx * half, oy + ny * half, ink, alpha);
            vertex(b, ox + dx * length, oy + dy * length, ink, 0);
        }
    }

    /** Pale gathering lines while the field hangs, ink speed lines after flips and during the slam. */
    static void speedLines(long seed, double age, int width, int height) {
        float alpha = (float) GravityInversionVisuals.speedLines(age);
        if (alpha <= 0) return;
        boolean slam = age >= GravityInversionRules.SLAM;
        int cel = (int) Math.floor(age / 1.5), color = slam ? 0xFFF4FC : 0xFFC8F2;
        begin(width, height);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < 70; i++) {
                int key = cel * 131 + i;
                double x = random(seed, 4000 + key) * width, y = random(seed, 4200 + key) * height;
                double length = height * (0.15 + 0.35 * random(seed, 4400 + key)), half = 0.6 + random(seed, 4600 + key) * 1.8;
                double dx, dy;
                if (slam) { dx = 0; dy = 1; }
                else {
                    double[] g = GravityInversionRules.gravity(seed, GravityInversionRules.flip(age));
                    double h = Math.hypot(g[0], g[2]);
                    dx = h * (GravityInversionRules.flip(age) % 2 == 0 ? 1 : -1); dy = -g[1];
                    double norm = Math.hypot(dx, dy); dx /= norm; dy /= norm;
                }
                vertex(b, x - dy * half, y + dx * half, color, alpha * 0.5F);
                vertex(b, x + dy * half, y - dx * half, color, alpha * 0.5F);
                vertex(b, x + dx * length, y + dy * length, color, 0);
            }
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    static void vignette(double age, int width, int height) {
        float alpha = (float) (0.35 + GravityInversionVisuals.hush(age));
        double cx = width * 0.5, cy = height * 0.5;
        begin(width, height);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            double squeeze = 1 - GravityInversionVisuals.hush(age) * 0.6;
            for (int i = 0; i <= 64; i++) {
                double a = i * Math.PI * 2 / 64, c = Math.cos(a), s = Math.sin(a);
                vertex(b, cx + c * width * 0.4 * squeeze, cy + s * height * 0.38 * squeeze, GravityInversionVisuals.VOID, 0);
                vertex(b, cx + c * width * 0.78, cy + s * height * 0.78, GravityInversionVisuals.VOID, alpha);
            }
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    private static void begin(int width, int height) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false); GL11.glEnable(GL11.GL_BLEND); GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void end() {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
        GL11.glPopClientAttrib(); GL11.glPopAttrib();
    }

    private static void vertex(BufferBuilder b, double x, double y, int color, float alpha) { EffectMesh.vertex(b, x, y, 0, color, alpha); }

    /** Polar triangle around (x, y). */
    private static void triangle(BufferBuilder b, double x, double y, double a, double r0, double c, double r1,
                                 double d, double r2, int color, float alpha) {
        vertex(b, x + Math.cos(a) * r0, y + Math.sin(a) * r0, color, alpha);
        vertex(b, x + Math.cos(c) * r1, y + Math.sin(c) * r1, color, alpha);
        vertex(b, x + Math.cos(d) * r2, y + Math.sin(d) * r2, color, alpha);
    }

    private static void quad(BufferBuilder b, double x0, double y0, double x1, double y1, double x2, double y2,
                             double x3, double y3, int color, float alpha) {
        vertex(b, x0, y0, color, alpha); vertex(b, x1, y1, color, alpha); vertex(b, x2, y2, color, alpha);
        vertex(b, x0, y0, color, alpha); vertex(b, x2, y2, color, alpha); vertex(b, x3, y3, color, alpha);
    }

    private static double random(long seed, int key) { return GravityInversionRules.unit(seed, key); }
}
