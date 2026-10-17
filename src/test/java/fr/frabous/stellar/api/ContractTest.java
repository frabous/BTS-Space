package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContractTest {
    @Test
    public void phaseBoundariesAndTerminalStateAreConsistent() {
        for (AttackId attack : AttackId.values()) {
            int elapsed = 0;
            for (LaunchPhase phase : LaunchPhase.values()) {
                assertEquals(elapsed, LaunchTimeline.phaseStartTicks(attack, phase));
                assertEquals(phase, LaunchTimeline.phaseAt(attack, elapsed));
                int duration = LaunchTimeline.durationTicks(attack, phase);
                if (duration > 0) assertEquals(phase, LaunchTimeline.phaseAt(attack, elapsed + duration - 1));
                elapsed += duration;
            }
            assertEquals(LaunchPhase.COMPLETE, LaunchTimeline.phaseAt(attack, Long.MAX_VALUE));
            assertEquals(LaunchPhase.MARKING, LaunchTimeline.phaseAt(attack, -1));
        }
    }

    @Test
    public void idsAndOfficialDimensionsRemainStable() {
        assertEquals(1, AttackId.RAILGUN.getWireId());
        assertArrayEquals(new AttackId[]{AttackId.RAILGUN, AttackId.BLACKHOLE, AttackId.ASTEROID_PULLER, AttackId.SUPERNOVA, AttackId.CARRIER_GUNNERY, AttackId.KINETIC_RAIN, AttackId.GRAVITY_INVERSION}, AttackId.values());
        assertEquals(AttackId.BLACKHOLE, AttackId.RAILGUN.next());
        assertEquals(AttackId.ASTEROID_PULLER, AttackId.BLACKHOLE.next());
        assertEquals(AttackId.SUPERNOVA, AttackId.ASTEROID_PULLER.next());
        assertEquals(AttackId.CARRIER_GUNNERY, AttackId.SUPERNOVA.next());
        assertEquals(AttackId.KINETIC_RAIN, AttackId.CARRIER_GUNNERY.next());
        assertEquals(AttackId.GRAVITY_INVERSION, AttackId.KINETIC_RAIN.next());
        assertEquals(AttackId.RAILGUN, AttackId.GRAVITY_INVERSION.next());
        assertEquals(200, AttackParameters.RAILGUN_RADIUS_BLOCKS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownAttackIsRejected() { AttackId.fromWireId(0); }

    @Test(expected = IllegalArgumentException.class)
    public void removedGungnirIsRejected() { AttackId.fromWireId(3); }

    @Test(expected = IllegalArgumentException.class)
    public void removedSevenStarsIsRejected() { AttackId.fromWireId(4); }

    @Test(expected = IllegalArgumentException.class)
    public void targetCannotUseSpaceSceneCoordinates() { new BlockTarget(0, 256, 0); }

    @Test(expected = IllegalArgumentException.class)
    public void integerOverflowCannotBypassBounds() { new BlockTarget(Integer.MIN_VALUE, 64, 0); }
}
