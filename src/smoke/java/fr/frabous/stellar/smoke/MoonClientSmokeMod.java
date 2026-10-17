package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchRequest;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.AsteroidPullerTiming;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.client.LocalClientBridge;
import fr.frabous.stellar.client.RemoteMenuLayout;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Development-only connected Moon acceptance driver; framebuffer images are review artifacts. */
@Mod(modid = "stellarmoonclientsmoke", name = "Stellar Moon Client Smoke", version = "1",
        acceptableRemoteVersions = "*", dependencies = "after:stellar")
public final class MoonClientSmokeMod {
    private final File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke-moon"));
    private static final int SKIP_FRAMES = 22;
    private final double[] frames = {10, 20, 30, 40, 48, 56, 66, 78, 92, 104, 116, 126, 136, 150, 164, 174, 186, 198, 212, 228, 246, 280,
            320, 346, 364, 372, 380, 390, 420, 460, 500, 524, 544, 558, 566, 572, 580, 596, 620, 660, 740, 830};
    private final List<ISound> sounds = new ArrayList<>();
    private Object bridge, cinema, soundtrack;
    private Method cinemaAge;
    private LaunchState launch;
    private GuiScreen menu;
    private Entity previousView;
    private boolean previousHideGUI;
    private int previousThirdPerson;
    private long readyAt, castAt, skippedAt = -1, endedAt = -1;
    private long lastBloomPass;
    private int sequence, selectionStage, frameIndex, playingSources, glChecks, cleanCameraFrames;
    private boolean menuCaptured, detailsOpened, detailsCaptured, baselineCaptured, cast, skipped, skipCaptured, cancelRequested;
    private float incomingFov;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.moon")) return;
        // Keep the test framebuffer at the runner's 1280x720; resizing changes capture timing and framing.
        org.lwjgl.opengl.Display.setResizable(false);
        bridge = StellarServices.client();
        while (!(bridge instanceof LocalClientBridge)) bridge = field(bridge, "bridge");
        require(output.isDirectory(), "Runner must create the Moon artifact directory before launch");
        MinecraftForge.EVENT_BUS.register(this);
        log("READY driver=moon modes=natural,skip-and-cancel");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void inputIsolation(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || sequence >= 2) return;
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        KeyBinding.unPressAllKeys();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void mouseIsolation(MouseEvent event) {
        if (sequence < 2) event.setCanceled(true);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sequence >= 2) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        long now = mc.world.getTotalWorldTime();
        mc.player.inventory.currentItem = 0;
        mc.playerController.updateController();
        if (selectionStage > 0) mc.setIngameNotInFocus();
        if (selectionStage == 0) {
            if (!new File(output, "ready-" + sequence).isFile() || LaunchController.remote(mc.player).isEmpty()) return;
            require(mc.player.isCreative() && !mc.player.isSpectator(), "Expected immune creative test player");
            require(LocalClientBridge.filmsEnabled && launches().isEmpty(), "Presentation or launch state leaked into next pass");
            mc.displayGuiScreen(null);
            mc.gameSettings.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.setIngameNotInFocus();
            previousHideGUI = mc.gameSettings.hideGUI;
            previousThirdPerson = mc.gameSettings.thirdPersonView;
            previousView = mc.getRenderViewEntity();
            readyAt = now + 80;
            if (sequence == 0) {
                press(mc, "menu");
                require(screen(mc, "SkillMenu"), "H did not open the skill menu");
                menu = mc.currentScreen;
                selectionStage = -1;
                log("MENU_OPEN sequence=0 rows=" + AttackId.values().length);
            } else {
                StellarNetwork.requestSelection(AttackId.ASTEROID_PULLER);
                selectionStage = 3;
            }
            return;
        }
        if (selectionStage == -1) {
            require(now < readyAt, "Menu framebuffer capture timed out");
            if (!menuCaptured || System.nanoTime() - ((Number) field(menu, "openedAt")).longValue() < 600_000_000L) return;
            require(mc.currentScreen == menu, "Skill menu unexpectedly closed");
            require(AttackId.values().length >= 3 && AttackId.values()[2] == AttackId.ASTEROID_PULLER,
                    "Moon is not the third menu row");
            RemoteMenuLayout layout = (RemoteMenuLayout) field(menu, "layout");
            double[] point = layout.project(layout.cardsLeft() + 110, RemoteMenuLayout.cardY(2) + 23);
            int x = (int) Math.round(point[0]), y = (int) Math.round(point[1]);
            if (!detailsOpened) {
                click(menu, x, y, 1);
                require(Boolean.TRUE.equals(field(menu, "details")) && field(menu, "preview") == AttackId.ASTEROID_PULLER,
                        "Third-row details did not select Moon");
                detailsOpened = true;
                return;
            }
            if (!detailsCaptured) return;
            click(menu, x, y, 1);
            require(Boolean.FALSE.equals(field(menu, "details")), "Moon details did not close");
            click(menu, x, y, 0);
            selectionStage = -2; readyAt = now + 80;
            return;
        }
        if (selectionStage == -2) {
            require(now < readyAt, "Third-row selection did not finish its close animation");
            if (mc.currentScreen != null) return;
            selectionStage = 1;
        }
        if (selectionStage < 4) {
            require(now < readyAt, "Server selection acknowledgement timed out");
            AttackId selected = ItemStellarRemote.selected(LaunchController.remote(mc.player));
            if (selectionStage == 1 && selected == AttackId.ASTEROID_PULLER) {
                log("MENU_SELECTED sequence=0 attack=ASTEROID_PULLER wireId=" + selected.getWireId() + " row=2");
                StellarNetwork.requestSelection(AttackId.RAILGUN);
                selectionStage = 2; readyAt = now + 80;
            } else if (selectionStage == 2 && selected == AttackId.RAILGUN) {
                KeyBinding moon = binding(mc, "moon_attract");
                require(moon.getKeyCode() == Keyboard.KEY_M, "Default Moon binding is not M");
                press(mc, "moon_attract"); selectionStage = 3; readyAt = now + 80;
            } else if (selectionStage == 3 && selected == AttackId.ASTEROID_PULLER) {
                selectionStage = 4;
                aim(mc, 256.5, 4.0, 256.5);
                readyAt = now + 10;
                log("SELECT sequence=" + sequence + " attack=ASTEROID_PULLER wireId=5 key=" + (sequence == 0 ? "M" : "network"));
            }
            return;
        }
        if (!cast) {
            aim(mc, 256.5, 4.0, 256.5);
            // Let normal player updates send the new look before the server re-aims the request.
            if (!baselineCaptured || now < readyAt) return;
            BlockPos hit = LaunchController.aimedBlock(mc.player, 256);
            require(hit != null && hit.getX() == 256 && hit.getY() == 3 && hit.getZ() == 256,
                    "Aim did not hit the disposable flat-world target: expected=256,3,256 actualHit=" + hit
                            + " playerPos=" + mc.player.posX + "," + mc.player.posY + "," + mc.player.posZ
                            + " eyeY=" + (mc.player.posY + mc.player.getEyeHeight())
                            + " yaw=" + mc.player.rotationYaw + " pitch=" + mc.player.rotationPitch
                            + " aimPoint=256.5,4.0,256.5");
            StellarServices.client().preparePresentation();
            StellarNetwork.requestLaunch(new LaunchRequest(AttackId.ASTEROID_PULLER, mc.player.dimension,
                    new BlockTarget(hit.getX(), hit.getY(), hit.getZ())));
            cast = true; castAt = now;
            log("CAST sequence=" + sequence + " prepared=true attack=ASTEROID_PULLER wireId=5");
            return;
        }
        observeCinema(mc);
        if (launch == null) {
            for (LaunchState state : launches().values()) if (state.getAttack() == AttackId.ASTEROID_PULLER) launch = state;
            require(launch != null || now - castAt < 60, "No authoritative Moon launch state received");
            if (launch == null) return;
            require(launch.getPhase() == LaunchPhase.MARKING, "Own launch did not arrive in MARKING");
            require(launch.getTarget().equals(new BlockTarget(256, 3, 256)), "Server launch target differs from the prepared aim");
            BlockTarget delivery = launch.getDeployment();
            double distance = Math.hypot(delivery.getX()+0.5-mc.player.posX,delivery.getZ()+0.5-mc.player.posZ);
            require(delivery.equals(launch.getTarget()),
                     "Cannon delivery missed the aimed smoke beacon: " + delivery);
            require(delivery.getY() == 3,"Cannon delivery did not use the loaded ground height");
            log("DELIVERY sequence=" + sequence + " caster=" + mc.player.posX + "," + mc.player.posZ
                    + " ground=" + delivery.getX() + "," + delivery.getY() + "," + delivery.getZ()
                    + " impact=256,3,256 distance=" + distance);
            log("LAUNCH sequence=" + sequence + " id=" + launch.getLaunchId() + " start=" + launch.getStartTick());
        }
        require(cinema != null || now - castAt < 12, "Prepared own Moon launch did not open MoonCinema");
        if (cinema == null) return;
        require(((LaunchState) field(cinema, "launch")).getLaunchId().equals(launch.getLaunchId()),
                "Cinema belongs to a different launch");
        double age = age(0);
        require(Math.abs(age - Math.max(0, now - launch.getStartTick())) < 1,
                "Cinema age is not synchronized to the authoritative launch");
        verifyAudio(mc, now);
        if (launches().containsKey(launch.getLaunchId())) {
            require(age < AsteroidPullerTiming.END + 20, "Asteroid Puller state did not complete at " + AsteroidPullerTiming.END + " ticks");
            if (!skipped && age < AsteroidPullerTiming.END - 1) require(mc.currentScreen == cinema, "Moon cinema closed early");
            if (sequence == 1 && age >= 290 && frameIndex == SKIP_FRAMES && !skipped) {
                StellarServices.client().stopPresentation();
                skipped = true; skippedAt = now;
                checkRestored(mc);
                require(launches().containsKey(launch.getLaunchId()), "Local skip removed the authoritative launch");
                log("SKIP sequence=1 age=" + age + " guiNull=true launchRetained=true soundsOwned=0");
            }
            if (skipped) {
                checkRestored(mc);
                if (skipCaptured && now - skippedAt >= 20 && !cancelRequested) {
                    cancelRequested = true;
                    log("CANCEL_REQUEST sequence=1 age=" + age);
                }
                require(age < 500, "Server cancellation was not acknowledged");
            }
            return;
        }
        checkRestored(mc);
        if (endedAt < 0) {
            require(sequence == 0 ? age >= AsteroidPullerTiming.END - 4 && age <= AsteroidPullerTiming.END + 20
                    : skipped && cancelRequested && age < 500, "Unexpected Moon completion age " + age);
            endedAt = now;
            log((sequence == 0 ? "NATURAL_END" : "CANCELED") + " sequence=" + sequence + " age=" + age);
        }
        if (now - endedAt < 60) return;
        require(launches().isEmpty() && ((Number) field(bridge, "awaitingUntil")).longValue() == -1,
                "Client effect state or presentation request leaked after completion");
        require(frameIndex == (sequence == 0 ? frames.length : SKIP_FRAMES), "Missing requested film captures");
        require(sequence == 0 || skipCaptured, "Missing post-skip world framebuffer capture");
        require(!sounds.isEmpty() && playingSources > 0, "No real cinematic audio source played");
        require(glChecks > 10 && cleanCameraFrames > 10, "Missing clean restored render/camera evidence");
        capture(mc, "sequence-" + sequence + "-restored.png");
        log("FINISHED sequence=" + sequence + " guiNull=true cameraRestored=true hudRestored=true effects=0"
                + " soundsOwned=0 soundsPlaying=0 frames=" + frameIndex + " glChecks=" + glChecks);
        sequence++;
        if (sequence == 2) { log("PASS"); mc.shutdown(); return; }
        selectionStage = frameIndex = playingSources = glChecks = cleanCameraFrames = 0;
        menuCaptured = detailsOpened = detailsCaptured = baselineCaptured = cast = skipped = skipCaptured = cancelRequested = false;
        launch = null; cinema = null; soundtrack = null; menu = null; cinemaAge = null;
        skippedAt = endedAt = -1; sounds.clear();
    }

    @SubscribeEvent public void soundSource(PlaySoundSourceEvent event) {
        ISound sound = event.getSound();
        if (!cast || !"stellar".equals(sound.getSoundLocation().getResourceDomain())
                || !sound.getSoundLocation().getResourcePath().startsWith("cinematic.")) return;
        require(!skipped && endedAt < 0, "Cinematic audio restarted after presentation closed");
        sounds.add(sound);
        log("AUDIO_SOURCE sequence=" + sequence + " name=" + sound.getSoundLocation() + " source=" + event.getUuid());
    }

    private void verifyAudio(Minecraft mc, long now) {
        for (ISound sound : sounds) {
            boolean playing = mc.getSoundHandler().isSoundPlaying(sound);
            if (playing) playingSources++;
            if (skipped && now - skippedAt >= 6 || endedAt >= 0 && now - endedAt >= 6)
                require(!playing, "Cinematic sound survived presentation cleanup: " + sound.getSoundLocation());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void incomingFov(EntityViewRenderEvent.FOVModifier event) {
        incomingFov = event.getFOV();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void outgoingFov(EntityViewRenderEvent.FOVModifier event) {
        if (cast && (skipped || endedAt >= 0) && sequence < 2)
            require(Math.abs(event.getFOV() - incomingFov) < 0.0001, "Cinematic FOV leaked after presentation closed");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void camera(EntityViewRenderEvent.CameraSetup event) {
        if (!cast || !(skipped || endedAt >= 0) || sequence >= 2) return;
        require(event.getEntity() == previousView && Math.abs(event.getRoll()) < 0.0001,
                "Cinematic view or camera roll leaked after presentation closed");
        cleanCameraFrames++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sequence >= 2 || selectionStage == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in Moon framebuffer render");
        glChecks++;
        require(mc.getRenderViewEntity() == previousView, "Cinematic camera leaked outside its render frame");
        if (selectionStage == -1 && !menuCaptured
                && System.nanoTime() - ((Number) field(menu, "openedAt")).longValue() >= 600_000_000L) {
            capture(mc, "moon-skill-menu.png"); menuCaptured = true;
            log("MENU_FRAME file=moon-skill-menu.png rows=" + AttackId.values().length); return;
        }
        if (selectionStage == -1 && detailsOpened && !detailsCaptured) {
            capture(mc, "moon-skill-details.png"); detailsCaptured = true;
            log("DETAILS_FRAME file=moon-skill-details.png attack=ASTEROID_PULLER"); return;
        }
        if (selectionStage == 4 && !cast && !baselineCaptured) {
            capture(mc, "sequence-" + sequence + "-baseline.png"); baselineCaptured = true; return;
        }
        observeCinema(mc);
        if (cinema == null || launch == null) return;
        if (skipped && !skipCaptured) {
            checkRestored(mc); capture(mc, "sequence-1-post-skip.png"); skipCaptured = true;
            log("SKIP_FRAME sequence=1 file=sequence-1-post-skip.png age=" + age(event.renderTickTime)); return;
        }
        int count = sequence == 0 ? frames.length : SKIP_FRAMES;
        if (frameIndex >= count || mc.currentScreen != cinema) return;
        double renderedAge = ((Number) field(cinema, "renderedAge")).doubleValue(), requested = frames[frameIndex];
        if (renderedAge < requested) return;
        require(Double.isFinite(renderedAge) && renderedAge - requested <= 6,
                "Requested framebuffer time missed by over 0.3 seconds: requested=" + requested + " actual=" + renderedAge);
        String name = "sequence-" + sequence + "-moon-" + (int) requested + "ticks.png";
        long bloom = bloomPasses();
        require(bloom > lastBloomPass,"Moon bloom stopped submitting between film captures");
        lastBloomPass = bloom;
        capture(mc, name); frameIndex++;
        log("FRAME sequence=" + sequence + " file=" + name + " requestedTicks=" + requested
                + " requestedSeconds=" + requested / 20 + " actualTicks=" + renderedAge
                + " source=AsteroidPullerCinema.renderedAge screen=AsteroidPullerCinema glError=0 bloomReady=true bloomPasses=" + bloom);
    }

    private static long bloomPasses() {
        try {
            Class<?> type = Class.forName("fr.frabous.stellar.client.EmissionBloom");
            Method ready = type.getDeclaredMethod("isReady"), passes = type.getDeclaredMethod("getCompletedPassCount");
            ready.setAccessible(true); passes.setAccessible(true);
            require((Boolean) ready.invoke(null),"Selective Moon bloom is not ready on the test renderer");
            return ((Number) passes.invoke(null)).longValue();
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private void observeCinema(Minecraft mc) {
        if (!screen(mc, "AsteroidPullerCinema")) return;
        if (cinema != null) { require(cinema == mc.currentScreen, "Moon cinema was replaced during a pass"); return; }
        cinema = mc.currentScreen;
        try {
            cinemaAge = cinema.getClass().getDeclaredMethod("age", float.class); cinemaAge.setAccessible(true);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot inspect MoonCinema.age", exception); }
        soundtrack = field(cinema, "soundtrack");
        log("CINEMA sequence=" + sequence + " openedByOwnServerState=true");
    }

    private double age(float partial) {
        try {
            double result = ((Number) cinemaAge.invoke(cinema, partial)).doubleValue();
            require(Double.isFinite(result), "Non-finite Moon cinema age"); return result;
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private void checkRestored(Minecraft mc) {
        require(mc.currentScreen == null && mc.getRenderViewEntity() == previousView
                && mc.gameSettings.hideGUI == previousHideGUI && mc.gameSettings.thirdPersonView == previousThirdPerson,
                "Presentation did not restore the GUI, HUD and camera");
        Object audio = soundtrack;
        if (optional(audio.getClass(), "owned") == null) audio = field(audio, "audio");
        require(((List<?>) field(audio, "owned")).isEmpty(), "Presentation left cinematic sounds owned");
    }

    private void capture(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(output, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        require(new File(new File(output, "screenshots"), name).isFile(), "Framebuffer capture was not written: " + name);
    }

    private static boolean screen(Minecraft mc, String name) {
        return mc.currentScreen != null && name.equals(mc.currentScreen.getClass().getSimpleName());
    }

    private static KeyBinding binding(Minecraft mc, String name) {
        for (KeyBinding key : mc.gameSettings.keyBindings) if (("key.stellar." + name).equals(key.getKeyDescription())) return key;
        throw new IllegalStateException("Missing Stellar key binding " + name);
    }

    private static void press(Minecraft mc, String name) {
        KeyBinding.onTick(binding(mc, name).getKeyCode());
        MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
    }

    private static void click(GuiScreen screen, int x, int y, int button) {
        try {
            Method click = screen.getClass().getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            click.setAccessible(true); click.invoke(screen, x, y, button);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void aim(Minecraft mc, double x, double y, double z) {
        double dx = x - mc.player.posX, dy = y - mc.player.posY - mc.player.getEyeHeight(), dz = z - mc.player.posZ;
        mc.player.rotationYaw = mc.player.prevRotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        mc.player.rotationPitch = mc.player.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.rotationYawHead = mc.player.prevRotationYawHead = mc.player.rotationYaw;
    }

    @SuppressWarnings("unchecked") private Map<UUID, LaunchState> launches() {
        return (Map<UUID, LaunchState>) field(bridge, "launches");
    }

    private static Field optional(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Object field(Object owner, String name) {
        Field field = optional(owner.getClass(), name);
        require(field != null, "Missing smoke inspection field " + owner.getClass().getName() + "." + name);
        try { return field.get(owner); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private static void log(String message) { System.out.println("STELLAR_MOON_SMOKE_" + message); }
    private static void require(boolean condition, String message) {
        if (!condition) { log("FAIL " + message); throw new IllegalStateException(message); }
    }
}
