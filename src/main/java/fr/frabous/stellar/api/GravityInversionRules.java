package fr.frabous.stellar.api;

/**
 * SS-05 local gravity inversion. Gravity flips, body drive and the illusory terrain fragments all derive
 * from the launch seed, so the server throws entities in the directions every client draws, with no extra packets.
 */
public final class GravityInversionRules {
    /** Gravity Failure: glowing fissures, loose debris starts floating. */
    public static final int FAILURE = 0;
    /** World Ascension: bodies lift off, terrain fragments tear free. */
    public static final int ASCENSION = 80;
    /** Axis Rotation: fragments orbit an invisible axis, gravity flips every FLIP ticks. */
    public static final int ROTATION = 200;
    /** Total Collapse: everything hangs for a heartbeat, then slams down. */
    public static final int COLLAPSE = 300;
    public static final int SLAM = 306;
    public static final int IMPACT = 316;
    public static final int SHOCK = 336;
    public static final int END = 380;

    public static final double RADIUS = 180;
    public static final int REACH = (int) Math.ceil(RADIUS);
    /** Captured volume below and above the aim point. */
    public static final double FLOOR = 24;
    public static final double CEILING = 120;
    public static final int DAMAGE_INTERVAL = 10;
    public static final float DAMAGE_PER_SECOND = 15;
    public static final float PULSE_DAMAGE = DAMAGE_PER_SECOND * DAMAGE_INTERVAL / 20F;
    public static final float COLLAPSE_DAMAGE = 120;

    public static final int FLIP = 25;
    public static final int FLIPS = (COLLAPSE - ROTATION) / FLIP;
    /** Ticks a gravity flip takes to swing from the previous direction to the new one. */
    public static final int FLIP_BLEND = 4;
    /** Peak speed in blocks/tick right after a flip. */
    public static final double THROW = 0.95;
    /** Lifted bodies stay inside this band above their local ground during the rotation. */
    public static final double MIN_LIFT = 5;
    public static final double MAX_LIFT = 64;

    public static final int FRAGMENTS = 30;
    public static final double AXIS_HEIGHT = 44;
    /** Tilt of the invisible rotation axis from vertical, radians. */
    public static final double AXIS_TILT = 0.32;
    /** Total orbit swept by the fragments during the rotation, radians. */
    public static final double ORBIT = Math.PI * 1.7;
    /** Angular speed of captured bodies around the axis (the fragments' mean rate), capped in blocks/tick. */
    public static final double SPIN_RATE = ORBIT / (COLLAPSE - ROTATION);
    public static final double SPIN_SPEED = 0.55;
    /** Ground ring reaches this radius at END. */
    public static final double SHOCK_RADIUS = RADIUS * 1.35;
    public static final double SHOCK_PUSH = 1.5;
    public static final double SHOCK_LIFT = 0.55;

    private GravityInversionRules() { }

    public static boolean damagePulse(long age) {
        return age >= ASCENSION && age < COLLAPSE && (age - ASCENSION) % DAMAGE_INTERVAL == 0;
    }

    public static boolean contains(BlockTarget target, double x, double y, double z) {
        double dx = x - (target.getX() + 0.5), dz = z - (target.getZ() + 0.5);
        return dx * dx + dz * dz <= RADIUS * RADIUS && y >= target.getY() - FLOOR && y <= target.getY() + CEILING;
    }

    /** Share of full lift in [0, 1]: bodies take off progressively through the ascension. */
    public static double lift(double age) {
        return smooth((age - ASCENSION) / 60.0);
    }

    /** Gravity flip index during the rotation, or -1. */
    public static int flip(double age) {
        if (age < ROTATION || age >= COLLAPSE) return -1;
        return Math.min(FLIPS - 1, (int) ((age - ROTATION) / FLIP));
    }

    /** Unit direction bodies fall towards after flip k; flip -1 is the ascension's upward pull. */
    public static double[] gravity(long seed, int k) {
        if (k < 0) return new double[] {0, 1, 0};
        double angle = unit(seed, 10) * Math.PI * 2;
        for (int i = 1; i <= k; i++) angle += Math.PI * (0.6 + 0.8 * unit(seed, 10 + i));
        double up = k % 2 == 0 ? 0.42 + 0.2 * unit(seed, 30 + k) : -0.3 - 0.15 * unit(seed, 30 + k);
        double flat = Math.sqrt(1 - up * up);
        return new double[] {Math.cos(angle) * flat, up, Math.sin(angle) * flat};
    }

    /** Hover height in blocks above local ground for a body, stable per entity id. */
    public static double hover(int body) {
        return 14 + 24 * unit(body * 0x2545F4914F6CDD1DL, 7);
    }

    /**
     * Velocity in blocks/tick a captured body is driven to, or null when the field does not hold it.
     * dx/dz are horizontal offsets from the aim centre, height is the body's height above its local ground.
     * Loose bodies (items, projectiles, debris) already float during the gravity failure.
     */
    public static double[] drive(long seed, double age, double dx, double height, double dz, int body, boolean loose) {
        if (age < FAILURE || age >= IMPACT) return null;
        if (age < ASCENSION) {
            if (!loose) return null;
            double rise = smooth((age - 20) / 50.0);
            return rise <= 0 ? null : new double[] {0, 0.04 + 0.1 * rise, 0};
        }
        double r = Math.sqrt(dx * dx + dz * dz);
        if (age < ROTATION) {
            double ramp = Math.max(loose ? 1 : 0, lift(age));
            double vy = clamp((hover(body) - height) * 0.05, -0.25, 0.42) * ramp
                    + Math.sin(age * 0.15 + body) * 0.03 * ramp;
            // The swirl winds up so bodies already circle the axis when the rotation begins.
            double swirl = r < 1 ? 0 : Math.min(SPIN_SPEED, r * SPIN_RATE) * (0.1 + 0.9 * smooth((age - 140) / 60.0)) * ramp / r;
            return new double[] {-dz * swirl, vy, dx * swirl};
        }
        if (age < COLLAPSE) {
            int k = flip(age);
            double into = age - ROTATION - k * FLIP;
            double[] from = gravity(seed, k - 1), to = gravity(seed, k);
            double blend = smooth(into / FLIP_BLEND), speed = THROW * (1 - 0.45 * into / FLIP);
            double vx = (from[0] + (to[0] - from[0]) * blend) * speed;
            double vy = (from[1] + (to[1] - from[1]) * blend) * speed;
            double vz = (from[2] + (to[2] - from[2]) * blend) * speed;
            if (height < MIN_LIFT) vy = Math.max(vy, (MIN_LIFT - height) * 0.2);
            if (height > MAX_LIFT) vy = Math.min(vy, (MAX_LIFT - height) * 0.2);
            // Bodies ride the fragments' orbit around the axis while each flip throws them.
            double spin = r < 1 ? 0 : Math.min(SPIN_SPEED, r * SPIN_RATE) / r;
            vx -= dz * spin; vz += dx * spin;
            double rim = RADIUS - 14;
            if (r > rim) { vx -= dx / r * (r - rim) * 0.08; vz -= dz / r * (r - rim) * 0.08; }
            return new double[] {vx, vy, vz};
        }
        if (age < SLAM) return new double[] {0, 0.01, 0};
        return new double[] {0, -Math.max(2.2, (height + 1) / Math.max(1, IMPACT - age)), 0};
    }

    /** Ground shockwave radius after the collapse; 0 before IMPACT. */
    public static double shock(double age) {
        if (age <= IMPACT) return 0;
        double t = Math.min(1, (age - IMPACT) / (END - 14.0 - IMPACT));
        return SHOCK_RADIUS * (1 - Math.pow(1 - t, 3));
    }

    /** Rotation angle of the fragment field around its axis. */
    public static double orbit(double age) {
        if (age <= ROTATION) return 0;
        double t = Math.min(age, COLLAPSE) - ROTATION, jolt = 0;
        for (int k = 1; k <= flip(Math.min(age, COLLAPSE - 1)); k++) jolt += smooth((t - k * FLIP) / 6.0) * 0.22;
        return ORBIT * smooth(t / (COLLAPSE - ROTATION)) + jolt;
    }

    /** Axis direction (unit) of the invisible rotation axis. */
    public static double[] axis(long seed) {
        double heading = unit(seed, 4) * Math.PI * 2;
        return new double[] {Math.cos(heading) * Math.sin(AXIS_TILT), Math.cos(AXIS_TILT), Math.sin(heading) * Math.sin(AXIS_TILT)};
    }

    /** Ground anchor {dx, dz, size} of a terrain fragment, offsets from the aim centre. */
    public static double[] anchor(long seed, int i) {
        double angle = unit(seed, 200 + i * 8) * Math.PI * 2;
        double distance = 24 + Math.sqrt(unit(seed, 201 + i * 8)) * (RADIUS - 44);
        double size = 5 + Math.pow(unit(seed, 202 + i * 8), 2) * 13;
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance, size};
    }

    /** Tick fragment i starts tearing out of the ground. */
    public static double tear(long seed, int i) { return ASCENSION + unit(seed, 204 + i * 8) * 70; }

    /**
     * Fragment i at an age: {dx, dy, dz, yaw, tilt, alpha}. dy is the top surface relative to the aim height,
     * ground is the fragment's local terrain height relative to the aim height.
     */
    public static double[] fragment(long seed, int i, double age, double ground) {
        double[] anchor = anchor(seed, i);
        double size = anchor[2], tear = tear(seed, i), hoverHeight = 22 + unit(seed, 203 + i * 8) * 46;
        double rise = smooth((age - tear) / 55.0);
        double x = anchor[0], z = anchor[1];
        double y = ground - 0.3 + (hoverHeight - ground + 0.3) * rise + Math.sin(age * 0.05 + i) * 1.2 * rise;
        double spinRate = (unit(seed, 205 + i * 8) - 0.5) * 0.035;
        double yaw = unit(seed, 206 + i * 8) * Math.PI * 2 + Math.max(0, age - tear) * spinRate;
        double tilt = Math.sin(age * 0.03 + i * 1.7) * 0.18 * rise;
        double hold = Math.min(age, SLAM);
        double theta = orbit(hold);
        if (theta != 0) {
            double[] k = axis(seed);
            double px = x, py = y - AXIS_HEIGHT, pz = z;
            double cos = Math.cos(theta), sin = Math.sin(theta), dot = k[0] * px + k[1] * py + k[2] * pz;
            x = px * cos + (k[1] * pz - k[2] * py) * sin + k[0] * dot * (1 - cos);
            y = AXIS_HEIGHT + py * cos + (k[2] * px - k[0] * pz) * sin + k[1] * dot * (1 - cos);
            z = pz * cos + (k[0] * py - k[1] * px) * sin + k[2] * dot * (1 - cos);
            y = Math.max(y, 6 + size * 0.5);
            tilt += Math.sin(theta + i) * 0.35;
        }
        double alpha = 1;
        if (age >= COLLAPSE && age < SLAM) {
            y += Math.sin(age * 3.1 + i) * 0.15;
        } else if (age >= SLAM) {
            double t = Math.min(1, (age - SLAM) / (double) (IMPACT - SLAM));
            y += (ground - size * 0.35 - y) * t * t;
            tilt *= 1 - t;
            if (age > IMPACT) y -= (age - IMPACT) * 0.04;
            alpha = 1 - smooth((age - SHOCK) / (END - SHOCK - 6.0));
        }
        return new double[] {x, y, z, yaw, tilt, alpha};
    }

    public static double smooth(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double value, double min, double max) { return value < min ? min : value > max ? max : value; }

    /** Deterministic value in [0, 1) for a seed and key (SplitMix64). */
    public static double unit(long seed, int key) { return KineticRainRules.unit(seed, key); }
}
