package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CarrierGunnerySceneTest {
    private static final LaunchState LAUNCH = new LaunchState(new UUID(0, 7), AttackId.CARRIER_GUNNERY, 0,
            new BlockTarget(20, 64, 20), new BlockTarget(-81, 71, 243), 0, 100, LaunchPhase.MARKING);

    @Test public void carrierHoldsStationWithTheGondolaOverTheCasterForEveryControlTick() {
        CarrierFlight.Plan plan = CarrierGunneryScene.plan(LAUNCH);
        BroadsideBatteries.Frame frame = BroadsideBatteries.frame(LAUNCH);
        assertEquals(frame.heading, plan.heading, 1.0E-12);
        for (int age = CarrierGunneryRules.BOARD; age < CarrierGunneryRules.RETURN; age++) {
            CarrierFlight.State state = CarrierFlight.at(plan, age);
            assertTrue(state.visible);
            assertEquals(frame.x, state.x, 1.0E-8);
            assertEquals(frame.z, state.z, 1.0E-8);
            assertEquals(CarrierGunneryRules.SHIP_Y, state.y, 1.0E-8);
            assertEquals(0, state.bay, 1.0E-8);
        }
        double[] seat = frame.world(BroadsideBatteries.GONDOLA_X, BroadsideBatteries.GONDOLA_FLOOR, 0);
        assertEquals(-80.5, seat[0], 1.0E-8);
        assertEquals(243.5, seat[2], 1.0E-8);
    }

    @Test public void departureNeverStartsBeforeThePlayerReturns() {
        CarrierFlight.Plan plan = CarrierGunneryScene.plan(LAUNCH);
        assertTrue(plan.station <= CarrierGunneryRules.BOARD);
        assertTrue(plan.spool >= CarrierGunneryRules.RETURN);
        assertTrue(plan.depart > CarrierGunneryRules.RETURN);
        assertFalse(CarrierFlight.at(plan, CarrierGunneryRules.END).visible);
    }

    @Test public void transferBeamLightsOnlyAroundBoardingAndReturn() {
        assertEquals(1, CarrierGunneryScene.beam(CarrierGunneryRules.BOARD), 1.0E-12);
        assertEquals(1, CarrierGunneryScene.beam(CarrierGunneryRules.RETURN + 4), 1.0E-12);
        assertEquals(0, CarrierGunneryScene.beam(CarrierGunneryRules.BOARD - CarrierGunneryRules.BEAM - 1), 1.0E-12);
        assertEquals(0, CarrierGunneryScene.beam(CarrierGunneryRules.BOARD + 100), 1.0E-12);
        assertEquals(0, CarrierGunneryScene.beam(CarrierGunneryRules.RETURN + CarrierGunneryRules.BEAM + 1), 1.0E-12);
    }

    @Test public void barrelsKickBackWithinATickAndReturnToBattery() {
        assertEquals(0, BroadsideFireControl.recoil(-1), 0);
        assertEquals(2.6, BroadsideFireControl.recoil(0.6), 1.0E-9);
        assertTrue(BroadsideFireControl.recoil(4) > 0 && BroadsideFireControl.recoil(4) < 2.6);
        assertEquals(0, BroadsideFireControl.recoil(15), 0);
    }

    @Test public void confirmedShellBearingPointsFromMuzzleToImpact() {
        CarrierShot shot = new CarrierShot(LAUNCH.getLaunchId(), 0, 2, 0, CarrierShot.TERRAIN, 100, 105, 115,
                0, 240, 0, 100, 140, -100);
        double[] bearing = BroadsideFireControl.bearing(shot);
        double[] d = BroadsideBatteries.direction(bearing[0], bearing[1]);
        assertEquals(1 / Math.sqrt(3), d[0], 1.0E-9);
        assertEquals(-1 / Math.sqrt(3), d[1], 1.0E-9);
        assertEquals(-1 / Math.sqrt(3), d[2], 1.0E-9);
    }
}
