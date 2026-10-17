package fr.frabous.stellar.api;

/** World block coordinates, never coordinates in the local cinematic scene. */
public final class BlockTarget {
    public static final int MAX_HORIZONTAL_COORDINATE = 29999984;
    private final int x;
    private final int y;
    private final int z;

    public BlockTarget(int x, int y, int z) {
        if (x < -MAX_HORIZONTAL_COORDINATE || x > MAX_HORIZONTAL_COORDINATE
                || z < -MAX_HORIZONTAL_COORDINATE || z > MAX_HORIZONTAL_COORDINATE
                || y < 0 || y > 255) {
            throw new IllegalArgumentException("Target outside Minecraft 1.12.2 bounds");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BlockTarget)) return false;
        BlockTarget target = (BlockTarget) other;
        return x == target.x && y == target.y && z == target.z;
    }

    @Override
    public int hashCode() { return (31 * x + y) * 31 + z; }
}
