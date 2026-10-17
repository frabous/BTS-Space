package fr.frabous.stellar.api;

/** SS-03A Asteroid Puller cues at 20 TPS. Every visual, audio and camera cue reads these ticks. */
public final class AsteroidPullerTiming {
    // Designation and summons.
    public static final int BEACON = 0;
    public static final int UPLINK = 12;
    public static final int SPACE_CUT = 24;
    // Carrier arrival, delivery and departure.
    public static final int WARP_OPEN = 26;
    public static final int WARP_EXIT = 40;
    public static final int WARP_CLEAR = 54;
    public static final int WARP_CLOSE = 68;
    public static final int STATION = 82;
    public static final int BAY_OPEN = 82;
    public static final int RELEASE = 102;
    public static final int WORLD_CUT = 128;
    public static final int RETRO = 150;
    public static final int LAND = 172;
    public static final int ANCHOR = 176;
    public static final int SHIP_SPOOL = 186;
    public static final int UNFOLD = 192;
    public static final int SHIP_DEPART = 200;
    public static final int SHIP_JUMP = 234;
    // Asteroid flyby, interception and deflection.
    public static final int APPEAR = 236;
    public static final int TRACK = 240;
    public static final int CHARGE = 330;
    public static final int MAXIMUM_CHARGE = 358;
    public static final int FIRE = 370;
    public static final int LOCK = 382;
    public static final int BEND_END = 500;
    public static final int ATMOSPHERE = 506;
    public static final int SEVER = 556;
    public static final int SILENCE = 564;
    // Collision and aftermath.
    public static final int IMPACT = 570;
    public static final int SHOCKWAVE = 572;
    public static final int DEBRIS = 600;
    public static final int AFTERMATH = 650;
    public static final int FADE = 820;
    public static final int END = 860;

    private AsteroidPullerTiming() { }
}
