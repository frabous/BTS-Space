package fr.frabous.stellar.api;

/**
 * SS-03B fire control shared by the server (authority) and every client (animation): the destroyer's
 * five ventral twin batteries, its observation gondola and the shell ballistics. Ship-local axes follow
 * the destroyer mesh (+X bow, +Y dorsal, +Z starboard); distances are blocks and angles radians. A
 * world yaw of 0 points along +X and positive yaw turns toward -Z, like GL/RigMesh rotations.
 */
public final class BroadsideBatteries {
    public static final int COUNT = 5;
    /**
     * Ring centres on the mounts authored in models/orbital_destroyer.bbmodel (group 07). The aft wing
     * pair sits at the outboard side of its mount so the gun house clears the keel at every bearing.
     */
    public static final double[] MOUNT_X = {-126, -48, -48, 29, 29};
    public static final double[] MOUNT_Z = {0, -39, 39, -33, 33};
    /** Hull surface each barbette is welded to, and the traverse ring the gun house hangs from. */
    public static final double[] HULL_Y = {-55, -15.7, -15.7, -14, -14};
    public static final double[] RING_Y = {-60, -33, -33, -20, -20};
    public static final String[] NAMES = {"B1 KEEL", "B2 PORT AFT", "B3 STBD AFT", "B4 PORT FWD", "B5 STBD FWD"};
    /** Trunnions sit ahead of and below the ring, so the barrels can depress to the vertical. */
    public static final double TRUNNION_FORWARD = 5, TRUNNION_DROP = 7;
    public static final double BARREL_LENGTH = 22.5, BARREL_SPACING = 3;
    public static final double MIN_PITCH = Math.toRadians(-88), MAX_PITCH = Math.toRadians(-2);
    public static final double REST_PITCH = Math.toRadians(-18);
    /** Traverse and elevation drive limits, per tick. */
    public static final double YAW_RATE = Math.toRadians(7.5), PITCH_RATE = Math.toRadians(5);
    public static final int LOCK_TICKS = 2, RELOAD_TICKS = 40;
    /** Coilgun slug speed in blocks per tick; straight flight, no measurable drop at these ranges. */
    public static final double SHELL_SPEED = 24;
    /** Observation gondola under the keel ramp: column and seat floor, ship-local. */
    public static final double GONDOLA_X = -8, GONDOLA_FLOOR = -40, GONDOLA_RADIUS = 4.6;
    private static final double TAU = Math.PI * 2, CLEARANCE = 160;
    /**
     * Solid hull volumes a shell may not cross, ship-local AABBs {x0, y0, z0, x1, y1, z1}: aft belly,
     * reactor keel, the keel ramp in steps that follow the probed mesh (it rises and narrows toward the
     * bow), both belly frames and the gondola.
     */
    private static final double[][] HULL = {
            {-241, -53, -42, -169, -12, 42}, {-169, -56, -35, -80, -17, 35},
            {-80, -49.2, -33, -72, -12, 33}, {-72, -46.6, -32, -64, -12, 32}, {-64, -44.1, -31, -56, -12, 31},
            {-56, -41.7, -30, -48, -12, 30}, {-48, -39.2, -29.5, -40, -12, 29.5}, {-40, -36.8, -28.5, -32, -12, 28.5},
            {-32, -34.3, -27, -24, -12, 27}, {-24, -31.9, -26, -16, -12, 26}, {-16, -29.4, -25.5, -8, -12, 25.5},
            {-8, -27, -23, 0, -12, 23}, {0, -24.5, -22.5, 18, -12, 22.5},
            {-203, -50, -52, -69, -18, -27}, {-203, -50, 27, -69, -18, 52},
            {GONDOLA_X - 5, GONDOLA_FLOOR - 3.5, -5, GONDOLA_X + 5, -26, 5}};

    private BroadsideBatteries() { }

    /** Ship placement for one launch: the gondola hangs over the deployment column, bow toward the aim. */
    public static final class Frame {
        public final double x, y, z, heading, cos, sin;

        Frame(double x, double y, double z, double heading) {
            this.x = x; this.y = y; this.z = z; this.heading = heading;
            cos = StrictMath.cos(heading); sin = StrictMath.sin(heading);
        }

        public double[] world(double lx, double ly, double lz) {
            return new double[] {x + lx * cos + lz * sin, y + ly, z - lx * sin + lz * cos};
        }

        public double[] local(double wx, double wy, double wz) {
            double dx = wx - x, dz = wz - z;
            return new double[] {dx * cos - dz * sin, wy - y, dx * sin + dz * cos};
        }
    }

    public static Frame frame(LaunchState launch) { return frame(launch.getDeployment(), launch.getTarget()); }

    public static Frame frame(BlockTarget deployment, BlockTarget target) {
        double dx = target.getX() - deployment.getX(), dz = target.getZ() - deployment.getZ();
        double heading = dx * dx + dz * dz < 1 ? 0 : StrictMath.atan2(-dz, dx);
        double cos = StrictMath.cos(heading), sin = StrictMath.sin(heading);
        return new Frame(deployment.getX() + 0.5 - GONDOLA_X * cos, CarrierGunneryRules.SHIP_Y,
                deployment.getZ() + 0.5 + GONDOLA_X * sin, heading);
    }

    public static double[] ring(Frame f, int battery) {
        return f.world(MOUNT_X[battery], RING_Y[battery], MOUNT_Z[battery]);
    }

    public static double[] trunnion(Frame f, int battery, double yaw) {
        double[] r = ring(f, battery);
        return new double[] {r[0] + Math.cos(yaw) * TRUNNION_FORWARD, r[1] - TRUNNION_DROP, r[2] - Math.sin(yaw) * TRUNNION_FORWARD};
    }

    public static double[] direction(double yaw, double pitch) {
        double horizontal = Math.cos(pitch);
        return new double[] {horizontal * Math.cos(yaw), Math.sin(pitch), -horizontal * Math.sin(yaw)};
    }

    /** Muzzle of barrel 0 (port of the bore line) or 1 for a given world bearing. */
    public static double[] muzzle(Frame f, int battery, int barrel, double yaw, double pitch) {
        double[] t = trunnion(f, battery, yaw), d = direction(yaw, pitch);
        double side = (barrel == 0 ? -1 : 1) * BARREL_SPACING;
        return new double[] {t[0] + d[0] * BARREL_LENGTH + Math.sin(yaw) * side, t[1] + d[1] * BARREL_LENGTH,
                t[2] + d[2] * BARREL_LENGTH + Math.cos(yaw) * side};
    }

    public static double[] rest(Frame f) { return new double[] {f.heading, REST_PITCH}; }

    /**
     * World bearing {yaw, pitch} that lays the bore on a point, or null when the point lies outside the
     * battery's arc: above the depression limits, under its own trunnion, or behind keel and gondola.
     */
    public static double[] solution(Frame f, int battery, double x, double y, double z) {
        double[] r = ring(f, battery);
        double dx = x - r[0], dz = z - r[2], reach = Math.sqrt(dx * dx + dz * dz) - TRUNNION_FORWARD;
        if (reach <= 0.5) return null;
        double yaw = Math.atan2(-dz, dx), pitch = Math.atan2(y - (r[1] - TRUNNION_DROP), reach);
        if (pitch < MIN_PITCH || pitch > MAX_PITCH) return null;
        return clear(f, battery, yaw, pitch) ? new double[] {yaw, pitch} : null;
    }

    /** True when the bore line leaves the ship without crossing a hull volume. */
    public static boolean clear(Frame f, int battery, double yaw, double pitch) {
        double[] t = trunnion(f, battery, yaw), d = direction(yaw, pitch);
        double[] start = f.local(t[0], t[1], t[2]), tip = f.local(t[0] + d[0], t[1] + d[1], t[2] + d[2]);
        double[] local = {tip[0] - start[0], tip[1] - start[1], tip[2] - start[2]};
        for (double[] box : HULL) if (crosses(start, local, CLEARANCE, box)) return false;
        return true;
    }

    /** Slab test of the segment start + direction * [0, length] against one AABB. */
    static boolean crosses(double[] start, double[] direction, double length, double[] box) {
        double near = 0, far = length;
        for (int axis = 0; axis < 3; axis++) {
            double low = box[axis], high = box[axis + 3];
            if (Math.abs(direction[axis]) < 1.0E-12) {
                if (start[axis] < low || start[axis] > high) return false;
                continue;
            }
            double a = (low - start[axis]) / direction[axis], b = (high - start[axis]) / direction[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return false;
        }
        return true;
    }

    /** One tick of traverse and elevation toward a goal; bearing is {yaw, pitch} and is updated in place. */
    public static void step(double[] bearing, double yaw, double pitch) {
        bearing[0] = wrap(bearing[0] + clamp(wrap(yaw - bearing[0]), YAW_RATE));
        bearing[1] += clamp(pitch - bearing[1], PITCH_RATE);
    }

    public static int slewTicks(double[] bearing, double yaw, double pitch) {
        double ticks = Math.max(Math.abs(wrap(yaw - bearing[0])) / YAW_RATE, Math.abs(pitch - bearing[1]) / PITCH_RATE);
        return (int) Math.ceil(ticks - 1.0E-9);
    }

    public static int flightTicks(double distance) { return Math.max(1, (int) Math.ceil(distance / SHELL_SPEED)); }

    public static double wrap(double angle) {
        angle %= TAU;
        if (angle > Math.PI) angle -= TAU;
        if (angle < -Math.PI) angle += TAU;
        return angle;
    }

    private static double clamp(double value, double limit) { return Math.max(-limit, Math.min(limit, value)); }
}
