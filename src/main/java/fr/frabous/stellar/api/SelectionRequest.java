package fr.frabous.stellar.api;

import java.util.Objects;

public final class SelectionRequest {
    private final AttackId attack;
    public SelectionRequest(AttackId attack) { this.attack = Objects.requireNonNull(attack, "attack"); }
    public AttackId getAttack() { return attack; }
}
