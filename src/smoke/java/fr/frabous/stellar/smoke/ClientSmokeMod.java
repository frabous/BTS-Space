package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Arrays;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.passive.EntityCow;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Development-only driver, compiled into its own source set and never packaged in Stellar. */
@Mod(modid = "stellarsmoke", name = "Stellar Smoke Driver", version = "1", acceptableRemoteVersions = "*")
public final class ClientSmokeMod {
    private static final AttackId[] ATTACKS = {AttackId.RAILGUN};
    private static final int LAUNCH_DELAY_TICKS = 10;
    private static final double NATURAL_END_TICKS = 514, END_SYNC_TOLERANCE_TICKS = 4;
    private final boolean naturalEnd = Boolean.getBoolean("stellar.smoke.naturalEnd");
    private long readyAt = -1, startedAt = -1;
    private int attackIndex, frameIndex;
    private boolean cast, checked, skipped, menu, menuCaptured, naturalClosed, previousHideGUI;
    private int previousThirdPerson;
    private Entity previousView;
    private EntityOtherPlayerMP cameraPlayer;
    private EntityCow cameraMob;
    private int localCameraModels, remoteCameraModels, mobCameraModels, distantCameraModels;
    private int frameLocalModels, frameRemoteModels, frameMobModels;
    private Object cinema;
    private Method cinemaAge;
    private Field cinemaSoundtrack, ownedSounds;
    private Method bloomReady, bloomPassCount;
    private long previousBloomPasses;
    private String terrainProbe;
    private int terrainProbeFrame = -1;
    private String incomingFog = "unobserved";
    // Requested launch ages; every capture logs the actual fractional cinema age separately.
    private final double[][] frames = {new double[] {0.5, 14, 22, 30, 40, 56, 72, 84, 100, 120, 150, 172, 194, 197, 206, 222, 236,
                    244, 250, 256, 261, 267, 274, 279, 284, 290, 298, 336, 374, 386, 396, 420, 454,
                    504, 516}};

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("stellar.smoke.blackhole")) return;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void inputIsolation(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        KeyBinding.unPressAllKeys();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void mouseIsolation(MouseEvent event) { event.setCanceled(true); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || LaunchController.remote(mc.player).isEmpty()) return;
        long now = mc.world.getTotalWorldTime();
        if (readyAt < 0) readyAt = now + 60;
        if (now < readyAt) return;
        if (!menu) {
            mc.gameSettings.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            for (KeyBinding key : mc.gameSettings.keyBindings) if ("key.stellar.menu".equals(key.getKeyDescription()))
                KeyBinding.onTick(key.getKeyCode());
            MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
            menu = true; readyAt = now + 20;
            System.out.println("STELLAR_SMOKE_MENU"); return;
        }
        if (!cast) {
            mc.displayGuiScreen(null);
            StellarNetwork.requestSelection(ATTACKS[attackIndex]);
            startedAt = now; cast = true; frameIndex = 0; checked = false; skipped = false;
            cinema = null; naturalClosed = false;
            previousHideGUI = mc.gameSettings.hideGUI;
            previousThirdPerson = mc.gameSettings.thirdPersonView;
            previousView = mc.getRenderViewEntity();
            cameraPlayer = new EntityOtherPlayerMP(mc.world,
                    new GameProfile(UUID.fromString("67a43263-8cbd-4a62-8f79-f8e0a0a58d20"), "CameraWitness"));
            cameraPlayer.setPosition(mc.player.posX + 1.5, mc.player.posY, mc.player.posZ);
            cameraPlayer.setNoGravity(true);
            mc.world.addEntityToWorld(-8100, cameraPlayer);
            cameraMob = new EntityCow(mc.world);
            cameraMob.setPosition(mc.player.posX - 1.5, mc.player.posY, mc.player.posZ);
            cameraMob.setNoGravity(true); cameraMob.setNoAI(true);
            mc.world.addEntityToWorld(-8101, cameraMob);
            System.out.println("STELLAR_SMOKE_FILM_AGE source=RailgunCinema.age pending=true naturalEnd=" + naturalEnd);
            return;
        }
        long age = now - startedAt;
        if (age == LAUNCH_DELAY_TICKS) {
            AttackId selected = ItemStellarRemote.selected(LaunchController.remote(mc.player));
            if (selected != ATTACKS[attackIndex]) throw new IllegalStateException("Server selection was not synchronized");
            mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
            System.out.println("STELLAR_SMOKE_CAST " + selected);
        }
        observeCinema(mc);
        if (naturalEnd && cinema != null) checkNaturalEnd(mc, launchAge(0));
        if (age >= 30 && !checked) {
            String screen = mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName();
            if (!"RailgunCinema".equals(screen))
                throw new IllegalStateException("Cinematic was not opened by server launch state; screen=" + screen);
            checked = true;
        }
        if (!naturalEnd && attackIndex == 0 && cinema != null && launchAge(0) >= 420 && !skipped) {
            StellarServices.client().stopPresentation();
            if (mc.currentScreen != null) throw new IllegalStateException("Local skip did not close the film");
            skipped = true; System.out.println("STELLAR_SMOKE_SKIP actualLaunchAge=" + launchAge(0));
        }
        if (age > 670) {
            if (mc.currentScreen != null) throw new IllegalStateException("Film did not close at the end: " + mc.currentScreen.getClass().getName());
            if (naturalEnd && (!naturalClosed || frameIndex != frames[attackIndex].length))
                throw new IllegalStateException("Natural ending or full-film captures were not completed");
            if (naturalEnd) checkRestored(mc);
            if (localCameraModels < 10 || remoteCameraModels < 10 || mobCameraModels < 10 || distantCameraModels < 10)
                throw new IllegalStateException("Detached camera did not draw local/remote players and mobs: "
                        + localCameraModels + "," + remoteCameraModels + "," + mobCameraModels + ", far=" + distantCameraModels);
            System.out.println("STELLAR_SMOKE_CAMERA_ENTITIES local=" + localCameraModels + " remote=" + remoteCameraModels
                    + " mob=" + mobCameraModels + " distant=" + distantCameraModels);
            System.out.println("STELLAR_SMOKE_FINISHED " + ATTACKS[attackIndex]);
            if (++attackIndex == ATTACKS.length) {
                System.out.println("STELLAR_SMOKE_PASS"); mc.shutdown();
            } else { cast = false; readyAt = now + 10; }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void entityFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frameLocalModels = frameRemoteModels = frameMobModels = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void cameraModel(RenderLivingEvent.Pre<?> event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!cast || mc.currentScreen != cinema || mc.getRenderViewEntity() == mc.player) return;
        if (event.getEntity() == mc.player) { localCameraModels++; frameLocalModels++; }
        else if (event.getEntity() == cameraPlayer) { remoteCameraModels++; frameRemoteModels++; }
        else if (event.getEntity() == cameraMob) { mobCameraModels++; frameMobModels++; }
        else return;
        if (mc.getRenderViewEntity().getDistanceSq(mc.player) > 128 * 128) distantCameraModels++;
        if (frameLocalModels > 1 || frameRemoteModels > 1 || frameMobModels > 1)
            throw new IllegalStateException("Cinematic entity rendered twice in one frame");
    }

    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || attackIndex >= ATTACKS.length) return;
        File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke"));
        if (menu && !menuCaptured && !cast && mc.world.getTotalWorldTime() >= readyAt - 5
                && "SkillMenu".equals(mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName())) {
            ScreenShotHelper.saveScreenshot(output, "skill-menu.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            checkBloom("skill-menu.png", false);
            menuCaptured = true;
            return;
        }
        if (!cast || frameIndex >= frames[attackIndex].length) return;
        observeCinema(mc);
        if (cinema == null) return;
        double age = launchAge(event.renderTickTime);
        double renderedAge = age;
        if (mc.currentScreen == cinema) {
            try {
                java.lang.reflect.Field rendered = cinema.getClass().getDeclaredField("renderedAge");
                rendered.setAccessible(true);
                renderedAge = rendered.getDouble(cinema);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read framebuffer cinema age", exception);
            }
        }
        double requestedAge = frames[attackIndex][frameIndex];
        if (renderedAge >= requestedAge) {
            if (!naturalEnd && attackIndex == 0 && requestedAge >= 430
                    && (!skipped || mc.currentScreen != null || mc.gameSettings.hideGUI))
                throw new IllegalStateException("Post-skip capture did not restore the world and HUD");
            if (naturalEnd && requestedAge >= 516 && naturalClosed) checkRestored(mc);
            String label = requestedAge == (long) requestedAge ? Long.toString((long) requestedAge) : Double.toString(requestedAge);
            String file = ATTACKS[attackIndex].getResourcePath() + "-" + label + "ticks.png";
            ScreenShotHelper.saveScreenshot(output, file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            if (terrainProbe != null && terrainProbeFrame == frameIndex) {
                System.out.println("STELLAR_SMOKE_TERRAIN capture=" + file + " " + terrainProbe);
                terrainProbe = null;
            }
            checkBloom(file, mc.currentScreen == cinema
                    || (!naturalEnd && age < NATURAL_END_TICKS - END_SYNC_TOLERANCE_TICKS));
            frameIndex++;
            System.out.println("STELLAR_SMOKE_FRAME " + file + " requestedLaunchAge=" + requestedAge
                    + " actualLaunchAge=" + age + " renderedAge=" + renderedAge
                    + " latenessTicks=" + (renderedAge - requestedAge)
                    + " source=RailgunCinema.age retainedWhenClosed=" + (mc.currentScreen != cinema)
                    + " skipped=" + skipped + " hudVisible=" + (mc.currentScreen == null && !mc.gameSettings.hideGUI) + " screen="
                    + (mc.currentScreen == null ? "world" : mc.currentScreen.getClass().getSimpleName()));
        }
    }

    private boolean wantsTerrainProbe() {
        if (!naturalEnd || !cast || cinema == null || attackIndex >= frames.length || frameIndex >= frames[attackIndex].length) return false;
        double requested = frames[attackIndex][frameIndex];
        return requested == 420 || requested == 454 || requested == 504;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void terrainFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && wantsTerrainProbe()) incomingFog = "unobserved";
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void incomingFog(EntityViewRenderEvent.FogDensity event) {
        if (!wantsTerrainProbe() || Minecraft.getMinecraft().currentScreen != cinema || !"unobserved".equals(incomingFog)) return;
        incomingFog = "mode=" + GL11.glGetInteger(GL11.GL_FOG_MODE) + ",start=" + GL11.glGetFloat(GL11.GL_FOG_START)
                + ",end=" + GL11.glGetFloat(GL11.GL_FOG_END) + ",density=" + GL11.glGetFloat(GL11.GL_FOG_DENSITY);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void terrainProbe(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!wantsTerrainProbe() || mc.currentScreen != cinema
                || launchAge(event.getPartialTicks()) < frames[attackIndex][frameIndex] - 1) return;
        try {
            Entity camera = mc.getRenderViewEntity();
            Field infosField = ReflectionHelper.findField(RenderGlobal.class, new String[] {"renderInfos", "field_72755_R"});
            Field frustumField = ReflectionHelper.findField(RenderGlobal.class, new String[] {"viewFrustum", "field_175008_n"});
            List<?> infos = (List<?>) infosField.get(mc.renderGlobal);
            ViewFrustum frustum = (ViewFrustum) frustumField.get(mc.renderGlobal);
            int dummy = 0, empty = 0, solid = 0;
            StringBuilder chunks = new StringBuilder();
            for (Object info : infos) {
                Field chunkField = ReflectionHelper.findField(info.getClass(), new String[] {"renderChunk", "field_178036_a"});
                RenderChunk chunk = (RenderChunk) chunkField.get(info);
                CompiledChunk compiled = chunk.getCompiledChunk();
                if (compiled == CompiledChunk.DUMMY) dummy++;
                else if (compiled.isEmpty()) empty++;
                if (!compiled.isLayerEmpty(BlockRenderLayer.SOLID)) {
                    solid++;
                    if (solid <= 16) chunks.append(chunk.getPosition()).append(';');
                }
            }
            int poolSolid = 0;
            for (RenderChunk chunk : frustum.renderChunks) if (!chunk.getCompiledChunk().isLayerEmpty(BlockRenderLayer.SOLID)) poolSolid++;
            Field launchField = cinema.getClass().getDeclaredField("launch");
            launchField.setAccessible(true);
            fr.frabous.stellar.api.LaunchState launch = (fr.frabous.stellar.api.LaunchState) launchField.get(cinema);
            int targetX = launch.getTarget().getX(), targetZ = launch.getTarget().getZ();
            int loaded = 0;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                if (mc.world.getChunkProvider().getLoadedChunk((targetX >> 4) + dx, (targetZ >> 4) + dz) != null) loaded++;
            }
            FloatBuffer projection = BufferUtils.createFloatBuffer(16), modelview = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
            float[] p = new float[16], m = new float[16]; projection.get(p); modelview.get(m);
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            StringBuilder depths = new StringBuilder();
            for (int sy = 1; sy <= 3; sy++) for (int sx = 1; sx <= 3; sx++) {
                depth.clear();
                GL11.glReadPixels(mc.displayWidth * sx / 4, mc.displayHeight * sy / 4, 1, 1,
                        GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
                depths.append(depth.get(0)).append(',');
            }
            terrainProbe = "worldAge=" + launchAge(event.getPartialTicks()) + " camera=" + camera.getClass().getName()
                    + " pos=" + camera.posX + ',' + camera.posY + ',' + camera.posZ
                    + " last=" + camera.lastTickPosX + ',' + camera.lastTickPosY + ',' + camera.lastTickPosZ
                    + " yawPitch=" + camera.rotationYaw + ',' + camera.rotationPitch + " bbox=" + camera.getEntityBoundingBox()
                    + " chunkCoords=" + camera.chunkCoordX + ',' + camera.chunkCoordY + ',' + camera.chunkCoordZ
                    + " player=" + mc.player.posX + ',' + mc.player.posY + ',' + mc.player.posZ
                    + " target=" + targetX + ',' + launch.getTarget().getY() + ',' + targetZ
                    + " targetChunk=" + (targetX >> 4) + ',' + (targetZ >> 4)
                    + " targetLoaded=" + (mc.world.getChunkProvider().getLoadedChunk(targetX >> 4, targetZ >> 4) != null)
                    + " loaded9x9=" + loaded + " renderInfos=" + infos.size() + " dummy=" + dummy + " empty=" + empty
                    + " solid=" + solid + " poolSolid=" + poolSolid + " solidPositions=" + chunks
                    + " incomingFog=" + incomingFog + " worldFogMode=" + GL11.glGetInteger(GL11.GL_FOG_MODE)
                    + " worldFogDensity=" + GL11.glGetFloat(GL11.GL_FOG_DENSITY)
                    + " fogStartEnd=" + GL11.glGetFloat(GL11.GL_FOG_START) + ',' + GL11.glGetFloat(GL11.GL_FOG_END)
                    + " depthSamples=" + depths + " projection=" + Arrays.toString(p) + " modelview=" + Arrays.toString(m)
                    + " renderer=" + mc.renderGlobal.getDebugInfoRenders();
            terrainProbeFrame = frameIndex;
            File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke"));
            ScreenShotHelper.saveScreenshot(output, "terrain-before-effects-" + (int) frames[attackIndex][frameIndex] + ".png",
                    mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot capture terrain diagnostics", exception);
        }
    }

    private void observeCinema(Minecraft mc) {
        if (mc.currentScreen == null || !"RailgunCinema".equals(mc.currentScreen.getClass().getSimpleName())) return;
        if (cinema != null) {
            if (mc.currentScreen != cinema) throw new IllegalStateException("Cinematic screen was replaced during the smoke pass");
            return;
        }
        try {
            cinema = mc.currentScreen;
            cinemaAge = cinema.getClass().getDeclaredMethod("age", float.class);
            cinemaAge.setAccessible(true);
            cinemaSoundtrack = cinema.getClass().getDeclaredField("soundtrack");
            cinemaSoundtrack.setAccessible(true);
            ownedSounds = cinemaSoundtrack.getType().getDeclaredField("owned");
            ownedSounds.setAccessible(true);
            System.out.println("STELLAR_SMOKE_FILM_AGE source=RailgunCinema.age actualLaunchAge=" + launchAge(0)
                    + " estimated=false naturalEnd=" + naturalEnd);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect cinema launch age and soundtrack", exception);
        }
    }

    private double launchAge(float partialTicks) {
        try {
            double age = (Double) cinemaAge.invoke(cinema, partialTicks);
            if (!Double.isFinite(age)) throw new IllegalStateException("Cinema launch age was not finite");
            return age;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read cinema launch age", exception);
        }
    }

    private void checkNaturalEnd(Minecraft mc, double age) {
        if (mc.currentScreen == cinema) {
            if (naturalClosed || age > NATURAL_END_TICKS + END_SYNC_TOLERANCE_TICKS)
                throw new IllegalStateException("Film remained open beyond the natural ending: actualLaunchAge=" + age);
            return;
        }
        if (naturalClosed) return;
        if (age < NATURAL_END_TICKS - 1 || age > NATURAL_END_TICKS + END_SYNC_TOLERANCE_TICKS)
            throw new IllegalStateException("Film closed outside the natural-ending window: actualLaunchAge=" + age);
        checkRestored(mc);
        naturalClosed = true;
        System.out.println("STELLAR_SMOKE_NATURAL_END actualLaunchAge=" + age
                + " source=RailgunCinema.age hudRestored=true cameraRestored=true soundtrackStopped=true");
    }

    private void checkRestored(Minecraft mc) {
        if (mc.currentScreen != null || mc.gameSettings.hideGUI != previousHideGUI
                || mc.gameSettings.thirdPersonView != previousThirdPerson || mc.getRenderViewEntity() != previousView)
            throw new IllegalStateException("Natural ending did not restore the world, HUD and camera");
        try {
            List<?> sounds = (List<?>) ownedSounds.get(cinemaSoundtrack.get(cinema));
            if (!sounds.isEmpty()) throw new IllegalStateException("Natural ending left soundtrack sounds owned: " + sounds.size());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect soundtrack cleanup", exception);
        }
    }

    private void checkBloom(String file, boolean requireBloom) {
        try {
            if (bloomReady == null) {
                Class<?> bloom = Class.forName("fr.frabous.stellar.client.EmissionBloom");
                bloomReady = bloom.getDeclaredMethod("isReady");
                bloomPassCount = bloom.getDeclaredMethod("getCompletedPassCount");
                bloomReady.setAccessible(true);
                bloomPassCount.setAccessible(true);
            }
            boolean ready = (Boolean) bloomReady.invoke(null);
            long passes = (Long) bloomPassCount.invoke(null);
            ContextCapabilities caps = GLContext.getCapabilities();
            boolean supported = caps.OpenGL20 && (caps.OpenGL30 || caps.GL_ARB_framebuffer_object)
                    && OpenGlHelper.isFramebufferEnabled();
            int glError = GL11.glGetError();
            System.out.println("STELLAR_SMOKE_BLOOM ready=" + ready + " passes=" + passes + " glError=" + glError
                    + " status=" + (!supported ? "unsupported" : requireBloom ? "required" : cast ? "post-film" : "pre-launch")
                    + " capture=" + file);
            if (glError != GL11.GL_NO_ERROR)
                throw new IllegalStateException("OpenGL error at " + file + ": " + glError);
            if (supported && requireBloom && (!ready || passes <= previousBloomPasses))
                throw new IllegalStateException("Bloom not ready or pass count did not increase at " + file
                        + ": ready=" + ready + ", passes=" + passes + ", previous=" + previousBloomPasses);
            previousBloomPasses = passes;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect bloom at " + file, exception);
        }
    }
}
