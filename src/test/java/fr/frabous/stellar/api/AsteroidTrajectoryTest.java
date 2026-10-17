package fr.frabous.stellar.api;

import org.junit.Test;
import static fr.frabous.stellar.api.AsteroidPullerTiming.*;
import static org.junit.Assert.*;

/** Physical coherence of the flyby, capture, deflection and dive. */
public class AsteroidTrajectoryTest {
    private static final double STEP = 0.125;

    @Test public void freeFlightIsAStraightHighPassAtConstantSpeed() {
        double[] heading = AsteroidTrajectory.velocity(APPEAR);
        for (double age = 0; age <= LOCK; age += STEP) {
            assertArrayEquals(heading, AsteroidTrajectory.velocity(age), 1.0E-9);
            assertEquals(AsteroidTrajectory.CRUISE_SPEED, AsteroidTrajectory.speed(age), 1.0E-9);
            assertTrue("Flyby must stay above 1000 blocks", AsteroidTrajectory.y(age) > 1000);
        }
        double[] start = AsteroidTrajectory.position(APPEAR), lock = AsteroidTrajectory.position(LOCK);
        double appear = Math.sqrt(start[0] * start[0] + start[1] * start[1] + start[2] * start[2]);
        assertTrue("Appears far but inside the 2.9 km film frustum: " + appear, appear > 2000 && appear < 2900);
        assertTrue("It passes over the strike zone from east to west", start[0] > 1500 && lock[0] < 0);
        assertArrayEquals(AsteroidTrajectory.interceptPoint(), lock, 1.0E-9);
    }

    @Test public void positionAndVelocityAreContinuousAndConsistent() {
        for (double age = APPEAR; age < IMPACT; age += STEP) {
            double[] v = AsteroidTrajectory.velocity(age), a = AsteroidTrajectory.position(age - 0.001),
                    b = AsteroidTrajectory.position(age + 0.001);
            for (int axis = 0; axis < 3; axis++) assertEquals("Velocity mismatch at " + age, (b[axis] - a[axis]) / 0.002, v[axis], 1.0E-3);
        }
        for (double joint : new double[] {LOCK, BEND_END}) {
            assertArrayEquals(AsteroidTrajectory.position(joint - 1.0E-7), AsteroidTrajectory.position(joint + 1.0E-7), 1.0E-4);
            assertArrayEquals("Velocity kink at " + joint, AsteroidTrajectory.velocity(joint - 1.0E-7),
                    AsteroidTrajectory.velocity(joint + 1.0E-7), 1.0E-4);
        }
    }

    @Test public void lockBendsThePathTowardTheLanceAndItNeverClimbs() {
        double previous = AsteroidTrajectory.y(LOCK);
        for (double age = LOCK + STEP; age <= IMPACT; age += STEP) {
            double y = AsteroidTrajectory.y(age);
            assertTrue("Asteroid climbs at " + age, y <= previous + 1.0E-9);
            assertTrue("Underside meets the ground before impact at " + age, age == IMPACT || y > AsteroidTrajectory.CONTACT);
            previous = y;
            if (age >= IMPACT - 0.5) continue;
            double[] p = AsteroidTrajectory.position(age), before = AsteroidTrajectory.velocity(age - 0.25),
                    after = AsteroidTrajectory.velocity(age + 0.25);
            double ax = after[0] - before[0], ay = after[1] - before[1], az = after[2] - before[2];
            double toward = -(ax * p[0] + ay * (p[1] - 20) + az * p[2]);
            assertTrue("Tether acceleration points away from the lance at " + age, toward > 0);
        }
        assertTrue("Heading reverses through the hook", AsteroidTrajectory.deflection(BEND_END) > 120);
        assertTrue("Swing slows the body before the dive", AsteroidTrajectory.speed(460) < 6);
    }

    @Test public void diveAcceleratesIntoAContactAtTheAnchor() {
        double previous = AsteroidTrajectory.speed(BEND_END);
        for (double age = BEND_END + STEP; age <= IMPACT; age += STEP) {
            double speed = AsteroidTrajectory.speed(age);
            assertTrue("Dive decelerates at " + age, speed >= previous - 1.0E-9);
            previous = speed;
        }
        assertArrayEquals(new double[] {0, AsteroidTrajectory.CONTACT, 0}, AsteroidTrajectory.position(IMPACT), 1.0E-9);
        assertEquals(AsteroidTrajectory.IMPACT_SPEED, AsteroidTrajectory.speed(IMPACT), 1.0E-9);
        double[] dive = AsteroidTrajectory.diveDirection();
        double angle = Math.toDegrees(Math.asin(-dive[1]));
        assertTrue("Steep, readable dive: " + angle, angle > 35 && angle < 60);
    }

    @Test public void heatingBuildsOnlyDuringTheDiveAndSpinIsDampedByTheLock() {
        assertTrue(AsteroidTrajectory.entryHeat(BEND_END - 40) < 0.05);
        double previous = 0;
        for (double age = ATMOSPHERE; age < IMPACT; age += STEP) {
            double heat = AsteroidTrajectory.entryHeat(age);
            assertTrue("Entry heat drops at " + age, heat >= previous - 1.0E-9);
            previous = heat;
        }
        assertTrue(AsteroidTrajectory.entryHeat(IMPACT - 2) > 0.85);
        double cruise = AsteroidTrajectory.spin(LOCK) - AsteroidTrajectory.spin(LOCK - 1);
        double held = AsteroidTrajectory.spin(LOCK + 101) - AsteroidTrajectory.spin(LOCK + 100);
        assertTrue(held < cruise * 0.05);
    }
}
