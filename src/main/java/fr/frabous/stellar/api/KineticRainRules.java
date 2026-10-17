package fr.frabous.stellar.api;

/**
 * SS-03C walking barrage. Every rod's ground mark and impact tick derive from the launch seed, so the
 * server strikes exactly where and when each client draws the rod, with no extra packets.
 */
public final class KineticRainRules {
    public static final int STATION = 60;
    public static final int BAY_OPEN = 70;
    /** Rods land in salvos of ARMS, one salvo every BEAT ticks from FIRST_IMPACT. */
    public static final int FIRST_IMPACT = 140;
    public static final int ARMS = 4;
    public static final int BEATS = 24;
    public static final int BEAT = 6;
    public static final int COUNT = ARMS * BEATS;
    /** Rail muzzle to ground. */
    public static final int FLIGHT = 36;
    /** Ground designation lead before each rod lands. */
    public static final int MARK = 50;
    public static final int HAMMER_RELEASE = 290;
    public static final int HAMMER_IMPACT = 340;
    public static final int HAMMER_MARK = 100;
    public static final int BAY_CLOSE = 320;
    public static final int SPOOL = 370;
    public static final int DEPART = 394;
    public static final int JUMP = 428;
    public static final int END = 460;
    /** The barrage walks inward along ARMS spiral arms from OUTER_RADIUS to INNER_RADIUS. */
    public static final double OUTER_RADIUS = 176;
    public static final double INNER_RADIUS = 30;
    public static final double TURNS = 1.25;
    public static final double JITTER = 6;
    public static final double ROD_RADIUS = 8;
    public static final float ROD_DAMAGE = 24;
    public static final double HAMMER_RADIUS = 28;
    public static final float HAMMER_DAMAGE = 80;
    /** Share of damage and knockback lost at the rim of a blast. */
    public static final double EDGE = 0.35;
    /** Farthest blast edge from the aim point, for coordinate and border checks. */
    public static final int REACH = (int) Math.ceil(OUTER_RADIUS + JITTER + ROD_RADIUS);
    public static final double MIN_SHIP_Y = 300;
    public static final double SHIP_CLEARANCE = 200;

    private KineticRainRules() { }

    public static double shipY(BlockTarget target) { return Math.max(MIN_SHIP_Y, target.getY() + SHIP_CLEARANCE); }

    /** Destroyer heading in radians. */
    public static double heading(long seed) { return unit(seed, 1) * Math.PI * 2; }

    public static int impact(int rod) { return FIRST_IMPACT + rod / ARMS * BEAT; }

    public static int release(int rod) { return impact(rod) - FLIGHT; }

    /** First rod of the salvo landing at this age, or -1. */
    public static int salvo(long age) {
        long since = age - FIRST_IMPACT;
        if (since < 0 || since % BEAT != 0 || since / BEAT >= BEATS) return -1;
        return (int) (since / BEAT) * ARMS;
    }

    /** Horizontal world position {x, z} where a rod lands. */
    public static double[] mark(LaunchState state, int rod) {
        long seed = state.getSeed();
        double progress = rod / ARMS / (double) (BEATS - 1);
        double spin = unit(seed, 2) < 0.5 ? -1 : 1;
        double angle = unit(seed, 3) * Math.PI * 2 + rod % ARMS * Math.PI * 2 / ARMS
                + spin * TURNS * Math.PI * 2 * progress + (unit(seed, 100 + rod * 2) - 0.5) * 0.1;
        double radius = OUTER_RADIUS + (INNER_RADIUS - OUTER_RADIUS) * progress + (unit(seed, 101 + rod * 2) - 0.5) * 2 * JITTER;
        BlockTarget target = state.getTarget();
        return new double[] {target.getX() + 0.5 + Math.cos(angle) * radius, target.getZ() + 0.5 + Math.sin(angle) * radius};
    }

    /** Fraction of full effect at a horizontal distance from a blast centre; 0 outside the blast. */
    public static double falloff(double distance, double radius) {
        return distance > radius ? 0 : 1 - EDGE * distance / radius;
    }

    /** Deterministic value in [0, 1) for a seed and key (SplitMix64). */
    public static double unit(long seed, int key) {
        long z = seed + 0x9E3779B97F4A7C15L * (key + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
