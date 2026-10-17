package fr.frabous.stellar.api;

import java.util.Objects;

/** Protocol v1 durations at 20 TPS. Railgun cuts follow the supplied 25.7-second reference. */
public final class LaunchTimeline {
    private static final int[] RAILGUN_TICKS = {44, 142, 68, 32, 168, 60};
    private static final int[] BLACKHOLE_TICKS = {60, 100, 140, 240, 200, 60};
    private static final int[] ASTEROID_PULLER_TICKS = {100,
            AsteroidPullerTiming.CHARGE - 100,
            AsteroidPullerTiming.FIRE - AsteroidPullerTiming.CHARGE,
            AsteroidPullerTiming.IMPACT - AsteroidPullerTiming.FIRE,
            AsteroidPullerTiming.AFTERMATH - AsteroidPullerTiming.IMPACT,
            AsteroidPullerTiming.END - AsteroidPullerTiming.AFTERMATH};
    private static final int[] SUPERNOVA_TICKS = {SupernovaTiming.STAR,
            SupernovaTiming.COLLAPSE - SupernovaTiming.STAR,
            SupernovaTiming.DETONATE - SupernovaTiming.COLLAPSE,
            SupernovaTiming.IMPACT - SupernovaTiming.DETONATE,
            SupernovaTiming.AFTERMATH - SupernovaTiming.IMPACT,
            SupernovaTiming.END - SupernovaTiming.AFTERMATH};
    /** FIRING is the rod barrage, IMPACT the central hammer. */
    private static final int[] KINETIC_RAIN_TICKS = {20,
            KineticRainRules.BAY_OPEN - 20,
            KineticRainRules.FIRST_IMPACT - KineticRainRules.BAY_OPEN,
            KineticRainRules.HAMMER_IMPACT - KineticRainRules.FIRST_IMPACT,
            20,
            KineticRainRules.END - KineticRainRules.HAMMER_IMPACT - 20};
    /** FIRING is the hang and slam, IMPACT the collapse, AFTERMATH the shockwave. */
    private static final int[] GRAVITY_INVERSION_TICKS = {GravityInversionRules.ASCENSION,
            GravityInversionRules.ROTATION - GravityInversionRules.ASCENSION,
            GravityInversionRules.COLLAPSE - GravityInversionRules.ROTATION,
            GravityInversionRules.IMPACT - GravityInversionRules.COLLAPSE,
            GravityInversionRules.SHOCK - GravityInversionRules.IMPACT,
            GravityInversionRules.END - GravityInversionRules.SHOCK};
    private LaunchTimeline() { }

    public static int durationTicks(AttackId attack, LaunchPhase phase) {
        Objects.requireNonNull(attack, "attack");
        Objects.requireNonNull(phase, "phase");
        // MARKING, ASCENT, CHARGING, FIRING, IMPACT, AFTERMATH; COMPLETE is terminal.
        int[] ticks;
        switch (attack) {
            case RAILGUN: ticks = RAILGUN_TICKS; break;
            case BLACKHOLE: ticks = BLACKHOLE_TICKS; break;
            case ASTEROID_PULLER: ticks = ASTEROID_PULLER_TICKS; break;
            case CARRIER_GUNNERY: ticks = new int[] {20, 60, 1, 599, 1, 79}; break;
            case SUPERNOVA: ticks = SUPERNOVA_TICKS; break;
            case KINETIC_RAIN: ticks = KINETIC_RAIN_TICKS; break;
            case GRAVITY_INVERSION: ticks = GRAVITY_INVERSION_TICKS; break;
            default: throw new IllegalArgumentException("attack");
        }
        return phase == LaunchPhase.COMPLETE ? 0 : ticks[phase.getWireId()];
    }

    public static int phaseStartTicks(AttackId attack, LaunchPhase phase) {
        int ticks = 0;
        for (LaunchPhase current : LaunchPhase.values()) {
            if (current == phase) return ticks;
            ticks += durationTicks(attack, current);
        }
        throw new IllegalArgumentException("phase");
    }

    public static int totalTicks(AttackId attack) {
        return phaseStartTicks(attack, LaunchPhase.COMPLETE);
    }

    /** Compose with radius and player altitude on the client; no extra packet is needed. */
    public static boolean blackholePullActive(long elapsedTicks) {
        return elapsedTicks >= phaseStartTicks(AttackId.BLACKHOLE, LaunchPhase.ASCENT)
                && elapsedTicks < phaseStartTicks(AttackId.BLACKHOLE, LaunchPhase.AFTERMATH);
    }

    public static LaunchPhase phaseAt(AttackId attack, long elapsedTicks) {
        if (elapsedTicks < 0) return LaunchPhase.MARKING;
        for (LaunchPhase phase : LaunchPhase.values()) {
            int duration = durationTicks(attack, phase);
            if (elapsedTicks < duration) return phase;
            elapsedTicks -= duration;
        }
        return LaunchPhase.COMPLETE;
    }
}
