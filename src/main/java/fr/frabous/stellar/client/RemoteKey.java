package fr.frabous.stellar.client;

/**
 * Physical keys on the remote face. Units are model pixels (1/16 block), origin at the body
 * centre, +Y towards the emitter, +Z out of the face. Half sizes describe the cap footprint.
 * Every key sits within reach of the right thumb rooted low on the right edge.
 */
enum RemoteKey {
    FIRE(0, 4.55, 2.05, 0.46, 0xD9443B),
    RAILGUN(-1.68, 2.85, 0.7, 0.7, 0x6FE7FF),
    BLACKHOLE(0, 2.85, 0.7, 0.7, 0xB08CFF),
    CARRIER(1.68, 2.85, 0.7, 0.7, 0xFFA25C),
    SUPERNOVA(-0.86, 1.0, 0.7, 0.7, 0xFFD36B),
    GRAVITY(0.86, 1.0, 0.7, 0.7, 0xFF5AD9),
    UP(0, -0.7, 0.48, 0.38, 0x2A3340),
    LEFT(-1.12, -1.75, 0.38, 0.48, 0x2A3340),
    OK(0, -1.75, 0.5, 0.5, 0x2A6E86),
    RIGHT(1.12, -1.75, 0.38, 0.48, 0x2A3340),
    DOWN(0, -2.8, 0.48, 0.38, 0x2A3340),
    MENU(-1.85, -4.55, 0.6, 0.36, 0x2E3846),
    INFO(0, -4.55, 0.6, 0.36, 0x2E3846),
    SKIP(1.85, -4.55, 0.6, 0.36, 0x2E3846);

    static final RemoteKey[] KEYS = values();
    /** Faceplate surface, cap height above it and the travel of a fully pressed cap. */
    static final double FACE = 1.12, CAP = 0.5, TRAVEL = 0.32;
    static final double PAD_X = 0, PAD_Y = -1.75, PAD_RADIUS = 1.85;

    final double x, y, halfWidth, halfHeight;
    final int color;

    RemoteKey(double x, double y, double halfWidth, double halfHeight, int color) {
        this.x = x; this.y = y; this.halfWidth = halfWidth; this.halfHeight = halfHeight; this.color = color;
    }

    /** Strike keys in menu row order: SS-01 to SS-05. */
    static RemoteKey attack(int row) { return KEYS[RAILGUN.ordinal() + row]; }

    int row() { return isAttack() ? ordinal() - RAILGUN.ordinal() : -1; }
    boolean isAttack() { return ordinal() >= RAILGUN.ordinal() && ordinal() <= GRAVITY.ordinal(); }
    boolean isArrow() { return this == UP || this == DOWN || this == LEFT || this == RIGHT; }
    boolean round() { return this == OK; }

    /** Top of the cap, lowered by a press in [0, 1]. */
    double top(double depress) { return FACE + CAP - TRAVEL * Math.max(0, Math.min(1, depress)); }

    boolean contains(double px, double py) {
        return Math.abs(px - x) <= halfWidth && Math.abs(py - y) <= halfHeight;
    }
}
