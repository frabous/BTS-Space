package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * SR-1 uplink remote: white lofted shell, graphite back, dark glass faceplate, hologram emitter,
 * live status screen and thirteen keys that sink and light on their own. Procedural so the keys
 * animate; drawn in remote pixels around the body centre (+Y emitter, +Z face).
 */
final class RemoteModel {
    static final double[] LIGHT = {0.33, 0.52, 0.8};
    static final int SHELL = 0xE4E9EF, GRAPHITE = 0x343B46, GLASS = 0x151C26, BEZEL = 0x0B1016, METAL = 0x8E99A8,
            LINK = 0x9CE8C3, SCREEN = 0x071422, WHITE = 0xEEF8FF;
    static final double FACE_TOP = 8.35, FACE_BOTTOM = -8.3, FACE_HALF = 2.68;
    private static final double SCREEN_LEFT = -2.4, SCREEN_RIGHT = 2.4, SCREEN_BOTTOM = 5.6, SCREEN_TOP = 7.75;
    /** Loft sections from the butt to the crown: y, half width, front z, back z, chamfer. */
    private static final double[][] SECTIONS = {
            {-9.3, 2.5, 0.72, -0.82, 0.5}, {-8.8, 3.3, 1.05, -1.12, 0.6}, {-4.6, 3.42, 1.05, -1.25, 0.7},
            {1.5, 3.32, 1.05, -1.12, 0.62}, {7.9, 3.45, 1.05, -1.05, 0.6}, {8.85, 3.2, 0.9, -0.92, 0.56},
            {9.3, 2.55, 0.55, -0.62, 0.42}};

    /** Everything that changes between frames; the geometry itself is fixed. */
    static final class Look {
        AttackId selected = AttackId.RAILGUN;
        float[] depress = new float[RemoteKey.KEYS.length];
        float[] flash = new float[RemoteKey.KEYS.length];
        /** Key under the menu focus, haloed; null outside the menu. */
        RemoteKey focus;
        double age;
        /** Hologram emitter output: idle glow at 0, projecting beam at 1. */
        float emitter;
        /** Key legends and screen text, only legible in close views. */
        boolean labels;
    }

    private RemoteModel() { }

    static void draw(Look look) {
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        try {
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            build(new RigMesh(b, false, LIGHT), look);
            Tessellator.getInstance().draw();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            build(new RigMesh(b, true, LIGHT), look);
            Tessellator.getInstance().draw();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            halos(new RigMesh(b, true, LIGHT), look);
            Tessellator.getInstance().draw();
            GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.enableTexture2D();
            if (look.labels) labels(look);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            if (!blend) GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull();
            if (lighting) GlStateManager.enableLighting();
        }
    }

    /** Half width of the shell at a height, for parts that follow the loft. */
    static double halfWidth(double y) {
        for (int i = 0; i + 1 < SECTIONS.length; i++) {
            double[] a = SECTIONS[i], b = SECTIONS[i + 1];
            if (y <= b[0]) return a[1] + (b[1] - a[1]) * Math.max(0, (y - a[0]) / (b[0] - a[0]));
        }
        return SECTIONS[SECTIONS.length - 1][1];
    }

    static int accent(AttackId attack) { return RemoteKey.attack(attack.getFamily().ordinal()).color; }

    private static void build(RigMesh rig, Look look) {
        int accent = accent(look.selected);
        shell(rig, accent);
        faceplate(rig, look, accent);
        for (RemoteKey key : RemoteKey.KEYS) key(rig, key, look);
        emitter(rig, look, accent);
    }

    private static void shell(RigMesh rig, int accent) {
        for (int s = 0; s + 1 < SECTIONS.length; s++) {
            double[][] lower = octagon(SECTIONS[s]), upper = octagon(SECTIONS[s + 1]);
            double middle = (SECTIONS[s][2] + SECTIONS[s][3]) / 2;
            for (int k = 0; k < 8; k++) {
                int n = (k + 1) % 8;
                rig.quad(lower[k], lower[n], upper[n], upper[k], (lower[k][0] + lower[n][0]) / 2, 0,
                        (lower[k][2] + lower[n][2]) / 2 - middle, k >= 2 && k <= 4 ? GRAPHITE : SHELL);
            }
        }
        cap(rig, SECTIONS[0], -1);
        cap(rig, SECTIONS[SECTIONS.length - 1], 1);
        // Light pipes along both flanks carry the selected strike's colour.
        double[] ys = {-7.8, -4.6, 1.5, 7.4};
        for (int side = -1; side <= 1; side += 2) for (int i = 0; i + 1 < ys.length; i++) {
            double x0 = side * (halfWidth(ys[i]) + 0.015), x1 = side * (halfWidth(ys[i + 1]) + 0.015);
            rig.glowQuad(new double[] {x0, ys[i], -0.13}, new double[] {x0, ys[i], 0.13},
                    new double[] {x1, ys[i + 1], 0.13}, new double[] {x1, ys[i + 1], -0.13}, accent, 0.8F);
        }
        // Grip seam on the back.
        rig.box(-2.2, -7.6, -1.32, 2.2, -7.45, -1.0, BEZEL);
        rig.box(-2.2, 6.4, -1.2, 2.2, 6.55, -0.9, BEZEL);
    }

    private static void cap(RigMesh rig, double[] section, int direction) {
        double[][] v = octagon(section);
        double[] centre = {0, section[0], (section[2] + section[3]) / 2};
        for (int k = 0; k < 8; k++) rig.quad(centre, v[k], v[(k + 1) % 8], v[(k + 1) % 8], 0, direction, 0, GRAPHITE);
    }

    /** Chamfered cross-section, counter-clockwise from the front-right corner. */
    private static double[][] octagon(double[] s) {
        double y = s[0], w = s[1], f = s[2], b = s[3], c = s[4];
        return new double[][] {{w - c, y, f}, {w, y, f - c}, {w, y, b + c}, {w - c, y, b},
                {-(w - c), y, b}, {-w, y, b + c}, {-w, y, f - c}, {-(w - c), y, f}};
    }

    private static void faceplate(RigMesh rig, Look look, int accent) {
        double face = RemoteKey.FACE;
        rig.box(-FACE_HALF - 0.1, FACE_BOTTOM - 0.1, 0.85, FACE_HALF + 0.1, FACE_TOP + 0.1, face - 0.04, METAL);
        rig.box(-FACE_HALF, FACE_BOTTOM, 0.9, FACE_HALF, FACE_TOP, face, GLASS);
        // Status screen: bezel, glass, live readout.
        rig.box(SCREEN_LEFT - 0.14, SCREEN_BOTTOM - 0.14, face - 0.02, SCREEN_RIGHT + 0.14, SCREEN_TOP + 0.14, face + 0.03, BEZEL);
        rig.glowQuad(p(SCREEN_LEFT, SCREEN_BOTTOM, face + 0.06), p(SCREEN_RIGHT, SCREEN_BOTTOM, face + 0.06),
                p(SCREEN_RIGHT, SCREEN_TOP, face + 0.06), p(SCREEN_LEFT, SCREEN_TOP, face + 0.06), SCREEN, 1);
        double z = face + 0.09;
        int selectedRow = look.selected.getFamily().ordinal();
        if (!look.labels) {
            glowRect(rig, SCREEN_LEFT + 0.25, 7.2, SCREEN_LEFT + 1.9, 7.45, z, accent, 1);
            glowRect(rig, SCREEN_LEFT + 0.25, 6.55, SCREEN_LEFT + 3.1, 6.8, z, WHITE, 0.75F);
        }
        for (int i = 0; i < RemoteMenuLayout.ROWS; i++) {
            double x = SCREEN_LEFT + 0.25 + i * 0.6;
            glowRect(rig, x, 5.85, x + 0.48, 6.08, z, RemoteKey.attack(i).color, i == selectedRow ? 1 : 0.25F);
        }
        float blink = (float) (0.55 + 0.45 * Math.sin(look.age * 3.2));
        for (int i = 0; i < 4; i++) {
            double x = SCREEN_RIGHT - 1.25 + i * 0.28;
            glowRect(rig, x, 5.85, x + 0.18, 5.85 + 0.12 * (i + 1), z, LINK, i == 3 ? blink : 0.9F);
        }
        double sweep = SCREEN_BOTTOM + (look.age * 0.45 % 1) * (SCREEN_TOP - SCREEN_BOTTOM - 0.1);
        glowRect(rig, SCREEN_LEFT, sweep, SCREEN_RIGHT, sweep + 0.1, z - 0.01, accent, 0.12F);
        // Navigation ring under the arrows and OK.
        rig.push();
        rig.translate(RemoteKey.PAD_X, RemoteKey.PAD_Y, 0);
        rig.rotateY(-Math.PI / 2);
        rig.prism(face - 0.03, face + 0.05, RemoteKey.PAD_RADIUS, RemoteKey.PAD_RADIUS, 24, 0, 0x1C232D, true);
        rig.ring(face, 0.08, RemoteKey.PAD_RADIUS - 0.08, RemoteKey.PAD_RADIUS + 0.05, 24, 0, METAL);
        rig.pop();
        // Link LED and grille.
        rig.glowBox(1.95, 7.98, face - 0.02, 2.3, 8.2, face + 0.08, LINK, blink);
        for (int i = 0; i < 3; i++) rig.box(-1.15, -5.95 - i * 0.42, face - 0.03, 1.15, -5.77 - i * 0.42, face + 0.02, BEZEL);
        glowRect(rig, -1.6, -7.7, 1.6, -7.56, face + 0.01, accent, 0.65F);
    }

    private static void key(RigMesh rig, RemoteKey key, Look look) {
        int i = key.ordinal();
        double face = RemoteKey.FACE, top = key.top(look.depress[i]);
        double x = key.x, y = key.y, hw = key.halfWidth, hh = key.halfHeight;
        boolean selected = key.isAttack() && key.row() == look.selected.getFamily().ordinal();
        float flash = look.flash[i];
        if (key == RemoteKey.FIRE) {
            rig.box(-2.55, y - 0.32, face - 0.02, -2.25, y + 0.32, face + 0.62, GRAPHITE);
            rig.box(2.25, y - 0.32, face - 0.02, 2.55, y + 0.32, face + 0.62, GRAPHITE);
        }
        if (key.round()) {
            rig.push();
            rig.translate(x, y, 0);
            rig.rotateY(-Math.PI / 2);
            rig.prism(face - 0.04, face + 0.04, hw + 0.16, hw + 0.16, 16, 0, BEZEL, true);
            rig.prism(face, top - 0.07, hw, hw, 16, 0, key.color, true);
            rig.prism(top - 0.07, top, hw, hw - 0.1, 16, 0, RigMesh.mix(key.color, WHITE, 0.18), true);
            rig.pop();
            rig.glowBox(x - 0.16, y - 0.16, top - 0.01, x + 0.16, y + 0.16, top + 0.02, 0x9BEFFF, 0.45F + 0.55F * flash);
            return;
        }
        rig.box(x - hw - 0.14, y - hh - 0.14, face - 0.04, x + hw + 0.14, y + hh + 0.14, face + 0.04, BEZEL);
        int base = capColor(key, selected);
        rig.box(x - hw, y - hh, face, x + hw, y + hh, top - 0.08, base);
        rig.box(x - hw + 0.1, y - hh + 0.1, top - 0.08, x + hw - 0.1, y + hh - 0.1, top, RigMesh.mix(base, WHITE, 0.14));
        double z = top + 0.03;
        if (key.isAttack()) {
            glowRect(rig, x - hw + 0.28, y + hh - 0.3, x + hw - 0.28, y + hh - 0.17, z, key.color,
                    Math.min(1, 0.3F + (selected ? 0.7F : 0) + flash));
            if (!look.labels) glyph(rig, key, x, y - 0.08, z, 0.9F);
        } else if (key == RemoteKey.FIRE) {
            glowRect(rig, x - hw + 0.25, y - 0.07, x + hw - 0.25, y + 0.07, z, 0xFF8A78, 0.25F + 0.75F * flash);
        } else if (key.isArrow()) {
            arrow(rig, key, x, y, z, 0.55F + 0.45F * flash);
        } else if (!look.labels) {
            glyph(rig, key, x, y, z, 0.6F + 0.4F * flash);
        }
    }

    private static int capColor(RemoteKey key, boolean selected) {
        if (key.isAttack()) return RigMesh.mix(0x1A212B, key.color, selected ? 0.62 : 0.4);
        return key.color;
    }

    /** Pictograms used when legends are too small to read. */
    private static void glyph(RigMesh rig, RemoteKey key, double x, double y, double z, float alpha) {
        int c = key.isAttack() ? WHITE : 0xC9D6E4;
        switch (key) {
            case RAILGUN:
                glowRect(rig, x - 0.55, y - 0.05, x + 0.55, y + 0.05, z, c, alpha);
                glowRect(rig, x - 0.05, y - 0.3, x + 0.05, y + 0.3, z, c, alpha);
                break;
            case BLACKHOLE:
                glowRect(rig, x - 0.4, y - 0.18, x + 0.4, y - 0.1, z, c, alpha);
                glowRect(rig, x - 0.4, y + 0.1, x + 0.4, y + 0.18, z, c, alpha);
                glowRect(rig, x - 0.12, y - 0.12, x + 0.12, y + 0.12, z, c, alpha);
                break;
            case CARRIER:
                rig.glowQuad(p(x - 0.55, y + 0.1, z), p(x + 0.55, y + 0.18, z), p(x + 0.35, y - 0.1, z), p(x - 0.45, y - 0.12, z), c, alpha);
                break;
            case SUPERNOVA:
                glowRect(rig, x - 0.15, y - 0.15, x + 0.15, y + 0.15, z, c, alpha);
                glowRect(rig, x - 0.45, y - 0.03, x + 0.45, y + 0.03, z, c, alpha * 0.8F);
                glowRect(rig, x - 0.03, y - 0.38, x + 0.03, y + 0.38, z, c, alpha * 0.8F);
                break;
            case GRAVITY:
                // Opposed chevrons: up over down, the field flipping.
                rig.glowQuad(p(x, y + 0.4, z), p(x - 0.36, y + 0.08, z), p(x + 0.36, y + 0.08, z), p(x + 0.36, y + 0.08, z), c, alpha);
                rig.glowQuad(p(x, y - 0.4, z), p(x + 0.36, y - 0.08, z), p(x - 0.36, y - 0.08, z), p(x - 0.36, y - 0.08, z), c, alpha * 0.6F);
                glowRect(rig, x - 0.5, y - 0.03, x + 0.5, y + 0.03, z, c, alpha * 0.8F);
                break;
            case MENU:
                for (int i = -1; i <= 1; i++) glowRect(rig, x - 0.28, y + i * 0.13 - 0.03, x + 0.28, y + i * 0.13 + 0.03, z, c, alpha);
                break;
            case INFO:
                glowRect(rig, x - 0.04, y - 0.2, x + 0.04, y + 0.08, z, c, alpha);
                glowRect(rig, x - 0.04, y + 0.14, x + 0.04, y + 0.22, z, c, alpha);
                break;
            case SKIP:
                for (int i = 0; i < 2; i++) {
                    double ox = x - 0.24 + i * 0.26;
                    rig.glowQuad(p(ox, y - 0.17, z), p(ox + 0.22, y, z), p(ox, y + 0.17, z), p(ox, y + 0.17, z), c, alpha);
                }
                break;
            default: break;
        }
    }

    private static void arrow(RigMesh rig, RemoteKey key, double x, double y, double z, float alpha) {
        double dx = key == RemoteKey.LEFT ? -1 : key == RemoteKey.RIGHT ? 1 : 0;
        double dy = key == RemoteKey.UP ? 1 : key == RemoteKey.DOWN ? -1 : 0;
        double r = 0.2;
        double[] tip = p(x + dx * r, y + dy * r, z);
        double[] left = p(x - dx * r + dy * r, y - dy * r - dx * r, z), right = p(x - dx * r - dy * r, y - dy * r + dx * r, z);
        rig.glowQuad(tip, left, right, right, 0xC9D6E4, alpha);
    }

    private static void emitter(RigMesh rig, Look look, int accent) {
        rig.push();
        rig.translate(0, 9.0, 0);
        rig.rotateZ(Math.PI / 2);
        rig.prism(0, 0.55, 1.45, 1.3, 10, Math.PI / 10, GRAPHITE, true);
        rig.ring(0.42, 0.12, 1.02, 1.44, 10, Math.PI / 10, METAL);
        rig.glowPrism(0.55, 0.63, 1.02, 0.94, 10, Math.PI / 10, accent, 0.55F + 0.45F * look.emitter);
        rig.pop();
        rig.box(-2.2, 8.5, -0.32, -1.72, 9.5, 0.32, GRAPHITE);
        rig.box(1.72, 8.5, -0.32, 2.2, 9.5, 0.32, GRAPHITE);
        rig.glowBox(-2.21, 9.0, -0.12, -1.71, 9.42, 0.12, accent, 0.7F);
        rig.glowBox(1.71, 9.0, -0.12, 2.21, 9.42, 0.12, accent, 0.7F);
    }

    /** Additive light: selected and focused keys, press flashes and the emitter's projection cone. */
    private static void halos(RigMesh rig, Look look) {
        int accent = accent(look.selected);
        float pulse = (float) (0.75 + 0.25 * Math.sin(look.age * 4));
        for (RemoteKey key : RemoteKey.KEYS) {
            int i = key.ordinal();
            boolean selected = key.isAttack() && key.row() == look.selected.getFamily().ordinal();
            float alpha = look.flash[i] * 0.45F + (selected ? 0.12F : 0) + (key == look.focus ? 0.22F * pulse : 0);
            if (alpha <= 0.01F) continue;
            double grow = 0.35 + 0.25 * look.flash[i], z = key.top(look.depress[i]) + 0.05;
            glowRect(rig, key.x - key.halfWidth - grow, key.y - key.halfHeight - grow,
                    key.x + key.halfWidth + grow, key.y + key.halfHeight + grow, z, key.isAttack() ? key.color : 0x9BEFFF, alpha);
        }
        if (look.emitter > 0.01F) {
            double y0 = 9.63, y1 = 9.63 + 6 * look.emitter;
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4, c = Math.cos(a), s = Math.sin(a);
                rig.glowQuad(p(-c * 0.9, y0, -s * 0.9), p(c * 0.9, y0, s * 0.9), p(c * 3.2, y1, s * 3.2), p(-c * 3.2, y1, -s * 3.2),
                        accent, 0.16F * look.emitter);
            }
        }
    }

    private static void labels(Look look) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableAlpha();
        for (RemoteKey key : RemoteKey.KEYS) {
            String legend = legend(key);
            if (legend == null) continue;
            double top = key.top(look.depress[key.ordinal()]) + 0.04;
            int color = key.isAttack() || key == RemoteKey.FIRE ? WHITE : 0xC9D6E4;
            double y = key.isAttack() ? key.y - 0.1 : key.y;
            text(font, legend, key.x, y, top, key.halfWidth * 2 - 0.35, key.halfHeight * 1.25, color, true);
        }
        AttackId attack = look.selected;
        double z = RemoteKey.FACE + 0.1;
        text(font, attack.getDisplayId(), SCREEN_LEFT + 0.25, 7.3, z, 2.4, 0.5, accent(attack), false);
        text(font, attack.getDisplayName().toUpperCase(java.util.Locale.ROOT), SCREEN_LEFT + 0.25, 6.62, z, 4.5, 0.4, WHITE, false);
        text(font, "SR-1", -2.45, 8.1, RemoteKey.FACE + 0.02, 1.2, 0.3, METAL, false);
    }

    private static String legend(RemoteKey key) {
        switch (key) {
            case RAILGUN: return ClientControls.RAILGUN.getDisplayName();
            case BLACKHOLE: return ClientControls.BLACKHOLE.getDisplayName();
            case CARRIER: return ClientControls.CARRIER.getDisplayName();
            case SUPERNOVA: return ClientControls.SUPERNOVA.getDisplayName();
            case GRAVITY: return ClientControls.GRAVITY.getDisplayName();
            case MENU: return ClientControls.MENU.getDisplayName();
            case SKIP: return ClientControls.SKIP.getDisplayName();
            case INFO: return "i";
            case FIRE: return "FIRE";
            case OK: return "OK";
            default: return null;
        }
    }

    /** Text on the face plane; centred around (x, y) or anchored at its top-left, shrunk to fit. */
    private static void text(FontRenderer font, String value, double x, double y, double z, double maxWidth, double height,
                             int color, boolean centred) {
        int width = Math.max(1, font.getStringWidth(value));
        double scale = Math.min(height / 7.0, maxWidth / width);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(scale, -scale, scale);
        if (centred) font.drawString(value, -width / 2, -4, 0xFF000000 | color);
        else font.drawString(value, 0, 0, 0xFF000000 | color);
        GlStateManager.popMatrix();
    }

    private static void glowRect(RigMesh rig, double x0, double y0, double x1, double y1, double z, int color, float alpha) {
        rig.glowQuad(p(x0, y0, z), p(x1, y0, z), p(x1, y1, z), p(x0, y1, z), color, alpha);
    }

    private static double[] p(double x, double y, double z) { return new double[] {x, y, z}; }
}
