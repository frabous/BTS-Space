package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AsteroidTrajectory;
import fr.frabous.stellar.api.LaunchState;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import static fr.frabous.stellar.api.AsteroidPullerTiming.*;

/**
 * SS-03A world-space scene, shared by the film's orbital lens and every witness in the dimension:
 * smoke beacon and uplink, carrier, Graviton Lance, asteroid, graviton harpoon and tether, entry
 * plasma and the impact. Coordinates are relative to the impact anchor; ages are ticks.
 */
final class AsteroidPullerScene {
    static final int GRAVITY = 0xB58AFF, GRAVITY_CORE = 0xF4ECFF, AMBER = 0xFFB56A, CYAN = 0x6BE6FF;
    private static final double TAU = Math.PI * 2;
    private static final double R = AsteroidTrajectory.RADIUS;
    private static final double[] SUN = AsteroidModel.SUN;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final double[][] ROCK = {{-0.9,-0.55,-0.7},{0.85,-0.4,-0.65},{1,0.7,-0.55},{-0.7,0.95,-0.5},
            {-0.75,-0.5,0.7},{0.95,-0.35,0.7},{0.6,0.6,1},{-0.8,0.8,0.65}};
    private static final int[] ROCK_FACES = {0,1,2,0,2,3,5,4,7,5,7,6,4,0,3,4,3,7,
            1,5,6,1,6,2,3,2,6,3,6,7,4,5,1,4,1,0};
    private static final double[] ROCK_SHADE = {0.62,0.76,0.52,0.84,0.96,0.46};

    private AsteroidPullerScene() { }

    static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }

    static void draw(LaunchState state, double age, boolean emission) {
        if (age < 0 || age >= END) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(state.getTarget().getX() + 0.5, state.getTarget().getY() + 1.1, state.getTarget().getZ() + 0.5);
            drawLocal(state, age, emission);
        } finally { GL11.glPopMatrix(); }
    }

    /** Anchor-relative scene. Opaque bodies first, then every additive or soft layer. */
    static void drawLocal(LaunchState state, double age, boolean emission) {
        if (age < 0 || age >= END) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            double[] eye = eye();
            CarrierFlight.Plan plan = AsteroidPullerLance.plan(state);
            GravitonLanceModel.Pose lance = AsteroidPullerLance.pose(plan, age);
            long seed = state.getSeed();
            CarrierRenderer.drawHull(plan, age, emission);
            GravitonLanceModel.draw(lance, emission);
            double[] centre = AsteroidTrajectory.position(age);
            double heat = heat(age);
            if (age >= 90 && age < IMPACT) {
                GL11.glPushMatrix();
                GL11.glTranslated(centre[0], centre[1], centre[2]);
                AsteroidModel.draw(age, heat, 1, emission);
                GL11.glPopMatrix();
            }
            restoreAdditive();
            beacon(plan, age, emission);
            uplink(plan, age, emission);
            CarrierRenderer.drawEffects(plan, age, emission);
            if (age < IMPACT) {
                descent(plan, lance, age, emission, seed);
                charge(plan, lance, age, emission, seed);
                if (age >= 90) asteroid(age, centre, heat, emission, eye, seed);
                tether(lance, age, centre, emission, eye);
            }
            if (age >= IMPACT) impact(age, emission, eye, seed);
        } finally { GL11.glPopAttrib(); }
    }

    /** Entry heating gated to the dive; the grazing pass only ionizes. */
    static double heat(double age) {
        return AsteroidTrajectory.entryHeat(age) * smooth((age - ATMOSPHERE + 16) / 24);
    }

    // --- Designation ------------------------------------------------------------------------

    private static void beacon(CarrierFlight.Plan plan, double age, boolean emission) {
        if (age >= LAND + 30) return;
        double x = plan.anchorX, y = plan.anchorY, z = plan.anchorZ;
        float fade = (float) (1 - smooth((age - LAND) / 24));
        EffectMesh.glowRing(x, y + 0.06, z, 0.7, 0.09, AMBER, fade * 0.7F);
        for (int pulse = 0; pulse < 3; pulse++) {
            double phase = ((age / 22.0) + pulse / 3.0) % 1;
            EffectMesh.glowRing(x, y + 0.08, z, 1 + phase * 7, 0.12, AMBER, (float) (fade * 0.5 * (1 - phase)));
        }
        MoonAttractVfx.glow(x, y + 0.3, z, 0.8, 0xFFC477, fade * 0.45F);
        // Drop-zone hologram while the cargo is inbound.
        double zone = smooth((age - RELEASE + 10) / 14) * (1 - smooth((age - LAND) / 16));
        if (zone > 0.01) {
            double spin = age * 0.04;
            EffectMesh.glowRing(x, y + 0.1, z, GravitonLanceModel.FOOT_RADIUS + 1.2, 0.18, CYAN, (float) (zone * 0.55));
            for (int k = 0; k < 4; k++) EffectMesh.arc(x, y + 0.12, z, GravitonLanceModel.FOOT_RADIUS + 2.4, 0.2,
                    spin + k * TAU / 4, spin + k * TAU / 4 + 0.7, CYAN, (float) (zone * 0.7));
        }
        if (emission) return;
        MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (int i = 0; i < 24; i++) {
            double born = age - i * 2.5;
            if (born < 0) continue;
            double t = (born % 60) / 60.0, rise = 0.3 + t * 14, drift = t * t * 2.2;
            smoke.add(x + Math.sin(i * 2.4 + t * 3) * (0.2 + t * 0.7) + drift, y + rise, z + Math.cos(i * 1.7) * (0.2 + t * 0.7),
                    0.6 + t * 1.9, 0.85 + t * 2.1, i * 0.7 + t * 0.4, i % 3 == 0 ? 0xE9AB60 : 0xC47838,
                    (float) (fade * 0.55 * Math.sin(Math.PI * t)), i);
        }
        smoke.draw();
    }

    /** Signal beam from the beacon to the carrier's hard point; it marks where the warp aims. */
    private static void uplink(CarrierFlight.Plan plan, double age, boolean emission) {
        if (age < UPLINK || age > 124) return;
        double top = plan.stationY() + CarrierFlight.BAY_Y - 6;
        double head = Math.min(top, (age - UPLINK) * 95);
        float alpha = (float) (smooth((age - UPLINK) / 2) * (1 - smooth((age - 104) / 18)));
        beam(plan.anchorX, plan.anchorY + 0.2, plan.anchorZ, plan.anchorX, plan.anchorY + head, plan.anchorZ, 0.3, AMBER, alpha);
        beam(plan.anchorX, plan.anchorY + 0.2, plan.anchorZ, plan.anchorX, plan.anchorY + head, plan.anchorZ, 0.08, 0xFFF4DE, alpha);
        MoonAttractVfx.Sprites pulses = new MoonAttractVfx.Sprites(MoonAttractVfx.GLOW, true);
        for (int k = 0; k < 6; k++) {
            double h = ((age - UPLINK) * 40 + k * top / 6) % Math.max(1, head);
            pulses.add(plan.anchorX, plan.anchorY + h, plan.anchorZ, 2.2, 2.2, 0, 0xFFD9A0, alpha * 0.5F, 0);
        }
        pulses.add(plan.anchorX, plan.anchorY + head, plan.anchorZ, 6, 6, 0, 0xFFE7C4, alpha * 0.8F, 0);
        pulses.draw();
    }

    // --- Graviton Lance delivery ---------------------------------------------------------------

    private static void descent(CarrierFlight.Plan plan, GravitonLanceModel.Pose p, double age, boolean emission, long seed) {
        if (age < RELEASE - 3 || age > ANCHOR + 40) return;
        double bottom = p.y + p.lift + GravitonLanceModel.BODY + p.squat;
        double release = age - RELEASE;
        if (release > -3 && release < 14) {
            float flash = (float) (Math.exp(-Math.max(0, release) / 3) * smooth((release + 3) / 3));
            MoonAttractVfx.glow(p.x, bottom + GravitonLanceModel.TOP - GravitonLanceModel.BODY, p.z, 6 + release, 0xBFF4FF, flash * 0.7F);
            ring(p.x, bottom + 7.4, p.z, 0, 1, 0, 3 + Math.max(0, release) * 1.8, 0.4, CYAN, flash * 0.6F);
        }
        double speed = AsteroidPullerLance.fallSpeed(plan, age);
        double heating = smooth((age - 112) / 10) * (1 - smooth((age - RETRO + 2) / 8));
        if (heating > 0.01) {
            float hot = (float) (heating * Math.min(1, speed / 12));
            flame(p.x, bottom - 0.4, p.z, 0, 1, 0, 4.4, 8 + speed * 2.6, 0xFFE9C8, 0xFF7A3A, hot * 0.6F);
            MoonAttractVfx.glow(p.x, bottom - 0.6, p.z, 5 + speed * 0.3, 0xFFB46E, hot * 0.7F);
            ring(p.x, bottom - 0.2, p.z, 0, 1, 0, 4.4, 0.6, 0xFFD2A0, hot * 0.5F);
        }
        double retro = smooth((age - RETRO) / 4) * (1 - smooth((age - LAND + 1) / 3));
        if (retro > 0.01) for (int jet = 0; jet < 4; jet++) {
            double a = Math.PI / 4 + jet * Math.PI / 2, jx = p.x + Math.cos(a) * 2.2, jz = p.z - Math.sin(a) * 2.2;
            double flicker = 0.85 + 0.15 * Math.sin(age * 3.3 + jet);
            flame(jx, bottom - 0.1, jz, 0, -1, 0, 0.75, (4 + 12 * retro) * flicker, 0xFFFFFF, 0x8FE4FF, (float) retro);
            MoonAttractVfx.glow(jx, bottom - 0.5, jz, 2.4, 0xBFF4FF, (float) (retro * 0.8));
        }
        double ground = (1 - smooth((p.lift - 3) / 30)) * smooth((age - RETRO) / 6);
        if (!emission && age < LAND + 40) {
            MoonAttractVfx.Sprites dust = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
            if (ground > 0.01 && age < LAND + 2) for (int i = 0; i < 18; i++) {
                double a = i * TAU / 18 + age * 0.05, r = 4 + ((age * 0.9 + i * 3.1) % 14);
                dust.add(p.x + Math.cos(a) * r, p.y + 0.8 + (r - 4) * 0.08, p.z + Math.sin(a) * r, 1.6 + r * 0.12, 1.2 + r * 0.06,
                        i * 0.7, 0xB5A894, (float) (ground * 0.32 * (1 - (r - 4) / 14)), i);
            }
            double land = age - LAND;
            if (land >= 0) for (int i = 0; i < 26; i++) {
                double a = i * TAU / 26 + variation(seed, i) * 0.3, r = 3 + Math.min(land, 30) * 0.75 * (0.7 + variation(seed, i + 40) * 0.6);
                dust.add(p.x + Math.cos(a) * r, p.y + 0.9 + land * 0.04, p.z + Math.sin(a) * r, 2.2 + land * 0.08, 1.4 + land * 0.05,
                        i * 0.61 + land * 0.02, 0xA89B88, (float) (0.45 * (1 - smooth(land / 40))), i);
            }
            double anchor = age - ANCHOR;
            if (anchor >= 0 && anchor < 30) for (int leg = 0; leg < 4; leg++) {
                double[] foot = GravitonLanceModel.foot(p, leg);
                for (int k = 0; k < 5; k++) {
                    double a = k * TAU / 5 + leg, r = 0.8 + anchor * 0.12;
                    dust.add(foot[0] + Math.cos(a) * r, p.y + 0.6 + anchor * 0.05, foot[2] + Math.sin(a) * r, 1 + anchor * 0.05, 0.8,
                            k + leg, 0x9C8F7C, (float) (0.4 * (1 - anchor / 30)), k);
                }
            }
            dust.draw();
        }
        double land = age - LAND;
        if (land >= 0 && land < 30) {
            EffectMesh.glowRing(p.x, p.y + 0.12, p.z, 3 + land * 1.1, 0.35, 0x9BCEFF, (float) (0.5 * (1 - land / 30)));
            MoonAttractVfx.glow(p.x, p.y + 0.5, p.z, 9, 0xD7F4FF, (float) (0.5 * Math.exp(-land / 3)));
        }
        double anchor = age - ANCHOR;
        if (anchor >= 0 && anchor < 20) for (int leg = 0; leg < 4; leg++) {
            double[] foot = GravitonLanceModel.foot(p, leg);
            float a = (float) Math.exp(-anchor / 4);
            MoonAttractVfx.glow(foot[0], p.y + 0.3, foot[2], 2.5 + anchor * 0.2, 0xBFF4FF, a * 0.8F);
            EffectMesh.glowRing(foot[0], p.y + 0.1, foot[2], 1.2 + anchor * 0.35, 0.15, CYAN, a * 0.7F);
        }
    }

    // --- Charge, harpoon and tether ----------------------------------------------------------

    private static void charge(CarrierFlight.Plan plan, GravitonLanceModel.Pose p, double age, boolean emission, long seed) {
        if (age < CHARGE - 10 || age > LOCK + 40) return;
        double[] m = GravitonLanceModel.muzzle(p), a = GravitonLanceModel.axis(p);
        double c = age < FIRE ? smooth((age - CHARGE) / (FIRE - CHARGE)) : Math.exp(-(age - FIRE) / 5);
        double surge = smooth((age - MAXIMUM_CHARGE) / (FIRE - MAXIMUM_CHARGE)) * (age < FIRE ? 1 : 0);
        if (c > 0.01) {
            MoonAttractVfx.Sprites glow = new MoonAttractVfx.Sprites(MoonAttractVfx.GLOW, true);
            glow.add(m[0], m[1], m[2], 1 + 3 * c + 2 * surge, 1 + 3 * c + 2 * surge, 0, GRAVITY_CORE, (float) (0.4 + 0.6 * c), 0);
            glow.add(m[0], m[1], m[2], 4 + 9 * c, 4 + 9 * c, 0, GRAVITY, (float) (0.35 * c), 0);
            // Motes spiralling into the singularity.
            for (int i = 0; i < 28; i++) {
                double phase = (age * (0.03 + 0.02 * c) + variation(seed, i)) % 1, radius = 11 * (1 - phase);
                double angle = i * 2.39996 + phase * 5;
                double[] off = perpendicular(a, angle, radius);
                glow.add(m[0] + off[0] - a[0] * radius * 0.4, m[1] + off[1] - a[1] * radius * 0.4, m[2] + off[2] - a[2] * radius * 0.4,
                        0.35 + 0.3 * phase, 0.35 + 0.3 * phase, 0, i % 3 == 0 ? GRAVITY_CORE : GRAVITY, (float) (c * 0.8 * phase), 0);
            }
            glow.draw();
            for (int k = 0; k < 3; k++) {
                double phase = 1 - ((age * 0.05 + k / 3.0) % 1);
                ring(m[0], m[1], m[2], a[0], a[1], a[2], 1.5 + 6 * phase, 0.25, GRAVITY, (float) (c * 0.55 * (1 - phase)));
            }
            if (surge > 0) arcs(m, a, age, (float) surge);
        }
        // Gravity well under the lance: dust and pebbles lifted off the ground.
        if (!emission && age < FIRE + 30) {
            double lift = smooth((age - CHARGE) / 30) * (age < FIRE ? 1 : 1 - smooth((age - FIRE) / 20));
            if (lift > 0.01) {
                MoonAttractVfx.Sprites dust = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
                for (int i = 0; i < 20; i++) {
                    double phase = (age * 0.012 + variation(seed, i + 300)) % 1, angle = i * 2.39996 + age * 0.01;
                    double radius = 5 + 10 * variation(seed, i + 320);
                    dust.add(p.x + Math.cos(angle) * radius, p.y + 0.5 + phase * 7, p.z + Math.sin(angle) * radius,
                            1.6 + phase * 2, 1.2 + phase * 1.5, i, 0xA49884, (float) (lift * 0.3 * Math.sin(Math.PI * phase)), i);
                }
                dust.draw();
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i < 26; i++) {
                    double angle = i * 2.39996, radius = 4 + 9 * variation(seed, i + 360);
                    double hover = lift * (0.6 + 3.5 * variation(seed, i + 380)) + Math.sin(age * 0.2 + i) * 0.15 * lift;
                    rock(b, p.x + Math.cos(angle) * radius, p.y + 0.2 + hover, p.z + Math.sin(angle) * radius,
                            0.18 + 0.25 * variation(seed, i + 400), age * 0.05 + i, 0x8A7F70, (float) Math.min(1, lift * 3));
                }
                Tessellator.getInstance().draw();
                restoreAdditive();
            }
        }
        double since = age - FIRE;
        if (since >= 0 && since < 40) {
            float flash = (float) Math.exp(-since / 3);
            MoonAttractVfx.glow(m[0], m[1], m[2], 10 + since * 1.5, GRAVITY_CORE, flash);
            for (int k = 0; k < 3; k++) {
                double local = since - k * 2;
                if (local < 0) continue;
                ring(m[0] + a[0] * local * 0.8, m[1] + a[1] * local * 0.8, m[2] + a[2] * local * 0.8, a[0], a[1], a[2],
                        2 + local * 1.6, 0.5, GRAVITY, (float) (0.7 * Math.exp(-local / 8)));
            }
            EffectMesh.glowRing(p.x, p.y + 0.2, p.z, 6 + since * 2.4, 0.6, 0xC9B4FF, (float) (0.5 * (1 - since / 40)));
            if (!emission) {
                MoonAttractVfx.Sprites blast = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
                for (int i = 0; i < 22; i++) {
                    double angle = i * TAU / 22, r = 6 + since * 1.8;
                    blast.add(p.x + Math.cos(angle) * r, p.y + 1 + since * 0.05, p.z + Math.sin(angle) * r, 2.2 + since * 0.1,
                            1.6, i, 0xB3A691, (float) (0.4 * (1 - since / 40)), i);
                }
                blast.draw();
            }
        }
        // Harpoon bolt: leads the asteroid and meets it exactly at the lock tick.
        if (age >= FIRE && age < LOCK) {
            double[] hit = AsteroidTrajectory.interceptPoint();
            double[] origin = GravitonLanceModel.muzzle(AsteroidPullerLance.pose(plan, FIRE));
            double u = (age - FIRE) / (LOCK - FIRE);
            double hx = origin[0] + (hit[0] - origin[0]) * u, hy = origin[1] + (hit[1] - origin[1]) * u;
            double hz = origin[2] + (hit[2] - origin[2]) * u;
            double tail = Math.max(0, u - 0.18);
            double tx = origin[0] + (hit[0] - origin[0]) * tail, ty = origin[1] + (hit[1] - origin[1]) * tail;
            double tz = origin[2] + (hit[2] - origin[2]) * tail;
            beam(tx, ty, tz, hx, hy, hz, 1.2, GRAVITY, 0.8F);
            beam(tx, ty, tz, hx, hy, hz, 0.3, GRAVITY_CORE, 1);
            beam(origin[0], origin[1], origin[2], hx, hy, hz, 0.5, GRAVITY, 0.18F);
            MoonAttractVfx.glow(hx, hy, hz, 14, GRAVITY, 0.6F);
            MoonAttractVfx.glow(hx, hy, hz, 5, 0xFFFFFF, 0.95F);
        }
    }

    private static void tether(GravitonLanceModel.Pose p, double age, double[] centre, boolean emission, double[] eye) {
        if (age < LOCK || age > SEVER + 12) return;
        double[] m = GravitonLanceModel.muzzle(p), v = AsteroidTrajectory.velocity(age);
        double dx = centre[0] - m[0], dy = centre[1] - m[1], dz = centre[2] - m[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] d = {dx / distance, dy / distance, dz / distance};
        double reach = distance - R * 0.9;
        double[] end = {m[0] + d[0] * reach, m[1] + d[1] * reach, m[2] + d[2] * reach};
        double along = v[0] * d[0] + v[1] * d[1] + v[2] * d[2];
        double[] lag = {-(v[0] - along * d[0]) * 3.2, -(v[1] - along * d[1]) * 3.2, -(v[2] - along * d[2]) * 3.2};
        float on = (float) (smooth((age - LOCK) / 3) * (1 - smooth((age - SEVER) / 3)));
        double strain = smooth((age - 500) / 50);
        float flicker = (float) (0.85 + 0.15 * Math.sin(age * 4.1) * Math.sin(age * 1.7) + 0.1 * strain * Math.sin(age * 9));
        int samples = 40;
        double[][] path = new double[samples + 1][];
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples, bend = 4 * t * (1 - t);
            path[i] = new double[] {m[0] + (end[0] - m[0]) * t + lag[0] * bend, m[1] + (end[1] - m[1]) * t + lag[1] * bend,
                    m[2] + (end[2] - m[2]) * t + lag[2] * bend};
        }
        double[] width = new double[samples + 1];
        float[] alpha = new float[samples + 1];
        for (int i = 0; i <= samples; i++) { width[i] = 3.4 + 2 * strain; alpha[i] = on * 0.32F * flicker; }
        ribbon(path, width, GRAVITY, alpha, eye);
        for (int i = 0; i <= samples; i++) { width[i] = 0.9 + 0.5 * strain; alpha[i] = on * flicker; }
        ribbon(path, width, GRAVITY_CORE, alpha, eye);
        // Two counter-twisting strands around the core.
        for (int strand = 0; strand < 2; strand++) {
            double[][] helix = new double[samples + 1][];
            for (int i = 0; i <= samples; i++) {
                double t = i / (double) samples, angle = t * distance * 0.045 * (strand == 0 ? 1 : -1) + age * 0.35 + strand * Math.PI;
                double[] off = perpendicular(d, angle, 2.2 + 1.5 * Math.sin(Math.PI * t) + strain);
                helix[i] = new double[] {path[i][0] + off[0], path[i][1] + off[1], path[i][2] + off[2]};
                width[i] = 0.35; alpha[i] = on * 0.7F;
            }
            ribbon(helix, width, strand == 0 ? 0xD9C7FF : CYAN, alpha, eye);
        }
        // Rings travel down the tether toward the lance: the pull, made visible.
        for (int k = 0; k < 7; k++) {
            double t = 1 - ((age * 0.035 + k / 7.0) % 1);
            int i = (int) Math.round(t * samples);
            ring(path[i][0], path[i][1], path[i][2], d[0], d[1], d[2], 3 + 4 * t, 0.35, GRAVITY, on * (float) (0.5 * Math.sin(Math.PI * t)));
        }
        MoonAttractVfx.glow(m[0], m[1], m[2], 5 + 2 * strain, GRAVITY_CORE, on * 0.8F);
        MoonAttractVfx.glow(end[0], end[1], end[2], 30 + 10 * strain, GRAVITY, on * 0.45F);
        double sever = age - SEVER;
        if (sever >= 0 && sever < 12) {
            float snap = (float) Math.exp(-sever / 2.5);
            MoonAttractVfx.glow(m[0], m[1], m[2], 18 + sever * 2, 0xFFFFFF, snap);
            ring(m[0], m[1], m[2], d[0], d[1], d[2], 3 + sever * 2.5, 0.6, GRAVITY_CORE, snap * 0.8F);
        }
    }

    // --- Asteroid ------------------------------------------------------------------------------

    private static void asteroid(double age, double[] c, double heat, boolean emission, double[] eye, long seed) {
        double[] v = AsteroidTrajectory.velocity(age);
        double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double[] lead = {v[0] / speed, v[1] / speed, v[2] / speed};
        trail(age, heat, emission, eye, seed);
        if (age >= LOCK) lockField(age, c, emission, seed);
        if (heat > 0.01) entry(age, c, lead, speed, heat, emission, eye, seed);
        if (!emission) shadow(age, c);
        if (!emission && age >= SEVER - 20) preImpactWind(age, seed);
        // Sun glint so the dark rock reads against a bright sky at long range.
        double distance = Math.sqrt(square(c[0] - eye[0]) + square(c[1] - eye[1]) + square(c[2] - eye[2]));
        float far = (float) smooth((distance - 900) / 1200);
        if (far > 0) MoonAttractVfx.glow(c[0], c[1], c[2], R * 1.7, 0xD9E6FF, far * 0.22F);
    }

    /** Path history as ribbons and smoke: the bend and the dive are drawn by the trail itself. */
    private static void trail(double age, double heat, boolean emission, double[] eye, long seed) {
        int samples = 48;
        double[][] path = new double[samples + 1][];
        double[] width = new double[samples + 1];
        float[] alpha = new float[samples + 1];
        double span = 70;
        boolean ionized = false;
        for (int i = 0; i <= samples; i++) {
            double back = span * i / samples, when = age - back, t = i / (double) samples;
            path[i] = AsteroidTrajectory.position(when);
            double ion = AsteroidTrajectory.ionization(when) * (when < 90 ? 0 : 1);
            width[i] = R * (0.9 - 0.5 * t);
            alpha[i] = (float) (ion * (1 - t) * (1 - t) * 0.55);
            ionized |= alpha[i] > 0.003;
        }
        if (ionized) ribbon(path, width, 0xCFE7FF, alpha, eye);
        if (heat <= 0.02) return;
        span = 26;
        for (int i = 0; i <= samples; i++) {
            double back = span * i / samples, when = age - back, t = i / (double) samples;
            path[i] = AsteroidTrajectory.position(when);
            width[i] = R * (2.1 - 1.4 * t) * (0.6 + 0.4 * heat);
            alpha[i] = (float) (heat * (1 - t) * 0.5);
        }
        ribbon(path, width, 0xFF8A3C, alpha, eye);
        for (int i = 0; i <= samples; i++) { width[i] *= 0.45; alpha[i] = (float) Math.min(1, alpha[i] * 1.6); }
        ribbon(path, width, 0xFFE6B8, alpha, eye);
        if (emission) return;
        // Billowing smoke left along the real dive path.
        MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (int i = 0; i < 70; i++) {
            double back = 3 + i * 1.6, when = age - back;
            double local = heat(when);
            if (local < 0.03) continue;
            double[] p = AsteroidTrajectory.position(when);
            double spread = R * (0.55 + 0.012 * back * back / 8) * (0.8 + 0.4 * variation(seed, i));
            double jitter = (variation(seed, i + 90) - 0.5) * R * 0.5;
            smoke.add(p[0] + jitter, p[1] + (variation(seed, i + 120) - 0.5) * R * 0.4, p[2] - jitter, spread, spread * 0.9,
                    i * 0.7 + age * 0.004, i % 3 == 0 ? 0x5E5650 : 0x6F6863, (float) (local * 0.42 * (1 - i / 75.0)), i);
        }
        smoke.draw();
    }

    /** Graviton lock: burst at contact, then a slowly rotating web of field lines. */
    private static void lockField(double age, double[] c, boolean emission, long seed) {
        double since = age - LOCK;
        float hold = (float) (smooth(since / 6) * (1 - smooth((age - SEVER) / 6)));
        double phase = (seed & 65535) / 65535.0 * TAU + age * 0.012;
        for (int band = 0; band < 5; band++) {
            double tilt = band * Math.PI / 5 + phase * (band % 2 == 0 ? 1 : -0.7);
            double[] n = {Math.sin(tilt) * Math.cos(band * 1.3), Math.cos(tilt), Math.sin(tilt) * Math.sin(band * 1.3)};
            double radius = R * (1.32 + 0.05 * Math.sin(age * 0.11 + band));
            ring(c[0], c[1], c[2], n[0], n[1], n[2], radius, 1.4, band % 2 == 0 ? GRAVITY : 0x8FD8FF,
                    hold * (float) (0.22 + 0.08 * Math.sin(age * 0.3 + band)));
        }
        if (since < 46) {
            float burst = (float) Math.exp(-since / 6);
            MoonAttractVfx.glow(c[0], c[1], c[2], R * (1.6 + since * 0.05), GRAVITY_CORE, burst * 0.85F);
            for (int k = 0; k < 4; k++) {
                double local = since - k * 3;
                if (local < 0) continue;
                double[] n = {Math.cos(k * 1.9), 0.6 + 0.3 * k, Math.sin(k * 1.9)};
                double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                ring(c[0], c[1], c[2], n[0] / length, n[1] / length, n[2] / length, R * (1.1 + local * 0.06), 3 + local * 0.2,
                        GRAVITY, (float) (0.6 * Math.exp(-local / 12)));
            }
            if (!emission) {
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
                double[] start = AsteroidTrajectory.position(LOCK);
                for (int i = 0; i < 40; i++) {
                    double[] dir = sphere(seed, i + 500);
                    double out = R * 0.95 + since * (1.2 + 1.8 * variation(seed, i + 540));
                    double drift = since * AsteroidTrajectory.CRUISE_SPEED * 0.85;
                    double[] heading = AsteroidTrajectory.velocity(LOCK - 1);
                    double x = start[0] + dir[0] * out + heading[0] / AsteroidTrajectory.CRUISE_SPEED * drift;
                    double y = start[1] + dir[1] * out + heading[1] / AsteroidTrajectory.CRUISE_SPEED * drift;
                    double z = start[2] + dir[2] * out + heading[2] / AsteroidTrajectory.CRUISE_SPEED * drift;
                    rock(b, x, y, z, 1.5 + 4 * variation(seed, i + 580), since * 0.1 + i, 0x7B7268, (float) (1 - smooth((since - 30) / 16)));
                }
                Tessellator.getInstance().draw();
                restoreAdditive();
            }
        }
    }

    private static void entry(double age, double[] c, double[] lead, double speed, double heat, boolean emission,
                              double[] eye, long seed) {
        double flicker = 0.9 + 0.1 * Math.sin(age * 3.7) * Math.sin(age * 1.3);
        // Bow shock: a white cap riding just ahead of the leading face.
        double front = AsteroidModel.MAX_RADIUS * 0.8;
        MoonAttractVfx.Sprites glow = new MoonAttractVfx.Sprites(MoonAttractVfx.GLOW, true);
        glow.add(c[0] + lead[0] * front, c[1] + lead[1] * front, c[2] + lead[2] * front, R * 1.5, R * 1.5, 0, 0xFFF0D8,
                (float) (heat * 0.75 * flicker), 0);
        glow.add(c[0], c[1], c[2], R * 2.6, R * 2.6, 0, 0xFF8A3C, (float) (heat * 0.35), 0);
        glow.draw();
        // Plasma envelope streaming back along the velocity.
        double length = R * (1.6 + 4.2 * heat);
        flame(c[0] + lead[0] * R * 0.2, c[1] + lead[1] * R * 0.2, c[2] + lead[2] * R * 0.2, -lead[0], -lead[1], -lead[2],
                R * 1.18, length, 0xFFEAC8, 0xFF6A28, (float) (heat * 0.55 * flicker));
        for (int i = 0; i < 14; i++) {
            double angle = i * TAU / 14 + (seed & 255) * 0.1;
            double[] rim = perpendicular(lead, angle, R * (1.0 + 0.08 * Math.sin(i * 2.1 + age * 0.4)));
            double sx = c[0] + rim[0], sy = c[1] + rim[1], sz = c[2] + rim[2];
            double l = length * (0.7 + 0.35 * variation(seed, i + 700)) * (0.9 + 0.1 * Math.sin(age * 0.8 + i));
            thermalTrail(sx, sy, sz, sx - lead[0] * l, sy - lead[1] * l, sz - lead[2] * l, 1.6 + heat * 3.2, i, (float) (heat * 0.42));
        }
        // Pressure fronts peel back from the nose.
        for (int k = 0; k < 4; k++) {
            double phase = (age * 0.08 + k / 4.0) % 1;
            double back = R * (0.6 + phase * 1.6);
            ring(c[0] - lead[0] * back, c[1] - lead[1] * back, c[2] - lead[2] * back, lead[0], lead[1], lead[2],
                    R * (1.25 + phase * 0.9), 2.5 + phase * 4, 0xFFD2A0, (float) (heat * 0.3 * (1 - phase)));
        }
        if (heat > 0.25) fragments(age, heat, emission, seed);
    }

    /** Fragments spalled off the heated face, each with its own ballistic arc and plasma tail. */
    private static void fragments(double age, double heat, boolean emission, long seed) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int pass = emission ? 1 : 0; pass < 2; pass++) {
            if (pass == 0) {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            }
            for (int i = 0; i < 30; i++) {
                double birth = 530 + i * 1.3 + variation(seed, i + 800) * 2;
                double flight = age - birth;
                if (flight < 0 || birth >= IMPACT) continue;
                double[] p = fragment(seed, i, birth, flight), q = fragment(seed, i, birth, Math.max(0, flight - 2.5));
                if (p[1] < 1) continue;
                double size = i % 5 == 0 ? 3.5 + 2 * variation(seed, i + 830) : 1 + 1.4 * variation(seed, i + 860);
                if (pass == 0) rock(b, p[0], p[1], p[2], size, flight * 0.3 + i, 0x6E655C, 1);
                else thermalTrail(p[0], p[1], p[2], q[0], q[1], q[2], 0.5 + size * 0.3, i, (float) (0.6 * heat));
            }
            if (pass == 0) { Tessellator.getInstance().draw(); restoreAdditive(); }
        }
    }

    private static double[] fragment(long seed, int i, double birth, double flight) {
        double[] start = AsteroidTrajectory.position(birth), v = AsteroidTrajectory.velocity(birth), dir = sphere(seed, i + 900);
        double kick = 0.6 + 1.2 * variation(seed, i + 940), drag = 1 - 0.006 * flight;
        return new double[] {start[0] + dir[0] * R + (v[0] * drag + dir[0] * kick) * flight,
                start[1] + dir[1] * R + (v[1] * drag + dir[1] * kick) * flight - 0.04 * flight * flight,
                start[2] + dir[2] * R + (v[2] * drag + dir[2] * kick) * flight};
    }

    /** Soft umbra cast along the sun direction; it races across the ground as the body descends. */
    private static void shadow(double age, double[] c) {
        double altitude = c[1];
        float alpha = (float) (smooth((1300 - altitude) / 700) * 0.42);
        if (alpha <= 0.01) return;
        double gx = c[0] - SUN[0] * altitude / SUN[1], gz = c[2] - SUN[2] * altitude / SUN[1];
        double radius = R * (1.15 + altitude / 1600);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 48; i++) {
            double a0 = i * TAU / 48, a1 = (i + 1) * TAU / 48;
            for (int ring = 0; ring < 2; ring++) {
                double inner = ring == 0 ? 0 : radius * 0.7, outer = ring == 0 ? radius * 0.7 : radius * 1.25;
                float ai = ring == 0 ? alpha : alpha, ao = ring == 0 ? alpha : 0;
                EffectMesh.vertex(b, gx + Math.cos(a0) * inner, 0.25, gz + Math.sin(a0) * inner, 0x0A0806, ai);
                EffectMesh.vertex(b, gx + Math.cos(a0) * outer, 0.25, gz + Math.sin(a0) * outer, 0x0A0806, ao);
                EffectMesh.vertex(b, gx + Math.cos(a1) * outer, 0.25, gz + Math.sin(a1) * outer, 0x0A0806, ao);
                if (ring == 1) {
                    EffectMesh.vertex(b, gx + Math.cos(a0) * inner, 0.25, gz + Math.sin(a0) * inner, 0x0A0806, ai);
                    EffectMesh.vertex(b, gx + Math.cos(a1) * outer, 0.25, gz + Math.sin(a1) * outer, 0x0A0806, ao);
                    EffectMesh.vertex(b, gx + Math.cos(a1) * inner, 0.25, gz + Math.sin(a1) * inner, 0x0A0806, ai);
                }
            }
        }
        Tessellator.getInstance().draw();
        restoreAdditive();
    }

    /** The compressed air column arrives first: dust sheets race outward and up before contact. */
    private static void preImpactWind(double age, long seed) {
        double wind = smooth((age - (SEVER - 20)) / 30);
        MoonAttractVfx.Sprites dust = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (int i = 0; i < 36; i++) {
            double phase = (age * 0.03 + variation(seed, i + 1000)) % 1, angle = i * 2.39996;
            double radius = 12 + phase * 110;
            dust.add(Math.cos(angle) * radius, 1 + phase * 9 * wind, Math.sin(angle) * radius, 4 + phase * 9, 2.5 + phase * 5,
                    i, 0xA39783, (float) (wind * 0.35 * Math.sin(Math.PI * phase)), i);
        }
        dust.draw();
    }

    // --- Impact ------------------------------------------------------------------------------

    private static void impact(double age, boolean emission, double[] eye, long seed) {
        double t = (age - IMPACT) / 20, ending = 1 - smooth((age - FADE) / (END - FADE));
        double phase = (seed & 65535) / 65535.0;
        double[] dive = AsteroidTrajectory.diveDirection();
        // Hypersonic contact flash and the hemispherical shock front.
        if (t < 2.5) {
            float flash = (float) Math.exp(-t / 0.18);
            EffectMesh.billboard(0, 8, 0, 160 + t * 200, 0xFFE9C8, flash);
            MoonAttractVfx.glow(0, 20, 0, 260 + t * 240, 0xFFD9A8, (float) (Math.exp(-t / 0.6) * 0.6));
            shockDome(t, eye);
        }
        float shock = (float) ((1 - smooth(t / 3.6)) * ending);
        if (shock > 0) {
            EffectMesh.glowRing(0, 1.4, 0, 30 + t * 210, 3 + t * 2.5, 0xFFD6A0, shock * 0.7F);
            if (t > 0.2) EffectMesh.glowRing(0, 2.5, 0, 25 + (t - 0.2) * 150, 3 + t * 2, 0xB79BFF, shock * 0.22F);
        }
        double growth = smooth(t / 5);
        if (!emission) {
            MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
            // Dust wall riding behind the ground shock.
            double wall = 30 + Math.min(t, 6) * 190 * (1 - 0.06 * Math.min(t, 6));
            for (int i = 0; i < 40; i++) {
                double a = i * TAU / 40 + phase, r = wall * (0.9 + 0.1 * variation(seed, i + 1100));
                double h = 10 + 24 * smooth(t / 1.5) + 8 * variation(seed, i + 1140);
                smoke.add(Math.cos(a) * r, h * 0.6, Math.sin(a) * r, 26 + t * 7, h, i * 0.31, 0x9A8670,
                        (float) (smooth(t / 0.4) * ending * 0.5 * (1 - smooth((t - 6) / 4))), i);
            }
            // Ejecta curtain: an inverted cone of debris and dust leaning away from the dive.
            for (int i = 0; i < 36; i++) {
                double a = i * TAU / 36 + phase, r = (20 + growth * 150) * (0.85 + 0.3 * variation(seed, i + 1200));
                double h = (12 + growth * 120) * (0.7 + 0.3 * variation(seed, i + 1240));
                double lean = 1 + 0.35 * (Math.cos(a) * dive[0] + Math.sin(a) * dive[2]);
                smoke.add(Math.cos(a) * r * lean, h, Math.sin(a) * r * lean, 20 + growth * 18, 26 + growth * 20, i * 0.7, 0x7F7064,
                        (float) (smooth(t / 0.6) * ending * 0.42 * (1 - smooth((t - 3) / 5))), i);
            }
            // Rising column and mushroom cap.
            for (int i = 0; i < 16; i++) {
                double h = 18 + i * (4 + growth * 15), radius = 26 + growth * 30 + (i % 3) * 5;
                smoke.add(Math.sin(i * 2.2 + phase) * 14 + t * 1.6, h, Math.cos(i * 2.2 + phase) * 14, radius, radius * 1.3,
                        i * 0.51 + t * 0.025, i < 5 ? 0x7E5E4C : 0x625E68, (float) (smooth(t / 0.8) * ending * 0.55), i);
            }
            double cap = 60 + growth * 220;
            for (int i = 0; i < 30; i++) {
                double a = i * 2.39996 + phase, r = (40 + growth * 110) * Math.sqrt((i + 0.5) / 30);
                double billow = (34 + growth * 46 + (i % 4) * 5) * (0.78 + variation(seed, i) * 0.38);
                smoke.add(Math.cos(a) * r + t * 2, cap + Math.sin(i * 7.3) * 26, Math.sin(a) * r, billow, billow * 0.82,
                        i * 0.73 + t * 0.03, i % 3 == 0 ? 0x8A786B : 0x686773, (float) (smooth((t - 0.3) / 1.5) * ending * 0.55), i);
            }
            smoke.draw();
        }
        if (t < 6) {
            double burst = smooth(t / 0.5), cooling = Math.exp(-t / 1.6) * (1 - smooth((t - 4) / 2));
            MoonAttractVfx.Sprites fire = new MoonAttractVfx.Sprites(MoonAttractVfx.FIRE, true);
            // Fireball: a hemisphere of flame cells that rises and cools from white to deep orange.
            double radius = 150 * (1 - Math.exp(-t * 2.4));
            for (int i = 0; i < 40; i++) {
                double[] dir = sphere(seed, i + 1300);
                if (dir[1] < 0) dir[1] = -dir[1] * 0.4;
                double cell = radius * (0.55 + 0.45 * variation(seed, i + 1340));
                double size = 22 + burst * 30 + 10 * variation(seed, i + 1380);
                int flame = cooling > 0.6 ? 0xFFE2A8 : cooling > 0.3 ? 0xFFA048 : 0xFF6A2E;
                fire.add(dir[0] * cell, 6 + dir[1] * cell * 0.75 + t * 14, dir[2] * cell, size, size * 1.1, i * 0.8 + t * 0.1,
                        flame, (float) (cooling * (emission ? 0.12 : 0.16)), i);
            }
            fire.draw();
            MoonAttractVfx.glow(0, 30 + burst * 30, 0, 60 + burst * 120, 0xFF9B45, (float) (Math.exp(-t / 0.9) * 0.4));
        }
        crater(t, ending, emission, seed);
        debris(t, ending, seed, emission, dive);
    }

    /** Thin condensation shell, brighter at its limb, expanding faster than the dust wall. */
    private static void shockDome(double t, double[] eye) {
        double radius = 40 + t * 260;
        float alpha = (float) (0.35 * (1 - smooth(t / 2.5)));
        if (alpha <= 0.01) return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int rings = 10, slices = 36;
        for (int ring = 0; ring < rings; ring++) for (int slice = 0; slice < slices; slice++) {
            double[][] corners = new double[4][];
            for (int k = 0; k < 4; k++) {
                double lat = (ring + (k == 1 || k == 2 ? 1 : 0)) * Math.PI / 2 / rings;
                double lon = (slice + (k >= 2 ? 1 : 0)) * TAU / slices;
                corners[k] = new double[] {Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
            }
            for (double[] n : corners) {
                double x = n[0] * radius, y = n[1] * radius * 0.85, z = n[2] * radius;
                double vx = eye[0] - x, vy = eye[1] - y, vz = eye[2] - z, length = Math.sqrt(vx * vx + vy * vy + vz * vz);
                double limb = 1 - Math.abs((n[0] * vx + n[1] * vy + n[2] * vz) / Math.max(1.0E-6, length));
                EffectMesh.vertex(b, x, y, z, 0xEAF2FF, (float) (alpha * (0.15 + 0.85 * limb * limb * limb)));
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void crater(double t, double ending, boolean emission, long seed) {
        float molten = (float) (Math.exp(-t / 6) * ending * smooth(t / 0.4));
        if (molten <= 0.01) return;
        EffectMesh.glowRing(0, 0.6, 0, 70, 7, 0xFF7A30, molten * 0.6F);
        EffectMesh.glowRing(0, 0.8, 0, 46, 4, 0xFFB050, molten * 0.45F);
        MoonAttractVfx.Sprites fire = new MoonAttractVfx.Sprites(MoonAttractVfx.FIRE, true);
        for (int i = 0; i < 18; i++) {
            double a = i * TAU / 18 + variation(seed, i + 1500), r = 50 + 30 * variation(seed, i + 1520);
            double flicker = 0.7 + 0.3 * Math.sin(t * 9 + i * 1.7);
            fire.add(Math.cos(a) * r, 4 + 3 * flicker, Math.sin(a) * r, 6 + 3 * flicker, 8 + 4 * flicker, i, 0xFF8A3C,
                    (float) (molten * 0.25 * flicker), i);
        }
        fire.draw();
    }

    private static void debris(double t, double ending, long seed, boolean emission, double[] dive) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        if (!emission) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        }
        double phase = (seed & 65535) / 65535.0;
        for (int i = 0; i < 110; i++) {
            double a = i * 2.39996 + phase + (variation(seed, i) - 0.5) * 0.32;
            double irregular = variation(seed, i + 1600);
            double cos = Math.cos(a), sin = Math.sin(a);
            // Ejecta leave downrange of the dive faster than uprange.
            double downrange = 1 + 0.4 * (cos * dive[0] + sin * dive[2]);
            double speed = (14 + (i % 11) * 3.6 + irregular * 10) * downrange, up = 30 + (i % 9) * 5 + irregular * 14;
            double y = 6 + up * t - 9.8 * t * t;
            if (y < 0) continue;
            double x = cos * speed * t, z = sin * speed * t;
            double size = i % 9 == 0 ? 4 + irregular * 4 : 0.8 + (i % 4) * 0.7 + irregular * 0.6;
            if (!emission) rock(b, x, y, z, size, i + t * (0.6 + i % 5 * 0.2), 0x8E7F72, (float) ending);
            else if (i % 2 == 0 && t < 4) {
                double trail = Math.min(0.2, t);
                double previousY = 6 + up * (t - trail) - 9.8 * (t - trail) * (t - trail);
                beam(x, y, z, cos * speed * (t - trail), previousY, sin * speed * (t - trail), 0.2 + size * 0.15, 0xFFC178,
                        (float) (Math.exp(-t / 1.3) * ending * 0.85));
            }
        }
        if (!emission) { Tessellator.getInstance().draw(); restoreAdditive(); }
    }

    // --- Geometry helpers ----------------------------------------------------------------------

    /** Camera position in the current model space, read from the modelview matrix. */
    static double[] eye() {
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        double tx = MODELVIEW.get(12), ty = MODELVIEW.get(13), tz = MODELVIEW.get(14);
        return new double[] {-(MODELVIEW.get(0) * tx + MODELVIEW.get(1) * ty + MODELVIEW.get(2) * tz),
                -(MODELVIEW.get(4) * tx + MODELVIEW.get(5) * ty + MODELVIEW.get(6) * tz),
                -(MODELVIEW.get(8) * tx + MODELVIEW.get(9) * ty + MODELVIEW.get(10) * tz)};
    }

    private static void restoreAdditive() { GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); }

    /** Camera-facing strip through the points, additive, with per-point width and alpha. */
    private static void ribbon(double[][] points, double[] width, int rgb, float[] alpha, double[] eye) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[][] sides = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            double[] previous = points[Math.max(0, i - 1)], next = points[Math.min(points.length - 1, i + 1)];
            double tx = next[0] - previous[0], ty = next[1] - previous[1], tz = next[2] - previous[2];
            double vx = eye[0] - points[i][0], vy = eye[1] - points[i][1], vz = eye[2] - points[i][2];
            double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-9) { sx = 0; sy = 1; sz = 0; length = 1; }
            double half = width[i] / 2 / length;
            sides[i] = new double[] {sx * half, sy * half, sz * half};
        }
        for (int i = 0; i < points.length - 1; i++) {
            if (alpha[i] <= 0.002 && alpha[i + 1] <= 0.002) continue;
            double[] p = points[i], q = points[i + 1], s = sides[i], u = sides[i + 1];
            EffectMesh.vertex(b, p[0] - s[0], p[1] - s[1], p[2] - s[2], rgb, 0);
            EffectMesh.vertex(b, p[0], p[1], p[2], rgb, alpha[i]);
            EffectMesh.vertex(b, q[0], q[1], q[2], rgb, alpha[i + 1]);
            EffectMesh.vertex(b, q[0] - u[0], q[1] - u[1], q[2] - u[2], rgb, 0);
            EffectMesh.vertex(b, p[0], p[1], p[2], rgb, alpha[i]);
            EffectMesh.vertex(b, p[0] + s[0], p[1] + s[1], p[2] + s[2], rgb, 0);
            EffectMesh.vertex(b, q[0] + u[0], q[1] + u[1], q[2] + u[2], rgb, 0);
            EffectMesh.vertex(b, q[0], q[1], q[2], rgb, alpha[i + 1]);
        }
        Tessellator.getInstance().draw();
    }

    /** Three nested additive cones from a base disc along a direction. */
    private static void flame(double x, double y, double z, double dx, double dy, double dz, double radius, double length,
                              int core, int sheath, float alpha) {
        if (alpha <= 0) return;
        double[] u = perpendicular(new double[] {dx, dy, dz}, 0, 1), w = perpendicular(new double[] {dx, dy, dz}, Math.PI / 2, 1);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int layer = 0; layer < 3; layer++) {
            double r = radius * (layer == 0 ? 0.45 : layer == 1 ? 0.8 : 1.1), l = length * (layer == 0 ? 0.5 : layer == 1 ? 0.8 : 1);
            int color = layer == 0 ? core : sheath;
            float a = alpha * (layer == 0 ? 0.8F : layer == 1 ? 0.38F : 0.15F);
            for (int i = 0; i < 20; i++) {
                double a0 = i * TAU / 20, a1 = (i + 1) * TAU / 20;
                double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
                EffectMesh.vertex(b, x + u[0] * c0 + w[0] * s0, y + u[1] * c0 + w[1] * s0, z + u[2] * c0 + w[2] * s0, color, a);
                EffectMesh.vertex(b, x + u[0] * c1 + w[0] * s1, y + u[1] * c1 + w[1] * s1, z + u[2] * c1 + w[2] * s1, color, a);
                EffectMesh.vertex(b, x + dx * l, y + dy * l, z + dz * l, sheath, 0);
            }
        }
        Tessellator.getInstance().draw();
    }

    /** glowRing centred at a point, in the plane whose normal is n. */
    private static void ring(double x, double y, double z, double nx, double ny, double nz, double radius, double width,
                             int rgb, float alpha) {
        if (alpha <= 0.002 || radius <= 0) return;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        double horizontal = Math.sqrt(nx * nx + nz * nz);
        if (horizontal > 1.0E-9 || ny < 0) {
            // Rotate +Y onto n: about the axis (nz, 0, -nx) by acos(ny).
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ny))));
            if (horizontal > 1.0E-9) GL11.glRotated(angle, nz / horizontal, 0, -nx / horizontal);
            else GL11.glRotated(180, 1, 0, 0);
        }
        EffectMesh.glowRing(0, 0, 0, radius, width, rgb, alpha);
        GL11.glPopMatrix();
    }

    /** Unit vector (scaled) perpendicular to a direction, rotated by angle around it. */
    private static double[] perpendicular(double[] d, double angle, double scale) {
        double ax = Math.abs(d[1]) < 0.9 ? 0 : 1, ay = Math.abs(d[1]) < 0.9 ? 1 : 0;
        double ux = ay * d[2], uy = -ax * d[2], uz = ax * d[1] - ay * d[0];
        double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= length; uy /= length; uz /= length;
        double wx = d[1] * uz - d[2] * uy, wy = d[2] * ux - d[0] * uz, wz = d[0] * uy - d[1] * ux;
        double c = Math.cos(angle) * scale, s = Math.sin(angle) * scale;
        return new double[] {ux * c + wx * s, uy * c + wy * s, uz * c + wz * s};
    }

    private static void arcs(double[] m, double[] a, double age, float strength) {
        int epoch = (int) Math.floor(age);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int arc = 0; arc < 6; arc++) {
            double start = CarrierRenderer.seed(epoch * 19 + arc) * TAU;
            double[] previous = perpendicular(a, start, 1.6);
            previous = new double[] {m[0] + previous[0] - a[0] * 1.3, m[1] + previous[1] - a[1] * 1.3, m[2] + previous[2] - a[2] * 1.3};
            for (int k = 1; k <= 6; k++) {
                double t = k / 6.0;
                double[] off = perpendicular(a, start + t * 1.6, 1.6 * (1 - t) + 0.4 * CarrierRenderer.seed(epoch * 7 + arc * 11 + k));
                double[] next = {m[0] + off[0] - a[0] * 1.3 * (1 - t), m[1] + off[1] - a[1] * 1.3 * (1 - t), m[2] + off[2] - a[2] * 1.3 * (1 - t)};
                EffectMesh.vertex(b, previous[0], previous[1], previous[2], GRAVITY_CORE, strength);
                EffectMesh.vertex(b, next[0], next[1], next[2], GRAVITY_CORE, strength);
                previous = next;
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void rock(BufferBuilder b, double x, double y, double z, double size, double spin, int rgb, float alpha) {
        double c = Math.cos(spin), s = Math.sin(spin), cp = Math.cos(spin * 0.73), sp = Math.sin(spin * 0.73);
        for (int i = 0; i < ROCK_FACES.length; i++) {
            double[] p = ROCK[ROCK_FACES[i]];
            int color = RigMesh.shade(rgb, ROCK_SHADE[i / 6]);
            double rz = p[0] * s + p[2] * c;
            EffectMesh.vertex(b, x + (p[0] * c - p[2] * s) * size, y + (p[1] * cp - rz * sp) * size,
                    z + (p[1] * sp + rz * cp) * size, color, alpha);
        }
    }

    /** Open tapered plasma ribbons: transparent tips and no flat cylindrical end caps. */
    private static void thermalTrail(double x, double y, double z, double tx, double ty, double tz,
                                     double width, int variant, float alpha) {
        if (alpha <= 0) return;
        double dx = tx - x, dy = ty - y, dz = tz - z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01) return;
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z);
        GL11.glRotated(Math.toDegrees(Math.atan2(dx, dz)), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(Math.atan2(Math.hypot(dx, dz), dy)), 1, 0, 0);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int plane = 0; plane < 2; plane++) for (int segment = 0; segment < 12; segment++) {
            double t0 = segment / 12.0, t1 = (segment + 1) / 12.0;
            double w0 = width * (0.4 + Math.sin(t0 * Math.PI)) * (1 - t0), w1 = width * (0.4 + Math.sin(t1 * Math.PI)) * (1 - t1);
            double bend0 = Math.sin(t0 * 9 + variant) * width * t0, bend1 = Math.sin(t1 * 9 + variant) * width * t1;
            float a0 = (float) (alpha * (1 - smooth(t0)) * smooth(t0 / 0.08)), a1 = (float) (alpha * (1 - smooth(t1)) * smooth(t1 / 0.08));
            int c0 = t0 < 0.25 ? 0xFFE1AA : 0xFF7C35, c1 = t1 < 0.25 ? 0xFFE1AA : 0xFF7C35;
            for (int band = 0; band < 2; band++) {
                double r0 = w0 * (band == 0 ? 3 : 1), r1 = w1 * (band == 0 ? 3 : 1);
                float gain = band == 0 ? 0.10F : 1;
                EffectMesh.vertex(b, plane == 0 ? bend0 - r0 : 0, t0 * length, plane == 1 ? bend0 - r0 : 0, c0, a0 * gain);
                EffectMesh.vertex(b, plane == 0 ? bend1 - r1 : 0, t1 * length, plane == 1 ? bend1 - r1 : 0, c1, a1 * gain);
                EffectMesh.vertex(b, plane == 0 ? bend1 + r1 : 0, t1 * length, plane == 1 ? bend1 + r1 : 0, c1, a1 * gain);
                EffectMesh.vertex(b, plane == 0 ? bend0 + r0 : 0, t0 * length, plane == 1 ? bend0 + r0 : 0, c0, a0 * gain);
            }
        }
        Tessellator.getInstance().draw(); GL11.glPopMatrix();
    }

    private static void beam(double x, double y, double z, double tx, double ty, double tz, double width, int rgb, float alpha) {
        if (alpha <= 0) return;
        double dx = tx - x, dy = ty - y, dz = tz - z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01) return;
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z);
        GL11.glRotated(Math.toDegrees(Math.atan2(dx, dz)), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(Math.atan2(Math.hypot(dx, dz), dy)), 1, 0, 0);
        EffectMesh.column(0, 0, 0, length, width * 3.2, rgb, alpha * 0.025F);
        EffectMesh.column(0, 0, 0, length, width * 1.8, rgb, alpha * 0.12F);
        EffectMesh.column(0, 0, 0, length, width, rgb, alpha);
        GL11.glPopMatrix();
    }

    private static double[] sphere(long seed, int key) {
        double y = variation(seed, key) * 2 - 1, a = variation(seed, key + 7919) * TAU, r = Math.sqrt(1 - y * y);
        return new double[] {Math.cos(a) * r, y, Math.sin(a) * r};
    }

    static double variation(long seed, int key) {
        long value = seed + 0x9E3779B97F4A7C15L * (key + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double square(double value) { return value * value; }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
