package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class RailgunGeometryTest {
    private final BlockTarget target = new BlockTarget(-128, 64, 256);

    @Test public void radiusUsesExactEntityPositionAndVisualCenter() {
        assertTrue(AttackGeometry.railgunContains(target, 72.5, 64, 256.5));
        assertFalse(AttackGeometry.railgunContains(target, 72.501, 64, 256.5));
        assertTrue(AttackGeometry.railgunContains(target, -327.5, 64, 256.5));
        assertFalse(AttackGeometry.railgunContains(target, -327.501, 64, 256.5));
        assertFalse(AttackGeometry.railgunContains(target, 72.5, 64, 456.5));
    }

    @Test public void columnIncludesUndergroundAndAirborneEntities() {
        assertTrue(AttackGeometry.railgunContains(target, -127.5, 0, 256.5));
        assertTrue(AttackGeometry.railgunContains(target, -127.5, 512, 256.5));
        assertFalse(AttackGeometry.railgunContains(target, -127.5, -0.01, 256.5));
        assertFalse(AttackGeometry.railgunContains(target, -127.5, 512.01, 256.5));
    }
}
