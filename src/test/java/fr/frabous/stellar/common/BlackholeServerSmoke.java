package fr.frabous.stellar.common;

import com.mojang.authlib.GameProfile;
import fr.frabous.stellar.api.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.*;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;

/** Compile/run separately in a disposable dedicated world, never a production mod. */
@Mod(modid = "stellarblackholesmoke", name = "Stellar Blackhole Server Checks", version = "1", acceptableRemoteVersions = "*")
public final class BlackholeServerSmoke {
    private final BlackholeField field = new BlackholeField();
    private final List<Entity> entities = new ArrayList<>();
    private final BlockTarget target = new BlockTarget(128, 64, 128);
    private LaunchState launch;
    private EntityItemFrame frame;
    private EntityFallingBlock falling;
    private EntityPlayerMP player;
    private RecordingConnection connection;
    private long start;
    private int ticks;
    private int capturedFallTime;
    private boolean running;
    private boolean measuringLandings;
    private int collisionQueries;

    @Mod.EventHandler public void start(FMLServerStartedEvent event) {
        running = true;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void collisions(GetCollisionBoxesEvent event) {
        if (measuringLandings) collisionQueries++;
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (!running || event.phase != TickEvent.Phase.END) return;
        WorldServer world = DimensionManager.getWorld(0);
        try {
            if (launch == null) { setup(world); return; }
            ticks++;
            field.clock(ticks);
            int chunks = world.getChunkProvider().getLoadedChunkCount();
            field.update(world, Collections.singletonList(launch));
            require(world.getChunkProvider().getLoadedChunkCount() == chunks, "Field loaded a chunk");
            for (Entity entity : entities) require(entity.isEntityAlive(), "Fixture died: " + entity.getClass().getSimpleName());
            require(frame.isEntityAlive() && falling.isEntityAlive(), "Generic entity broke/expired");
            if (ticks % 40 == 0) {
                Entity cow = entities.get(0);
                System.out.println("STELLAR_BLACKHOLE_PROGRESS age=" + (world.getTotalWorldTime() - start)
                        + " Y=" + cow.posY + " motion=" + cow.motionY + " blocked=" + cow.updateBlocked
                        + " loaded=" + world.loadedEntityList.contains(cow) + " chunks=" + chunks);
            }
            if (ticks == 1) {
                capturedFallTime = falling.fallTime;
                require(player.capabilities.allowFlying && !player.capabilities.isFlying, "MP anti-fly allowance missing");
                require(connection.velocityPackets > 0 && connection.lastY > 0, "MP authoritative lift packet missing");
                player.interactionManager.setGameType(GameType.ADVENTURE);
                field.update(world, Collections.singletonList(launch));
                require(player.capabilities.allowFlying, "New eligible mode lost anti-fly allowance");
                field.release(player, true);
                world.loadedEntityList.remove(player);
                require(!player.capabilities.allowFlying && !player.capabilities.isFlying, "MP flags leaked on release");
                require(player.interactionManager.getGameType() == GameType.ADVENTURE, "Release changed game mode");
                require(player.motionY == 0 && player.posY < 100, "MP release left a lethal drop");
                player.interactionManager.setGameType(GameType.CREATIVE);
                player.setPosition(132.5, 65.1, 128.5);
                world.loadedEntityList.add(player);
                player.startRiding(entities.get(4), true);
                field.update(world, Collections.singletonList(launch));
                // Vanilla dismount can shift feet a little to find clear ground beside the cart.
                require(!player.isRiding() && player.posY < 75 && player.motionY == 0,
                        "Creative passenger was pulled indirectly: Y=" + player.posY + " motion=" + player.motionY);
                require(player.capabilities.allowFlying, "Creative flight permission changed");
            }
            if (ticks == 160) {
                for (Entity entity : entities) {
                    require(entity.posY >= 300, "Entity did not physically reach space: " + entity.getClass().getSimpleName() + " Y=" + entity.posY);
                    require(entity.updateBlocked, "Generic update was not suspended");
                    require(entity.motionY <= 3.9 && entity.motionY >= -3.9, "Unrepresentable velocity");
                }
                require(falling.fallTime == capturedFallTime, "Falling block expiry advanced during capture");
                require(world.getBlockState(new BlockPos(128, 64, 128)).getBlock() == Blocks.STONE, "Terrain changed");
                require(world.getBlockState(new BlockPos(130, 64, 128)).getBlock() == Blocks.STONE, "Frame support changed");
                NBTTagCompound root = new NBTTagCompound(), level = new NBTTagCompound(), cowTag = new NBTTagCompound();
                entities.get(0).writeToNBT(cowTag);
                NBTTagList savedEntities = new NBTTagList(); savedEntities.appendTag(cowTag);
                level.setTag("Entities", savedEntities); root.setTag("Level", level);
                field.savedChunk(world, root);
                require(!cowTag.getBoolean("UpdateBlocked"), "Transient suspension persisted in autosave");
                require(cowTag.getTagList("Pos", 6).getDoubleAt(1) < 100, "Shutdown save stranded entity in space");
                require(((int) Math.floor(cowTag.getTagList("Pos", 6).getDoubleAt(0)) >> 4) == entities.get(0).chunkCoordX,
                        "Save moved an entity record into the wrong X chunk");
                require(((int) Math.floor(cowTag.getTagList("Pos", 6).getDoubleAt(2)) >> 4) == entities.get(0).chunkCoordZ,
                        "Save moved an entity record into the wrong Z chunk");
            }
            if (ticks == 220) {
                // Original field ends, but a second owner still holds the same capture.
                world.getWorldInfo().setWorldTotalTime(start + 740);
                LaunchState overlap = launch(world.getTotalWorldTime() - 100);
                field.update(world, Arrays.asList(launch, overlap));
                require(entities.get(0).updateBlocked && entities.get(0).posY >= 300, "Overlap restored too early");
                field.update(world, Collections.singletonList(launch));
                restored(world, "normal age740 release");
                require(player.isRiding(), "Exempt passenger mount was not restored");
                player.dismountRidingEntity();
                world.loadedEntityList.remove(player);
                field.update(world, Collections.singletonList(overlap));
                field.cancel(world);
                restored(world, "cancel");
                field.update(world, Collections.singletonList(overlap));
                Entity removed = entities.get(0);
                double removedY = removed.posY;
                world.loadedEntityList.remove(removed);
                field.update(world, Collections.singletonList(overlap));
                require(!removed.updateBlocked && removed.posY == removedY, "Lost reference was repositioned/reinserted");
                world.loadedEntityList.add(removed);
                field.cancel(world);
                field.update(world, Collections.singletonList(overlap));
                Entity cow = entities.get(0);
                field.unloadChunk(world, cow.chunkCoordX, cow.chunkCoordZ);
                require(!cow.updateBlocked && cow.posY < 100, "Chunk unload did not restore before save");
                field.cancel(world);
                field.update(world, Collections.singletonList(overlap));
                field.clear();
                restored(world, "server stop clear");
                playerRecoveryChecks(world);
                mountRecoveryChecks(world);
                massReleaseChecks(world);
                running = false;
                System.out.println("STELLAR_BLACKHOLE_SERVER_SMOKE_PASS");
                world.getMinecraftServer().initiateShutdown();
            }
        } catch (Throwable failure) {
            running = false;
            failure.printStackTrace();
            System.out.println("STELLAR_BLACKHOLE_SERVER_SMOKE_FAIL: " + failure);
            world.getMinecraftServer().initiateShutdown();
        }
    }

    private void setup(WorldServer world) {
        // Fixture loads only: the field must not add any chunks itself.
        ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, loadedWorld) -> { });
        ForgeChunkManager.Ticket fixture = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
        require(fixture != null, "Fixture ticket unavailable");
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) {
            world.getChunkProvider().provideChunk(x, z);
            ForgeChunkManager.forceChunk(fixture, new ChunkPos(x, z));
        }
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("doEntityDrops", "false");
        world.setBlockState(new BlockPos(128, 64, 128), Blocks.STONE.getDefaultState());
        world.setBlockState(new BlockPos(130, 64, 128), Blocks.STONE.getDefaultState());
        world.setBlockState(new BlockPos(132, 64, 128), Blocks.STONE.getDefaultState());
        world.setBlockState(new BlockPos(132, 65, 128), Blocks.RAIL.getDefaultState());
        EntityCow cow = new EntityCow(world); cow.setNoAI(true);
        add(world, cow, 128.5, 65, 128.5);
        EntityArmorStand stand = new EntityArmorStand(world); stand.setNoGravity(true);
        stand.updateBlocked = true;
        add(world, stand, 129.5, 65, 128.5);
        add(world, new EntityItem(world, 128.5, 65, 130.5, new ItemStack(Items.DIAMOND)), 128.5, 65, 130.5);
        falling = new EntityFallingBlock(world, 128.5, 66, 132.5, Blocks.SAND.getDefaultState());
        falling.fallTime = 20;
        add(world, falling, 128.5, 66, 132.5);
        EntityMinecartEmpty cart = new EntityMinecartEmpty(world, 132.5, 65.1, 128.5);
        cart.motionX = 0.2;
        add(world, cart, 132.5, 65.1, 128.5);
        frame = new EntityItemFrame(world, new BlockPos(130, 64, 129), EnumFacing.SOUTH);
        frame.setDisplayedItem(new ItemStack(Items.EMERALD));
        require(world.spawnEntity(frame), "Frame fixture did not spawn"); entities.add(frame);
        player = new EntityPlayerMP(world.getMinecraftServer(), world, new GameProfile(UUID.randomUUID(), "BlackholeSmoke"), new PlayerInteractionManager(world));
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPosition(128.5, 65, 128.5);
        NetworkManager network = new NetworkManager(net.minecraft.network.EnumPacketDirection.SERVERBOUND);
        new EmbeddedChannel(network);
        network.setConnectionState(EnumConnectionState.PLAY);
        connection = new RecordingConnection(world, network, player);
        player.connection = connection;
        world.loadedEntityList.add(player); // Test MP packets without opening a real client session.
        world.getWorldInfo().setWorldTotalTime(1000);
        start = world.getTotalWorldTime() - 60;
        launch = launch(start);
    }

    private LaunchState launch(long start) {
        return new LaunchState(UUID.randomUUID(), AttackId.BLACKHOLE, 0, target, 7, start, LaunchPhase.MARKING);
    }

    private void add(WorldServer world, Entity entity, double x, double y, double z) {
        entity.setPosition(x, y, z);
        require(world.spawnEntity(entity), "Fixture did not spawn"); entities.add(entity);
    }

    private void restored(WorldServer world, String path) {
        for (Entity entity : entities) {
            require(entity.isEntityAlive(), path + " killed an entity");
            require(entity.updateBlocked == (entity == entities.get(1)), path + " did not restore update suspension baseline");
            require(entity.motionY == 0 && entity.fallDistance == 0, path + " leaked fall/velocity");
            require(entity.posY < 100, path + " stranded entity in space");
        }
        require(entities.get(1).hasNoGravity(), path + " changed existing gravity");
        require(frame.getHangingPosition().equals(new BlockPos(130, 64, 129)), path + " changed attachment");
        require(frame.getDisplayedItem().getItem() == Items.EMERALD, path + " lost frame content");
    }

    private void playerRecoveryChecks(WorldServer world) {
        for (int scenario = 0; scenario < 5; scenario++) {
            BlackholeField beforeCrash = new BlackholeField();
            EntityPlayerMP original = testPlayer(world, new GameProfile(UUID.randomUUID(), "Recovery" + scenario));
            original.setPosition(128.5, 65, 128.5);
            original.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
            original.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setString("OtherMod", "keep");
            if (scenario == 1) { original.capabilities.allowFlying = true; original.capabilities.isFlying = true; }
            world.loadedEntityList.add(original);
            LaunchState active = launch(world.getTotalWorldTime() - 60);
            beforeCrash.update(world, Collections.singletonList(active));
            require(original.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .hasKey(BlackholeField.PLAYER_RECOVERY, 10), "Capture recovery marker missing");
            if (scenario == 2) {
                original.interactionManager.setGameType(GameType.ADVENTURE);
                beforeCrash.update(world, Collections.singletonList(active));
            }
            if (scenario == 3) original.interactionManager.setGameType(GameType.CREATIVE); // Crash before the next field tick.
            original.setPosition(128.5, 420, 128.5);
            if (scenario == 4) {
                original.setPosition(161.5, 420, 144.5);
                original.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                        .getCompoundTag(BlackholeField.PLAYER_RECOVERY).setInteger("Dimension", -1);
            }
            // Real compressed playerdata on disk, not an aliased writeToNBT compound.
            world.getSaveHandler().getPlayerNBTManager().writePlayerData(original);
            world.loadedEntityList.remove(original);
            EntityPlayerMP relogged = testPlayer(world, original.getGameProfile());
            NBTTagCompound saved = world.getSaveHandler().getPlayerNBTManager().readPlayerData(relogged);
            require(saved != null && relogged.posY == 420, "Player autosave did not contain the in-flight position");
            require(saved.getCompoundTag("ForgeData").getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .hasKey(BlackholeField.PLAYER_RECOVERY, 10), "PlayerPersisted marker did not survive disk save/read");
            world.loadedEntityList.add(relogged);
            require(LaunchController.INSTANCE.status().contains("0 launches"), "Recovery test unexpectedly has a live launch");
            // The controller has no beforeCrash capture: recovery must use only the persisted marker.
            LaunchController.INSTANCE.login(new PlayerEvent.PlayerLoggedInEvent(relogged));
            require(!relogged.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .hasKey(BlackholeField.PLAYER_RECOVERY), "Recovery marker was not consumed");
            require("keep".equals(relogged.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                    .getString("OtherMod")), "Recovery erased another mod's persisted data");
            require(relogged.motionY == 0 && relogged.fallDistance == 0, "Recovery left residual fall/velocity");
            require(relogged.capabilities.allowFlying == (scenario == 1 || scenario == 3), "Recovery restored the wrong flight baseline");
            require(relogged.capabilities.isFlying == (scenario == 1), "Recovery restored the wrong flying baseline");
            if (scenario == 3) {
                require(relogged.interactionManager.getGameType() == GameType.CREATIVE && relogged.posY == 420,
                        "Recovery undid a legitimate creative mode change");
            } else require(relogged.posY < 100, "Crash/relogin stranded a survival/adventure player in space");
            if (scenario == 2) require(relogged.interactionManager.getGameType() == GameType.ADVENTURE, "Recovery changed adventure mode");
            if (scenario == 4) require(relogged.dimension == 0 && relogged.posX > 160, "Recovery crossed the saved origin dimension");
            world.loadedEntityList.remove(relogged);
            beforeCrash.clear();
        }

        // Exercise Forge's real PlayerPersisted copy and PlayerEvent.Clone hook: no old-life teleport.
        BlackholeField beforeDeath = new BlackholeField();
        EntityPlayerMP original = testPlayer(world, new GameProfile(UUID.randomUUID(), "RecoveryClone"));
        original.setPosition(128.5, 65, 128.5); world.loadedEntityList.add(original);
        beforeDeath.update(world, Collections.singletonList(launch(world.getTotalWorldTime() - 60)));
        EntityPlayerMP clone = testPlayer(world, original.getGameProfile());
        clone.setPosition(144.5, 10, 144.5);
        clone.copyFrom(original, true);
        require(clone.posX == 144.5 && clone.posY == 10, "Clone recovery teleported to the old origin");
        require(!clone.capabilities.allowFlying && !clone.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .hasKey(BlackholeField.PLAYER_RECOVERY), "Clone inherited temporary flight/recovery");
        beforeDeath.release(original, false); world.loadedEntityList.remove(original); beforeDeath.clear();
        System.out.println("STELLAR_BLACKHOLE_PLAYER_DISK_RECOVERY_PASS cases=5 clone=1");
    }

    private EntityPlayerMP testPlayer(WorldServer world, GameProfile profile) {
        EntityPlayerMP result = new EntityPlayerMP(world.getMinecraftServer(), world, profile, new PlayerInteractionManager(world));
        NetworkManager network = new NetworkManager(net.minecraft.network.EnumPacketDirection.SERVERBOUND);
        new EmbeddedChannel(network); network.setConnectionState(EnumConnectionState.PLAY);
        result.connection = new RecordingConnection(world, network, result);
        result.interactionManager.setGameType(GameType.SURVIVAL);
        return result;
    }

    private void mountRecoveryChecks(WorldServer world) {
        BlackholeField mounts = new BlackholeField();
        EntityMinecartEmpty mount = new EntityMinecartEmpty(world, 140.5, 4, 140.5);
        EntityArmorStand rider = new EntityArmorStand(world);
        rider.setPosition(140.5, 4, 140.5);
        require(world.spawnEntity(mount) && world.spawnEntity(rider), "Mount fixtures failed to spawn");
        require(rider.startRiding(mount, true), "Mount fixture could not attach");
        LaunchState active = launch(world.getTotalWorldTime() - 60);
        mounts.update(world, Collections.singletonList(active));
        require(!rider.isRiding(), "Captured rider was not detached");
        mounts.unloadChunk(world, rider.chunkCoordX, rider.chunkCoordZ);
        require(rider.getRidingEntity() == mount, "Same-chunk unload batch did not restore the original mount");
        NBTTagCompound nested = mount.writeToNBT(new NBTTagCompound());
        require(nested.getTagList("Passengers", 10).tagCount() == 1
                && nested.getTagList("Passengers", 10).getCompoundTagAt(0).getUniqueId("UUID").equals(rider.getUniqueID()),
                "Remounted unload did not use vanilla nested passenger serialization");

        mounts.update(world, Collections.singletonList(active));
        mount.setPositionAndUpdate(161.5, 420, 140.5); // Different chunk, original identity must survive rider unload.
        mounts.unloadChunk(world, rider.chunkCoordX, rider.chunkCoordZ);
        require(!rider.isRiding() && rider.getEntityData().hasKey(BlackholeField.MOUNT_RECOVERY, 10),
                "Unloading rider discarded its unresolved original mount identity");
        NBTTagCompound savedRider = rider.writeToNBT(new NBTTagCompound()).copy();
        UUID originalMount = mount.getUniqueID();
        world.removeEntityDangerously(rider);
        EntityArmorStand reloaded = new EntityArmorStand(world);
        reloaded.readFromNBT(savedRider); require(world.spawnEntity(reloaded), "Rider reload failed");
        mounts.queueMountRecovery(reloaded);
        require(savedRider.getCompoundTag("ForgeData").getCompoundTag(BlackholeField.MOUNT_RECOVERY)
                .getUniqueId("Mount").equals(originalMount), "Serialized rider lost mount UUID");
        mounts.clock(20); mounts.update(world, Collections.emptyList());
        require(reloaded.getRidingEntity() == mount && !reloaded.getEntityData().hasKey(BlackholeField.MOUNT_RECOVERY),
                "Reloaded rider did not reattach to the original released mount");
        require(world.getEntityFromUuid(reloaded.getUniqueID()) == reloaded, "Mount recovery duplicated/misindexed the rider");
        mounts.clear(); reloaded.dismountRidingEntity();
        world.removeEntityDangerously(reloaded); world.removeEntityDangerously(mount);
        System.out.println("STELLAR_BLACKHOLE_UNLOAD_MOUNT_RECOVERY_PASS");
    }

    private void massReleaseChecks(WorldServer world) throws ReflectiveOperationException {
        int count = 128;
        BlackholeField mass = new BlackholeField();
        List<EntityArmorStand> fixtures = new ArrayList<>();
        java.lang.reflect.Field listField = net.minecraft.world.World.class.getField("loadedEntityList");
        listField.setAccessible(true);
        List<Entity> original = world.loadedEntityList;
        CountingList counted = new CountingList(original);
        listField.set(world, counted);
        try {
            for (int i = 0; i < count; i++) {
                EntityArmorStand entity = new EntityArmorStand(world);
                entity.setNoGravity(true); entity.setPosition(144.5, 4, 144.5);
                require(world.spawnEntity(entity), "Mass fixture failed to spawn"); fixtures.add(entity);
            }
            mass.update(world, Collections.singletonList(launch(world.getTotalWorldTime() - 60)));
            NBTTagCompound root = new NBTTagCompound(), level = new NBTTagCompound();
            NBTTagList saved = new NBTTagList();
            for (EntityArmorStand entity : fixtures) {
                entity.setPositionAndUpdate(entity.posX, 420, entity.posZ);
                saved.appendTag(entity.writeToNBT(new NBTTagCompound()));
            }
            level.setTag("Entities", saved); root.setTag("Level", level);
            int chunks = world.getChunkProvider().getLoadedChunkCount();
            counted.containsCalls = 0; collisionQueries = 0; measuringLandings = true;
            mass.savedChunk(world, root);
            mass.cancel(world);
            measuringLandings = false;
            require(counted.containsCalls == 0, "Bulk cleanup performed linear loadedEntityList.contains calls: " + counted.containsCalls);
            require(collisionQueries <= (count + entities.size()) * 16,
                    "Bulk save/release exceeded bounded landing work: " + collisionQueries);
            require(world.getChunkProvider().getLoadedChunkCount() == chunks, "Bulk cleanup loaded chunks");
            for (EntityArmorStand entity : fixtures) require(entity.posY < 100 && !entity.updateBlocked, "Mass release failed");
            System.out.println("STELLAR_BLACKHOLE_MASS_WORK_PASS entities=" + count + " listContains="
                    + counted.containsCalls + " collisionQueries=" + collisionQueries);
        } finally {
            measuringLandings = false; mass.clear();
            for (EntityArmorStand entity : fixtures) world.removeEntityDangerously(entity);
            original.clear(); original.addAll(counted); listField.set(world, original);
        }
    }

    private static final class CountingList extends ArrayList<Entity> {
        int containsCalls;
        CountingList(Collection<Entity> entities) { super(entities); }
        @Override public boolean contains(Object entity) { containsCalls++; return super.contains(entity); }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static final class RecordingConnection extends NetHandlerPlayServer {
        int velocityPackets;
        double lastY;
        RecordingConnection(WorldServer world, NetworkManager network, EntityPlayerMP player) {
            super(world.getMinecraftServer(), network, player);
        }
        @Override public void sendPacket(Packet<?> packet) {
            if (packet instanceof SPacketEntityVelocity) {
                velocityPackets++;
                PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                try {
                    packet.writePacketData(buffer);
                    buffer.readVarInt(); buffer.readShort();
                    lastY = buffer.readShort() / 8000.0;
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                } finally { buffer.release(); }
            }
        }
    }
}
