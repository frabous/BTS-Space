package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlackholeTidesTest {
    @Test public void theShowIsHarmlessUntilTheLethalWindowAndStopsWithThePull() {
        for (double distance = 0; distance <= 200; distance += 5) {
            assertEquals(0, BlackholeTides.exposure(BlackholeTides.LETHAL_START_TICKS, distance), 0);
            assertEquals(0, BlackholeTides.exposure(200, distance), 0);
            assertEquals(0, BlackholeTides.exposure(740, distance), 0);
            assertEquals(0, BlackholeTides.exposure(900, distance), 0);
            assertEquals(0, BlackholeTides.pulseDamage(399, distance, 20), 0);
        }
        assertEquals(1, BlackholeTides.exposure(480, 40), 1e-12);
        assertEquals(1, BlackholeTides.exposure(739, 0), 1e-12);
    }

    @Test public void lethalityIsADeepHorizonShellThatFadesContinuously() {
        assertEquals(1, BlackholeTides.lethality(56, 70), 0);
        assertEquals(0, BlackholeTides.lethality(98, 70), 0);
        assertEquals(0, BlackholeTides.lethality(10, 0), 0);
        double previous = 1;
        for (double distance = 0; distance <= 150; distance += 0.25) {
            double value = BlackholeTides.lethality(distance, 70);
            assertTrue(value <= previous && value >= 0);
            previous = value;
        }
    }

    @Test public void fullExposureKillsSmallBodiesQuicklyAndBossesWithinTheWindow() {
        assertTrue(ticksToKill(10) <= 40);
        assertTrue(ticksToKill(20) <= 80);
        assertTrue(ticksToKill(100) <= 160);
        assertTrue(ticksToKill(300) <= 200);
        assertTrue(ticksToKill(1024) <= 220);
        // Damage is never negative, even for a corrupted max-health attribute.
        assertTrue(BlackholeTides.pulseDamage(600, 30, -5) > 0);
    }

    @Test public void horizonGrowsMonotonicallyToItsFullRadius() {
        double previous = 0;
        for (int age = 0; age <= 800; age++) {
            double radius = BlackholeTides.horizonRadius(age, 70);
            assertTrue(radius >= previous && radius <= 70);
            previous = radius;
        }
        assertEquals(70 * 0.12, BlackholeTides.horizonRadius(60, 70), 1e-12);
        assertEquals(70, BlackholeTides.horizonRadius(440, 70), 0);
    }

    private static int ticksToKill(float health) {
        float remaining = health;
        int ticks = 0;
        while (remaining > 0) {
            remaining -= BlackholeTides.pulseDamage(500 + ticks, 20, health);
            ticks += BlackholeTides.DAMAGE_INTERVAL_TICKS;
        }
        return ticks;
    }
}
