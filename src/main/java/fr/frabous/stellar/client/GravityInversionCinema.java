package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;

import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** Caster's film of SS-05; the world keeps running and every witness sees the same field. */
final class GravityInversionCinema extends GuiScreen {
    private final LaunchState launch;
    private final GravityInversionSoundtrack soundtrack = new GravityInversionSoundtrack();
    private CinematicCamera camera;

    GravityInversionCinema(LaunchState launch) { this.launch = launch; }

    boolean matches(UUID id) { return launch.getLaunchId().equals(id); }
    double age(float partial) { return Math.max(0, mc.world.getTotalWorldTime() - launch.getStartTick() + partial); }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void initGui() {
        if (camera == null && mc.player != null) {
            camera = new CinematicCamera(this, launch);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @Override public void onGuiClosed() {
        soundtrack.stop();
        MinecraftForge.EVENT_BUS.unregister(this);
        if (camera != null) { camera.close(); camera = null; }
    }

    @Override public void updateScreen() {
        if (mc.world == null || mc.player == null || !mc.player.isEntityAlive()
                || mc.world.provider.getDimension() != launch.getDimension() || age(0) >= END)
            mc.displayGuiScreen(null);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && mc.currentScreen == this && mc.world != null) soundtrack.update(age(0));
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == ClientControls.SKIP.getKeyCode() || key == 1) StellarServices.client().stopPresentation();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        if (mc.world == null) return;
        double age = age(partial);
        long seed = launch.getSeed();
        // Faint magenta cast while gravity is wrong, gone once the world is back on the ground.
        double tint = smooth(age / 40) * (1 - smooth((age - IMPACT) / 30));
        if (tint > 0) drawRect(0, 0, width, height, (int) (tint * 34) << 24 | 0x40104A);
        GravityInversionImpactFrames.vignette(age, width, height);
        double hush = GravityInversionVisuals.hush(age);
        if (hush > 0) drawRect(0, 0, width, height, (int) (hush * 255) << 24 | GravityInversionVisuals.VOID);
        GravityInversionImpactFrames.speedLines(seed, age, width, height);
        double flash = GravityInversionVisuals.flash(age);
        if (flash > 0) drawRect(0, 0, width, height, (int) (flash * 220) << 24 | 0xFFF2FB);
        double[] point = camera == null ? null : camera.project(0, -1, 0, width, height);
        double cx = point == null ? width * 0.5 : Math.max(width * 0.2, Math.min(width * 0.8, point[0]));
        double cy = point == null ? height * 0.62 : Math.max(height * 0.35, Math.min(height * 0.85, point[1]));
        GravityInversionImpactFrames.draw(seed, age, width, height, cx, cy);

        int base = Math.max(24, (int) (height * 0.075));
        int bars = (int) (base * (1 + GravityInversionVisuals.letterbox(age) * 1.2));
        drawRect(0, 0, width, bars, 0xFF06020A);
        drawRect(0, height - bars, width, height, 0xFF06020A);
        int lineY = height - bars - 5;
        drawRect(12, lineY, width - 12, lineY + 1, 0xFF3A2C44);
        drawRect(12, lineY, 12 + (int) ((width - 24) * Math.min(1, age / END)), lineY + 1, 0xFF000000 | GravityInversionVisuals.MAGENTA);
        fontRenderer.drawString("SS-05 / GRAVITY INVERSION", 12, height - bars + 4, 0xFFC8F2);
        fontRenderer.drawString(phaseLabel(age), 12, height - bars + 14, 0xB7A6C9);
        fontRenderer.drawString("J / ESC: SKIP", width - fontRenderer.getStringWidth("J / ESC: SKIP") - 12, 8, 0xB7A6C9);
        gauge(age, bars);
        card(age, 4, "GRAVITY FAILURE", "LOCAL FIELD / 180 M");
        card(age, ASCENSION + 4, "WORLD ASCENSION", "BODIES AND TERRAIN LIFT OFF");
        card(age, ROTATION + 8, "AXIS ROTATION", "GRAVITY FLIPS / " + FLIPS + " TIMES");
        card(age, IMPACT + 8, "TOTAL COLLAPSE", "120 DAMAGE / SHOCKWAVE");
    }

    /** Gravity vector readout: g swings, flips sign, then reads x0 during the hang. */
    private void gauge(double age, int bars) {
        if (age >= IMPACT + 20) return;
        double g;
        if (age < ASCENSION) g = 1 - 1.0 * smooth(age / ASCENSION);
        else if (age < ROTATION) g = -0.6 * lift(age);
        else if (age < COLLAPSE) g = flip(age) % 2 == 0 ? -1.4 : 1.2;
        else if (age < SLAM) g = 0;
        else g = 9.5;
        String text = String.format(Locale.ROOT, "g x%+.1f", g);
        boolean alarm = age >= COLLAPSE && ((int) (age / 2) & 1) == 0;
        int color = alarm ? 0xFFFFFF : age >= ROTATION ? 0xFF7FE3 : 0xFFC8F2;
        GL11.glPushMatrix();
        GL11.glTranslated(width - 12 - fontRenderer.getStringWidth(text) * 2, bars + 8, 0);
        GL11.glScaled(2, 2, 1);
        fontRenderer.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
        if (age >= ROTATION && age < COLLAPSE) {
            String flip = String.format(Locale.ROOT, "FLIP %d / %d", flip(age) + 1, FLIPS);
            fontRenderer.drawString(flip, width - 12 - fontRenderer.getStringWidth(flip), bars + 28, 0xB7A6C9);
        }
    }

    private void card(double age, double start, String title, String subtitle) {
        double alpha = GravityInversionVisuals.card(age, start);
        if (alpha < 0.02) return;
        int a = Math.max(4, (int) (alpha * 255)) << 24;
        double spread = 1 + (1 - alpha) * 0.25;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glTranslated(width / 2.0, height * 0.40, 0);
        GL11.glScaled(3 * spread, 3, 1);
        fontRenderer.drawStringWithShadow(title, -fontRenderer.getStringWidth(title) / 2F, 0, a | 0xFFF0FA);
        GL11.glPopMatrix();
        int lineWidth = (int) (fontRenderer.getStringWidth(title) * 1.5 * alpha);
        int y = (int) (height * 0.40) + 31;
        drawRect(width / 2 - lineWidth, y, width / 2 + lineWidth, y + 1, a | GravityInversionVisuals.MAGENTA);
        fontRenderer.drawString(subtitle, (width - fontRenderer.getStringWidth(subtitle)) / 2, y + 5, a | 0xB7A6C9);
    }

    private static String phaseLabel(double age) {
        if (age < ASCENSION) return "GRAVITY FAILURE";
        if (age < ROTATION) return "WORLD ASCENSION";
        if (age < COLLAPSE) return "AXIS ROTATION / FLIP " + (flip(age) + 1);
        if (age < SLAM) return "HANG";
        if (age < IMPACT) return "TOTAL COLLAPSE";
        return "SHOCKWAVE";
    }
}
