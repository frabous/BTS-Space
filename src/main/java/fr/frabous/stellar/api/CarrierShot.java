package fr.frabous.stellar.api;

import java.util.UUID;

/**
 * Server-confirmed shell. Coordinates never come from a client request. Ticks are world ticks: the
 * request was accepted at {@code issued}, the battery fires once laid at {@code fire}, and the shell
 * lands (and deals damage) at {@code impact}.
 */
public final class CarrierShot {
    public static final int TERRAIN = 0, ENTITY = 1, AIR = 2;
    public final UUID launchId;
    public final int dimension, battery, barrel, kind;
    public final long issued, fire, impact;
    /** Muzzle and impact point. */
    public final double x, y, z, hitX, hitY, hitZ;

    public CarrierShot(UUID launchId, int dimension, int battery, int barrel, int kind, long issued, long fire, long impact,
                       double x, double y, double z, double hitX, double hitY, double hitZ) {
        this.launchId = launchId; this.dimension = dimension; this.battery = battery; this.barrel = barrel; this.kind = kind;
        this.issued = issued; this.fire = fire; this.impact = impact;
        this.x = x; this.y = y; this.z = z; this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
    }
}
