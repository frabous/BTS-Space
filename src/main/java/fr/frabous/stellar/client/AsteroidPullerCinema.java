package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AsteroidTrajectory;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;

import static fr.frabous.stellar.api.AsteroidPullerTiming.*;

/** SS-03A film: orbital lens for the carrier, world camera for the rest, and a tactical HUD. */
final class AsteroidPullerCinema extends GuiScreen {
    static final double EARTH_RADIUS = 1200;
    private static final double[] SUN = {0.38, 0.82, 0.43};
    private final LaunchState launch;
    private final CarrierFlight.Plan plan;
    private final AsteroidPullerSoundtrack soundtrack = new AsteroidPullerSoundtrack();
    private final double[][] stars = new double[1400][4];
    private CinematicCamera camera;
    private RailgunCameraPath.Pose spacePose;
    private double renderedAge;

    AsteroidPullerCinema(LaunchState launch) {
        this.launch = launch;
        this.plan = AsteroidPullerLance.plan(launch);
        Random random = new Random(launch.getSeed());
        for (double[] star : stars) {
            double a = random.nextDouble() * Math.PI * 2, y = random.nextDouble() * 2 - 1, r = Math.sqrt(1 - y * y);
            star[0] = Math.cos(a) * r * 4600; star[1] = y * 4600; star[2] = Math.sin(a) * r * 4600;
            star[3] = random.nextDouble();
        }
    }

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
        renderedAge = age(partial);
        double age = renderedAge;
        boolean space = AsteroidPullerCamera.isSpaceShot(age);
        spacePose = space ? AsteroidPullerCamera.orbital(plan, age) : null;
        if (space) orbitalScene(age, spacePose);
        GlStateManager.disableDepth();
        overlays(age);
        tactical(age);
        letterbox(age);
        GlStateManager.enableDepth();
    }

    // --- Orbital lens ---------------------------------------------------------------------

    private void orbitalScene(double age, RailgunCameraPath.Pose pose) {
        drawRect(0, 0, width, height, 0xFF020309);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GLU.gluPerspective((float) pose.fov, (float) width / Math.max(1, height), 0.15F, 7000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glRotated(pose.roll, 0, 0, 1);
            GLU.gluLookAt((float) pose.x, (float) pose.y, (float) pose.z,
                    (float) pose.targetX, (float) pose.targetY, (float) pose.targetZ, 0, 1, 0);
            GL11.glPointSize(1.4F);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
            for (double[] star : stars)
                EffectMesh.vertex(b, pose.x + star[0], pose.y + star[1], pose.z + star[2], 0xDBE9FF, (float) (0.15 + star[3] * 0.55));
            Tessellator.getInstance().draw();
            // A far sun matching the scene light, with a soft corona.
            double sx = pose.x + SUN[0] * 5200, sy = pose.y + SUN[1] * 5200, sz = pose.z + SUN[2] * 5200;
            MoonAttractVfx.glow(sx, sy, sz, 900, 0xFFE9C8, 0.35F);
            MoonAttractVfx.glow(sx, sy, sz, 180, 0xFFFFFF, 0.95F);
            GL11.glPushMatrix(); GL11.glTranslated(plan.anchorX, plan.anchorY - EARTH_RADIUS, plan.anchorZ);
            CelestialModels.earth(EARTH_RADIUS, age); GL11.glPopMatrix();
            AsteroidPullerScene.drawLocal(launch, age, false);
            EmissionBloom.render(() -> AsteroidPullerScene.drawLocal(launch, age, true));
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(active);
        }
    }

    // --- Full-frame grading and transitions -------------------------------------------------

    private void overlays(double age) {
        double flash = AsteroidPullerCamera.spaceFlash(age);
        if (flash > 0) {
            fill(0xFFF3DE, flash * 0.95);
            streaks(age, flash);
        }
        double clouds = AsteroidPullerCamera.cloudWipe(age);
        if (clouds > 0) clouds(age, clouds);
        // Weapon and lock flashes.
        double fire = age < FIRE ? 0 : Math.exp(-(age - FIRE) / 2.2) * 0.45;
        double lock = age < LOCK ? 0 : Math.exp(-(age - LOCK) / 3) * 0.3;
        if (fire + lock > 0.003) fill(0xE9DDFF, fire + lock);
        // The sky warms and darkens under the incoming mass.
        double dread = AsteroidPullerScene.smooth((age - 500) / 70) * (age < IMPACT ? 1 : 1 - AsteroidPullerScene.smooth((age - IMPACT) / 40));
        if (dread > 0) fill(0x1A0C06, dread * 0.32);
        double heat = AsteroidPullerScene.heat(Math.min(age, IMPACT - 0.01)) * (age < IMPACT ? 1 : 0);
        if (heat > 0.02) vignette(0xFF6A20, heat * 0.55);
        if (age >= IMPACT) {
            double t = age - IMPACT;
            double white = t < 2 ? 1 : Math.exp(-(t - 2) / 5);
            fill(0xFFF7EC, white * 0.97);
            fill(0xFF8A3C, Math.exp(-t / 30) * 0.22 * (1 - white));
            if (t > 8) fill(0x2A2018, AsteroidPullerScene.smooth((t - 8) / 60) * 0.28 * (1 - AsteroidPullerScene.smooth((age - FADE) / 40)));
        }
        if (age > END - 16) fill(0x000000, AsteroidPullerScene.smooth((age - END + 16) / 14));
    }

    /** Radial speed lines converging on the asteroid as it dives. */
    private void speedLines(double[] centre, double age, double strength) {
        if (centre == null || strength <= 0) return;
        begin(GL11.GL_LINES);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int i = 0; i < 64; i++) {
            double angle = i * 2.39996 + age * 0.03, phase = (age * 0.2 + CarrierRenderer.seed(i)) % 1;
            double inner = Math.max(width, height) * (0.25 + 0.6 * phase), outer = inner + Math.max(width, height) * 0.25;
            double c = Math.cos(angle), s = Math.sin(angle);
            vertex(b, centre[0] + c * inner, centre[1] + s * inner, 0xFFE2C0, (float) (strength * 0.35 * phase));
            vertex(b, centre[0] + c * outer, centre[1] + s * outer, 0xFFE2C0, 0);
        }
        end();
    }

    private void streaks(double age, double strength) {
        begin(GL11.GL_LINES);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int i = 0; i < 48; i++) {
            double x = CarrierRenderer.seed(i * 3 + 1) * width, length = height * (0.3 + 0.6 * CarrierRenderer.seed(i * 5 + 2));
            double y = height - ((age * 40 + CarrierRenderer.seed(i * 7 + 3) * height * 2) % (height * 2));
            vertex(b, x, y, 0xFFFFFF, (float) (strength * 0.6));
            vertex(b, x, y + length, 0xFFE2B0, 0);
        }
        end();
    }

    /** Screen-space cloud deck sweeping upward while the camera falls through it. */
    private void clouds(double age, double alpha) {
        fill(0xE6ECF2, alpha * 0.9);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(MoonAttractVfx.SMOKE);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double travel = (age - WORLD_CUT + 14) * height * 0.09;
        for (int i = 0; i < 22; i++) {
            double size = height * (0.5 + 0.7 * CarrierRenderer.seed(i * 11 + 1));
            double x = CarrierRenderer.seed(i * 13 + 2) * width, y = height * 1.4 - ((travel + CarrierRenderer.seed(i * 17 + 3) * height * 2.2) % (height * 2.2));
            int tile = i & 3;
            double u = (tile % 2) * 0.5, v = (tile / 2) * 0.5;
            float a = (float) (alpha * (0.55 + 0.45 * CarrierRenderer.seed(i * 19 + 4)));
            int shade = i % 3 == 0 ? 0xCFD8E3 : 0xF4F7FA;
            float r = (shade >> 16 & 255) / 255F, g = (shade >> 8 & 255) / 255F, bl = (shade & 255) / 255F;
            b.pos(x - size, y - size, 0).tex(u, v).color(r, g, bl, a).endVertex();
            b.pos(x - size, y + size, 0).tex(u, v + 0.5).color(r, g, bl, a).endVertex();
            b.pos(x + size, y + size, 0).tex(u + 0.5, v + 0.5).color(r, g, bl, a).endVertex();
            b.pos(x + size, y - size, 0).tex(u + 0.5, v).color(r, g, bl, a).endVertex();
        }
        Tessellator.getInstance().draw();
        GlStateManager.disableTexture2D();
    }

    // --- Tactical HUD -----------------------------------------------------------------------

    private void tactical(double age) {
        if (age < APPEAR - 10 || age >= IMPACT) return;
        double[] rock = AsteroidTrajectory.position(age);
        double[] screen = project(rock[0], rock[1], rock[2]);
        double[] eye = eyeLocal();
        double distance = eye == null ? 0 : Math.sqrt(sq(rock[0] - eye[0]) + sq(rock[1] - eye[1]) + sq(rock[2] - eye[2]));
        int accent = age < LOCK ? 0xFFC46A : 0xC8A8FF;
        float hud = (float) (AsteroidPullerScene.smooth((age - APPEAR) / 10) * (1 - AsteroidPullerScene.smooth((age - IMPACT + 12) / 10)));
        if (screen != null && hud > 0.01) {
            double angular = distance > 1 ? Math.atan(AsteroidTrajectory.RADIUS * 1.25 / distance) : 0.5;
            double half = Math.max(10, Math.min(height * 0.45, angular / Math.toRadians(fov() / 2) * height / 2));
            brackets(screen[0], screen[1], half, accent, hud);
            String id = "TGT 2026-TX  /  D 160 m";
            text(id, screen[0] + half + 6, screen[1] - half, accent, hud);
            text(String.format(Locale.ROOT, "RNG %,d m  VEL %,d m/s", (int) distance,
                    (int) Math.round(AsteroidTrajectory.speed(age) * 20)), screen[0] + half + 6, screen[1] - half + 10, 0xE8EEF5, hud);
        }
        if (age >= CHARGE && age < LOCK) {
            double[] lead = AsteroidTrajectory.interceptPoint();
            double[] marker = project(lead[0], lead[1], lead[2]);
            float a = (float) AsteroidPullerScene.smooth((age - CHARGE) / 10);
            if (marker != null) {
                diamond(marker[0], marker[1], 7 + 2 * Math.sin(age * 0.8), 0xC8A8FF, a);
                if (screen != null) dashed(screen[0], screen[1], marker[0], marker[1], 0xC8A8FF, a * 0.8F);
                text(String.format(Locale.ROOT, "LEAD SOLUTION  T-%.2fs", Math.max(0, (LOCK - age) / 20)), marker[0] + 10, marker[1] + 6, 0xE3D6FF, a);
            }
        }
        if (age >= CHARGE - 10 && age < FIRE + 10) {
            double level = AsteroidPullerScene.smooth((age - CHARGE) / (FIRE - CHARGE));
            float a = (float) (AsteroidPullerScene.smooth((age - CHARGE + 10) / 10) * (1 - AsteroidPullerScene.smooth((age - FIRE) / 10)));
            double w = width * 0.32, x = (width - w) / 2, y = height * 0.80;
            rect(x, y, w, 3, 0x2A2F3A, a * 0.8F);
            rect(x, y, w * level, 3, level > 0.96 ? 0xFFFFFF : 0xC8A8FF, a);
            textCentered(String.format(Locale.ROOT, "GRAVITON CHARGE  %d%%", (int) Math.round(level * 100)), width / 2.0, y - 11, 0xE3D6FF, a);
        }
        if (age >= LOCK && age < BEND_END + 20) {
            float banner = (float) (AsteroidPullerScene.smooth((age - LOCK) / 3) * (1 - AsteroidPullerScene.smooth((age - BEND_END) / 20)));
            boolean blink = age < LOCK + 30 && ((int) (age / 3)) % 2 == 0;
            textCentered("GRAVITON LOCK", width / 2.0, height * 0.14, blink ? 0xFFFFFF : 0xC8A8FF, banner);
            textCentered(String.format(Locale.ROOT, "TRAJECTORY DEFLECTION  %.1f\u00b0", AsteroidTrajectory.deflection(age)),
                    width / 2.0, height * 0.14 + 12, 0xE3D6FF, banner);
        }
        if (age >= BEND_END - 10) {
            float a = (float) AsteroidPullerScene.smooth((age - BEND_END + 10) / 10);
            int red = ((int) (age / 2)) % 2 == 0 ? 0xFF5A4A : 0xFFB0A0;
            String countdown = String.format(Locale.ROOT, "IMPACT  T-%.2fs", Math.max(0, (IMPACT - age) / 20));
            text(countdown, width - 14 - fontRenderer.getStringWidth(countdown), height * 0.12, red, a);
            String heat = String.format(Locale.ROOT, "ENTRY HEAT %d%%  VEL %,d m/s", (int) Math.round(AsteroidPullerScene.heat(age) * 100),
                    (int) Math.round(AsteroidTrajectory.speed(age) * 20));
            text(heat, width - 14 - fontRenderer.getStringWidth(heat), height * 0.12 + 11, 0xFFD2B8, a);
            speedLines(screen, age, AsteroidPullerScene.smooth((age - 520) / 30));
        }
    }

    private double[] project(double x, double y, double z) {
        if (spacePose != null) return AsteroidPullerCamera.project(spacePose, x, y, z, width, height);
        return camera == null ? null : camera.project(x, y, z, width, height);
    }

    private double[] eyeLocal() {
        if (spacePose != null) return new double[] {spacePose.x, spacePose.y, spacePose.z};
        return camera == null ? null : camera.eyeLocal();
    }

    private double fov() { return spacePose != null ? spacePose.fov : camera == null ? 70 : camera.fov(); }

    // --- Frame and captions -----------------------------------------------------------------

    private void letterbox(double age) {
        int bars = Math.max(1, (int) (height * 0.075));
        drawRect(0, 0, width, bars, 0xFF05060C);
        drawRect(0, height - bars, width, height, 0xFF05060C);
        int lineY = height - bars - 6;
        drawRect(12, lineY, width - 12, lineY + 1, 0xFF343846);
        drawRect(12, lineY, 12 + (int) ((width - 24) * Math.min(1, age / END)), lineY + 1, 0xFFB58AFF);
        fontRenderer.drawString("SS-03A / CARRIER OPS \u00b7 ASTEROID PULLER", 12, height - bars + 3, 0xC6B9E6);
        fontRenderer.drawString(phaseLabel(age), 12, height - bars + 15, 0xFF8D9DAF);
    }

    static String phaseLabel(double age) {
        if (age < UPLINK) return "SMOKE BEACON / TARGET DESIGNATED";
        if (age < WARP_EXIT) return "UPLINK / CARRIER WARP SOLUTION";
        if (age < STATION) return "SES LEGISLATOR / WARP EXIT & BRAKING BURN";
        if (age < RELEASE) return "VENTRAL BAY / AG-07 GRAVITON LANCE";
        if (age < LAND) return "PAYLOAD DROP / RE-ENTRY & RETRO BURN";
        if (age < SHIP_JUMP) return "ANCHORING / CARRIER DEPARTURE";
        if (age < CHARGE) return "INBOUND 2026-TX / TRACKING";
        if (age < FIRE) return "GRAVITON CHARGE / LEAD SOLUTION";
        if (age < LOCK) return "HARPOON FIRED / INTERCEPT";
        if (age < BEND_END) return "GRAVITON LOCK / TRAJECTORY BEND";
        if (age < IMPACT) return "ATMOSPHERIC ENTRY / TERMINAL DIVE";
        if (age < AFTERMATH) return "IMPACT / SHOCK FRONT";
        return "AFTERMATH / EJECTA SETTLING";
    }

    // --- 2D helpers ----------------------------------------------------------------------------

    private void fill(int rgb, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a > 0) drawRect(0, 0, width, height, a << 24 | rgb);
    }

    private void vignette(int rgb, double alpha) {
        begin(GL11.GL_TRIANGLES);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        double cx = width / 2.0, cy = height / 2.0, rx = width * 0.75, ry = height * 0.75;
        for (int i = 0; i < 40; i++) {
            double a0 = i * Math.PI * 2 / 40, a1 = (i + 1) * Math.PI * 2 / 40;
            vertex(b, cx + Math.cos(a0) * rx * 0.55, cy + Math.sin(a0) * ry * 0.55, rgb, 0);
            vertex(b, cx + Math.cos(a0) * rx, cy + Math.sin(a0) * ry, rgb, (float) alpha);
            vertex(b, cx + Math.cos(a1) * rx, cy + Math.sin(a1) * ry, rgb, (float) alpha);
            vertex(b, cx + Math.cos(a0) * rx * 0.55, cy + Math.sin(a0) * ry * 0.55, rgb, 0);
            vertex(b, cx + Math.cos(a1) * rx, cy + Math.sin(a1) * ry, rgb, (float) alpha);
            vertex(b, cx + Math.cos(a1) * rx * 0.55, cy + Math.sin(a1) * ry * 0.55, rgb, 0);
        }
        end();
    }

    private void brackets(double x, double y, double half, int rgb, float alpha) {
        double arm = Math.max(4, half * 0.35);
        begin(GL11.GL_LINES);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            double cx = x + sx * half, cy = y + sy * half;
            vertex(b, cx, cy, rgb, alpha); vertex(b, cx - sx * arm, cy, rgb, alpha);
            vertex(b, cx, cy, rgb, alpha); vertex(b, cx, cy - sy * arm, rgb, alpha);
        }
        vertex(b, x - 3, y, rgb, alpha * 0.7F); vertex(b, x + 3, y, rgb, alpha * 0.7F);
        vertex(b, x, y - 3, rgb, alpha * 0.7F); vertex(b, x, y + 3, rgb, alpha * 0.7F);
        end();
    }

    private void diamond(double x, double y, double r, int rgb, float alpha) {
        begin(GL11.GL_LINE_LOOP);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        vertex(b, x, y - r, rgb, alpha); vertex(b, x + r, y, rgb, alpha);
        vertex(b, x, y + r, rgb, alpha); vertex(b, x - r, y, rgb, alpha);
        end();
    }

    private void dashed(double x0, double y0, double x1, double y1, int rgb, float alpha) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        int dashes = (int) Math.max(1, length / 8);
        begin(GL11.GL_LINES);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int i = 0; i < dashes; i += 2) {
            double t0 = i / (double) dashes, t1 = Math.min(1, (i + 1) / (double) dashes);
            vertex(b, x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, rgb, alpha);
            vertex(b, x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1, rgb, alpha);
        }
        end();
    }

    private void rect(double x, double y, double w, double h, int rgb, float alpha) {
        begin(GL11.GL_QUADS);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        vertex(b, x, y, rgb, alpha); vertex(b, x, y + h, rgb, alpha);
        vertex(b, x + w, y + h, rgb, alpha); vertex(b, x + w, y, rgb, alpha);
        end();
    }

    private void text(String value, double x, double y, int rgb, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a < 8) return;
        GlStateManager.enableBlend();
        fontRenderer.drawStringWithShadow(value, (float) x, (float) y, a << 24 | rgb);
    }

    private void textCentered(String value, double x, double y, int rgb, float alpha) {
        text(value, x - fontRenderer.getStringWidth(value) / 2.0, y, rgb, alpha);
    }

    private void begin(int mode) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator.getInstance().getBuffer().begin(mode, DefaultVertexFormats.POSITION_COLOR);
    }

    private void end() {
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
    }

    private static void vertex(BufferBuilder b, double x, double y, int rgb, float alpha) {
        b.pos(x, y, 0).color((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, Math.max(0, Math.min(1, alpha))).endVertex();
    }

    private static double sq(double value) { return value * value; }
}
