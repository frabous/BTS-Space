package fr.frabous.stellar.client;

import java.nio.DoubleBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Draws the {@link CarrierFlight} destroyer with its warp apertures, drive plumes and animated
 * ventral bay. Payload scenes draw their own cargo at {@link CarrierFlight#hardPoint}.
 */
final class CarrierRenderer {
    private static final double TAU = Math.PI * 2;
    private static final int WARP = 0x7FB8FF, WARP_CORE = 0xE9F6FF, DRIVE = 0x8FE4FF, RETRO = 0xFFB46A;
    private static final double[][] MAIN_DRIVES = {{-312, -1, -28}, {-312, -1, -10}, {-312, -1, 10}, {-312, -1, 28}};
    private static final double[][] SIDE_DRIVES = {{-259, -13, -58}, {-259, 13, -58}, {-259, -13, 58}, {-259, 13, 58},
            {-105, -13, -87}, {-105, 13, -87}, {-105, -13, 87}, {-105, 13, 87}};
    private static final double[][] RETROS = {{379, -1.5, -44}, {379, -1.5, 44}};
    private static final DoubleBuffer PLANE = BufferUtils.createDoubleBuffer(4);

    /** Articulated parts that ride the hull transform, so warp stretch and aperture clipping apply to them. */
    interface Fittings { void draw(CarrierFlight.State s, boolean emission); }

    private static final DestroyerBatteryModel.Pose[] REST = DestroyerBatteryModel.rest();
    /** Batteries stowed bow-forward: every payload that does not command the guns. */
    static final Fittings STOWED = (s, emission) -> DestroyerBatteryModel.draw(REST, emission);

    private CarrierRenderer() { }

    static void prepare() { OrbitalDestroyerModel.prepare(); }

    /** Hull, emissive trim and bay: depth-writing geometry, drawn before any additive effect. */
    static void drawHull(CarrierFlight.Plan plan, double age, boolean emission) { drawHull(plan, age, emission, STOWED); }

    static void drawHull(CarrierFlight.Plan plan, double age, boolean emission, Fittings fittings) {
        CarrierFlight.State s = CarrierFlight.at(plan, age);
        if (!s.visible) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            transform(plan, s);
            OrbitalDestroyerModel.render(emission);
            bay(s, emission);
            fittings.draw(s, emission);
        } finally {
            for (int plane = 0; plane < 2; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** Apertures, drive plumes and warp streaks; additive, after all opaque scene geometry. */
    static void drawEffects(CarrierFlight.Plan plan, double age, boolean emission) {
        CarrierFlight.State s = CarrierFlight.at(plan, age);
        gate(plan, s.arrival, age, emission, true);
        gate(plan, s.departure, age, emission, false);
        if (!s.visible) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            transform(plan, s);
            if (s.stretch != 1) streaks(s, emission);
            drives(s, age, emission);
        } finally {
            for (int plane = 0; plane < 2; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void transform(CarrierFlight.Plan plan, CarrierFlight.State s) {
        if (s.clipArrival) clip(plan.hx(), plan.hz(), s.arrivalPlaneX, s.arrivalPlaneZ, 1);
        if (s.clipDeparture) clip(plan.hx(), plan.hz(), s.departurePlaneX, s.departurePlaneZ, -1);
        GL11.glTranslated(s.x, s.y, s.z);
        GL11.glRotated(Math.toDegrees(plan.heading), 0, 1, 0);
        if (s.stretch != 1) {
            GL11.glTranslated(s.stretchAnchor, 0, 0);
            GL11.glScaled(s.stretch, 1, 1);
            GL11.glTranslated(-s.stretchAnchor, 0, 0);
        }
    }

    /** Keeps ship geometry on the open side of a warp aperture; sign 1 keeps the bow side. */
    private static void clip(double hx, double hz, double px, double pz, int sign) {
        PLANE.clear();
        PLANE.put(new double[] {sign * hx, 0, sign * hz, -sign * (hx * px + hz * pz)}).flip();
        int index = sign > 0 ? 0 : 1;
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0 + index, PLANE);
        GL11.glEnable(GL11.GL_CLIP_PLANE0 + index);
    }

    /** Doors, dark well and four-rail gantry, in ship-local coordinates. */
    private static void bay(CarrierFlight.State s, boolean emission) {
        if (s.bay <= 0 && s.cradle <= -CarrierFlight.RETRACTED + 0.01) return;
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int pass = emission ? 1 : 0; pass < 2; pass++) {
                GravitonLanceModel.state(pass == 1);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                RigMesh rig = new RigMesh(b, pass == 1);
                rig.translate(CarrierFlight.BAY_X, CarrierFlight.BAY_Y, 0);
                double open = s.bay;
                rig.box(-15, -0.5, -12.5, 15, -0.2, 12.5, 0x0B0D11);
                rig.glowBox(-15, -0.55, -12.6, 15, -0.5, -12.3, 0xFFC25A, (float) (open * 0.8));
                rig.glowBox(-15, -0.55, 12.3, 15, -0.5, 12.6, 0xFFC25A, (float) (open * 0.8));
                for (int side = -1; side <= 1; side += 2) {
                    rig.push();
                    rig.translate(0, -0.5, side * 12.5);
                    rig.rotateX(side * open * Math.toRadians(-105));
                    rig.box(-14.5, -0.8, side < 0 ? 0 : -12.4, 14.5, 0, side < 0 ? 12.4 : 0, 0x4D535C);
                    rig.box(-14.5, -1.0, side < 0 ? 5.5 : -6.5, 14.5, -0.8, side < 0 ? 6.5 : -5.5, 0xBFA54A);
                    rig.pop();
                }
                double hang = -1 - s.cradle;
                if (hang < -0.6) {
                    for (int corner = 0; corner < 4; corner++) {
                        double x = corner < 2 ? -9 : 9, z = corner % 2 == 0 ? -7 : 7;
                        rig.box(x - 0.45, hang, z - 0.45, x + 0.45, 4, z + 0.45, 0x8E96A1);
                        rig.glowBox(x - 0.5, hang + 0.6, z - 0.5, x + 0.5, hang + 1.0, z + 0.5, DRIVE, (float) (0.9 * open));
                    }
                    rig.box(-9.5, hang - 0.6, -7.5, 9.5, hang, 7.5, 0x30353D);
                    for (int side = -1; side <= 1; side += 2) {
                        double swing = s.clamps * 1.4;
                        rig.box(-3.2, hang - 2.6, side * (2.9 + swing) - 0.35, 3.2, hang - 0.6, side * (2.9 + swing) + 0.35, 0x23272E);
                        rig.glowBox(-3.0, hang - 2.5, side * (2.9 + swing) - 0.4, 3.0, hang - 2.3, side * (2.9 + swing) + 0.4,
                                s.clamps > 0 ? 0x7DFF9C : 0xFF5A4A, (float) (0.9 * open));
                    }
                }
                Tessellator.getInstance().draw();
            }
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    /** Afterimage lines trailing the hull while it is stretched by the warp. */
    private static void streaks(CarrierFlight.State s, boolean emission) {
        double strength = Math.min(1, (s.stretch - 1) / 1.7);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 48; i++) {
            double y = -70 + seed(i * 3 + 1) * 120, z = (seed(i * 7 + 2) - 0.5) * 200;
            double start = -STERN_LINE + seed(i * 11 + 5) * 300, length = 180 + seed(i * 13 + 3) * 520;
            int color = i % 3 == 0 ? WARP_CORE : WARP;
            EffectMesh.vertex(b, start, y, z, color, (float) (strength * (emission ? 0.35 : 0.5)));
            EffectMesh.vertex(b, start - length * strength, y, z, color, 0);
        }
        Tessellator.getInstance().draw();
    }

    private static final double STERN_LINE = CarrierFlight.STERN;

    private static void drives(CarrierFlight.State s, double age, boolean emission) {
        double flicker = 0.92 + 0.08 * Math.sin(age * 2.7);
        if (s.engine > 0.01) for (double[] p : MAIN_DRIVES) {
            double power = s.engine * flicker;
            plume(p[0], p[1], p[2], -1, 7 + power * 6, 12 + power * 110, DRIVE, (float) Math.min(1, power), emission);
            MoonAttractVfx.glow(p[0] - 2, p[1], p[2], 10 + power * 22, DRIVE, (float) (0.35 * Math.min(1, power)));
            MoonAttractVfx.glow(p[0] - 1, p[1], p[2], 4 + power * 6, 0xFFFFFF, (float) (0.5 * Math.min(1, power)));
        }
        double side = Math.max(s.brake * 0.6, s.engine > 0.5 ? (s.engine - 0.5) * 0.6 : 0);
        if (side > 0.01) for (double[] p : SIDE_DRIVES)
            MoonAttractVfx.glow(p[0], p[1], p[2] + Math.signum(p[2]) * 1.5, 5 + side * 6, DRIVE, (float) (side * 0.55));
        if (s.brake > 0.01) for (double[] p : RETROS) {
            plume(p[0], p[1], p[2], 1, 4 + s.brake * 4, 30 + s.brake * 160, RETRO, (float) s.brake, emission);
            MoonAttractVfx.glow(p[0] + 3, p[1], p[2], 12 + s.brake * 20, RETRO, (float) (0.45 * s.brake));
        }
    }

    /** Additive cone along local +/-X with a white throat and a coloured, fading sheath. */
    private static void plume(double x, double y, double z, int direction, double radius, double length,
                              int rgb, float alpha, boolean emission) {
        if (alpha <= 0) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glDepthMask(false);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            for (int layer = 0; layer < 3; layer++) {
                double r = radius * (layer == 0 ? 0.35 : layer == 1 ? 0.7 : 1.15);
                double l = length * (layer == 0 ? 0.45 : layer == 1 ? 0.8 : 1);
                int color = layer == 0 ? 0xFFFFFF : rgb;
                float a = alpha * (layer == 0 ? 0.7F : layer == 1 ? 0.32F : 0.12F) * (emission ? 0.8F : 1);
                for (int i = 0; i < 16; i++) {
                    double a0 = i * TAU / 16, a1 = (i + 1) * TAU / 16;
                    EffectMesh.vertex(b, x, y + Math.cos(a0) * r, z + Math.sin(a0) * r, color, a);
                    EffectMesh.vertex(b, x, y + Math.cos(a1) * r, z + Math.sin(a1) * r, color, a);
                    EffectMesh.vertex(b, x + direction * l, y, z, rgb, 0);
                }
            }
            Tessellator.getInstance().draw();
        } finally { GL11.glPopAttrib(); }
    }

    /** Spatial aperture in the plane normal to the heading; arrival and departure share it. */
    private static void gate(CarrierFlight.Plan plan, CarrierFlight.Gate g, double age, boolean emission, boolean arrival) {
        if (g.energy <= 0.002 && (g.flash < 0 || age < g.flash || age > g.flash + 30)) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(g.x, g.y, g.z);
            GL11.glRotated(Math.toDegrees(plan.heading), 0, 1, 0);
            double radius = CarrierFlight.APERTURE_RADIUS * g.open;
            float energy = (float) g.energy;
            double spin = age * (arrival ? 0.21 : -0.24);
            // Converging star-streaks while space folds, before the rim is visible.
            double gather = smooth((age - (arrival ? plan.warpOpen - 6 : plan.jump - 12)) / 6) * (1 - g.open);
            if (gather > 0.01) {
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i < 64; i++) {
                    double a = i * 2.39996 + seed(i) * 0.4, phase = (age * 0.09 + seed(i + 70)) % 1;
                    double r0 = CarrierFlight.APERTURE_RADIUS * (2.6 - 2.2 * phase), r1 = r0 + 60 * (1 - phase);
                    EffectMesh.vertex(b, 0, Math.cos(a) * r0, Math.sin(a) * r0, WARP_CORE, (float) (gather * 0.6 * phase));
                    EffectMesh.vertex(b, 0, Math.cos(a) * r1, Math.sin(a) * r1, WARP, 0);
                }
                Tessellator.getInstance().draw();
                MoonAttractVfx.glow(0, 0, 0, 20 + 60 * gather, WARP_CORE, (float) (gather * 0.6));
            }
            if (radius > 0.5) {
                GL11.glPushMatrix();
                GL11.glRotated(90, 0, 0, 1);
                for (int ring = 0; ring < 4; ring++)
                    EffectMesh.glowRing(0, 0, 0, radius * (1 + ring * 0.045), 1.6 + ring * 1.4,
                            ring == 0 ? WARP_CORE : WARP, energy * (ring == 0 ? 0.95F : 0.3F - ring * 0.05F));
                // Inward ripples on the folded surface.
                for (int ripple = 0; ripple < 5; ripple++) {
                    double phase = 1 - ((age * 0.045 + ripple / 5.0) % 1);
                    EffectMesh.glowRing(0, 0, 0, radius * phase, 1.2, WARP, energy * (float) (0.22 * Math.sin(Math.PI * phase)));
                }
                GL11.glPopMatrix();
                surface(radius, spin, energy, emission);
                arcs(radius, age, energy);
                MoonAttractVfx.glow(0, 0, 0, radius * 1.7, WARP, energy * 0.16F);
                MoonAttractVfx.glow(0, 0, 0, radius * 0.45, WARP_CORE, energy * 0.35F);
            }
            // Flash and planar shock ring when the hull clears the aperture.
            double since = age - g.flash;
            if (g.flash >= 0 && since >= 0 && since < 30) {
                float flash = (float) Math.exp(-since / 3.5);
                MoonAttractVfx.glow(0, 0, 0, CarrierFlight.APERTURE_RADIUS * (1.2 + since * 0.08), WARP_CORE, flash * 0.9F);
                GL11.glPushMatrix();
                GL11.glRotated(90, 0, 0, 1);
                EffectMesh.glowRing(0, 0, 0, CarrierFlight.APERTURE_RADIUS * (1 + since * 0.22), 4 + since * 0.6, WARP,
                        (float) (0.6 * (1 - since / 30)));
                GL11.glPopMatrix();
                if (g.collapse > 0.85) MoonAttractVfx.glow(0, 0, 0, 30 + 90 * (1 - g.collapse), 0xFFFFFF,
                        (float) (smooth((g.collapse - 0.85) / 0.15) * (1 - smooth((since - 22) / 8))));
            }
        } finally { GL11.glPopMatrix(); }
    }

    /** Four spiral arms over a soft disc; drawn in the aperture's YZ plane. */
    private static void surface(double radius, double spin, float energy, boolean emission) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 48; i++) {
            double a0 = i * TAU / 48, a1 = (i + 1) * TAU / 48;
            EffectMesh.vertex(b, 0, 0, 0, WARP_CORE, energy * 0.28F);
            EffectMesh.vertex(b, 0, Math.cos(a0) * radius, Math.sin(a0) * radius, WARP, energy * 0.06F);
            EffectMesh.vertex(b, 0, Math.cos(a1) * radius, Math.sin(a1) * radius, WARP, energy * 0.06F);
        }
        for (int arm = 0; arm < 4; arm++) {
            double base = spin + arm * TAU / 4;
            for (int k = 0; k < 24; k++) {
                double s0 = k / 24.0, s1 = (k + 1) / 24.0;
                double r0 = radius * Math.pow(s0, 0.8), r1 = radius * Math.pow(s1, 0.8);
                double t0 = base + s0 * 3.1, t1 = base + s1 * 3.1;
                double w0 = radius * 0.06 * (1 - s0) + 0.6, w1 = radius * 0.06 * (1 - s1) + 0.6;
                float alpha0 = energy * (float) (0.5 * (1 - s0)), alpha1 = energy * (float) (0.5 * (1 - s1));
                int c0 = s0 < 0.3 ? WARP_CORE : WARP, c1 = s1 < 0.3 ? WARP_CORE : WARP;
                double[] p0a = polar(r0 - w0, t0), p0b = polar(r0 + w0, t0), p1a = polar(r1 - w1, t1), p1b = polar(r1 + w1, t1);
                EffectMesh.vertex(b, 0, p0a[0], p0a[1], c0, alpha0); EffectMesh.vertex(b, 0, p0b[0], p0b[1], c0, alpha0);
                EffectMesh.vertex(b, 0, p1b[0], p1b[1], c1, alpha1);
                EffectMesh.vertex(b, 0, p0a[0], p0a[1], c0, alpha0); EffectMesh.vertex(b, 0, p1b[0], p1b[1], c1, alpha1);
                EffectMesh.vertex(b, 0, p1a[0], p1a[1], c1, alpha1);
            }
        }
        Tessellator.getInstance().draw();
    }

    /** Short-lived lightning between rim points, regenerated every two ticks. */
    private static void arcs(double radius, double age, float energy) {
        int epoch = (int) Math.floor(age / 2);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int arc = 0; arc < 7; arc++) {
            double start = seed(epoch * 31 + arc) * TAU, span = 0.25 + seed(epoch * 17 + arc * 5) * 0.5;
            double[] previous = polar(radius, start);
            for (int k = 1; k <= 8; k++) {
                double t = k / 8.0, bulge = Math.sin(Math.PI * t) * radius * 0.12;
                double jitter = (seed(epoch * 97 + arc * 13 + k) - 0.5) * radius * 0.08;
                double[] next = polar(radius + bulge + jitter, start + span * t);
                EffectMesh.vertex(b, 0, previous[0], previous[1], WARP_CORE, energy * 0.85F);
                EffectMesh.vertex(b, 0, next[0], next[1], WARP_CORE, energy * 0.85F);
                previous = next;
            }
        }
        Tessellator.getInstance().draw();
    }

    private static double[] polar(double r, double a) { return new double[] {Math.cos(a) * r, Math.sin(a) * r}; }

    static double seed(int key) {
        double value = Math.sin(key * 127.1 + 311.7) * 43758.5453;
        return value - Math.floor(value);
    }

    private static double smooth(double value) { return CarrierFlight.smooth(value); }
}
