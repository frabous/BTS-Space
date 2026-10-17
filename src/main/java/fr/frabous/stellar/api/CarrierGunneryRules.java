package fr.frabous.stellar.api;

/** Authoritative timing and limits shared by the gondola seat, the batteries and the presentation. */
public final class CarrierGunneryRules {
    public static final int BOARD = 80;
    public static final int CONTROL_TICKS = 600;
    public static final int RETURN = BOARD + CONTROL_TICKS;
    public static final int END = RETURN + 80;
    /** Transfer beam lights before boarding and after the return. */
    public static final int BEAM = 24;
    public static final int SHOT_INTERVAL = 8;
    /** Aim reports from the seat and aim broadcasts to witnesses, at most once per this many ticks. */
    public static final int AIM_INTERVAL = 2;
    public static final int RANGE = 768;
    public static final double SHIP_Y = 280;
    /** Feet of the pilot on the observation gondola floor. */
    public static final double SEAT_Y = SHIP_Y + BroadsideBatteries.GONDOLA_FLOOR;
    public static final double BLAST_RADIUS = 8;
    public static final float DAMAGE = 20;

    private CarrierGunneryRules() { }
    public static boolean controls(long age) { return age >= BOARD && age < RETURN; }
    public static boolean canFire(long age, long lastShot) {
        return controls(age) && age - lastShot >= SHOT_INTERVAL;
    }
    public static boolean validAim(float yaw, float pitch) {
        return Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(yaw) <= 360000 && pitch >= -90 && pitch <= 90;
    }
}
