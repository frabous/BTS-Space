package fr.frabous.stellar.api;

public enum LaunchPhase {
    MARKING(0), ASCENT(1), CHARGING(2), FIRING(3), IMPACT(4), AFTERMATH(5), COMPLETE(6);

    private final int wireId;

    LaunchPhase(int wireId) { this.wireId = wireId; }
    public int getWireId() { return wireId; }

    public static LaunchPhase fromWireId(int value) {
        for (LaunchPhase phase : values()) {
            if (phase.wireId == value) return phase;
        }
        throw new IllegalArgumentException("Unknown phase: " + value);
    }
}
