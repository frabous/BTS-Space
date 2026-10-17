package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.BroadsideBatteries.*;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * The destroyer's five ventral twin batteries, procedural so every joint animates: a barbette welded to
 * the hull, a gun house on a traverse ring, a trunnion drum and two coilgun barrels that recoil one at
 * a time. Ship-local, drawn inside the hull transform; units are blocks, +X is the bore at zero yaw.
 */
final class DestroyerBatteryModel {
    static final int IDLE = 0, READY = 1, LAYING = 2, RELOAD = 3, NO_ARC = 4;
    private static final int HULL = 0x5B636D, DARK = 0x2A3038, PANEL = 0x7E8893, PALE = 0xAAB4BF,
            YELLOW = 0xC9AE45, BLACK = 0x15181D;
    static final int CYAN = 0x7FE6FF, AMBER = 0xFFB45A, RED = 0xFF4A3A, GREEN = 0x7DFF9C, HEAT = 0xFF7A30, WHITE = 0xEAF8FF;

    /** Articulation of one battery; plain numbers so poses interpolate and test easily. */
    static final class Pose {
        /** Traverse relative to the bow, and elevation (negative is down). */
        double yaw, pitch = REST_PITCH;
        final double[] recoil = new double[2], heat = new double[2], flash = new double[2];
        /** Coil field: 0 discharged, 1 fully charged. */
        double charge = 1;
        int status = IDLE;
        double age;
    }

    private DestroyerBatteryModel() { }

    static Pose[] rest() {
        Pose[] poses = new Pose[COUNT];
        for (int i = 0; i < COUNT; i++) poses[i] = new Pose();
        return poses;
    }

    static int statusColor(Pose p) {
        switch (p.status) {
            case READY: return GREEN;
            case LAYING: return (int) (p.age * 0.5) % 2 == 0 ? AMBER : WHITE;
            case RELOAD: return AMBER;
            case NO_ARC: return RED;
            default: return CYAN;
        }
    }

    /** Opaque then emissive pass inside the caller's transform; GL state is restored. */
    static void draw(Pose[] poses, boolean emission) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int pass = emission ? 1 : 0; pass < 2; pass++) {
                GravitonLanceModel.state(pass == 1);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                RigMesh rig = new RigMesh(b, pass == 1);
                for (int i = 0; i < COUNT; i++) {
                    rig.push();
                    rig.translate(MOUNT_X[i], RING_Y[i], MOUNT_Z[i]);
                    battery(rig, poses[i], HULL_Y[i] - RING_Y[i]);
                    rig.pop();
                }
                Tessellator.getInstance().draw();
            }
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static void battery(RigMesh rig, Pose p, double height) {
        int status = statusColor(p);
        // Barbette: fixed drum from the traverse ring up into the hull, ribbed when it is tall.
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.prism(0, height + 0.8, 6.2, 7.3, 12, 0, DARK, true);
        rig.ring(height - 0.7, 0.7, 6.9, 8.3, 12, 0, PANEL);
        rig.notchedRing(0.2, 0.55, 6.15, 6.6, 24, 0, YELLOW, BLACK);
        rig.glowRing(0.95, 0.22, 6.2, 6.38, 24, 0, status, 0.85F);
        if (height > 8) for (int rib = 0; rib < 6; rib++) {
            rig.push();
            rig.rotateX(rib * Math.PI / 3 + Math.PI / 6);
            rig.box(1.4, -0.45, 6.15, height - 0.9, 0.45, 6.75, HULL);
            rig.glowBox(1.8, -0.12, 6.74, height - 1.4, 0.12, 6.8, CYAN, 0.45F);
            rig.pop();
        }
        rig.pop();
        rig.push();
        rig.rotateY(p.yaw);
        // The traverse ring turns with the house; its notches make every bearing change readable.
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.notchedRing(-0.75, 0.75, 5.3, 6.5, 24, 0, BLACK, PALE);
        rig.pop();
        house(rig, p, status);
        cradle(rig, p);
        rig.pop();
    }

    private static void house(RigMesh rig, Pose p, int status) {
        rig.box(-8.5, -7.6, -6, 2.5, -0.75, 6, HULL);
        rig.box(-7.6, -8.5, -5.2, 1.4, -7.6, 5.2, PANEL);
        rig.box(-6.4, -8.75, -2, -3.2, -8.5, 2, DARK);
        // Magazine bustle; its vents glow while the coils recharge.
        rig.box(-12, -6.8, -4.8, -8.5, -1.5, 4.8, DARK);
        float vents = (float) ((1 - p.charge) * 0.9);
        for (int vent = 0; vent < 4; vent++)
            rig.glowBox(-12.06, -6.1 + vent * 1.15, -3.8, -11.98, -5.65 + vent * 1.15, 3.8, AMBER, vents);
        rig.glowBox(-11, -6.86, -0.9, -9, -6.8, 0.9, status, 0.95F);
        for (int side = -1; side <= 1; side += 2) {
            // Side skirts, recognition stripe, trunnion cheeks and the optical rangefinder pods.
            rig.box(-8.2, -7.3, z(side, 6, 6.45), 2.2, -1.1, z2(side, 6, 6.45), PANEL);
            rig.box(-8.25, -4.4, z(side, 6.45, 6.52), 2.25, -3.9, z2(side, 6.45, 6.52), YELLOW);
            rig.box(1.0, -9.6, z(side, 4.7, 6.0), 6.6, -2.2, z2(side, 4.7, 6.0), HULL);
            rig.box(1.3, -9.8, z(side, 5.0, 5.7), 6.3, -9.6, z2(side, 5.0, 5.7), YELLOW);
            rig.box(-3.2, -5.4, z(side, 6.5, 8.4), 0.8, -3.2, z2(side, 6.5, 8.4), DARK);
            rig.box(-3.0, -3.2, z(side, 6.8, 8.1), 0.2, -2.9, z2(side, 6.8, 8.1), PANEL);
            int lens = p.status == NO_ARC ? RED : p.status == LAYING ? WHITE : CYAN;
            rig.glowBox(0.8, -4.9, z(side, 7.0, 7.9), 0.92, -3.7, z2(side, 7.0, 7.9), lens, p.status == LAYING ? 1 : 0.7F);
        }
        // Belly sensor turret under the house.
        rig.push();
        rig.translate(-4.6, -8.5, 0);
        rig.rotateZ(-Math.PI / 2);
        rig.prism(0, 0.9, 1.3, 0.85, 8, Math.PI / 8, PALE, true);
        rig.glowPrism(0.9, 0.95, 0.6, 0.6, 8, Math.PI / 8, status, 0.8F);
        rig.pop();
    }

    private static void cradle(RigMesh rig, Pose p) {
        rig.push();
        rig.translate(TRUNNION_FORWARD, -TRUNNION_DROP, 0);
        rig.rotateZ(p.pitch);
        rig.push();
        rig.rotateY(Math.PI / 2);
        rig.prism(-4.6, 4.6, 3.0, 3.0, 12, 0, HULL, true);
        rig.ring(-4.75, 0.35, 2.0, 3.15, 12, 0, PANEL);
        rig.ring(4.4, 0.35, 2.0, 3.15, 12, 0, PANEL);
        rig.pop();
        rig.box(1.8, -2.5, -4.5, 3.7, 2.5, 4.5, DARK);
        rig.box(2.0, 2.5, -4.2, 3.5, 2.75, 4.2, YELLOW);
        for (int barrel = 0; barrel < 2; barrel++) {
            rig.push();
            rig.translate(0, 0, (barrel == 0 ? -1 : 1) * BARREL_SPACING);
            barrel(rig, p, barrel);
            rig.pop();
        }
        rig.pop();
    }

    private static void barrel(RigMesh rig, Pose p, int k) {
        float flash = (float) p.flash[k], heat = (float) p.heat[k], charge = (float) p.charge;
        // Fixed recoil sleeve; everything after the translate runs back on firing.
        rig.prism(3.6, 8.6, 1.58, 1.46, 10, 0, HULL, true);
        rig.ring(8.6, 0.45, 1.0, 1.62, 10, 0, PANEL);
        rig.push();
        rig.translate(-p.recoil[k], 0, 0);
        rig.box(-4.2, -1.4, -1.4, -1.0, 1.4, 1.4, DARK);
        rig.prism(3.0, 21.0, 1.08, 0.88, 10, 0, PALE, false);
        int coil = RigMesh.mix(CYAN, WHITE, Math.max(flash, charge * 0.35));
        for (int c = 0; c < 5; c++) {
            double x = 9.6 + c * 2.15;
            rig.notchedRing(x, 0.7, 0.95, 1.32, 12, c * 0.3, DARK, PANEL);
            rig.glowRing(x + 0.15, 0.4, 1.32, 1.4, 12, 0, coil, 0.25F + 0.65F * Math.max(flash, charge));
        }
        rig.box(20.6, -1.36, -1.36, 22.5, 1.36, 1.36, DARK);
        for (int slot = 0; slot < 3; slot++) {
            double x = 20.85 + slot * 0.55;
            rig.glowBox(x, -1.4, -0.5, x + 0.3, 1.4, 0.5, RigMesh.mix(HEAT, WHITE, flash), Math.max(flash, heat * 0.5F));
        }
        rig.glowRing(22.45, 0.1, 0.45, 1.12, 10, 0, RigMesh.mix(CYAN, WHITE, flash), 0.35F + 0.65F * flash);
        if (heat > 0.02F) rig.glowPrism(13.5, 21.0, 0.98, 0.91, 10, 0, HEAT, heat * 0.75F);
        rig.pop();
    }

    private static double z(int side, double a, double b) { return side < 0 ? -b : a; }
    private static double z2(int side, double a, double b) { return side < 0 ? -a : b; }
}
