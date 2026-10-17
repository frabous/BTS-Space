package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackFamily;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumHandSide;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

final class SkillMenu extends GuiScreen {
    /** One card per family; a carrier card holds every payload the destroyer can deliver. */
    private static final AttackFamily[] FAMILIES = AttackFamily.values();
    private static final int WHITE = 0xEEF8FF, MUTED = 0x8FA8BF, DIM = 0x637C91, AMBER = 0xFFD18A,
            LINK = 0x9CE8C3, DANGER = 0xFF6B6B, GLASS = 0x0A1624, DEEP = 0x02070F;
    private static final int[] ACCENTS = {RemoteKey.RAILGUN.color, RemoteKey.BLACKHOLE.color, RemoteKey.CARRIER.color,
            RemoteKey.SUPERNOVA.color, RemoteKey.GRAVITY.color};
    private static final RemoteThumb THUMB = RemoteFirstPerson.THUMB;
    private static final String[] STAT_LABELS = {"OUTPUT", "REACH", "SPAN"};
    private static final double[][] LEVELS = {{0.80, 0.32, 0.62}, {0.58, 0.74, 1}, {0.86, 0.32, 0.30}, {1, 1, 0.30}, {0.72, 0.82, 0.48}};
    private final RemoteMenuLayout layout = new RemoteMenuLayout();
    private final RemoteMenuAudio audio = new RemoteMenuAudio();
    private final RemoteModel.Look look = new RemoteModel.Look();
    private final FloatBuffer transform = BufferUtils.createFloatBuffer(16);
    private final float[] hover = new float[RemoteMenuLayout.ROWS];
    private final float[] weight = new float[RemoteMenuLayout.ROWS];
    private long openedAt, lastFrame, closingAt;
    private int hovered = -1, focused;
    private RemoteKey keyUnderMouse;
    private AttackId linked = AttackId.RAILGUN, preview = AttackId.RAILGUN;
    private boolean details, initialized;
    private double mouseLeanX, mouseLeanY, lastMouseX = -1, lastMouseY = -1;
    private float opacity = 1, closeHover, briefHover;

    @Override public void initGui() {
        if (initialized) return; // Resizing must not replay the opening cue or restart the animation.
        initialized = true;
        openedAt = lastFrame = System.nanoTime();
        // The remote stands on the side of the hand holding it.
        EnumHandSide grip = mc.player.getPrimaryHand();
        if (LaunchController.remote(mc.player) != mc.player.getHeldItemMainhand()) grip = grip.opposite();
        layout.side(grip == EnumHandSide.RIGHT ? 1 : -1);
        linked = ItemStellarRemote.selected(LaunchController.remote(mc.player));
        preview = linked; focused = row(linked);
        weight[focused] = 1;
        THUMB.hover(RemoteKey.attack(focused));
        audio.play("open", 0.36F, 1);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void updateScreen() {
        if (mc.world == null || mc.player == null || LaunchController.remote(mc.player).isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (closingAt != 0 && secondsSince(closingAt) >= RemoteMenuLayout.CLOSE_SECONDS)
            mc.displayGuiScreen(null);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0, (now - lastFrame) / 1.0e9));
        lastFrame = now;
        double age = secondsSince(openedAt), opening = RemoteMenuLayout.ease(age / RemoteMenuLayout.OPEN_SECONDS);
        double closing = closingAt == 0 ? 0 : RemoteMenuLayout.ease(secondsSince(closingAt) / RemoteMenuLayout.CLOSE_SECONDS);
        opacity = (float) (opening * (1 - closing));
        double follow = 1 - Math.exp(-dt * 9);
        mouseLeanX += (Math.max(-1, Math.min(1, mouseX * 2.0 / width - 1)) - mouseLeanX) * follow;
        mouseLeanY += (Math.max(-1, Math.min(1, mouseY * 2.0 / height - 1)) - mouseLeanY) * follow;
        layout.update(width, height, layout.yaw(opening, mouseLeanX),
                3 + mouseLeanY * 3, opening, closing, Math.sin(age * 1.35) * 1.4);
        layout.poseRemote(mouseLeanX, mouseLeanY, 46 * (1 - opening) * (1 - opening) + 30 * closing);
        boolean ready = closingAt == 0 && age >= RemoteMenuLayout.OPEN_SECONDS;
        keyUnderMouse = ready ? layout.keyAt(mouseX, mouseY) : null;
        int underMouse = closingAt == 0 ? layout.rowAt(mouseX, mouseY, age, hover) : -1;
        if (underMouse < 0 && keyUnderMouse != null) underMouse = keyUnderMouse.row();
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            hovered = underMouse;
            if (hovered >= 0) focus(hovered, false);
        } else if (hovered >= 0) hovered = underMouse;
        lastMouseX = mouseX; lastMouseY = mouseY;
        audio.hover(hovered, now);
        double fast = 1 - Math.exp(-dt * 14), smooth = 1 - Math.exp(-dt * 8);
        for (int row = 0; row < hover.length; row++) {
            hover[row] += ((row == focused ? 1 : 0) - hover[row]) * fast;
            weight[row] += ((row == row(preview) ? 1 : 0) - weight[row]) * smooth;
        }
        boolean live = closingAt == 0 && age >= RemoteMenuLayout.OPEN_SECONDS;
        closeHover += ((live && layout.closeAt(mouseX, mouseY) ? 1 : 0) - closeHover) * fast;
        briefHover += ((live && layout.briefingAt(mouseX, mouseY) ? 1 : 0) - briefHover) * fast;
        int accent = accent();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            drawGradientRect(0, 0, width, height, argb(0x06101C, 0.66F), argb(0x01040B, 0.88F));
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha(); // Faint glass layers sit below the GUI alpha-test threshold.
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            blend(false);
            gradient(0, 0, width * 0.32, height, DEEP, 0.5F, DEEP, 0, false);
            gradient(width * 0.68, 0, width * 0.32, height, DEEP, 0, DEEP, 0.5F, false);
            gradient(0, height * 0.72, width, height * 0.28, DEEP, 0, DEEP, 0.6F, true);
            GlStateManager.pushMatrix();
            transform.clear(); transform.put(layout.matrix()).flip();
            GL11.glMultMatrix(transform);
            drawBackdrop(age, accent);
            drawHeader(age, accent);
            for (int row = 0; row < FAMILIES.length; row++) drawCard(row, age);
            GlStateManager.pushMatrix();
            GlStateManager.translate(layout.panelShift(), 0, 0);
            drawProjection(age, accent);
            GlStateManager.popMatrix();
            drawFooter(age);
            GlStateManager.popMatrix();
            drawLinks(age);
            drawRemote(age);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private void drawBackdrop(double age, int accent) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, -65);
        arc(10, 1, 337, 150, age * 4, age * 4 + 255, accent, 0.14F);
        arc(10, 1, 348, 159, 195 - age * 3, 320 - age * 3, AMBER, 0.15F);
        for (int i = 0; i < 72; i++) {
            double a = Math.toRadians(i * 5 + age * 2), c = Math.cos(a), s = Math.sin(a);
            boolean major = i % 6 == 0;
            double inner = major ? 0.94 : 0.97;
            line(10 + c * 326, 1 + s * 141, 10 + c * 326 * inner, 1 + s * 141 * inner, WHITE, major ? 0.14F : 0.06F);
        }
        for (int i = 0; i < 46; i++) {
            double x = -342 + (i * 109 % 684) + Math.sin(age * 0.05 + i) * 3, y = -172 + (i * 71 % 344);
            float twinkle = (float) (0.5 + 0.5 * Math.sin(age * (0.9 + i % 5 * 0.37) + i * 1.7));
            double size = i % 7 == 0 ? 2.2 : 1.2;
            fill(x, y + Math.sin(age * 0.7 + i) * 4, size, size, i % 3 == 0 ? accent : WHITE, 0.04F + 0.14F * twinkle);
        }
        GlStateManager.popMatrix();
        // Column behind the hand-held remote: soft key light and a projection base under the emitter.
        double x = layout.remoteX();
        glow(x, -10, 92, accent, 0.13F);
        arc(x, -112, 30, 6, 0, 360, accent, 0.32F);
        arc(x, -112, 22 + 4 * Math.sin(age * 2.4), 4, age * 60, age * 60 + 250, WHITE, 0.25F);
        text("SR-1", x - 40, -134, MUTED, 0.75F);
        textRight("UPLINK", x + 40, -134, DIM, 0.6F);
    }

    private void drawHeader(double age, int accent) {
        double left = layout.cardsLeft(), span = RemoteMenuLayout.CARD_WIDTH;
        text("BEYOND THE SPACE", left + 0.7, -173.3, accent, 1.7F, 0.35F);
        text("BEYOND THE SPACE", left, -174, WHITE, 1.7F);
        text("REMOTE // ORBITAL ARRAY", left + 1, -150, MUTED, 0.9F);
        fill(left, -128, span, 1, WHITE, 0.14F);
        for (int i = 0; i <= 24; i++) {
            boolean major = i % 4 == 0;
            fill(left + i * (span - 1) / 24, major ? -132 : -130, 1, major ? 4 : 2, WHITE, major ? 0.28F : 0.14F);
        }
        fill(left, -128.5, 48, 2, AMBER, 1);
        fill(left + 58 + previewIndex() * 40, -128.5, 30, 2, accent, 0.95F);
        text("SELECT A STRIKE", left + 1, -119, MUTED, 0.75F);
        textRight(String.format(Locale.ROOT, "%02d / %02d", focused + 1, FAMILIES.length), left + span, -119, accent, 0.75F);

        GlStateManager.pushMatrix();
        GlStateManager.translate(layout.panelShift(), 0, 0);
        float blink = (float) (0.55 + 0.45 * Math.sin(age * 3.2));
        glow(43, -165, 8, LINK, 0.4F * blink);
        disc(43, -165, 2.3, LINK, 0.95F);
        text("REMOTE LINK", 52, -169, WHITE, 1);
        for (int i = 0; i < 5; i++) {
            double barHeight = 3 + i * 2;
            fill(126 + i * 4, -161 - barHeight, 2.5, barHeight, LINK, i == 4 ? 0.3F + 0.6F * blink : 0.85F);
        }
        text("DIM " + (mc.player == null ? "--" : mc.player.dimension) + " / SIGNAL ESTABLISHED", 40, -150, MUTED, 0.8F);
        textRight("UPLINK " + clock(age), 318, -126, DIM, 0.7F);
        GlStateManager.popMatrix();
        int closeColor = mix(WHITE, DANGER, closeHover);
        panel(295, -170, 23, 21, closeColor, 0.05F + 0.18F * closeHover, 4);
        outline(295, -170, 23, 21, closeColor, 0.3F + 0.6F * closeHover, 4);
        text("x", 303, -165, closeColor, 1);
    }

    private void drawCard(int row, double age) {
        AttackFamily family = FAMILIES[row];
        AttackId attack = rowAttack(row);
        double x = layout.cardX(age, row, hover[row]), y = RemoteMenuLayout.cardY(row);
        float saved = opacity, h = hover[row];
        opacity *= (float) RemoteMenuLayout.reveal(age, row);
        boolean selected = linked.getFamily() == family;
        int accent = ACCENTS[row], edge = selected ? LINK : accent;
        double w = RemoteMenuLayout.CARD_WIDTH, ch = RemoteMenuLayout.CARD_HEIGHT, mid = ch / 2, right = x + w - 8;
        panel(x + 3, y + 5, w, ch, DEEP, 0.35F);
        panel(x, y, w, ch, GLASS, 0.80F + h * 0.12F);
        gradient(x + 3, y + 3, 170, ch - 6, accent, 0.08F + 0.22F * h, accent, 0, false);
        if (h > 0.01F) shimmer(x, y, age, row, h);
        outline(x, y, w, ch, edge, 0.16F + 0.6F * h + (selected ? 0.2F : 0), 5);
        double bar = 12 + 17 * h + (selected ? 5 : 0), socket = layout.socketX(x);
        fill(layout.side() > 0 ? x + w - 2 : x, y + mid - bar / 2, 2, bar, edge, 0.55F + 0.45F * h);
        slant(x + 10, y + 8, 30, 24, 5, accent, 0.10F + 0.40F * h);
        text(String.format(Locale.ROOT, "%02d", row + 1), x + 19, y + 16, mix(accent, WHITE, h), 0.9F);
        text(family.getTitle(), x + 48, y + 8, WHITE, 1.08F);
        text(subtitle(family, attack), x + 48, y + 25, mix(MUTED, accent, 0.25F + 0.45F * h), 0.7F);
        if (family.attacks().size() > 1 || family == AttackFamily.CARRIER) payloads(family, attack, x + 48 + width(family.getTitle(), 1.08F) + 8, y + 8.5, accent, h);
        String key = attackBindings()[row].getDisplayName();
        keycap(key, right - keycapWidth(key), y + 6, mix(MUTED, WHITE, h), 1);
        if (selected) {
            double chip = width("LINKED", 0.62F) + 10;
            panel(right - chip, y + 23, chip, 11, LINK, 0.88F, 2);
            text("LINKED", right + 5 - chip, y + 25.5, 0x0B2A1E, 0.62F);
        } else textRight(family.getDisplayId(), right, y + 25.5, DIM, 0.65F);
        if (h > 0.01F) {
            gradient(x + 8, y + ch - 2, (w - 16) * h, 1, accent, 0.95F, accent, 0.1F, false);
            // Chevron pointing at the projection panel, on the edge away from the socket.
            double base = layout.side() > 0 ? x - 4 : x + w + 4, tip = base - layout.side() * 5;
            line(base, y + 9, tip, y + mid, accent, h);
            line(tip, y + mid, base, y + ch - 9, accent, h);
        }
        // Socket for the remote's link line; lit when the matching key is focused or linked.
        double ny = y + mid;
        disc(socket, ny, 2.6, 0x07111C, 0.95F);
        arc(socket, ny, 3.6, 3.6, 0, 360, edge, selected ? 0.9F : 0.3F + 0.6F * h);
        opacity = saved;
    }

    private void shimmer(double x, double y, double age, int row, float strength) {
        double w = RemoteMenuLayout.CARD_WIDTH;
        double t = (age * 0.55 + row * 0.31) % 1, center = x - 40 + t * (w + 88);
        float alpha = (float) (0.13 * strength * Math.sin(t * Math.PI));
        double left = x + 3, right = x + w - 3;
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            double outer = center + side * 16;
            vertex(b, clamp(center, left, right), y + 3, WHITE, alpha);
            vertex(b, clamp(outer, left, right), y + 3, WHITE, 0);
            vertex(b, clamp(outer - 14, left, right), y + RemoteMenuLayout.CARD_HEIGHT - 3, WHITE, 0);
            vertex(b, clamp(center - 14, left, right), y + RemoteMenuLayout.CARD_HEIGHT - 3, WHITE, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private void drawProjection(double age, int accent) {
        double px = 34, py = -112, pw = 280, ph = 230;
        panel(px + 4, py + 6, pw, ph, DEEP, 0.35F);
        panel(px, py, pw, ph, 0x07131F, 0.88F);
        gradient(px + 3, py + 3, pw - 6, 80, accent, 0.11F, accent, 0, true);
        for (int i = 1; i < 14; i++) fill(px + i * 20, py + 4, 1, ph - 8, WHITE, 0.022F);
        for (int i = 1; i < 12; i++) fill(px + 4, py + i * 20 - 5, pw - 8, 1, WHITE, 0.022F);
        double sweep = py + 6 + age * 0.3 % 1 * (ph - 30);
        gradient(px + 3, sweep, pw - 6, 18, accent, 0, accent, 0.07F, true);
        fill(px + 3, sweep + 18, pw - 6, 1, accent, 0.16F);
        outline(px, py, pw, ph, accent, 0.16F, 5);
        brackets(px, py, pw, ph, accent, 12 + 2.5 * Math.sin(age * 2.2));
        disc(51, -96.5, 1.8, accent, (float) (0.6 + 0.4 * Math.sin(age * 4)));
        text(details ? "MISSION BRIEF" : "LIVE PROJECTION", 57, -99, MUTED, 0.8F);
        textRight(preview.getDisplayId(), 300, -99, accent, 0.8F);
        if (details) {
            text(title(preview), 48, -80, WHITE, 1.12F);
            fill(48, -68, 40, 1, accent, 0.9F);
            wrapped(description(preview), 48, -57, 250, 0xD6E6F2, 10);
            if (preview == AttackId.SUPERNOVA) hazard(48, 50, 252, 5, age);
            text("CREATIVE / SPECTATOR: IMMUNE", 48, 62, MUTED, 0.75F);
            text(ClientControls.SKIP.getDisplayName() + ": CAMERA EFFECTS ON / OFF", 48, 74, MUTED, 0.75F);
        } else {
            double cx = 174, cy = -42;
            glow(cx, cy, 74, accent, 0.18F);
            radar(cx, cy, 58, age * 55, accent, 0.18F);
            arc(cx, cy, 66, 22, age * 15, age * 15 + 320, accent, 0.28F);
            arc(cx, cy, 55, 55, 30 - age * 8, 280 - age * 8, accent, 0.30F);
            arc(cx, cy, 61, 61, 220 + age * 12, 310 + age * 12, WHITE, 0.45F);
            for (int i = 0; i < 36; i++) {
                double a = Math.toRadians(i * 10 - age * 6), c = Math.cos(a), s = Math.sin(a);
                line(cx + c * 47, cy + s * 47, cx + c * 50, cy + s * 50, WHITE, i % 9 == 0 ? 0.4F : 0.12F);
            }
            glyph(preview, cx, cy, 36, age, accent, 0.95F);
            line(70, cy, 98, cy, accent, 0.35F); line(250, cy, 278, cy, accent, 0.35F);
            if (preview.getFamily() == AttackFamily.CARRIER) text(payloadLabel(preview), 48, 16, accent, 0.62F);
            text(title(preview), 48.6, 26.6, accent, 1.15F, 0.3F);
            text(title(preview), 48, 26, WHITE, 1.15F);
            for (int stat = 0; stat < 3; stat++) statBar(stat, 42 + stat * 11, accent);
            text("TERRAIN PRESERVED", 48, 77, DIM, 0.6F);
        }
        panel(48, 88, 252, 22, accent, 0.07F + 0.17F * briefHover, 4);
        outline(48, 88, 252, 22, accent, 0.28F + 0.55F * briefHover, 4);
        gradient(50, 90, 140 * briefHover, 18, accent, 0.18F * briefHover, accent, 0, false);
        text(details ? "< BACK TO PROJECTION" : "OPEN MISSION BRIEF >", 58 + 3 * briefHover, 95,
                mix(accent, WHITE, briefHover), 0.8F);
        String key = details ? "ESC" : "RMB";
        keycap(key, 294 - keycapWidth(key), 93.5, DIM, 1);
    }

    private void statBar(int stat, double y, int accent) {
        text(STAT_LABELS[stat], 48, y, MUTED, 0.62F);
        double level = 0, total = 0;
        for (int row = 0; row < weight.length; row++) { level += LEVELS[row][stat] * weight[row]; total += weight[row]; }
        level = total > 1.0e-4 ? level / total : LEVELS[row(preview)][stat];
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            float lit = (float) Math.max(0, Math.min(1, level * segments - i));
            fill(92 + i * 12.5, y + 0.5, 10, 4, WHITE, 0.07F);
            if (lit > 0) fill(92 + i * 12.5, y + 0.5, 10, 4, accent, 0.9F * lit);
        }
        textRight(stats(preview)[stat], 300, y, preview == AttackId.SUPERNOVA ? AMBER : WHITE, 0.65F);
    }

    private void drawFooter(double age) {
        boolean disconnecting = closingAt != 0;
        int state = disconnecting ? DANGER : LINK;
        double left = layout.cardsLeft(), panel = layout.panelShift();
        fill(left, 123, RemoteMenuLayout.CARD_WIDTH, 1, WHITE, 0.14F);
        fill(left, 122.5, 48, 2, state, 0.9F);
        glow(left + 4, 135.5, 6, state, (float) (0.25 + 0.2 * Math.sin(age * 3.2)));
        disc(left + 4, 135.5, 1.8, state, 1);
        text((disconnecting ? "DISCONNECTING / " : "LINKED / ") + title(linked), left + 10, 132.5, state, 0.8F);
        hint("ENTER", "SELECT", hint("ARROWS", "NAVIGATE", left + 1, 152), 152);
        double x = hint("LMB", "SELECT", 40 + panel, 130);
        x = hint("RMB", "BRIEFING", x, 130);
        boolean films = LocalClientBridge.filmsEnabled;
        hint(ClientControls.SKIP.getDisplayName(), films ? "FILMS ON" : "FILMS OFF", x, 130);
        x = keycap(ClientControls.MENU.getDisplayName(), 40 + panel, 152, WHITE, 0.9F) + 3;
        x = keycap("ESC", x, 152, WHITE, 0.9F);
        text("CLOSE", x + 5, 154.5, MUTED, 0.75F);
    }

    private double hint(String key, String label, double x, double y) {
        double end = keycap(key, x, y, WHITE, 0.9F);
        text(label, end + 5, y + 2.5, MUTED, 0.75F);
        return end + 5 + width(label, 0.75F) + 12;
    }

    private double keycapWidth(String label) { return width(label, 0.75F) + 8; }

    private double keycap(String label, double x, double y, int color, float alpha) {
        double w = keycapWidth(label);
        panel(x, y, w, 11, color, 0.07F * alpha, 2);
        outline(x, y, w, 11, color, 0.45F * alpha, 2);
        text(label, x + 4, y + 2.5, color, 0.75F, alpha);
        return x + w;
    }

    private void glyph(AttackId attack, double x, double y, double r, double age, int color, float alpha) {
        if (attack == AttackId.RAILGUN) {
            arc(x, y, r * 0.66, r * 0.66, 0, 360, color, alpha * 0.65F);
            line(x - r, y + r * 0.55, x + r, y - r * 0.55, color, alpha);
            line(x - r * 0.75, y + r * 0.65, x + r * 0.65, y - r * 0.15, WHITE, alpha * 0.7F);
            line(x - r * 0.3, y - r * 0.7, x + r * 0.3, y + r * 0.7, color, alpha);
            disc(x, y, Math.max(1.2, r * 0.09), WHITE, alpha);
        } else if (attack == AttackId.BLACKHOLE) {
            for (int ring = 0; ring < 3; ring++)
                arc(x, y, r * (0.7 + ring * 0.15), r * (0.22 + ring * 0.08),
                        age * (22 + ring * 9), 300 + age * (22 + ring * 9), AMBER, alpha * (0.9F - ring * 0.2F));
            disc(x, y, r * 0.37, 0x020811, 1);
            arc(x, y, r * 0.39, r * 0.39, 0, 360, WHITE, alpha);
            double a = age * 0.8;
            disc(x + Math.cos(a) * r, y + Math.sin(a) * r * 0.38, Math.max(1.2, r * 0.045), WHITE, alpha);
        } else if (attack == AttackId.GRAVITY_INVERSION) {
            // One loop of the strike: fragments tear free, orbit the tilted axis, hang, then slam into a ring.
            double t = age * 0.32 % 1, gy = y + r * 0.72;
            double rise = smoothstep(t / 0.35), orbit = smoothstep((t - 0.35) / 0.3) * Math.PI * 1.4;
            double slam = t < 0.7 ? 0 : Math.min(1, (t - 0.7) / 0.06), shock = t < 0.76 ? 0 : (t - 0.76) / 0.24;
            line(x - r, gy, x + r, gy, WHITE, alpha * 0.55F);
            for (int crack = -3; crack <= 3; crack++)
                line(x + crack * r * 0.24, gy, x + crack * r * 0.3, gy + r * 0.08, color, alpha * 0.5F * (float) (1 - shock));
            line(x - r * 0.12, y - r, x + r * 0.12, gy, color, alpha * 0.25F);
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3 + orbit, size = r * (0.1 + 0.05 * (i % 3));
                double fx = x + Math.cos(angle) * r * 0.62, lift = r * (0.55 + 0.25 * Math.sin(i * 1.9)) * rise;
                double fy = gy - size - lift + Math.sin(angle) * r * 0.22 * rise;
                fy += (gy - size - fy) * slam * slam;
                float lit = alpha * (float) (0.55 + 0.45 * Math.cos(angle));
                line(fx - size, fy, fx + size, fy, WHITE, lit);
                line(fx - size, fy, fx, fy + size * 1.4, color, lit);
                line(fx + size, fy, fx, fy + size * 1.4, color, lit);
            }
            int flip = t < 0.35 || t >= 0.7 ? 0 : (int) ((t - 0.35) / 0.0875);
            double dir = flip % 2 == 0 ? -1 : 1, ax = x + r * 0.95;
            line(ax, y, ax - r * 0.12, y + dir * r * 0.18, WHITE, alpha * (1 - (float) slam));
            line(ax, y, ax + r * 0.12, y + dir * r * 0.18, WHITE, alpha * (1 - (float) slam));
            if (shock > 0) {
                arc(x, gy, r * 1.1 * shock, r * 0.2 * shock, 0, 360, WHITE, alpha * (float) (1 - shock));
                arc(x, gy, r * 0.8 * shock, r * 0.14 * shock, 0, 360, color, alpha * (float) (1 - shock));
            }
        } else if (attack.getFamily() == AttackFamily.CARRIER) {
            // Shared destroyer silhouette; the lower glyph describes the selected payload.
            double sy = y - r * 0.62;
            line(x - r * 0.95, sy, x + r * 0.75, sy - r * 0.08, WHITE, alpha);
            line(x - r * 0.95, sy, x - r * 0.8, sy + r * 0.16, WHITE, alpha);
            line(x - r * 0.8, sy + r * 0.16, x + r * 0.55, sy + r * 0.1, WHITE, alpha);
            line(x + r * 0.55, sy + r * 0.1, x + r * 0.75, sy - r * 0.08, WHITE, alpha);
            if (attack == AttackId.CARRIER_GUNNERY) {
                // Ventral batteries hanging off the keel, each tracking the designator below.
                double tx = x + Math.sin(age * 0.8) * r * 0.3, ty = y + r * 0.6;
                for (int gun = -1; gun <= 1; gun++) {
                    double gx = x + gun * r * 0.5, gy = sy + r * 0.2;
                    double angle = Math.atan2(ty - gy, tx - gx);
                    disc(gx, gy, Math.max(1, r * 0.07), WHITE, alpha);
                    line(gx, gy, gx + Math.cos(angle) * r * 0.22, gy + Math.sin(angle) * r * 0.22, WHITE, alpha);
                    float shot = (float) Math.max(0, Math.sin(age * 3 + gun * 2.1));
                    line(gx + Math.cos(angle) * r * 0.22, gy + Math.sin(angle) * r * 0.22, tx, ty, color, alpha * 0.7F * shot);
                }
                arc(tx, ty, r * 0.26, r * 0.26, 0, 360, color, alpha);
                line(tx - r * 0.38, ty, tx + r * 0.38, ty, WHITE, alpha);
                line(tx, ty - r * 0.38, tx, ty + r * 0.38, WHITE, alpha);
                return;
            }
            if (attack == AttackId.KINETIC_RAIN) {
                // Rods leave the bay on a beat and dive onto their marks; the hammer waits over the centre.
                double gy = y + r * 0.72;
                for (int rod = 0; rod < 4; rod++) {
                    double k = (rod - 1.5) / 1.5;
                    double mx = x + k * r * 0.62, bx = x + k * r * 0.2, by = sy + r * 0.14;
                    double phase = (age * 0.7 + rod * 0.27) % 1, tail = Math.max(0, phase - 0.25);
                    double hx = bx + (mx - bx) * phase, hy = by + (gy - by) * phase * phase;
                    line(bx + (mx - bx) * tail, by + (gy - by) * tail * tail, hx, hy, color, alpha * 0.8F);
                    disc(hx, hy, Math.max(1, r * 0.05), WHITE, alpha);
                    arc(mx, gy, r * 0.18, r * 0.06, 0, 360, phase > 0.85 ? WHITE : AMBER, alpha * (0.4F + 0.6F * (float) phase));
                }
                line(x - r * 0.04, sy + r * 0.16, x - r * 0.04, sy + r * 0.46, WHITE, alpha);
                line(x + r * 0.04, sy + r * 0.16, x + r * 0.04, sy + r * 0.46, WHITE, alpha);
                arc(x, gy, r * 0.36, r * 0.12, age * 60, age * 60 + 240, color, alpha * 0.6F);
                return;
            }
            for (int dash = 0; dash < 3; dash++)
                line(x - r * 0.1, sy + r * (0.28 + dash * 0.22), x - r * 0.1, sy + r * (0.38 + dash * 0.22), color, alpha * 0.7F);
            line(x - r * 0.35, y + r * 0.82, x + r * 0.15, y + r * 0.82, color, alpha);
            line(x - r * 0.1, y + r * 0.82, x + r * 0.35, y + r * 0.45, color, alpha);
            arc(x + r * 0.62, y + r * 0.2, r * 0.22, r * 0.2, 0, 360, AMBER, alpha);
            arc(x + r * 0.62, y + r * 0.2, r * 0.34, r * 0.32, age * 40, age * 40 + 200, color, alpha * 0.55F);
            arc(x + r * 0.1, y - r * 0.15, r * 0.95, r * 0.6, 300, 375, AMBER, alpha * 0.45F);
        } else {
            double pulse = 1 + Math.sin(age * 2.8) * 0.07;
            disc(x, y, r * 0.40 * pulse, AMBER, alpha * 0.12F);
            arc(x, y, r * 0.40 * pulse, r * 0.40 * pulse, 0, 360, AMBER, alpha);
            arc(x, y, r * 0.62, r * 0.62, age * 16, 290 + age * 16, WHITE, alpha * 0.45F);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6 + age * 0.08, c = Math.cos(a), s = Math.sin(a);
                double outer = r * (0.78 + Math.sin(age * 1.5 + i * 2) * 0.15);
                line(x + c * r * 0.49, y + s * r * 0.49, x + c * outer, y + s * outer, AMBER, alpha * 0.8F);
            }
            disc(x, y, r * 0.12, WHITE, alpha);
        }
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (closingAt != 0 || secondsSince(openedAt) < RemoteMenuLayout.OPEN_SECONDS) return;
        RemoteKey key = layout.keyAt(x, y);
        if (key != null) { remoteKey(key, button); return; }
        if (button == 0 && layout.closeAt(x, y)) { THUMB.press(RemoteKey.MENU); close(false); return; }
        int row = layout.rowAt(x, y, secondsSince(openedAt), hover);
        if (row >= 0) {
            focus(row, false);
            if (button == 0) { THUMB.press(RemoteKey.attack(row)); select(row); }
            else if (button == 1) { THUMB.press(RemoteKey.INFO); briefing(); }
        } else if (button == 0 && layout.briefingAt(x, y)) { THUMB.press(RemoteKey.INFO); briefing(); }
    }

    /** Clicking the remote itself works like its keyboard twin. */
    private void remoteKey(RemoteKey key, int button) {
        if (key.isAttack()) {
            focus(key.row(), false);
            THUMB.press(button == 1 ? RemoteKey.INFO : key);
            if (button == 1) briefing(); else select(key.row());
            return;
        }
        if (button != 0) return;
        switch (key) {
            case UP: navigate(Keyboard.KEY_UP); break;
            case DOWN: navigate(Keyboard.KEY_DOWN); break;
            case LEFT: navigate(Keyboard.KEY_LEFT); break;
            case RIGHT: navigate(Keyboard.KEY_RIGHT); break;
            case OK: THUMB.press(key); select(focused); break;
            case INFO: THUMB.press(key); briefing(); break;
            case MENU: THUMB.press(key); close(false); break;
            case SKIP: THUMB.press(key); toggleFilms(); break;
            default: THUMB.press(key); break;
        }
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (closingAt != 0) return;
        if (key == Keyboard.KEY_ESCAPE) {
            THUMB.press(details ? RemoteKey.INFO : RemoteKey.MENU);
            if (details) briefing(); else close(false);
        } else if (key == ClientControls.MENU.getKeyCode()) { THUMB.press(RemoteKey.MENU); close(false); }
        else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT)
            navigate(key);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { THUMB.press(RemoteKey.OK); select(focused); }
        else if (key == ClientControls.SKIP.getKeyCode()) { THUMB.press(RemoteKey.SKIP); toggleFilms(); }
        else {
            KeyBinding[] bindings = attackBindings();
            for (int row = 0; row < bindings.length; row++)
                if (key == bindings[row].getKeyCode()) { THUMB.press(RemoteKey.attack(row)); select(row); break; }
        }
    }

    private void navigate(int key) {
        THUMB.press(key == Keyboard.KEY_UP ? RemoteKey.UP : key == Keyboard.KEY_DOWN ? RemoteKey.DOWN
                : key == Keyboard.KEY_LEFT ? RemoteKey.LEFT : RemoteKey.RIGHT);
        if ((key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) && FAMILIES[focused].attacks().size() > 1)
            cyclePayload(key == Keyboard.KEY_RIGHT ? 1 : -1);
        else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_LEFT) focus((focused + FAMILIES.length - 1) % FAMILIES.length, true);
        else focus((focused + 1) % FAMILIES.length, true);
    }

    private void toggleFilms() {
        LocalClientBridge.filmsEnabled = !LocalClientBridge.filmsEnabled;
        audio.play("details", 0.26F, LocalClientBridge.filmsEnabled ? 1.06F : 0.84F);
    }

    private void focus(int row, boolean sound) {
        focused = row; preview = rowAttack(row);
        THUMB.hover(RemoteKey.attack(row));
        if (sound) { hovered = -1; audio.hover(row, System.nanoTime()); }
    }

    private void cyclePayload(int direction) {
        List<AttackId> payloads = FAMILIES[focused].attacks();
        int index = Math.max(0, payloads.indexOf(preview));
        preview = payloads.get((index + direction + payloads.size()) % payloads.size());
        hovered = -1; audio.hover(focused, System.nanoTime());
    }

    /** The card's strike: the previewed payload, else the linked one, else the family default. */
    private AttackId rowAttack(int row) {
        AttackFamily family = FAMILIES[row];
        if (preview != null && preview.getFamily() == family) return preview;
        return linked.getFamily() == family ? linked : family.first();
    }

    private static int row(AttackId attack) { return attack.getFamily().ordinal(); }

    private static String title(AttackId attack) { return attack.getDisplayName().toUpperCase(Locale.ROOT); }

    private static String subtitle(AttackFamily family, AttackId attack) {
        return family == AttackFamily.CARRIER ? "DESTROYER / PAYLOAD: " + title(attack) : family.getSubtitle();
    }

    private static String payloadLabel(AttackId attack) {
        List<AttackId> payloads = attack.getFamily().attacks();
        return attack.getFamily().getTitle() + " / PAYLOAD " + (payloads.indexOf(attack) + 1) + " OF " + payloads.size();
    }

    /** One chip per carrier payload; the shown one is filled. */
    private void payloads(AttackFamily family, AttackId shown, double x, double y, int accent, float h) {
        List<AttackId> payloads = family.attacks();
        for (int i = 0; i < payloads.size(); i++) {
            boolean active = payloads.get(i) == shown;
            double cx = x + i * 13;
            panel(cx, y, 10, 8, accent, active ? 0.75F : 0.08F + 0.1F * h, 2);
            outline(cx, y, 10, 8, accent, 0.5F + 0.4F * h, 2);
            text(String.valueOf((char) ('A' + i)), cx + 3, y + 1, active ? 0x0B1420 : accent, 0.62F);
        }
    }

    private static String[] stats(AttackId attack) {
        switch (attack) {
            case RAILGUN: return new String[] {"100/s", "200m", "8.4s"};
            case BLACKHOLE: return new String[] {"PULL", "1000m", "37s"};
            case SUPERNOVA: return new String[] {"100/s", "GLOBAL", "4s"};
            case CARRIER_GUNNERY: return new String[] {"20/SHELL", "8m", "30s"};
            case KINETIC_RAIN: return new String[] {"24/ROD", "176m", "23s"};
            case GRAVITY_INVERSION: return new String[] {"15/s+120", "180m", "19s"};
            default: return new String[] {"100/s", "200m", "4s"};
        }
    }

    private void select(int row) {
        if (closingAt != 0) return;
        linked = rowAttack(row);
        StellarNetwork.requestSelection(linked);
        audio.play("select", 0.40F, 1);
        close(true);
    }

    private void briefing() {
        details = !details;
        audio.play("details", 0.30F, details ? 1 : 0.88F);
    }

    private void close(boolean selected) {
        if (closingAt != 0) return;
        closingAt = System.nanoTime();
        if (!selected) audio.play("close", 0.30F, 1);
    }

    @Override public void onGuiClosed() { audio.stop(); THUMB.hover(null); }

    /** Link lines from each strike key to its card; the remote is drawn over their inner ends. */
    private void drawLinks(double age) {
        for (int row = 0; row < FAMILIES.length; row++) {
            RemoteKey key = RemoteKey.attack(row);
            double[] from = layout.projectRemote(key.x, key.y, key.top(0));
            double[] to = layout.project(layout.socketX(layout.cardX(age, row, hover[row])), RemoteMenuLayout.cardY(row) + RemoteMenuLayout.CARD_HEIGHT / 2);
            float saved = opacity;
            opacity *= (float) RemoteMenuLayout.reveal(age, row);
            boolean linkedRow = linked.getFamily() == FAMILIES[row];
            float strength = 0.16F + 0.62F * hover[row] + (linkedRow ? 0.12F : 0);
            line(from[0], from[1], to[0], to[1], ACCENTS[row], strength);
            if (hover[row] > 0.05F) {
                double t = age * 1.6 % 1;
                disc(from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, 1.1, WHITE, hover[row] * (float) Math.sin(t * Math.PI));
            }
            opacity = saved;
        }
    }

    /** The remote standing in its column on the grip side, drawn in depth over the links. */
    private void drawRemote(double age) {
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            transform.clear(); transform.put(layout.remoteMatrix()).flip();
            GL11.glMultMatrix(transform);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.color(1, 1, 1, 1);
            look.selected = linked;
            look.depress = THUMB.depress;
            look.flash = THUMB.flash;
            look.focus = keyUnderMouse != null ? keyUnderMouse : RemoteKey.attack(focused);
            look.age = age;
            look.emitter = opacity;
            look.labels = true;
            THUMB.side(layout.side());
            RemoteModel.draw(look);
        } finally {
            GlStateManager.popMatrix();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }
        drawTouch();
    }

    /** Holographic fingertip riding the thumb choreography: hovers, dives onto a key and rings it on contact. */
    private void drawTouch() {
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        blend(false);
        // Lift above the caps only widens the ring, so the cursor stays centred on the key it points at.
        double[] tip = THUMB.tip();
        double face = RemoteKey.FACE + RemoteKey.CAP;
        double[] at = layout.projectRemote(tip[0], tip[1], face), step = layout.projectRemote(tip[0] + 1, tip[1], face);
        double unit = Math.hypot(step[0] - at[0], step[1] - at[1]);
        float lift = (float) clamp((tip[2] - face - RemoteThumb.PAD) / RemoteThumb.HOVER, 0, 1.5);
        for (RemoteKey key : RemoteKey.KEYS) {
            float flash = THUMB.flash[key.ordinal()];
            if (flash < 0.02F) continue;
            double[] centre = layout.projectRemote(key.x, key.y, key.top(THUMB.depress[key.ordinal()]));
            int color = key.isAttack() ? ACCENTS[key.row()] : 0x9BEFFF;
            double radius = unit * (Math.max(key.halfWidth, key.halfHeight) + 0.4 + (1 - flash) * 2.6);
            glow(centre[0], centre[1], radius, color, flash * 0.45F);
            arc(centre[0], centre[1], radius, radius, 0, 360, color, flash);
        }
        glow(at[0], at[1], unit * (1.3 + 0.5 * lift), 0x9BEFFF, 0.3F);
        double ring = unit * (0.42 + 0.3 * lift);
        arc(at[0], at[1], ring, ring, 0, 360, WHITE, 0.9F);
        disc(at[0], at[1], unit * 0.14, WHITE, 0.95F);
    }

    private static double secondsSince(long time) { return (System.nanoTime() - time) / 1.0e9; }
    private static KeyBinding[] attackBindings() {
        return new KeyBinding[] {ClientControls.RAILGUN, ClientControls.BLACKHOLE, ClientControls.CARRIER, ClientControls.SUPERNOVA,
                ClientControls.GRAVITY};
    }
    private static String clock(double seconds) {
        return String.format(Locale.ROOT, "%02d:%04.1f", (int) (seconds / 60), seconds % 60);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double smoothstep(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    private static String description(AttackId attack) {
        if (attack == AttackId.GRAVITY_INVERSION)
            return "Gravity fails within 180 blocks of the aim. Entities, projectiles and items lift off as illusory slabs of terrain tear free and orbit an invisible axis. Gravity flips four times, throwing bodies around: 15 damage/s while lifted. Then everything slams down: 120 damage and a shockwave. The caster is caught too, spinning with the field. 19s. No terrain damage.";
        if (attack == AttackId.KINETIC_RAIN)
            return "Carrier payload C. The destroyer warps in over the aim and four rails drop out of its bay. 96 tungsten rods walk a spiral barrage from 176 blocks inward; every mark lights 2.5s before its rod lands: 24 raw damage in 8 blocks, with knockback. Then the hammer falls on the centre: 80 damage in 28 blocks. 23s. The caster is exposed in survival. No terrain damage.";
        if (attack == AttackId.CARRIER_GUNNERY)
            return "Carrier payload B. The destroyer warps in over you and a transfer beam lifts you into its fire-control gondola. For 30s your crosshair designates: the five ventral twin batteries traverse onto it and fire coilgun shells. Hold attack to fire, use for the optic. 20 raw damage per shell when it lands, 8-block blast. J / Escape returns you home. No terrain damage.";
        if (attack == AttackId.BLACKHOLE)
            return "A gravity well rises at Y=420. Loaded entities within 1000 blocks follow tilted orbits into the accretion disk. Space begins at Y=300. Entities return after 37 seconds. Terrain stays intact.";
        if (attack == AttackId.ASTEROID_PULLER)
            return "Carrier payload A. Smoke marks the drop zone; the destroyer warps in and drops an AG-07 Graviton Lance. It harpoons a passing asteroid, bends its path and pulls it onto the beacon: 100 damage/s for 4s in a 200-block radius. 43s presentation. No terrain damage.";
        if (attack == AttackId.SUPERNOVA)
            return "GLOBAL WARNING: 100 damage/s for 4s to ALL loaded living entities in this dimension, including the caster. A massive star collapses; light outruns its ejecta. 32s presentation. No terrain damage. Scales are schematic.";
        return "An orbital precision beam deals 100 damage/s for 8.4s to living entities within a 200-block radius. The caster is also exposed in survival. Terrain stays intact. Select here, then aim and right-click in the world to fire.";
    }

    /** Accent colour cross-fades with the preview weights, so every accent-tinted element eases between strikes. */
    private int accent() {
        double r = 0, g = 0, b = 0, total = 0;
        for (int row = 0; row < weight.length; row++) {
            int color = ACCENTS[row];
            r += (color >> 16 & 255) * weight[row]; g += (color >> 8 & 255) * weight[row];
            b += (color & 255) * weight[row]; total += weight[row];
        }
        if (total < 1.0e-4) return ACCENTS[row(preview)];
        return (int) (r / total) << 16 | (int) (g / total) << 8 | (int) (b / total);
    }

    private double previewIndex() {
        double index = 0, total = 0;
        for (int row = 0; row < weight.length; row++) { index += weight[row] * row; total += weight[row]; }
        return total > 1.0e-4 ? index / total : row(preview);
    }

    private static int mix(int from, int to, float t) {
        float k = Math.max(0, Math.min(1, t));
        int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * k);
        int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * k);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * k);
        return r << 16 | g << 8 | b;
    }

    private int argb(int rgb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha * opacity * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    private void blend(boolean additive) {
        if (additive) GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private double width(String value, float size) { return fontRenderer.getStringWidth(value) * size; }

    private void text(String value, double x, double y, int color, float size) { text(value, x, y, color, size, 1); }

    private void text(String value, double x, double y, int color, float size, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha * opacity)) * 255);
        if (a < 8) return; // FontRenderer treats near-zero alpha as an opaque legacy RGB color.
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0); GlStateManager.scale(size, size, 1);
        GlStateManager.enableTexture2D();
        fontRenderer.drawString(value, 0, 0, a << 24 | color & 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    private void textRight(String value, double right, double y, int color, float size) {
        text(value, right - width(value, size), y, color, size);
    }

    private void wrapped(String value, int x, int y, int width, int color, int lineHeight) {
        List<String> lines = fontRenderer.listFormattedStringToWidth(value, width);
        for (int row = 0; row < lines.size(); row++) text(lines.get(row), x, y + row * lineHeight, color, 1);
    }

    private void fill(double x, double y, double width, double height, int color, float alpha) {
        gradient(x, y, width, height, color, alpha, color, alpha, false);
    }

    private void gradient(double x, double y, double width, double height, int from, float fromAlpha,
                          int to, float toAlpha, boolean vertical) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x, y, from, fromAlpha);
        vertex(b, x, y + height, vertical ? to : from, vertical ? toAlpha : fromAlpha);
        vertex(b, x + width, y + height, to, toAlpha);
        vertex(b, x + width, y, vertical ? from : to, vertical ? fromAlpha : toAlpha);
        Tessellator.getInstance().draw();
    }

    private void panel(double x, double y, double width, double height, int color, float alpha) {
        panel(x, y, width, height, color, alpha, 5);
    }

    private void panel(double x, double y, double width, double height, int color, float alpha, double cut) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        octagon(b, x, y, width, height, cut, color, alpha);
        Tessellator.getInstance().draw();
    }

    private void outline(double x, double y, double width, double height, int color, float alpha, double cut) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        octagon(b, x, y, width, height, cut, color, alpha);
        Tessellator.getInstance().draw();
    }

    private void octagon(BufferBuilder b, double x, double y, double width, double height, double cut, int color, float alpha) {
        vertex(b, x + cut, y, color, alpha); vertex(b, x + width - cut, y, color, alpha);
        vertex(b, x + width, y + cut, color, alpha); vertex(b, x + width, y + height - cut, color, alpha);
        vertex(b, x + width - cut, y + height, color, alpha); vertex(b, x + cut, y + height, color, alpha);
        vertex(b, x, y + height - cut, color, alpha); vertex(b, x, y + cut, color, alpha);
    }

    private void slant(double x, double y, double width, double height, double skew, int color, float alpha) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x + skew, y, color, alpha); vertex(b, x, y + height, color, alpha);
        vertex(b, x + width - skew, y + height, color, alpha); vertex(b, x + width, y, color, alpha);
        Tessellator.getInstance().draw();
    }

    private void hazard(double x, double y, double width, double height, double age) {
        double shift = age * 8 % 10;
        for (double stripe = x - 10 + shift; stripe < x + width; stripe += 10) {
            GlStateManager.disableTexture2D();
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            vertex(b, clamp(stripe + height, x, x + width), y, DANGER, 0.6F);
            vertex(b, clamp(stripe, x, x + width), y + height, DANGER, 0.6F);
            vertex(b, clamp(stripe + 5, x, x + width), y + height, DANGER, 0.6F);
            vertex(b, clamp(stripe + 5 + height, x, x + width), y, DANGER, 0.6F);
            Tessellator.getInstance().draw();
        }
    }

    private void line(double x1, double y1, double x2, double y2, int color, float alpha) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x1, y1, color, alpha); vertex(b, x2, y2, color, alpha);
        Tessellator.getInstance().draw();
    }

    private void arc(double x, double y, double rx, double ry, double start, double end, int color, float alpha) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int segments = Math.max(12, (int) ((end - start) / 6));
        for (int i = 0; i <= segments; i++) {
            double a = Math.toRadians(start + (end - start) * i / segments);
            vertex(b, x + Math.cos(a) * rx, y + Math.sin(a) * ry, color, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private void disc(double x, double y, double radius, int color, float alpha) {
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x, y, color, alpha);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI / 16;
            vertex(b, x + Math.cos(a) * radius, y + Math.sin(a) * radius, color, alpha);
        }
        Tessellator.getInstance().draw();
    }

    /** Soft additive halo; fades from the centre to transparent at the rim. */
    private void glow(double x, double y, double radius, int color, float alpha) {
        blend(true);
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x, y, color, alpha);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI / 16;
            vertex(b, x + Math.cos(a) * radius, y + Math.sin(a) * radius, color, 0);
        }
        Tessellator.getInstance().draw();
        blend(false);
    }

    private void radar(double x, double y, double radius, double angle, int color, float alpha) {
        blend(true);
        GlStateManager.disableTexture2D();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x, y, color, alpha * 0.6F);
        for (int i = 0; i <= 16; i++) {
            double a = Math.toRadians(angle - 70 + 70.0 * i / 16);
            vertex(b, x + Math.cos(a) * radius, y + Math.sin(a) * radius, color, alpha * i / 16);
        }
        Tessellator.getInstance().draw();
        double lead = Math.toRadians(angle);
        line(x, y, x + Math.cos(lead) * radius, y + Math.sin(lead) * radius, color, alpha * 2.5F);
        blend(false);
    }

    private void brackets(double x, double y, double width, double height, int color, double size) {
        for (int side = 0; side < 2; side++) for (int edge = 0; edge < 2; edge++) {
            double px = x + side * width, py = y + edge * height;
            line(px, py, px + (side == 0 ? size : -size), py, color, 0.85F);
            line(px, py, px, py + (edge == 0 ? size : -size), color, 0.85F);
        }
    }

    private void vertex(BufferBuilder b, double x, double y, int color, float alpha) {
        b.pos(x, y, 0).color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, Math.max(0, Math.min(1, alpha * opacity))).endVertex();
    }
}
