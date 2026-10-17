package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.KineticRainRules.*;

import fr.frabous.stellar.api.LaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * SS-03C effects in world coordinates under WorldEffects' additive state. Each mark shows the rod's true
 * blast radius and counts down to its impact; rail lasers designate it, the rod dives on it trailing
 * plasma and punches the cloud deck, then flash, spike, fireball, sparks, scorch and dust. The hammer
 * gets the same treatment at scale. Terrain is never modified.
 */
final class KineticRainVfx {
    private static final double TAU = Math.PI * 2;
    private static final int CORE = 0xEAF8FF, PLASMA = 0x7FE6FF, LASER = 0xFFB45A, ALERT = 0xFF3B2E;
    private static final int FIRE = 0xFF9A4A, SHOCK = 0xD6F2FF, MOLTEN = 0xFFD08A;
    private static final double SCORCH = 220, DUST = 120, AFTERGLOW = 30, PUNCH = 24;

    private KineticRainVfx() { }

    static void draw(LaunchState state, CarrierFlight.Plan plan, double age, boolean emission) {
        float fade = (float) (1 - CarrierFlight.smooth((age - END + 40) / 40));
        if (fade <= 0) return;
        double[][] muzzles = KineticRainScene.muzzles(plan);
        double fallback = state.getTarget().getY() + 1;
        // Marks and ground heights of every rod with something to show this frame.
        double[][] rods = new double[COUNT][];
        for (int rod = 0; rod < COUNT; rod++) {
            double since = age - impact(rod);
            if (since < -MARK || since >= SCORCH) continue;
            double[] m = mark(state, rod);
            rods[rod] = new double[] {m[0], ground(m[0], m[1], fallback), m[1]};
        }
        double cx = state.getTarget().getX() + 0.5, cz = state.getTarget().getZ() + 0.5;
        double centre = ground(cx, cz, fallback);
        double[] eye = BroadsideShotVfx.eye();
        MoonAttractVfx.Sprites glows = new MoonAttractVfx.Sprites(MoonAttractVfx.GLOW, true);
        MoonAttractVfx.Sprites fire = new MoonAttractVfx.Sprites(MoonAttractVfx.FIRE, true);
        float level = fade * (emission ? 0.6F : 1);
        double cloud = cloud();

        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int rod = 0; rod < COUNT; rod++) {
            if (rods[rod] == null) continue;
            double since = age - impact(rod);
            if (since < 0) designation(b, eye, glows, rods[rod], -since, age, level);
            rod(b, eye, glows, muzzles[rod % ARMS], rods[rod], age - release(rod), cloud, fade);
            if (since >= 0) blast(b, eye, glows, fire, rods[rod], since, rod, emission, fade);
        }
        hammerZone(b, eye, glows, cx, centre, cz, age, level);
        hammerFall(b, eye, glows, state, plan, age, fade);
        hammerBlast(b, eye, glows, fire, cx, centre, cz, age - HAMMER_IMPACT, emission, fade);
        Tessellator.getInstance().draw();

        GL11.glLineWidth(2);
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int rod = 0; rod < COUNT; rod++) {
            if (rods[rod] == null) continue;
            double since = age - impact(rod);
            if (age < release(rod) && since >= -MARK) {
                double[] m = muzzles[rod % ARMS], r = rods[rod];
                float blink = (float) (0.55 + 0.45 * Math.sin(age * 2.4 + rod));
                EffectMesh.vertex(b, m[0], m[1], m[2], LASER, 0);
                EffectMesh.vertex(b, r[0], r[1] + 0.2, r[2], LASER, 0.6F * blink * level);
            }
            if (since >= 0 && since < 28) sparks(b, rods[rod], since, rod * 41, 24, 1, fade);
        }
        double hammerSince = age - HAMMER_IMPACT;
        if (hammerSince >= 0 && hammerSince < 50) sparks(b, new double[] {cx, centre, cz}, hammerSince / 1.8, 7919, 48, 3.2, fade);
        crosshair(b, plan, cx, centre, cz, age, level);
        Tessellator.getInstance().draw();

        for (int rod = 0; rod < COUNT; rod++) {
            double since = age - impact(rod);
            if (rods[rod] == null || since < 0 || since >= 8) continue;
            double[] r = rods[rod];
            EffectMesh.billboard(r[0], r[1] + 2, r[2], 18 * (1 + since * 0.3), CORE, fade * (float) Math.exp(-since / 1.6));
        }
        if (hammerSince >= 0 && hammerSince < 14)
            EffectMesh.billboard(cx, centre + 4, cz, 60 * (1 + hammerSince * 0.25), CORE, fade * (float) Math.exp(-hammerSince / 2.5));
        glows.draw();
        fire.draw();
        if (emission) return;

        scorch(rods, cx, centre, cz, age, fade);
        MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (int rod = 0; rod < COUNT; rod++) {
            double since = age - impact(rod);
            if (rods[rod] != null && since >= 0 && since < DUST) dust(smoke, rods[rod], since, rod * 13, 1, DUST, fade);
        }
        if (hammerSince >= 0) dust(smoke, new double[] {cx, centre, cz}, hammerSince, 977, 3.2, END - HAMMER_IMPACT, fade);
        smoke.draw();
    }

    /** Honest blast radius: tinted disc, rim, a ring closing on the centre, and a beacon visible from afar. */
    private static void designation(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, double[] r, double remaining,
                                    double age, float level) {
        double k = remaining / MARK;
        float appear = (float) CarrierFlight.smooth((MARK - remaining) / 6) * level;
        float blink = (float) (0.75 + 0.25 * Math.sin(age * (1.2 + 2.5 * (1 - k))));
        int color = RigMesh.mix(LASER, ALERT, 1 - k);
        double x = r[0], y = r[1], z = r[2];
        disc(b, x, y + 0.12, z, ROD_RADIUS, color, 0.08F * appear, 0.2F * appear);
        band(b, x, y + 0.15, z, ROD_RADIUS, 0.45, color, 0.85F * appear * blink, 0, 0);
        band(b, x, y + 0.16, z, ROD_RADIUS * k, 0.35, CORE, 0.6F * appear, 0, 0);
        BroadsideShotVfx.ribbon(b, eye, new double[] {x, y, z}, new double[] {x, y + 26, z}, 0.35, 0.05, color, color, 0.5F * appear, 0);
        glows.add(x, y + 0.6, z, 3, 3, 0, color, 0.55F * appear * blink, 0);
    }

    /** Rod in flight with its trail, the ionisation track it leaves, and the punch through the cloud deck. */
    private static void rod(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, double[] muzzle, double[] r,
                            double since, double cloud, float fade) {
        if (since < 0 || since >= FLIGHT + AFTERGLOW) return;
        double u = KineticRainScene.flight(since);
        float track = since < FLIGHT ? 0.16F : 0.16F * (float) Math.exp(-(since - FLIGHT) / 8);
        trail(b, eye, muzzle, r, 0, u, 8, 0.25, 0.35, PLASMA, PLASMA, 0.1F * fade, track * fade);
        if (since < FLIGHT) {
            trail(b, eye, muzzle, r, Math.max(0, u - 0.16), u, 5, 0.05, 0.5, PLASMA, CORE, 0, 0.95F * fade);
            double[] head = KineticRainScene.path(muzzle, r[0], r[1], r[2], u);
            glows.add(head[0], head[1], head[2], 2.2, 2.2, 0, CORE, 0.95F * fade, 0);
            glows.add(head[0], head[1], head[2], 5 + 5 * u, 5 + 5 * u, 0, RigMesh.mix(PLASMA, FIRE, u), 0.5F * fade, 0);
        }
        if (muzzle[1] <= cloud || r[1] + 4 >= cloud) return;
        double low = 0, high = 1;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) / 2;
            if (KineticRainScene.path(muzzle, r[0], r[1], r[2], mid)[1] > cloud) low = mid; else high = mid;
        }
        double t = since - FLIGHT * Math.pow(high, 1 / 1.4);
        if (t < 0 || t >= PUNCH) return;
        double[] p = KineticRainScene.path(muzzle, r[0], r[1], r[2], high);
        float punch = (float) (1 - t / PUNCH) * fade;
        band(b, p[0], cloud, p[2], 1.5 + t * 1.7, 0.8 + t * 0.12, SHOCK, 0.45F * punch, 0, 0);
        glows.add(p[0], cloud, p[2], 6 + t, 6 + t, 0, SHOCK, 0.3F * punch, 0);
    }

    private static void blast(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, MoonAttractVfx.Sprites fire,
                              double[] r, double t, int seed, boolean emission, float fade) {
        double x = r[0], y = r[1], z = r[2];
        if (t < 10) {
            glows.add(x, y + 3, z, 30 + t * 3, 30 + t * 3, 0, PLASMA, 0.55F * fade * (float) Math.exp(-t / 3), 0);
            glows.add(x, y + 2, z, 18 + t * 2, 18 + t * 2, 0, FIRE, 0.5F * fade * (float) Math.exp(-t / 5), 0);
            // Ejecta spike thrown straight back up the rod's track.
            BroadsideShotVfx.ribbon(b, eye, new double[] {x, y, z}, new double[] {x, y + 34 * (1 - Math.exp(-t / 1.5)), z},
                    1.8 * (1 + t * 0.15), 0.4, CORE, FIRE, 0.7F * fade * (float) Math.exp(-t / 3), 0);
        }
        if (t < 22) {
            float shock = (float) (1 - t / 22) * fade;
            band(b, x, y + 0.25, z, 2 + t * 1.5, 0.7 + t * 0.08, SHOCK, 0.8F * shock, 0, 0);
            band(b, x, y + 0.3, z, 1 + t, 0.8, FIRE, 0.5F * shock, 0, 0);
        }
        if (t < 160) {
            disc(b, x, y + 0.09, z, 3, MOLTEN, 0.4F * fade * (float) Math.exp(-t / 12), 0);
            band(b, x, y + 0.1, z, 3.2, 1, FIRE, 0.6F * fade * (float) Math.exp(-t / 40), 0, 0);
        }
        if (t < 28) fireball(fire, x, y, z, t, seed, 10, 7.5, 1, emission, fade);
    }

    /** Hammer zone: the full 28-block blast, dashed outer ring, closing ring and beacon. */
    private static void hammerZone(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, double x, double y, double z,
                                   double age, float level) {
        double remaining = HAMMER_IMPACT - age;
        if (remaining <= 0 || remaining > HAMMER_MARK) return;
        double k = remaining / HAMMER_MARK;
        float appear = (float) CarrierFlight.smooth((HAMMER_MARK - remaining) / 10) * level;
        float blink = (float) (0.75 + 0.25 * Math.sin(age * (0.8 + 2.2 * (1 - k))));
        int color = RigMesh.mix(LASER, ALERT, 1 - Math.sqrt(k));
        disc(b, x, y + 0.13, z, HAMMER_RADIUS, color, 0.06F * appear, 0.16F * appear);
        band(b, x, y + 0.16, z, HAMMER_RADIUS, 0.8, color, 0.9F * appear * blink, 0, 0);
        band(b, x, y + 0.17, z, HAMMER_RADIUS + 3, 0.5, color, 0.6F * appear, age * 0.03, 12);
        band(b, x, y + 0.18, z, HAMMER_RADIUS * k, 0.6, CORE, 0.7F * appear, 0, 0);
        BroadsideShotVfx.ribbon(b, eye, new double[] {x, y, z}, new double[] {x, y + 80, z}, 0.9, 0.2, color, color, 0.6F * appear, 0);
        glows.add(x, y + 1, z, 9, 9, 0, color, 0.6F * appear * blink, 0);
    }

    private static void crosshair(BufferBuilder b, CarrierFlight.Plan plan, double x, double y, double z, double age, float level) {
        double remaining = HAMMER_IMPACT - age;
        if (remaining <= 0 || remaining > HAMMER_MARK) return;
        float alpha = (float) CarrierFlight.smooth((HAMMER_MARK - remaining) / 10) * level * 0.7F;
        for (int arm = 0; arm < 4; arm++) {
            double a = plan.heading + arm * Math.PI / 2, c = Math.cos(a), s = -Math.sin(a);
            EffectMesh.vertex(b, x + c * 6, y + 0.2, z + s * 6, ALERT, alpha);
            EffectMesh.vertex(b, x + c * (HAMMER_RADIUS - 2), y + 0.2, z + s * (HAMMER_RADIUS - 2), ALERT, alpha * 0.3F);
        }
    }

    /** Re-entry sheath on the falling hammer's nose and the track behind it. */
    private static void hammerFall(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, LaunchState state,
                                   CarrierFlight.Plan plan, double age, float fade) {
        if (age < HAMMER_RELEASE || age >= HAMMER_IMPACT) return;
        double[] top = KineticRainScene.hammerTop(state, plan, age);
        double u = (age - HAMMER_RELEASE) / (double) (HAMMER_IMPACT - HAMMER_RELEASE);
        double[] start = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, HAMMER_RELEASE));
        double noseY = top[1] - KineticRainScene.HAMMER_LENGTH;
        BroadsideShotVfx.ribbon(b, eye, new double[] {start[0], start[1], start[2]}, new double[] {top[0], top[1], top[2]},
                1.2, 2.2, PLASMA, CORE, 0, (float) (0.5 * u) * fade);
        if (u > 0.3) band(b, top[0], noseY + 2, top[2], 2.5 + 4 * u, 0.6, SHOCK, (float) (0.6 * (u - 0.3)) * fade, 0, 0);
        glows.add(top[0], noseY, top[2], 4 + 8 * u, 4 + 8 * u, 0, RigMesh.mix(PLASMA, FIRE, u), (float) (0.3 + 0.5 * u) * fade, 0);
    }

    private static void hammerBlast(BufferBuilder b, double[] eye, MoonAttractVfx.Sprites glows, MoonAttractVfx.Sprites fire,
                                    double x, double y, double z, double t, boolean emission, float fade) {
        if (t < 0) return;
        if (t < 40) {
            glows.add(x, y + 8, z, 110 + t * 6, 110 + t * 6, 0, PLASMA, 0.6F * fade * (float) Math.exp(-t / 6), 0);
            glows.add(x, y + 5, z, 60 + t * 3, 60 + t * 3, 0, FIRE, 0.6F * fade * (float) Math.exp(-t / 10), 0);
        }
        if (t < 16) BroadsideShotVfx.ribbon(b, eye, new double[] {x, y, z}, new double[] {x, y + 140 * (1 - Math.exp(-t / 2)), z},
                6 * (1 + t * 0.1), 1, CORE, PLASMA, 0.8F * fade * (float) Math.exp(-t / 4), 0);
        if (t < 45) band(b, x, y + 0.4, z, 4 + t * 3.2, 1.5 + t * 0.2, SHOCK, 0.9F * fade * (float) (1 - t / 45), 0, 0);
        if (t >= 6 && t < 46) band(b, x, y + 0.5, z, 3 + (t - 6) * 2.4, 1.2, FIRE, 0.6F * fade * (float) (1 - (t - 6) / 40), 0, 0);
        if (t < 30) band(b, x, y + 20 + t * 0.8, z, 6 + t * 2, 2, SHOCK, 0.3F * fade * (float) (1 - t / 30), 0, 0);
        disc(b, x, y + 0.09, z, 9, MOLTEN, 0.5F * fade * (float) Math.exp(-t / 30), 0);
        band(b, x, y + 0.1, z, 10, 2, FIRE, 0.7F * fade * (float) Math.exp(-t / 45), 0, 0);
        if (t < 50) fireball(fire, x, y, z, t / 1.8, 4447, 24, 18, 2.4, emission, fade);
    }

    /** Fireball cells rise and cool from white through orange. */
    private static void fireball(MoonAttractVfx.Sprites fire, double x, double y, double z, double t, int seed, int cells,
                                 double radius, double scale, boolean emission, float fade) {
        double grow = 1 - Math.exp(-t / 3), cool = Math.exp(-t / 9);
        int flame = cool > 0.6 ? 0xFFE2B0 : cool > 0.3 ? 0xFFA048 : 0xFF6A2E;
        for (int i = 0; i < cells; i++) {
            double a = i * 2.39996 + CarrierRenderer.seed(seed + i), r = radius * grow * (0.4 + 0.6 * CarrierRenderer.seed(seed + i + 40));
            double lift = 1.5 * scale + 3 * scale * grow * CarrierRenderer.seed(seed + i + 80) + t * 0.15 * scale;
            double size = (2.5 + 4 * grow) * scale;
            fire.add(x + Math.cos(a) * r, y + lift, z + Math.sin(a) * r, size, size * 1.1, i + t * 0.05,
                    flame, (float) (cool * (emission ? 0.2 : 0.3)) * fade, i);
        }
    }

    /** Ballistic streaks thrown out of the crater; they cool as they fall. */
    private static void sparks(BufferBuilder b, double[] r, double t, int seed, int count, double scale, float fade) {
        float alpha = (float) (1 - t / 28) * fade;
        int color = RigMesh.mix(CORE, FIRE, t / 8);
        for (int i = 0; i < count; i++) {
            double a = CarrierRenderer.seed(seed + i * 3) * TAU, speed = (0.9 + 1.4 * CarrierRenderer.seed(seed + i * 3 + 1)) * scale;
            double up = (0.6 + 1.0 * CarrierRenderer.seed(seed + i * 3 + 2)) * scale, t0 = Math.max(0, t - 1.2);
            double y1 = up * t - 0.05 * scale * t * t, y0 = up * t0 - 0.05 * scale * t0 * t0;
            if (y1 < -0.5) continue;
            EffectMesh.vertex(b, r[0] + Math.cos(a) * speed * t0, r[1] + y0, r[2] + Math.sin(a) * speed * t0, color, 0);
            EffectMesh.vertex(b, r[0] + Math.cos(a) * speed * t, r[1] + y1, r[2] + Math.sin(a) * speed * t, color, alpha);
        }
    }

    /** Dark discs under the craters, drawn with ordinary blending and restored to additive. */
    private static void scorch(double[][] rods, double cx, double cy, double cz, double age, float fade) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            for (int rod = 0; rod < COUNT; rod++) {
                double t = age - impact(rod);
                if (rods[rod] != null && t >= 0) crater(b, rods[rod][0], rods[rod][1], rods[rod][2], 5.5, rod * 17,
                        (float) (0.6 * Math.min(1, t / 2) * (1 - t / SCORCH)) * fade);
            }
            double t = age - HAMMER_IMPACT;
            if (t >= 0) crater(b, cx, cy, cz, 16, 4999, (float) (0.75 * Math.min(1, t / 3)) * fade);
            Tessellator.getInstance().draw();
        } finally { GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); }
    }

    private static void crater(BufferBuilder b, double x, double y, double z, double radius, int seed, float alpha) {
        if (alpha <= 0) return;
        y += 0.03;
        for (int i = 0; i < 16; i++) {
            double a0 = i * TAU / 16, a1 = (i + 1) * TAU / 16;
            double r0 = radius * (0.8 + 0.2 * CarrierRenderer.seed(seed + i)), r1 = radius * (0.8 + 0.2 * CarrierRenderer.seed(seed + (i + 1) % 16));
            EffectMesh.vertex(b, x, y, z, 0x0E0B08, alpha);
            EffectMesh.vertex(b, x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0, 0x2A2420, 0);
            EffectMesh.vertex(b, x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1, 0x2A2420, 0);
        }
    }

    /** Ground surge ring and rising dust column; scale 1 for a rod. */
    private static void dust(MoonAttractVfx.Sprites smoke, double[] r, double t, int seed, double scale, double life, float fade) {
        double grow = 1 - Math.exp(-t / (25 * Math.sqrt(scale)));
        float alpha = (float) (Math.min(1, t / 3) * (1 - t / life)) * fade;
        if (alpha <= 0) return;
        int ring = scale > 1 ? 24 : 6;
        for (int i = 0; i < ring; i++) {
            double a = i * TAU / ring + CarrierRenderer.seed(seed + i), d = (2 + 9 * grow) * scale * (scale > 1 ? 1.6 : 1);
            double size = (2.5 + 3 * grow) * scale;
            smoke.add(r[0] + Math.cos(a) * d, r[1] + 1.2 * scale + grow, r[2] + Math.sin(a) * d, size, size * 0.7,
                    i, 0x8A8178, 0.42F * alpha, i);
        }
        int column = scale > 1 ? 14 : 6;
        for (int i = 0; i < column; i++) {
            double h = (1.5 + i * (0.8 + 1.8 * grow)) * scale, wobble = (CarrierRenderer.seed(seed + i + 30) - 0.5) * 2.5 * scale;
            double size = (2.2 + grow * (2.5 + 0.4 * i)) * scale;
            smoke.add(r[0] + wobble + t * 0.03, r[1] + h, r[2] + wobble * 0.7, size, size,
                    i * 0.7 + t * 0.01, i < column / 3 ? 0x5E544B : 0x6E6862, 0.5F * alpha, i);
        }
    }

    /** Camera-facing ribbons along a stretch of a rod's path. */
    private static void trail(BufferBuilder b, double[] eye, double[] muzzle, double[] r, double from, double to, int steps,
                              double w0, double w1, int c0, int c1, float a0, float a1) {
        if (to - from < 1.0E-4 || Math.max(a0, a1) <= 0.004F) return;
        double[] previous = KineticRainScene.path(muzzle, r[0], r[1], r[2], from);
        for (int i = 1; i <= steps; i++) {
            double f0 = (i - 1) / (double) steps, f1 = i / (double) steps;
            double[] next = KineticRainScene.path(muzzle, r[0], r[1], r[2], from + (to - from) * f1);
            BroadsideShotVfx.ribbon(b, eye, previous, next, w0 + (w1 - w0) * f0, w0 + (w1 - w0) * f1,
                    RigMesh.mix(c0, c1, f0), RigMesh.mix(c0, c1, f1), a0 + (a1 - a0) * (float) f0, a0 + (a1 - a0) * (float) f1);
            previous = next;
        }
    }

    /**
     * Soft horizontal ring for a GL_QUADS batch: transparent edges, bright centre line. With dashes, only
     * every other of that many arcs is drawn, rotated by phase.
     */
    private static void band(BufferBuilder b, double x, double y, double z, double radius, double width, int rgb, float alpha,
                             double phase, int dashes) {
        if (alpha <= 0.004F || radius <= 0) return;
        int segments = radius > 40 ? 96 : 48;
        double inner = Math.max(0, radius - width), outer = radius + width;
        for (int i = 0; i < segments; i++) {
            if (dashes > 0 && i * dashes * 2 / segments % 2 == 1) continue;
            double a0 = phase + i * TAU / segments, a1 = phase + (i + 1) * TAU / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            EffectMesh.vertex(b, x + c0 * inner, y, z + s0 * inner, rgb, 0);
            EffectMesh.vertex(b, x + c1 * inner, y, z + s1 * inner, rgb, 0);
            EffectMesh.vertex(b, x + c1 * radius, y, z + s1 * radius, rgb, alpha);
            EffectMesh.vertex(b, x + c0 * radius, y, z + s0 * radius, rgb, alpha);
            EffectMesh.vertex(b, x + c0 * radius, y, z + s0 * radius, rgb, alpha);
            EffectMesh.vertex(b, x + c1 * radius, y, z + s1 * radius, rgb, alpha);
            EffectMesh.vertex(b, x + c1 * outer, y, z + s1 * outer, rgb, 0);
            EffectMesh.vertex(b, x + c0 * outer, y, z + s0 * outer, rgb, 0);
        }
    }

    /** Filled horizontal disc for a GL_QUADS batch, as degenerate quads from the centre. */
    private static void disc(BufferBuilder b, double x, double y, double z, double radius, int rgb, float centre, float edge) {
        if (Math.max(centre, edge) <= 0.004F) return;
        for (int i = 0; i < 32; i++) {
            double a0 = i * TAU / 32, a1 = (i + 1) * TAU / 32;
            EffectMesh.vertex(b, x, y, z, rgb, centre);
            EffectMesh.vertex(b, x, y, z, rgb, centre);
            EffectMesh.vertex(b, x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, rgb, edge);
            EffectMesh.vertex(b, x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius, rgb, edge);
        }
    }

    /** Surface height of a loaded column, or the fallback; never loads a chunk. */
    static double ground(double x, double z, double fallback) {
        WorldClient world = Minecraft.getMinecraft().world;
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        if (world == null || world.getChunkProvider().getLoadedChunk(bx >> 4, bz >> 4) == null) return fallback;
        int height = world.getHeight(bx, bz);
        return height > 0 ? height : fallback;
    }

    private static double cloud() {
        WorldClient world = Minecraft.getMinecraft().world;
        return world == null ? 128 : world.provider.getCloudHeight();
    }
}
