package fr.frabous.stellar.common;

import fr.frabous.stellar.api.*;
import fr.frabous.stellar.api.network.StellarNetwork;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class LaunchController implements ServerRequestHandler {
    public static final LaunchController INSTANCE = new LaunchController();
    static final String RESTRICTED = "Beyond The Space is restricted to operators.";
    private final Map<Integer, List<ActiveLaunch>> launches = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final BlackholeField blackholes = new BlackholeField();
    private long serverTicks;

    private LaunchController() { }

    public static ItemStack remote(EntityPlayer player) {
        if (player.getHeldItemMainhand().getItem() == StellarRegistry.REMOTE) return player.getHeldItemMainhand();
        if (player.getHeldItemOffhand().getItem() == StellarRegistry.REMOTE) return player.getHeldItemOffhand();
        return ItemStack.EMPTY;
    }

    @Override public void select(EntityPlayerMP sender, SelectionRequest request) {
        ItemStack stack = remote(sender);
        if (stack.isEmpty() || sender.isSpectator() || !sender.isEntityAlive()) return;
        if (!ItemStellarRemote.authorized(sender)) { fail(sender, RESTRICTED); return; }
        ItemStellarRemote.select(stack, request.getAttack());
        StellarNetwork.sendSelection(sender, request.getAttack());
        sender.inventory.markDirty();
        sender.sendStatusMessage(new TextComponentString(request.getAttack().getDisplayId() + " / "
                + request.getAttack().getDisplayName()), true);
    }

    @Override public void launch(EntityPlayerMP sender, LaunchRequest request) {
        if (sender.dimension == request.getDimension() && !remote(sender).isEmpty()
                && ItemStellarRemote.selected(remote(sender)) == request.getAttack()) launchAimed(sender);
    }

    public void launchAimed(EntityPlayerMP player) {
        if (!player.isEntityAlive() || player.isSpectator() || remote(player).isEmpty()) return;
        if (!ItemStellarRemote.authorized(player)) { fail(player, RESTRICTED); return; }
        if (carrierPilot(player)) { fail(player, "Leave the command seat before launching another strike."); return; }
        Long ready = cooldowns.get(player.getUniqueID());
        if (ready != null && ready > serverTicks) { fail(player, "Beyond The Space recharge: " + (ready - serverTicks + 19) / 20 + "s"); return; }
        WorldServer world = player.getServerWorld();
        List<ActiveLaunch> active = launches.computeIfAbsent(player.dimension, id -> new ArrayList<>());
        if (active.size() >= StellarConfig.maxActive) { fail(player, "This dimension already has active stellar strikes."); return; }
        BlockPos hit = aimedBlock(player, StellarConfig.targetRange);
        if (hit == null) { fail(player, "Aim at loaded terrain within " + StellarConfig.targetRange + " blocks."); return; }
        BlockTarget target = new BlockTarget(hit.getX(), hit.getY(), hit.getZ());
        AttackId attack = ItemStellarRemote.selected(remote(player));
        if (attack == AttackId.CARRIER_GUNNERY && (player.isRiding() || player.isElytraFlying())) {
            fail(player, "Dismount and land before boarding the carrier."); return;
        }
        int radius = radius(attack);
        if (Math.abs((long) hit.getX()) + radius > BlockTarget.MAX_HORIZONTAL_COORDINATE
                || Math.abs((long) hit.getZ()) + radius > BlockTarget.MAX_HORIZONTAL_COORDINATE) {
            fail(player, "The strike does not fit inside the world coordinate limits."); return;
        }
        if (!fitsBorder(world, target, radius)) { fail(player, "The full strike must fit inside the world border."); return; }
        BlockTarget deployment = target;
        if (attack == AttackId.CARRIER_GUNNERY) deployment = new BlockTarget(MathHelper.floor(player.posX),
                MathHelper.clamp(MathHelper.floor(player.posY), 0, 255), MathHelper.floor(player.posZ));
        LaunchState state = new LaunchState(UUID.randomUUID(), attack, player.dimension, target, deployment,
                world.rand.nextLong(), world.getTotalWorldTime(), LaunchPhase.MARKING);
        active.add(new ActiveLaunch(state, player));
        cooldowns.put(player.getUniqueID(), serverTicks + StellarConfig.cooldownTicks);
        if (attack.getFamily() == AttackFamily.CARRIER || attack == AttackId.SUPERNOVA || attack == AttackId.GRAVITY_INVERSION)
            StellarNetwork.sendPresentation(player, state);
        StellarNetwork.sendStateToDimension(state);
        if (attack == AttackId.SUPERNOVA) for (EntityPlayer viewer : world.playerEntities)
            viewer.sendMessage(new TextComponentString("SS-04 SUPERNOVA / Global irradiation in 20s: all loaded living entities in this dimension, including the caster. Creative and spectator players are immune. Terrain stays intact."));
        if (attack == AttackId.GRAVITY_INVERSION) for (EntityPlayer viewer : world.playerEntities)
            if (viewer.getDistanceSq(hit) < (GravityInversionRules.RADIUS + 96) * (GravityInversionRules.RADIUS + 96))
                viewer.sendMessage(new TextComponentString("SS-05 GRAVITY INVERSION / Local gravity fails in 4s within "
                        + (int) GravityInversionRules.RADIUS + "m of " + hit.getX() + " " + hit.getY() + " " + hit.getZ()
                        + ". Everyone inside is lifted, including the caster; creative players take no damage, spectators are untouched. Terrain stays intact."));
        world.playSound(null, hit, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 4.0F, 0.6F);
    }

    private static int radius(AttackId attack) {
        switch (attack) {
            case RAILGUN:
            case ASTEROID_PULLER: return AttackParameters.RAILGUN_RADIUS_BLOCKS;
            case SUPERNOVA: return 0; // The aim supplies a visual origin, not a bounded damage cylinder.
            case CARRIER_GUNNERY: return (int) CarrierGunneryRules.BLAST_RADIUS;
            case BLACKHOLE: return AttackParameters.BLACKHOLE_RADIUS_BLOCKS;
            case KINETIC_RAIN: return KineticRainRules.REACH;
            case GRAVITY_INVERSION: return GravityInversionRules.REACH;
            default: throw new IllegalArgumentException("attack");
        }
    }

    private static boolean fitsBorder(World world, BlockTarget target, int radius) {
        return world.getWorldBorder().contains(new BlockPos(target.getX() - radius, 64, target.getZ() - radius))
                && world.getWorldBorder().contains(new BlockPos(target.getX() + radius, 64, target.getZ() + radius));
    }

    public static BlockPos aimedBlock(EntityPlayer player, int range) {
        Vec3d previous = player.getPositionEyes(1.0F), direction = player.getLook(1.0F);
        for (int i = 1; i <= range * 2; i++) {
            Vec3d next = player.getPositionEyes(1.0F).add(direction.scale(i * 0.5));
            BlockPos pos = new BlockPos(next);
            if (pos.getY() < 0 || pos.getY() > 255 || !player.world.isBlockLoaded(pos, false)) return null;
            RayTraceResult result = player.world.rayTraceBlocks(previous, next, false, true, false);
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) return result.getBlockPos();
            previous = next;
        }
        return null;
    }

    private static void fail(EntityPlayerMP player, String message) { player.sendStatusMessage(new TextComponentString(message), true); }

    private boolean carrierPilot(EntityPlayer player) {
        for (List<ActiveLaunch> list : launches.values()) for (ActiveLaunch launch : list)
            if (launch.carrier != null && launch.carrier.owns(player)) return true;
        return false;
    }

    public void controlCarrier(EntityPlayerMP player, fr.frabous.stellar.api.network.CarrierControlMessage command) {
        List<ActiveLaunch> active = launches.get(player.dimension);
        if (active != null) for (ActiveLaunch launch : active)
            if (launch.carrier != null && launch.carrier.owns(player)) launch.carrier.control(command);
    }

    private void releaseCarrier(EntityPlayer player, boolean returnPosition) {
        for (List<ActiveLaunch> list : launches.values()) for (ActiveLaunch launch : list)
            if (launch.carrier != null && launch.carrier.owns(player)) launch.carrier.finish(returnPosition);
    }

    @SubscribeEvent public void clock(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            serverTicks++;
            blackholes.clock(serverTicks);
            cooldowns.entrySet().removeIf(entry -> entry.getValue() <= serverTicks);
        }
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        List<ActiveLaunch> active = launches.get(world.provider.getDimension());
        if (active == null) active = Collections.emptyList();
        List<LaunchState> fields = new ArrayList<>();
        for (ActiveLaunch launch : active) if (launch.state.getAttack() == AttackId.BLACKHOLE) fields.add(launch.state);
        // Gravity capture and the command seat must never own the same player's movement/flight baseline.
        for (ActiveLaunch launch : active) if (launch.carrier != null && launch.carrier.owns(launch.carrier.pilot)
                && BlackholeField.ownerAt(fields, world.getTotalWorldTime(), launch.carrier.pilot.posX, launch.carrier.pilot.posZ) != null)
            launch.carrier.finish(true);
        blackholes.update(world, fields);
        if (active.isEmpty()) return;
        for (ActiveLaunch launch : active) launch.update(world);
        for (ActiveLaunch launch : active) if (launch.inversion != null)
            launch.inversion.update(world, world.getTotalWorldTime() - launch.state.getStartTick(), fields);
        Iterator<ActiveLaunch> it = active.iterator();
        while (it.hasNext()) {
            ActiveLaunch launch = it.next();
            if (world.getTotalWorldTime() - launch.state.getStartTick() >= LaunchTimeline.totalTicks(launch.state.getAttack())) {
                if (launch.inversion != null) launch.inversion.finish();
                StellarNetwork.sendStateToDimension(launch.state.withPhase(LaunchPhase.COMPLETE)); it.remove();
            }
        }
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) CarrierGunnerySession.recover((EntityPlayerMP) event.player, true);
        if (event.player instanceof EntityPlayerMP) blackholes.recoverPlayer((EntityPlayerMP) event.player, true);
        replay(event.player);
    }
    @SubscribeEvent public void joinedWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote) blackholes.queueMountRecovery(event.getEntity());
    }
    @SubscribeEvent public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        releaseCarrier(event.player, false);
        blackholes.release(event.player, false);
        replay(event.player);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { replay(event.player); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { releaseCarrier(event.player, true); blackholes.release(event.player, true); }
    @SubscribeEvent public void clonePlayer(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        releaseCarrier(event.getOriginal(), false);
        if (event.getEntityPlayer() instanceof EntityPlayerMP) CarrierGunnerySession.recover((EntityPlayerMP) event.getEntityPlayer(), false);
        blackholes.release(event.getOriginal(), false);
        if (event.getEntityPlayer() instanceof EntityPlayerMP) blackholes.recoverPlayer((EntityPlayerMP) event.getEntityPlayer(), false);
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) releaseCarrier((EntityPlayer) event.getEntityLiving(), true);
        blackholes.release(event.getEntityLiving(), false);
    }
    @SubscribeEvent public void fall(LivingFallEvent event) {
        if (blackholes.protectFall(event.getEntityLiving()) || GravityInversionField.protectFall(event.getEntityLiving()))
            event.setDamageMultiplier(0);
    }
    private void replay(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        List<ActiveLaunch> active = launches.get(player.dimension);
        if (active != null) for (ActiveLaunch launch : active) StellarNetwork.sendState((EntityPlayerMP) player, launch.state);
        if (!remote(player).isEmpty()) StellarNetwork.sendSelection((EntityPlayerMP) player, ItemStellarRemote.selected(remote(player)));
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) { if (!event.getWorld().isRemote) cancel(event.getWorld()); }
    @SubscribeEvent public void unloadChunk(ChunkEvent.Unload event) {
        if (!event.getWorld().isRemote) blackholes.unloadChunk(event.getWorld(), event.getChunk().x, event.getChunk().z);
    }
    @SubscribeEvent public void savedChunk(ChunkDataEvent.Save event) {
        if (!event.getWorld().isRemote) blackholes.savedChunk(event.getWorld(), event.getData());
    }

    public void cancel(World world) {
        blackholes.cancel(world);
        List<ActiveLaunch> active = launches.remove(world.provider.getDimension());
        if (active != null) for (ActiveLaunch launch : active) {
            if (launch.carrier != null) launch.carrier.finish(true);
            if (launch.inversion != null) launch.inversion.finish();
            StellarNetwork.sendStateToDimension(launch.state.withPhase(LaunchPhase.COMPLETE));
        }
    }

    public String status() {
        int active = 0;
        for (List<ActiveLaunch> list : launches.values()) active += list.size();
        return "Beyond The Space: " + active + " launches. Railgun radius=" + AttackParameters.RAILGUN_RADIUS_BLOCKS
                + ", damage=" + AttackParameters.RAILGUN_DAMAGE_PER_SECOND + "/s. Blackhole radius="
                + AttackParameters.BLACKHOLE_RADIUS_BLOCKS + ", space Y=" + AttackParameters.BLACKHOLE_SPACE_Y
                + ". Asteroid Puller radius=" + AttackParameters.RAILGUN_RADIUS_BLOCKS + ", damage="
                + AttackParameters.RAILGUN_DAMAGE_PER_SECOND + "/s during 4s impact / "
                + LaunchTimeline.totalTicks(AttackId.ASTEROID_PULLER) / 20.0 + "s total. Supernova scope=all loaded living entities in this dimension, damage="
                + AttackParameters.RAILGUN_DAMAGE_PER_SECOND + "/s during 4s irradiation / 32s total. Broadside Command: 30s manual control of "
                + BroadsideBatteries.COUNT + " ventral batteries, " + CarrierGunneryRules.DAMAGE + "/shell on landing, radius="
                + CarrierGunneryRules.BLAST_RADIUS + ". Kinetic Rain: " + KineticRainRules.COUNT + " rods, "
                + KineticRainRules.ROD_DAMAGE + " each, radius=" + KineticRainRules.ROD_RADIUS + ", then the hammer "
                + KineticRainRules.HAMMER_DAMAGE + ", radius=" + KineticRainRules.HAMMER_RADIUS + " / "
                + KineticRainRules.END / 20.0 + "s total. Gravity Inversion radius=" + GravityInversionRules.RADIUS
                + ", " + GravityInversionRules.DAMAGE_PER_SECOND + "/s while lifted, " + GravityInversionRules.COLLAPSE_DAMAGE
                + " on collapse / " + GravityInversionRules.END / 20.0 + "s total. Terrain unchanged.";
    }

    @Override public void serverStopped() {
        for (List<ActiveLaunch> list : launches.values()) for (ActiveLaunch launch : list)
            if (launch.carrier != null) launch.carrier.finish(true);
        blackholes.clear(); launches.clear(); cooldowns.clear(); serverTicks = 0;
    }

    private static final class ActiveLaunch {
        LaunchState state;
        boolean impactPlayed;
        final CarrierGunnerySession carrier;
        final GravityInversionField inversion;

        ActiveLaunch(LaunchState state, EntityPlayerMP owner) {
            this.state = state;
            carrier = state.getAttack() == AttackId.CARRIER_GUNNERY ? new CarrierGunnerySession(owner, state) : null;
            inversion = state.getAttack() == AttackId.GRAVITY_INVERSION ? new GravityInversionField(state) : null;
        }

        void update(WorldServer world) {
            AttackId attack = state.getAttack();
            long age = world.getTotalWorldTime() - state.getStartTick();
            if (carrier != null) carrier.update(world);
            LaunchPhase phase = LaunchTimeline.phaseAt(attack, age);
            if (phase == LaunchPhase.COMPLETE) phase = LaunchPhase.AFTERMATH;
            if (phase != state.getPhase()) { state = state.withPhase(phase); StellarNetwork.sendStateToDimension(state); }
            if (attack == AttackId.SUPERNOVA) {
                if (SupernovaPhysics.damagePulse(age)) strikeDimension(world);
                // Each modded client mixes its own delayed atmospheric sound, including shelter filtering.
                return;
            }
            if (attack == AttackId.KINETIC_RAIN) {
                // Marks and impact ticks come from the shared seed; clients draw the same rods.
                int first = KineticRainRules.salvo(age);
                if (first >= 0) for (int rod = first; rod < first + KineticRainRules.ARMS; rod++) {
                    double[] mark = KineticRainRules.mark(state, rod);
                    blast(world, mark[0], mark[1], KineticRainRules.ROD_RADIUS, KineticRainRules.ROD_DAMAGE, 0.9, 0.45);
                }
                BlockTarget target = state.getTarget();
                if (age == KineticRainRules.HAMMER_IMPACT) blast(world, target.getX() + 0.5, target.getZ() + 0.5,
                        KineticRainRules.HAMMER_RADIUS, KineticRainRules.HAMMER_DAMAGE, 2.2, 0.9);
                return;
            }
            if (attack != AttackId.RAILGUN && attack != AttackId.ASTEROID_PULLER) return;
            long impactAge = age - LaunchTimeline.phaseStartTicks(attack, LaunchPhase.IMPACT);
            if (impactAge < 0 || impactAge >= LaunchTimeline.durationTicks(attack, LaunchPhase.IMPACT)) return;
            BlockTarget target = state.getTarget();
            if (!impactPlayed) {
                impactPlayed = true;
                sound(world, target.getX(), target.getY(), target.getZ(), 0.5F);
            }
            if (impactAge % AttackParameters.RAILGUN_DAMAGE_INTERVAL_TICKS == 0) strikeEntities(world, target);
        }

        private static void sound(WorldServer world, int x, int y, int z, float pitch) {
            world.playSound(null, new BlockPos(x, y, z), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 16, pitch);
            world.playSound(null, new BlockPos(x, y, z), SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.PLAYERS, 16, pitch);
        }

        private static void strikeEntities(WorldServer world, BlockTarget target) {
            int radius = AttackParameters.RAILGUN_RADIUS_BLOCKS;
            double x = target.getX() + 0.5, z = target.getZ() + 0.5;
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class,
                    new AxisAlignedBB(x - radius, AttackParameters.WORLD_MIN_Y, z - radius,
                            x + radius, AttackParameters.RAILGUN_DAMAGE_MAX_Y, z + radius))) {
                if (!entity.isEntityAlive() || !AttackGeometry.railgunContains(target, entity.posX, entity.posY, entity.posZ)) continue;
                damageEntity(entity);
            }
        }

        private static void strikeDimension(WorldServer world) {
            // Snapshot only loaded entities: Forge damage handlers can mutate this list during a pulse.
            // There is no block query, distance/altitude limit, chunk load or traversal of another dimension.
            for (Entity entity : new ArrayList<>(world.loadedEntityList))
                if (entity.world == world && entity instanceof EntityLivingBase && entity.isEntityAlive()
                        // Vanilla CombatTracker reads ladder blocks while applying damage. Do not let a stale
                        // loaded-list entry pull an already unloaded chunk back into memory through that path.
                        && world.getChunkProvider().getLoadedChunk(MathHelper.floor(entity.posX)>>4,MathHelper.floor(entity.posZ)>>4)!=null)
                    damageEntity((EntityLivingBase)entity);
        }

        /** Vertical blast column: full effect at the centre, EDGE less at the rim, thrown outward and up. */
        private static void blast(WorldServer world, double x, double z, double radius, float damage, double push, double lift) {
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class,
                    new AxisAlignedBB(x - radius, AttackParameters.WORLD_MIN_Y, z - radius,
                            x + radius, AttackParameters.RAILGUN_DAMAGE_MAX_Y, z + radius))) {
                double dx = entity.posX - x, dz = entity.posZ - z, distance = Math.sqrt(dx * dx + dz * dz);
                double share = KineticRainRules.falloff(distance, radius);
                if (!entity.isEntityAlive() || share <= 0 || entity.posY < AttackParameters.WORLD_MIN_Y
                        || entity.posY > AttackParameters.RAILGUN_DAMAGE_MAX_Y) continue;
                if (!damageEntity(entity, (float) (damage * share)) || !entity.isEntityAlive()) continue;
                double hold = 1 - entity.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue();
                if (hold <= 0) continue;
                double out = distance < 0.5 ? 0 : push * share * hold / distance;
                entity.addVelocity(dx * out, lift * share * hold, dz * out);
                entity.velocityChanged = true;
            }
        }

        private static void damageEntity(EntityLivingBase entity) { damageEntity(entity, AttackParameters.RAILGUN_DAMAGE_PER_PULSE); }

        /** False when the entity is exempt (creative or spectator). */
        private static boolean damageEntity(EntityLivingBase entity, float amount) {
            if (entity instanceof EntityPlayer && (((EntityPlayer)entity).isCreative() || ((EntityPlayer)entity).isSpectator())) return false;
            // Stellar pulses bypass hurt cooldown; ordinary damage keeps its previous protection.
            int previousImmunity=entity.hurtResistantTime;
            entity.hurtResistantTime=0;
            try { entity.attackEntityFrom(DamageSource.GENERIC,amount); }
            finally { entity.hurtResistantTime=Math.max(previousImmunity,entity.hurtResistantTime); }
            return true;
        }
    }
}
