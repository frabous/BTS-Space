package fr.frabous.stellar.client;

import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.common.CommonBootstrap;
import fr.frabous.stellar.common.StellarRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientBootstrap extends CommonBootstrap {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        CarrierRenderer.prepare();
        AsteroidModel.prepare();
        LocalClientBridge bridge = new LocalClientBridge();
        BlackholeEffects blackhole = new BlackholeEffects(bridge);
        StellarServices.installClient(blackhole);
        MinecraftForge.EVENT_BUS.register(bridge);
        MinecraftForge.EVENT_BUS.register(new SupernovaWitnessEffects(bridge));
        MinecraftForge.EVENT_BUS.register(new GravityInversionWitness(bridge));
        MinecraftForge.EVENT_BUS.register(new GravityInversionRide(bridge));
        MinecraftForge.EVENT_BUS.register(blackhole);
        ClientControls.register();
        MinecraftForge.EVENT_BUS.register(ClientControls.class);
        StellarRegistry.REMOTE.setTileEntityItemStackRenderer(new RemoteItemRenderer());
        MinecraftForge.EVENT_BUS.register(new RemoteFirstPerson());
        MinecraftForge.EVENT_BUS.register(new CarrierGunneryClient());
        MinecraftForge.EVENT_BUS.register(new WorldEffects(bridge, blackhole));
        MinecraftForge.EVENT_BUS.register(new MoonAttractProjection(bridge));
    }
}
