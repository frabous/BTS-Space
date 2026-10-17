package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.BroadsideBatteries.*;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Fire-control gondola hung under the keel ramp: an elevator pylon, an armoured collar and a glass
 * bowl around the pilot's eye, with ribs, a see-through floor, overhead battery displays and
 * navigation lights. Ship-local, inside the hull transform; the pilot stands at its centre.
 */
final class FireControlGondolaModel {
    private static final int HULL = 0x5B636D, DARK = 0x222932, PANEL = 0x7E8893, YELLOW = 0xC9AE45,
            BLACK = 0x12161B, GLASS = 0x9FD8FF;
    static final double CENTRE_Y = GONDOLA_FLOOR + 1.7, COLLAR_Y = CENTRE_Y + 3.1, HULL_ATTACH = -26;
    /** Pilot eye height, and the radius of the unribbed window at the bottom of the bowl. */
    private static final double EYE = GONDOLA_FLOOR + 1.62, OCULUS = 1.4;
    private static final double TAU = Math.PI * 2;
    private static final int LATITUDES = 9, LONGITUDES = 24;

    private FireControlGondolaModel() { }

    /**
     * @param interior consoles, seat, ribs and floor; hidden for the pilot's optic so it sees the ground
     * @param inside the camera is the seated pilot, so the bowl is nearly clear
     * @param poses battery states mirrored on the consoles
     */
    static void draw(DestroyerBatteryModel.Pose[] poses, double age, boolean emission, boolean interior, boolean inside, float beam) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int pass = emission ? 1 : 0; pass < 2; pass++) {
                GravitonLanceModel.state(pass == 1);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                RigMesh rig = new RigMesh(b, pass == 1);
                rig.translate(GONDOLA_X, 0, 0);
                exterior(rig, age, beam, inside);
                if (interior) interior(rig, poses, age);
                Tessellator.getInstance().draw();
            }
            if (!emission) glass(inside);
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static void exterior(RigMesh rig, double age, float beam, boolean inside) {
        rig.push();
        rig.rotateZ(Math.PI / 2);
        // Elevator pylon from the collar into the keel, with lit guide rails.
        rig.prism(COLLAR_Y, HULL_ATTACH + 0.6, 2.0, 2.7, 8, Math.PI / 8, HULL, true);
        rig.ring(COLLAR_Y + 2.2, 0.35, 1.9, 2.25, 8, Math.PI / 8, YELLOW);
        rig.ring(HULL_ATTACH - 0.8, 0.8, 2.4, 3.6, 8, Math.PI / 8, PANEL);
        for (int rail = 0; rail < 4; rail++) {
            rig.push();
            rig.rotateX(rail * Math.PI / 2 + Math.PI / 8);
            rig.glowBox(COLLAR_Y + 0.6, -0.12, 2.02, HULL_ATTACH - 1.2, 0.12, 2.12, DestroyerBatteryModel.CYAN, 0.55F + 0.45F * beam);
            rig.pop();
        }
        // Armoured collar that carries the bowl.
        rig.prism(COLLAR_Y - 1.5, COLLAR_Y, 4.15, 3.1, 16, 0, DARK, true);
        rig.ring(COLLAR_Y - 1.65, 0.3, 3.7, 4.3, 16, 0, PANEL);
        // Trim light for witnesses; from the seat it would bloom into a band across the top of the view.
        rig.glowRing(COLLAR_Y - 1.75, 0.12, 3.75, 4.25, 16, 0, DestroyerBatteryModel.CYAN, inside ? 0.06F : 0.45F);
        rig.pop();
        // Port red, starboard green, and a white strobe on the aft rim, clear of the pilot's view down.
        rig.glowBox(-0.4, COLLAR_Y - 1.2, -4.35, 0.4, COLLAR_Y - 0.6, -4.15, 0xFF4040, 1);
        rig.glowBox(-0.4, COLLAR_Y - 1.2, 4.15, 0.4, COLLAR_Y - 0.6, 4.35, 0x50FF70, 1);
        float strobe = (age % 30) < 2 ? 1 : 0.15F;
        rig.box(-4.55, COLLAR_Y - 1.75, -0.45, -3.75, COLLAR_Y - 1.3, 0.45, BLACK);
        rig.glowBox(-4.6, COLLAR_Y - 1.95, -0.3, -3.9, COLLAR_Y - 1.75, 0.3, 0xFFFFFF, Math.max(strobe, beam));
    }

    private static void interior(RigMesh rig, DestroyerBatteryModel.Pose[] poses, double age) {
        // Eight glazing ribs from the collar down to the rim of a clear oculus right under the pilot.
        double top = Math.asin((COLLAR_Y - 1.5 - CENTRE_Y) / GONDOLA_RADIUS), low = -Math.acos(OCULUS / GONDOLA_RADIUS);
        for (int rib = 0; rib < 8; rib++) {
            rig.push();
            rig.translate(0, CENTRE_Y, 0);
            rig.rotateY(rib * TAU / 8);
            for (int k = 0; k < 8; k++) {
                double a0 = low + (top - low) * k / 8, a1 = low + (top - low) * (k + 1) / 8;
                double length = GONDOLA_RADIUS * (a1 - a0);
                rig.push();
                rig.translate(Math.cos(a0) * GONDOLA_RADIUS, Math.sin(a0) * GONDOLA_RADIUS, 0);
                rig.rotateZ(a0 + Math.PI / 2);
                rig.box(0, -0.07, -0.07, length + 0.02, 0.07, 0.07, PANEL);
                rig.pop();
            }
            rig.pop();
        }
        // See-through floor: spokes and an outer ring around an open centre; the oculus rim lies below it.
        double floor = GONDOLA_FLOOR, rim = Math.sqrt(GONDOLA_RADIUS * GONDOLA_RADIUS - 1.7 * 1.7);
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.ring(floor - 0.12, 0.12, rim - 0.25, rim, 24, 0, PANEL);
        rig.ring(CENTRE_Y + Math.sin(low) * GONDOLA_RADIUS - 0.1, 0.16, OCULUS - 0.05, OCULUS + 0.22, 24, 0, PANEL);
        rig.pop();
        for (int spoke = 0; spoke < 6; spoke++) {
            rig.push();
            rig.rotateY(spoke * TAU / 6);
            rig.box(1.1, floor - 0.1, -0.06, rim - 0.2, floor, 0.06, PANEL);
            rig.pop();
        }
        // Fire-control displays hang from the collar over the aft half, above the eye line, so every
        // look down stays open; each faces the pilot's eye.
        for (int desk = 0; desk < 4; desk++) {
            rig.push();
            rig.rotateY(Math.PI * (0.62 + desk * 0.253));
            rig.translate(2.6, EYE + 0.95, 0);
            rig.push();
            rig.rotateZ(2.07);
            rig.box(-0.05, -0.3, -0.65, 0.8, -0.08, 0.65, DARK);
            rig.box(-0.1, -0.08, -0.6, 0.85, 0.02, 0.6, BLACK);
            rig.glowBox(0.05, 0.025, -0.5, 0.75, 0.035, 0.5, 0x23506A, 0.6F);
            for (int i = 0; i < poses.length; i++) {
                int color = DestroyerBatteryModel.statusColor(poses[i]);
                double z = -0.42 + i * 0.21;
                rig.glowBox(0.1, 0.04, z, 0.22, 0.05, z + 0.14, color, 0.95F);
                double bar = 0.12 + 0.36 * poses[i].charge;
                rig.glowBox(0.28, 0.04, z + 0.03, 0.28 + bar, 0.05, z + 0.11, DestroyerBatteryModel.CYAN, 0.7F);
            }
            double sweep = (age * 0.07 + desk * 0.25) % 1;
            rig.glowBox(0.05 + 0.65 * sweep, 0.04, -0.5, 0.09 + 0.65 * sweep, 0.052, 0.5, 0x9BF6FF, 0.55F);
            rig.pop();
            rig.pop();
        }
        // Ceiling light ring under the collar, inside the displays.
        rig.push();
        rig.rotateZ(Math.PI / 2);
        rig.glowRing(COLLAR_Y - 1.56, 0.04, 1.2, 1.5, 24, 0, 0xD6F6FF, 0.16F);
        rig.pop();
    }

    /** Translucent bowl from the collar down, latitude/longitude quads with a cool tint. */
    private static void glass(boolean inside) {
        GravitonLanceModel.state(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double top = Math.asin((COLLAR_Y - 1.5 - CENTRE_Y) / GONDOLA_RADIUS);
        float alpha = inside ? 0.08F : 0.3F;
        for (int lat = 0; lat < LATITUDES; lat++) {
            double a0 = -Math.PI / 2 + (top + Math.PI / 2) * lat / LATITUDES;
            double a1 = -Math.PI / 2 + (top + Math.PI / 2) * (lat + 1) / LATITUDES;
            for (int lon = 0; lon < LONGITUDES; lon++) {
                double b0 = lon * TAU / LONGITUDES, b1 = (lon + 1) * TAU / LONGITUDES;
                vertex(b, a0, b0, alpha); vertex(b, a0, b1, alpha); vertex(b, a1, b1, alpha); vertex(b, a1, b0, alpha);
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void vertex(BufferBuilder b, double latitude, double longitude, float alpha) {
        double r = Math.cos(latitude) * GONDOLA_RADIUS;
        // Grazing glass reads brighter near the rim, as a real bowl does.
        float rim = (float) (alpha * (0.7 + 0.6 * Math.cos(latitude)));
        EffectMesh.vertex(b, GONDOLA_X + Math.cos(longitude) * r, CENTRE_Y + Math.sin(latitude) * GONDOLA_RADIUS,
                -Math.sin(longitude) * r, GLASS, rim);
    }
}
