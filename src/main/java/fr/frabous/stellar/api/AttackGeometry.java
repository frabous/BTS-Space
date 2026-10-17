package fr.frabous.stellar.api;

/** Shared world-space inclusion rules. Blackhole influence has no vertical limit. */
public final class AttackGeometry {
    private AttackGeometry() { }

    private static boolean circle(BlockTarget target, double x, double z, int radius) {
        double dx = x - (target.getX() + 0.5);
        double dz = z - (target.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public static boolean railgunContains(BlockTarget target, double x, double y, double z) {
        return y >= AttackParameters.WORLD_MIN_Y && y <= AttackParameters.RAILGUN_DAMAGE_MAX_Y
                && circle(target, x, z, AttackParameters.RAILGUN_RADIUS_BLOCKS);
    }

    public static boolean blackholeContains(BlockTarget target, double x, double z) {
        return circle(target, x, z, AttackParameters.BLACKHOLE_RADIUS_BLOCKS);
    }

    public static boolean blackholeSpaceContains(BlockTarget target, double x, double y, double z) {
        return y >= AttackParameters.BLACKHOLE_SPACE_Y && blackholeContains(target, x, z);
    }
}
