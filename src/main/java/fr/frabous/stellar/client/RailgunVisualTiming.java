package fr.frabous.stellar.client;

/** Client-only reference beats; the authoritative damage timeline remains independent. */
final class RailgunVisualTiming {
    static final double MARK_START = 12;
    static final double LOCK = 22;
    static final double PERIMETER_END = 40;
    /** The designation uplink reaches the cannon's muzzle; the rails answer right after. */
    static final double UPLINK = 195.5;
    static final double DEPLOY_START = 196;
    static final double SPOOL_START = 200;
    static final double CHARGE_START = 236;
    static final double CHARGE_FULL = 250;
    static final double INHALE = 250.5;
    static final double ARC_END = 252;
    static final double FIRE = 254;
    static final double TRANSIT = 262;
    static final double SKY = 274;
    static final double ARRIVAL = 282;
    static final double IMPACT = 286;
    static final double BLOSSOM_END = 294;
    static final double SHRINK = 374;
    static final double RELEASE = 384;
    static final double BEAM_END = 386;
    static final double FINAL_CUT = 454;
    static final double END = 514;
    static final double DESCENT_HEIGHT = 1700;
    private static final double DESCENT_CURVE = 2.2;

    private RailgunVisualTiming() { }

    private static double smooth(double v) { return RailgunCameraPath.smooth(v); }

    private static double clamp(double v) { return RailgunCameraPath.clamp(v); }

    static double marker(double age) {
        return smooth((age - MARK_START) / 2) * (1 - smooth((age - SKY) / (IMPACT - SKY)));
    }

    /** Reticle contraction onto the target; cubic ease-out so the lock lands decisively. */
    static double lock(double age) {
        double t = clamp((age - MARK_START) / (LOCK - MARK_START));
        return 1 - Math.pow(1 - t, 3);
    }

    static double lockFlash(double age) {
        return smooth((age - (LOCK - 0.6)) / 0.6) * (1 - smooth((age - LOCK) / 6));
    }

    /** Fraction of the 200-block danger perimeter already traced by the designator. */
    static double perimeter(double age) {
        return smooth((age - LOCK) / (PERIMETER_END - LOCK));
    }

    /** Rails unfold front-to-rear in a ripple, finished well before the chamber close-up. */
    static double railOpening(double partX, double age) {
        double delay = (58 - Math.max(-65, Math.min(58, partX))) / 124 * 14;
        return smooth((age - DEPLOY_START - delay) / 18);
    }

    /** Slow spool through the reveal, then the decisive charge. */
    static double charge(double age) {
        return 0.32 * smooth((age - SPOOL_START) / (CHARGE_START - SPOOL_START))
                + 0.68 * smooth((age - CHARGE_START) / (CHARGE_FULL - CHARGE_START));
    }

    /** Coil sequencing: cycles per tick climb with the charge, read as accelerating pulses. */
    static double coilPhase(double age) {
        double late = Math.max(0, age - 220);
        return age * 0.02 + late * late * 0.003;
    }

    /** Collar rotation in radians; its rate accelerates up to the inhale. */
    static double collarSpin(double age) {
        double late = Math.max(0, Math.min(age, FIRE) - 210);
        return age * 0.015 + late * late * 0.006;
    }

    static double discharge(double age) {
        return charge(age) * smooth((age - 226) / 10) * (1 - smooth((age - INHALE) / (ARC_END - INHALE)));
    }

    /** Energy drawn into the muzzle just before release; arcs and strips dim meanwhile. */
    static double inhale(double age) {
        return smooth((age - INHALE) / (FIRE - INHALE)) * (1 - smooth((age - FIRE) / 0.6));
    }

    static double firing(double age) {
        return smooth((age - FIRE) / 0.8);
    }

    /** Barrel recoil distance in model units: violent kick, slower hydraulic return. */
    static double recoil(double age) {
        double t = age - FIRE;
        return t <= 0 ? 0 : 5.5 * (1 - Math.exp(-t * 4)) * Math.exp(-t * 0.45);
    }

    /** Height of the returning beam front above the target, accelerating toward the ground. */
    static double headHeight(double age) {
        double t = clamp((age - SKY) / (IMPACT - SKY));
        return DESCENT_HEIGHT * Math.pow(1 - t, DESCENT_CURVE);
    }

    static double ageAtHeight(double height) {
        double h = Math.max(0, Math.min(DESCENT_HEIGHT, height));
        return SKY + (IMPACT - SKY) * (1 - Math.pow(h / DESCENT_HEIGHT, 1 / DESCENT_CURVE));
    }

    /** Column opening after contact, from the punch-through radius to the full hazard radius. */
    static double blossom(double age) {
        double t = clamp((age - IMPACT) / (BLOSSOM_END - IMPACT));
        return 0.22 + 0.78 * (1 - Math.pow(1 - t, 3));
    }

    static double shrink(double age) {
        return smooth((age - SHRINK) / (BEAM_END - SHRINK));
    }

    static double beamAlpha(double age) {
        return age < IMPACT ? 0 : 1 - shrink(age);
    }

    static double radiusScale(double age) {
        return RailgunCameraPath.mix(1, 0.002, shrink(age));
    }

    static double terminalFlash(double age) {
        return smooth((age - 382) / 2) * (1 - smooth((age - 384) / 4));
    }

    static double residue(double age) {
        return smooth((age - 380) / 6) * (1 - smooth((age - 500) / (END - 500)));
    }
}
