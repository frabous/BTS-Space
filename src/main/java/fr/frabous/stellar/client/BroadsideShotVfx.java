package fr.frabous.stellar.client;

import fr.frabous.stellar.api.BroadsideBatteries;
import fr.frabous.stellar.api.CarrierShot;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * One shell's life in the world, in world coordinates under WorldEffects' additive state: designation
 * laser while the battery lays, muzzle blast, coilgun slug and tracer in flight, then flash, fireball,
 * sparks, ground shock, dust column and a scorch decal. Terrain is never modified.
 */
final class BroadsideShotVfx {
    private static final double TAU = Math.PI * 2;
    private static final int CORE = 0xEAF8FF, PLASMA = 0x7FE6FF, LASER = 0xFFB45A, FIRE = 0xFF9A4A, SHOCK = 0xD6F2FF;
    private static final double MUZZLE_SMOKE = 90, DUST = 200, SCORCH = 240;
    private static final FloatBuffer VIEW = BufferUtils.createFloatBuffer(16);

    private BroadsideShotVfx() { }

    static void draw(BroadsideFireControl.Fleet fleet, double time, boolean emission) {
        if (fleet.shots.isEmpty()) return;
        double[] eye = eye();
        MoonAttractVfx.Sprites fire = new MoonAttractVfx.Sprites(MoonAttractVfx.FIRE, true);
        for (CarrierShot shot : fleet.shots) {
            double fired = time - shot.fire, landed = time - shot.impact;
            if (fired < 0) { designate(fleet, shot, time); continue; }
            muzzle(shot, fired);
            if (landed < 0) shell(shot, fired, eye);
            else impact(shot, landed, fire, emission);
        }
        fire.draw();
        if (emission) return;
        scorch(fleet, time);
        MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (CarrierShot shot : fleet.shots) smoke(shot, time, smoke);
        smoke.draw();
    }

    /** Rangefinder lasers from both pods of the laying battery onto the point it will hit. */
    private static void designate(BroadsideFireControl.Fleet fleet, CarrierShot shot, double time) {
        double progress = (time - shot.issued) / Math.max(1, shot.fire - shot.issued);
        double[] ring = BroadsideBatteries.ring(fleet.frame, shot.battery);
        double yaw = Math.atan2(-(shot.hitZ - ring[2]), shot.hitX - ring[0]);
        float blink = (float) (0.55 + 0.45 * Math.sin(time * 2.4));
        GL11.glLineWidth(2);
        for (int side = -1; side <= 1; side += 2) {
            double x = ring[0] + Math.cos(yaw) * 0.9 + Math.sin(yaw) * side * 7.45, y = ring[1] - 4.3;
            double z = ring[2] - Math.sin(yaw) * 0.9 + Math.cos(yaw) * side * 7.45;
            EffectMesh.line(x, y, z, shot.hitX, shot.hitY + 0.2, shot.hitZ, LASER, 0.75F * blink);
        }
        double radius = 7 - 4.5 * Math.min(1, progress);
        EffectMesh.glowRing(shot.hitX, shot.hitY + 0.15, shot.hitZ, radius, 0.35, LASER, 0.8F * blink);
        MoonAttractVfx.glow(shot.hitX, shot.hitY + 0.6, shot.hitZ, 2.5, LASER, 0.6F * blink);
    }

    private static void muzzle(CarrierShot shot, double t) {
        if (t >= 10) return;
        double[] dir = direction(shot);
        float blast = (float) Math.exp(-t / 1.2);
        if (t < 5) {
            EffectMesh.billboard(shot.x, shot.y, shot.z, 1.5 + 7 * blast, CORE, blast);
            MoonAttractVfx.glow(shot.x, shot.y, shot.z, 12 + t * 2.5, PLASMA, 0.7F * (float) Math.exp(-t / 1.6));
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(shot.x, shot.y, shot.z);
            GL11.glRotated(Math.toDegrees(Math.atan2(-dir[2], dir[0])), 0, 1, 0);
            GL11.glRotated(Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dir[1])))), 0, 0, 1);
            if (t < 4) cone(7 + 9 * (1 - blast), 1.4 + 1.6 * (1 - blast), blast);
            // Muzzle shock: a ring in the plane across the bore, racing ahead of the gas.
            GL11.glTranslated(1.5 + t * 0.9, 0, 0);
            GL11.glRotated(90, 0, 0, 1);
            EffectMesh.glowRing(0, 0, 0, 1.2 + t * 1.6, 0.35 + t * 0.07, SHOCK, (float) (0.6 * (1 - t / 10)));
        } finally { GL11.glPopMatrix(); }
    }

    /** Muzzle cone along local +X: white throat, plasma sheath, and four short flash spikes across the bore. */
    private static void cone(double length, double radius, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int layer = 0; layer < 2; layer++) {
            double r = radius * (layer == 0 ? 0.45 : 1), l = length * (layer == 0 ? 0.55 : 1);
            int color = layer == 0 ? CORE : PLASMA;
            float a = alpha * (layer == 0 ? 0.85F : 0.35F);
            for (int i = 0; i < 12; i++) {
                double a0 = i * TAU / 12, a1 = (i + 1) * TAU / 12;
                EffectMesh.vertex(b, 0, Math.cos(a0) * r, Math.sin(a0) * r, color, a);
                EffectMesh.vertex(b, 0, Math.cos(a1) * r, Math.sin(a1) * r, color, a);
                EffectMesh.vertex(b, l, 0, 0, PLASMA, 0);
            }
        }
        for (int spike = 0; spike < 4; spike++) {
            double a = spike * TAU / 4 + Math.PI / 4, c = Math.cos(a), s = Math.sin(a), reach = radius * 3.2;
            EffectMesh.vertex(b, 0.6, c * 0.4 - s * 0.25, s * 0.4 + c * 0.25, CORE, alpha);
            EffectMesh.vertex(b, 0.6, c * 0.4 + s * 0.25, s * 0.4 - c * 0.25, CORE, alpha);
            EffectMesh.vertex(b, 0.8, c * reach, s * reach, PLASMA, 0);
        }
        Tessellator.getInstance().draw();
    }

    /** Coilgun slug: white core, plasma halo, a hot tracer behind it and a fading ionisation trail to the muzzle. */
    private static void shell(CarrierShot shot, double t, double[] eye) {
        double flight = Math.max(1, shot.impact - shot.fire), u = Math.min(1, t / flight);
        double dx = shot.hitX - shot.x, dy = shot.hitY - shot.y, dz = shot.hitZ - shot.z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] head = {shot.x + dx * u, shot.y + dy * u, shot.z + dz * u};
        double tail = Math.max(0, u - 36 / Math.max(1, length));
        double[] back = {shot.x + dx * tail, shot.y + dy * tail, shot.z + dz * tail};
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        ribbon(b, eye, new double[] {shot.x, shot.y, shot.z}, head, 0.15, 0.3, PLASMA, PLASMA, 0, (float) (0.22 * Math.exp(-t / 12)));
        ribbon(b, eye, back, head, 0.2, 0.75, PLASMA, CORE, 0, 0.9F);
        Tessellator.getInstance().draw();
        EffectMesh.billboard(head[0], head[1], head[2], 1.7, CORE, 1);
        MoonAttractVfx.glow(head[0], head[1], head[2], 5.5, PLASMA, 0.75F);
    }

    private static void impact(CarrierShot shot, double t, MoonAttractVfx.Sprites fire, boolean emission) {
        boolean ground = shot.kind == CarrierShot.TERRAIN;
        long seed = shot.fire * 31 + shot.battery * 7 + shot.barrel;
        if (t < 8) {
            float flash = (float) Math.exp(-t / 1.5);
            EffectMesh.billboard(shot.hitX, shot.hitY + 1.5, shot.hitZ, 14 * (1 + t * 0.25), CORE, flash);
            MoonAttractVfx.glow(shot.hitX, shot.hitY + 3, shot.hitZ, 28 + t * 3, PLASMA, 0.55F * (float) Math.exp(-t / 3));
            MoonAttractVfx.glow(shot.hitX, shot.hitY + 2, shot.hitZ, 16 + t * 2, FIRE, 0.5F * (float) Math.exp(-t / 5));
        }
        if (t < 18) {
            float shock = (float) (1 - t / 18);
            double y = shot.hitY + (ground ? 0.25 : 0);
            EffectMesh.glowRing(shot.hitX, y, shot.hitZ, 1.5 + t * 1.9, 0.5 + t * 0.08, SHOCK, 0.75F * shock);
            EffectMesh.glowRing(shot.hitX, y + 0.1, shot.hitZ, 1 + t * 1.25, 0.6, FIRE, 0.5F * shock);
        }
        if (ground && t < 140) EffectMesh.glowRing(shot.hitX, shot.hitY + 0.08, shot.hitZ, 3.2, 0.8, FIRE, 0.55F * (float) Math.exp(-t / 35));
        if (t < 26) {
            // Fireball cells rise and cool from white through orange.
            double grow = 1 - Math.exp(-t / 3), cool = Math.exp(-t / 9);
            int flame = cool > 0.6 ? 0xFFE2B0 : cool > 0.3 ? 0xFFA048 : 0xFF6A2E;
            for (int i = 0; i < 12; i++) {
                double a = i * 2.39996 + CarrierRenderer.seed((int) seed + i), r = 6.5 * grow * (0.4 + 0.6 * CarrierRenderer.seed((int) seed + i + 40));
                double lift = (ground ? 1.5 : 0) + 2.5 * grow * CarrierRenderer.seed((int) seed + i + 80) + t * 0.12;
                double size = 2.5 + 3.5 * grow;
                fire.add(shot.hitX + Math.cos(a) * r, shot.hitY + lift, shot.hitZ + Math.sin(a) * r, size, size * 1.1, i + t * 0.05,
                        flame, (float) (cool * (emission ? 0.2 : 0.3)), i);
            }
            sparks(shot, t, seed);
        }
    }

    /** Ballistic streaks thrown out of the crater; they cool as they fall. */
    private static void sparks(CarrierShot shot, double t, long seed) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        GL11.glLineWidth(2);
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        float alpha = (float) (1 - t / 26);
        for (int i = 0; i < 28; i++) {
            double a = CarrierRenderer.seed((int) seed + i * 3) * TAU, speed = 0.7 + 0.9 * CarrierRenderer.seed((int) seed + i * 3 + 1);
            double up = 0.5 + 0.9 * CarrierRenderer.seed((int) seed + i * 3 + 2), t0 = Math.max(0, t - 1.2);
            double y1 = up * t - 0.04 * t * t, y0 = up * t0 - 0.04 * t0 * t0;
            if (y1 < -0.5) continue;
            int color = RigMesh.mix(CORE, FIRE, t / 8);
            EffectMesh.vertex(b, shot.hitX + Math.cos(a) * speed * t0, shot.hitY + y0, shot.hitZ + Math.sin(a) * speed * t0, color, 0);
            EffectMesh.vertex(b, shot.hitX + Math.cos(a) * speed * t, shot.hitY + y1, shot.hitZ + Math.sin(a) * speed * t, color, alpha);
        }
        Tessellator.getInstance().draw();
    }

    /** Dark disc under each terrain impact, drawn with ordinary blending and restored to additive. */
    private static void scorch(BroadsideFireControl.Fleet fleet, double time) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            for (CarrierShot shot : fleet.shots) {
                double t = time - shot.impact;
                if (shot.kind != CarrierShot.TERRAIN || t < 0 || t >= SCORCH) continue;
                float alpha = (float) (0.6 * Math.min(1, t / 2) * (1 - t / SCORCH));
                double y = shot.hitY + 0.03;
                for (int i = 0; i < 16; i++) {
                    double a0 = i * TAU / 16, a1 = (i + 1) * TAU / 16;
                    double r0 = 4.6 * (0.8 + 0.2 * CarrierRenderer.seed((int) shot.fire + i)), r1 = 4.6 * (0.8 + 0.2 * CarrierRenderer.seed((int) shot.fire + (i + 1) % 16));
                    EffectMesh.vertex(b, shot.hitX, y, shot.hitZ, 0x0E0B08, alpha);
                    EffectMesh.vertex(b, shot.hitX + Math.cos(a0) * r0, y, shot.hitZ + Math.sin(a0) * r0, 0x2A2420, 0);
                    EffectMesh.vertex(b, shot.hitX + Math.cos(a1) * r1, y, shot.hitZ + Math.sin(a1) * r1, 0x2A2420, 0);
                }
            }
            Tessellator.getInstance().draw();
        } finally { GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); }
    }

    /** Ionised vapour from the muzzle, then the dust column of the impact or a dark airburst cloud. */
    private static void smoke(CarrierShot shot, double time, MoonAttractVfx.Sprites smoke) {
        double fired = time - shot.fire, landed = time - shot.impact;
        int seed = (int) shot.fire * 13 + shot.battery * 5 + shot.barrel;
        if (fired >= 0 && fired < MUZZLE_SMOKE) {
            double[] dir = direction(shot);
            float alpha = (float) (0.3 * Math.min(1, fired / 2) * (1 - fired / MUZZLE_SMOKE));
            for (int i = 0; i < 7; i++) {
                double along = (1 + i * 1.7) * (1 + fired * 0.04), spread = (CarrierRenderer.seed(seed + i) - 0.5) * 3;
                double x = shot.x + dir[0] * along + fired * 0.03 + spread, y = shot.y + dir[1] * along + fired * 0.02;
                double z = shot.z + dir[2] * along + (CarrierRenderer.seed(seed + i + 20) - 0.5) * 3;
                double size = 1.2 + i * 0.25 + fired * 0.09;
                smoke.add(x, y, z, size, size, i + fired * 0.01, 0xB9C2CC, alpha, i);
            }
        }
        if (landed < 0 || landed >= DUST) return;
        double grow = 1 - Math.exp(-landed / 25);
        float fade = (float) (Math.min(1, landed / 3) * (1 - landed / DUST));
        if (shot.kind != CarrierShot.TERRAIN) {
            for (int i = 0; i < 6; i++) {
                double a = i * TAU / 6 + CarrierRenderer.seed(seed + i), r = 2.5 * grow;
                smoke.add(shot.hitX + Math.cos(a) * r, shot.hitY + (CarrierRenderer.seed(seed + i + 9) - 0.5) * 3, shot.hitZ + Math.sin(a) * r,
                        3 + 4 * grow, 3 + 4 * grow, i, 0x3A3A40, 0.45F * fade, i);
            }
            return;
        }
        for (int i = 0; i < 6; i++) {
            double a = i * TAU / 6 + CarrierRenderer.seed(seed + i), r = 2 + 7 * grow;
            smoke.add(shot.hitX + Math.cos(a) * r, shot.hitY + 1.2 + grow, shot.hitZ + Math.sin(a) * r, 2.5 + 3 * grow, 1.8 + 2 * grow,
                    i, 0x8A8178, 0.42F * fade, i);
        }
        for (int i = 0; i < 10; i++) {
            double h = 1.5 + i * (0.8 + 1.6 * grow), wobble = (CarrierRenderer.seed(seed + i + 30) - 0.5) * 2.5;
            double size = 2.2 + grow * (2.5 + 0.4 * i);
            smoke.add(shot.hitX + wobble + landed * 0.03, shot.hitY + h, shot.hitZ + wobble * 0.7, size, size,
                    i * 0.7 + landed * 0.01, i < 4 ? 0x5E544B : 0x6E6862, 0.5F * fade, i);
        }
    }

    /** Camera-facing strip from a to c; widths and colours interpolate along it. */
    static void ribbon(BufferBuilder b, double[] eye, double[] a, double[] c, double wa, double wc,
                               int ca, int cc, float aa, float ac) {
        double dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2];
        double vx = eye[0] - (a[0] + c[0]) / 2, vy = eye[1] - (a[1] + c[1]) / 2, vz = eye[2] - (a[2] + c[2]) / 2;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx, length = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0E-9) return;
        sx /= length; sy /= length; sz /= length;
        EffectMesh.vertex(b, a[0] - sx * wa, a[1] - sy * wa, a[2] - sz * wa, ca, aa);
        EffectMesh.vertex(b, a[0] + sx * wa, a[1] + sy * wa, a[2] + sz * wa, ca, aa);
        EffectMesh.vertex(b, c[0] + sx * wc, c[1] + sy * wc, c[2] + sz * wc, cc, ac);
        EffectMesh.vertex(b, c[0] - sx * wc, c[1] - sy * wc, c[2] - sz * wc, cc, ac);
    }

    private static double[] direction(CarrierShot shot) {
        double dx = shot.hitX - shot.x, dy = shot.hitY - shot.y, dz = shot.hitZ - shot.z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return length < 1.0E-9 ? new double[] {1, 0, 0} : new double[] {dx / length, dy / length, dz / length};
    }

    /** Camera position in the current model space, from the modelview matrix (rotation plus translation). */
    static double[] eye() {
        VIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, VIEW);
        double tx = VIEW.get(12), ty = VIEW.get(13), tz = VIEW.get(14);
        return new double[] {-(VIEW.get(0) * tx + VIEW.get(1) * ty + VIEW.get(2) * tz),
                -(VIEW.get(4) * tx + VIEW.get(5) * ty + VIEW.get(6) * tz),
                -(VIEW.get(8) * tx + VIEW.get(9) * ty + VIEW.get(10) * tz)};
    }
}
