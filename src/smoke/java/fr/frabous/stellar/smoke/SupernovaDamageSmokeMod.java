package fr.frabous.stellar.smoke;

import com.mojang.authlib.GameProfile;
import fr.frabous.stellar.api.SupernovaPhysics;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Synchronous, server-thread-only production damage probe. Never spawned, tracked or saved as world entities. */
@Mod(modid="stellarsupernovadamagesmoke",name="Stellar Supernova Damage Smoke",version="1",
        acceptableRemoteVersions="*",dependencies="after:stellar")
public final class SupernovaDamageSmokeMod {
    private final Set<EntityLivingBase> owned=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Entity> attached=new ArrayList<>();
    private EntityCow protectedCow;
    private boolean active;
    private boolean damaging;
    private int chunkLoads;

    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        if (Boolean.getBoolean("stellar.smoke.supernova.damage")) event.registerServerCommand(new Relocate());
    }

    /** Only the runner's two explicit console relocations may reset a movement baseline, never a cinematic frame. */
    private static final class Relocate extends CommandBase {
        @Override public String getName() { return "stellar_smoke_relocate"; }
        @Override public String getUsage(ICommandSender sender) { return "/stellar_smoke_relocate origin|observer"; }
        @Override public int getRequiredPermissionLevel() { return 4; }
        @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
            if (sender!=server || args.length!=1 || !("origin".equals(args[0]) || "observer".equals(args[0])))
                throw new CommandException(getUsage(sender));
            EntityPlayerMP player=getPlayer(server,sender,"StellarSmoke");
            boolean observer="observer".equals(args[0]);
            player.connection.setPlayerLocation(observer?4096.5:264.5,4,observer?4096.5:256.5,observer?180:0,observer?-12:90);
            try {
                // 1.12.2 console /tp runs after the network tick. A quick teleport acknowledgement can otherwise
                // compare the new position to firstGoodX/Z from before that tick's administrative relocation.
                Method capture=NetHandlerPlayServer.class.getDeclaredMethod("captureCurrentPosition");
                capture.setAccessible(true);capture.invoke(player.connection);
            } catch (ReflectiveOperationException exception) {
                throw new CommandException("Cannot synchronize the test relocation: "+exception);
            }
            sender.sendMessage(new TextComponentString("Stellar smoke relocation synchronized: "+args[0]));
        }
    }

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.supernova.damage")) return;
        WorldServer world=DimensionManager.getWorld(0),other=DimensionManager.getWorld(-1);
        require(world!=null && other!=null,"Probe requires already initialized overworld and Nether");
        require(world.getMinecraftServer().isCallingFromMinecraftThread(),"Probe is not on the server thread");
        require(world.getMinecraftServer().getPlayerList().getCurrentPlayerCount()==0,"Probe requires an empty disposable server");
        MinecraftForge.EVENT_BUS.register(this);active=true;
        try {
            EntityCow center=cow(world,0,64,0),far=cow(world,8192,64,-8192),high=cow(world,0,900,0);
            EntityCow below=cow(world,0,-64,0),leaving=cow(world,32,64,0),entering=cow(world,16384,80,0);
            EntityCow foreign=cow(other,0,64,0),dead=cow(world,0,64,0);
            EntityCow stale=cow(world,-32768,64,-32768);
            protectedCow=cow(world,0,64,4);dead.setDead();
            attach(center);attach(far);attach(high);attach(below);attach(leaving);attach(protectedCow);attach(dead);attach(foreign);
            attach(stale);
            FakePlayer creative=player(world,"NovaCreative",GameType.CREATIVE);
            FakePlayer spectator=player(world,"NovaSpectator",GameType.SPECTATOR);
            attach(creative);attach(spectator);
            float creativeHealth=creative.getHealth(),spectatorHealth=spectator.getHealth();
            Class<?> owner=Class.forName("fr.frabous.stellar.common.LaunchController$ActiveLaunch");
            Method pulse=owner.getDeclaredMethod("strikeDimension",WorldServer.class);pulse.setAccessible(true);
            // Prepare only the fixtures' chunks in this disposable save, before measuring production code.
            // Vanilla's damage bookkeeping queries ladder blocks; a list-only fixture is not a loaded-world entity.
            for (Entity entity:new Entity[]{center,far,high,below,leaving,entering,protectedCow})
                world.getChunkProvider().provideChunk(net.minecraft.util.math.MathHelper.floor(entity.posX)>>4,
                        net.minecraft.util.math.MathHelper.floor(entity.posZ)>>4);
            require(world.getChunkProvider().getLoadedChunk(-2048,-2048)==null,"Stale fixture chunk is unexpectedly loaded");
            damaging=true;
            int pulses=0;
            for (long age=0;age<=640;age++) if (SupernovaPhysics.damagePulse(age)) {
                if (pulses==20) { world.loadedEntityList.remove(leaving);attach(entering); }
                pulse.invoke(null,world);pulses++;
            }
            require(pulses==40,"Global irradiation pulse count changed");
            health(center,600,"center");health(far,600,"far beyond old 200-block radius");
            health(high,600,"above old Y=512");health(below,600,"below old Y=0");
            health(entering,800,"loaded halfway through");health(leaving,800,"unloaded halfway through");
            health(foreign,1000,"other dimension");health(dead,1000,"dead entity");health(protectedCow,1000,"Forge cancellation");
            health(stale,1000,"stale entity in an unloaded chunk");
            health(creative,creativeHealth,"creative player");health(spectator,spectatorHealth,"spectator player");
            require(center.hurtResistantTime>=100,"Ordinary damage immunity was not restored");
            require(chunkLoads==0,"Global damage forced a chunk load");
            System.out.println("STELLAR_SUPERNOVA_DAMAGE_PASS pulses=40 rawDamage=400 far=true high=true below=true entryExit=true otherDimension=true creativeSpectatorImmune=true forgeCancellation=true immunityRestored=true staleUnloadedSkipped=true chunkLoads=0");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Production global damage probe failed",exception);
        } finally {
            active=false;
            for (Entity entity:attached) entity.world.loadedEntityList.remove(entity);
            attached.clear();owned.clear();MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    private EntityCow cow(WorldServer world,double x,double y,double z) {
        EntityCow cow=new EntityCow(world);cow.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        cow.setHealth(1000);cow.setNoAI(true);cow.setNoGravity(true);cow.setPosition(x,y,z);
        cow.hurtResistantTime=100;owned.add(cow);return cow;
    }

    private FakePlayer player(WorldServer world,String name,GameType mode) {
        FakePlayer player=FakePlayerFactory.get(world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        player.interactionManager.setGameType(mode);player.setHealth(player.getMaxHealth());player.setPosition(0,64,0);
        owned.add(player);return player;
    }

    private void attach(Entity entity) { entity.world.loadedEntityList.add(entity);attached.add(entity); }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void protect(LivingAttackEvent event) {
        // The probe may not damage any pre-existing world entity, even in a disposable save.
        if (active && (!owned.contains(event.getEntityLiving()) || event.getEntityLiving()==protectedCow)) event.setCanceled(true);
    }
    @SubscribeEvent public void chunk(ChunkEvent.Load event) {
        if (active && damaging) {
            chunkLoads++;
            if (chunkLoads==1) new Exception("Unexpected chunk load during the production damage pulse").printStackTrace();
        }
    }

    private static void health(EntityLivingBase entity,float expected,String label) {
        require(Math.abs(entity.getHealth()-expected)<0.001,label+": expected "+expected+", got "+entity.getHealth());
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
