package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.KineticRainRules.*;

import fr.frabous.stellar.api.*;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * SS-03C in the world: the destroyer warps in with its bay over the aim, four coilgun rails slide out
 * and walk a spiral of tungsten rods inward onto the target, then the clamps let the hammer fall on the
 * centre and the ship jumps out. Every client derives the same rods from the launch seed.
 */
final class KineticRainScene {
    /** Rail corners in bay coordinates, one per spiral arm. */
    static final double[][] RAILS = {{-12.5, -10}, {12.5, -10}, {12.5, 10}, {-12.5, 10}};
    static final double RAIL_LENGTH = 11, RAIL_TOP = 2, RAIL_DROP = 11;
    static final double HAMMER_LENGTH = 9.6;
    private static final int TUNGSTEN = 0x3C3F46, NOSE = 0x5B5F66, RAIL = 0x2B2F36, COLLAR = 0x6B7380;
    private static final int AMBER = 0xFFC25A, PLASMA = 0x7FE6FF, CORE = 0xEAF8FF;

    private KineticRainScene() { }

    static CarrierFlight.Plan plan(LaunchState state) {
        BlockTarget t = state.getTarget();
        // The hammer is the cradle payload: lowered through the barrage, released at HAMMER_RELEASE.
        return new CarrierFlight.Plan(4, 16, STATION, BAY_OPEN, HAMMER_RELEASE, BAY_CLOSE, SPOOL, DEPART, JUMP,
                t.getX() + 0.5, shipY(t), t.getZ() + 0.5, heading(state.getSeed()), 420, 0);
    }

    static void draw(LaunchState state, double age, boolean emission) {
        CarrierFlight.Plan plan = plan(state);
        CarrierRenderer.drawHull(plan, age, emission, (s, e) -> {
            CarrierRenderer.STOWED.draw(s, e);
            fittings(s, age, e);
        });
        if (age >= HAMMER_RELEASE && age < HAMMER_IMPACT) fallingHammer(state, plan, age, emission);
        CarrierRenderer.drawEffects(plan, age, emission);
        KineticRainVfx.draw(state, plan, age, emission);
    }

    /** Rail extension: out of the bay before the first rod, back in after the last. */
    static double deploy(double age) {
        return CarrierFlight.smooth((age - BAY_OPEN - 8) / 16) * (1 - CarrierFlight.smooth((age - release(COUNT - 1) - 8) / 16));
    }

    /** Coil glow of the rails: a flash on every salvo over a low standing charge. */
    static double heat(double age) {
        int first = release(0), last = release(COUNT - 1);
        if (age < first - 12 || age > last + 24) return 0;
        double idle = 0.3 * CarrierFlight.smooth((age - first + 12) / 12) * (1 - CarrierFlight.smooth((age - last) / 24));
        if (age < first) return idle;
        double since = age > last ? age - last : (age - first) % BEAT;
        return Math.max(idle, Math.exp(-since / 2));
    }

    /** Hammer status lights: they climb to full as the release nears. */
    static double armed(double age) { return CarrierFlight.smooth((age - HAMMER_RELEASE + HAMMER_MARK) / HAMMER_MARK); }

    /** World position of each rail muzzle while the ship holds station with its rails out. */
    static double[][] muzzles(CarrierFlight.Plan plan) {
        CarrierFlight.State s = CarrierFlight.at(plan, FIRST_IMPACT);
        double hx = plan.hx(), hz = plan.hz(), y = s.y + CarrierFlight.BAY_Y + RAIL_TOP - RAIL_DROP;
        double[][] muzzles = new double[ARMS][];
        for (int arm = 0; arm < ARMS; arm++) {
            double lx = CarrierFlight.BAY_X + RAILS[arm][0], lz = RAILS[arm][1];
            muzzles[arm] = new double[] {s.x + lx * hx - lz * hz, y, s.z + lx * hz + lz * hx};
        }
        return muzzles;
    }

    /**
     * Rod position at flight fraction u: straight down out of the rail, an S-curve across, then a
     * near-vertical dive onto its mark.
     */
    static double[] path(double[] muzzle, double mx, double ground, double mz, double u) {
        double drop = muzzle[1] - ground, v = 1 - u;
        double a = v * v * v, b = 3 * v * v * u, c = 3 * v * u * u, d = u * u * u;
        return new double[] {(a + b) * muzzle[0] + (c + d) * mx,
                a * muzzle[1] + b * (muzzle[1] - 0.3 * drop) + c * (ground + 0.45 * drop) + d * ground,
                (a + b) * muzzle[2] + (c + d) * mz};
    }

    /** Flight fraction along {@link #path} at a tick since release; the rod keeps accelerating. */
    static double flight(double since) { return Math.pow(Math.max(0, Math.min(1, since / FLIGHT)), 1.4); }

    /** Top of the falling hammer: released from the cradle, nose on the ground at HAMMER_IMPACT. */
    static double[] hammerTop(LaunchState state, CarrierFlight.Plan plan, double age) {
        double[] start = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, HAMMER_RELEASE));
        double top = start[1] - 0.6, ground = KineticRainVfx.ground(start[0], start[2], state.getTarget().getY() + 1);
        double u = Math.max(0, Math.min(1, (age - HAMMER_RELEASE) / (double) (HAMMER_IMPACT - HAMMER_RELEASE)));
        return new double[] {start[0], top - (top - HAMMER_LENGTH - ground) * u * u, start[2]};
    }

    private static void fittings(CarrierFlight.State s, double age, boolean emission) {
        double out = deploy(age);
        boolean hammer = age < HAMMER_RELEASE && (s.bay > 0 || s.cradle > -CarrierFlight.RETRACTED + 0.01);
        if (out <= 0 && !hammer) return;
        float heat = (float) heat(age), armed = (float) armed(age);
        rig(emission, rig -> {
            rig.translate(CarrierFlight.BAY_X, CarrierFlight.BAY_Y, 0);
            if (out > 0) for (double[] rail : RAILS) rail(rig, rail[0], rail[1], out, heat);
            if (hammer) {
                rig.push();
                rig.translate(0, -1 - s.cradle - 0.6, 0);
                hammer(rig, armed);
                rig.pop();
            }
        });
    }

    private static void fallingHammer(LaunchState state, CarrierFlight.Plan plan, double age, boolean emission) {
        double[] top = hammerTop(state, plan, age);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(top[0], top[1], top[2]);
            // A slow roll from the clamp release keeps the fins reading as it falls.
            GL11.glRotated(Math.toDegrees(plan.heading) + (age - HAMMER_RELEASE) * 2.5, 0, 1, 0);
            rig(emission, rig -> hammer(rig, 1));
        } finally { GL11.glPopMatrix(); }
    }

    /** Coilgun rod rail at a bay corner, sliding out below the hull; its muzzle is the lower end. */
    private static void rail(RigMesh rig, double x, double z, double out, float heat) {
        rig.push();
        rig.translate(x, RAIL_TOP - RAIL_DROP * out + RAIL_LENGTH, z);
        rig.rotateZ(-Math.PI / 2);
        rig.prism(0, RAIL_LENGTH, 0.95, 0.95, 8, Math.PI / 8, RAIL, true);
        int coil = RigMesh.mix(PLASMA, CORE, heat);
        for (int i = 0; i < 4; i++) {
            double at = 3 + i * 2.2;
            rig.ring(at, 0.5, 0.95, 1.35, 8, Math.PI / 8, COLLAR);
            rig.glowRing(at + 0.1, 0.3, 1.35, 1.45, 8, Math.PI / 8, coil, (float) out * (0.25F + 0.75F * heat));
        }
        rig.glowRing(RAIL_LENGTH - 0.05, 0.1, 0, 0.7, 8, Math.PI / 8, CORE, (float) out * heat);
        rig.pop();
    }

    /** Tungsten hammer with its top at the origin and the nose down. */
    private static void hammer(RigMesh rig, float armed) {
        rig.push();
        rig.rotateZ(-Math.PI / 2);
        rig.prism(0, 7.6, 2.2, 2.2, 12, 0, TUNGSTEN, true);
        rig.prism(7.6, HAMMER_LENGTH, 2.2, 0.4, 12, 0, NOSE, true);
        rig.ring(0.1, 0.5, 2.2, 2.45, 12, 0, COLLAR);
        rig.ring(4.4, 0.4, 2.2, 2.4, 12, 0, COLLAR);
        for (int fin = 0; fin < 4; fin++) {
            rig.push();
            rig.rotateX(fin * Math.PI / 2 + Math.PI / 4);
            rig.box(2.4, -0.12, 2.0, 4.6, 0.12, 3.4, COLLAR);
            rig.pop();
        }
        float glow = 0.25F + 0.75F * armed;
        rig.glowRing(1.2, 0.25, 2.2, 2.3, 12, 0, AMBER, glow);
        rig.glowRing(5.4, 0.25, 2.2, 2.3, 12, 0, AMBER, glow);
        rig.glowRing(7.4, 0.2, 2.2, 2.28, 12, 0, PLASMA, glow * 0.8F);
        rig.pop();
    }

    /** Opaque then emissive passes of one rig batch, like the carrier bay. */
    private static void rig(boolean emission, Consumer<RigMesh> body) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int pass = emission ? 1 : 0; pass < 2; pass++) {
                GravitonLanceModel.state(pass == 1);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                body.accept(new RigMesh(b, pass == 1));
                Tessellator.getInstance().draw();
            }
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }
}
