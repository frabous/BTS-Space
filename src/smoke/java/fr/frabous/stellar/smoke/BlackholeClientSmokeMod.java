package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.BufferUtils;

/** Connected-client acceptance driver; never included in the production jar. */
@Mod(modid = "stellarblackholeclientsmoke", name = "Stellar Blackhole Client Smoke", version = "1",
        acceptableRemoteVersions = "*", dependencies = "after:stellar")
public final class BlackholeClientSmokeMod {
    private final File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke-blackhole"));
    private final int[] frames = {20, 50, 100, 160, 200, 220, 260, 300, 340, 380, 420, 460, 500, 620, 730, 750, 790, 805, 840};
    private Object effects, bridge;
    private Field launchesField, stretchesField;
    private Method localMethod, ageMethod, lensPassesMethod, tidalBindsMethod, lensReadyMethod, tidalReadyMethod;
    private long lensAtCast, bindsAtCast;
    private boolean shadersWarm;
    private int dyingMobModels;
    private LaunchState launch;
    private long readyAt = -1, castAt = -1, endedAt = -1;
    private int sequence, frameIndex, spaceTicks, longestSpace, currentSpace, playerModels, mobModels;
    private int skippedPlayerModels, skippedMobModels, glChecks;
    private double maxY, maxRoll;
    private float incomingFov;
    private int skippedCameraFrames;
    private int cinematicFrames;
    private int manualTurnFrames;
    private double manualTurnYaw, manualTurnAge, manualTurnBlend, manualPlayerYaw, manualTargetYaw;
    private boolean manualOppositeCrossed;
    private int cinematicPlayerModels, cinematicMobModels;
    private boolean selected, cast, skipped, cancelRequested, baseline;
    private boolean oldHideGUI;
    private int oldThirdPerson;
    private Entity oldView;
    private final Map<Entity, float[]> beforeMatrices = new IdentityHashMap<>();
    private final Set<String> matrixProbes = new HashSet<>();
    private final Set<String> audioCues = new HashSet<>(), playingCues = new HashSet<>();
    private final List<ISound> blackholeSounds = new ArrayList<>();
    private final Map<String, Integer> audioCounts = new HashMap<>();
    private boolean audioRollbackChecked, audioSuspendChecked;
    private double audioSuspendAt = -1;
    private CompletableFuture<Void> screenshotWrites = CompletableFuture.completedFuture(null);
    private int screenshotsWritten;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.blackhole")) return;
        effects = StellarServices.client();
        bridge = field(effects, "bridge");
        launchesField = declared(bridge.getClass(), "launches");
        stretchesField = declared(effects.getClass(), "modelStretches");
        try {
            localMethod = effects.getClass().getDeclaredMethod("local", double.class, boolean.class);
            ageMethod = effects.getClass().getDeclaredMethod("age", LaunchState.class, double.class);
            localMethod.setAccessible(true); ageMethod.setAccessible(true);
            lensPassesMethod = Class.forName("fr.frabous.stellar.client.SpacetimeLens").getDeclaredMethod("getCompletedPassCount");
            tidalBindsMethod = Class.forName("fr.frabous.stellar.client.TidalShader").getDeclaredMethod("getBindCount");
            lensPassesMethod.setAccessible(true); tidalBindsMethod.setAccessible(true);
            lensReadyMethod = lensPassesMethod.getDeclaringClass().getDeclaredMethod("isReady");
            tidalReadyMethod = tidalBindsMethod.getDeclaringClass().getDeclaredMethod("isReady");
            lensReadyMethod.setAccessible(true); tidalReadyMethod.setAccessible(true);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        MinecraftForge.EVENT_BUS.register(this);
        log("READY bridge=" + effects.getClass().getName());
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sequence >= 2) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        verifyAudio(mc);
        mc.player.inventory.currentItem = 0;
        mc.playerController.updateController();
        require(mc.currentScreen == null || !"RailgunCinema".equals(mc.currentScreen.getClass().getSimpleName()),
                "Blackhole opened RailgunCinema");
        if (selected && mc.currentScreen != null && (audioSuspendAt < 0 || audioSuspendChecked)) {
            log("INPUT_SCREEN sequence=" + sequence + " screen=" + mc.currentScreen.getClass().getSimpleName());
            mc.displayGuiScreen(null);
        }
        if (selected) mc.setIngameNotInFocus();
        long now = mc.world.getTotalWorldTime();
        if (!selected) {
            if (!new File(output, "ready-" + sequence).exists() || LaunchController.remote(mc.player).isEmpty()) return;
            require(!mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.capabilities.allowFlying,
                    "Expected survival player without flight");
            mc.displayGuiScreen(null);
            mc.gameSettings.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            oldHideGUI = mc.gameSettings.hideGUI; oldThirdPerson = mc.gameSettings.thirdPersonView;
            oldView = mc.getRenderViewEntity();
            mc.gameSettings.thirdPersonView = 0;
            mc.setIngameNotInFocus();
            aim(mc, 256.5, 3.5, 256.5);
            StellarNetwork.requestSelection(AttackId.BLACKHOLE);
            selected = true; readyAt = now + 30;
            log("SELECT sequence=" + sequence + " y=" + mc.player.posY);
            return;
        }
        if (!cast) {
            aim(mc, 256.5, 3.5, 256.5);
            if (now < readyAt) return;
            require(ItemStellarRemote.selected(LaunchController.remote(mc.player)) == AttackId.BLACKHOLE,
                    "Server selection was not synchronized");
            mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
            cast = true; castAt = now;
            lensAtCast = counter(lensPassesMethod); bindsAtCast = counter(tidalBindsMethod);
            log("CAST sequence=" + sequence + " survival=true y=" + mc.player.posY);
            return;
        }
        if (launch == null) {
            for (LaunchState state : launches().values()) if (state.getAttack() == AttackId.BLACKHOLE) launch = state;
            require(launch != null || now - castAt < 80, "No server launch state received after right click");
            if (launch == null) return;
            log("LAUNCH sequence=" + sequence + " id=" + launch.getLaunchId() + " start=" + launch.getStartTick()
                    + " target=" + launch.getTarget().getX() + "," + launch.getTarget().getY() + "," + launch.getTarget().getZ());
        }
        double age = age(0);
        if (!shadersWarm && age >= 30 && age < 60) {
            // A first-use compile during the ascent stalls movement packets; it must happen on the ground.
            require(flag(lensReadyMethod) && flag(tidalReadyMethod), "Spacetime shaders were not compiled before the ascent");
            shadersWarm = true;
            log("SHADERS_WARM sequence=" + sequence + " age=" + age + " y=" + mc.player.posY);
        }
        if (sequence == 1 && age >= 320 && audioCues.contains("blackhole_horizon") && !audioRollbackChecked) {
            Object soundtrack = field(effects, "soundtrack");
            Map<String, Integer> before = new HashMap<>(audioCounts);
            try {
                Method update = soundtrack.getClass().getDeclaredMethod("update", LaunchState.class,
                        double.class, double.class, double.class, double.class, boolean.class);
                update.setAccessible(true);
                update.invoke(soundtrack, launch, age - 3, 1.0, 1.0, 1.0, true);
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            require(before.equals(audioCounts), "Clock correction replayed blackhole audio");
            audioRollbackChecked = true;
            log("AUDIO_CLOCK_ROLLBACK sequence=1 age=" + age + " rewind=3 replayed=false");
        }
        if (sequence == 1 && age >= 440 && audioRollbackChecked && !audioSuspendChecked) {
            if (audioSuspendAt < 0) {
                mc.displayGuiScreen(new GuiScreen() { });
                audioSuspendAt = age;
                log("AUDIO_SUSPEND sequence=1 age=" + age);
            } else {
                require(ownedAudio().isEmpty(), "Interface left blackhole audio owned");
                if (age - audioSuspendAt >= 5) {
                    audioSuspendChecked = true;
                    mc.displayGuiScreen(null);
                    log("AUDIO_RESUME sequence=1 age=" + age);
                }
            }
        }
        if (launches().containsKey(launch.getLaunchId())) {
            maxY = Math.max(maxY, mc.player.posY);
            if (mc.player.posY >= 300) { spaceTicks++; currentSpace++; longestSpace = Math.max(longestSpace, currentSpace); }
            else currentSpace = 0;
            // Sky/horizon and first-person tunnel, then close third-person model views.
            mc.gameSettings.thirdPersonView = age >= 195 && age < 210 || age >= 330 && age < 420 || age >= 470 && age < 510 ? 1 : 0;
            aim(mc, launch.getTarget().getX() + 0.5, 420, launch.getTarget().getZ() + 0.5);
            if (age >= 215 && age < 235) {
                aim(mc, mc.player.posX - 34, mc.player.posY + mc.player.getEyeHeight() - 23, mc.player.posZ + 58);
            }
            if (age >= 195 && age < 210 || age >= 370 && age < 390 || age >= 490 && age < 510) {
                Entity nearest = null;
                for (Entity entity : mc.world.loadedEntityList) if (entity instanceof EntityAnimal
                        && entity.isEntityAlive() && (nearest == null || mc.player.getDistanceSq(entity) < mc.player.getDistanceSq(nearest))) nearest = entity;
                if (nearest != null) aim(mc, nearest.posX, nearest.posY + nearest.getEyeHeight(), nearest.posZ);
            }
            double revealAge = age - (Double) field(effects, "revealStart");
            if (sequence == 1 && revealAge >= 92 && revealAge < 100) {
                float yaw = (float) ((Double) field(effects, "revealYaw") + 180 + (revealAge - 96) * 2);
                mc.player.prevRotationYaw = yaw - 2;
                mc.player.rotationYaw = yaw;
            }
            if ((long) age % 20 == 0) log("SAMPLE sequence=" + sequence + " age=" + age + " y=" + mc.player.posY
                    + " x=" + mc.player.posX + " z=" + mc.player.posZ + " mayfly=" + mc.player.capabilities.allowFlying
                    + " spaceTicks=" + spaceTicks + " thirdPerson=" + mc.gameSettings.thirdPersonView);
            // Orbit-dependent ascent times vary; interrupt after observing the actual travelling shot.
            if (sequence == 0 && age >= 230 && cinematicFrames >= 20 && !skipped) {
                require(maxRoll > 0.1, "Camera deformation was never observed before J");
                pressSkip(mc); skipped = true;
                require(local(0, true) == null && local(0, false) != null,
                        "J did not suppress presentation while retaining world effects");
                require(ownedAudio().isEmpty(), "J left blackhole sounds owned");
                log("J sequence=0 age=" + age + " presentationSuppressed=true effectsRetained=true");
            }
            if (sequence == 0 && age >= 520 && !cancelRequested && frameIndex >= 13) {
                cancelRequested = true; log("CANCEL_REQUEST sequence=0 age=" + age);
            }
            require(age < 860, "Launch did not complete at 800 ticks");
            return;
        }
        if (endedAt < 0) {
            require(sequence == 0 ? cancelRequested && age < 740 : age >= 799 && age <= 810,
                    "Unexpected completion age " + age);
            endedAt = now;
            mc.gameSettings.thirdPersonView = oldThirdPerson;
            mc.gameSettings.hideGUI = oldHideGUI;
            aim(mc, mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ + 8);
            log("END sequence=" + sequence + " age=" + age + " y=" + mc.player.posY);
        }
        if (now - endedAt < 60) return;
        require(mc.player.posY < 10 && mc.player.onGround && mc.player.isEntityAlive()
                && !mc.player.capabilities.allowFlying && !mc.player.capabilities.isFlying, "Release did not restore survival on ground");
        require(mc.currentScreen == null && mc.getRenderViewEntity() == oldView && mc.gameSettings.hideGUI == oldHideGUI
                && mc.gameSettings.thirdPersonView == oldThirdPerson, "HUD/view settings were not restored");
        require(longestSpace >= 100 && playerModels > 0 && mobModels > 0 && glChecks > 10,
                "Missing sustained altitude, stretched player/mob or GL evidence");
        require(cinematicPlayerModels > 10 && cinematicMobModels > 10,
                "Detached orbital camera did not render player/mob models: player=" + cinematicPlayerModels
                        + " mob=" + cinematicMobModels + " frames=" + cinematicFrames);
        long lensPasses = counter(lensPassesMethod) - lensAtCast, tidalBinds = counter(tidalBindsMethod) - bindsAtCast;
        require(lensPasses > 10 && tidalBinds > 10, "Missing spacetime lens or tidal vertex stage: lensPasses="
                + lensPasses + " tidalBinds=" + tidalBinds);
        log("SPACETIME sequence=" + sequence + " lensPasses=" + lensPasses + " tidalBinds=" + tidalBinds
                + " dyingMobModels=" + dyingMobModels);
        if (sequence == 0) require(skippedPlayerModels > 0 && skippedMobModels > 0 && skippedCameraFrames > 10,
                "Missing post-J model/camera evidence");
        else {
            require(frameIndex == frames.length && cinematicFrames > 10, "Missing natural-ending captures or orbital camera");
            require(manualTurnFrames > 1 && manualOppositeCrossed,
                    "Missing camera continuity check across the opposite heading");
            Set<String> expected = new HashSet<>(Arrays.asList("blackhole_ascent", "blackhole_space_bed",
                    "blackhole_reveal", "blackhole_accretion", "blackhole_tidal", "blackhole_horizon", "blackhole_return"));
            require(audioCues.containsAll(expected) && playingCues.containsAll(expected),
                    "Missing real audio sources: requested=" + audioCues + " playing=" + playingCues);
            require(audioRollbackChecked, "Missing audio clock-correction regression check");
            require(audioSuspendChecked, "Missing audio suspension regression check");
            for (String loop : Arrays.asList("blackhole_space_bed", "blackhole_accretion", "blackhole_tidal")) {
                require(audioCounts.getOrDefault(loop, 0) == 2, "Suspended loop did not resume once: " + loop);
            }
        }
        require(ownedAudio().isEmpty(), "Ending left blackhole audio owned");
        capture(mc, "sequence-" + sequence + "-restored.png");
        log("FINISHED sequence=" + sequence + " age=" + age + " maxY=" + maxY + " spaceTicks=" + spaceTicks
                + " longestSpace=" + longestSpace + " playerModels=" + playerModels + " mobModels=" + mobModels
                + " skippedPlayerModels=" + skippedPlayerModels + " skippedMobModels=" + skippedMobModels
                + " maxRoll=" + maxRoll + " skippedCameraFrames=" + skippedCameraFrames + " cinematicFrames=" + cinematicFrames
                + " manualTurnFrames=" + manualTurnFrames
                + " cinematicPlayerModels=" + cinematicPlayerModels + " cinematicMobModels=" + cinematicMobModels
                + " glChecks=" + glChecks + " audioSources=" + audioCues.size() + " audioPlaying=" + playingCues.size()
                + " audioStopped=true y=" + mc.player.posY + " mayfly=false onGround=true");
        if (skipped) pressSkip(mc);
        sequence++;
        if (sequence == 2) {
            try { screenshotWrites.join(); }
            catch (CompletionException exception) {
                log("FAIL Screenshot write failed: " + exception.getCause());
                throw exception;
            }
            require(screenshotsWritten == 36, "Missing completed framebuffer captures: " + screenshotsWritten);
            log("PASS"); mc.shutdown(); return;
        }
        selected = cast = skipped = cancelRequested = baseline = false;
        launch = null; readyAt = castAt = endedAt = -1; frameIndex = spaceTicks = longestSpace = currentSpace = 0;
        playerModels = mobModels = skippedPlayerModels = skippedMobModels = glChecks = dyingMobModels = 0; maxY = maxRoll = 0;
        skippedCameraFrames = cinematicFrames = manualTurnFrames = 0;
        manualOppositeCrossed = false;
        cinematicPlayerModels = cinematicMobModels = 0;
        audioCues.clear(); playingCues.clear(); blackholeSounds.clear();
        audioCounts.clear(); audioRollbackChecked = audioSuspendChecked = false; audioSuspendAt = -1;
    }

    @SubscribeEvent public void soundSource(PlaySoundSourceEvent event) {
        ISound sound = event.getSound();
        String path = sound.getSoundLocation().getResourcePath();
        if (!"stellar".equals(sound.getSoundLocation().getResourceDomain()) || !path.startsWith("cinematic.blackhole_")) return;
        String name = path.substring("cinematic.".length());
        int count = audioCounts.getOrDefault(name, 0) + 1;
        audioCounts.put(name, count);
        require(count == 1 || sound.canRepeat() && audioSuspendChecked && count == 2,
                "Blackhole cue replayed: " + name);
        audioCues.add(name); blackholeSounds.add(sound);
        log("AUDIO_SOURCE sequence=" + sequence + " name=" + name + " age=" + (launch == null ? -1 : age(0))
                + " repeat=" + sound.canRepeat() + " source=" + event.getUuid());
    }

    private List<?> ownedAudio() {
        return (List<?>) field(field(field(effects, "soundtrack"), "audio"), "owned");
    }

    private void verifyAudio(Minecraft mc) {
        for (ISound sound : blackholeSounds) {
            boolean playing = mc.getSoundHandler().isSoundPlaying(sound);
            if (playing) {
                String name = sound.getSoundLocation().getResourcePath().substring("cinematic.".length());
                if (playingCues.add(name)) log("AUDIO_PLAYING sequence=" + sequence + " name=" + name);
            }
            if (skipped && launch != null && age(0) > 236) require(!playing, "Blackhole audio survived J");
        }
        if (skipped) require(ownedAudio().isEmpty(), "Blackhole audio restarted after J");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void inputIsolation(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // The dev window can receive desktop input while the harness owns its controls.
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        KeyBinding.unPressAllKeys();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void mouseIsolation(MouseEvent event) { event.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void incomingFov(EntityViewRenderEvent.FOVModifier event) {
        incomingFov = event.getFOV();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void outgoingFov(EntityViewRenderEvent.FOVModifier event) {
        if (!skipped || launch == null || endedAt >= 0) return;
        require(Math.abs(event.getFOV() - incomingFov) < 0.0001, "FOV deformation survived J");
        if (++skippedCameraFrames == 1) log("J_CAMERA sequence=" + sequence + " age=" + age(event.getRenderPartialTicks())
                + " fov=" + event.getFOV() + " baselineFov=" + incomingFov + " deformation=0");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void camera(EntityViewRenderEvent.CameraSetup event) {
        if (launch == null || endedAt >= 0) return;
        maxRoll = Math.max(maxRoll, Math.abs(event.getRoll()));
        if ("SpaceView".equals(event.getEntity().getClass().getSimpleName())) {
            cinematicFrames++;
            double revealAge = age(event.getRenderPartialTicks()) - (Double) field(effects, "revealStart");
            if (sequence == 1 && revealAge >= 93 && revealAge < 100) {
                double yaw = event.getEntity().rotationYaw;
                double playerYaw = (Double) field(effects, "revealPlayerYaw");
                double targetYaw = (Double) field(effects, "revealYaw");
                double blend = (Double) field(effects, "revealBlend");
                if (manualTurnFrames > 0) {
                    double turn = ((yaw - manualTurnYaw) % 360 + 540) % 360 - 180;
                    // Bound continuous source/blend motion, including frames delayed by framebuffer captures.
                    double bound = Math.abs(playerYaw - manualPlayerYaw) + Math.abs(targetYaw - manualTargetYaw)
                            + Math.abs(blend - manualTurnBlend) * Math.abs(manualTargetYaw - manualPlayerYaw) + 0.01;
                    require(Math.abs(turn) <= bound, "Camera snapped during manual rotation: " + turn
                            + " bound=" + bound + " ticks=" + (revealAge - manualTurnAge));
                    manualOppositeCrossed |= Math.floor((playerYaw - targetYaw + 180) / 360)
                            != Math.floor((manualPlayerYaw - manualTargetYaw + 180) / 360);
                }
                manualTurnYaw = yaw;
                manualTurnAge = revealAge; manualTurnBlend = blend;
                manualPlayerYaw = playerYaw; manualTargetYaw = targetYaw;
                if (++manualTurnFrames == 1) log("CAMERA_MANUAL_TURN sequence=1 age=" + age(event.getRenderPartialTicks()));
            }
        }
        if (skipped) require(Math.abs(event.getRoll()) < 0.0001, "Camera roll survived J: " + event.getRoll()
                + " filmsEnabled=" + fr.frabous.stellar.client.LocalClientBridge.filmsEnabled
                + " skippedIds=" + field(effects, "skipped") + " launch=" + launch.getLaunchId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void model(RenderLivingEvent.Pre<?> event) {
        if (launch == null || endedAt >= 0) return;
        if (event.getEntity() != Minecraft.getMinecraft().player && !(event.getEntity() instanceof EntityAnimal)) return;
        Deque<?> scopes = (Deque<?>) value(stretchesField, effects);
        if (scopes.isEmpty()) return;
        Object scope = scopes.peek();
        if (field(scope, "event") != event || ((Integer) field(scope, "depth")) < 0) return;
        boolean player = event.getEntity() == Minecraft.getMinecraft().player;
        if ("SpaceView".equals(Minecraft.getMinecraft().getRenderViewEntity().getClass().getSimpleName())) {
            if (player) cinematicPlayerModels++;
            else cinematicMobModels++;
        }
        int bucket = (int) age(event.getPartialRenderTick()) / 20;
        String probe = sequence + ":" + bucket + ":" + player;
        if ((bucket == 10 || bucket == 17 || bucket == 19 || bucket == 25) && matrixProbes.add(probe)) {
            log("MATRIX sequence=" + sequence + " age=" + age(event.getPartialRenderTick()) + " player=" + player
                    + " entity=" + event.getEntity().getClass().getSimpleName()
                    + " height=" + event.getEntity().height + " width=" + event.getEntity().width
                    + " anchor=" + event.getX() + "," + event.getY() + "," + event.getZ()
                    + " before=" + Arrays.toString(beforeMatrices.get(event.getEntity()))
                    + " after=" + Arrays.toString(matrix(GL11.GL_MODELVIEW_MATRIX))
                    + " projection=" + Arrays.toString(matrix(GL11.GL_PROJECTION_MATRIX)));
        }
        if (player) { playerModels++; if (skipped) skippedPlayerModels++; }
        else { mobModels++; if (skipped) skippedMobModels++; }
        if (!player && event.getEntity().deathTime > 0 && ++dyingMobModels == 1)
            log("SPAGHETTI_DEATH sequence=" + sequence + " age=" + age(event.getPartialRenderTick())
                    + " entity=" + event.getEntity().getClass().getSimpleName() + " deathTime=" + event.getEntity().deathTime);
        if ((player ? playerModels : mobModels) == 1 || skipped && (player ? skippedPlayerModels : skippedMobModels) == 1)
            log("MODEL sequence=" + sequence + " age=" + age(event.getPartialRenderTick()) + " player=" + player
                    + " entity=" + event.getEntity().getClass().getSimpleName() + " y=" + event.getEntity().posY
                    + " transformed=true skipped=" + skipped);
    }

    @SubscribeEvent(priority = EventPriority.HIGH) public void beforeModelProbe(RenderLivingEvent.Pre<?> event) {
        if (launch != null && (event.getEntity() == Minecraft.getMinecraft().player || event.getEntity() instanceof EntityAnimal))
            beforeMatrices.put(event.getEntity(), matrix(GL11.GL_MODELVIEW_MATRIX));
    }

    private static float[] matrix(int name) {
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(name, values);
        float[] result = new float[16]; values.get(result); return result;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sequence >= 2 || !selected) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        int error = GL11.glGetError(); glChecks++;
        require(error == GL11.GL_NO_ERROR, "OpenGL error " + error);
        require(((Deque<?>) value(stretchesField, effects)).isEmpty(), "Model transformation scope leaked across frames");
        require(mc.getRenderViewEntity() == oldView, "Orbital camera leaked outside its render frame");
        if (!baseline && !cast) { capture(mc, "sequence-" + sequence + "-baseline.png"); baseline = true; }
        if (launch == null || frameIndex >= frames.length) return;
        double age = age(event.renderTickTime);
        if (age < frames[frameIndex]) return;
        if (sequence == 0 && frames[frameIndex] > 500) return;
        String name = "sequence-" + sequence + "-" + frames[frameIndex] + "ticks.png";
        capture(mc, name);
        log("FRAME sequence=" + sequence + " file=" + name + " requested=" + frames[frameIndex] + " actual=" + age
                + " source=BlackholeEffects.age y=" + mc.player.posY + " thirdPerson=" + mc.gameSettings.thirdPersonView
                + " presentation=" + (local(event.renderTickTime, true) != null)
                + " effects=" + (local(event.renderTickTime, false) != null) + " glError=0");
        frameIndex++;
    }

    private void capture(Minecraft mc, String name) {
        long started = System.nanoTime();
        BufferedImage image = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        double readbackMs = (System.nanoTime() - started) / 1_000_000.0;
        File directory = new File(output, "screenshots");
        require(directory.isDirectory() || directory.mkdirs(), "Cannot create screenshot directory");
        screenshotWrites = screenshotWrites.thenRunAsync(() -> {
            long encoding = System.nanoTime();
            try {
                if (!ImageIO.write(image, "png", new File(directory, name))) throw new IOException("PNG writer unavailable");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            screenshotsWritten++;
            log("SCREENSHOT_WRITTEN file=" + name + " readbackMs=" + readbackMs
                    + " encodeMs=" + (System.nanoTime() - encoding) / 1_000_000.0);
        });
    }

    private static void aim(Minecraft mc, double x, double y, double z) {
        double dx = x - mc.player.posX, dy = y - mc.player.posY - mc.player.getEyeHeight(), dz = z - mc.player.posZ;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.rotationYaw = mc.player.prevRotationYaw = yaw;
        mc.player.rotationPitch = mc.player.prevRotationPitch = pitch;
        mc.player.rotationYawHead = mc.player.prevRotationYawHead = yaw;
    }

    private static void pressSkip(Minecraft mc) {
        require(mc.currentScreen == null, "J input intercepted by a GUI");
        boolean before = fr.frabous.stellar.client.LocalClientBridge.filmsEnabled;
        for (KeyBinding key : mc.gameSettings.keyBindings) if ("key.stellar.skip".equals(key.getKeyDescription())) {
            KeyBinding.onTick(key.getKeyCode());
            MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
            boolean after = fr.frabous.stellar.client.LocalClientBridge.filmsEnabled;
            log("J_INPUT before=" + before + " after=" + after + " key=" + key.getKeyCode());
            require(before != after, "J binding did not reach ClientControls");
            return;
        }
        throw new IllegalStateException("J binding not registered");
    }

    @SuppressWarnings("unchecked") private Map<UUID, LaunchState> launches() {
        return (Map<UUID, LaunchState>) value(launchesField, bridge);
    }
    private static long counter(Method method) {
        try { return ((Number) method.invoke(null)).longValue(); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static boolean flag(Method method) {
        try { return (Boolean) method.invoke(null); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private double age(double partial) {
        try { return ((Number) ageMethod.invoke(null, launch, partial)).doubleValue(); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private Object local(double partial, boolean presentation) {
        try { return localMethod.invoke(effects, partial, presentation); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Field declared(Class<?> type, String name) {
        try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object value(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }
    private static Object field(Object owner, String name) { return value(declared(owner.getClass(), name), owner); }
    private static void log(String message) { System.out.println("STELLAR_BLACKHOLE_SMOKE_" + message); }
    private static void require(boolean condition, String message) {
        if (!condition) { log("FAIL " + message); throw new IllegalStateException(message); }
    }
}
