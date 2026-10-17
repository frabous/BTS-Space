package fr.frabous.stellar.common;

import fr.frabous.stellar.api.StellarServices;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonBootstrap {
    public void preInit(FMLPreInitializationEvent event) {
        StellarConfig.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(StellarRegistry.class);
        StellarServices.installServer(LaunchController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(LaunchController.INSTANCE);
    }
    public void init(FMLInitializationEvent event) { }
}
