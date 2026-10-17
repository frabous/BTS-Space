package fr.frabous.stellar;

import fr.frabous.stellar.api.StellarIds;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.common.CommonBootstrap;
import fr.frabous.stellar.common.StellarCommand;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = StellarIds.MOD_ID, name = StellarIds.MOD_NAME, version = StellarIds.VERSION,
     acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:forge@[" + StellarIds.FORGE_VERSION + ",)")
public final class Stellar {
    @SidedProxy(clientSide = "fr.frabous.stellar.client.ClientBootstrap",
                serverSide = "fr.frabous.stellar.common.CommonBootstrap")
    public static CommonBootstrap proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        StellarNetwork.register();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) { proxy.init(event); }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) { event.registerServerCommand(new StellarCommand()); }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) { StellarServices.server().serverStopped(); }
}
