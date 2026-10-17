package fr.frabous.stellar.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Custom eight-rail satellite cannon. Local +X is the firing direction; muzzle X = 58.95. */
public final class OrbitalRailgunModel {
    private static final int STRIDE = 5;
    private static final float[] MESH = loadMesh();
    private static final float[][] PART_CENTERS = partCenters();
    private static final float[] FACE_SHADE = faceShades();
    private static final double[] EMISSION_STOPS = {0, 0.22, 0.44, 0.56, 0.78, 1};
    private static final int[] EMISSION_COLORS = {0xFF203C, 0xFF608B, 0xFFF4FA, 0xFFF4FA, 0xFF608B, 0xFF203C};
    private static final float[] EMISSION_ALPHA = {0, 0.65F, 1, 1, 0.65F, 0};
    private static final int[] BODY_COLORS = {0xFF1834, 0xFF284B, 0xFF7396, 0xFF7396, 0xFF284B, 0xFF1834};
    private static final double[] JUNCTION_RADII = {0, 0.18, 0.6, 1.9};
    private static final int[] JUNCTION_COLORS = {0xFFF4FA, 0xFFF4FA, 0xFF608B, 0xFF203C};
    private static final float[] JUNCTION_ALPHAS = {1, 1, 0.65F, 0};

    private OrbitalRailgunModel() { }

    /** Charge and firing are normalized intensities. The caller supplies the model transform. */
    public static void render(double ageTicks, double charge, double firing) {
        charge = Math.max(0, Math.min(1, charge));
        firing = Math.max(0, Math.min(1, firing));
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // Raw GL changes are bracketed so Minecraft's cached GL state stays consistent.
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < MESH.length; i += STRIDE) {
                int color = (int) MESH[i + 3];
                if (MESH[i + 4] == 2) color = 0x64202C;
                float shade = FACE_SHADE[i / (4 * STRIDE)];
                color = ((int) ((color >> 16 & 255) * shade) << 16)
                        | ((int) ((color >> 8 & 255) * shade) << 8)
                        | (int) ((color & 255) * shade);
                modelVertex(b, i, color, 1, ageTicks);
            }
            Tessellator.getInstance().draw();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            emission(ageTicks, charge, firing);
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    /** Geometry only, in the same model space as render; the caller owns all GL state. */
    static void emission(double ageTicks, double charge, double firing) {
        charge = Math.max(0, Math.min(1, charge));
        firing = Math.max(0, Math.min(1, firing));
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < MESH.length; i += 4 * STRIDE) {
            if (MESH[i + 4] != 2) continue;
            chargeStrip(b, i, ageTicks, charge, firing);
        }
        outerChargeStrips(b, ageTicks, charge, firing);
        chamberArcs(b, ageTicks, charge, firing);
        muzzleConvergence(b, ageTicks);
        Tessellator.getInstance().draw();
    }

    /** Streaks spiral into the muzzle during the inhale, then a white core swells and releases. */
    private static void muzzleConvergence(BufferBuilder b, double age) {
        double inhale = RailgunVisualTiming.inhale(age);
        double release = age < RailgunVisualTiming.FIRE ? 0 : Math.exp(-(age - RailgunVisualTiming.FIRE) * 0.6);
        double[] from = new double[3], to = new double[3];
        if (inhale > 0) {
            for (int i = 0; i < 28; i++) {
                double travel = (i * 0.6180339 + age * 0.42) % 1;
                double angle = i * 2.39996323 + travel * 1.4;
                double reach = (9 + seeded(401 + i) * 22) * (1 - travel);
                double x = 59 - (seeded(503 + i) * 34) * (1 - travel);
                from[0] = x; from[1] = Math.cos(angle) * reach; from[2] = Math.sin(angle) * reach;
                double inner = reach * 0.55, nextAngle = angle + 0.35;
                to[0] = x + (61 - x) * 0.45; to[1] = Math.cos(nextAngle) * inner; to[2] = Math.sin(nextAngle) * inner;
                float alpha = (float) (inhale * Math.sin(travel * Math.PI) * 0.9);
                electricalSegment(b, from, to, 0.32, alpha);
            }
        }
        double core = inhale * 2.6 + release * 6;
        if (core > 0.05) {
            from[0] = 60.5; from[1] = 0; from[2] = 0;
            junctionNode(b, from, (float) Math.min(1, inhale + release), core);
        }
    }

    private static void chargeStrip(BufferBuilder b, int index, double age, double charge, double firing) {
        // Sample the front along X, rather than interpolating alpha over an entire 93-unit rail.
        boolean alongX = Math.abs(MESH[index + STRIDE] - MESH[index])
                > Math.abs(MESH[index + 3 * STRIDE] - MESH[index]);
        int next = index + (alongX ? STRIDE : 3 * STRIDE);
        int across = index + (alongX ? 3 * STRIDE : STRIDE);
        int opposite = index + 2 * STRIDE;
        int slices = Math.max(1, (int) Math.ceil(Math.abs(MESH[next] - MESH[index]) / 4));
        for (int slice = 0; slice < slices; slice++) {
            double start = slice / (double) slices, end = (slice + 1) / (double) slices;
            for (int band = 0; band < EMISSION_STOPS.length - 1; band++) {
                stripVertex(b, index, across, next, opposite, band, start, age, charge, firing);
                stripVertex(b, index, across, next, opposite, alongX ? band : band + 1,
                        alongX ? end : start, age, charge, firing);
                stripVertex(b, index, across, next, opposite, band + 1, end, age, charge, firing);
                stripVertex(b, index, across, next, opposite, alongX ? band + 1 : band,
                        alongX ? start : end, age, charge, firing);
            }
        }
    }

    private static void stripVertex(BufferBuilder b, int from, int to, int nextFrom, int nextTo,
                                    int stop, double along, double age, double charge, double firing) {
        double t = EMISSION_STOPS[stop];
        double x = MESH[from] + (MESH[to] - MESH[from]) * t;
        double y = MESH[from + 1] + (MESH[to + 1] - MESH[from + 1]) * t;
        double z = MESH[from + 2] + (MESH[to + 2] - MESH[from + 2]) * t;
        x += (MESH[nextFrom] + (MESH[nextTo] - MESH[nextFrom]) * t - x) * along;
        y += (MESH[nextFrom + 1] + (MESH[nextTo + 1] - MESH[nextFrom + 1]) * t - y) * along;
        z += (MESH[nextFrom + 2] + (MESH[nextTo + 2] - MESH[nextFrom + 2]) * t - z) * along;
        float alpha = (float) Math.min(1, (chargeLevel(x, charge)
                * (0.72 + 0.08 * Math.sin(age * 0.23 - x * 0.14)) + coilPulse(x, age) * charge * 0.65)
                * inhaleDim(x, age) + firing * heat(age));
        modelVertex(b, from, x, y, z, EMISSION_COLORS[stop], alpha * EMISSION_ALPHA[stop], age);
    }

    private static double chargeLevel(double x, double charge) {
        return smooth((charge - (x + 65) / 124) * 12);
    }

    /** Bright bands travel breech-to-muzzle like sequenced coils; a second band joins at high charge. */
    private static double coilPulse(double x, double age) {
        double phase = RailgunVisualTiming.coilPhase(age), pulse = 0;
        for (int band = 0; band < 2; band++) {
            if (band == 1 && RailgunVisualTiming.charge(age) < 0.55) break;
            double head = -66 + ((phase + band * 0.5) % 1) * 128, d = (x - head) / 5.5;
            pulse += Math.exp(-d * d);
        }
        return pulse;
    }

    /** The body darkens while the muzzle drinks the stored energy, so the release reads by contrast. */
    private static double inhaleDim(double x, double age) {
        return 1 - RailgunVisualTiming.inhale(age) * 0.7 * smooth((52 - x) / 30);
    }

    private static double heat(double age) {
        double t = Math.max(0, age - RailgunVisualTiming.FIRE);
        return 0.45 + 0.55 * Math.exp(-t * 0.35);
    }

    private static void outerChargeStrips(BufferBuilder b, double age, double charge, double firing) {
        for (int rail = 0; rail < 8; rail++) {
            double angle = rail * Math.PI / 4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int section = 0; section < 14; section++) {
                // Rear armor stays fixed; the panel strips follow each opened rail's part center.
                boolean rear = section < 4;
                double x = rear ? -62 + section * 4 : -34.5 + (section - 4) * 9;
                double end = x + (rear ? 3.5 : 8.3);
                double extension = RailgunVisualTiming.railOpening((x + end) * 0.5, age) * 0.55;
                double radial = rear ? 14.79 : 12.22 + 10.85 * extension;
                double offset = rear ? 2.55 : 2.1;
                for (int side = -1; side <= 1; side += 2) {
                    for (int band = 0; band < EMISSION_STOPS.length - 1; band++) {
                        for (int corner = 0; corner < 4; corner++) {
                            int stop = band + (corner >= 2 ? 1 : 0);
                            double localX = corner == 0 || corner == 3 ? x : end;
                            double across = side * offset + (EMISSION_STOPS[stop] * 2 - 1) * 0.7;
                            double y = cos * radial - sin * across;
                            double z = sin * radial + cos * across;
                            float alpha = (float) Math.min(1, (chargeLevel(localX, charge) * 0.8
                                    * (0.94 + 0.06 * Math.sin(age * 0.19 - localX * 0.1))
                                    + coilPulse(localX, age) * charge * 0.5) * inhaleDim(localX, age)
                                    + firing * 0.5 * heat(age));
                            EffectMesh.vertex(b, localX, y, z, BODY_COLORS[stop], alpha * EMISSION_ALPHA[stop]);
                        }
                    }
                }
            }
        }
    }

    private static void modelVertex(BufferBuilder b, int index, int color, float alpha, double age) {
        modelVertex(b, index, MESH[index], MESH[index + 1], MESH[index + 2], color, alpha, age);
    }

    private static void modelVertex(BufferBuilder b, int index, double x, double y, double z,
                                    int color, float alpha, double age) {
        float[] center = PART_CENTERS[index / (24 * STRIDE)];
        double radial = Math.hypot(center[1], center[2]);
        // Open the segmented forward rails to expose the charged bore for the chamber close-up.
        double extension = center[0] >= -32 && radial > 4 && radial < 18
                ? RailgunVisualTiming.railOpening(center[0], age) * 0.55 : 0;
        EffectMesh.vertex(b, x, y + center[1] * extension, z + center[2] * extension, color, alpha);
    }

    private static float[][] partCenters() {
        float[][] centers = new float[MESH.length / (24 * STRIDE)][3];
        for (int part = 0; part < centers.length; part++) {
            for (int vertex = 0; vertex < 24; vertex++) {
                int index = (part * 24 + vertex) * STRIDE;
                for (int axis = 0; axis < 3; axis++) centers[part][axis] += MESH[index + axis] / 24;
            }
        }
        return centers;
    }

    private static float[] faceShades() {
        float[] shades = new float[MESH.length / (4 * STRIDE)];
        for (int face = 0; face < shades.length; face++) {
            int i = face * 4 * STRIDE;
            double ax = MESH[i + STRIDE] - MESH[i];
            double ay = MESH[i + STRIDE + 1] - MESH[i + 1];
            double az = MESH[i + STRIDE + 2] - MESH[i + 2];
            double bx = MESH[i + 3 * STRIDE] - MESH[i];
            double by = MESH[i + 3 * STRIDE + 1] - MESH[i + 1];
            double bz = MESH[i + 3 * STRIDE + 2] - MESH[i + 2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            shades[face] = length == 0 ? 1 : (float) (0.88
                    + 0.12 * Math.abs((nx * 0.35 + ny * 0.8 + nz * 0.48) / length));
        }
        return shades;
    }

    private static void chamberArcs(BufferBuilder b, double age, double charge, double firing) {
        float alpha = (float) Math.min(1, charge * 0.75 + firing);
        if (alpha <= 0) return;
        double spin = RailgunVisualTiming.collarSpin(age);
        for (int station = 0; station < 4; station++) {
            double x = 53 - station * 22;
            double r = 6.2 + 7 * RailgunVisualTiming.railOpening(x, age) * 0.55;
            double width = station == 0 ? 1.5 : 0.8;
            // Collars ignite one after another from the muzzle back, then flare together at release.
            double ignition = smooth((charge - station * 0.12) * 4);
            float collarAlpha = (float) (alpha * (station == 0 ? 1 : 0.65) * Math.min(1, ignition + firing)
                    * (1 - RailgunVisualTiming.inhale(age) * 0.5));
            for (int segment = 0; segment < 64; segment++) {
                if ((segment / 5 + station) % 3 == 0) continue;
                double a = segment * Math.PI / 32 + spin * (station % 2 == 0 ? 1 : -1) + station * 0.3;
                double n = a + Math.PI / 32;
                ribbon(b, x, a, x, n, r, width, collarAlpha);
                collarSleeve(b, x, a, n, r + width * 0.5, width, collarAlpha);
            }
        }
        double discharge = RailgunVisualTiming.discharge(age);
        if (discharge <= 0 || charge <= 0) return;
        double radius = 6.92 + 7 * RailgunVisualTiming.railOpening(20, age) * 0.55;
        double[] pointA = new double[3], pointB = new double[3];
        int bolts = discharge > 0.6 ? 3 : 2;
        for (int bolt = 0; bolt < bolts; bolt++) {
            int seed = 173 + bolt * 97;
            // Early arcs strike intermittently; the gaps close as the stored charge rises.
            if (seeded(seed + (int) Math.floor(age * 0.8) * 7919) > 0.25 + discharge * 0.9) continue;
            double angle = bolt * Math.PI * 2 / 3 + Math.PI / 4;
            double gap = Math.PI * 3 / 8;
            double spread = (bolt == 0 ? -0.4 : 0.4) * radius;
            double[] junction = {9 + bolt * 20 + noise(seed + 1, age) * 1.2,
                    Math.cos(gap) * radius * 0.18 - Math.sin(gap) * spread + noise(seed + 2, age) * 0.6,
                    Math.sin(gap) * radius * 0.18 + Math.cos(gap) * spread + noise(seed + 3, age) * 0.6};
            float intensity = (float) (smooth(discharge * 1.7) * (0.86 + noise(seed + 5, age) * 0.12));
            for (int branch = 0; branch < 3; branch++) {
                double railAngle = angle + (branch == 0 ? 0 : branch == 1 ? Math.PI / 4 : Math.PI);
                double[] rail = {junction[0] - 7 + branch * 6 + seeded(seed + 7 + branch) * 3,
                        Math.cos(railAngle) * radius, Math.sin(railAngle) * radius};
                double[] previous = rail;
                for (int segment = 1; segment <= 5; segment++) {
                    double[] next = (segment & 1) == 1 ? pointA : pointB;
                    arcPoint(rail, junction, segment / 5.0, seed + branch * 31, segment, age, next);
                    electricalSegment(b, previous, next, branch == 2 ? 0.95 : 1.25, intensity);
                    previous = next;
                }
            }
            junctionNode(b, junction, intensity);
        }
    }

    private static void arcPoint(double[] from, double[] to, double t, int seed, int segment, double age,
                                 double[] point) {
        double taper = 4 * t * (1 - t);
        for (int axis = 0; axis < 3; axis++) {
            int key = seed + segment * 19 + axis * 43;
            double offset = (seeded(key) * 2 - 1) * 2.6 + noise(key + 11, age) * 1.2;
            point[axis] = from[axis] + (to[axis] - from[axis]) * t + offset * taper;
        }
    }

    private static void electricalSegment(BufferBuilder b, double[] from, double[] to,
                                          double scale, float alpha) {
        if (alpha <= 0) return;
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001) return;
        dx /= length;
        dy /= length;
        dz /= length;
        double ux = -dz, uy = 0, uz = dx;
        double sideLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (sideLength < 0.0001) {
            ux = 1;
            uz = 0;
            sideLength = 1;
        }
        ux /= sideLength;
        uy /= sideLength;
        uz /= sideLength;
        double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        // Crossed ribbons keep thin electrical cores readable from both chamber camera sides.
        for (int plane = 0; plane < 2; plane++) {
            double sx = plane == 0 ? ux : vx, sy = plane == 0 ? uy : vy, sz = plane == 0 ? uz : vz;
            for (int band = 0; band < EMISSION_STOPS.length - 1; band++) {
                double inner = (EMISSION_STOPS[band] * 2 - 1) * 0.5 * scale;
                double outer = (EMISSION_STOPS[band + 1] * 2 - 1) * 0.5 * scale;
                EffectMesh.vertex(b, from[0] + sx * inner, from[1] + sy * inner, from[2] + sz * inner,
                        EMISSION_COLORS[band], alpha * EMISSION_ALPHA[band]);
                EffectMesh.vertex(b, to[0] + sx * inner, to[1] + sy * inner, to[2] + sz * inner,
                        EMISSION_COLORS[band], alpha * EMISSION_ALPHA[band]);
                EffectMesh.vertex(b, to[0] + sx * outer, to[1] + sy * outer, to[2] + sz * outer,
                        EMISSION_COLORS[band + 1], alpha * EMISSION_ALPHA[band + 1]);
                EffectMesh.vertex(b, from[0] + sx * outer, from[1] + sy * outer, from[2] + sz * outer,
                        EMISSION_COLORS[band + 1], alpha * EMISSION_ALPHA[band + 1]);
            }
        }
    }

    private static void junctionNode(BufferBuilder b, double[] point, float alpha) {
        junctionNode(b, point, alpha, 1);
    }

    private static void junctionNode(BufferBuilder b, double[] point, float alpha, double scale) {
        if (alpha <= 0) return;
        for (int plane = 0; plane < 3; plane++) {
            int first = plane, second = (plane + 1) % 3;
            for (int segment = 0; segment < 8; segment++) {
                double a = segment * Math.PI / 4, next = a + Math.PI / 4;
                for (int band = 0; band < JUNCTION_RADII.length - 1; band++) {
                    for (int corner = 0; corner < 4; corner++) {
                        int stop = band + (corner >= 2 ? 1 : 0);
                        double angle = corner == 0 || corner == 3 ? a : next;
                        double x = point[0], y = point[1], z = point[2];
                        double firstOffset = Math.cos(angle) * JUNCTION_RADII[stop] * scale;
                        double secondOffset = Math.sin(angle) * JUNCTION_RADII[stop] * scale;
                        if (first == 0) x += firstOffset;
                        else if (first == 1) y += firstOffset;
                        else z += firstOffset;
                        if (second == 0) x += secondOffset;
                        else if (second == 1) y += secondOffset;
                        else z += secondOffset;
                        EffectMesh.vertex(b, x, y, z, JUNCTION_COLORS[stop], alpha * JUNCTION_ALPHAS[stop]);
                    }
                }
            }
        }
    }

    private static void collarSleeve(BufferBuilder b, double x, double a, double n,
                                      double radius, double width, float alpha) {
        // Axial thickness remains visible when the annular face is almost edge-on.
        for (int band = 0; band < EMISSION_STOPS.length - 1; band++) {
            double start = x + (EMISSION_STOPS[band] - 0.5) * width;
            double end = x + (EMISSION_STOPS[band + 1] - 0.5) * width;
            EffectMesh.vertex(b, start, Math.cos(a) * radius, Math.sin(a) * radius,
                    EMISSION_COLORS[band], alpha * EMISSION_ALPHA[band]);
            EffectMesh.vertex(b, start, Math.cos(n) * radius, Math.sin(n) * radius,
                    EMISSION_COLORS[band], alpha * EMISSION_ALPHA[band]);
            EffectMesh.vertex(b, end, Math.cos(n) * radius, Math.sin(n) * radius,
                    EMISSION_COLORS[band + 1], alpha * EMISSION_ALPHA[band + 1]);
            EffectMesh.vertex(b, end, Math.cos(a) * radius, Math.sin(a) * radius,
                    EMISSION_COLORS[band + 1], alpha * EMISSION_ALPHA[band + 1]);
        }
    }

    private static double seeded(int seed) {
        int value = seed * 0x45d9f3b;
        value = (value ^ value >>> 16) * 0x45d9f3b;
        return ((value ^ (value >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    private static double noise(int seed, double age) {
        double time = age * 0.35;
        int frame = (int) Math.floor(time);
        double blend = smooth(time - frame);
        double from = seeded(seed + frame * 1013), to = seeded(seed + (frame + 1) * 1013);
        return (from + (to - from) * blend) * 2 - 1;
    }

    private static double smooth(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }

    private static void ribbon(BufferBuilder b, double x, double a, double nextX, double n,
                               double radius, double width, float alpha) {
        for (int band = 0; band < EMISSION_STOPS.length - 1; band++) {
            double inner = radius + width * EMISSION_STOPS[band];
            double outer = radius + width * EMISSION_STOPS[band + 1];
            int innerColor = EMISSION_COLORS[band], outerColor = EMISSION_COLORS[band + 1];
            float innerAlpha = alpha * EMISSION_ALPHA[band], outerAlpha = alpha * EMISSION_ALPHA[band + 1];
            EffectMesh.vertex(b, x, Math.cos(a) * inner, Math.sin(a) * inner, innerColor, innerAlpha);
            EffectMesh.vertex(b, nextX, Math.cos(n) * inner, Math.sin(n) * inner, innerColor, innerAlpha);
            EffectMesh.vertex(b, nextX, Math.cos(n) * outer, Math.sin(n) * outer, outerColor, outerAlpha);
            EffectMesh.vertex(b, x, Math.cos(a) * outer, Math.sin(a) * outer, outerColor, outerAlpha);
        }
    }

    private static float[] loadMesh() {
        String path = "/assets/stellar/models/orbital_railgun.mesh";
        InputStream stream = OrbitalRailgunModel.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing orbital railgun mesh: " + path);
        try (DataInputStream input = new DataInputStream(stream)) {
            if (input.readInt() != 0x53524731) throw new IOException("Invalid orbital railgun mesh header");
            int count = input.readInt();
            if (count <= 0 || count > 100000 || count % 24 != 0) throw new IOException("Invalid railgun vertex count");
            float[] result = new float[count * STRIDE];
            for (int i = 0; i < result.length; i += STRIDE) {
                result[i] = input.readFloat();
                result[i + 1] = input.readFloat();
                result[i + 2] = input.readFloat();
                result[i + 3] = input.readInt();
                result[i + 4] = input.readUnsignedByte();
            }
            if (input.read() != -1) throw new IOException("Trailing railgun mesh data");
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load orbital railgun mesh", exception);
        }
    }
}
