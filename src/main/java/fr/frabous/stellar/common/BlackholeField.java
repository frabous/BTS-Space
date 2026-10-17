package fr.frabous.stellar.common;

import fr.frabous.stellar.api.*;
import java.util.*;
import java.lang.reflect.Method;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** One owner per entity, even where several blackholes overlap. Server-thread only. */
final class BlackholeField {
    static final double MAX_SPEED = 3.8; // SPacketEntityVelocity clamps each component at 3.9.
    static final int MAX_LANDING_PROBES = 12;
    static final String PLAYER_RECOVERY = "StellarBlackholeRecovery";
    static final String MOUNT_RECOVERY = "StellarBlackholeMount";
    /** Tidal shear has no attacker and ignores armour, protection enchantments and resistance. */
    static final DamageSource SPAGHETTIFICATION = new DamageSource("stellar.spaghettification")
            .setDamageBypassesArmor().setDamageIsAbsolute();
    private static final class NetworkPosition {
        static final Method CAPTURE = ReflectionHelper.findMethod(NetHandlerPlayServer.class,
                "captureCurrentPosition", "func_184342_d");
    }
    private final Map<Entity, Capture> captures = new IdentityHashMap<>();
    private final Map<EntityLivingBase, Long> landingGuards = new WeakHashMap<>();
    private final Map<Entity, UUID> pendingMounts = new WeakHashMap<>();
    private long ticks;

    void clock(long serverTicks) {
        ticks = serverTicks;
        landingGuards.entrySet().removeIf(entry -> entry.getValue() <= ticks
                || !entry.getKey().isEntityAlive() || entry.getKey().onGround);
    }

    void update(WorldServer world, List<LaunchState> launches) {
        if (launches.isEmpty()) {
            cancel(world);
            if (!pendingMounts.isEmpty() && ticks % 20 == 0) recoverMounts(world, membership(world.loadedEntityList));
            return;
        }
        List<Entity> entities = new ArrayList<>(world.loadedEntityList);
        Set<Entity> loadedEntities = membership(entities);
        Set<Entity> influenced = Collections.newSetFromMap(new IdentityHashMap<>());
        // Iterate already-loaded entities, never a 2000-block chunk/AABB query.
        for (Entity entity : entities) {
            if (!eligible(entity)) continue;
            LaunchState owner = ownerAt(launches, world.getTotalWorldTime(), entity.posX, entity.posZ);
            if (owner == null) continue;
            influenced.add(entity);
            Capture capture = captures.get(entity);
            if (capture == null) {
                capture = new Capture(entity);
                capture.originLanding = landingAt(capture, capture.x, capture.y, capture.z);
                captures.put(entity, capture);
                persistPlayer(capture);
                if (capture.mount != null) rememberMount(entity, capture.mount);
                else queueMountRecovery(entity);
                if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isPlayerSleeping()) {
                    ((EntityPlayer) entity).wakeUpPlayer(false, true, true);
                }
            }
            // A riding MP uses the vehicle anti-fly path, which ignores allowFlying.
            entity.dismountRidingEntity();
            capture.detachedPassengers.removeIf(passenger -> passenger.world != world
                    || !loadedEntities.contains(passenger));
            // Exempt players must not be pulled indirectly by a captured mount either.
            for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
                if (passenger instanceof EntityPlayer && !eligible(passenger)) {
                    if (!capture.detachedPassengers.contains(passenger)) capture.detachedPassengers.add(passenger);
                    rememberMount(passenger, entity);
                    passenger.dismountRidingEntity();
                }
            }
            // Pause generic entity logic (rail constraints, projectile explosions and expiry).
            // MP movement remains client-integrated and server-validated, never update-blocked.
            if (!(entity instanceof EntityPlayerMP)) entity.updateBlocked = true;
            pull(world, entity, capture, owner);
            // A death here releases the capture through LivingDeathEvent before the sweep below.
            if (entity instanceof EntityLivingBase) shred(world, (EntityLivingBase) entity, owner);
        }
        List<Capture> released = new ArrayList<>();
        Iterator<Map.Entry<Entity, Capture>> it = captures.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Capture> entry = it.next();
            Capture capture = entry.getValue();
            if (capture.world != world || influenced.contains(entry.getKey())) continue;
            it.remove();
            restore(capture, true, true, loadedEntities);
            released.add(capture);
        }
        for (Capture capture : released) restoreMount(capture, loadedEntities);
        if (!pendingMounts.isEmpty() && ticks % 20 == 0) recoverMounts(world, loadedEntities);
    }

    private static Set<Entity> membership(Collection<Entity> entities) {
        Set<Entity> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(entities);
        return result;
    }

    /** Nearest active field wins; overlap never stacks velocities or snapshots temporary flags. */
    static LaunchState ownerAt(List<LaunchState> launches, long worldTick, double x, double z) {
        LaunchState owner = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (LaunchState launch : launches) {
            if (launch.getAttack() != AttackId.BLACKHOLE || launch.getPhase() == LaunchPhase.COMPLETE
                    || !LaunchTimeline.blackholePullActive(worldTick - launch.getStartTick())
                    || !AttackGeometry.blackholeContains(launch.getTarget(), x, z)) continue;
            double dx = x - (launch.getTarget().getX() + 0.5);
            double dz = z - (launch.getTarget().getZ() + 0.5);
            double distance = dx * dx + dz * dz;
            if (distance < nearest) { nearest = distance; owner = launch; }
        }
        return owner;
    }

    private static boolean eligible(Entity entity) {
        return entity.isEntityAlive() && (!(entity instanceof EntityPlayer)
                || (!((EntityPlayer) entity).isCreative() && !((EntityPlayer) entity).isSpectator()));
    }

    /** Kepler-inspired eccentric flow, apsidal precession and dissipative disk settling. */
    static Vec3d pullVelocity(BlockTarget target, double x, double y, double z, long orbitTicks, long seed) {
        // Mix adjacent entity IDs so neighbours do not share almost identical trajectories.
        long mixed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        double phase = (mixed & 65535L) * Math.PI * 2 / 65536.0;
        double lane = ((mixed >>> 16) & 65535L) / 65535.0;
        double tilt = ((mixed >>> 32) & 65535L) / 65535.0;
        double time = Math.max(0, Math.min(1, (orbitTicks - 60) / 220.0));
        double settling = time * time * (3 - 2 * time);
        double major = AttackParameters.BLACKHOLE_HORIZON_RADIUS * (1.28 + 0.25 * lane) * (1 - 0.48 * settling);
        double eccentricity = (0.18 + 0.20 * tilt) * (1 - 0.5 * settling);
        double parameter = major * (1 - eccentricity * eccentricity);
        double momentum = Math.sqrt((210 + 35 * lane) * parameter);
        double precession = 0.0018 + 0.0012 * lane;
        double dx = x - target.getX() - 0.5, dz = z - target.getZ() - 0.5;
        double radius = Math.sqrt(dx * dx + dz * dz);
        double angle = radius > 0.001 ? Math.atan2(dz, dx) : phase;
        double nx = Math.cos(angle), nz = Math.sin(angle);
        double anomaly = angle - phase - orbitTicks * precession;
        double denominator = 1 + eccentricity * Math.cos(anomaly);
        double orbitRadius = parameter / denominator;
        // r * v_t stays constant around each ellipse: fast periapsis, slow apoapsis.
        double tangent = Math.min(2.9, momentum / Math.max(30, radius));
        double radial = eccentricity * momentum / parameter * Math.sin(anomaly)
                - precession * parameter * eccentricity * Math.sin(anomaly) / (denominator * denominator)
                - (radius - orbitRadius) * 0.045;
        // Dissipation shrinks each lane smoothly instead of snapping late arrivals into the core.
        radial -= AttackParameters.BLACKHOLE_HORIZON_RADIUS * (1.28 + 0.25 * lane)
                * 0.48 * 6 * time * (1 - time) / 220;
        radial = Math.max(-3.0, Math.min(1.8, radial));
        double inclination = (0.12 + 0.18 * tilt) * (1 - 0.75 * settling);
        double node = phase * 0.7 + orbitTicks * precession * 0.35;
        double height = AttackParameters.BLACKHOLE_CENTER_Y
                + Math.min(180, radius) * inclination * Math.sin(angle - node);
        double planeVelocity = inclination * (tangent * Math.cos(angle - node)
                + radial * Math.sin(angle - node));
        double vertical = Math.max(-2.4, Math.min(2.4, (height - y) * 0.07 + planeVelocity));
        return capped(new Vec3d(nx * radial - nz * tangent, vertical, nz * radial + nx * tangent));
    }

    private static Vec3d capped(Vec3d velocity) {
        double length = velocity.lengthVector();
        return length > MAX_SPEED ? velocity.scale(MAX_SPEED / length) : velocity;
    }

    private void pull(WorldServer world, Entity entity, Capture capture, LaunchState owner) {
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            // Survival/adventure transitions reset capabilities while the player stays eligible.
            // Capture that new baseline before granting our temporary allowance again.
            if (player.interactionManager.getGameType() != capture.gameType) {
                capture.gameType = player.interactionManager.getGameType();
                capture.allowFlying = player.capabilities.allowFlying;
                capture.isFlying = player.capabilities.isFlying;
                persistPlayer(capture);
            }
            if (!player.capabilities.allowFlying || player.capabilities.isFlying) {
                player.capabilities.allowFlying = true;
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
            }
        }
        if (!owner.getLaunchId().equals(capture.orbitOwner)) {
            capture.orbitOwner = owner.getLaunchId();
            capture.orbitTicks = 0;
        }
        double dx = entity.posX - owner.getTarget().getX() - 0.5;
        double dz = entity.posZ - owner.getTarget().getZ() - 0.5;
        if (entity.posY >= AttackParameters.BLACKHOLE_SPACE_Y && dx * dx + dz * dz < 180 * 180) capture.orbitTicks++;
        Vec3d desired = pullVelocity(owner.getTarget(), entity.posX, entity.posY, entity.posZ,
                capture.orbitTicks, owner.getSeed() ^ entity.getEntityId());
        Vec3d velocity = approachVelocity(new Vec3d(entity.motionX, entity.motionY, entity.motionZ), desired);
        if (!loaded(world, entity.getEntityBoundingBox().expand(velocity.x, velocity.y, velocity.z))) {
            // Keep lifting at a loaded frontier, without pushing into the missing neighbour.
            velocity = loaded(world, entity.getEntityBoundingBox()) ? new Vec3d(0, velocity.y, 0) : Vec3d.ZERO;
        }
        entity.fallDistance = 0;
        entity.motionX = velocity.x; entity.motionY = velocity.y; entity.motionZ = velocity.z;
        // Bypass destructive/rail-constrained move overrides, not terrain collision.
        if (!(entity instanceof EntityPlayerMP)) physicalStep(world, entity, velocity, capture);
        entity.fallDistance = 0;
        syncVelocity(entity);
    }

    private static void shred(WorldServer world, EntityLivingBase entity, LaunchState owner) {
        long age = world.getTotalWorldTime() - owner.getStartTick();
        if (age % BlackholeTides.DAMAGE_INTERVAL_TICKS != 0 || !StellarConfig.blackholeLethal || !entity.isEntityAlive()
                || entity instanceof EntityPlayer && !StellarConfig.blackholeLethalPlayers) return;
        double dx = entity.posX - owner.getTarget().getX() - 0.5;
        double dy = entity.posY + entity.height * 0.5 - AttackParameters.BLACKHOLE_CENTER_Y;
        double dz = entity.posZ - owner.getTarget().getZ() - 0.5;
        float damage = BlackholeTides.pulseDamage(age, Math.sqrt(dx * dx + dy * dy + dz * dz), entity.getMaxHealth());
        // CombatTracker reads blocks at the victim: never reload an unloaded chunk through damage.
        if (damage <= 0 || world.getChunkProvider().getLoadedChunk(MathHelper.floor(entity.posX) >> 4,
                MathHelper.floor(entity.posZ) >> 4) == null) return;
        int previousImmunity = entity.hurtResistantTime;
        entity.hurtResistantTime = 0;
        try { entity.attackEntityFrom(SPAGHETTIFICATION, damage); }
        finally { entity.hurtResistantTime = Math.max(previousImmunity, entity.hurtResistantTime); }
    }

    private static void physicalStep(World world, Entity entity, Vec3d velocity, Capture capture) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double x = velocity.x, y = velocity.y, z = velocity.z;
        if (entity instanceof EntityHanging) {
            // Attached entities keep their original chunk/anchor for save/reload safety. They
            // still lift into space and pull inward within that chunk; ordinary entities spiral freely.
            int chunkX = (int) Math.floor(capture.x) >> 4, chunkZ = (int) Math.floor(capture.z) >> 4;
            x = Math.max(chunkX * 16 + 0.001, Math.min(chunkX * 16 + 15.999, entity.posX + x)) - entity.posX;
            z = Math.max(chunkZ * 16 + 0.001, Math.min(chunkZ * 16 + 15.999, entity.posZ + z)) - entity.posZ;
        }
        List<AxisAlignedBB> collisions = world.getCollisionBoxes(entity, box.expand(x, y, z));
        for (AxisAlignedBB collision : collisions) y = collision.calculateYOffset(box, y);
        box = box.offset(0, y, 0);
        for (AxisAlignedBB collision : collisions) x = collision.calculateXOffset(box, x);
        box = box.offset(x, 0, 0);
        for (AxisAlignedBB collision : collisions) z = collision.calculateZOffset(box, z);
        entity.prevPosX = entity.posX; entity.prevPosY = entity.posY; entity.prevPosZ = entity.posZ;
        entity.posX += x; entity.posY += y; entity.posZ += z;
        entity.setEntityBoundingBox(box.offset(0, 0, z));
        entity.onGround = velocity.y < 0 && y != velocity.y;
        world.updateEntityWithOptionalForce(entity, false);
    }

    static Vec3d approachVelocity(Vec3d current, Vec3d desired) {
        current = capped(current);
        Vec3d change = desired.subtract(current);
        double acceleration = change.lengthVector();
        return capped(current.add(acceleration > 0.3 ? change.scale(0.3 / acceleration) : change));
    }

    private static void syncVelocity(Entity entity) {
        entity.velocityChanged = true;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            if (player.connection != null && player.connection.getNetworkManager().isChannelOpen()) {
                player.connection.sendPacket(new SPacketEntityVelocity(player));
            }
        }
    }

    /** Project the loaded-area query to build height; isAreaLoaded rejects Y >= 256. */
    private static boolean loaded(World world, AxisAlignedBB box) {
        return world.isAreaLoaded(new BlockPos(box.minX - 1, 0, box.minZ - 1),
                new BlockPos(box.maxX + 1, 255, box.maxZ + 1), false);
    }

    void release(Entity entity, boolean returnToGround) {
        Capture capture = captures.remove(entity);
        if (capture != null) {
            Set<Entity> loadedEntities = returnToGround ? membership(capture.world.loadedEntityList) : Collections.emptySet();
            restore(capture, returnToGround, true, loadedEntities);
            if (returnToGround) restoreMount(capture, loadedEntities);
            else forgetMount(entity);
        }
    }

    /** Forge serializes this through ForgeData and copies PlayerPersisted during respawn. */
    private static void persistPlayer(Capture capture) {
        if (!(capture.entity instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) capture.entity;
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        NBTTagCompound marker = new NBTTagCompound();
        marker.setDouble("X", capture.x); marker.setDouble("Y", capture.y); marker.setDouble("Z", capture.z);
        marker.setInteger("Dimension", capture.world.provider.getDimension());
        marker.setInteger("GameType", capture.gameType.getID());
        marker.setBoolean("AllowFlying", capture.allowFlying); marker.setBoolean("Flying", capture.isFlying);
        if (capture.originLanding != null) marker.setDouble("GroundY", capture.originLanding.y);
        persisted.setTag(PLAYER_RECOVERY, marker);
    }

    /** Login recovery must work without any surviving launch/capture memory. */
    void recoverPlayer(EntityPlayerMP player, boolean returnToGround) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.hasKey(PLAYER_RECOVERY, 10)) {
            NBTTagCompound marker = persisted.getCompoundTag(PLAYER_RECOVERY);
            Capture capture = new Capture(player, marker);
            restore(capture, returnToGround, marker.getInteger("Dimension") == player.dimension,
                    returnToGround ? membership(player.world.loadedEntityList) : Collections.emptySet());
        }
        if (returnToGround) queueMountRecovery(player);
        else forgetMount(player); // Respawn/dimension departure must not reattach an old-life mount.
    }

    void cancel(World world) {
        if (captures.isEmpty()) return;
        List<Capture> released = new ArrayList<>();
        Iterator<Capture> it = captures.values().iterator();
        while (it.hasNext()) {
            Capture capture = it.next();
            if (capture.world != world) continue;
            it.remove();
            released.add(capture);
        }
        if (released.isEmpty()) return;
        Set<Entity> loadedEntities = membership(world.loadedEntityList);
        for (Capture capture : released) restore(capture, true, true, loadedEntities);
        for (Capture capture : released) restoreMount(capture, loadedEntities);
    }

    /** Restore before ChunkProviderServer saves an unloading chunk, without moving to another chunk. */
    void unloadChunk(World world, int x, int z) {
        if (captures.isEmpty()) return;
        List<Capture> released = new ArrayList<>();
        Iterator<Capture> it = captures.values().iterator();
        while (it.hasNext()) {
            Capture capture = it.next();
            Entity entity = capture.entity;
            if (capture.world != world || entity.chunkCoordX != x || entity.chunkCoordZ != z) continue;
            it.remove();
            released.add(capture);
        }
        if (released.isEmpty()) return;
        Set<Entity> loadedEntities = membership(world.loadedEntityList);
        for (Capture capture : released) restore(capture, true, false, loadedEntities);
        for (Capture capture : released) restoreMount(capture, loadedEntities);
    }

    /** Transient update suspension must never become permanent in an autosave/shutdown save. */
    void savedChunk(World world, NBTTagCompound data) {
        if (captures.isEmpty() || !(world instanceof WorldServer)) return;
        NBTTagList entities = data.getCompoundTag("Level").getTagList("Entities", 10);
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound tag = entities.getCompoundTagAt(i);
            if (!tag.hasUniqueId("UUID")) continue;
            Entity entity = ((WorldServer) world).getEntityFromUuid(tag.getUniqueId("UUID"));
            Capture capture = captures.get(entity);
            if (capture == null) continue;
            tag.setBoolean("UpdateBlocked", capture.updateBlocked);
            // This NBT belongs to the current chunk: never serialize an origin in another chunk.
            Vec3d landing = entity instanceof EntityHanging ? new Vec3d(capture.x, capture.y, capture.z)
                    : landingAt(capture, entity.posX, 256, entity.posZ);
            if (landing != null) tag.setTag("Pos", vectorTag(landing));
            tag.setTag("Motion", vectorTag(Vec3d.ZERO));
            tag.setFloat("FallDistance", 0);
        }
    }

    private static NBTTagList vectorTag(Vec3d vector) {
        NBTTagList tag = new NBTTagList();
        tag.appendTag(new NBTTagDouble(vector.x)); tag.appendTag(new NBTTagDouble(vector.y));
        tag.appendTag(new NBTTagDouble(vector.z));
        return tag;
    }

    void clear() {
        List<Capture> released = new ArrayList<>(captures.values());
        Map<World, Set<Entity>> loadedEntities = new IdentityHashMap<>();
        for (Capture capture : released) loadedEntities.computeIfAbsent(capture.world,
                world -> membership(world.loadedEntityList));
        captures.clear();
        for (Capture capture : released) restore(capture, true, true, loadedEntities.get(capture.world));
        for (Capture capture : released) restoreMount(capture, loadedEntities.get(capture.world));
        landingGuards.clear();
        pendingMounts.clear(); // UUID markers remain on unresolved riders, not in controller memory.
        ticks = 0;
    }

    boolean protectFall(EntityLivingBase entity) {
        if (captures.containsKey(entity)) return true;
        Long expiry = landingGuards.remove(entity);
        return expiry != null && expiry > ticks;
    }

    private void restore(Capture capture, boolean returnToGround, boolean allowOrigin, Set<Entity> loadedEntities) {
        Entity entity = capture.entity;
        // Do not move a dead/respawned/dimension-transferred instance back into its old world.
        if (returnToGround && entity.world == capture.world && eligible(entity)
                && loadedEntities.contains(entity)) {
            Vec3d landing = allowOrigin ? originLanding(capture) : null;
            if (entity instanceof EntityHanging) {
                // The attachment anchor was never changed; restore its exact thin bounding box.
                entity.posX = capture.x; entity.posY = capture.y; entity.posZ = capture.z;
                entity.setEntityBoundingBox(capture.box);
                capture.world.updateEntityWithOptionalForce(entity, false);
                landing = null;
            }
            if (landing == null && !(entity instanceof EntityHanging)) landing = landingAt(capture, entity.posX, 256, entity.posZ);
            if (landing != null && !(entity instanceof EntityHanging)) {
                if (entity instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) entity;
                    // The teleport handshake also prevents stale ascent packets undoing release.
                    player.connection.setPlayerLocation(landing.x, landing.y, landing.z,
                            player.rotationYaw, player.rotationPitch);
                    // An acknowledgement can arrive before the next connection tick. Align both
                    // movement baselines now so the ground packet is not compared to the space position.
                    try { NetworkPosition.CAPTURE.invoke(player.connection); }
                    catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Cannot align blackhole landing position", exception);
                    }
                } else entity.setPositionAndUpdate(landing.x, landing.y, landing.z);
            } else if (entity instanceof EntityLivingBase) {
                // No loaded, clear support (e.g. void): restore flags now, protect only the next
                // landing for at most 60s. Weak keys cannot retain unloaded entities/worlds.
                landingGuards.put((EntityLivingBase) entity, ticks + 1200);
            }
        }
        entity.motionX = 0; entity.motionY = 0; entity.motionZ = 0;
        entity.fallDistance = 0;
        entity.updateBlocked = capture.updateBlocked;
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            // A game-mode change owns the new capability values; never undo creative/spectator.
            if (player.interactionManager.getGameType() == capture.gameType) {
                player.capabilities.allowFlying = capture.allowFlying;
                player.capabilities.isFlying = capture.isFlying;
                player.sendPlayerAbilities();
            }
            player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).removeTag(PLAYER_RECOVERY);
        }
        // Gravity, noClip, invulnerability, potions and terrain were never modified.
        syncVelocity(entity);
    }

    private static Vec3d originLanding(Capture capture) {
        Vec3d cached = capture.originLanding;
        if (cached != null && supported(capture, cached)) return cached;
        capture.originLanding = landingAt(capture, capture.x, capture.y, capture.z);
        return capture.originLanding;
    }

    private static boolean supported(Capture capture, Vec3d position) {
        AxisAlignedBB box = landingBox(capture, position.x, position.y, position.z);
        return loaded(capture.world, box) && capture.world.getWorldBorder().contains(box)
                && capture.world.getCollisionBoxes(capture.entity, box).isEmpty()
                && !capture.world.containsAnyLiquid(box)
                && !capture.world.getCollisionBoxes(capture.entity, box.offset(0, -0.1, 0)).isEmpty();
    }

    private static AxisAlignedBB landingBox(Capture capture, double x, double y, double z) {
        Entity entity = capture.entity;
        return entity.getEntityBoundingBox().offset(x - entity.posX, y - entity.posY, z - entity.posZ);
    }

    private static Vec3d landingAt(Capture capture, double x, double y, double z) {
        if (!loaded(capture.world, landingBox(capture, x, y, z))) return null;
        // A known standing position is cheaper and safer than searching the whole column.
        Vec3d exact = supportAt(capture, x, Math.min(256, y), z);
        if (exact != null) return exact;
        if (!(capture.world instanceof WorldServer)) return null;
        int blockX = (int) Math.floor(x), blockZ = (int) Math.floor(z);
        Chunk chunk = ((WorldServer) capture.world).getChunkProvider().getLoadedChunk(blockX >> 4, blockZ >> 4);
        if (chunk == null) return null;
        // Heightmap is a hint, not proof of support: allow fences/slabs and validate collisions.
        double top = Math.min(Math.min(256, y), chunk.getHeightValue(blockX & 15, blockZ & 15) + 2);
        for (int probe = 0; probe < MAX_LANDING_PROBES && top - probe >= 1; probe++) {
            Vec3d landing = supportAt(capture, x, top - probe, z);
            if (landing != null) return landing;
        }
        return null;
    }

    private static Vec3d supportAt(Capture capture, double x, double height, double z) {
        Entity entity = capture.entity;
        AxisAlignedBB box = landingBox(capture, x, height, z);
        if (!loaded(capture.world, box) || !capture.world.getWorldBorder().contains(box)) return null;
        double supportY = Double.NEGATIVE_INFINITY;
        AxisAlignedBB feet = new AxisAlignedBB(box.minX, height - 1, box.minZ,
                box.maxX, height + 0.1, box.maxZ);
        for (AxisAlignedBB support : capture.world.getCollisionBoxes(entity, feet)) {
            if (support.maxY <= height + 0.1) supportY = Math.max(supportY, support.maxY);
        }
        if (supportY != Double.NEGATIVE_INFINITY) {
            AxisAlignedBB landing = box.offset(0, supportY - height, 0);
            if (capture.world.getCollisionBoxes(entity, landing).isEmpty()
                    && !capture.world.containsAnyLiquid(landing)) return new Vec3d(x, supportY, z);
        }
        return null;
    }

    private void rememberMount(Entity entity, Entity mount) {
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(MOUNT_RECOVERY, 10)) {
            NBTTagCompound marker = new NBTTagCompound();
            marker.setUniqueId("Mount", mount.getUniqueID());
            marker.setInteger("Dimension", entity.world.provider.getDimension());
            data.setTag(MOUNT_RECOVERY, marker);
        }
        queueMountRecovery(entity);
    }

    void queueMountRecovery(Entity entity) {
        NBTTagCompound data = entity.getEntityData();
        if (data.hasKey(MOUNT_RECOVERY, 10)) pendingMounts.put(entity, data.getCompoundTag(MOUNT_RECOVERY).getUniqueId("Mount"));
    }

    private void forgetMount(Entity entity) {
        pendingMounts.remove(entity);
        entity.getEntityData().removeTag(MOUNT_RECOVERY);
    }

    /** Weak keys + UUID-only values cannot retain unloaded riders or worlds. No chunk is loaded. */
    private void recoverMounts(WorldServer world, Set<Entity> loadedEntities) {
        Iterator<Map.Entry<Entity, UUID>> it = pendingMounts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, UUID> entry = it.next();
            Entity entity = entry.getKey();
            if (entity.world != world) continue;
            NBTTagCompound data = entity.getEntityData();
            if (!loadedEntities.contains(entity)) { it.remove(); continue; } // Keep its serialized identity for reload.
            if (!entity.isEntityAlive() || !data.hasKey(MOUNT_RECOVERY, 10)
                    || data.getCompoundTag(MOUNT_RECOVERY).getInteger("Dimension") != world.provider.getDimension()) {
                it.remove(); data.removeTag(MOUNT_RECOVERY); continue;
            }
            Entity mount = world.getEntityFromUuid(entry.getValue());
            if (reattach(entity, mount, world, loadedEntities)) {
                it.remove(); data.removeTag(MOUNT_RECOVERY);
            }
        }
    }

    private void restoreMount(Capture capture, Set<Entity> loadedEntities) {
        finishMount(capture.entity, capture.mount, capture.world, loadedEntities);
        for (Entity passenger : capture.detachedPassengers) finishMount(passenger, capture.entity, capture.world, loadedEntities);
    }

    private void finishMount(Entity entity, Entity fallback, World world, Set<Entity> loadedEntities) {
        NBTTagCompound data = entity.getEntityData();
        Entity mount = fallback;
        if (data.hasKey(MOUNT_RECOVERY, 10) && world instanceof WorldServer) {
            mount = ((WorldServer) world).getEntityFromUuid(data.getCompoundTag(MOUNT_RECOVERY).getUniqueId("Mount"));
        }
        if (reattach(entity, mount, world, loadedEntities)) forgetMount(entity);
    }

    private boolean reattach(Entity entity, Entity mount, World world, Set<Entity> loadedEntities) {
        if (entity.isRiding() && !captures.containsKey(entity)) return true; // Respect a replacement mount.
        if (mount != null && entity.isEntityAlive() && mount.isEntityAlive() && !entity.isRiding()
                && entity.world == world && mount.world == world
                && loadedEntities.contains(entity) && loadedEntities.contains(mount)
                && !captures.containsKey(entity) && !captures.containsKey(mount)
                && entity.getDistanceSq(mount) < 64) return entity.startRiding(mount, true);
        return false;
    }

    private static final class Capture {
        final Entity entity, mount;
        final List<Entity> detachedPassengers = new ArrayList<>();
        final World world;
        final double x, y, z;
        final boolean updateBlocked;
        boolean allowFlying, isFlying;
        GameType gameType;
        final AxisAlignedBB box;
        Vec3d originLanding;
        UUID orbitOwner;
        long orbitTicks;

        Capture(Entity entity) {
            this.entity = entity; world = entity.world; mount = entity.getRidingEntity();
            x = entity.posX; y = entity.posY; z = entity.posZ;
            box = entity.getEntityBoundingBox(); updateBlocked = entity.updateBlocked;
            EntityPlayerMP player = entity instanceof EntityPlayerMP ? (EntityPlayerMP) entity : null;
            allowFlying = player != null && player.capabilities.allowFlying;
            isFlying = player != null && player.capabilities.isFlying;
            gameType = player == null ? null : player.interactionManager.getGameType();
        }

        Capture(EntityPlayerMP player, NBTTagCompound marker) {
            entity = player; world = player.world; mount = null;
            x = marker.getDouble("X"); y = marker.getDouble("Y"); z = marker.getDouble("Z");
            box = player.getEntityBoundingBox(); updateBlocked = player.updateBlocked;
            allowFlying = marker.getBoolean("AllowFlying"); isFlying = marker.getBoolean("Flying");
            gameType = GameType.getByID(marker.getInteger("GameType"));
            if (marker.hasKey("GroundY", 99)) originLanding = new Vec3d(x, marker.getDouble("GroundY"), z);
        }
    }
}
