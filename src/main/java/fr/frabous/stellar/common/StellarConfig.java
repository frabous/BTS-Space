package fr.frabous.stellar.common;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class StellarConfig {
    public static int cooldownTicks;
    public static int maxActive;
    public static int targetRange;
    public static boolean blackholeLethal = true;
    public static boolean blackholeLethalPlayers = true;
    private static Configuration config;

    private StellarConfig() { }

    public static void load(File file) {
        config = new Configuration(file);
        cooldownTicks = config.getInt("cooldownTicks", "gameplay", 600, 20, 72000, "Cooldown in server ticks.");
        maxActive = config.getInt("maxActive", "performance", 2, 1, 8, "Maximum simultaneous launches per dimension.");
        targetRange = config.getInt("targetRange", "gameplay", 256, 16, 512, "Server-authoritative targeting range.");
        blackholeLethal = config.getBoolean("blackholeLethal", "gameplay", true,
                "Blackhole tides spaghettify and kill living entities that reach the horizon (survival players included).");
        blackholeLethalPlayers = config.getBoolean("blackholeLethalPlayers", "gameplay", true,
                "When blackholeLethal is enabled, also kill survival/adventure players. Creative and spectator stay immune.");
        if (config.hasChanged()) config.save();
    }

}
