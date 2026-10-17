package fr.frabous.stellar.client;

import java.util.function.BiPredicate;

/** Visual phases at 20 ticks per second; the space ascent shares one continuous camera. */
final class RailgunCameraPath {
    enum Shot { WORLD_MARK, WORLD_REAR, ASCENT, CLOUDS, EARTH, SATURN, SOLAR_SYSTEM, GALAXY,
        CANNON_WIDE, BARREL_FLYBY, REAR, CHARGE, CHAMBER, MUZZLE, WARP, SKY, DESCENT, IMPACT, AFTERMATH }

    static final double[] CUTS = {0, 10, 44, 58, 68, 120, 128, 158, 194, 210, 226, 236, 244, 254, 262, 274, 282, 286, 386, 514};

    private RailgunCameraPath() { }

    static Shot shot(double age) {
        for (int i = CUTS.length - 2; i >= 0; i--) if (age >= CUTS[i]) return Shot.values()[i];
        return Shot.WORLD_MARK;
    }

    static double progress(double age) {
        return progress(age, shot(age));
    }

    private static double progress(double age, Shot shot) {
        int i = shot.ordinal();
        return clamp((age - CUTS[i]) / (CUTS[i + 1] - CUTS[i]));
    }

    static boolean isWorldShot(Shot shot) {
        switch (shot) {
            case WORLD_MARK: case WORLD_REAR: case ASCENT: case CLOUDS: case SKY: case DESCENT: case IMPACT: case AFTERMATH:
                return true;
            default: return false;
        }
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    static double smooth(double v) { v = clamp(v); return v * v * (3 - 2 * v); }
    static double mix(double a, double b, double t) { return a + (b - a) * t; }
    /** Mostly linear travel with softened ends, so consecutive cuts keep their momentum. */
    static double glide(double v) { v = clamp(v); return mix(v, smooth(v), 0.4); }

    static Pose pose(double age) {
        if (RailgunPullback.active(age)) return RailgunPullback.pose(age);
        Shot shot = shot(age);
        double p = progress(age, shot), t = glide(p);
        switch (shot) {
            case CANNON_WIDE: return new Pose(mix(110, 85, smooth(p)), mix(62, 45, smooth(p)), mix(120, 95, smooth(p)),
                    -4, 0, 0, mix(-27, -22, p), mix(53, 50, p));
            case BARREL_FLYBY: return new Pose(mix(69, -47, t), mix(19, 17, t), 24, mix(-28, -85, t), 1, 0, mix(-6, -10, t), 65);
            case REAR: return new Pose(mix(-113, -96, t), mix(33, 25, t), mix(65, 48, t), -36, 0, 0, mix(26, 22, t), 60);
            case CHARGE: return new Pose(mix(-92, -78, t), mix(16, 12, t), mix(30, 26, t), 25, 0, 0, mix(-12, -16, t), mix(66, 62, t));
            // The 67.5-degree sector lies between rails 45/90 and away from the 22.5/112.5-degree trusses.
            case CHAMBER: return new Pose(mix(20, 30, t), Math.cos(Math.PI * 3 / 8) * mix(52, 48, t),
                    Math.sin(Math.PI * 3 / 8) * mix(52, 48, t), mix(12, 20, t), 0, 0, -4, mix(60, 55, t));
            // Ahead of the muzzle and off-axis: the discharge tears past the lens while the barrel kicks away.
            case MUZZLE: return new Pose(mix(112, 128, t), mix(16, 22, t), mix(-58, -70, t),
                    mix(40, 48, t), mix(0, 2, t), 0, mix(4, 1, t), 70 + 8 * Math.exp(-(age - CUTS[13]) * 0.5));
            default: return new Pose(0, 0, 160, 0, 0, 0, 0, 60);
        }
    }

    static boolean loadedRectangle(double minX, double minZ, double maxX, double maxZ, BiPredicate<Double, Double> loaded) {
        for (int x = (int) Math.floor(minX) >> 4; x <= ((int) Math.floor(maxX) >> 4); x++) {
            for (int z = (int) Math.floor(minZ) >> 4; z <= ((int) Math.floor(maxZ) >> 4); z++) {
                if (!loaded.test(x * 16.0 + 8, z * 16.0 + 8)) return false;
            }
        }
        return true;
    }

    static boolean loadedView(Pose pose, BiPredicate<Double, Double> loaded) {
        return loadedRectangle(Math.min(pose.x, pose.targetX) - 8, Math.min(pose.z, pose.targetZ) - 8,
                Math.max(pose.x, pose.targetX) + 8, Math.max(pose.z, pose.targetZ) + 8, loaded);
    }

    static double aftermathCutStart(double age) {
        return age < 396 ? RailgunVisualTiming.BEAM_END : age < RailgunVisualTiming.FINAL_CUT ? 396
                : age < 504 ? RailgunVisualTiming.FINAL_CUT : 504;
    }

    static Pose aftermathPose(double age, double x, double y, double z) {
        if (age < 396) return new Pose(x + 450, y + 190, z + 310, x, y + 125, z, 0, 66);
        if (age < RailgunVisualTiming.FINAL_CUT) {
            double p = smooth((age - 396) / (RailgunVisualTiming.FINAL_CUT - 396));
            return new Pose(x + mix(390, 220, p), y + mix(170, 70, p), z + mix(275, 170, p), x, y + 2, z, 0, 66);
        }
        if (age < 504) {
            double p = smooth((age - RailgunVisualTiming.FINAL_CUT) / (504 - RailgunVisualTiming.FINAL_CUT));
            double angle = mix(1.8, 2.03, p), radius = mix(237, 222, p);
            return new Pose(x + Math.cos(angle) * radius, y + mix(38, 25, p), z + Math.sin(angle) * radius,
                    x + Math.cos(angle + 0.24) * 194, y + 1, z + Math.sin(angle + 0.24) * 194, 0, 64);
        }
        double p = smooth((age - 504) / (RailgunVisualTiming.END - 504)), angle = 2.55, radius = mix(216, 207, p);
        return new Pose(x + Math.cos(angle) * radius, y + mix(22, 15, p), z + Math.sin(angle) * radius,
                x + Math.cos(angle - 0.15) * 192, y + 1, z + Math.sin(angle - 0.15) * 192, 0, 60);
    }

    private static Pose rotatePose(Pose pose, double x, double z, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new Pose(x + (pose.x - x) * c - (pose.z - z) * s, pose.y,
                z + (pose.x - x) * s + (pose.z - z) * c,
                x + (pose.targetX - x) * c - (pose.targetZ - z) * s, pose.targetY,
                z + (pose.targetX - x) * s + (pose.targetZ - z) * c, pose.roll, pose.fov);
    }

    private static boolean loadedTrajectory(double start, double end, double x, double y, double z,
                                             double rotation, BiPredicate<Double, Double> loaded) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        // Include the outgoing endpoint, not the next cut. The complete footprint is conservative between samples.
        for (double age = start; age <= end; age += 0.125) {
            Pose pose = rotatePose(aftermathPose(Math.min(age, end - 0.000001), x, y, z), x, z, rotation);
            minX = Math.min(minX, Math.min(pose.x, pose.targetX)); minZ = Math.min(minZ, Math.min(pose.z, pose.targetZ));
            maxX = Math.max(maxX, Math.max(pose.x, pose.targetX)); maxZ = Math.max(maxZ, Math.max(pose.z, pose.targetZ));
        }
        return loadedRectangle(minX - 8, minZ - 8, maxX + 8, maxZ + 8, loaded);
    }

    static AftermathPlan planAftermath(double age, double x, double y, double z, BiPredicate<Double, Double> loaded) {
        double start = aftermathCutStart(age), end = age < 396 ? 396 : age < RailgunVisualTiming.FINAL_CUT
                ? RailgunVisualTiming.FINAL_CUT : age < 504 ? 504 : RailgunVisualTiming.END;
        double rotation = Double.NaN;
        if (loadedTrajectory(start, end, x, y, z, 0, loaded)) rotation = 0;
        else if (age >= RailgunVisualTiming.FINAL_CUT) {
            search: for (int step = 1; step <= 32; step++) {
                for (int side = -1; side <= 1; side += 2) {
                    double angle = side * step * Math.PI / 32;
                    if (loadedTrajectory(start, end, x, y, z, angle, loaded)) { rotation = angle; break search; }
                }
            }
        }
        Pose aerial = aerialAftermathPose(start, x, y, z, loaded);
        if (Double.isNaN(rotation) && aerial == null) return null;
        if (aerial == null) {
            Pose first = rotatePose(aftermathPose(start, x, y, z), x, z, rotation);
            aerial = aerialAftermathPose(start, first.targetX, first.targetY, first.targetZ, loaded);
        }
        return new AftermathPlan(x, y, z, rotation, aerial);
    }

    private static Pose aerialAftermathPose(double age, double x, double y, double z, BiPredicate<Double, Double> loaded) {
        // Short view distances cannot supply the 200-block rim. Keep the cuts aerial over loaded ground.
        double dx = age < RailgunVisualTiming.FINAL_CUT ? 18 : age < 504 ? -14 : 10;
        double dz = age < RailgunVisualTiming.FINAL_CUT ? 18 : age < 504 ? 14 : -12;
        double height = age < RailgunVisualTiming.FINAL_CUT ? 45 : age < 504 ? 35 : 24;
        double fov = aftermathPose(age, x, y, z).fov;
        for (double scale = 1; scale >= 0.25; scale -= 0.25) {
            for (int sector = 0; sector < 4; sector++) {
                double angle = sector * Math.PI / 2, c = Math.cos(angle), s = Math.sin(angle);
                Pose local = new Pose(x + (dx * c - dz * s) * scale, y + height, z + (dx * s + dz * c) * scale,
                        x, y + 1, z, 0, fov);
                if (loadedView(local, loaded)) return local;
            }
        }
        return loaded.test(x, z) ? new Pose(x, y + height, z, x, y + 1, z, 0, fov) : null;
    }

    static Pose blendPoses(Pose from, Pose to, double progress) {
        double p = smooth(progress);
        return new Pose(mix(from.x, to.x, p), mix(from.y, to.y, p), mix(from.z, to.z, p),
                mix(from.targetX, to.targetX, p), mix(from.targetY, to.targetY, p), mix(from.targetZ, to.targetZ, p),
                mix(from.roll, to.roll, p), mix(from.fov, to.fov, p));
    }

    static final class AftermathPlan {
        final double x, y, z, rotation;
        final Pose aerial;
        AftermathPlan(double x, double y, double z, double rotation, Pose aerial) {
            this.x = x; this.y = y; this.z = z; this.rotation = rotation; this.aerial = aerial;
        }
        Pose pose(double age) {
            return Double.isNaN(rotation) ? aerial : rotatePose(aftermathPose(age, x, y, z), x, z, rotation);
        }
    }

    static final class Pose {
        final double x, y, z, targetX, targetY, targetZ, roll, fov;
        Pose(double x, double y, double z, double targetX, double targetY, double targetZ, double roll, double fov) {
            this.x = x; this.y = y; this.z = z;
            this.targetX = targetX; this.targetY = targetY; this.targetZ = targetZ;
            this.roll = roll; this.fov = fov;
        }
    }
}
