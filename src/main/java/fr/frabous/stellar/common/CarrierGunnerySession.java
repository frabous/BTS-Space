package fr.frabous.stellar.common;

import fr.frabous.stellar.api.*;
import fr.frabous.stellar.api.network.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Method;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * One owner per gondola seat. The five ventral batteries are server-owned: they traverse toward the
 * pilot's aim at their drive limits, fire once laid, and each shell deals its damage when it lands.
 * No terrain edits, entity spawning or chunk tickets.
 */
final class CarrierGunnerySession {
    private static final String RECOVERY = "StellarCarrierReturn";
    /** Trace step; every chunk a segment can touch is checked before the block query. */
    private static final double SEGMENT = 8;
    private static final class NetworkPosition {
        static final Method CAPTURE = ReflectionHelper.findMethod(NetHandlerPlayServer.class,
                "captureCurrentPosition", "func_184342_d");
    }
    /** An accepted shell: laid and fired at {@code fire}, landing at {@code impact}. */
    private static final class Salvo {
        final int battery;
        final double yaw, pitch;
        final long fire, impact;
        final Vec3d hit;
        Salvo(int battery, double yaw, double pitch, long fire, long impact, Vec3d hit) {
            this.battery = battery; this.yaw = yaw; this.pitch = pitch; this.fire = fire; this.impact = impact; this.hit = hit;
        }
    }
    final EntityPlayerMP pilot;
    private final LaunchState launch;
    private final BroadsideBatteries.Frame frame;
    private final double[][] bearings = new double[BroadsideBatteries.COUNT][];
    private final long[] ready = new long[BroadsideBatteries.COUNT];
    private final int[] barrels = new int[BroadsideBatteries.COUNT];
    private final Salvo[] laying = new Salvo[BroadsideBatteries.COUNT];
    private final List<Salvo> inFlight = new ArrayList<>();
    private boolean boarded, finished, aimDirty;
    private long lastShot = -CarrierGunneryRules.SHOT_INTERVAL, lastAim = -CarrierGunneryRules.AIM_INTERVAL;
    private float aimYaw, aimPitch;
    private int traced;
    private Vec3d aim, broadcastAim;

    CarrierGunnerySession(EntityPlayerMP pilot, LaunchState launch) {
        this.pilot = pilot; this.launch = launch; frame = BroadsideBatteries.frame(launch);
        for (int i = 0; i < bearings.length; i++) bearings[i] = BroadsideBatteries.rest(frame);
    }
    boolean owns(EntityPlayer player) { return !finished && pilot == player; }

    void update(WorldServer world) {
        long now = world.getTotalWorldTime(), age = now - launch.getStartTick();
        if (!finished) seat(world, age);
        if (!finished && boarded && aimDirty && age - lastAim >= CarrierGunneryRules.AIM_INTERVAL) {
            aimDirty = false; lastAim = age;
            aim(trace(world, aimYaw, aimPitch, false));
        }
        // Committed shells complete even after the pilot leaves; the guns then return to rest.
        lay(now);
        land(world, now);
    }

    private void seat(WorldServer world, long age) {
        if (!pilot.isEntityAlive() || pilot.world != world || pilot.connection == null || pilot.isSpectator()
                || age >= CarrierGunneryRules.RETURN) { finish(pilot.world == world); return; }
        if (age < CarrierGunneryRules.BOARD) return;
        if (!boarded) board();
        // Keep the seat server-owned while leaving mouse rotation completely free.
        double x = launch.getDeployment().getX() + 0.5, z = launch.getDeployment().getZ() + 0.5;
        pilot.motionX = pilot.motionY = pilot.motionZ = 0;
        pilot.fallDistance = 0;
        pilot.setNoGravity(true);
        if (pilot.getDistanceSq(x, CarrierGunneryRules.SEAT_Y, z) > 0.0025)
            teleport(pilot, x, CarrierGunneryRules.SEAT_Y, z, pilot.rotationYaw, pilot.rotationPitch);
    }

    private void board() {
        NBTTagCompound marker = new NBTTagCompound();
        marker.setDouble("X", pilot.posX); marker.setDouble("Y", pilot.posY); marker.setDouble("Z", pilot.posZ);
        marker.setFloat("Yaw", pilot.rotationYaw); marker.setFloat("Pitch", pilot.rotationPitch);
        marker.setInteger("Dimension", pilot.dimension);
        marker.setBoolean("Gravity", pilot.hasNoGravity());
        marker.setBoolean("AllowFlying", pilot.capabilities.allowFlying);
        marker.setBoolean("Flying", pilot.capabilities.isFlying);
        marker.setInteger("GameType", pilot.interactionManager.getGameType().getID());
        persisted(pilot).setTag(RECOVERY, marker);
        boarded = true;
        pilot.dismountRidingEntity();
        pilot.setNoGravity(true);
        pilot.capabilities.allowFlying = true; // Vanilla floating-kick exemption, restored on every exit.
        pilot.capabilities.isFlying = true;
        pilot.sendPlayerAbilities();
        double x = launch.getDeployment().getX() + 0.5, z = launch.getDeployment().getZ() + 0.5;
        double dx = launch.getTarget().getX() + 0.5 - x, dz = launch.getTarget().getZ() + 0.5 - z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) Math.toDegrees(Math.atan2(CarrierGunneryRules.SEAT_Y + pilot.getEyeHeight()
                - launch.getTarget().getY() - 1, Math.hypot(dx, dz)));
        teleport(pilot, x, CarrierGunneryRules.SEAT_Y, z, yaw, pitch);
        StellarNetwork.sendPresentation(pilot, launch);
    }

    void control(CarrierControlMessage command) {
        if (finished || !command.launchId.equals(launch.getLaunchId())) return;
        if (command.exit()) { finish(true); return; }
        if (!boarded || pilot.dimension != launch.getDimension() || !pilot.isEntityAlive() || pilot.isSpectator()
                || !CarrierGunneryRules.validAim(command.yaw, command.pitch)) return;
        aimYaw = command.yaw; aimPitch = command.pitch; aimDirty = true;
        WorldServer world = pilot.getServerWorld();
        long now = world.getTotalWorldTime(), age = now - launch.getStartTick();
        if (command.action != CarrierControlMessage.FIRE || !CarrierGunneryRules.canFire(age, lastShot)) return;
        lastShot = age; // A refused shot also consumes one firing cycle; packet spam cannot accelerate the guns.
        Vec3d target = trace(world, command.yaw, command.pitch, true);
        aimDirty = false; lastAim = age;
        aim(target);
        // The free battery that is already laid closest to the target takes it; ties go to the longest idle.
        int battery = -1, slew = Integer.MAX_VALUE;
        double[] bearing = null;
        for (int i = 0; i < BroadsideBatteries.COUNT; i++) {
            if (laying[i] != null || ready[i] > now) continue;
            double[] solution = BroadsideBatteries.solution(frame, i, target.x, target.y, target.z);
            if (solution == null) continue;
            int ticks = BroadsideBatteries.slewTicks(bearings[i], solution[0], solution[1]);
            if (ticks < slew || ticks == slew && ready[i] < ready[battery]) { battery = i; slew = ticks; bearing = solution; }
        }
        if (battery < 0) return;
        long fire = now + Math.max(BroadsideBatteries.LOCK_TICKS, slew);
        int barrel = barrels[battery];
        barrels[battery] ^= 1;
        double[] m = BroadsideBatteries.muzzle(frame, battery, barrel, bearing[0], bearing[1]);
        Vec3d muzzle = new Vec3d(m[0], m[1], m[2]), path = target.subtract(muzzle);
        // The fire-control solution cannot shoot through a hill between the battery and the target.
        Vec3d hit = target;
        int kind = traced;
        int[] obstacle = {CarrierShot.AIR};
        Vec3d blocked = trace(world, muzzle, path.normalize(), path.lengthVector(), obstacle);
        if (obstacle[0] == CarrierShot.TERRAIN && muzzle.distanceTo(blocked) < muzzle.distanceTo(target) - 0.75) {
            hit = blocked; kind = CarrierShot.TERRAIN;
        }
        long impact = fire + BroadsideBatteries.flightTicks(muzzle.distanceTo(hit));
        ready[battery] = fire + BroadsideBatteries.RELOAD_TICKS;
        laying[battery] = new Salvo(battery, bearing[0], bearing[1], fire, impact, hit);
        StellarNetwork.CHANNEL.sendToDimension(new CarrierShotMessage(new CarrierShot(launch.getLaunchId(), pilot.dimension,
                battery, barrel, kind, now, fire, impact, muzzle.x, muzzle.y, muzzle.z, hit.x, hit.y, hit.z)), pilot.dimension);
    }

    /** Traverse every battery one tick toward its laying, the shared aim point, or rest. */
    private void lay(long now) {
        for (int i = 0; i < BroadsideBatteries.COUNT; i++) {
            Salvo salvo = laying[i];
            double[] goal = salvo != null ? new double[] {salvo.yaw, salvo.pitch}
                    : aim == null ? null : BroadsideBatteries.solution(frame, i, aim.x, aim.y, aim.z);
            if (goal == null) goal = BroadsideBatteries.rest(frame);
            BroadsideBatteries.step(bearings[i], goal[0], goal[1]);
            if (salvo != null && now >= salvo.fire) {
                bearings[i][0] = salvo.yaw; bearings[i][1] = salvo.pitch;
                laying[i] = null; inFlight.add(salvo);
            }
        }
    }

    private void land(WorldServer world, long now) {
        for (Iterator<Salvo> it = inFlight.iterator(); it.hasNext(); ) {
            Salvo salvo = it.next();
            if (now < salvo.impact) continue;
            it.remove();
            blast(world, salvo.hit);
        }
    }

    private void blast(WorldServer world, Vec3d end) {
        double radiusSq = CarrierGunneryRules.BLAST_RADIUS * CarrierGunneryRules.BLAST_RADIUS;
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!(entity instanceof EntityLivingBase) || entity == pilot || !entity.isEntityAlive()
                    || !loaded(world, entity.posX, entity.posZ)
                    || entity.getDistanceSq(end.x, end.y, end.z) > radiusSq) continue;
            if (entity instanceof EntityPlayer && (((EntityPlayer) entity).isCreative() || ((EntityPlayer) entity).isSpectator())) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            int immunity = living.hurtResistantTime;
            living.hurtResistantTime = 0;
            try { living.attackEntityFrom(DamageSource.causePlayerDamage(pilot), CarrierGunneryRules.DAMAGE); }
            finally { living.hurtResistantTime = Math.max(immunity, living.hurtResistantTime); }
        }
    }

    private void aim(Vec3d point) {
        aim = point;
        boolean moved = point == null ? broadcastAim != null : broadcastAim == null || broadcastAim.squareDistanceTo(point) > 0.5;
        if (!moved) return;
        broadcastAim = point;
        StellarNetwork.CHANNEL.sendToDimension(point == null ? new CarrierAimMessage(launch.getLaunchId(), false, 0, 0, 0)
                : new CarrierAimMessage(launch.getLaunchId(), true, point.x, point.y, point.z), launch.getDimension());
    }

    /**
     * Pilot's line of sight from the gondola eye; aerial targets can be hit before the terrain. Records
     * in {@link #traced} whether it ended on terrain, on a living entity, or in open air.
     */
    private Vec3d trace(WorldServer world, float yawDegrees, float pitchDegrees, boolean entities) {
        Vec3d eye = new Vec3d(launch.getDeployment().getX() + 0.5,
                CarrierGunneryRules.SEAT_Y + pilot.getEyeHeight(), launch.getDeployment().getZ() + 0.5);
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        Vec3d direction = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        int[] kind = {CarrierShot.AIR};
        Vec3d hit = trace(world, eye, direction, CarrierGunneryRules.RANGE, kind);
        traced = kind[0];
        if (!entities) return hit;
        Vec3d end = hit;
        double nearest = eye.squareDistanceTo(hit);
        // Snapshot iteration tolerates Forge callbacks.
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!(entity instanceof EntityLivingBase) || entity == pilot || !entity.isEntityAlive() || !loaded(world, entity.posX, entity.posZ)) continue;
            RayTraceResult intercept = entity.getEntityBoundingBox().grow(0.25).calculateIntercept(eye, hit);
            if (intercept != null && eye.squareDistanceTo(intercept.hitVec) < nearest) {
                nearest = eye.squareDistanceTo(intercept.hitVec); end = intercept.hitVec; traced = CarrierShot.ENTITY;
            }
        }
        return end;
    }

    /** Stop before the first unloaded chunk; never let rayTraceBlocks fetch an unknown one. */
    static Vec3d trace(WorldServer world, Vec3d from, Vec3d direction, double range, int[] kind) {
        Vec3d previous = from;
        int steps = (int) Math.ceil(range / SEGMENT);
        for (int i = 1; i <= steps; i++) {
            Vec3d next = from.add(direction.scale(Math.min(range, i * SEGMENT)));
            if (!loaded(world, previous, next) || !world.getWorldBorder().contains(new BlockPos(next.x, 64, next.z))) return previous;
            if (next.y < 0) next = previous.add(next.subtract(previous).scale(previous.y / Math.max(1.0E-9, previous.y - next.y)));
            if (previous.y < 256 || next.y < 256) {
                RayTraceResult block = world.rayTraceBlocks(previous, next, false, true, false);
                if (block != null && block.typeOfHit == RayTraceResult.Type.BLOCK) { kind[0] = CarrierShot.TERRAIN; return block.hitVec; }
            }
            if (next.y <= 0) return next;
            previous = next;
        }
        return previous;
    }

    private static boolean loaded(WorldServer world, Vec3d a, Vec3d b) {
        int x0 = MathHelper.floor(Math.min(a.x, b.x)) >> 4, x1 = MathHelper.floor(Math.max(a.x, b.x)) >> 4;
        int z0 = MathHelper.floor(Math.min(a.z, b.z)) >> 4, z1 = MathHelper.floor(Math.max(a.z, b.z)) >> 4;
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++)
            if (world.getChunkProvider().getLoadedChunk(cx, cz) == null) return false;
        return true;
    }

    private static boolean loaded(WorldServer world, double x, double z) {
        return world.getChunkProvider().getLoadedChunk(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4) != null;
    }

    void finish(boolean returnPosition) {
        if (finished) return;
        finished = true;
        aim(null);
        if (boarded) recover(pilot, returnPosition);
        // Private terminal state closes controls immediately; witnesses keep the departure animation.
        if (pilot.connection != null) StellarNetwork.sendPresentation(pilot, launch.withPhase(LaunchPhase.COMPLETE));
    }

    static void recover(EntityPlayerMP player, boolean returnPosition) {
        NBTTagCompound data = persisted(player);
        if (!data.hasKey(RECOVERY, 10)) return;
        NBTTagCompound marker = data.getCompoundTag(RECOVERY);
        player.setNoGravity(marker.getBoolean("Gravity"));
        if (player.interactionManager.getGameType().getID() == marker.getInteger("GameType")) {
            player.capabilities.allowFlying = marker.getBoolean("AllowFlying");
            player.capabilities.isFlying = marker.getBoolean("Flying");
        }
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        if (returnPosition && player.dimension == marker.getInteger("Dimension")) {
            teleport(player, marker.getDouble("X"), marker.getDouble("Y"), marker.getDouble("Z"),
                     marker.getFloat("Yaw"), marker.getFloat("Pitch"));
        }
        player.sendPlayerAbilities();
        data.removeTag(RECOVERY);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) root.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static void teleport(EntityPlayerMP player, double x, double y, double z, float yaw, float pitch) {
        player.connection.setPlayerLocation(x, y, z, yaw, pitch);
        // As on blackhole release, an acknowledgement can precede the next connection tick.
        // Refresh both vanilla movement baselines; never disable its movement checks globally.
        try { NetworkPosition.CAPTURE.invoke(player.connection); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot align command seat position", error); }
    }
}
