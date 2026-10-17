package fr.frabous.stellar.client;

import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.GravityInversionRules;
import fr.frabous.stellar.api.LaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Local render-view entity. Never added to the world or used for server targeting. */
final class CinematicCamera {
    private static final Field FAR_PLANE = ReflectionHelper.findField(EntityRenderer.class, "farPlaneDistance", "field_78530_s");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final GuiScreen cinema;
    private final LaunchState launch;
    private final Entity player;
    private final View view;
    private final double originX, originY, originZ;
    private Entity framePrevious;
    private int frameThirdPerson;
    private Float frameFarPlane;
    private Boolean frameOcclusion;
    private Integer frameClouds;
    private Integer frameFogMode;
    private double terrainContext = -1;
    private double roll;
    private double fov = 60;
    private double aftermathCut = -1, aftermathBlendStart = Double.NaN;
    private RailgunCameraPath.AftermathPlan aftermathPlan;
    private RailgunCameraPath.Pose lastAftermathPose, aftermathBlendFrom;
    private AsteroidPullerCamera.Layout asteroidLayout;
    private CarrierFlight.Plan asteroidPlan;
    private double[] supernovaOffset;
    private double inversionHeading = Double.NaN;

    CinematicCamera(GuiScreen cinema, LaunchState launch) {
        this.cinema = cinema; this.launch = launch;
        player = mc.player;
        originX = mc.player.posX; originY = mc.player.posY + 1.6; originZ = mc.player.posZ;
        view = new View(mc.world);
        MinecraftForge.EVENT_BUS.register(this);
    }

    void close() {
        MinecraftForge.EVENT_BUS.unregister(this);
        restoreFrame();
        // Distant camera shots recycle terrain meshes; rebuild them around the restored player.
        if (mc.world == view.world) mc.renderGlobal.loadRenderers();
    }

    private void restoreFrame() {
        if (frameFogMode != null) {
            GlStateManager.setFog(frameFogMode == GL11.GL_EXP ? GlStateManager.FogMode.EXP
                    : frameFogMode == GL11.GL_EXP2 ? GlStateManager.FogMode.EXP2 : GlStateManager.FogMode.LINEAR);
            frameFogMode = null;
        }
        if (frameClouds != null) {
            mc.gameSettings.clouds = frameClouds;
            frameClouds = null;
        }
        if (frameOcclusion != null) {
            mc.renderChunksMany = frameOcclusion;
            frameOcclusion = null;
        }
        if (frameFarPlane != null) {
            setFarPlane(frameFarPlane);
            frameFarPlane = null;
        }
        if (framePrevious == null) return;
        if (mc.getRenderViewEntity() == view) mc.setRenderViewEntity(mc.world == view.world
                && mc.player == player && !framePrevious.isDead ? framePrevious : mc.player);
        mc.gameSettings.thirdPersonView = frameThirdPerson;
        framePrevious = null;
    }

    double[] projectBeamPoint(double heightAboveTarget, int width, int height) {
        return project(0, heightAboveTarget, 0, width, height);
    }

    /** GUI-space point for an anchor-relative position seen by this frame's view, or null behind it. */
    double[] project(double localX, double localY, double localZ, double width, double height) {
        double yaw = Math.toRadians(view.rotationYaw), pitch = Math.toRadians(view.rotationPitch);
        double dx = launch.getTarget().getX() + 0.5 + localX - view.posX;
        double dy = launch.getTarget().getY() + 1.1 + localY - view.posY;
        double dz = launch.getTarget().getZ() + 0.5 + localZ - view.posZ;
        double horizontal = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double depth = Math.cos(pitch) * horizontal - Math.sin(pitch) * dy;
        if (depth <= 0.1) return null;
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        double up = Math.sin(pitch) * horizontal + Math.cos(pitch) * dy;
        double angle = Math.toRadians(roll), rotatedRight = right * Math.cos(angle) - up * Math.sin(angle);
        double rotatedUp = right * Math.sin(angle) + up * Math.cos(angle);
        double scale = height / (2 * Math.tan(Math.toRadians(fov) / 2));
        return new double[] {width * 0.5 + rotatedRight / depth * scale, height * 0.5 - rotatedUp / depth * scale};
    }

    double[] eyeLocal() {
        return new double[] {view.posX - launch.getTarget().getX() - 0.5, view.posY - launch.getTarget().getY() - 1.1,
                view.posZ - launch.getTarget().getZ() - 0.5};
    }

    double fov() { return fov; }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { restoreFrame(); return; }
        if (mc.currentScreen != cinema || mc.world != view.world) return;
        if (mc.player != player) { mc.displayGuiScreen(null); return; }
        if (cinema instanceof SupernovaCinema) {
            double age = ((SupernovaCinema) cinema).age(event.renderTickTime);
            if (SupernovaVisuals.isSpaceShot(age)) { terrainContext = -1; return; }
            supernovaFrame(age);
            return;
        }
        if (cinema instanceof GravityInversionCinema) {
            inversionFrame(((GravityInversionCinema) cinema).age(event.renderTickTime), event.renderTickTime);
            return;
        }
        if (cinema instanceof AsteroidPullerCinema) {
            double age = ((AsteroidPullerCinema) cinema).age(event.renderTickTime);
            if (AsteroidPullerCamera.isSpaceShot(age)) { terrainContext = -1; return; }
            asteroidFrame(age);
            return;
        }
        double age = ((RailgunCinema) cinema).age(event.renderTickTime);
        RailgunCameraPath.Shot shot = RailgunCameraPath.shot(age);
        if (!RailgunCameraPath.isWorldShot(shot)) { terrainContext = -1; return; }
        double p = RailgunCameraPath.smooth(RailgunCameraPath.progress(age));
        BlockTarget target = launch.getTarget();
        double x = target.getX() + 0.5, y = target.getY() + 1.1, z = target.getZ() + 0.5;
        double cx, cy, cz, tx = x, ty = y, tz = z;
        roll = 0; fov = 60;
        if (shot == RailgunCameraPath.Shot.WORLD_MARK) {
            double yaw = Math.toRadians(player.rotationYaw);
            cx = originX - Math.sin(yaw) * 6 + Math.cos(yaw) * 2;
            cy = originY + 1.5;
            cz = originZ + Math.cos(yaw) * 6 + Math.sin(yaw) * 2;
            tx = originX; ty = originY - 0.7; tz = originZ;
            fov = 65;
        } else if (shot == RailgunCameraPath.Shot.WORLD_REAR) {
            double yaw = Math.toRadians(player.rotationYaw), distance = RailgunCameraPath.mix(7, 10, p);
            cx = originX + Math.sin(yaw) * distance + Math.cos(yaw) * 2;
            cy = originY + 3;
            cz = originZ - Math.cos(yaw) * distance + Math.sin(yaw) * 2;
            tx = RailgunCameraPath.mix(originX, x, 0.12);
            ty = RailgunCameraPath.mix(originY, y, 0.12);
            tz = RailgunCameraPath.mix(originZ, z, 0.12);
            fov = 65;
        } else if (shot == RailgunCameraPath.Shot.ASCENT || shot == RailgunCameraPath.Shot.CLOUDS) {
            p = RailgunCameraPath.smooth((age - 44) / 24);
            cx = x + RailgunCameraPath.mix(40, 25, p);
            cy = y + RailgunCameraPath.mix(100, 220, p);
            cz = z + RailgunCameraPath.mix(75, 42, p);
            ty = y + RailgunCameraPath.mix(220, 800, p);
            fov = RailgunCameraPath.mix(58, 52, p);
        } else if (shot == RailgunCameraPath.Shot.SKY) {
            // One continuous move from here to the impact: the lens tilts down with the falling beam front.
            cx = x + 270; cy = y + RailgunCameraPath.mix(105, 90, p); cz = z + 185;
            ty = y + 55 + RailgunVisualTiming.headHeight(age) * 0.75;
            tx = x + 15 * (1 - p);
            fov = RailgunCameraPath.mix(64, 68, p);
        } else if (shot == RailgunCameraPath.Shot.DESCENT) {
            cx = x + RailgunCameraPath.mix(270, 281, p);
            cy = y + RailgunCameraPath.mix(90, 105, p);
            cz = z + RailgunCameraPath.mix(185, 200, p);
            ty = y + 55 + RailgunVisualTiming.headHeight(age) * 0.75 + p * 15;
            fov = RailgunCameraPath.mix(68, 70, p);
        } else if (shot == RailgunCameraPath.Shot.IMPACT) {
            if (age < 336) {
                double follow = RailgunCameraPath.smooth((age - RailgunVisualTiming.IMPACT) / 50);
                double angle = 0.62 + follow * 0.05;
                cx = x + Math.cos(angle) * 345; cz = z + Math.sin(angle) * 345;
                cy = y + 105; ty = y + RailgunCameraPath.mix(70, 110, follow);
                fov = 70;
            } else if (age < 368) {
                double lookUp = RailgunCameraPath.smooth((age - 336) / 32);
                cx = x - 285; cz = z + 135; cy = y + 60;
                ty = y + RailgunCameraPath.mix(200, 1000, lookUp);
                roll = RailgunCameraPath.mix(-9, -18, lookUp);
                fov = 72;
            } else {
                cx = x + 450; cz = z + 310; cy = y + 190;
                ty = y + 125;
                fov = 66;
            }
            // Hard jolt at contact, then a long rumble; the push-in breathes the FOV back after the punch.
            double since = age - RailgunVisualTiming.IMPACT;
            double shake = 2.2 * Math.exp(-since * 0.35) + 0.35 * (1 - RailgunCameraPath.smooth((since - 20) / 60));
            cx += Math.sin(age * 2.1) * shake;
            cy += Math.sin(age * 2.7) * shake;
            roll += Math.sin(age * 1.9) * shake * 0.8;
            if (age < 336) fov += 6 * Math.exp(-since * 0.4);
        } else {
            double cut = RailgunCameraPath.aftermathCutStart(age);
            if (cut != aftermathCut) {
                aftermathCut = cut;
                aftermathPlan = RailgunCameraPath.planAftermath(age, x, y, z, this::terrainLoaded);
                aftermathBlendStart = Double.NaN;
                lastAftermathPose = null;
            }
            if (aftermathPlan == null) return;
            RailgunCameraPath.Pose composed = aftermathPlan.pose(age);
            if (Double.isNaN(aftermathBlendStart) && !Double.isNaN(aftermathPlan.rotation)
                    && !RailgunCameraPath.loadedView(composed, this::terrainLoaded)) {
                aftermathBlendStart = age;
                aftermathBlendFrom = lastAftermathPose == null ? composed : lastAftermathPose;
            }
            if (!Double.isNaN(aftermathBlendStart)) composed = RailgunCameraPath.blendPoses(
                    aftermathBlendFrom, aftermathPlan.aerial, (age - aftermathBlendStart) / 20);
            cx = composed.x; cy = composed.y; cz = composed.z;
            tx = composed.targetX; ty = composed.targetY; tz = composed.targetZ;
            roll = composed.roll; fov = composed.fov;
            if (age >= RailgunVisualTiming.FINAL_CUT) ty = Math.max(ty, terrainHeight(tx, tz) + 1);
        }
        // The reference excavates terrain; these close rim shots stay above the intact local heightmap.
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        int terrain = Math.max(Math.max(terrainHeight(bx - 2, bz - 2), terrainHeight(bx + 2, bz - 2)),
                Math.max(terrainHeight(bx - 2, bz + 2), terrainHeight(bx + 2, bz + 2)));
        cy = Math.max(cy, terrain + 6);
        if (shot == RailgunCameraPath.Shot.AFTERMATH) lastAftermathPose = new RailgunCameraPath.Pose(cx, cy, cz, tx, ty, tz, roll, fov);
        view.setPosition(cx, cy, cz);
        view.lastTickPosX = view.prevPosX = cx; view.lastTickPosY = view.prevPosY = cy; view.lastTickPosZ = view.prevPosZ = cz;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        view.rotationYaw = view.prevRotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        view.rotationPitch = view.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        framePrevious = mc.getRenderViewEntity();
        frameThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(view);
        // Relocated DUMMY sections report no visible faces. Let aerial traversal reach loaded ground while they compile.
        frameOcclusion = mc.renderChunksMany;
        mc.renderChunksMany = false;
        if (shot == RailgunCameraPath.Shot.AFTERMATH) {
            // High fallback views look through the vanilla cloud plane; keep the ground reveal unobstructed.
            frameClouds = mc.gameSettings.clouds;
            mc.gameSettings.clouds = 0;
        }
        double context = shot == RailgunCameraPath.Shot.AFTERMATH ? RailgunCameraPath.aftermathCutStart(age)
                : shot == RailgunCameraPath.Shot.IMPACT && age >= 368 ? 368
                : shot == RailgunCameraPath.Shot.IMPACT && age >= 336 ? 336 : RailgunCameraPath.CUTS[shot.ordinal()];
        if (context != terrainContext) {
            boolean reanchor = terrainContext < 0;
            terrainContext = context;
            // loadRenderers anchors its mesh pool to getRenderViewEntity(), so switch the view first.
            // Inside a world-camera context, setupTerrain updates chunk positions without clearing the entire compiled pool.
            if (reanchor) mc.renderGlobal.loadRenderers();
        }
    }

    private int terrainHeight(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return mc.world.getChunkProvider().getLoadedChunk(bx >> 4, bz >> 4) == null ? 0 : mc.world.getHeight(bx, bz);
    }

    private void supernovaFrame(double age) {
        BlockTarget target = launch.getTarget();
        double x = target.getX()+0.5, y = target.getY()+1.1, z = target.getZ()+0.5;
        RailgunCameraPath.Pose pose;
        if (age < fr.frabous.stellar.api.SupernovaTiming.STAR) {
            double yaw = Math.toRadians(player.rotationYaw);
            pose = new RailgunCameraPath.Pose(originX+Math.sin(yaw)*4+Math.cos(yaw)*2,originY+2,
                    originZ-Math.cos(yaw)*4+Math.sin(yaw)*2,x,y+2,z,0,65);
        } else {
            if (supernovaOffset == null) {
                supernovaOffset = terrainLoaded(x+135,z+140) ? new double[]{135,140}
                        : fallbackOffset((dx,dz) -> terrainLoaded(x+dx,z+dz));
                if (supernovaOffset == null) supernovaOffset = new double[]{originX-x,originZ-z};
            }
            double cx = x+supernovaOffset[0], cz = z+supernovaOffset[1];
            RailgunCameraPath.Pose local = SupernovaVisuals.groundCamera(age,supernovaOffset[0],supernovaOffset[1],
                    terrainHeight(cx,cz)+6-y);
            pose = new RailgunCameraPath.Pose(x+local.x,y+local.y,z+local.z,
                    x+local.targetX,y+local.targetY,z+local.targetZ,local.roll,local.fov);
        }
        roll = pose.roll; fov = pose.fov;
        double cy = Math.max(pose.y,terrainHeight(pose.x,pose.z)+6);
        view.setPosition(pose.x,cy,pose.z);
        view.lastTickPosX = view.prevPosX = pose.x; view.lastTickPosY = view.prevPosY = cy;
        view.lastTickPosZ = view.prevPosZ = pose.z;
        double dx = pose.targetX-pose.x, dy = pose.targetY-cy, dz = pose.targetZ-pose.z;
        view.rotationYaw = view.prevRotationYaw = (float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        view.rotationPitch = view.prevRotationPitch = (float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        framePrevious = mc.getRenderViewEntity(); frameThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0; mc.setRenderViewEntity(view);
        frameOcclusion = mc.renderChunksMany; mc.renderChunksMany = false;
        frameClouds = mc.gameSettings.clouds; mc.gameSettings.clouds = 0;
        if (terrainContext < 0) terrainContext = 0;
    }

    /** SS-05 shots, framed from the caster's side of the field where terrain is already received. */
    private void inversionFrame(double age, float partial) {
        BlockTarget target = launch.getTarget();
        double x = target.getX() + 0.5, y = target.getY() + 1.1, z = target.getZ() + 0.5;
        if (Double.isNaN(inversionHeading)) {
            double dx = originX - x, dz = originZ - z;
            inversionHeading = dx * dx + dz * dz < 4 ? Math.toRadians(player.rotationYaw + 90) : Math.atan2(dz, dx);
        }
        // The caster rides their own field: some shots frame their body instead of the aim point.
        double[] rider = null;
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial + player.height * 0.5;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial;
        if (GravityInversionRide.captured(launch, player, age)) rider = new double[] {px - x, py - y, pz - z};
        RailgunCameraPath.Pose local = GravityInversionVisuals.camera(launch.getSeed(), age, inversionHeading, rider);
        double cx = x + local.x, cz = z + local.z;
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        int ground = Math.max(Math.max(terrainHeight(bx - 1, bz - 1), terrainHeight(bx + 1, bz - 1)),
                Math.max(terrainHeight(bx - 1, bz + 1), terrainHeight(bx + 1, bz + 1)));
        // Low shots ride the local terrain, never under it.
        double cy = local.y < 12 ? Math.max(ground + local.y, y + local.y) : Math.max(y + local.y, ground + 4);
        roll = local.roll; fov = local.fov;
        view.setPosition(cx, cy, cz);
        view.lastTickPosX = view.prevPosX = cx; view.lastTickPosY = view.prevPosY = cy; view.lastTickPosZ = view.prevPosZ = cz;
        double dx = x + local.targetX - cx, dy = y + local.targetY - cy, dz = z + local.targetZ - cz;
        view.rotationYaw = view.prevRotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        view.rotationPitch = view.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        framePrevious = mc.getRenderViewEntity(); frameThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0; mc.setRenderViewEntity(view);
        frameOcclusion = mc.renderChunksMany; mc.renderChunksMany = false;
        if (age >= GravityInversionRules.IMPACT) { frameClouds = mc.gameSettings.clouds; mc.gameSettings.clouds = 0; }
        if (terrainContext < 0) terrainContext = 0;
    }

    private void asteroidFrame(double age) {
        BlockTarget target = launch.getTarget();
        double x = target.getX() + 0.5, y = target.getY() + 1.1, z = target.getZ() + 0.5;
        if (asteroidPlan == null) asteroidPlan = AsteroidPullerLance.plan(launch);
        // Choose received terrain once; later chunk arrivals must not move the camera.
        if (asteroidLayout == null) asteroidLayout = new AsteroidPullerCamera.Layout(originX - x, originY - y, originZ - z,
                player.rotationYaw, reach(x, z));
        RailgunCameraPath.Pose pose = AsteroidPullerCamera.world(asteroidPlan, asteroidLayout, age);
        double cx = x + pose.x, cz = z + pose.z;
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        int ground = Math.max(Math.max(terrainHeight(bx - 1, bz - 1), terrainHeight(bx + 1, bz - 1)),
                Math.max(terrainHeight(bx - 1, bz + 1), terrainHeight(bx + 1, bz + 1)));
        double cy = Math.max(y + pose.y, ground + 1.8);
        roll = pose.roll; fov = pose.fov;
        view.setPosition(cx, cy, cz);
        view.lastTickPosX = view.prevPosX = cx; view.lastTickPosY = view.prevPosY = cy; view.lastTickPosZ = view.prevPosZ = cz;
        double dx = x + pose.targetX - cx, dy = y + pose.targetY - cy, dz = z + pose.targetZ - cz;
        view.rotationYaw = view.prevRotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        view.rotationPitch = view.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        framePrevious = mc.getRenderViewEntity(); frameThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0; mc.setRenderViewEntity(view);
        frameOcclusion = mc.renderChunksMany; mc.renderChunksMany = false;
        // These near-ground shots reuse the existing mesh pool; clearing it stalls the summon.
        if (terrainContext < 0) terrainContext = 0;
    }

    /** Largest framing scale whose planned ground positions already have received chunks. */
    private double reach(double x, double z) {
        double[][] stations = {{18, 27}, {-12, 30}, {-24, 8}, {3, 24}, {40, 180}, {95, 225}};
        for (double scale : new double[] {1, 0.8, 0.65, 0.55}) {
            boolean loaded = true;
            for (double[] offset : stations) loaded &= terrainLoaded(x + offset[0] * scale, z + offset[1] * scale);
            if (loaded) return scale;
        }
        return 0.55;
    }

    /** Relative loaded offsets, farthest first; null reserves the caller's origin fallback. */
    static double[] fallbackOffset(java.util.function.BiPredicate<Double, Double> loaded) {
        for (double radius : new double[] {70, 60, 50, 40}) {
            for (int sector = 0; sector < 16; sector++) {
                double angle = Math.PI / 4 + sector * Math.PI * 2 / 16;
                double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
                if (loaded.test(x, z)) return new double[] {x, z};
            }
        }
        return null;
    }

    private boolean terrainLoaded(double x, double z) {
        return mc.world.getChunkProvider().getLoadedChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4) != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH) public void entities(RenderWorldLastEvent event) {
        if (framePrevious != null) CinematicEntities.render(view, event.getPartialTicks());
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        if (mc.currentScreen != cinema || event.getEntity() != view) return;
        event.setRoll((float) roll);
        float current = getFarPlane();
        if (frameFarPlane == null) frameFarPlane = current;
        // Minecraft rebuilds the projection after the sky; keep that pass consistent with the frustum.
        setFarPlane(Math.max(current, 1450));
        // Extend only the current render projection; terrain still comes from already loaded chunks.
        // Preserve the projection's FOV, aspect and off-axis offsets, including anaglyph adjustments.
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        float near = projection.get(14) / (projection.get(10) - 1);
        float far = cinema instanceof AsteroidPullerCinema ? Math.max(getFarPlane() * 2, 4096)
                : cinema instanceof SupernovaCinema || cinema instanceof GravityInversionCinema ? getFarPlane() * 2 : 2048;
        projection.put(10, -(far + near) / (far - near));
        projection.put(14, -2 * far * near / (far - near));
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadMatrix(projection);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private float getFarPlane() {
        try { return FAR_PLANE.getFloat(mc.entityRenderer); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot read cinematic far plane", exception); }
    }

    private void setFarPlane(float distance) {
        try { FAR_PLANE.setFloat(mc.entityRenderer, distance); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot restore cinematic far plane", exception); }
    }

    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier event) {
        if (mc.currentScreen == cinema && event.getEntity() == view) event.setFOV((float) fov);
    }

    @SubscribeEvent public void hud(RenderGameOverlayEvent.Pre event) {
        if (mc.currentScreen == cinema && event.getType() == RenderGameOverlayEvent.ElementType.ALL) event.setCanceled(true);
    }

    @SubscribeEvent public void hand(RenderHandEvent event) {
        if (mc.currentScreen == cinema) event.setCanceled(true);
    }

    @SubscribeEvent public void blockOverlay(RenderBlockOverlayEvent event) {
        if (mc.currentScreen == cinema) event.setCanceled(true);
    }

    @SubscribeEvent public void fog(EntityViewRenderEvent.FogDensity event) {
        if (mc.currentScreen == cinema && event.getEntity() == view) {
            if (frameFogMode == null) frameFogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
            // Density zero only disables exponential fog. Cancelling setup with LINEAR mode preserves stale near-player limits.
            GlStateManager.setFog(GlStateManager.FogMode.EXP);
            // Low-distance servers cannot supply a panorama; atmospheric haze softens the edge of received terrain.
            event.setDensity(limitedNovaTerrain() ? 0.025F : 0); event.setCanceled(true);
        }
    }

    @SubscribeEvent public void fogColor(EntityViewRenderEvent.FogColors event) {
        if (mc.currentScreen != cinema || event.getEntity() != view || !limitedNovaTerrain()) return;
        // Use the world's atmospheric color, without the near-player brightness attenuation that outlines the finite mesh edge.
        net.minecraft.util.math.Vec3d color=mc.world.getFogColor((float)event.getRenderPartialTicks());
        event.setRed((float)color.x);event.setGreen((float)color.y);event.setBlue((float)color.z);
    }

    private boolean limitedNovaTerrain() {
        return cinema instanceof SupernovaCinema && supernovaOffset != null
                && Math.hypot(supernovaOffset[0],supernovaOffset[1]) < 125;
    }

    private static final class View extends Entity {
        View(World world) { super(world); setSize(0.1F, 0.1F); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
        @Override public float getEyeHeight() { return 0; }
    }
}
