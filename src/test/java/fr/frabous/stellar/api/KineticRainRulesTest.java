package fr.frabous.stellar.api;

import static fr.frabous.stellar.api.KineticRainRules.*;
import static org.junit.Assert.*;

import java.util.UUID;
import org.junit.Test;

public class KineticRainRulesTest {
    private static LaunchState launch(long seed) {
        BlockTarget target = new BlockTarget(1200, 70, -340);
        return new LaunchState(new UUID(0, 8), AttackId.KINETIC_RAIN, 0, target, target, seed, 5000, LaunchPhase.MARKING);
    }

    @Test public void identityAndTimeline() {
        assertEquals(8, AttackId.KINETIC_RAIN.getWireId());
        assertEquals(AttackId.KINETIC_RAIN, AttackId.fromWireId(8));
        assertEquals("SS-03C", AttackId.KINETIC_RAIN.getDisplayId());
        assertEquals(AttackFamily.CARRIER, AttackId.KINETIC_RAIN.getFamily());
        assertTrue(AttackId.KINETIC_RAIN.carriesDeployment());
        assertEquals(END, LaunchTimeline.totalTicks(AttackId.KINETIC_RAIN));
        assertEquals(FIRST_IMPACT, LaunchTimeline.phaseStartTicks(AttackId.KINETIC_RAIN, LaunchPhase.FIRING));
        assertEquals(HAMMER_IMPACT, LaunchTimeline.phaseStartTicks(AttackId.KINETIC_RAIN, LaunchPhase.IMPACT));
        assertTrue(BAY_OPEN > STATION && release(0) > BAY_OPEN + 24 && impact(COUNT - 1) < HAMMER_RELEASE);
        assertTrue(HAMMER_RELEASE < BAY_CLOSE && BAY_CLOSE < HAMMER_IMPACT && HAMMER_IMPACT < SPOOL);
        assertTrue(SPOOL < DEPART && DEPART < JUMP && JUMP < END);
    }

    @Test public void everyRodLandsOnceOnItsSalvoBeat() {
        int[] seen = new int[COUNT];
        for (long age = -10; age <= END; age++) {
            int first = salvo(age);
            if (first < 0) continue;
            assertEquals(0, first % ARMS);
            for (int rod = first; rod < first + ARMS; rod++) {
                assertEquals(age, impact(rod));
                seen[rod]++;
            }
        }
        for (int rod = 0; rod < COUNT; rod++) assertEquals("rod " + rod, 1, seen[rod]);
        assertEquals(FIRST_IMPACT, impact(0));
        assertEquals(FIRST_IMPACT + (BEATS - 1) * BEAT, impact(COUNT - 1));
        assertEquals(FLIGHT, impact(5) - release(5));
    }

    @Test public void barrageWalksInwardAndStaysInsideItsReach() {
        for (long seed : new long[] {0, 1, -7, 0x5DEECE66DL, Long.MAX_VALUE}) {
            LaunchState state = launch(seed);
            double cx = state.getTarget().getX() + 0.5, cz = state.getTarget().getZ() + 0.5;
            double previous = Double.MAX_VALUE;
            for (int beat = 0; beat < BEATS; beat++) {
                double mean = 0;
                for (int arm = 0; arm < ARMS; arm++) {
                    double[] m = mark(state, beat * ARMS + arm);
                    double radius = Math.hypot(m[0] - cx, m[1] - cz);
                    assertTrue(radius + ROD_RADIUS <= REACH);
                    assertTrue(radius >= INNER_RADIUS - JITTER);
                    mean += radius / ARMS;
                }
                if (beat % 4 == 0) { assertTrue(mean < previous); previous = mean; }
            }
            assertArrayEquals(mark(state, 37), mark(launch(seed), 37), 0);
        }
        assertNotEquals(mark(launch(1), 0)[0], mark(launch(2), 0)[0], 1.0E-6);
    }

    @Test public void falloffKeepsTheRimDangerous() {
        assertEquals(1, falloff(0, ROD_RADIUS), 1.0E-9);
        assertEquals(1 - EDGE, falloff(ROD_RADIUS, ROD_RADIUS), 1.0E-9);
        assertEquals(0, falloff(ROD_RADIUS + 0.01, ROD_RADIUS), 0);
        assertTrue(shipY(new BlockTarget(0, 64, 0)) >= MIN_SHIP_Y);
        assertEquals(455, shipY(new BlockTarget(0, 255, 0)), 0);
        for (int key = 0; key < 1000; key++) {
            double u = unit(42, key);
            assertTrue(u >= 0 && u < 1);
        }
    }
}
