package fr.frabous.stellar.api;

/** Shared attack dimensions and raw damage at 20 server ticks per second. */
public final class AttackParameters {
    public static final int RAILGUN_RADIUS_BLOCKS = 200;
    public static final int WORLD_MIN_Y = 0;
    public static final int WORLD_MAX_Y = 255;
    public static final int RAILGUN_DAMAGE_MAX_Y = 512;
    public static final int RAILGUN_DAMAGE_INTERVAL_TICKS = 2;
    public static final float RAILGUN_DAMAGE_PER_SECOND = 100.0F;
    public static final float RAILGUN_DAMAGE_PER_PULSE = RAILGUN_DAMAGE_PER_SECOND * RAILGUN_DAMAGE_INTERVAL_TICKS / 20;
    public static final int RAILGUN_COLOR_RGB = 0xFF263D;

    public static final int BLACKHOLE_RADIUS_BLOCKS = 1000;
    public static final int BLACKHOLE_SPACE_Y = 300;
    public static final int BLACKHOLE_CENTER_Y = 420;
    public static final int BLACKHOLE_HORIZON_RADIUS = 70;

    private AttackParameters() { }
}
