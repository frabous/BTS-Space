package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.AttackParameters;
import fr.frabous.stellar.api.BlackholeTides;
import fr.frabous.stellar.api.ClientBridge;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.LaunchTimeline;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Procedural celestial rendering and opt-out-aware, frame-local tidal presentation. */
final class BlackholeEffects implements ClientBridge {
    private static final double TAU = Math.PI * 2;
    private static final double[] DISK_SEGMENT_SINES = new double[193];
    private static final double[] DISK_SEGMENT_COSINES = new double[193];
    private static final int GOLD = 0xFFB04A;
    static {
        for (int segment = 0; segment <= 192; segment++) {
            double angle = segment * TAU / 192;
            DISK_SEGMENT_SINES[segment] = Math.sin(angle);
            DISK_SEGMENT_COSINES[segment] = Math.cos(angle);
        }
    }
    private final LocalClientBridge bridge;
    private final BlackholeSoundtrack soundtrack = new BlackholeSoundtrack();
    private final Set<UUID> skipped = new HashSet<>();
    private final double[][] stars = new double[2800][4];
    private final Deque<ModelStretch> modelStretches = new ArrayDeque<>();
    private final FloatBuffer stretchMatrix = BufferUtils.createFloatBuffer(16);
    private final double[] view = new double[16];
    // Rebuilt from this frame's model renders, drawn later with the hole geometry.
    private final Map<EntityLivingBase, Stream> streams = new IdentityHashMap<>();
    private final FloatBuffer lensMatrix = BufferUtils.createFloatBuffer(16);
    private final float[] lensProjection = new float[16];
    private int frameModelviewDepth = -1;
    private WorldClient world;
    private SpaceView spaceView;
    private Entity framePrevious;
    private int frameThirdPerson;
    private UUID revealLaunch;
    private double revealStart, revealBlend, revealYaw, revealPlayerYaw;

    BlackholeEffects(LocalClientBridge bridge) {
        this.bridge = bridge;
        Random random = new Random(0x535302L);
        for (double[] star : stars) {
            double angle = random.nextDouble() * TAU, y = random.nextDouble() * 2 - 1;
            double radial = Math.sqrt(1 - y * y);
            star[0] = Math.cos(angle) * radial;
            star[1] = y;
            star[2] = Math.sin(angle) * radial;
            star[3] = random.nextDouble();
        }
    }

    // Decorating the installed boundary lets J/stopPresentation reach this effect without editing the bridge.
    @Override public void enqueue(Runnable action) { bridge.enqueue(action); }
    @Override public void onSelection(AttackId attack) { bridge.onSelection(attack); }
    @Override public void onPresentationState(LaunchState state) { bridge.onPresentationState(state); }
    @Override public void onCarrierShot(fr.frabous.stellar.api.CarrierShot shot) { bridge.onCarrierShot(shot); }
    @Override public void onCarrierAim(UUID launchId, boolean valid, double x, double y, double z) {
        bridge.onCarrierAim(launchId, valid, x, y, z);
    }
    @Override public void onLaunchState(LaunchState state) {
        if (state.getAttack() == AttackId.BLACKHOLE && state.getPhase() == LaunchPhase.COMPLETE) {
            soundtrack.finish(state.getLaunchId());
        }
        bridge.onLaunchState(state);
        if (state.getPhase() == LaunchPhase.COMPLETE) skipped.remove(state.getLaunchId());
    }
    @Override public void preparePresentation() { bridge.preparePresentation(); }
    @Override public void stopPresentation() {
        skipBlackholes();
        restoreCamera();
        bridge.stopPresentation();
    }

    private void skipBlackholes() {
        soundtrack.stop();
        for (LaunchState state : bridge.launches.values()) {
            if (state.getAttack() == AttackId.BLACKHOLE) skipped.add(state.getLaunchId());
        }
    }
    @Override public void reset() {
        soundtrack.stop();
        restoreCamera(); skipped.clear(); world = null; revealLaunch = null; spaceView = null; bridge.reset();
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.world) {
            soundtrack.stop();
            restoreCamera(); skipped.clear(); revealLaunch = null; spaceView = null; world = mc.world;
        }
        skipped.retainAll(bridge.launches.keySet());
        if (mc.player != null && !mc.player.isEntityAlive()) skipBlackholes();
        if (mc.world == null || mc.player == null || !mc.player.isEntityAlive()
                || !LocalClientBridge.filmsEnabled) {
            soundtrack.stop();
            return;
        }
        if (mc.currentScreen != null) {
            soundtrack.suspend();
            return;
        }
        LaunchState soundState = audible();
        if (soundState == null) {
            soundtrack.update(null, 0, 0, 0, 0, false);
        } else {
            double time = age(soundState, 0);
            boolean revealed = soundState.getLaunchId().equals(revealLaunch) && time - revealStart >= 110;
            soundtrack.update(soundState, time, localStrength(soundState, 0), deformation(soundState, 0),
                    entry(soundState, 0), revealed);
        }
    }

    private LaunchState audible() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player.isCreative() || mc.player.isSpectator()) return null;
        LaunchState nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (LaunchState state : bridge.launches.values()) {
            if (!active(state, age(state, 0)) || skipped.contains(state.getLaunchId())) continue;
            double dx = mc.player.posX - state.getTarget().getX() - 0.5;
            double dz = mc.player.posZ - state.getTarget().getZ() - 0.5;
            double squared = dx * dx + dz * dz;
            if (squared < AttackParameters.BLACKHOLE_RADIUS_BLOCKS * AttackParameters.BLACKHOLE_RADIUS_BLOCKS
                    && squared < distance) { nearest = state; distance = squared; }
        }
        return nearest;
    }

    private LaunchState local(double partial, boolean presentation) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || !mc.player.isEntityAlive()
                || (mc.getRenderViewEntity() != mc.player && mc.getRenderViewEntity() != spaceView)
                || mc.currentScreen instanceof RailgunCinema
                || mc.currentScreen instanceof SupernovaCinema
                || mc.currentScreen instanceof GravityInversionCinema
                || (presentation && (mc.currentScreen != null || !LocalClientBridge.filmsEnabled))) return null;
        LaunchState strongest = null;
        double strength = 0;
        for (LaunchState state : bridge.launches.values()) {
            if (!active(state, age(state, partial)) || (presentation && skipped.contains(state.getLaunchId()))) continue;
            double value = localStrength(state, partial);
            if (value > strength) { strength = value; strongest = state; }
        }
        return strongest;
    }

    private static boolean active(LaunchState state, double age) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world != null && state.getAttack() == AttackId.BLACKHOLE
                && state.getDimension() == mc.world.provider.getDimension()
                && state.getPhase() != LaunchPhase.COMPLETE && age >= 0
                && age < LaunchTimeline.totalTicks(state.getAttack());
    }

    private static double age(LaunchState state, double partial) {
        return Minecraft.getMinecraft().world.getTotalWorldTime() - state.getStartTick() + partial;
    }

    private static double position(double previous, double current, double partial) {
        return previous + (current - previous) * partial;
    }

    private static double localStrength(LaunchState state, double partial) {
        Entity player = Minecraft.getMinecraft().player;
        double x = position(player.lastTickPosX, player.posX, partial) - state.getTarget().getX() - 0.5;
        double z = position(player.lastTickPosZ, player.posZ, partial) - state.getTarget().getZ() - 0.5;
        double y = position(player.lastTickPosY, player.posY, partial);
        return BlackholeVisualMath.visibility(age(state, partial))
                * BlackholeVisualMath.range(Math.hypot(x, z), AttackParameters.BLACKHOLE_RADIUS_BLOCKS)
                * BlackholeVisualMath.space(y, AttackParameters.BLACKHOLE_SPACE_Y);
    }

    private static double deformation(LaunchState state, double partial) {
        Entity player = Minecraft.getMinecraft().player;
        double x = position(player.lastTickPosX, player.posX, partial) - state.getTarget().getX() - 0.5;
        double z = position(player.lastTickPosZ, player.posZ, partial) - state.getTarget().getZ() - 0.5;
        double y = position(player.lastTickPosY, player.posY, partial) + player.getEyeHeight()
                - AttackParameters.BLACKHOLE_CENTER_Y;
        double tidal = BlackholeVisualMath.tidal(Math.sqrt(x * x + y * y + z * z),
                BlackholeVisualMath.horizonRadius(age(state, partial), AttackParameters.BLACKHOLE_HORIZON_RADIUS));
        return localStrength(state, partial) * BlackholeVisualMath.pull(age(state, partial))
                * (0.04 + 0.96 * tidal * tidal);
    }

    private static double entry(LaunchState state, double partial) {
        Entity player = Minecraft.getMinecraft().player;
        double x = position(player.lastTickPosX, player.posX, partial) - state.getTarget().getX() - 0.5;
        double y = position(player.lastTickPosY, player.posY, partial) + player.getEyeHeight()
                - AttackParameters.BLACKHOLE_CENTER_Y;
        double z = position(player.lastTickPosZ, player.posZ, partial) - state.getTarget().getZ() - 0.5;
        return BlackholeVisualMath.entry(Math.sqrt(x * x + y * y + z * z),
                BlackholeVisualMath.horizonRadius(age(state, partial), AttackParameters.BLACKHOLE_HORIZON_RADIUS));
    }

    private static final class ModelStretch {
        final RenderLivingEvent.Pre<?> event;
        final int depth;
        int program = -1; // Program to restore while the tidal vertex stage is bound.
        ModelStretch(RenderLivingEvent.Pre<?> event, int depth) { this.event = event; this.depth = depth; }
    }

    private static final class Stream {
        final LaunchState state;
        final double x, y, z, ax, ay, az, tip, tide, torn;
        final int seed;
        Stream(LaunchState state, double x, double y, double z, double ax, double ay, double az, double tip,
               double tide, double torn, int seed) {
            this.state = state; this.x = x; this.y = y; this.z = z; this.ax = ax; this.ay = ay; this.az = az;
            this.tip = tip; this.tide = tide; this.torn = torn; this.seed = seed;
        }
    }

    @SubscribeEvent public void spaceCamera(TickEvent.RenderTickEvent event) {
        restoreCamera();
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        LaunchState state = local(event.renderTickTime, true);
        if (state == null) return;
        double time = age(state, event.renderTickTime);
        double playerYaw = position(mc.player.prevRotationYaw,
                BlackholeVisualMath.unwrapAngle(mc.player.rotationYaw, mc.player.prevRotationYaw), event.renderTickTime);
        if (!state.getLaunchId().equals(revealLaunch)) {
            revealLaunch = state.getLaunchId(); revealStart = time;
            revealYaw = revealPlayerYaw = playerYaw;
        }
        revealPlayerYaw = BlackholeVisualMath.unwrapAngle(playerYaw, revealPlayerYaw);
        double elapsed = time - revealStart;
        if (elapsed >= 110 || mc.gameSettings.thirdPersonView != 0) return;
        revealBlend = BlackholeVisualMath.smooth(elapsed / 14)
                * (1 - BlackholeVisualMath.smooth((elapsed - 82) / 28));
        if (revealBlend <= 0.001) return;
        if (spaceView == null || spaceView.world != mc.world) spaceView = new SpaceView(mc.world);
        double tx = state.getTarget().getX() + 0.5, tz = state.getTarget().getZ() + 0.5;
        double px = position(mc.player.lastTickPosX, mc.player.posX, event.renderTickTime);
        double py = position(mc.player.lastTickPosY, mc.player.posY, event.renderTickTime) + mc.player.getEyeHeight();
        double pz = position(mc.player.lastTickPosZ, mc.player.posZ, event.renderTickTime);
        // Follow the near side of the eccentric orbit, keeping actual models inside the far plane.
        double angle = Math.atan2(pz - tz, px - tx) - 0.45 + elapsed * 0.0011;
        double distance = Math.hypot(px - tx, pz - tz) + 110 - elapsed * 0.3;
        double x = px + (tx + Math.cos(angle) * distance - px) * revealBlend;
        double y = py + (Math.min(AttackParameters.BLACKHOLE_CENTER_Y + 65, py + 65) - py) * revealBlend;
        double z = pz + (tz + Math.sin(angle) * distance - pz) * revealBlend;
        spaceView.setPosition(x, y, z);
        spaceView.lastTickPosX = spaceView.prevPosX = x;
        spaceView.lastTickPosY = spaceView.prevPosY = y;
        spaceView.lastTickPosZ = spaceView.prevPosZ = z;
        double dx = tx * 0.65 + px * 0.35 - x;
        double dy = AttackParameters.BLACKHOLE_CENTER_Y * 0.55 + py * 0.45 - y;
        double dz = tz * 0.65 + pz * 0.35 - z;
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        revealYaw = BlackholeVisualMath.unwrapAngle(yaw, revealYaw);
        spaceView.rotationYaw = spaceView.prevRotationYaw = (float) (revealPlayerYaw + (revealYaw - revealPlayerYaw) * revealBlend);
        double playerPitch = position(mc.player.prevRotationPitch, mc.player.rotationPitch, event.renderTickTime);
        spaceView.rotationPitch = spaceView.prevRotationPitch = (float) (playerPitch
                + (-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) - playerPitch) * revealBlend);
        framePrevious = mc.getRenderViewEntity(); frameThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(spaceView);
    }

    private void restoreCamera() {
        if (framePrevious == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getRenderViewEntity() == spaceView) mc.setRenderViewEntity(mc.world == spaceView.world
                && !framePrevious.isDead ? framePrevious : mc.player);
        mc.gameSettings.thirdPersonView = frameThirdPerson;
        framePrevious = null;
    }

    @SubscribeEvent public void hand(RenderHandEvent event) {
        if (framePrevious != null && revealBlend > 0.5) event.setCanceled(true);
    }

    @SubscribeEvent public void cinemaHud(RenderGameOverlayEvent.Pre event) {
        if (framePrevious == null || revealBlend <= 0.5) return;
        RenderGameOverlayEvent.ElementType type = event.getType();
        if (type == RenderGameOverlayEvent.ElementType.HOTBAR || type == RenderGameOverlayEvent.ElementType.CROSSHAIRS
                || type == RenderGameOverlayEvent.ElementType.HEALTH || type == RenderGameOverlayEvent.ElementType.FOOD
                || type == RenderGameOverlayEvent.ElementType.ARMOR || type == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void modelFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreModels(true);
            streams.clear();
            frameModelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        } else {
            restoreModels(true);
            frameModelviewDepth = -1;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void beforeModel(RenderLivingEvent.Pre<?> event) {
        restoreModels(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void stretchModel(RenderLivingEvent.Pre<?> event) {
        if (event.isCanceled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase entity = event.getEntity();
        double partial = event.getPartialRenderTick();
        double[] up = bodyAxis(entity, partial);
        double half = entity.height * 0.5;
        double x = position(entity.lastTickPosX, entity.posX, partial);
        double y = position(entity.lastTickPosY, entity.posY, partial);
        double z = position(entity.lastTickPosZ, entity.posZ, partial);
        double cx = x + up[0] * half, cy = y + up[1] * half, cz = z + up[2] * half;
        LaunchState strongest = null;
        double tide = 0, torn = 0;
        // Dying bodies stay in scope: the death animation completes their unravelling.
        // A model nested inside a bound tidal scope already follows the outer body's deformation.
        if (mc.world != null && entity.world == mc.world && !entity.isDead && !insideTidalScope()
                && MinecraftForgeClient.getRenderPass() >= 0 && y >= AttackParameters.BLACKHOLE_SPACE_Y) {
            double health = entity.getMaxHealth() > 0 ? entity.getHealth() / entity.getMaxHealth() : 1;
            double dying = entity.deathTime > 0 ? (entity.deathTime + partial) / 20 : 0;
            for (LaunchState state : bridge.launches.values()) {
                double age = age(state, partial);
                if (!active(state, age)) continue;
                double dx = state.getTarget().getX() + 0.5 - cx;
                double dy = AttackParameters.BLACKHOLE_CENTER_Y - cy;
                double dz = state.getTarget().getZ() + 0.5 - cz;
                double horizontal = Math.hypot(dx, dz), center = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double value = BlackholeVisualMath.modelStrength(age, y, horizontal, center,
                        AttackParameters.BLACKHOLE_RADIUS_BLOCKS, AttackParameters.BLACKHOLE_SPACE_Y,
                        AttackParameters.BLACKHOLE_HORIZON_RADIUS);
                // Same exposure as the server's lethal pulses: the visible shredding follows real damage.
                double shred = BlackholeVisualMath.visibility(age)
                        * BlackholeVisualMath.space(y, AttackParameters.BLACKHOLE_SPACE_Y)
                        * BlackholeVisualMath.range(horizontal, AttackParameters.BLACKHOLE_RADIUS_BLOCKS)
                        * BlackholeVisualMath.unravel(BlackholeTides.exposure(age, center), health, dying);
                if (value + shred > tide + torn) { tide = value; torn = shred; strongest = state; }
            }
        }
        // Track unaffected nested renders too, so their Post cannot close the outer entity's scope.
        if (strongest == null) {
            if (!modelStretches.isEmpty()) modelStretches.push(new ModelStretch(event, -1));
            return;
        }
        stretchMatrix.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, stretchMatrix);
        for (int i = 0; i < view.length; i++) view[i] = stretchMatrix.get(i);
        // Pre is before RenderLivingBase's entity translation and body rotation; event coordinates
        // are camera-relative world coordinates. Convert both the axis and anchor through this live view.
        double ox = event.getX() + up[0] * half, oy = event.getY() + up[1] * half, oz = event.getZ() + up[2] * half;
        double[] anchor = eye(view, ox, oy, oz, 1);
        double size = Math.max(0.3, 0.5 * Math.max(entity.height, entity.width));
        // Bodies very close to the eye can cross the near plane and become viewport-sized pointed shards.
        double readable = BlackholeVisualMath.readableModelStrength(1, length(anchor), size * 2);
        tide *= readable; torn *= readable;
        double hx = strongest.getTarget().getX() + 0.5 - cx, hy = AttackParameters.BLACKHOLE_CENTER_Y - cy;
        double hz = strongest.getTarget().getZ() + 0.5 - cz, toHole = Math.max(0.001, Math.sqrt(hx * hx + hy * hy + hz * hz));
        double amount = BlackholeVisualMath.spaghettiAmount(tide, torn);
        if (tide + torn > 0.08 && MinecraftForgeClient.getRenderPass() == 0) {
            double tip = Math.min(toHole * 0.8, BlackholeVisualMath.spaghettiAlong(BlackholeVisualMath.SPAGHETTI_LEAD, amount) * size);
            streams.put(entity, new Stream(strongest, cx, cy, cz, hx / toHole, hy / toHole, hz / toHole, tip,
                    tide, torn, entity.getEntityId()));
        }
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        GL11.glPushMatrix();
        ModelStretch scope = new ModelStretch(event, depth);
        modelStretches.push(scope);
        try {
            double[] axis = normalized(eye(view, hx, hy, hz, 0));
            // Server orbits run counter-clockwise from +X toward +Z: that tangent sets the shear.
            double[] drift = eye(view, hz, 0, -hx, 0);
            double along = dot(drift, axis);
            drift = new double[] {drift[0] - axis[0] * along, drift[1] - axis[1] * along, drift[2] - axis[2] * along};
            // Directly above or below the hole the tangent vanishes: any perpendicular keeps the shear finite.
            drift = normalized(length(drift) > 1e-6 ? drift : new double[] {0, axis[2], -axis[1]});
            double[] bodyUp = normalized(eye(view, up[0], up[1], up[2], 0));
            double[] alignment = BlackholeVisualMath.alignTo(bodyUp[0], bodyUp[1], bodyUp[2], axis[0], axis[1], axis[2],
                    BlackholeVisualMath.spaghettiAlignment(tide, torn));
            scope.program = TidalShader.bind(anchor, axis, drift, alignment, size, amount,
                    BlackholeVisualMath.spaghettiBend(size, tide, torn), BlackholeVisualMath.spaghettiFloor(torn),
                    size * 0.05 * (tide + torn), mc.world.getTotalWorldTime() + partial);
            if (scope.program < 0) {
                // Fixed-function fallback: one affine stretch about the body centre.
                double[] matrix = BlackholeVisualMath.tidalInView(view, hx, hy, hz, ox, oy, oz, Math.min(1, tide + torn));
                stretchMatrix.clear();
                for (double value : matrix) stretchMatrix.put((float) value);
                stretchMatrix.flip();
                GL11.glLoadMatrix(stretchMatrix);
            }
        } finally { GL11.glMatrixMode(mode); }
    }

    /** Name tags are drawn inside the living render, after the body: keep them upright and readable. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeLabel(RenderLivingEvent.Specials.Pre<?> event) {
        ModelStretch scope = modelStretches.peek();
        if (scope != null && scope.program >= 0 && scope.event.getEntity() == event.getEntity()) {
            TidalShader.unbind(scope.program);
            scope.program = -1;
        }
    }

    /** World direction of the model's long axis, following vanilla's death tilt about the feet. */
    private static double[] bodyAxis(EntityLivingBase entity, double partial) {
        if (entity.deathTime <= 0) return new double[] {0, 1, 0};
        double tilt = Math.min(1, Math.sqrt(Math.max(0, (entity.deathTime + partial - 1) / 20 * 1.6))) * Math.PI / 2;
        double yaw = Math.toRadians(180 - (entity.prevRenderYawOffset
                + MathHelper.wrapDegrees(entity.renderYawOffset - entity.prevRenderYawOffset) * partial));
        double side = -Math.sin(tilt);
        return new double[] {side * Math.cos(yaw), Math.cos(tilt), -side * Math.sin(yaw)};
    }

    private boolean insideTidalScope() {
        for (ModelStretch scope : modelStretches) if (scope.program >= 0) return true;
        return false;
    }

    private static double[] eye(double[] view, double x, double y, double z, double w) {
        return new double[] {view[0] * x + view[4] * y + view[8] * z + view[12] * w,
                view[1] * x + view[5] * y + view[9] * z + view[13] * w,
                view[2] * x + view[6] * y + view[10] * z + view[14] * w};
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double length(double[] vector) { return Math.sqrt(dot(vector, vector)); }

    private static double[] normalized(double[] vector) {
        double length = length(vector);
        return length > 1e-9 ? new double[] {vector[0] / length, vector[1] / length, vector[2] / length}
                : new double[] {0, 1, 0};
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterModel(RenderLivingEvent.Post<?> event) {
        closeModel(event.getEntity(), event.getRenderer());
    }

    // A player still gets this outer Post when its inner RenderLiving Pre was canceled.
    // No RenderPlayer Pre transform is applied: RenderLiving already covers player models once.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterPlayerModel(RenderPlayerEvent.Post event) {
        restoreModels(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterWorldModels(RenderWorldLastEvent event) { restoreModels(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void cinematicEntities(RenderWorldLastEvent event) {
        if (framePrevious != null) CinematicEntities.render(spaceView, event.getPartialTicks());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void beforeHudModels(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) restoreModels(true);
    }

    private void closeModel(EntityLivingBase entity, Object renderer) {
        ModelStretch match = null;
        for (ModelStretch scope : modelStretches) {
            if (scope.event.getEntity() == entity && scope.event.getRenderer() == renderer) { match = scope; break; }
        }
        if (match == null) return;
        while (true) {
            ModelStretch scope = modelStretches.pop();
            restoreModel(scope);
            if (scope == match) return;
        }
    }

    private void restoreModels(boolean all) {
        if (modelStretches.isEmpty()) return;
        boolean pushed = false;
        while (!modelStretches.isEmpty() && (all || modelStretches.peek().event.isCanceled())) {
            ModelStretch scope = modelStretches.pop();
            pushed |= scope.depth >= 0;
            restoreModel(scope);
        }
        // Missing Post can cause a renderer's outer pop to consume our push instead of its own.
        // At a frame boundary unwind the remaining levels to the original frame depth too.
        if (all && pushed && frameModelviewDepth >= 0) popModelviewTo(frameModelviewDepth);
    }

    private static void restoreModel(ModelStretch scope) {
        if (scope.program >= 0) { TidalShader.unbind(scope.program); scope.program = -1; }
        if (scope.depth >= 0) popModelviewTo(scope.depth);
    }

    private static void popModelviewTo(int depth) {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            int current = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
            while (current-- > depth) GL11.glPopMatrix();
        } finally { GL11.glMatrixMode(mode); }
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        LaunchState state = local(event.getRenderPartialTicks(), true);
        if (state == null) return;
        if (event.getEntity() == spaceView) {
            event.setRoll((float) (Math.sin(age(state, event.getRenderPartialTicks()) * 0.008) * 2 * revealBlend));
            return;
        }
        if (event.getEntity() != Minecraft.getMinecraft().player) return;
        double strength = deformation(state, event.getRenderPartialTicks());
        double time = age(state, event.getRenderPartialTicks());
        double approach = localStrength(state, event.getRenderPartialTicks());
        event.setRoll(event.getRoll() + (float) (approach * Math.sin(time * 0.009) * 1.4
                + strength * (5 * Math.sin(time * 0.025) + 0.5 * Math.sin(time * 0.13))));
        event.setPitch(event.getPitch() + (float) (strength * Math.sin(time * 0.035) * 0.7));
    }

    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier event) {
        LaunchState state = local(event.getRenderPartialTicks(), true);
        if (state == null) return;
        if (event.getEntity() == spaceView) {
            event.setFOV((float) (event.getFOV() + (58 - event.getFOV()) * revealBlend));
            return;
        }
        if (event.getEntity() != Minecraft.getMinecraft().player) return;
        double strength = deformation(state, event.getRenderPartialTicks());
        double approach = localStrength(state, event.getRenderPartialTicks());
        event.setFOV((float) Math.max(30, Math.min(110, event.getFOV() - approach * 4
                + strength * (18 + 1.5 * Math.sin(age(state, event.getRenderPartialTicks()) * 0.04)))));
    }

    @SubscribeEvent public void fog(EntityViewRenderEvent.FogColors event) {
        LaunchState state = local(event.getRenderPartialTicks(), false);
        if (state == null) return;
        float strength = (float) localStrength(state, event.getRenderPartialTicks());
        event.setRed(event.getRed() * (1 - strength) + 0.008F * strength);
        event.setGreen(event.getGreen() * (1 - strength) + 0.01F * strength);
        event.setBlue(event.getBlue() * (1 - strength) + 0.025F * strength);
    }

    /** Called before world meshes, after vanilla terrain. The sky only fills untouched depth pixels. */
    void renderSpace(float partial) {
        LaunchState state = local(partial, false);
        if (state == null) return;
        double strength = localStrength(state, partial);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(-1, 1, -1, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            untextured();
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthRange(1, 1); GL11.glDepthMask(false);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            EffectMesh.vertex(b, -1, -1, 0, 0x080915, (float) strength);
            EffectMesh.vertex(b, 1, -1, 0, 0x080915, (float) strength);
            EffectMesh.vertex(b, 1, 1, 0, 0x010107, (float) strength);
            EffectMesh.vertex(b, -1, 1, 0, 0x010107, (float) strength);
            Tessellator.getInstance().draw();
            // Restore the world projection for view-stable celestial directions.
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glPushMatrix();
            Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
            GL11.glTranslated(-position(camera.lastTickPosX, camera.posX, partial),
                    -position(camera.lastTickPosY, camera.posY, partial),
                    -position(camera.lastTickPosZ, camera.posZ, partial));
            GL11.glTranslated(position(camera.lastTickPosX, camera.posX, partial),
                    position(camera.lastTickPosY, camera.posY, partial) + camera.getEyeHeight(),
                    position(camera.lastTickPosZ, camera.posZ, partial));
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            starfield(state, partial, strength);
            spacePlanet(age(state, partial), strength);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(activeTexture);
        }
    }

    private void starfield(LaunchState state, float partial, double strength) {
        double time = age(state, partial);
        double warp = LocalClientBridge.filmsEnabled && !skipped.contains(state.getLaunchId())
                && framePrevious == null ? deformation(state, partial) * Math.pow(entry(state, partial), 2) : 0;
        Entity player = Minecraft.getMinecraft().player;
        // Interpolated like the camera itself, so the warp streaks do not jitter at 20 TPS.
        double dx = state.getTarget().getX() + 0.5 - position(player.lastTickPosX, player.posX, partial);
        double dy = AttackParameters.BLACKHOLE_CENTER_Y - position(player.lastTickPosY, player.posY, partial)
                - player.getEyeHeight();
        double dz = state.getTarget().getZ() + 0.5 - position(player.lastTickPosZ, player.posZ, partial);
        double skyRadius = Math.max(32, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 12);
        nebula(skyRadius, strength);
        drawStars(time, warp, dx, dy, dz, skyRadius, strength);
    }

    private static void spacePlanet(double time, double strength) {
        if (strength < 0.001) return;
        double skyRadius = Math.max(32, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 12);
        GL11.glPushMatrix();
        try {
            // A fixed celestial direction: the player can turn toward the planet at any time.
            GL11.glTranslated(-skyRadius * 0.34, -skyRadius * 0.23, skyRadius * 0.58);
            GL11.glRotated(23, 0, 0, 1);
            CelestialModels.earth(skyRadius * 0.15, time * 0.12, (float) strength);
        } finally { GL11.glPopMatrix(); }
    }

    private void nebula(double radius, double strength) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glPointSize(18);
        b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (double[] star : stars) {
            double band = Math.abs(star[1] + star[0] * 0.34);
            if (band > 0.24) continue;
            float alpha = (float) (strength * Math.pow(1 - band / 0.24, 2) * 0.032
                    * (0.4 + 0.6 * star[3]));
            EffectMesh.vertex(b, star[0] * radius, star[1] * radius, star[2] * radius,
                    star[3] < 0.6 ? 0x556F9C : 0x95736F, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private void drawStars(double time, double warp, double dx, double dy, double dz, double skyRadius, double strength) {
        double length = Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz));
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        for (int tier = 0; tier < 3; tier++) {
            GL11.glPointSize(tier == 0 ? 1.15F : tier == 1 ? 2.0F : 3.4F);
            b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
            for (double[] star : stars) {
                int level = star[3] < 0.78 ? 0 : star[3] < 0.975 ? 1 : 2;
                if (level != tier) continue;
                float alpha = (float) (strength * (0.25 + 0.65 * star[3])
                        * (0.97 + 0.03 * Math.sin(time * 0.009 + star[3] * 30)));
                EffectMesh.vertex(b, star[0] * skyRadius, star[1] * skyRadius, star[2] * skyRadius,
                        star[0] < -0.45 ? 0xBACFFF : 0xFFF0DD, alpha);
            }
            Tessellator.getInstance().draw();
        }
        GL11.glDisable(GL11.GL_POINT_SMOOTH);
        if (warp <= 0.001) return;
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] star : stars) {
            double dot = (star[0] * dx + star[1] * dy + star[2] * dz) / length;
            double stretch = warp * (0.01 + star[3] * 0.03);
            float alpha = (float) (strength * (0.2 + 0.2 * star[3])
                    * (0.88 + 0.12 * Math.sin(time * 0.018 + star[3] * 30)));
            int rgb = star[3] < 0.2 ? 0x9FBAFF : 0xFFF0D9;
            EffectMesh.vertex(b, star[0] * skyRadius, star[1] * skyRadius, star[2] * skyRadius, rgb, alpha);
            EffectMesh.vertex(b, (star[0] + (dx / length - star[0] * dot) * stretch + 0.0015) * skyRadius,
                    (star[1] + (dy / length - star[1] * dot) * stretch) * skyRadius,
                    (star[2] + (dz / length - star[2] * dot) * stretch) * skyRadius, rgb, alpha * 0.35F);
        }
        Tessellator.getInstance().draw();
    }

    /** WorldEffects already supplies camera-relative translation and restored GL attributes. */
    void renderWorld(LaunchState state, double age, Entity camera, float partial, boolean emission) {
        if (!active(state, age)) return;
        float alpha = (float) BlackholeVisualMath.visibility(age);
        if (alpha <= 0) return;
        // True world positions, like the bodies they leave: never inside the celestial proxy scale.
        matterStreams(state, age, alpha * (emission ? 0.45F : 1));
        double x = state.getTarget().getX() + 0.5, y = AttackParameters.BLACKHOLE_CENTER_Y;
        double z = state.getTarget().getZ() + 0.5;
        double cx = position(camera.lastTickPosX, camera.posX, partial);
        double cy = position(camera.lastTickPosY, camera.posY, partial) + camera.getEyeHeight();
        double cz = position(camera.lastTickPosZ, camera.posZ, partial);
        double distance = Math.sqrt((cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z));
        // A celestial proxy preserves angular size beyond vanilla's far plane without changing the camera/frustum.
        double far = Math.max(48, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 32 - 24);
        double proxy = Math.min(1, far / (distance + AttackParameters.BLACKHOLE_HORIZON_RADIUS * 7.2));
        double radius = BlackholeVisualMath.horizonRadius(age, AttackParameters.BLACKHOLE_HORIZON_RADIUS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(cx, cy, cz); GL11.glScaled(proxy, proxy, proxy);
            GL11.glTranslated(x - cx, y - cy, z - cz);
            celestialMesh(radius, age, state.getSeed(), alpha, cx - x, cy - y, cz - z, emission);
        } finally { GL11.glPopMatrix(); }
    }

    /** Luminous filaments torn from each spaghettified body, spiralling with the orbital flow into the hole. */
    private void matterStreams(LaunchState state, double age, float alpha) {
        if (streams.isEmpty()) return;
        double hx = state.getTarget().getX() + 0.5, hy = AttackParameters.BLACKHOLE_CENTER_Y;
        double hz = state.getTarget().getZ() + 0.5;
        double horizon = BlackholeVisualMath.horizonRadius(age, AttackParameters.BLACKHOLE_HORIZON_RADIUS);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        int drawn = 0;
        for (Stream stream : streams.values()) {
            double intensity = alpha * (0.2 * stream.tide + 0.8 * stream.torn);
            if (stream.state != state || intensity < 0.01 || drawn++ >= 64) continue;
            double sx = stream.x + stream.ax * stream.tip, sy = stream.y + stream.ay * stream.tip;
            double sz = stream.z + stream.az * stream.tip;
            double start = Math.max(1, Math.hypot(sx - hx, sz - hz)), heading = Math.atan2(sz - hz, sx - hx);
            double end = Math.min(start * 0.35, horizon * 0.3);
            for (int filament = 0; filament < 3; filament++) {
                double turn = 1.1 + 0.35 * filament + (stream.seed & 7) * 0.05;
                double spread = (filament - 1) * 0.05, phase = filament * 2.1 + (stream.seed & 31);
                double px = sx, py = sy, pz = sz;
                float previous = (float) (intensity * (0.55 + 0.45 * Math.sin(phase - age * 0.5)));
                int previousRgb = plasmaColor(0.3, 0);
                for (int step = 1; step <= 24; step++) {
                    double t = step / 24.0, ease = 1 - Math.pow(1 - t, 1.6);
                    double radius = start + (end - start) * ease, angle = heading + (turn + spread) * t;
                    double x = hx + Math.cos(angle) * radius, z = hz + Math.sin(angle) * radius;
                    double y = sy + (hy - sy) * ease;
                    // Bright knots travel inward along each filament, like matter falling into the disk.
                    float opacity = (float) (intensity * (0.55 + 0.45 * Math.sin(t * 26 + phase - age * 0.5))
                            * Math.pow(1 - t, 0.7));
                    int rgb = plasmaColor(0.3 + 0.65 * t, 0);
                    EffectMesh.vertex(b, px, py, pz, previousRgb, previous);
                    EffectMesh.vertex(b, x, y, z, rgb, opacity);
                    px = x; py = y; pz = z; previous = opacity; previousRgb = rgb;
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    /** Bends the finished scene before the hole is drawn; WorldEffects calls this right after renderSpace. */
    void renderLens(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        if (mc.world == null || camera == null) return;
        double fx = position(camera.lastTickPosX, camera.posX, partial);
        double fy = position(camera.lastTickPosY, camera.posY, partial);
        double fz = position(camera.lastTickPosZ, camera.posZ, partial);
        boolean viewRead = false;
        double[] hole = null, angles = null;
        double strength = 0, score = 0, holeAge = 0;
        for (LaunchState state : bridge.launches.values()) {
            double age = age(state, partial);
            if (!active(state, age)) continue;
            // Compile while the caster is still grounded: a first-use stall mid-ascent makes the
            // server reject the delayed movement burst as "moved too quickly".
            SpacetimeLens.warmUp();
            TidalShader.warmUp();
            double visibility = BlackholeVisualMath.visibility(age);
            if (visibility <= 0.001) continue;
            if (!viewRead) {
                // The live modelview maps feet-relative world offsets to eye space, as in WorldEffects.
                lensMatrix.clear();
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, lensMatrix);
                for (int i = 0; i < view.length; i++) view[i] = lensMatrix.get(i);
                viewRead = true;
            }
            double[] eye = eye(view, state.getTarget().getX() + 0.5 - fx, AttackParameters.BLACKHOLE_CENTER_Y - fy,
                    state.getTarget().getZ() + 0.5 - fz, 1);
            double[] lens = BlackholeVisualMath.lensAngles(length(eye),
                    BlackholeVisualMath.horizonRadius(age, AttackParameters.BLACKHOLE_HORIZON_RADIUS));
            if (lens[1] * visibility > score) {
                score = lens[1] * visibility; hole = eye; angles = lens; strength = visibility; holeAge = age;
            }
        }
        if (hole == null) return;
        lensMatrix.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, lensMatrix);
        lensMatrix.get(lensProjection);
        double[] direction = normalized(hole);
        // Disk angular momentum: frame dragging turns the bent image with the disk, as seen by this eye.
        double[] spin = normalized(eye(view, 0, -BlackholeVisualMath.DISK_COS, -BlackholeVisualMath.DISK_SIN, 0));
        double[] ripple = BlackholeVisualMath.ripple(holeAge);
        SpacetimeLens.render(lensProjection, direction, angles[0], angles[1], -0.35 * dot(spin, direction), strength,
                -hole[2], angles[2], ripple[0] * strength, angles[0] + ripple[1]);
    }

    private static void celestialMesh(double radius, double age, long seed, float alpha, double dx, double dy, double dz,
                                      boolean emission) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float opaque = alpha * (float) BlackholeVisualMath.horizonOpacity(distance, radius);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (opaque > 0 && !emission) {
                GL11.glEnable(GL11.GL_CULL_FACE); GL11.glCullFace(GL11.GL_BACK); GL11.glFrontFace(GL11.GL_CCW);
                GL11.glDepthMask(opaque >= 0.99F);
                horizon(radius, opaque);
            }
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDepthMask(false);
            double[] view = BlackholeVisualMath.diskView(dx, dy, dz);
            disk(radius, age, seed, alpha * (emission ? 0.32F : 1), view[0], view[2]);
            // Interior orbits need smaller apparent photon arcs, rather than another viewport-filling shell.
            double photonRadius = BlackholeVisualMath.lensRadius(distance, radius);
            if (distance <= radius) photonRadius = Math.min(photonRadius, distance * 0.5);
            lens(photonRadius, dx, dy, dz, age,
                    alpha * (emission ? 0.38F : 1));
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            strands(radius, age, seed, alpha * (emission ? 0.08F : 0.22F));
        } finally { GL11.glPopAttrib(); }
    }

    private static void horizon(double radius, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int latitude = 0; latitude < 32; latitude++) {
            for (int longitude = 0; longitude < 64; longitude++) {
                sphereVertex(b, radius, latitude, longitude, alpha);
                sphereVertex(b, radius, latitude, longitude + 1, alpha);
                sphereVertex(b, radius, latitude + 1, longitude + 1, alpha);
                sphereVertex(b, radius, latitude + 1, longitude, alpha);
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void sphereVertex(BufferBuilder b, double radius, int latitude, int longitude, float alpha) {
        double angle = longitude * TAU / 64, elevation = latitude * Math.PI / 32;
        EffectMesh.vertex(b, Math.cos(angle) * Math.sin(elevation) * radius,
                Math.cos(elevation) * radius, Math.sin(angle) * Math.sin(elevation) * radius, 0x000000, alpha);
    }

    private static void disk(double radius, double age, long seed, float alpha, double dx, double dz) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Two plasma surfaces give the disk a readable thickness even when the orbit crosses its plane.
        for (int sheet = -1; sheet <= 1; sheet += 2) {
            for (int band = 0; band < 32; band++) {
                for (int segment = 0; segment < 192; segment++) {
                    diskVertex(b, radius, band, segment, age, seed, alpha, sheet, dx, dz);
                    diskVertex(b, radius, band + 1, segment, age, seed, alpha, sheet, dx, dz);
                    diskVertex(b, radius, band + 1, segment + 1, age, seed, alpha, sheet, dx, dz);
                    diskVertex(b, radius, band, segment + 1, age, seed, alpha, sheet, dx, dz);
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void diskVertex(BufferBuilder b, double radius, int band, int segment, double age, long seed,
                                   float alpha, int sheet, double dx, double dz) {
        double t = band / 32.0, angle = segment * TAU / 192;
        double sin = DISK_SEGMENT_SINES[segment], cos = DISK_SEGMENT_COSINES[segment];
        double radial = radius * (1.10 + t * 3.35);
        double phase = angle * 17 - age * (0.07 / Math.pow(1 + t * 3.35, 1.5)) + (seed & 1023) * 0.01;
        double filaments = Math.sin(phase + t * 172 + Math.sin(angle * 7 - age * 0.012) * 1.8);
        double turbulence = 0.62 + 0.22 * filaments + 0.16 * Math.sin(t * 290 - phase * 2.3);
        double heat = BlackholeVisualMath.diskHeat(t);
        double doppler = -sin * dx + cos * dz;
        double brightness = (0.68 + 0.32 * doppler) * turbulence;
        double edge = BlackholeVisualMath.smooth(t / 0.04) * (1 - BlackholeVisualMath.smooth((t - 0.55) / 0.45));
        int rgb = plasmaColor(heat, doppler);
        double px = cos * radial, pz = sin * radial;
        double py = radius * (sheet * 0.016 + filaments * 0.006 * (1 + t))
                * (1 - BlackholeVisualMath.smooth((t - 0.55) / 0.45));
        EffectMesh.vertex(b, px, py * BlackholeVisualMath.DISK_COS - pz * BlackholeVisualMath.DISK_SIN,
                py * BlackholeVisualMath.DISK_SIN + pz * BlackholeVisualMath.DISK_COS,
                rgb, alpha * (float) (edge * brightness * (0.95 - t * 0.62)));
    }

    private static int plasmaColor(double heat, double doppler) {
        double white = BlackholeVisualMath.smooth((heat - 0.42) / 0.48);
        int red = (int) (166 + 89 * BlackholeVisualMath.smooth(heat * 2));
        int green = (int) (55 + 120 * heat + 80 * white);
        int blue = (int) (14 + 63 * heat + 166 * white + Math.max(0, doppler) * 12);
        return Math.min(255, red) << 16 | Math.min(255, green) << 8 | Math.min(255, blue);
    }

    private static void lens(double radius, double dx, double dy, double dz, double age, float alpha) {
        double distance = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double nx = dx / distance, ny = dy / distance, nz = dz / distance;
        double[] basis = BlackholeVisualMath.lensBasis(nx, ny, nz);
        double rx = basis[0], ry = basis[1], rz = basis[2];
        double ux = basis[3], uy = basis[4], uz = basis[5], inclination = basis[6];
        double ratio = Math.min(0.999, radius / distance);
        double tangentRadius = radius * Math.sqrt(1 - ratio * ratio);
        double front = radius * ratio;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int band = 0; band < 24; band++) {
            for (int segment = 0; segment < 192; segment++) {
                lensVertex(b, tangentRadius, front, band, segment, rx, ry, rz, ux, uy, uz, nx, ny, nz, inclination, age, alpha);
                lensVertex(b, tangentRadius, front, band + 1, segment, rx, ry, rz, ux, uy, uz, nx, ny, nz, inclination, age, alpha);
                lensVertex(b, tangentRadius, front, band + 1, segment + 1, rx, ry, rz, ux, uy, uz, nx, ny, nz, inclination, age, alpha);
                lensVertex(b, tangentRadius, front, band, segment + 1, rx, ry, rz, ux, uy, uz, nx, ny, nz, inclination, age, alpha);
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void lensVertex(BufferBuilder b, double radius, double front, int band, int segment,
                                    double rx, double ry, double rz, double ux, double uy, double uz,
                                    double nx, double ny, double nz, double inclination, double age, float alpha) {
        double t = band / 24.0, angle = segment * TAU / 192;
        double sin = DISK_SEGMENT_SINES[segment], cos = DISK_SEGMENT_COSINES[segment];
        double polar = Math.abs(sin);
        double radial = radius * (1.012 + t * 0.90);
        double side = cos * radial;
        double up = sin * radial;
        double photon = Math.exp(-t * t * 1500);
        double arc = inclination * Math.pow(polar, 1.5) * (0.14 * Math.pow(1 - t, 3)
                + 1.35 * Math.exp(-Math.pow((t - 0.12) / 0.085, 2)));
        double weave = 0.72 + 0.18 * Math.sin(t * 170 + angle * 13 - age * 0.032)
                + 0.10 * Math.sin(t * 310 - angle * 9 + age * 0.015);
        double doppler = cos * inclination;
        double boost = 0.75 + 0.25 * doppler;
        // Observer-facing approximation of the disk's bent secondary image, outside the black shadow.
        EffectMesh.vertex(b, rx * side + ux * up + nx * front,
                ry * side + uy * up + ny * front, rz * side + uz * up + nz * front,
                plasmaColor(1 - t * 0.65, doppler),
                alpha * (float) Math.min(1, (photon * 0.90 + arc * weave)
                        * boost * (sin < 0 ? 1 - 0.28 * inclination : 1)));
    }

    private static void strands(double radius, double age, long seed, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int strand = 0; strand < 40; strand++) {
            double phase = (age * 0.004 + strand * 0.61803398875) % 1;
            double angle = strand * 2.399963 + age * 0.018 + (seed & 255) * 0.01;
            double outer = radius * (1.3 + (1 - phase) * 5.1);
            double height = Math.sin(strand * 7.1) * radius * (1 - phase) * 1.8;
            for (int step = 0; step < 14; step++) {
                for (int end = 0; end < 2; end++) {
                    double t = (step + end) / 14.0;
                    double a = angle - t * (0.18 + phase * 0.65);
                    double radial = outer + t * radius * (0.2 + phase * 0.9);
                    float opacity = alpha * (float) (Math.sin(phase * Math.PI) * (1 - t) * 0.65);
                    EffectMesh.vertex(b, Math.cos(a) * radial, height + t * height * 0.3,
                            Math.sin(a) * radial, strand % 4 == 0 ? 0xBFCFFF : GOLD, opacity);
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    @SubscribeEvent public void tunnel(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        LaunchState state = local(event.getPartialTicks(), true);
        Minecraft mc = Minecraft.getMinecraft();
        if (state == null || mc.gameSettings.thirdPersonView != 0) return;
        double presentation = localStrength(state, event.getPartialTicks());
        double strength = framePrevious == null
                ? deformation(state, event.getPartialTicks()) * entry(state, event.getPartialTicks()) : 0;
        if (presentation <= 0.001) return;
        double age = age(state, event.getPartialTicks());
        double width = event.getResolution().getScaledWidth(), height = event.getResolution().getScaledHeight();
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            untextured(); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            double bar = height * 0.055 * presentation;
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int edge = 0; edge < 2; edge++) {
                double top = edge == 0 ? 0 : height - bar;
                EffectMesh.vertex(b, 0, top, 0, 0, 1);
                EffectMesh.vertex(b, width, top, 0, 0, 1);
                EffectMesh.vertex(b, width, top + bar, 0, 0, 1);
                EffectMesh.vertex(b, 0, top + bar, 0, 0, 1);
            }
            Tessellator.getInstance().draw();
            if (strength <= 0.001) return;
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int segment = 0; segment < 96; segment++) {
                double a = segment * TAU / 96, n = (segment + 1) * TAU / 96;
                screenVertex(b, width, height, a, 0.32, 0x080312, 0);
                screenVertex(b, width, height, n, 0.32, 0x080312, 0);
                screenVertex(b, width, height, n, 1.5, 0x020207, (float) (strength * 0.68));
                screenVertex(b, width, height, a, 1.5, 0x020207, (float) (strength * 0.68));
            }
            Tessellator.getInstance().draw();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (int strand = 0; strand < 40; strand++) {
                double phase = (age * 0.009 + strand * 0.61803398875) % 1;
                double angle = strand * 2.399963 + age * 0.004;
                double radial = 0.12 + phase * 1.3;
                float alpha = (float) (strength * Math.sin(phase * Math.PI) * 0.16);
                for (int segment = 0; segment < 8; segment++) {
                    double t = segment / 8.0;
                    screenVertex(b, width, height, angle + t * strength * 0.4,
                            radial + t * (0.1 + strength * 0.55), strand % 3 == 0 ? GOLD : 0xA9BFFF, alpha * (float) (1 - t));
                    double next = (segment + 1) / 8.0;
                    screenVertex(b, width, height, angle + next * strength * 0.4,
                            radial + next * (0.1 + strength * 0.55), strand % 3 == 0 ? GOLD : 0xA9BFFF, alpha * (float) (1 - next));
                }
            }
            Tessellator.getInstance().draw();
        } finally { GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(activeTexture); }
    }

    private static void screenVertex(BufferBuilder b, double width, double height, double angle,
                                      double radius, int rgb, float alpha) {
        EffectMesh.vertex(b, width * 0.5 + Math.cos(angle) * width * 0.55 * radius,
                height * 0.5 + Math.sin(angle) * height * 0.7 * radius, 0, rgb, alpha);
    }

    private static void untextured() {
        GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND); GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(1.5F);
    }

    private static final class SpaceView extends Entity {
        SpaceView(World world) { super(world); setSize(0.1F, 0.1F); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
        @Override public float getEyeHeight() { return 0; }
    }
}
