package fr.frabous.stellar.api;

/** A scaled explanatory model, not a hydrodynamics simulation. Lengths below are illustration units. */
public final class SupernovaPhysics {
    public static final double ENVELOPE_RADIUS = 82;
    public static final double STAR_HEIGHT = 640;
    // Roughly 10,000 km/s ejecta versus 300,000 km/s light. Both use the SAME illustrative clock.
    public static final double LIGHT_TO_EJECTA_SPEED = 30;
    private static final double TRAVEL_TICKS = SupernovaTiming.IMPACT - SupernovaTiming.DETONATE;
    private static final double LIGHT_SPEED = (STAR_HEIGHT - ENVELOPE_RADIUS) / TRAVEL_TICKS;

    private SupernovaPhysics() { }

    /** The later shot accelerates astronomical time, rather than letting gas overtake the light. */
    public static double elapsed(double age) {
        double before = Math.max(0, Math.min(TRAVEL_TICKS, age - SupernovaTiming.DETONATE));
        return before + Math.max(0, age - SupernovaTiming.IMPACT) * (LIGHT_TO_EJECTA_SPEED - 1);
    }

    public static double radiationRadius(double age) { return ENVELOPE_RADIUS + LIGHT_SPEED * elapsed(age); }
    public static double ejectaRadius(double age) { return ENVELOPE_RADIUS + LIGHT_SPEED * elapsed(age) / LIGHT_TO_EJECTA_SPEED; }
    public static double coreRadius(double age) {
        double t = smooth((age - SupernovaTiming.COLLAPSE) / (SupernovaTiming.SHOCK_REVIVAL - SupernovaTiming.COLLAPSE));
        return 19 + (2.8 - 19) * t;
    }
    public static double interiorShockRadius(double age) {
        return 2.8 + (ENVELOPE_RADIUS - 2.8) * smooth((age - SupernovaTiming.SHOCK_REVIVAL)
                / (SupernovaTiming.DETONATE - SupernovaTiming.SHOCK_REVIVAL));
    }
    public static boolean damagePulse(long age) {
        long elapsed = age - SupernovaTiming.IMPACT;
        return elapsed >= 0 && age < SupernovaTiming.AFTERMATH
                && elapsed % AttackParameters.RAILGUN_DAMAGE_INTERVAL_TICKS == 0;
    }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t*t*(3-2*t); }
}
