package fr.frabous.stellar.smoke;

import com.mojang.authlib.GameProfile;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.GravityInversionRules;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * SS-05 server probes. At startup, drives fixtures through the production field tick by tick, far from every
 * player. During the connected film, records what really happened to the runner's named cow and item.
 */
@Mod(modid="stellarinversiondamagesmoke",name="Stellar Gravity Inversion Damage Smoke",version="1",
        acceptableRemoteVersions="*",dependencies="after:stellar")
public final class GravityInversionDamageSmokeMod {
    private static final String COW = "SS05Probe", ITEM = "SS05Item";
    private static final double X0 = -12288, Z0 = 12288;
    private final Set<Entity> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private EntityCow protectedCow;
    private boolean active, driving;
    private int chunkLoads;
    // Live record of the connected run.
    private final Map<String, Integer> hurts = new LinkedHashMap<>();
    private double cowStart = Double.NaN, cowTop = Double.NaN, itemStart = Double.NaN, itemTop = Double.NaN;
    private boolean recording;

    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        if (Boolean.getBoolean("stellar.smoke.inversion.damage")) event.registerServerCommand(new Probe());
    }

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.inversion.damage")) return;
        WorldServer world = DimensionManager.getWorld(0);
        MinecraftServer server = world.getMinecraftServer();
        require(server.isCallingFromMinecraftThread(), "Probe is not on the server thread");
        require(server.getPlayerList().getCurrentPlayerCount() == 0, "Probe requires an empty disposable server");
        MinecraftForge.EVENT_BUS.register(this);
        active = true;
        List<Entity> placed = new ArrayList<>();
        Map<Entity, double[]> bystanders = new IdentityHashMap<>();
        double cx = X0 + 0.5, cz = Z0 + 0.5;
        try {
            BlockTarget target = new BlockTarget((int) X0, 3, (int) Z0);
            for (double[] at : new double[][] {{cx, cz}, {cx + 179, cz}, {cx - 6, cz}})
                world.getChunkProvider().provideChunk(MathHelper.floor(at[0]) >> 4, MathHelper.floor(at[1]) >> 4);
            double reach = GravityInversionRules.RADIUS + 2;
            AxisAlignedBB box = new AxisAlignedBB(cx - reach, 0, cz - reach, cx + reach, 256, cz + reach);
            for (Entity entity : world.getEntitiesWithinAABB(Entity.class, box))
                bystanders.put(entity, new double[] {entity.motionX, entity.motionY, entity.motionZ});

            EntityCow center = cow(world, cx + 6, 4, cz), edge = cow(world, cx + 179, 4, cz);
            EntityCow outside = cow(world, cx + 181, 4, cz), high = cow(world, cx + 6, 3 + GravityInversionRules.CEILING + 1, cz + 3);
            protectedCow = cow(world, cx - 6, 4, cz);
            EntityItem item = new EntityItem(world, cx, 4.2, cz + 6, new ItemStack(Blocks.STONE));
            item.setInfinitePickupDelay(); item.motionX = item.motionY = item.motionZ = 0;
            EntityTippedArrow arrow = new EntityTippedArrow(world, cx + 2, 4.5, cz - 4);
            EntityPlayerMP creative = player(world, "InversionCreative", GameType.CREATIVE, cx + 2, cz + 2);
            creative.capabilities.isFlying = true;
            EntityPlayerMP spectator = player(world, "InversionSpectator", GameType.SPECTATOR, cx - 2, cz + 2);
            FakePlayer machine = fake(world, "InversionMachine", GameType.SURVIVAL, cx + 2, cz - 2);
            // The caster rides and takes their own strike like everyone else.
            EntityPlayerMP caster = player(world, "InversionCaster", GameType.SURVIVAL, cx + 3, cz + 3);
            EntityPlayerMP victim = player(world, "InversionVictim", GameType.SURVIVAL, cx - 3, cz - 3);
            for (Entity entity : new Entity[] {center, edge, outside, high, protectedCow, item, arrow,
                    creative, spectator, machine, caster, victim}) { place(world, entity); placed.add(entity); owned.add(entity); }

            LaunchState state = new LaunchState(UUID.randomUUID(), AttackId.GRAVITY_INVERSION, 0, target,
                    0x55AA05L, world.getTotalWorldTime(), LaunchPhase.MARKING);
            Class<?> type = Class.forName("fr.frabous.stellar.common.GravityInversionField");
            Constructor<?> make = type.getDeclaredConstructor(LaunchState.class);
            make.setAccessible(true);
            Object field = make.newInstance(state);
            Method update = type.getDeclaredMethod("update", WorldServer.class, long.class, List.class);
            Method finish = type.getDeclaredMethod("finish"), guard = type.getDeclaredMethod("protectFall", EntityLivingBase.class);
            update.setAccessible(true); finish.setAccessible(true); guard.setAccessible(true);

            driving = true;
            boolean looseEarly = false, livingEarly = false, liftOff = false, slam = false, levitated = false, released = false;
            for (long age = 0; age <= GravityInversionRules.END; age++) {
                update.invoke(field, world, age, Collections.<LaunchState>emptyList());
                if (age == 40) {
                    looseEarly = item.motionY > 0.04 && arrow.motionY > 0.04;
                    livingEarly = center.motionY == 0 && victim.motionY == 0;
                }
                if (age == 150) {
                    liftOff = center.motionY > 0.1 && edge.motionY > 0.1 && victim.motionY > 0.1 && caster.motionY > 0.1
                            && creative.motionY > 0.1 && !creative.capabilities.isFlying && (Boolean) guard.invoke(null, center);
                    levitated = hidden(victim) && hidden(caster) && hidden(creative);
                }
                if (age == GravityInversionRules.SLAM + 2) {
                    slam = center.motionY <= -2.2 && victim.motionY <= -2.2 && caster.motionY <= -2.2;
                    released = victim.getActivePotionEffect(MobEffects.LEVITATION) == null
                            && caster.getActivePotionEffect(MobEffects.LEVITATION) == null;
                }
            }
            finish.invoke(field);
            driving = false;
            require(looseEarly, "Loose bodies did not float during the gravity failure");
            require(livingEarly, "Living bodies lifted before the ascension");
            require(liftOff, "Captured bodies (caster and creative included) did not lift off, kept creative flight or lost their fall guard");
            require(levitated, "Captured players were not carried by hidden levitation");
            require(slam && released, "Collapse did not slam bodies down or kept our levitation");
            float expected = 1000 - 22 * GravityInversionRules.PULSE_DAMAGE - GravityInversionRules.COLLAPSE_DAMAGE;
            health(center, expected, "centre"); health(edge, expected, "179 m edge"); health(victim, expected, "survival player");
            health(caster, expected, "caster");
            health(outside, 1000, "181 m outside"); health(high, 1000, "above the ceiling"); health(protectedCow, 1000, "Forge cancellation");
            health(creative, 1000, "creative"); health(spectator, 1000, "spectator");
            require(outside.motionX == 0 && outside.motionY == 0 && high.motionY == 0, "Bodies outside the field were moved");
            require(spectator.motionY == 0 && machine.motionY == 0, "Spectator or machine player was moved");
            require(machine.getActivePotionEffect(MobEffects.LEVITATION) == null && machine.getHealth() == machine.getMaxHealth(),
                    "Machine player was captured");
            require(chunkLoads == 0, "The field forced a chunk load");
            System.out.println("STELLAR_INVERSION_DAMAGE_PASS pulses=22 pulseDamage=" + GravityInversionRules.PULSE_DAMAGE
                    + " collapse=" + GravityInversionRules.COLLAPSE_DAMAGE + " expectedHealth=" + expected
                    + " edge179=true outside181=true ceiling=true casterHit=true creativeRidesUnharmed=true spectatorMachineExempt=true"
                    + " looseFloatFirst=true levitation=hidden released=true slam=true forgeCancellation=true chunkLoads=0");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Production gravity inversion probe failed", exception);
        } finally {
            active = driving = false;
            for (Entity entity : placed) {
                world.getChunkFromChunkCoords(entity.chunkCoordX, entity.chunkCoordZ).removeEntity(entity);
                if (entity instanceof EntityPlayerMP && !(entity instanceof FakePlayer)) ((EntityPlayerMP) entity).getAdvancements().dispose();
            }
            for (Map.Entry<Entity, double[]> entry : bystanders.entrySet()) {
                entry.getKey().motionX = entry.getValue()[0]; entry.getKey().motionY = entry.getValue()[1]; entry.getKey().motionZ = entry.getValue()[2];
                entry.getKey().getEntityData().removeTag("StellarInversionFall");
            }
            owned.clear();
        }
    }

    private EntityCow cow(WorldServer world, double x, double y, double z) {
        EntityCow cow = new EntityCow(world);
        cow.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        cow.setHealth(1000); cow.setPosition(x, y, z);
        return cow;
    }

    private static FakePlayer fake(WorldServer world, String name, GameType mode, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        player.interactionManager.setGameType(mode);
        player.setHealth(player.getMaxHealth()); player.setPosition(x, 4, z);
        player.motionX = player.motionY = player.motionZ = 0;
        return player;
    }

    /** A real server player object whose connection only queues packets, never connected to any socket. */
    private static EntityPlayerMP player(WorldServer world, String name, GameType mode, double x, double z) {
        MinecraftServer server = world.getMinecraftServer();
        EntityPlayerMP player = new EntityPlayerMP(server, world,
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name), new PlayerInteractionManager(world));
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        player.interactionManager.setGameType(mode);
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, player, 0, "respawnInvulnerabilityTicks", "field_147101_bU");
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000); player.setPosition(x, 4, z);
        return player;
    }

    private static boolean hidden(EntityPlayerMP player) {
        PotionEffect effect = player.getActivePotionEffect(MobEffects.LEVITATION);
        return effect != null && !effect.doesShowParticles();
    }

    private static void place(WorldServer world, Entity entity) {
        Chunk chunk = world.getChunkFromChunkCoords(MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4);
        chunk.addEntity(entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void protect(LivingAttackEvent event) {
        if (active && (!owned.contains(event.getEntityLiving()) || event.getEntityLiving() == protectedCow)) event.setCanceled(true);
    }

    @SubscribeEvent public void chunk(ChunkEvent.Load event) {
        if (active && driving && chunkLoads++ == 0) new Exception("Unexpected chunk load during the field update").printStackTrace();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void hurt(LivingHurtEvent event) {
        if (!recording || !COW.equals(event.getEntityLiving().getCustomNameTag())) return;
        String key = event.getSource().getDamageType() + "@" + event.getAmount();
        hurts.merge(key, 1, Integer::sum);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if (!recording || event.phase != TickEvent.Phase.END || event.world.provider.getDimension() != 0) return;
        for (Entity entity : event.world.loadedEntityList) {
            if (COW.equals(entity.getCustomNameTag())) {
                if (Double.isNaN(cowStart)) cowStart = entity.posY;
                cowTop = Double.isNaN(cowTop) ? entity.posY : Math.max(cowTop, entity.posY);
            } else if (entity instanceof EntityItem && ITEM.equals(entity.getCustomNameTag())) {
                if (Double.isNaN(itemStart)) itemStart = entity.posY;
                itemTop = Double.isNaN(itemTop) ? entity.posY : Math.max(itemTop, entity.posY);
            }
        }
    }

    /** {@code stellar_inversion_probe reset|report}, console only. */
    private final class Probe extends CommandBase {
        @Override public String getName() { return "stellar_inversion_probe"; }
        @Override public String getUsage(ICommandSender sender) { return "/stellar_inversion_probe reset|report|home"; }
        @Override public int getRequiredPermissionLevel() { return 4; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (sender != server || args.length != 1) throw new CommandException(getUsage(sender));
            if ("reset".equals(args[0])) {
                hurts.clear(); cowStart = cowTop = itemStart = itemTop = Double.NaN; recording = true;
                sender.sendMessage(new TextComponentString("STELLAR_INVERSION_PROBE reset"));
                return;
            }
            if ("home".equals(args[0])) {
                // The field throws the test player tens of blocks away. Console /tp runs after the network tick,
                // so its acknowledgement would be checked against the pre-teleport position: resync explicitly.
                EntityPlayerMP player = getPlayer(server, sender, "StellarSmoke");
                player.motionX = player.motionY = player.motionZ = 0; player.fallDistance = 0;
                player.connection.setPlayerLocation(264.5, 4, 256.5, 0, 90);
                try {
                    Method capture = NetHandlerPlayServer.class.getDeclaredMethod("captureCurrentPosition");
                    capture.setAccessible(true); capture.invoke(player.connection);
                } catch (ReflectiveOperationException exception) {
                    throw new CommandException("Cannot synchronize the test relocation: " + exception);
                }
                sender.sendMessage(new TextComponentString("STELLAR_INVERSION_PROBE home"));
                return;
            }
            if (!"report".equals(args[0])) throw new CommandException(getUsage(sender));
            EntityLivingBase cow = null;
            for (Entity entity : DimensionManager.getWorld(0).loadedEntityList)
                if (entity instanceof EntityCow && COW.equals(entity.getCustomNameTag())) cow = (EntityLivingBase) entity;
            StringBuilder line = new StringBuilder("STELLAR_INVERSION_PROBE");
            line.append(" alive=").append(cow != null && cow.isEntityAlive());
            if (cow != null) line.append(" health=").append(cow.getHealth()).append(" onGround=").append(cow.onGround)
                    .append(" y=").append(cow.posY);
            line.append(" cowLift=").append(cowTop - cowStart).append(" itemLift=").append(itemTop - itemStart);
            for (Map.Entry<String, Integer> entry : hurts.entrySet()) line.append(" hurt[").append(entry.getKey()).append("]=").append(entry.getValue());
            sender.sendMessage(new TextComponentString(line.toString()));
        }
    }

    private static void health(EntityLivingBase entity, float expected, String label) {
        require(Math.abs(entity.getHealth() - expected) < 0.001, label + ": expected " + expected + ", got " + entity.getHealth());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
