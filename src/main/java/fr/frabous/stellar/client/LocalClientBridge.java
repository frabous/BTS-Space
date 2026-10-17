package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.ClientBridge;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchTimeline;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LocalClientBridge implements ClientBridge {
    final Map<UUID, LaunchState> launches = new LinkedHashMap<>();
    private WorldClient world;
    private long awaitingUntil = -1;
    private AttackId awaitingAttack;
    public static boolean filmsEnabled = true;

    @Override
    public void enqueue(Runnable action) { Minecraft.getMinecraft().addScheduledTask(action); }
    @Override
    public void onSelection(AttackId attack) { }
    @Override
    public void onLaunchState(LaunchState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.world.provider.getDimension() != state.getDimension()) return;
        if (world != mc.world) { reset(); world = mc.world; }
        if (state.getPhase() == LaunchPhase.COMPLETE) {
            CarrierGunneryClient.end(state.getLaunchId());
            launches.remove(state.getLaunchId());
            if (mc.currentScreen instanceof RailgunCinema && ((RailgunCinema) mc.currentScreen).matches(state.getLaunchId()))
                mc.displayGuiScreen(null);
            if (mc.currentScreen instanceof AsteroidPullerCinema && ((AsteroidPullerCinema) mc.currentScreen).matches(state.getLaunchId()))
                mc.displayGuiScreen(null);
            if (mc.currentScreen instanceof SupernovaCinema && ((SupernovaCinema) mc.currentScreen).matches(state.getLaunchId()))
                mc.displayGuiScreen(null);
            if (mc.currentScreen instanceof GravityInversionCinema && ((GravityInversionCinema) mc.currentScreen).matches(state.getLaunchId()))
                mc.displayGuiScreen(null);
            return;
        }
        LaunchState old = launches.get(state.getLaunchId());
        if (old != null && old.getPhase().getWireId() > state.getPhase().getWireId()) return;
        launches.put(state.getLaunchId(), state);
        if (old == null && state.getAttack() == AttackId.RAILGUN && awaitingAttack == state.getAttack()
                && state.getPhase() == LaunchPhase.MARKING && awaitingUntil >= mc.world.getTotalWorldTime()) {
            awaitingUntil = -1;
            if (state.getAttack() == AttackId.RAILGUN && filmsEnabled && mc.currentScreen == null)
                mc.displayGuiScreen(new RailgunCinema(state));
        }
    }
    @Override public void onPresentationState(LaunchState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.world.provider.getDimension() != state.getDimension()) return;
        if (state.getAttack() == AttackId.CARRIER_GUNNERY) {
            if (state.getPhase() == LaunchPhase.COMPLETE) { CarrierGunneryClient.end(state.getLaunchId()); return; }
            onLaunchState(state);
            // This is interactive gameplay, not an optional film, and is owner-addressed by the server.
            stopPresentation();
            CarrierGunneryClient.begin(state);
            return;
        }
        boolean pending = awaitingAttack == state.getAttack() && awaitingUntil >= mc.world.getTotalWorldTime();
        onLaunchState(state);
        if (pending && (state.getAttack() == AttackId.ASTEROID_PULLER || state.getAttack() == AttackId.SUPERNOVA
                || state.getAttack() == AttackId.GRAVITY_INVERSION) && state.getPhase() == LaunchPhase.MARKING) {
            awaitingUntil = -1;
            if (filmsEnabled && mc.currentScreen == null) mc.displayGuiScreen(state.getAttack() == AttackId.SUPERNOVA
                    ? new SupernovaCinema(state) : state.getAttack() == AttackId.GRAVITY_INVERSION
                    ? new GravityInversionCinema(state) : new AsteroidPullerCinema(state));
        }
    }
    @Override
    public void preparePresentation() {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.world) { reset(); world = mc.world; }
        if (mc.world != null) {
            awaitingUntil = mc.world.getTotalWorldTime() + 10;
            awaitingAttack = fr.frabous.stellar.common.ItemStellarRemote.selected(
                    fr.frabous.stellar.common.LaunchController.remote(mc.player));
        }
    }
    @Override
    public void stopPresentation() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof RailgunCinema) mc.displayGuiScreen(null);
        if (mc.currentScreen instanceof AsteroidPullerCinema) mc.displayGuiScreen(null);
        if (mc.currentScreen instanceof SupernovaCinema) mc.displayGuiScreen(null);
        if (mc.currentScreen instanceof GravityInversionCinema) mc.displayGuiScreen(null);
        awaitingUntil = -1;
        awaitingAttack = null;
    }
    @Override
    public void reset() {
        CarrierGunneryClient.reset(); BroadsideFireControl.reset(); BroadsideAudio.reset(); KineticRainAudio.reset();
        launches.clear(); awaitingUntil = -1; world = null; stopPresentation();
    }
    @Override public void onCarrierShot(fr.frabous.stellar.api.CarrierShot shot) {
        Minecraft mc = Minecraft.getMinecraft();
        LaunchState state = launches.get(shot.launchId);
        if (state != null && mc.world != null && mc.world.provider.getDimension() == shot.dimension) BroadsideFireControl.shot(state, shot);
    }
    @Override public void onCarrierAim(UUID launchId, boolean valid, double x, double y, double z) {
        LaunchState state = launches.get(launchId);
        if (state != null) BroadsideFireControl.aim(state, valid, x, y, z);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.world) { reset(); world = mc.world; }
        if (world != null) launches.entrySet().removeIf(entry -> world.getTotalWorldTime() - entry.getValue().getStartTick()
                > LaunchTimeline.totalTicks(entry.getValue().getAttack()) + 200);
        BroadsideFireControl.tick(world, launches.values());
        // Kinetic Rain queues on the Broadside cue list, so it must run first to play due cues this tick.
        KineticRainAudio.tick(world, launches.values());
        BroadsideAudio.tick(world, BroadsideFireControl.fleets());
    }
}
