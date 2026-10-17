package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.KineticRainRules.*;

import fr.frabous.stellar.api.*;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KineticRainSceneTest {
    private static final BlockTarget TARGET = new BlockTarget(-300, 66, 512);
    private static final LaunchState LAUNCH = new LaunchState(new UUID(0, 8), AttackId.KINETIC_RAIN, 0,
            TARGET, TARGET, 1234567, 100, LaunchPhase.MARKING);

    @Test public void bayHoldsOverTheAimThroughTheWholeBarrageAndHammerDrop() {
        CarrierFlight.Plan plan = KineticRainScene.plan(LAUNCH);
        for (int age = STATION; age <= HAMMER_RELEASE; age++) {
            CarrierFlight.State s = CarrierFlight.at(plan, age);
            double[] bay = CarrierFlight.hardPoint(plan, s);
            assertTrue(s.visible);
            assertEquals(TARGET.getX() + 0.5, bay[0], 1.0E-8);
            assertEquals(TARGET.getZ() + 0.5, bay[2], 1.0E-8);
            assertEquals(shipY(TARGET), s.y, 1.0E-8);
        }
        assertEquals(1, CarrierFlight.at(plan, HAMMER_RELEASE).clamps, 1.0E-9);
        assertEquals(0, CarrierFlight.at(plan, HAMMER_RELEASE - 3).clamps, 1.0E-9);
        assertFalse(CarrierFlight.at(plan, END).visible);
    }

    @Test public void railsAreOutForEveryRodAndStowedOtherwise() {
        assertEquals(0, KineticRainScene.deploy(BAY_OPEN), 0);
        for (int rod = 0; rod < COUNT; rod++) assertEquals(1, KineticRainScene.deploy(release(rod)), 1.0E-12);
        assertEquals(0, KineticRainScene.deploy(HAMMER_RELEASE), 0);
        assertEquals(1, KineticRainScene.heat(release(9)), 1.0E-12);
        assertEquals(0, KineticRainScene.heat(BAY_OPEN), 0);
        double[][] muzzles = KineticRainScene.muzzles(KineticRainScene.plan(LAUNCH));
        for (double[] muzzle : muzzles) {
            assertTrue(Math.hypot(muzzle[0] - TARGET.getX() - 0.5, muzzle[2] - TARGET.getZ() - 0.5) < 17);
            assertTrue(muzzle[1] < shipY(TARGET) + CarrierFlight.BAY_Y);
        }
    }

    @Test public void rodsLeaveTheMuzzleAndDiveOntoTheirMark() {
        double[] muzzle = {10, 250, -4};
        double[] start = KineticRainScene.path(muzzle, 150, 70, 90, 0);
        double[] end = KineticRainScene.path(muzzle, 150, 70, 90, 1);
        assertArrayEquals(muzzle, start, 1.0E-9);
        assertArrayEquals(new double[] {150, 70, 90}, end, 1.0E-9);
        double[] last = KineticRainScene.path(muzzle, 150, 70, 90, 0.97);
        // Near-vertical terminal dive.
        assertTrue(last[1] - end[1] > 3 * Math.hypot(last[0] - end[0], last[2] - end[2]));
        double previous = Double.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            double y = KineticRainScene.path(muzzle, 150, 70, 90, i / 100.0)[1];
            assertTrue(y <= previous);
            previous = y;
        }
        assertEquals(0, KineticRainScene.flight(0), 0);
        assertEquals(1, KineticRainScene.flight(FLIGHT), 0);
        assertTrue(KineticRainScene.flight(FLIGHT / 2.0) < 0.5);
    }
}
