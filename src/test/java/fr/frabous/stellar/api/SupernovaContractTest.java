package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupernovaContractTest {
    @Test public void appendOnlyIdentityDoesNotReuseRemovedAttackIds() {
        assertEquals(6, AttackId.SUPERNOVA.getWireId());
        assertEquals(AttackId.SUPERNOVA, AttackId.fromWireId(6));
        assertEquals("SS-04", AttackId.SUPERNOVA.getDisplayId());
        assertEquals("supernova", AttackId.SUPERNOVA.getResourcePath());
        assertEquals(1, AttackId.RAILGUN.getWireId());
        assertEquals(2, AttackId.BLACKHOLE.getWireId());
        assertEquals(5, AttackId.ASTEROID_PULLER.getWireId());
    }

    @Test public void authoritativeDamageWindowIsExactlyTwentyToTwentyFourSeconds() {
        int[] starts = {0, 60, 180, 320, 400, 480, 640};
        for (LaunchPhase phase : LaunchPhase.values())
            assertEquals(starts[phase.getWireId()], LaunchTimeline.phaseStartTicks(AttackId.SUPERNOVA, phase));
        assertEquals(LaunchPhase.FIRING, LaunchTimeline.phaseAt(AttackId.SUPERNOVA, 399));
        assertEquals(LaunchPhase.IMPACT, LaunchTimeline.phaseAt(AttackId.SUPERNOVA, 400));
        assertEquals(LaunchPhase.IMPACT, LaunchTimeline.phaseAt(AttackId.SUPERNOVA, 479));
        assertEquals(LaunchPhase.AFTERMATH, LaunchTimeline.phaseAt(AttackId.SUPERNOVA, 480));
        assertEquals(LaunchPhase.COMPLETE, LaunchTimeline.phaseAt(AttackId.SUPERNOVA, 640));
        assertEquals(SupernovaTiming.STAR, starts[1]);
        assertEquals(SupernovaTiming.COLLAPSE, starts[2]);
        assertEquals(SupernovaTiming.DETONATE, starts[3]);
        assertEquals(SupernovaTiming.IMPACT, starts[4]);
        assertEquals(SupernovaTiming.AFTERMATH, starts[5]);
        assertEquals(SupernovaTiming.END, starts[6]);
        assertTrue(SupernovaTiming.WORLD_RETURN > SupernovaTiming.DETONATE);
        assertTrue(SupernovaTiming.WORLD_RETURN < SupernovaTiming.IMPACT);
        assertEquals(400F, 80F / AttackParameters.RAILGUN_DAMAGE_INTERVAL_TICKS
                * AttackParameters.RAILGUN_DAMAGE_PER_PULSE, 0);
    }

    @Test public void existingAttackDurationsAndGeometryAreUnchanged() {
        assertEquals(514, LaunchTimeline.totalTicks(AttackId.RAILGUN));
        assertEquals(800, LaunchTimeline.totalTicks(AttackId.BLACKHOLE));
        assertEquals(AsteroidPullerTiming.END, LaunchTimeline.totalTicks(AttackId.ASTEROID_PULLER));
        BlockTarget target = new BlockTarget(-123, 255, 45);
        assertTrue(AttackGeometry.railgunContains(target, -122.5+200, 512, 45.5));
        assertFalse(AttackGeometry.railgunContains(target, -122.5+200.01, 512, 45.5));
        assertFalse(AttackGeometry.railgunContains(target, -122.5, 512.01, 45.5));
        assertFalse(AttackGeometry.railgunContains(target, -122.5, -0.01, 45.5));
    }
}
