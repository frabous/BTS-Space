package fr.frabous.stellar.api;

import java.util.Objects;

/** Untrusted client intent. Authorization and targeting must be checked on the server. */
public final class LaunchRequest {
    private final AttackId attack;
    private final int dimension;
    private final BlockTarget target;

    public LaunchRequest(AttackId attack, int dimension, BlockTarget target) {
        this.attack = Objects.requireNonNull(attack, "attack");
        this.dimension = dimension;
        this.target = Objects.requireNonNull(target, "target");
    }

    public AttackId getAttack() { return attack; }
    public int getDimension() { return dimension; }
    public BlockTarget getTarget() { return target; }
}
