package fr.frabous.stellar.client;

/** One nested space scene, viewed by a continuous logarithmic dolly away from Earth. */
final class RailgunPullback {
    static final double START = 58, END = 194, EARTH_RADIUS = 40;
    static final double SUN_X = -1600, SUN_Z = -900;
    static final double GALAXY_X = -450000, GALAXY_Z = -220000, GALAXY_RADIUS = 900000;
    static final double[] ORBITS = {550, 1050, Math.hypot(SUN_X, SUN_Z), 2700, 4400, 6800};
    /** Surface normal at the designated target: the uplink leaves radially and stays in profile for the whole dolly. */
    static final double[] UPLINK = normal(Math.toRadians(30), Math.toRadians(60));
    private static final double LOOK_AHEAD = 24;
    private static final double[] AGES = {58, 68, 108, 120, 128, 144, 158, 178, 194};
    private static final double[] DISTANCES = {44, 53, 320, 2100, 5600, 14000, 180000, 2800000, 6500000};
    private static final double[] LOG_DISTANCES = logDistances();
    private static final double[] TANGENTS = tangents();

    private RailgunPullback() { }

    static boolean active(double age) { return age >= START && age < END; }

    static double opacity(double age) { return RailgunCameraPath.smooth((age - START) / 10); }

    static double clouds(double age) {
        return RailgunCameraPath.smooth((age - 52) / 8) * (1 - RailgunCameraPath.smooth((age - 64) / 14));
    }

    static double distance(double age) {
        if (age <= START) return DISTANCES[0];
        if (age >= END) return DISTANCES[DISTANCES.length - 1];
        int i = 0;
        while (age > AGES[i + 1]) i++;
        double span = AGES[i + 1] - AGES[i], t = (age - AGES[i]) / span;
        double a = LOG_DISTANCES[i], b = LOG_DISTANCES[i + 1];
        // Monotone cubic tangents keep the zoom moving through every former cut.
        double log = (2*t*t*t - 3*t*t + 1)*a + (t*t*t - 2*t*t + t)*span*TANGENTS[i]
                + (-2*t*t*t + 3*t*t)*b + (t*t*t - t*t)*span*TANGENTS[i + 1];
        return Math.exp(log);
    }

    private static double[] logDistances() {
        double[] values = new double[DISTANCES.length];
        for (int i = 0; i < values.length; i++) values[i] = Math.log(DISTANCES[i]);
        return values;
    }

    private static double[] tangents() {
        double[] values = new double[AGES.length];
        for (int i = 0; i < values.length; i++) values[i] = tangent(i);
        return values;
    }

    private static double slope(int i) {
        return Math.log(DISTANCES[i + 1] / DISTANCES[i]) / (AGES[i + 1] - AGES[i]);
    }

    private static double tangent(int i) {
        if (i == 0) return slope(0);
        if (i == AGES.length - 1) return slope(i - 1);
        double before = AGES[i] - AGES[i - 1], after = AGES[i + 1] - AGES[i];
        double w1 = 2*after + before, w2 = after + 2*before;
        return (w1 + w2) / (w1 / slope(i - 1) + w2 / slope(i));
    }

    static RailgunCameraPath.Pose pose(double age) {
        return pose(age, distance(age));
    }

    static RailgunCameraPath.Pose pose(double age, double d) {
        double reveal = RailgunCameraPath.smooth((age - START) / 30);
        double solar = RailgunCameraPath.smooth((age - 110) / 42);
        double galaxy = RailgunCameraPath.smooth((age - 150) / 34);
        double cx = RailgunCameraPath.mix(SUN_X * solar, GALAXY_X, galaxy);
        double cz = RailgunCameraPath.mix(SUN_Z * solar, GALAXY_Z, galaxy);
        // The opening look follows the uplink climbing past the limb, then settles on Earth's centre.
        double look = (EARTH_RADIUS + LOOK_AHEAD) * (1 - reveal);
        double elevation = Math.toRadians(RailgunCameraPath.mix(10, 46,
                RailgunCameraPath.smooth((age - START) / (END - START))));
        double yaw = RailgunCameraPath.mix(0, 0.24, RailgunCameraPath.smooth((age - 88) / 96));
        double horizontal = Math.cos(elevation) * d;
        // The eye retreats from the same Earth origin; reframing never replaces a body or resets the distance.
        return new RailgunCameraPath.Pose(cx + Math.sin(yaw) * horizontal, Math.sin(elevation) * d,
                cz + Math.cos(yaw) * horizontal, cx + UPLINK[0] * look, UPLINK[1] * look, cz + UPLINK[2] * look,
                -12 * RailgunCameraPath.smooth((age - START) / 50), 52);
    }

    private static double[] normal(double elevation, double yaw) {
        return new double[] {Math.cos(elevation) * Math.sin(yaw), Math.sin(elevation), Math.cos(elevation) * Math.cos(yaw)};
    }

    static double renderScale(double age) { return Math.min(1, 160 / distance(age)); }
    static double renderScaleForDistance(double distance) { return Math.min(1, 160 / distance); }

    static double orbitAngle(int i) { return i == 2 ? Math.atan2(-SUN_Z, -SUN_X) : i == 5 ? -0.65 : i * 1.8; }
}
