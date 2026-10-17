package fr.frabous.stellar.client;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * Articulated hard-surface geometry for cinematic machines: an affine transform stack over one
 * POSITION_COLOR quad batch. A pass emits either shaded hull faces or emissive faces, never both,
 * so the caller can draw the same rig opaque, then additive, then into the bloom target.
 */
final class RigMesh {
    private static final double[] LIGHT = normalize(0.38, 0.82, 0.43);
    private static final double TAU = Math.PI * 2;
    private final BufferBuilder buffer;
    private final boolean emissive;
    private final double[] light;
    private final double[][] stack = new double[24][];
    private double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    private int depth;

    RigMesh(BufferBuilder buffer, boolean emissive) { this(buffer, emissive, LIGHT); }

    /** Hand-held props light their face from the viewer's side instead of the sky. */
    RigMesh(BufferBuilder buffer, boolean emissive, double[] light) {
        this.buffer = buffer; this.emissive = emissive; this.light = normalize(light[0], light[1], light[2]);
    }

    boolean emissive() { return emissive; }

    void push() { stack[depth++] = m.clone(); }
    void pop() { m = stack[--depth]; }

    void translate(double x, double y, double z) {
        m[3] += m[0] * x + m[1] * y + m[2] * z;
        m[7] += m[4] * x + m[5] * y + m[6] * z;
        m[11] += m[8] * x + m[9] * y + m[10] * z;
    }

    void rotateX(double a) { multiply(1, 0, 0, 0, Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a)); }
    void rotateY(double a) { multiply(Math.cos(a), 0, Math.sin(a), 0, 1, 0, -Math.sin(a), 0, Math.cos(a)); }
    void rotateZ(double a) { multiply(Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a), 0, 0, 0, 1); }
    void scale(double s) { multiply(s, 0, 0, 0, s, 0, 0, 0, s); }

    /** Current local point in the caller's space, used for beam anchors and tests. */
    double[] point(double x, double y, double z) {
        return new double[] {m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    double[] direction(double x, double y, double z) {
        double[] d = {m[0] * x + m[1] * y + m[2] * z, m[4] * x + m[5] * y + m[6] * z, m[8] * x + m[9] * y + m[10] * z};
        double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d[0] /= length; d[1] /= length; d[2] /= length;
        return d;
    }

    // --- Shaded hull primitives -------------------------------------------------------------

    void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
        if (emissive) return;
        boxFaces(x0, y0, z0, x1, y1, z1, rgb, 1, false);
    }

    void glowBox(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha) {
        if (!emissive || alpha <= 0) return;
        boxFaces(x0, y0, z0, x1, y1, z1, rgb, Math.min(1, alpha), true);
    }

    /** n-gon frustum along local X; radii are circumscribed, phase rotates the first corner. */
    void prism(double x0, double x1, double r0, double r1, int sides, double phase, int rgb, boolean caps) {
        if (emissive) return;
        prismFaces(x0, x1, r0, r1, sides, phase, rgb, 1, caps, false);
    }

    void glowPrism(double x0, double x1, double r0, double r1, int sides, double phase, int rgb, float alpha) {
        if (!emissive || alpha <= 0) return;
        prismFaces(x0, x1, r0, r1, sides, phase, rgb, Math.min(1, alpha), false, true);
    }

    /** Square-section ring around local X, from x to x+width. */
    void ring(double x, double width, double inner, double outer, int sides, double phase, int rgb) {
        if (emissive) return;
        ringFaces(x, width, inner, outer, sides, phase, rgb, 1, false, 0, 0);
    }

    /** Ring whose segments alternate rgb/alternate, so its rotation reads clearly. */
    void notchedRing(double x, double width, double inner, double outer, int sides, double phase, int rgb, int alternate) {
        if (emissive) return;
        ringFaces(x, width, inner, outer, sides, phase, rgb, 1, false, alternate, 2);
    }

    void glowRing(double x, double width, double inner, double outer, int sides, double phase, int rgb, float alpha) {
        if (!emissive || alpha <= 0) return;
        ringFaces(x, width, inner, outer, sides, phase, rgb, Math.min(1, alpha), true, 0, 0);
    }

    /** Arbitrary planar quad with an outward hint used only to orient its shading normal. */
    void quad(double[] a, double[] b, double[] c, double[] d, double hx, double hy, double hz, int rgb) {
        if (emissive) return;
        face(a, b, c, d, hx, hy, hz, rgb, 1, false);
    }

    void glowQuad(double[] a, double[] b, double[] c, double[] d, int rgb, float alpha) {
        if (!emissive || alpha <= 0) return;
        face(a, b, c, d, 0, 1, 0, rgb, Math.min(1, alpha), true);
    }

    // --- Internals ------------------------------------------------------------------------

    private void boxFaces(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha, boolean light) {
        double[] p000 = {x0, y0, z0}, p100 = {x1, y0, z0}, p110 = {x1, y1, z0}, p010 = {x0, y1, z0};
        double[] p001 = {x0, y0, z1}, p101 = {x1, y0, z1}, p111 = {x1, y1, z1}, p011 = {x0, y1, z1};
        face(p000, p001, p011, p010, -1, 0, 0, rgb, alpha, light);
        face(p100, p110, p111, p101, 1, 0, 0, rgb, alpha, light);
        face(p000, p100, p101, p001, 0, -1, 0, rgb, alpha, light);
        face(p010, p011, p111, p110, 0, 1, 0, rgb, alpha, light);
        face(p000, p010, p110, p100, 0, 0, -1, rgb, alpha, light);
        face(p001, p101, p111, p011, 0, 0, 1, rgb, alpha, light);
    }

    private void prismFaces(double x0, double x1, double r0, double r1, int sides, double phase, int rgb,
                            float alpha, boolean caps, boolean light) {
        for (int i = 0; i < sides; i++) {
            double a = phase + i * TAU / sides, b = phase + (i + 1) * TAU / sides, mid = (a + b) / 2;
            double[] p0 = {x0, Math.cos(a) * r0, Math.sin(a) * r0}, p1 = {x1, Math.cos(a) * r1, Math.sin(a) * r1};
            double[] p2 = {x1, Math.cos(b) * r1, Math.sin(b) * r1}, p3 = {x0, Math.cos(b) * r0, Math.sin(b) * r0};
            face(p0, p1, p2, p3, 0, Math.cos(mid), Math.sin(mid), rgb, alpha, light);
            if (caps) {
                double[] c0 = {x0, 0, 0}, c1 = {x1, 0, 0};
                face(c0, p0, p3, p3, -1, 0, 0, rgb, alpha, light);
                face(c1, p2, p1, p1, 1, 0, 0, rgb, alpha, light);
            }
        }
    }

    private void ringFaces(double x, double width, double inner, double outer, int sides, double phase, int rgb,
                           float alpha, boolean light, int alternate, int period) {
        double xa = x, xb = x + width;
        for (int i = 0; i < sides; i++) {
            double a = phase + i * TAU / sides, b = phase + (i + 1) * TAU / sides, mid = (a + b) / 2;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            int color = period > 0 && i % period == 0 ? alternate : rgb;
            double[] oa0 = {xa, ca * outer, sa * outer}, ob0 = {xa, cb * outer, sb * outer};
            double[] oa1 = {xb, ca * outer, sa * outer}, ob1 = {xb, cb * outer, sb * outer};
            double[] ia0 = {xa, ca * inner, sa * inner}, ib0 = {xa, cb * inner, sb * inner};
            double[] ia1 = {xb, ca * inner, sa * inner}, ib1 = {xb, cb * inner, sb * inner};
            face(oa0, oa1, ob1, ob0, 0, Math.cos(mid), Math.sin(mid), color, alpha, light);
            face(ia0, ib0, ib1, ia1, 0, -Math.cos(mid), -Math.sin(mid), color, alpha, light);
            face(oa0, ob0, ib0, ia0, -1, 0, 0, color, alpha, light);
            face(oa1, ia1, ib1, ob1, 1, 0, 0, color, alpha, light);
        }
    }

    private void face(double[] a, double[] b, double[] c, double[] d, double hx, double hy, double hz,
                      int rgb, float alpha, boolean light) {
        double[] pa = point(a[0], a[1], a[2]), pb = point(b[0], b[1], b[2]);
        double[] pc = point(c[0], c[1], c[2]), pd = point(d[0], d[1], d[2]);
        int color = rgb;
        if (!light) {
            double ux = pc[0] - pa[0], uy = pc[1] - pa[1], uz = pc[2] - pa[2];
            double vx = pd[0] - pb[0], vy = pd[1] - pb[1], vz = pd[2] - pb[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double[] hint = direction(hx, hy, hz);
            if (length < 1.0E-12) { nx = hint[0]; ny = hint[1]; nz = hint[2]; length = 1; }
            nx /= length; ny /= length; nz /= length;
            if (nx * hint[0] + ny * hint[1] + nz * hint[2] < 0) { nx = -nx; ny = -ny; nz = -nz; }
            color = shade(rgb, level(this.light, nx, ny, nz));
        }
        EffectMesh.vertex(buffer, pa[0], pa[1], pa[2], color, alpha);
        EffectMesh.vertex(buffer, pb[0], pb[1], pb[2], color, alpha);
        EffectMesh.vertex(buffer, pc[0], pc[1], pc[2], color, alpha);
        EffectMesh.vertex(buffer, pd[0], pd[1], pd[2], color, alpha);
    }

    private void multiply(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        double[] r = m;
        double r00 = r[0] * a + r[1] * d + r[2] * g, r01 = r[0] * b + r[1] * e + r[2] * h, r02 = r[0] * c + r[1] * f + r[2] * i;
        double r10 = r[4] * a + r[5] * d + r[6] * g, r11 = r[4] * b + r[5] * e + r[6] * h, r12 = r[4] * c + r[5] * f + r[6] * i;
        double r20 = r[8] * a + r[9] * d + r[10] * g, r21 = r[8] * b + r[9] * e + r[10] * h, r22 = r[8] * c + r[9] * f + r[10] * i;
        r[0] = r00; r[1] = r01; r[2] = r02; r[4] = r10; r[5] = r11; r[6] = r12; r[8] = r20; r[9] = r21; r[10] = r22;
    }

    /** Baked diffuse plus sky term for a unit normal, shared by textured props drawn beside a rig. */
    static double level(double[] light, double nx, double ny, double nz) {
        double diffuse = Math.max(0, nx * light[0] + ny * light[1] + nz * light[2]);
        return 0.34 + 0.62 * diffuse + 0.12 * (ny * 0.5 + 0.5);
    }

    static int shade(int rgb, double value) {
        int r = (int) Math.min(255, (rgb >> 16 & 255) * value);
        int g = (int) Math.min(255, (rgb >> 8 & 255) * value);
        int b = (int) Math.min(255, (rgb & 255) * value);
        return r << 16 | g << 8 | b;
    }

    static int mix(int from, int to, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int g = (int) ((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
        return r << 16 | g << 8 | b;
    }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
