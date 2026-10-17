package fr.frabous.stellar.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static fr.frabous.stellar.api.AsteroidPullerTiming.*;
import static org.junit.Assert.*;

public class AsteroidPullerContractTest {
    @Test public void identityStaysCompatibleWithTheFormerAsteroideAttract() {
        assertEquals(5, AttackId.ASTEROID_PULLER.getWireId());
        assertEquals(AttackId.ASTEROID_PULLER, AttackId.fromWireId(5));
        assertEquals("SS-03A", AttackId.ASTEROID_PULLER.getDisplayId());
        assertEquals("moon_attract", AttackId.ASTEROID_PULLER.getResourcePath());
        assertEquals("Asteroid Puller", AttackId.ASTEROID_PULLER.getDisplayName());
        Set<Integer> wireIds = new HashSet<>();
        Set<String> displayIds = new HashSet<>(), resourcePaths = new HashSet<>();
        for (AttackId attack : AttackId.values()) {
            assertTrue(wireIds.add(attack.getWireId()));
            assertTrue(displayIds.add(attack.getDisplayId()));
            assertTrue(resourcePaths.add(attack.getResourcePath()));
            assertEquals(attack.getFamily() == AttackFamily.CARRIER, attack.carriesDeployment());
            assertEquals(attack.carriesDeployment(), AttackId.carriesDeployment(attack.getWireId()));
        }
        assertFalse(AttackId.carriesDeployment(3));
    }

    @Test public void carrierIsAFamilyThatFutureShipStrikesCanJoin() {
        assertEquals(AttackFamily.CARRIER, AttackId.ASTEROID_PULLER.getFamily());
        assertEquals("SS-03", AttackFamily.CARRIER.getDisplayId());
        assertEquals(Arrays.asList(AttackId.ASTEROID_PULLER, AttackId.CARRIER_GUNNERY, AttackId.KINETIC_RAIN),
                AttackFamily.CARRIER.attacks());
        assertEquals(AttackId.ASTEROID_PULLER, AttackFamily.CARRIER.first());
        assertEquals(AttackId.ASTEROID_PULLER, AttackFamily.CARRIER.next(AttackId.RAILGUN));
        assertEquals(AttackId.CARRIER_GUNNERY, AttackFamily.CARRIER.next(AttackId.ASTEROID_PULLER));
        assertEquals(AttackId.KINETIC_RAIN, AttackFamily.CARRIER.next(AttackId.CARRIER_GUNNERY));
        assertEquals(AttackId.ASTEROID_PULLER, AttackFamily.CARRIER.next(AttackId.KINETIC_RAIN));
        Set<String> rows = new HashSet<>();
        int members = 0;
        for (AttackFamily family : AttackFamily.values()) {
            assertFalse(family.attacks().isEmpty());
            assertTrue(rows.add(family.getDisplayId()));
            for (AttackId attack : family.attacks()) {
                assertEquals(family, attack.getFamily());
                assertTrue(attack.getDisplayId().startsWith(family.getDisplayId()));
            }
            members += family.attacks().size();
        }
        assertEquals(AttackId.values().length, members);
    }

    @Test public void serverPhasesFollowTheFilmAndKeepAFourSecondImpact() {
        int[] durations = {100, 230, 40, 200, 80, 210, 0};
        int[] starts = {0, 100, CHARGE, FIRE, IMPACT, AFTERMATH, END};
        for (LaunchPhase phase : LaunchPhase.values()) {
            int start = starts[phase.getWireId()], duration = durations[phase.getWireId()];
            assertEquals(start, LaunchTimeline.phaseStartTicks(AttackId.ASTEROID_PULLER, phase));
            assertEquals(duration, LaunchTimeline.durationTicks(AttackId.ASTEROID_PULLER, phase));
        }
        assertEquals(LaunchPhase.FIRING, LaunchTimeline.phaseAt(AttackId.ASTEROID_PULLER, LOCK));
        assertEquals(LaunchPhase.FIRING, LaunchTimeline.phaseAt(AttackId.ASTEROID_PULLER, IMPACT - 1));
        assertEquals(LaunchPhase.IMPACT, LaunchTimeline.phaseAt(AttackId.ASTEROID_PULLER, IMPACT));
        assertEquals(4, LaunchTimeline.durationTicks(AttackId.ASTEROID_PULLER, LaunchPhase.IMPACT) / 20);
        assertEquals(43.0, LaunchTimeline.totalTicks(AttackId.ASTEROID_PULLER) / 20.0, 0);
    }

    @Test public void cuesRunInStoryOrder() {
        int[] order = {BEACON, UPLINK, SPACE_CUT, WARP_OPEN, WARP_EXIT, WARP_CLEAR, WARP_CLOSE, STATION, RELEASE,
                WORLD_CUT, RETRO, LAND, ANCHOR, SHIP_SPOOL, UNFOLD, SHIP_DEPART, SHIP_JUMP, APPEAR, TRACK, CHARGE,
                MAXIMUM_CHARGE, FIRE, LOCK, BEND_END, ATMOSPHERE, SEVER, SILENCE, IMPACT, SHOCKWAVE, DEBRIS,
                AFTERMATH, FADE, END};
        for (int i = 1; i < order.length; i++) assertTrue("Cue " + i + " out of order", order[i] > order[i - 1]);
        assertEquals(STATION, BAY_OPEN);
    }
}
