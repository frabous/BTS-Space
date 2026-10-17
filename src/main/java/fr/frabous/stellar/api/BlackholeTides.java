package fr.frabous.stellar.api;

/** Horizon growth and tidal lethality shared by server damage and client deformation, in ticks and blocks. */
public final class BlackholeTides {
    public static final int DAMAGE_INTERVAL_TICKS = 10;
    /** The show builds first: ascent, reveal and horizon growth stay harmless until this age. */
    public static final int LETHAL_START_TICKS = 400;
    private BlackholeTides() { }

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    public static double horizonRadius(double age, double radius) {
        return radius * (0.12 + 0.88 * smooth((age - 60) / 380));
    }

    /** 1 deep inside the grown horizon, 0 beyond 1.4 horizon radii from the singularity. */
    public static double lethality(double distance, double horizon) {
        if (horizon <= 0) return 0;
        return 1 - smooth((distance - horizon * 0.8) / (horizon * 0.6));
    }

    /** Ramps over four seconds from LETHAL_START_TICKS and stops with the pull. */
    public static double lethalWindow(double age) {
        if (!LaunchTimeline.blackholePullActive((long) Math.floor(age))) return 0;
        return smooth((age - LETHAL_START_TICKS) / 80);
    }

    /** Combined client/server tidal exposure at one distance from the hole centre. */
    public static double exposure(double age, double distance) {
        return lethalWindow(age) * lethality(distance, horizonRadius(age, AttackParameters.BLACKHOLE_HORIZON_RADIUS));
    }

    /**
     * Damage of one pulse. A flat part shreds small bodies; the max-health part keeps bosses
     * mortal too: full exposure kills 10 HP in about 2 s and 300 HP in about 9 s.
     */
    public static float pulseDamage(double age, double distance, float maxHealth) {
        double tide = exposure(age, distance);
        double perSecond = tide * tide * tide * (4 + 0.1 * Math.max(0, maxHealth));
        return (float) (perSecond * DAMAGE_INTERVAL_TICKS / 20);
    }
}
