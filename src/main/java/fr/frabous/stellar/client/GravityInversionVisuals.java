package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;

/** SS-05 film grammar: camera path, flashes, impact frame cels and title cards. Pure math, no GL. */
final class GravityInversionVisuals {
    static final int MAGENTA = 0xFF5AD9, CYAN = 0x8FE9FF, WHITE = 0xF6F0FF, VOID = 0x12041A;
    /** Cuts of the caster's film. */
    static final double[] CUTS = {0, 40, ASCENSION, 140, ROTATION, COLLAPSE, SLAM, IMPACT, SHOCK};

    private GravityInversionVisuals() { }

    /**
     * Camera pose relative to the aim point (+1.1 Y). heading points from the aim towards the caster, where
     * terrain is loaded; ground is the terrain height under the aim relative to the same point.
     */
    static RailgunCameraPath.Pose camera(long seed, double age, double heading, double[] rider) {
        if (rider != null) {
            RailgunCameraPath.Pose shot = riderShot(age, heading, rider);
            if (shot != null) return shot;
        }
        double cx, cy, cz, tx = 0, ty = 0, tz = 0, roll = 0, fov;
        if (age < 40) {
            double p = smooth(age / 40), a = heading + 0.25;
            double d = mix(28, 19, p);
            cx = Math.cos(a) * d; cz = Math.sin(a) * d; cy = 2.4;
            ty = mix(-0.5, 1.5, p); fov = mix(72, 66, p);
        } else if (age < ASCENSION) {
            double p = smooth((age - 40) / 40), a = heading + 0.9;
            // Keep the cracked ground in the lower half while the pebbles climb out of frame.
            cx = Math.cos(a) * 11; cz = Math.sin(a) * 11; cy = 1.4;
            tx = Math.cos(a) * 2; tz = Math.sin(a) * 2; ty = mix(0, 3.5, p); fov = mix(60, 54, p);
        } else if (age < 140) {
            double p = smooth((age - ASCENSION) / 60), a = heading + 0.35 + p * 0.2;
            double d = mix(46, 64, p);
            cx = Math.cos(a) * d; cz = Math.sin(a) * d; cy = mix(3, 42, p);
            ty = mix(6, 26, p); fov = mix(62, 70, p);
        } else if (age < ROTATION) {
            double p = (age - 140) / 60, a = heading + 0.55 + p * 0.45;
            cx = Math.cos(a) * 118; cz = Math.sin(a) * 118; cy = mix(48, 60, smooth(p));
            ty = 30; fov = 72;
        } else if (age < COLLAPSE) {
            double t = age - ROTATION, a = heading + 1.0 + t * 0.012 + orbit(age) * 0.18;
            double d = 92 + 10 * Math.sin(t * 0.05);
            cx = Math.cos(a) * d; cz = Math.sin(a) * d; cy = 46 + 8 * Math.sin(t * 0.04);
            ty = AXIS_HEIGHT * 0.8; fov = 70;
            int k = flip(age);
            double into = t - k * FLIP, swing = smooth(into / 5);
            double from = k == 0 ? 0 : (k % 2 == 1 ? 14 : -14), to = k % 2 == 0 ? 14 : -14;
            roll = mix(from, to, swing);
            double kick = Math.exp(-into * 0.3);
            fov += 12 * kick;
            cx += Math.sin(age * 2.3) * 1.6 * kick; cy += Math.sin(age * 2.9) * 1.6 * kick;
            roll += Math.sin(age * 1.7) * 3 * kick;
        } else if (age < SLAM) {
            // Time stops: low under the hanging field, a slow push looking straight up.
            double p = (age - COLLAPSE) / (SLAM - COLLAPSE), a = heading + 2.1;
            double d = mix(34, 31, p);
            cx = Math.cos(a) * d; cz = Math.sin(a) * d; cy = 3;
            tx = Math.cos(a) * 8; tz = Math.sin(a) * 8; ty = 48; fov = mix(48, 46, p); roll = -4;
        } else if (age < IMPACT) {
            double p = (age - SLAM) / (IMPACT - SLAM), a = heading + 2.1;
            cx = Math.cos(a) * 31; cz = Math.sin(a) * 31; cy = 3 + 6 * p * p;
            tx = Math.cos(a) * 8 * (1 - p); tz = Math.sin(a) * 8 * (1 - p); ty = mix(48, 0, p * p);
            fov = mix(46, 100, p * p); roll = mix(-4, 8, p);
            cx += Math.sin(age * 3.1) * p * 0.8; cy += Math.sin(age * 3.7) * p * 0.8;
        } else {
            double since = age - IMPACT, p = smooth(since / (END - IMPACT)), a = heading + 0.5 + p * 0.25;
            // Low enough that the horizon stays in frame and the ring sweeps under the lens.
            double d = mix(70, 118, p);
            cx = Math.cos(a) * d; cz = Math.sin(a) * d; cy = mix(34, 62, p);
            ty = 0; fov = mix(78, 70, smooth(since / 30));
            double shake = 3.2 * Math.exp(-since * 0.22) + 0.4 * (1 - smooth((since - 10) / 40));
            cx += Math.sin(age * 2.1) * shake; cy += Math.sin(age * 2.7) * shake;
            roll = Math.sin(age * 1.9) * shake * 0.9;
        }
        return new RailgunCameraPath.Pose(cx, cy, cz, tx, ty, tz, roll, fov);
    }

    /**
     * Shots on the caster's own body while the field holds it, or null: a low portrait as they rise among the
     * fragments, then a tight chase that banks with them on the second and fourth gravity flips.
     * rider is the body centre relative to the aim point.
     */
    private static RailgunCameraPath.Pose riderShot(double age, double heading, double[] rider) {
        double px = rider[0], py = rider[1], pz = rider[2];
        if (age >= 140 && age < ROTATION) {
            double p = (age - 140) / (ROTATION - 140), a = heading + 2.4 + p * 0.8;
            double d = mix(9, 6.5, smooth(p));
            return new RailgunCameraPath.Pose(px + Math.cos(a) * d, Math.max(1.5, py - mix(3, 1.2, p)), pz + Math.sin(a) * d,
                    px, py + 0.4, pz, sway(age) * 0.5, mix(62, 56, p));
        }
        int k = flip(age);
        if (k == 1 || k == 3) {
            double into = age - ROTATION - k * FLIP, a = heading + (k == 1 ? -0.6 : 2.6) + into * 0.05;
            double kick = Math.exp(-into * 0.3);
            return new RailgunCameraPath.Pose(px + Math.cos(a) * 5.5, py + 2.2 + Math.sin(age * 2.9) * 0.6 * kick,
                    // Aim above the body so it sits under the centred title card.
                    pz + Math.sin(a) * 5.5, px, py + 1.4, pz, rideRoll(age) * 0.6 + Math.sin(age * 1.7) * 4 * kick, 66 + 14 * kick);
        }
        return null;
    }

    /** Full-frame white flash: gravity flips pop, the collapse blinds. */
    static double flash(double age) {
        double hit = age < IMPACT ? 0 : Math.exp(-(age - IMPACT) * 0.28);
        int k = flip(age);
        double pop = k < 0 ? 0 : 0.32 * Math.exp(-(age - ROTATION - k * FLIP) * 0.9);
        return Math.max(hit, pop);
    }

    /** Ink impact frame alpha: four cels at the collapse, single cels at each flip and at the slam. */
    static double impactFrame(double age) {
        if (age >= IMPACT && age < IMPACT + 7) return age < IMPACT + 5 ? 1 : 1 - (age - IMPACT - 5) / 2;
        if (age >= SLAM && age < SLAM + 1.5) return 0.9;
        int k = flip(age);
        if (k > 0) {
            double into = age - ROTATION - k * FLIP;
            if (into < 1.2) return 0.85;
        }
        return 0;
    }

    /** Alternates negative and positive drawings, two ticks each at the collapse. */
    static boolean impactNegative(double age) {
        if (age < IMPACT) return true;
        return ((int) ((age - IMPACT) / 1.5) & 1) == 0;
    }

    static double letterbox(double age) {
        return smooth((age - (COLLAPSE - 6)) / 6) * (1 - smooth((age - IMPACT - 10) / 20));
    }

    /** Darkening of the frame while the world hangs. */
    static double hush(double age) {
        return smooth((age - COLLAPSE) / 3) * (1 - smooth((age - SLAM) / 2)) * 0.45;
    }

    static double card(double age, double start) {
        double t = age - start;
        if (t < 0 || t > 50) return 0;
        return smooth(t / 8) * (1 - smooth((t - 38) / 12));
    }

    /** Speed lines along the gravity direction after a flip, and down during the slam. */
    static double speedLines(double age) {
        if (age >= SLAM && age < IMPACT) return 0.9;
        int k = flip(age);
        if (k < 0) return 0;
        return Math.exp(-(age - ROTATION - k * FLIP) * 0.18) * 0.8;
    }

    /** Body spin of a captured body around its own axis, degrees/tick. */
    private static double spinRate(double age) {
        if (age < ASCENSION) return 0;
        if (age < ROTATION) return 2.5 * lift(age);
        if (age < COLLAPSE) return 4 + 9 * Math.exp(-(age - ROTATION - flip(age) * FLIP) * 0.18);
        return age < SLAM ? 0.6 : 0;
    }

    private static double tumbleCacheAge = Double.NaN, tumbleCache;

    /** Accumulated spin until age, degrees. */
    private static double spun(double age) {
        if (age == tumbleCacheAge) return tumbleCache;
        double total = 0, end = Math.min(age, SLAM);
        for (double t = ASCENSION; t < end; t += 0.5) total += spinRate(t) * Math.min(0.5, end - t);
        tumbleCacheAge = age; tumbleCache = total;
        return total;
    }

    /** Tumble angle of a captured body, degrees; it rights itself exactly by the impact. */
    static double tumble(double age, double scale) {
        if (age < ASCENSION || age >= IMPACT) return 0;
        double angle = spun(age) * scale;
        if (age < SLAM) return angle;
        double upright = Math.ceil(angle / 360) * 360;
        return mix(angle, upright, smooth((age - SLAM) / (IMPACT - SLAM)));
    }

    /** Yaw a captured player's body turns per tick: they drift with the field, then whirl at each flip. */
    static double rideYaw(double age) {
        if (age < ASCENSION || age >= COLLAPSE) return 0;
        if (age < ROTATION) return 1.5 * lift(age);
        return 3 + 7 * Math.exp(-(age - ROTATION - flip(age) * FLIP) * 0.2);
    }

    /** Camera roll of a captured player: a slow sway, a hard bank at every flip, level again on landing. */
    static double rideRoll(double age) {
        if (age < ASCENSION || age >= IMPACT) return 0;
        if (age < ROTATION) return sway(age);
        double bank = 36;
        if (age < COLLAPSE) {
            int k = flip(age);
            double into = age - ROTATION - k * FLIP;
            double from = k == 0 ? sway(ROTATION) : side(k - 1) * bank;
            return mix(from, side(k) * bank, smooth(into / 6)) + 5 * Math.sin(age * 0.6) * Math.exp(-into * 0.15);
        }
        double hold = side(FLIPS - 1) * bank;
        return age < SLAM ? hold : mix(hold, 0, smooth((age - SLAM) / (IMPACT - SLAM)));
    }

    private static double sway(double age) { return 14 * lift(age) * Math.sin((age - ASCENSION) * 0.05); }
    private static int side(int k) { return k % 2 == 0 ? 1 : -1; }

    static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
