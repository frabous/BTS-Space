package fr.frabous.stellar.api;

import static fr.frabous.stellar.api.AsteroidPullerTiming.BEND_END;
import static fr.frabous.stellar.api.AsteroidPullerTiming.IMPACT;
import static fr.frabous.stellar.api.AsteroidPullerTiming.LOCK;

/**
 * Asteroid centre, in blocks relative to the impact anchor (target block centre, 1.1 above it).
 * Free flight is a straight high pass; after the graviton lock two C1 Hermite arcs bend the
 * path back toward the cannon, then accelerate the dive. Velocities are blocks per tick.
 */
public final class AsteroidTrajectory {
    /** Nominal mean radius; the irregular mesh is scaled around it. */
    public static final double RADIUS = 80;
    /** Centre height when the underside meets the anchor at {@link AsteroidPullerTiming#IMPACT}. */
    public static final double CONTACT = 74;
    public static final double CRUISE_SPEED = 15, BEND_SPEED = 8, IMPACT_SPEED = 26;
    private static final double[] LOCK_POINT = {-60, 1110, -486};
    private static final double[] BEND_POINT = {-650, 780, -330};
    private static final double[] IMPACT_POINT = {0, CONTACT, 0};
    private static final double[] CRUISE = scale(normalize(-1, -0.07, 0.12), CRUISE_SPEED);
    private static final double[] DIVE = normalize(IMPACT_POINT[0] - BEND_POINT[0],
            IMPACT_POINT[1] - BEND_POINT[1], IMPACT_POINT[2] - BEND_POINT[2]);
    private static final double[] BEND = scale(DIVE, BEND_SPEED);
    private static final double[] STRIKE = scale(DIVE, IMPACT_SPEED);
    private static final double SPIN_RATE = 0.012, SPIN_DAMPING = 24;

    private AsteroidTrajectory() { }

    public static double[] position(double age) { double[] out = new double[3]; position(age, out); return out; }
    public static double[] velocity(double age) { double[] out = new double[3]; velocity(age, out); return out; }
    public static double x(double age) { return position(age)[0]; }
    public static double y(double age) { return position(age)[1]; }
    public static double z(double age) { return position(age)[2]; }

    public static void position(double age, double[] out) {
        if (age <= LOCK) {
            for (int i = 0; i < 3; i++) out[i] = LOCK_POINT[i] + CRUISE[i] * (age - LOCK);
        } else if (age <= BEND_END) {
            hermite(LOCK_POINT, CRUISE, BEND_POINT, BEND, BEND_END - LOCK, (age - LOCK) / (BEND_END - LOCK), out, false);
        } else if (age <= IMPACT) {
            hermite(BEND_POINT, BEND, IMPACT_POINT, STRIKE, IMPACT - BEND_END, (age - BEND_END) / (IMPACT - BEND_END), out, false);
        } else {
            System.arraycopy(IMPACT_POINT, 0, out, 0, 3);
        }
    }

    public static void velocity(double age, double[] out) {
        if (age <= LOCK) System.arraycopy(CRUISE, 0, out, 0, 3);
        else if (age <= BEND_END)
            hermite(LOCK_POINT, CRUISE, BEND_POINT, BEND, BEND_END - LOCK, (age - LOCK) / (BEND_END - LOCK), out, true);
        else if (age <= IMPACT)
            hermite(BEND_POINT, BEND, IMPACT_POINT, STRIKE, IMPACT - BEND_END, (age - BEND_END) / (IMPACT - BEND_END), out, true);
        else System.arraycopy(STRIKE, 0, out, 0, 3);
    }

    public static double speed(double age) {
        double[] v = velocity(age);
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** Where the graviton harpoon meets the asteroid; the barrel leads its shot onto this point. */
    public static double[] interceptPoint() { return LOCK_POINT.clone(); }

    /** Unit dive direction of the final straight approach. */
    public static double[] diveDirection() { return DIVE.clone(); }

    /** Tumble angle in radians: constant while free, damped by the gravitational lock. */
    public static double spin(double age) {
        if (age <= LOCK) return SPIN_RATE * age;
        return SPIN_RATE * LOCK + SPIN_RATE * SPIN_DAMPING * (1 - Math.exp(-(age - LOCK) / SPIN_DAMPING));
    }

    /** Degrees between the current heading and the free-flight heading. */
    public static double deflection(double age) {
        double[] v = velocity(age);
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1.0E-9) return 0;
        double dot = (v[0] * CRUISE[0] + v[1] * CRUISE[1] + v[2] * CRUISE[2]) / (length * CRUISE_SPEED);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
    }

    /**
     * Ram heating, 0..1: dynamic pressure grows with density (scale height 210 blocks) and with the
     * cube of speed. Normalised so the last frames before contact saturate.
     */
    public static double entryHeat(double age) {
        if (age >= IMPACT) return 1;
        double altitude = Math.max(0, y(age) - CONTACT), speed = speed(age) / IMPACT_SPEED;
        double load = Math.exp(-altitude / 210) * speed * speed * speed;
        double heat = Math.log1p(load * 400) / Math.log1p(400);
        return Math.max(0, Math.min(1, heat));
    }

    /** Faint glow of the high grazing pass, scaled by speed; independent of the dive heating. */
    public static double ionization(double age) {
        double speed = speed(age) / CRUISE_SPEED;
        double appear = smooth((age - AsteroidPullerTiming.APPEAR + 30) / 40);
        return appear * Math.min(1, speed * speed * speed) * 0.35;
    }

    private static void hermite(double[] p0, double[] v0, double[] p1, double[] v1, double duration, double s,
                                double[] out, boolean derivative) {
        double h00, h10, h01, h11;
        if (derivative) {
            h00 = (6 * s * s - 6 * s) / duration; h10 = 3 * s * s - 4 * s + 1;
            h01 = (-6 * s * s + 6 * s) / duration; h11 = 3 * s * s - 2 * s;
        } else {
            h00 = 2 * s * s * s - 3 * s * s + 1; h10 = (s * s * s - 2 * s * s + s) * duration;
            h01 = -2 * s * s * s + 3 * s * s; h11 = (s * s * s - s * s) * duration;
        }
        for (int i = 0; i < 3; i++) out[i] = h00 * p0[i] + h10 * v0[i] + h01 * p1[i] + h11 * v1[i];
    }

    static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    private static double[] scale(double[] v, double factor) {
        return new double[] {v[0] * factor, v[1] * factor, v[2] * factor};
    }
}
