package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlackholeContractTest {
    @Test public void identityAndDimensionsAreTheAgreedContract() {
        assertEquals(AttackId.BLACKHOLE, AttackId.fromWireId(2));
        assertEquals("SS-02", AttackId.BLACKHOLE.getDisplayId());
        assertEquals("blackhole", AttackId.BLACKHOLE.getResourcePath());
        assertEquals(1000, AttackParameters.BLACKHOLE_RADIUS_BLOCKS);
        assertEquals(300, AttackParameters.BLACKHOLE_SPACE_Y);
        assertEquals(420, AttackParameters.BLACKHOLE_CENTER_Y);
        assertEquals(70, AttackParameters.BLACKHOLE_HORIZON_RADIUS);
    }

    @Test public void timelineHasExactDurationsAndExclusivePullEnd() {
        int[] durations = {60, 100, 140, 240, 200, 60, 0};
        int[] starts = {0, 60, 160, 300, 540, 740, 800};
        for (LaunchPhase phase : LaunchPhase.values()) {
            assertEquals(durations[phase.getWireId()], LaunchTimeline.durationTicks(AttackId.BLACKHOLE, phase));
            assertEquals(starts[phase.getWireId()], LaunchTimeline.phaseStartTicks(AttackId.BLACKHOLE, phase));
        }
        assertEquals(800, LaunchTimeline.totalTicks(AttackId.BLACKHOLE));
        assertFalse(LaunchTimeline.blackholePullActive(-1));
        assertFalse(LaunchTimeline.blackholePullActive(59));
        assertTrue(LaunchTimeline.blackholePullActive(60));
        assertTrue(LaunchTimeline.blackholePullActive(739));
        assertFalse(LaunchTimeline.blackholePullActive(740));
        assertFalse(LaunchTimeline.blackholePullActive(Long.MAX_VALUE));
        assertEquals(514, LaunchTimeline.totalTicks(AttackId.RAILGUN));
    }

    @Test public void influenceIsAnInclusiveHorizontalCircleAndSpaceUsesRealAltitude() {
        BlockTarget target = new BlockTarget(-40, 3, 25);
        double x = -39.5, z = 25.5;
        assertTrue(AttackGeometry.blackholeContains(target, x + 1000, z));
        assertTrue(AttackGeometry.blackholeContains(target, x + 600, z - 800));
        assertFalse(AttackGeometry.blackholeContains(target, x + 1000.001, z));
        assertFalse(AttackGeometry.blackholeContains(target, x + 800, z + 800));
        assertFalse(AttackGeometry.blackholeSpaceContains(target, x, 299.999, z));
        assertTrue(AttackGeometry.blackholeSpaceContains(target, x, 300, z));
        assertTrue(AttackGeometry.blackholeSpaceContains(target, x, 10000, z));
        assertFalse(AttackGeometry.blackholeSpaceContains(target, x + 1001, 420, z));
    }
}
