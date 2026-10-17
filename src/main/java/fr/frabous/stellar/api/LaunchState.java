package fr.frabous.stellar.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable, server-authored snapshot. The start tick uses World.getTotalWorldTime(). */
public final class LaunchState {
    private final UUID launchId;
    private final AttackId attack;
    private final int dimension;
    private final BlockTarget target;
    private final BlockTarget deployment;
    private final long seed;
    private final long startTick;
    private final LaunchPhase phase;

    public LaunchState(UUID launchId, AttackId attack, int dimension, BlockTarget target,
                       long seed, long startTick, LaunchPhase phase) {
        this(launchId, attack, dimension, target, target, seed, startTick, phase);
    }

    public LaunchState(UUID launchId, AttackId attack, int dimension, BlockTarget target, BlockTarget deployment,
                       long seed, long startTick, LaunchPhase phase) {
        if (startTick < 0) throw new IllegalArgumentException("Negative start tick");
        this.launchId = Objects.requireNonNull(launchId, "launchId");
        this.attack = Objects.requireNonNull(attack, "attack");
        this.dimension = dimension;
        this.target = Objects.requireNonNull(target, "target");
        this.deployment = Objects.requireNonNull(deployment, "deployment");
        this.seed = seed;
        this.startTick = startTick;
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public UUID getLaunchId() { return launchId; }
    public AttackId getAttack() { return attack; }
    public int getDimension() { return dimension; }
    public BlockTarget getTarget() { return target; }
    public BlockTarget getDeployment() { return deployment; }
    public long getSeed() { return seed; }
    public long getStartTick() { return startTick; }
    public LaunchPhase getPhase() { return phase; }

    public LaunchState withPhase(LaunchPhase next) {
        return new LaunchState(launchId, attack, dimension, target, deployment, seed, startTick, next);
    }
}
