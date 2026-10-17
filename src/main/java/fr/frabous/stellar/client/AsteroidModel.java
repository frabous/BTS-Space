package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AsteroidTrajectory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Irregular rubble-pile asteroid: elongated ellipsoid, fbm relief, impact craters and fresh planar
 * fracture faces. It spins about its short axis, the stable axis of a real tumbling body. Lighting
 * is evaluated per frame against the scene sun so the terminator stays fixed while it rotates; the
 * leading face heats along the actual velocity.
 */
final class AsteroidModel {
    static final double[] SPIN_AXIS = normalize(0.32, 1, 0.18);
    private static final double SPIN_OFFSET = 1.1;
    /** Low afternoon key light behind the ground cameras, so the face they see is lit, not the sky-facing top. */
    static final double[] SUN = normalize(0.3, 0.5, 0.81);
    private static final int LON = 144, LAT = 72;
    private static final double AXIS_X = 1.32, AXIS_Y = 0.86, AXIS_Z = 1.0;
    private static final double TAU = Math.PI * 2;
    private static final ResourceLocation REGOLITH = new ResourceLocation("stellar", "textures/cinema/moon_regolith.png");
    private static final double[][] VERTICES = new double[(LAT + 1) * (LON + 1)][3];
    private static final double[][] NORMALS = new double[VERTICES.length][3];
    private static final int[] ALBEDO = new int[VERTICES.length];
    private static final int[] INDICES = new int[LON * (LAT - 1) * 6];
    static final double MAX_RADIUS;

    static { MAX_RADIUS = build(); }

    private AsteroidModel() { }

    /** Builds the procedural mesh at start-up instead of on the first film frame. */
    static void prepare() { }

    static void prepareTextures() {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (ResourceLocation texture : new ResourceLocation[] {REGOLITH, MoonAttractVfx.GLOW, MoonAttractVfx.SMOKE, MoonAttractVfx.FIRE})
                Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        }
        finally { GlStateManager.bindTexture(binding); GlStateManager.setActiveTexture(active); }
    }

    static double angle(double age) { return AsteroidTrajectory.spin(age) + SPIN_OFFSET; }

    /** Row-major object-to-scene rotation for the current tumble angle. */
    static double[] rotation(double age) {
        double a = angle(age), c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        double x = SPIN_AXIS[0], y = SPIN_AXIS[1], z = SPIN_AXIS[2];
        return new double[] {t * x * x + c, t * x * y - s * z, t * x * z + s * y,
                t * x * y + s * z, t * y * y + c, t * y * z - s * x,
                t * x * z - s * y, t * y * z + s * x, t * z * z + c};
    }

    /** Lowest scene-space surface point below the centre; used to keep contact honest. */
    static double lowestOffset(double age) {
        double[] r = rotation(age);
        double low = 0;
        for (double[] v : VERTICES) low = Math.min(low, r[3] * v[0] + r[4] * v[1] + r[5] * v[2]);
        return low;
    }

    /** Surface extent along a scene direction (support distance). */
    static double extent(double age, double dx, double dy, double dz) {
        double[] r = rotation(age);
        double best = 0;
        for (double[] v : VERTICES) {
            double x = r[0] * v[0] + r[1] * v[1] + r[2] * v[2], y = r[3] * v[0] + r[4] * v[1] + r[5] * v[2];
            double z = r[6] * v[0] + r[7] * v[1] + r[8] * v[2];
            best = Math.max(best, x * dx + y * dy + z * dz);
        }
        return best;
    }

    /** Caller translates to the centre. Emission draws only the heated leading face. */
    static void draw(double age, double heat, double scale, boolean emission) {
        if (emission && heat <= 0.02) return;
        double[] r = rotation(age), v = AsteroidTrajectory.velocity(Math.min(age, fr.frabous.stellar.api.AsteroidPullerTiming.IMPACT));
        double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double[] lead = speed < 1.0E-6 ? new double[] {0, -1, 0} : new double[] {v[0] / speed, v[1] / speed, v[2] / speed};
        // Scene vectors in object space: R transpose.
        double lx = r[0] * SUN[0] + r[3] * SUN[1] + r[6] * SUN[2], ly = r[1] * SUN[0] + r[4] * SUN[1] + r[7] * SUN[2];
        double lz = r[2] * SUN[0] + r[5] * SUN[1] + r[8] * SUN[2];
        double vx = r[0] * lead[0] + r[3] * lead[1] + r[6] * lead[2], vy = r[1] * lead[0] + r[4] * lead[1] + r[7] * lead[2];
        double vz = r[2] * lead[0] + r[5] * lead[1] + r[8] * lead[2];
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushMatrix();
        try {
            GL11.glRotated(Math.toDegrees(angle(age)), SPIN_AXIS[0], SPIN_AXIS[1], SPIN_AXIS[2]);
            if (scale != 1) GL11.glScaled(scale, scale, scale);
            GravitonLanceModel.state(emission);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            int[] colors = new int[VERTICES.length];
            float[] glow = new float[VERTICES.length];
            for (int i = 0; i < VERTICES.length; i++) {
                double[] n = NORMALS[i];
                double front = smooth((n[0] * vx + n[1] * vy + n[2] * vz - 0.1) / 0.9);
                glow[i] = (float) (heat * front);
                if (emission) continue;
                double diffuse = Math.max(0, n[0] * lx + n[1] * ly + n[2] * lz);
                double up = r[3] * n[0] + r[4] * n[1] + r[5] * n[2];
                // Key sun, blue sky fill from above and warm ground bounce from below keep the night side readable.
                double sky = up * 0.5 + 0.5, bounce = Math.max(0, -up);
                double shade = 0.2 + 1.05 * diffuse + 0.16 * sky + 0.34 * bounce;
                int color = RigMesh.shade(ALBEDO[i], shade);
                if (diffuse < 0.35) color = RigMesh.mix(color, RigMesh.shade(bounce > 0.3 ? 0x8C8274 : 0x7C8FB0, shade), 0.4 * (1 - diffuse / 0.35));
                if (glow[i] > 0) color = RigMesh.mix(color, glow[i] > 0.7 ? 0xFFE7C2 : 0xFF8A3C, glow[i] * 0.85);
                colors[i] = color;
            }
            if (emission) {
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i < INDICES.length; i += 3) {
                    int a = INDICES[i], c = INDICES[i + 1], d = INDICES[i + 2];
                    if (glow[a] + glow[c] + glow[d] < 0.06) continue;
                    for (int k = 0; k < 3; k++) {
                        int index = INDICES[i + k];
                        double[] p = VERTICES[index];
                        float g = glow[index];
                        EffectMesh.vertex(b, p[0] * 1.004, p[1] * 1.004, p[2] * 1.004, g > 0.6 ? 0xFFF0D8 : 0xFF7A30, g * 0.75F);
                    }
                }
            } else {
                Minecraft.getMinecraft().getTextureManager().bindTexture(REGOLITH);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
                GL11.glEnable(GL11.GL_CULL_FACE);
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
                for (int index : INDICES) {
                    double[] p = VERTICES[index];
                    int color = colors[index];
                    b.pos(p[0], p[1], p[2]).tex((index % (LON + 1)) / (double) LON * 2, (index / (LON + 1)) / (double) LAT)
                            .color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1).endVertex();
                }
            }
            Tessellator.getInstance().draw();
        } finally {
            GL11.glPopMatrix();
            GlStateManager.bindTexture(binding);
            GlStateManager.setActiveTexture(activeTexture);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
            GlStateManager.resetColor();
        }
    }

    private static double build() {
        double[][] craters = new double[42][5];
        for (int i = 0; i < craters.length; i++) {
            double y = 1 - 2 * (i + 0.5) / craters.length, angle = i * 2.399963229728653 + (hash(i * 13 + 2) - 0.5) * 0.9;
            double radial = Math.sqrt(1 - y * y), size = Math.pow(hash(i * 7 + 3), 2.2);
            craters[i] = new double[] {radial * Math.cos(angle), y, radial * Math.sin(angle), 0.06 + 0.34 * size,
                    0.03 + 0.07 * size};
        }
        double[][] blobs = new double[8][5];
        for (int i = 0; i < blobs.length; i++) {
            double[] d = normalize(hash(i * 5 + 11) - 0.5, hash(i * 5 + 12) - 0.5, hash(i * 5 + 13) - 0.5);
            blobs[i] = new double[] {d[0], d[1], d[2], 0.55 + 0.5 * hash(i * 5 + 14), (hash(i * 5 + 15) - 0.42) * 0.26};
        }
        double[][] cuts = new double[4][4];
        for (int i = 0; i < cuts.length; i++) {
            double[] d = normalize(hash(i * 9 + 31) - 0.5, (hash(i * 9 + 32) - 0.5) * 1.4, hash(i * 9 + 33) - 0.5);
            cuts[i] = new double[] {d[0], d[1], d[2], (0.8 + 0.08 * hash(i * 9 + 34)) * ellipsoid(d)};
        }
        boolean[] fracture = new boolean[VERTICES.length];
        double[] relief = new double[VERTICES.length];
        for (int lat = 0; lat <= LAT; lat++) {
            double theta = lat * Math.PI / LAT, y = Math.cos(theta), radial = Math.sin(theta);
            for (int lon = 0; lon <= LON; lon++) {
                double phi = (lon % LON) * TAU / LON, x = radial * Math.cos(phi), z = radial * Math.sin(phi);
                double surface = fbm(x * 1.7, y * 1.7, z * 1.7, 3) * 0.17 + fbm(x * 4.6 + 7, y * 4.6, z * 4.6, 3) * 0.055
                        + fbm(x * 12 + 3, y * 12 + 9, z * 12, 2) * 0.016;
                for (double[] blob : blobs) {
                    double angle = Math.acos(clamp(x * blob[0] + y * blob[1] + z * blob[2], -1, 1)) / blob[3];
                    surface += blob[4] * Math.exp(-angle * angle);
                }
                double crater = 0;
                for (double[] c : craters) {
                    double distance = Math.acos(clamp(x * c[0] + y * c[1] + z * c[2], -1, 1)) / c[3];
                    if (distance >= 1.45) continue;
                    double bowl = 1 - smooth(distance), rim = Math.exp(-Math.pow((distance - 1) / 0.16, 2));
                    crater += c[4] * (-bowl + 0.35 * rim);
                }
                double r = AsteroidTrajectory.RADIUS * ellipsoid(new double[] {x, y, z}) * (1 + surface + crater);
                int index = lat * (LON + 1) + lon;
                for (double[] cut : cuts) {
                    double facing = x * cut[0] + y * cut[1] + z * cut[2];
                    if (facing > 0.05) {
                        double limit = AsteroidTrajectory.RADIUS * cut[3] / facing;
                        if (limit < r) { r = limit; fracture[index] = true; }
                    }
                }
                VERTICES[index][0] = x * r; VERTICES[index][1] = y * r; VERTICES[index][2] = z * r;
                relief[index] = crater;
                double tone = fbm(x * 3 + 21, y * 3, z * 3 + 5, 3);
                int albedo = RigMesh.mix(0x938878, 0x645B52, smooth(tone * 2.4 + 0.5));
                if (crater < -0.01) albedo = RigMesh.mix(albedo, 0x4A443E, Math.min(1, -crater * 14));
                if (crater > 0.004) albedo = RigMesh.mix(albedo, 0xA39A8E, Math.min(1, crater * 40));
                if (fracture[index]) albedo = RigMesh.mix(albedo, 0x958B7F, 0.55);
                ALBEDO[index] = albedo;
            }
        }
        int cursor = 0;
        for (int lat = 0; lat < LAT; lat++) for (int lon = 0; lon < LON; lon++) {
            int a = lat * (LON + 1) + lon, b = a + LON + 1;
            // Same winding as the former lunar mesh, which renders its outside with back-face culling.
            if (lat != 0) { INDICES[cursor++] = a; INDICES[cursor++] = a + 1; INDICES[cursor++] = b; }
            if (lat != LAT - 1) { INDICES[cursor++] = a + 1; INDICES[cursor++] = b + 1; INDICES[cursor++] = b; }
        }
        for (int i = 0; i < INDICES.length; i += 3) {
            double[] a = VERTICES[INDICES[i]], b = VERTICES[INDICES[i + 1]], c = VERTICES[INDICES[i + 2]];
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int k = 0; k < 3; k++) { double[] n = NORMALS[INDICES[i + k]]; n[0] += nx; n[1] += ny; n[2] += nz; }
        }
        for (int lat = 0; lat <= LAT; lat++) {
            int first = lat * (LON + 1), last = first + LON;
            for (int axis = 0; axis < 3; axis++) { NORMALS[first][axis] += NORMALS[last][axis]; NORMALS[last][axis] = NORMALS[first][axis]; }
        }
        double max = 0;
        for (int i = 0; i < VERTICES.length; i++) {
            double[] n = NORMALS[i], p = VERTICES[i];
            double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            // Poles collapse to a point; fall back to the radial direction there.
            if (i < LON + 1 || i >= LAT * (LON + 1) || length < 1.0E-9) {
                double radius = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                n[0] = p[0] / radius; n[1] = p[1] / radius; n[2] = p[2] / radius;
            } else {
                // Outward winding check: flip if the accumulated normal points inward.
                if (n[0] * p[0] + n[1] * p[1] + n[2] * p[2] < 0) length = -length;
                n[0] /= length; n[1] /= length; n[2] /= length;
            }
            max = Math.max(max, Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
        }
        return max;
    }

    private static double ellipsoid(double[] d) {
        double x = d[0] / AXIS_X, y = d[1] / AXIS_Y, z = d[2] / AXIS_Z;
        return 1 / Math.sqrt(x * x + y * y + z * z);
    }

    private static double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amplitude = 1, total = 0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * (noise(x, y, z) * 2 - 1);
            total += amplitude;
            x *= 2.03; y *= 2.03; z *= 2.03; amplitude *= 0.5;
        }
        return sum / total;
    }

    private static double noise(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = fade(x - ix), fy = fade(y - iy), fz = fade(z - iz);
        double result = 0;
        for (int dz = 0; dz < 2; dz++) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
            result += weight * lattice(ix + dx, iy + dy, iz + dz);
        }
        return result;
    }

    private static double lattice(int x, int y, int z) {
        long h = x * 0x8DA6B343L ^ y * 0xD8163841L ^ z * 0xCB1AB31FL;
        h = (h ^ (h >>> 13)) * 0x5BD1E995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static double hash(int key) {
        double value = Math.sin(key * 91.7 + 47.3) * 43758.5453;
        return value - Math.floor(value);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    static double smooth(double value) { double t = clamp(value, 0, 1); return t * t * (3 - 2 * t); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
