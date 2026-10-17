package fr.frabous.stellar.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote menu rows. A family groups strikes that share one delivery platform. */
public enum AttackFamily {
    ORBITAL("SS-01", "RAILGUN", "ORBITAL / PRECISION BEAM"),
    GRAVITY("SS-02", "BLACKHOLE", "GRAVITY / ACCRETION FIELD"),
    /** Destroyer-delivered payloads. New ship strikes join this family with their own wire id. */
    CARRIER("SS-03", "CARRIER OPS", "DESTROYER / PAYLOAD DROP"),
    STELLAR("SS-04", "SUPERNOVA", "STELLAR / GLOBAL IRRADIATION"),
    INVERSION("SS-05", "GRAVITY INVERSION", "GRAVITY / AXIS COLLAPSE");

    private final String displayId;
    private final String title;
    private final String subtitle;
    private volatile List<AttackId> attacks;

    AttackFamily(String displayId, String title, String subtitle) {
        this.displayId = displayId;
        this.title = title;
        this.subtitle = subtitle;
    }

    public String getDisplayId() { return displayId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }

    /** Members in declaration order; the first member is the family default. */
    public List<AttackId> attacks() {
        List<AttackId> members = attacks;
        if (members == null) {
            List<AttackId> found = new ArrayList<>();
            for (AttackId attack : AttackId.values()) if (attack.getFamily() == this) found.add(attack);
            attacks = members = Collections.unmodifiableList(found);
        }
        return members;
    }

    public AttackId first() { return attacks().get(0); }

    /** Keeps a member selected, or cycles to the next member when it already is. */
    public AttackId next(AttackId current) {
        List<AttackId> members = attacks();
        int index = members.indexOf(current);
        return index < 0 ? members.get(0) : members.get((index + 1) % members.size());
    }
}
