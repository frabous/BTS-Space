package fr.frabous.stellar.common;

import com.mojang.authlib.GameProfile;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchTimeline;
import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Real-server railgun regression checks in the disposable smoke world only. */
@Mod(modid = "stellarrailgunsmoke", name = "Stellar Railgun Smoke Driver", version = "1", acceptableRemoteVersions = "*")
public final class RailgunSmokeMod {
    private long startTick;
    private int stage;
    private boolean active;
    private EntityCow center, edge, outside, entering, leaving, protectedCow;
    private final BlockPos target = new BlockPos(128, 64, 128);

    @Mod.EventHandler public void start(FMLServerStartedEvent event) {
        active = true;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void protect(LivingAttackEvent event) {
        if (event.getEntityLiving() == protectedCow) event.setCanceled(true);
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END) return;
        WorldServer world = DimensionManager.getWorld(0);
        if (stage == 0) {
            world.getChunkProvider().provideChunk(8, 8);
            world.setBlockState(target, Blocks.STONE.getDefaultState());
            world.setBlockState(new BlockPos(128, 0, 128), Blocks.BEDROCK.getDefaultState());
            world.setBlockState(target.down(), Blocks.GOLD_BLOCK.getDefaultState());
            center = cow(world, 128.5, 128.5);
            edge = cow(world, 328.5, 128.5);
            outside = cow(world, 328.6, 128.5);
            entering = cow(world, 400.5, 128.5);
            leaving = cow(world, 128.5, 130.5);
            protectedCow = cow(world, 128.5, 132.5);
            FakePlayer player = FakePlayerFactory.get(world, new GameProfile(
                    UUID.fromString("65ad7d4a-b421-4cb7-84d8-5463ee30c9df"), "RailgunSmoke"));
            player.setLocationAndAngles(128.5, 80, 128.5, 0, 90);
            ItemStack remote = new ItemStack(StellarRegistry.REMOTE);
            remote.setTagCompound(new NBTTagCompound());
            remote.getTagCompound().setInteger("StellarAttack", 4);
            require(ItemStellarRemote.selected(remote) == AttackId.RAILGUN, "Old selection did not migrate to Railgun");
            player.setHeldItem(EnumHand.MAIN_HAND, remote);
            require(target.equals(LaunchController.aimedBlock(player, StellarConfig.targetRange)), "Unexpected aim target");
            LaunchController.INSTANCE.launchAimed(player);
            require(LaunchController.INSTANCE.status().contains("1 launches"), "Railgun was not launched");
            startTick = world.getTotalWorldTime();
            stage = 1;
            return;
        }
        long impactAge = world.getTotalWorldTime() - startTick
                - LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.IMPACT);
        if (impactAge == -1) {
            health(center, 1000, "Damage before impact");
            // Existing hurt cooldown must not reduce the beam's damage rate.
            center.hurtResistantTime = 100;
        }
        if (impactAge == 19) {
            health(center, 900, "Expected 100 damage in the first second");
            health(edge, 900, "Radius edge did not receive 100 damage");
            health(outside, 1000, "Damage escaped the circular radius");
            health(protectedCow, 1000, "Forge damage cancellation was ignored");
            entering.setPositionAndUpdate(128.5, 80, 134.5);
            leaving.setPositionAndUpdate(400.5, 80, 130.5);
            System.out.println("STELLAR_RAILGUN_SMOKE_FIRST_SECOND_PASS");
        }
        if (impactAge == 39) {
            health(center, 800, "Repeated damage was blocked by hurt cooldown");
            health(entering, 900, "Entity entering the beam was not damaged");
            health(leaving, 900, "Entity leaving the beam kept taking damage");
        }
        if (impactAge == 167) {
            health(center, 160, "Full 8.4-second impact must deal 840 raw damage");
            health(outside, 1000, "Outside entity was damaged");
            health(protectedCow, 1000, "Protected entity was damaged");
        }
        if (impactAge == 187) {
            health(center, 160, "Damage continued after beam extinction");
            require(world.getBlockState(target).getBlock() == Blocks.STONE, "Target block changed");
            require(world.getBlockState(target.down()).getBlock() == Blocks.GOLD_BLOCK, "Ground block changed");
            require(world.getBlockState(new BlockPos(128, 0, 128)).getBlock() == Blocks.BEDROCK, "Bedrock changed");
            System.out.println("STELLAR_RAILGUN_SMOKE_DAMAGE_TERRAIN_PASS");
        }
        if (world.getTotalWorldTime() - startTick >= LaunchTimeline.totalTicks(AttackId.RAILGUN) + 2) {
            require(LaunchController.INSTANCE.status().contains("0 launches"), "Completed launch was not removed");
            for (EntityCow entity : new EntityCow[]{center, edge, outside, entering, leaving, protectedCow}) entity.setDead();
            active = false;
            MinecraftForge.EVENT_BUS.unregister(this);
            System.out.println("STELLAR_RAILGUN_SMOKE_PASS");
        }
    }

    private static EntityCow cow(WorldServer world, double x, double z) {
        world.getChunkProvider().provideChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        EntityCow entity = new EntityCow(world);
        entity.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        entity.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        entity.setHealth(1000);
        entity.setNoAI(true);
        entity.setNoGravity(true);
        entity.setPosition(x, 80, z);
        require(world.spawnEntity(entity), "Could not spawn test entity");
        return entity;
    }

    private static void health(EntityCow entity, float expected, String failure) {
        require(Math.abs(entity.getHealth() - expected) < 0.01F, failure + ": " + entity.getHealth());
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }
}
