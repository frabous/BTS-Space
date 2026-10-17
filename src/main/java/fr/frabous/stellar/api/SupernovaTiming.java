package fr.frabous.stellar.api;

/** Shared supernova cues, in world ticks at 20 TPS. Damage exists only during IMPACT. */
public final class SupernovaTiming {
    public static final int STAR = 60;
    public static final int COLLAPSE = 180;
    public static final int SHOCK_REVIVAL = 260;
    public static final int DETONATE = 320;
    public static final int WORLD_RETURN = 382;
    public static final int IMPACT = 400;
    public static final int AFTERMATH = 480;
    public static final int END = 640;
    private SupernovaTiming() { }
}
