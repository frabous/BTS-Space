package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class CarrierGunneryRulesTest {
    @Test public void identityAndExactlyThirtySecondsAfterBoarding() {
        assertEquals(7, AttackId.CARRIER_GUNNERY.getWireId());
        assertEquals("SS-03B", AttackId.CARRIER_GUNNERY.getDisplayId());
        assertEquals(AttackFamily.CARRIER, AttackId.CARRIER_GUNNERY.getFamily());
        assertEquals(600, CarrierGunneryRules.RETURN - CarrierGunneryRules.BOARD);
        assertFalse(CarrierGunneryRules.controls(79));
        assertTrue(CarrierGunneryRules.controls(80));
        assertTrue(CarrierGunneryRules.controls(679));
        assertFalse(CarrierGunneryRules.controls(680));
        assertFalse(CarrierGunneryRules.controls(Long.MAX_VALUE));
        assertEquals(CarrierGunneryRules.END, LaunchTimeline.totalTicks(AttackId.CARRIER_GUNNERY));
    }
    @Test public void packetSpamCannotAccelerateGunsOrExtendControl() {
        assertFalse(CarrierGunneryRules.canFire(79, 0));
        assertTrue(CarrierGunneryRules.canFire(80, 0));
        assertFalse(CarrierGunneryRules.canFire(80, 80));
        assertFalse(CarrierGunneryRules.canFire(87, 80));
        assertTrue(CarrierGunneryRules.canFire(88, 80));
        assertTrue(CarrierGunneryRules.canFire(679, 671));
        assertFalse(CarrierGunneryRules.canFire(680, 671));
    }
    @Test public void mouseLookAcceptsAllBearingsButRejectsNonfiniteAndImpossibleAngles() {
        for (float yaw : new float[]{-360, -180, 0, 180, 360})
            for (float pitch : new float[]{-90, 0, 90}) assertTrue(CarrierGunneryRules.validAim(yaw, pitch));
        for (float invalid : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
            assertFalse(CarrierGunneryRules.validAim(invalid, 0));
            assertFalse(CarrierGunneryRules.validAim(0, invalid));
        }
        assertFalse(CarrierGunneryRules.validAim(0, 90.1F));
        assertFalse(CarrierGunneryRules.validAim(0, -90.1F));
    }
}
