package fr.frabous.stellar.api;

/** Shared identifiers. Paths are contracts, not claims that assets already exist. */
public final class StellarIds {
    public static final String MOD_ID = "stellar";
    public static final String MOD_NAME = "Beyond The Space";
    public static final String VERSION = "1.1.0";
    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String FORGE_VERSION = "14.23.5.2859";
    public static final int PROTOCOL_VERSION = 1;
    public static final String NETWORK_CHANNEL = "stellar_v1";
    public static final String REMOTE = "stellar_remote";
    public static final String NEEDLE = "gungnir_needle";
    public static final String STAR_CORE = "star_core";
    public static final String REMOTE_TEXTURE = "textures/items/stellar_remote.png";
    public static final String MENU_TEXTURE = "textures/gui/skill_menu.png";

    private StellarIds() { }

    public static String resource(String path) {
        return MOD_ID + ":" + path;
    }

    public static String sound(AttackId attack, LaunchPhase phase) {
        return resource(attack.getResourcePath() + "." + phase.name().toLowerCase(java.util.Locale.ROOT));
    }
}
