package fr.frabous.stellar.client;

import org.junit.Test;
import static org.junit.Assert.*;

/** Reusable destroyer choreography, independent of the Asteroid Puller payload. */
public class CarrierFlightTest {
    private static final double STEP = 0.125;

    private static CarrierFlight.Plan plan(double heading, double ax, double ay, double az) {
        return new CarrierFlight.Plan(26, 40, 82, 82, 102, 118, 186, 200, 234, ax, ay, az, heading, 640, 760);
    }

    @Test public void shipLeavesTheApertureBrakesAndHoldsStationOverTheAnchor() {
        CarrierFlight.Plan plan = plan(0, 0, 0, 0);
        assertFalse(CarrierFlight.at(plan, 39.9).visible);
        double previous = plan.travel(40), previousSpeed = plan.speed(40);
        for (double age = 40 + STEP; age <= 82; age += STEP) {
            double travel = plan.travel(age), speed = plan.speed(age);
            assertTrue("Ship reverses during arrival at " + age, travel >= previous);
            assertTrue("Braking is not monotonic at " + age, speed <= previousSpeed + 1.0E-9);
            previous = travel; previousSpeed = speed;
        }
        assertEquals(0, plan.travel(82), 1.0E-9);
        assertEquals(0, plan.speed(82), 1.0E-9);
        for (double age = 82; age < 200; age += STEP) {
            double[] hard = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, age));
            assertEquals("Bay drifted off the anchor at " + age, 0, hard[0], 1.0E-9);
            assertEquals(0, hard[2], 1.0E-9);
        }
        double clear = CarrierFlight.clearTime(plan, -CarrierFlight.STERN, plan.arrivalPlane());
        assertTrue("Stern must clear the aperture well before station: " + clear, clear > 46 && clear < 62);
        assertTrue(plan.speed(40) > 50);
    }

    @Test public void payloadStaysInsideTheHullUntilTheBayOpensAndDropsBelowTheKeel() {
        CarrierFlight.Plan plan = plan(0, 0, 0, 0);
        for (double age = 82; age <= 88; age += STEP) {
            CarrierFlight.State s = CarrierFlight.at(plan, age);
            assertTrue("Cargo hangs below the closed hull at " + age, CarrierFlight.hardPoint(plan, s)[1] > s.y + CarrierFlight.BAY_Y);
        }
        CarrierFlight.State release = CarrierFlight.at(plan, 102);
        assertEquals(CarrierFlight.DROP, release.cradle, 1.0E-6);
        assertEquals(1, release.clamps, 1.0E-9);
        assertEquals(0, CarrierFlight.at(plan, 99).clamps, 1.0E-9);
        assertEquals(1, CarrierFlight.at(plan, 95).bay, 1.0E-9);
        assertEquals(0, CarrierFlight.at(plan, 131).bay, 1.0E-9);
    }

    @Test public void departureAcceleratesThenJumpsThroughItsOwnAperture() {
        CarrierFlight.Plan plan = plan(0, 0, 0, 0);
        assertEquals(0, plan.travel(199.9), 1.0E-9);
        double previous = 0;
        for (double age = 200; age < 240; age += STEP) {
            double travel = plan.travel(age);
            assertTrue(travel >= previous);
            previous = travel;
        }
        CarrierFlight.State before = CarrierFlight.at(plan, 232);
        assertTrue("Bow already inside the departure aperture", before.x + CarrierFlight.BOW < before.departurePlaneX);
        assertTrue(CarrierFlight.at(plan, 233.9).visible);
        assertFalse(CarrierFlight.at(plan, 241).visible);
        assertTrue("Stern passes the departure plane before the hull is hidden",
                plan.travel(240) - CarrierFlight.STERN > plan.departurePlane());
        assertTrue(CarrierFlight.at(plan, 230).engine > 0.9);
    }

    @Test public void anotherPayloadCanFlyADifferentHeadingAndAnchor() {
        CarrierFlight.Plan plan = plan(Math.PI / 2, 12, -4, -30);
        double[] hard = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, 100));
        assertEquals(12, hard[0], 1.0E-9);
        assertEquals(-30, hard[2], 1.0E-9);
        assertTrue(hard[1] > 690 && hard[1] < 720);
        CarrierFlight.State arriving = CarrierFlight.at(plan, 45);
        assertEquals(12, arriving.x - CarrierFlight.BAY_X * plan.hx(), 1.0E-9);
        assertTrue("Heading +90 degrees arrives from +Z, flying toward -Z", arriving.z > plan.stationZ() + 100);
    }
}
