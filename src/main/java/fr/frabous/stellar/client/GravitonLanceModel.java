package fr.frabous.stellar.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * AG-07 Graviton Lance: a carrier-dropped siege emplacement. Four folding legs with ground anchors,
 * a slewing turret, a three-stage telescoping barrel wrapped in counter-rotating coils, radiator
 * fins and a four-prong emitter crown. Procedural so every joint animates. Units are blocks;
 * local +X is the barrel at zero yaw, the origin is the ground point under the turret.
 */
final class GravitonLanceModel {
    static final double BODY = 2.2, PIVOT = 5.8, TOP = 9.6;
    static final double LEG_HIP = 3.3, LEG_LENGTH = 5.2, FOOT_RADIUS = 7.6;
    private static final double TAU = Math.PI * 2;
    private static final double LEG_FOLDED = Math.toRadians(-72), LEG_PLANTED = Math.asin((BODY + 1.2 - 0.5) / LEG_LENGTH);
    private static final int HULL = 0x4A5059, DARK = 0x262A31, PANEL = 0x747C87, PALE = 0xA6AFBA,
            YELLOW = 0xC9AE45, BLACK = 0x15181D, COIL = 0x2E3440;
    static final int CYAN = 0x6BE6FF, VIOLET = 0xB48CFF, WHITE = 0xEAF8FF, HEAT = 0xFF7A30;

    /** Articulation state; every field is a plain number so poses blend and test easily. */
    static final class Pose {
        double x, y, z, lift, legs, anchors, squat, yaw, pitch, telescope2, telescope3, fins, crown;
        double coil, lights, charge, recoil, heat, tremor, age;
        boolean visible = true;
    }

    private GravitonLanceModel() { }

    static void draw(Pose p, boolean emission) {
        if (!p.visible) return;
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int pass = emission ? 1 : 0; pass < 2; pass++) {
                state(pass == 1);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                build(new RigMesh(b, pass == 1), p);
                Tessellator.getInstance().draw();
            }
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
            GlStateManager.resetColor();
        }
    }

    /** Beam origin: the focus between the open crown prongs. */
    static double[] muzzle(Pose p) {
        RigMesh rig = new RigMesh(null, false);
        barrelFrame(rig, p);
        return rig.point(muzzleX(p) + 1.6, 0, 0);
    }

    static double[] axis(Pose p) {
        RigMesh rig = new RigMesh(null, false);
        barrelFrame(rig, p);
        return rig.direction(1, 0, 0);
    }

    /** Scene-local position of a foot pad centre, for dust and anchor effects. */
    static double[] foot(Pose p, int leg) {
        RigMesh rig = new RigMesh(null, false);
        chassisFrame(rig, p);
        legFrame(rig, p, leg);
        return rig.point(LEG_LENGTH, 0, 0);
    }

    static double muzzleX(Pose p) { return seg2Front(p) - 0.4 + 5.0 * p.telescope3; }
    private static double seg2Front(Pose p) { return 15.0 - 6.0 * (1 - p.telescope2); }

    private static void chassisFrame(RigMesh rig, Pose p) {
        rig.translate(p.x, p.y + p.lift + BODY + p.squat, p.z);
    }

    private static void legFrame(RigMesh rig, Pose p, int leg) {
        double angle = LEG_FOLDED + (LEG_PLANTED - LEG_FOLDED) * p.legs;
        rig.rotateY(Math.PI / 4 + leg * Math.PI / 2);
        rig.translate(LEG_HIP, 1.2, 0);
        rig.rotateZ(-angle);
    }

    private static void barrelFrame(RigMesh rig, Pose p) {
        chassisFrame(rig, p);
        rig.rotateY(p.yaw + Math.sin(p.age * 3.1) * p.tremor * 0.004);
        rig.translate(0, PIVOT, 0);
        rig.rotateZ(p.pitch + Math.sin(p.age * 2.3 + 1) * p.tremor * 0.004);
        rig.translate(-p.recoil, 0, 0);
    }

    private static void build(RigMesh rig, Pose p) {
        float lights = (float) p.lights, charge = (float) p.charge;
        int energy = RigMesh.mix(CYAN, VIOLET, charge * 0.8);
        rig.push();
        chassisFrame(rig, p);
        // Chassis: sloped octagonal skirt, deck, hazard band and vents.
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.prism(-0.2, 0.9, 3.75, 3.35, 8, Math.PI / 8, DARK, true);
        rig.prism(0.9, 1.5, 3.35, 3.0, 8, Math.PI / 8, HULL, true);
        rig.ring(0.62, 0.16, 3.38, 3.56, 8, Math.PI / 8, YELLOW);
        rig.glowRing(0.3, 0.12, 3.5, 3.72, 8, Math.PI / 8, energy, lights * (0.55F + charge * 0.45F));
        rig.pop();
        for (int vent = 0; vent < 4; vent++) {
            rig.push();
            rig.rotateY(vent * Math.PI / 2);
            rig.box(2.85, 0.15, -1.1, 3.25, 0.95, 1.1, BLACK);
            for (int slit = 0; slit < 4; slit++)
                rig.glowBox(3.2, 0.25 + slit * 0.18, -0.95, 3.28, 0.33 + slit * 0.18, 0.95, energy, lights * (0.5F + charge * 0.5F));
            rig.pop();
        }
        // Legs: hip housings, struts, pistons, feet and anchors.
        for (int leg = 0; leg < 4; leg++) {
            rig.push();
            rig.rotateY(Math.PI / 4 + leg * Math.PI / 2);
            rig.box(2.6, 0.5, -0.8, 3.8, 1.9, 0.8, HULL);
            rig.box(2.7, 1.9, -0.6, 3.6, 2.05, 0.6, YELLOW);
            rig.pop();
            rig.push();
            legFrame(rig, p, leg);
            rig.box(0, -0.42, -0.5, LEG_LENGTH, 0.42, 0.5, HULL);
            rig.box(0.4, 0.42, -0.32, LEG_LENGTH - 0.6, 0.62, 0.32, PANEL);
            rig.box(1.2, -0.62, -0.25, LEG_LENGTH - 1.0, -0.42, 0.25, BLACK);
            rig.glowBox(1.4, -0.66, -0.12, LEG_LENGTH - 1.2, -0.6, 0.12, energy, lights * 0.7F);
            rig.translate(LEG_LENGTH, 0, 0);
            double angle = LEG_FOLDED + (LEG_PLANTED - LEG_FOLDED) * p.legs;
            rig.rotateZ(angle);
            foot(rig, p, energy, lights);
            rig.pop();
        }
        // Turret: slewing ring, deck, cheeks and trunnion axle.
        rig.push();
        rig.rotateY(p.yaw);
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.notchedRing(1.5, 0.22, 2.2, 2.95, 24, p.yaw, BLACK, YELLOW);
        rig.prism(1.72, 2.25, 2.85, 2.55, 12, 0, HULL, true);
        rig.prism(2.25, 2.4, 2.55, 2.35, 12, 0, PANEL, true);
        rig.pop();
        for (int side = -1; side <= 1; side += 2) {
            double inner = side * 1.75, outer = side * 2.55;
            rig.box(-1.9, 2.4, Math.min(inner, outer), 2.1, PIVOT + 0.6, Math.max(inner, outer), HULL);
            rig.box(-1.3, PIVOT + 0.6, Math.min(inner, outer) + side * 0.1, 1.5, PIVOT + 1.2, Math.max(inner, outer) - side * 0.1, PANEL);
            rig.box(-1.95, 3.0, Math.min(outer, outer + side * 0.06), 2.15, 3.35, Math.max(outer, outer + side * 0.06), YELLOW);
            rig.glowBox(-1.2, 3.7, Math.min(outer, outer + side * 0.08), 1.4, 3.85, Math.max(outer, outer + side * 0.08), energy, lights);
            rig.glowBox(-1.2, 4.3, Math.min(outer, outer + side * 0.08), 1.4, 4.45, Math.max(outer, outer + side * 0.08), energy, lights);
        }
        rig.push();
        rig.translate(0, PIVOT, 0);
        rig.rotateY(Math.PI / 2);
        rig.prism(-2.75, 2.75, 0.62, 0.62, 10, 0, PALE, true);
        rig.pop();
        rig.pop();
        rig.pop();
        barrel(rig, p, energy, lights, charge);
    }

    private static void foot(RigMesh rig, Pose p, int energy, float lights) {
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.prism(-0.55, -0.05, 1.25, 1.05, 8, Math.PI / 8, DARK, true);
        rig.prism(-0.05, 0.6, 0.62, 0.55, 8, Math.PI / 8, PANEL, true);
        double drive = p.anchors * 1.9;
        rig.prism(-0.55 - drive, -0.55, 0.3, 0.3, 6, 0, PALE, true);
        rig.glowRing(-0.62, 0.1, 1.05, 1.28, 8, Math.PI / 8, energy, lights * (0.35F + (float) p.anchors * 0.65F));
        rig.pop();
        rig.box(-0.2, -0.05, -1.25, 0.2, 0.55, 1.25, YELLOW);
    }

    private static void barrel(RigMesh rig, Pose p, int energy, float lights, float charge) {
        rig.push();
        barrelFrame(rig, p);
        int hot = RigMesh.mix(energy, WHITE, charge * 0.6);
        // Cradle sleeve and reactor breech.
        rig.box(-1.4, -1.25, -1.45, 2.0, 1.25, 1.45, HULL);
        rig.box(-1.2, 1.25, -1.2, 1.8, 1.45, 1.2, PANEL);
        rig.box(-1.45, -0.3, -1.5, 2.05, 0.3, 1.5, YELLOW);
        rig.box(-3.0, -1.3, -1.3, -1.4, 1.3, 1.3, DARK);
        rig.prism(-3.6, -3.0, 1.2, 1.55, 8, Math.PI / 8, HULL, true);
        for (int side = -1; side <= 1; side += 2)
            rig.glowBox(-2.8, -0.75, side > 0 ? 1.3 : -1.34, -1.6, 0.75, side > 0 ? 1.34 : -1.3, hot,
                    lights * (0.55F + 0.45F * charge) * (float) (0.85 + 0.15 * Math.sin(p.age * (1 + charge * 3))));
        rig.glowRing(-3.65, 0.08, 0.35, 0.9, 12, 0, hot, lights * (0.6F + 0.4F * charge));
        // Radiator fins open off the breech roof, glowing as the tether loads them.
        for (int side = -1; side <= 1; side += 2) {
            rig.push();
            rig.translate(0, 1.3, side * 1.0);
            rig.rotateX(-side * p.fins * Math.toRadians(62));
            double z0 = side < 0 ? -2.3 : 0, z1 = side < 0 ? 0 : 2.3;
            rig.box(-2.9, 0, z0, -1.5, 0.12, z1, PANEL);
            for (int rib = 0; rib < 4; rib++)
                rig.box(-2.8 + rib * 0.35, 0.12, z0 + (side < 0 ? 0.2 : 0), -2.65 + rib * 0.35, 0.22, z1 - (side < 0 ? 0 : 0.2), DARK);
            rig.glowBox(-2.85, 0.13, z0, -1.55, 0.16, z1, HEAT, (float) (p.heat * 0.85 * p.fins));
            rig.pop();
        }
        // Stage one: octagonal tube, tri-rails and three counter-rotating coils.
        rig.prism(1.8, 9.2, 1.42, 1.42, 8, Math.PI / 8, HULL, true);
        for (int rail = 0; rail < 3; rail++) {
            rig.push();
            rig.rotateX(Math.PI / 2 + rail * TAU / 3);
            rig.box(2.2, 1.45, -0.16, 9.0, 1.8, 0.16, PANEL);
            rig.glowBox(2.4, 1.42, -0.06, 8.8, 1.46, 0.06, energy, lights * 0.8F);
            rig.pop();
        }
        for (int coil = 0; coil < 3; coil++) coil(rig, 3.0 + coil * 2.0, 1.48, coil % 2 == 0 ? p.coil : -p.coil, energy, lights, charge);
        rig.ring(9.0, 0.45, 1.36, 1.62, 8, Math.PI / 8, PALE);
        // Stage two.
        double front2 = seg2Front(p);
        rig.prism(front2 - 6.0, front2, 1.12, 1.12, 8, Math.PI / 8, DARK, false);
        rig.ring(front2 - 0.45, 0.45, 1.08, 1.32, 8, Math.PI / 8, PANEL);
        for (int coil = 0; coil < 2; coil++) coil(rig, front2 - 4.6 + coil * 2.2, 1.16, coil % 2 == 0 ? -p.coil * 1.3 : p.coil * 1.3,
                energy, lights, charge);
        rig.glowBox(front2 - 5.6, 1.1, -0.08, front2 - 0.6, 1.14, 0.08, energy, lights * 0.7F * (float) p.telescope2);
        // Stage three and the emitter crown.
        double front3 = muzzleX(p);
        rig.prism(front3 - 5.0, front3, 0.88, 0.88, 8, Math.PI / 8, HULL, false);
        rig.prism(front3 - 0.6, front3 + 0.3, 1.05, 1.15, 8, Math.PI / 8, DARK, true);
        rig.glowRing(front3 - 0.65, 0.08, 1.05, 1.22, 8, Math.PI / 8, hot, lights);
        for (int prong = 0; prong < 4; prong++) {
            rig.push();
            rig.translate(front3 + 0.3, 0, 0);
            rig.rotateX(Math.PI / 4 + prong * Math.PI / 2);
            rig.translate(0, 0.85, 0);
            rig.rotateZ(p.crown * Math.toRadians(26));
            rig.box(0, -0.22, -0.28, 2.7, 0.22, 0.28, PANEL);
            rig.box(0.3, 0.22, -0.18, 2.2, 0.34, 0.18, YELLOW);
            rig.glowBox(2.1, -0.24, -0.3, 2.75, 0.24, 0.3, hot, lights * (0.5F + charge * 0.5F));
            rig.glowBox(0.2, -0.26, -0.06, 2.6, -0.22, 0.06, hot, lights * 0.8F * (float) p.crown);
            rig.pop();
        }
        // Heat creeps from the breech toward the muzzle once the tether loads the barrel.
        if (p.heat > 0) rig.glowPrism(1.8, 9.2, 1.44, 1.44, 8, Math.PI / 8, HEAT, (float) (p.heat * 0.22));
        rig.pop();
    }

    private static void coil(RigMesh rig, double x, double inner, double spin, int energy, float lights, float charge) {
        rig.push();
        rig.rotateX(spin);
        rig.notchedRing(x, 0.6, inner, inner + 0.42, 12, 0, COIL, PALE);
        rig.glowRing(x + 0.12, 0.36, inner + 0.42, inner + 0.5, 12, 0, RigMesh.mix(energy, WHITE, charge * 0.5),
                lights * (0.45F + charge * 0.55F));
        rig.pop();
    }

    /** Raw GL inside the caller's attribute bracket; Minecraft's enable caches stay untouched. */
    static void state(boolean emission) {
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(!emission);
        if (emission) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        } else GL11.glDisable(GL11.GL_BLEND);
    }
}
