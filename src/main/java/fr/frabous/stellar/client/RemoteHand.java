package fr.frabous.stellar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EnumPlayerModelParts;
import org.lwjgl.opengl.GL11;

/**
 * The holding hand in the player's own skin: forearm behind the remote, four fingers curled
 * around the far flank and a three-joint thumb posed by {@link RemoteThumb}. Blocky like the
 * vanilla arm, shaded like {@link RemoteModel}. Built for the right hand and mirrored for the left.
 */
final class RemoteHand {
    private static final double[] LIGHT = normalize(RemoteModel.LIGHT[0], RemoteModel.LIGHT[1], RemoteModel.LIGHT[2]);
    /** Palm centre behind the body at zero grip. */
    private static final double[] PALM = {2.4, RemoteThumb.BASE[1], -2.9};
    /** Forearm direction towards the elbow, out of the screen. */
    private static final double[] FOREARM = normalize(0.22, -0.92, 0.33);
    /** Per finger, index to little: height offset from the palm and how far the tip curls onto the flank. */
    private static final double[][] FINGERS = {{1.5, 0.25}, {0.25, 0.42}, {-0.98, 0.25}, {-2.15, -0.1}};
    private static final double[] FACE_UP = {0, 0, 1}, ACROSS = {1, 0, 0};
    private static final int NAIL = 0xF1DAD0;

    private RemoteHand() { }

    static void draw(AbstractClientPlayer player, RemoteThumb thumb, double forearmLength) {
        int side = thumb.side();
        boolean right = side > 0, slim = "slim".equals(player.getSkinType());
        double width = slim ? 3 : 4, grip = thumb.grip();
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        Minecraft.getMinecraft().getTextureManager().bindTexture(player.getLocationSkin());
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1, 1, 1, 1);
        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder b = tessellator.getBuffer();
            int u = right ? 40 : 32, v = right ? 16 : 48;
            double[] patch = {u + 4 + width + 0.5, v + 0.5, u + 4 + 2 * width - 0.5, v + 3.5};
            double[] palm = {PALM[0], PALM[1] + grip, PALM[2]};
            double[] wrist = add(palm, FOREARM, -2.2), elbow = add(palm, FOREARM, forearmLength - 2.2);
            GlStateManager.disableBlend();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            beam(b, side, wrist, elbow, width / 2, 2, FACE_UP, arm(u, v, width, right));
            fingers(b, side, grip, patch);
            double[] nail = thumb(b, side, thumb, grip, patch);
            tessellator.draw();
            GlStateManager.disableTexture2D();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            float shade = (float) Math.min(1, RigMesh.level(LIGHT, nail[12] * side, nail[13], nail[14]));
            for (int i = 0; i < 4; i++)
                b.pos(nail[i * 3] * side, nail[i * 3 + 1], nail[i * 3 + 2]).color((NAIL >> 16 & 255) / 255F * shade,
                        (NAIL >> 8 & 255) / 255F * shade, (NAIL & 255) / 255F * shade, 1F).endVertex();
            tessellator.draw();
            GlStateManager.enableTexture2D();
            if (player.isWearing(right ? EnumPlayerModelParts.RIGHT_SLEEVE : EnumPlayerModelParts.LEFT_SLEEVE)) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                beam(b, side, add(wrist, FOREARM, -0.25), add(elbow, FOREARM, 0.25), width / 2 + 0.25, 2.25, FACE_UP,
                        arm(right ? 40 : 48, right ? 32 : 48, width, right));
                tessellator.draw();
            }
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull();
            if (lighting) GlStateManager.enableLighting();
        }
    }

    private static void fingers(BufferBuilder b, int side, double grip, double[] patch) {
        double[][] faces = uniform(patch);
        for (double[] finger : FINGERS) {
            double y = PALM[1] + grip + finger[0];
            double[] knuckle = {1.1, y, -2.45}, bend = {-2.85, y, -2.1}, tip = {-3.72, y, finger[1]};
            beam(b, side, knuckle, bend, 0.62, 0.62, FACE_UP, faces);
            beam(b, side, bend, tip, 0.6, 0.6, ACROSS, faces);
        }
    }

    /** Palm mass, proximal and distal phalanges; returns the nail quad (four corners, then its normal). */
    private static double[] thumb(BufferBuilder b, int side, RemoteThumb thumb, double grip, double[] patch) {
        double[][] faces = uniform(patch);
        double[] base = unmirror(thumb.base(), side), knuckle = unmirror(thumb.knuckle(), side), tip = unmirror(thumb.tip(), side);
        double[] root = {2.75, PALM[1] + grip - 0.8, -2.0};
        double[] reach = sub(tip, knuckle);
        double[] axis = normalize(reach[0], reach[1], reach[2]);
        double[] end = add(tip, axis, 0.3);
        beam(b, side, root, base, 1.2, 1.05, ACROSS, faces);
        beam(b, side, base, knuckle, 0.82, 0.72, FACE_UP, faces);
        beam(b, side, knuckle, end, 0.72, 0.62, FACE_UP, faces);
        double[] up = sub(FACE_UP, scale(axis, dot(FACE_UP, axis)));
        up = dot(up, up) < 1.0e-6 ? new double[] {0, 1, 0} : normalize(up[0], up[1], up[2]);
        double[] across = cross(axis, up), centre = add(add(end, axis, -0.5), up, 0.635);
        double[] nail = new double[15];
        double[][] corners = {add(add(centre, across, -0.5), axis, -0.42), add(add(centre, across, 0.5), axis, -0.42),
                add(add(centre, across, 0.5), axis, 0.42), add(add(centre, across, -0.5), axis, 0.42)};
        for (int i = 0; i < 4; i++) System.arraycopy(corners[i], 0, nail, i * 3, 3);
        System.arraycopy(up, 0, nail, 12, 3);
        return nail;
    }

    /** Vanilla arm layout: elbow end, hand end, outer, front, inner, back; side faces run elbow to hand. */
    private static double[][] arm(int u, int v, double width, boolean right) {
        double outer = right ? u : u + 4 + width, inner = right ? u + 4 + width : u;
        return new double[][] {
                {u + 4, v, u + 4 + width, v + 4},
                {u + 4 + width, v, u + 4 + 2 * width, v + 4},
                {outer, v + 4, outer + 4, v + 16},
                {u + 4, v + 4, u + 4 + width, v + 16},
                {inner, v + 4, inner + 4, v + 16},
                {u + 8 + width, v + 4, u + 8 + 2 * width, v + 16}};
    }

    private static double[][] uniform(double[] patch) { return new double[][] {patch, patch, patch, patch, patch, patch}; }

    /**
     * Box from {@code a} to {@code e} with half extents across and deep; {@code up} orients the deep axis.
     * Faces follow {@link #arm}: end at e, end at a, -across, +up, +across, -up.
     */
    private static void beam(BufferBuilder b, int side, double[] a, double[] e, double across, double deep, double[] up,
                             double[][] uv) {
        double[] d = sub(e, a);
        double length = Math.sqrt(dot(d, d));
        if (length < 1.0e-6) return;
        double[] axis = {d[0] / length, d[1] / length, d[2] / length};
        double[] u = sub(up, scale(axis, dot(up, axis)));
        if (dot(u, u) < 1.0e-6) u = sub(ACROSS, scale(axis, dot(ACROSS, axis)));
        if (dot(u, u) < 1.0e-6) u = sub(new double[] {0, 1, 0}, scale(axis, axis[1]));
        u = normalize(u[0], u[1], u[2]);
        double[] s = cross(axis, u);
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double[] origin = (i & 4) != 0 ? e : a;
            double sa = (i & 1) != 0 ? across : -across, su = (i & 2) != 0 ? deep : -deep;
            c[i] = add(add(origin, s, sa), u, su);
        }
        // Corner index bits: 1 = +across, 2 = +up, 4 = far end. Side faces unfold as outer, front, inner, back.
        face(b, side, c[4], c[5], c[7], c[6], axis, uv[0]);
        face(b, side, c[2], c[3], c[1], c[0], scale(axis, -1), uv[1]);
        face(b, side, c[4], c[6], c[2], c[0], scale(s, -1), uv[2]);
        face(b, side, c[6], c[7], c[3], c[2], u, uv[3]);
        face(b, side, c[7], c[5], c[1], c[3], s, uv[4]);
        face(b, side, c[5], c[4], c[0], c[1], scale(u, -1), uv[5]);
    }

    /** Quad corners map to the rect's top-left, top-right, bottom-right, bottom-left. */
    private static void face(BufferBuilder b, int side, double[] p0, double[] p1, double[] p2, double[] p3, double[] normal, double[] uv) {
        float shade = (float) Math.min(1, RigMesh.level(LIGHT, normal[0] * side, normal[1], normal[2]));
        vertex(b, side, p0, uv[0], uv[1], shade);
        vertex(b, side, p1, uv[2], uv[1], shade);
        vertex(b, side, p2, uv[2], uv[3], shade);
        vertex(b, side, p3, uv[0], uv[3], shade);
    }

    private static void vertex(BufferBuilder b, int side, double[] p, double u, double v, float shade) {
        b.pos(p[0] * side, p[1], p[2]).tex(u / 64.0, v / 64.0).color(shade, shade, shade, 1F).endVertex();
    }

    private static double[] unmirror(double[] p, int side) { return new double[] {p[0] * side, p[1], p[2]}; }
    private static double[] add(double[] a, double[] b, double k) { return new double[] {a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k}; }
    private static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double[] scale(double[] a, double k) { return new double[] {a[0] * k, a[1] * k, a[2] * k}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
