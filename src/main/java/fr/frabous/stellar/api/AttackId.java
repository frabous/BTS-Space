package fr.frabous.stellar.api;

public enum AttackId {
    RAILGUN(1, "SS-01", "railgun", AttackFamily.ORBITAL, "RAILGUN"),
    BLACKHOLE(2, "SS-02", "blackhole", AttackFamily.GRAVITY, "BLACKHOLE"),
    /** Wire id, NBT value and "moon_attract" resources stay compatible with the former Asteroide Attract. */
    ASTEROID_PULLER(5, "SS-03A", "moon_attract", AttackFamily.CARRIER, "Asteroid Puller"),
    SUPERNOVA(6, "SS-04", "supernova", AttackFamily.STELLAR, "SUPERNOVA"),
    CARRIER_GUNNERY(7, "SS-03B", "carrier_gunnery", AttackFamily.CARRIER, "Broadside Command"),
    KINETIC_RAIN(8, "SS-03C", "kinetic_rain", AttackFamily.CARRIER, "Kinetic Rain"),
    GRAVITY_INVERSION(9, "SS-05", "gravity_inversion", AttackFamily.INVERSION, "GRAVITY INVERSION");

    private final int wireId;
    private final String displayId;
    private final String resourcePath;
    private final AttackFamily family;
    private final String displayName;

    AttackId(int wireId, String displayId, String resourcePath, AttackFamily family, String displayName) {
        this.wireId = wireId;
        this.displayId = displayId;
        this.resourcePath = resourcePath;
        this.family = family;
        this.displayName = displayName;
    }

    public int getWireId() { return wireId; }
    public String getDisplayId() { return displayId; }
    public String getResourcePath() { return resourcePath; }
    public AttackFamily getFamily() { return family; }
    public String getDisplayName() { return displayName; }

    /** Carrier strikes also send the delivery point chosen by the server. */
    public boolean carriesDeployment() { return family == AttackFamily.CARRIER; }

    public AttackId next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static AttackId fromWireId(int value) {
        for (AttackId attack : values()) {
            if (attack.wireId == value) return attack;
        }
        throw new IllegalArgumentException("Unknown attack: " + value);
    }

    /** Length probe for network decoding; unknown ids are rejected later by {@link #fromWireId}. */
    public static boolean carriesDeployment(int wireId) {
        for (AttackId attack : values()) if (attack.wireId == wireId) return attack.carriesDeployment();
        return false;
    }
}
