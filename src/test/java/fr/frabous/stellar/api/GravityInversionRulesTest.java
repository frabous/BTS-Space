package fr.frabous.stellar.api;

import static fr.frabous.stellar.api.GravityInversionRules.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class GravityInversionRulesTest {
    private static final long SEED = 0x5EED_05L;

    @Test public void identityAndTimeline() {
        assertEquals(9, AttackId.GRAVITY_INVERSION.getWireId());
        assertEquals(AttackId.GRAVITY_INVERSION, AttackId.fromWireId(9));
        assertEquals("SS-05", AttackId.GRAVITY_INVERSION.getDisplayId());
        assertEquals(AttackFamily.INVERSION, AttackId.GRAVITY_INVERSION.getFamily());
        assertFalse(AttackId.GRAVITY_INVERSION.carriesDeployment());
        assertEquals(19 * 20, END);
        assertEquals(END, LaunchTimeline.totalTicks(AttackId.GRAVITY_INVERSION));
        assertEquals(ASCENSION, LaunchTimeline.phaseStartTicks(AttackId.GRAVITY_INVERSION, LaunchPhase.ASCENT));
        assertEquals(ROTATION, LaunchTimeline.phaseStartTicks(AttackId.GRAVITY_INVERSION, LaunchPhase.CHARGING));
        assertEquals(COLLAPSE, LaunchTimeline.phaseStartTicks(AttackId.GRAVITY_INVERSION, LaunchPhase.FIRING));
        assertEquals(IMPACT, LaunchTimeline.phaseStartTicks(AttackId.GRAVITY_INVERSION, LaunchPhase.IMPACT));
        assertEquals(SHOCK, LaunchTimeline.phaseStartTicks(AttackId.GRAVITY_INVERSION, LaunchPhase.AFTERMATH));
        // The four acts of the brief: 0-4 s, 4-10 s, 10-15 s, 15-19 s.
        assertEquals(4 * 20, ASCENSION);
        assertEquals(10 * 20, ROTATION);
        assertEquals(15 * 20, COLLAPSE);
        assertTrue(COLLAPSE < SLAM && SLAM < IMPACT && IMPACT < SHOCK && SHOCK < END);
    }

    @Test public void damageIsFifteenPerSecondWhileManipulatedThenOneHundredTwentyAtCollapse() {
        assertEquals(180, RADIUS, 0);
        int pulses = 0;
        for (long age = 0; age < END; age++) if (damagePulse(age)) pulses++;
        assertEquals(15.0 * (COLLAPSE - ASCENSION) / 20, pulses * PULSE_DAMAGE, 1e-6);
        assertFalse(damagePulse(ASCENSION - 1));
        assertFalse(damagePulse(COLLAPSE));
        assertEquals(120, COLLAPSE_DAMAGE, 0);
    }

    @Test public void fieldIsACylinderAroundTheAim() {
        BlockTarget aim = new BlockTarget(100, 64, -40);
        assertTrue(contains(aim, 100.5 + 179, 70, -39.5));
        assertFalse(contains(aim, 100.5 + 181, 70, -39.5));
        assertFalse(contains(aim, 100.5, 64 - FLOOR - 1, -39.5));
        assertFalse(contains(aim, 100.5, 64 + CEILING + 1, -39.5));
    }

    @Test public void bodiesFloatThenFlipThenHangThenSlam() {
        int body = 42;
        assertNull("Grounded bodies are not touched before the ascension", drive(SEED, 30, 10, 0, 10, body, false));
        assertTrue("Loose debris already floats", drive(SEED, 60, 10, 0, 10, body, true)[1] > 0);
        assertTrue("Bodies lift off", drive(SEED, 160, 10, 0, 10, body, false)[1] > 0);
        assertEquals(FLIPS, 4);
        for (int k = 0; k < FLIPS; k++) {
            double[] g = gravity(SEED, k);
            assertEquals(1, Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]), 1e-9);
            double[] v = drive(SEED, ROTATION + k * FLIP + FLIP_BLEND + 1, 0, 20, 0, body, false);
            assertTrue(Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) <= THROW + 1e-9);
            if (k > 0) assertNotEquals(gravity(SEED, k - 1)[0], g[0], 1e-6);
        }
        double[] hang = drive(SEED, COLLAPSE + 1, 0, 30, 0, body, false);
        assertEquals(0, Math.hypot(hang[0], hang[2]), 0);
        assertTrue(Math.abs(hang[1]) < 0.05);
        for (double height : new double[] {2, 30, 64}) {
            double[] slam = drive(SEED, SLAM, 0, height, 0, body, false);
            assertTrue("Slam reaches the ground by the impact", -slam[1] * (IMPACT - SLAM) >= height);
        }
        assertNull(drive(SEED, IMPACT, 0, 0, 0, body, false));
    }

    @Test public void bodiesOrbitTheAxisWithTheFragments() {
        int body = 7;
        for (double age = ROTATION; age < COLLAPSE; age += 5) {
            double[] centre = drive(SEED, age, 0, 20, 0, body, false), off = drive(SEED, age, 30, 20, 0, body, false);
            // Same flip throw everywhere; 30 blocks out (+X) the extra motion is a counter-clockwise sweep (+Z).
            assertEquals(Math.min(SPIN_SPEED, 30 * SPIN_RATE), off[2] - centre[2], 1e-9);
            assertEquals(0, off[0] - centre[0], 1e-9);
        }
        double[] late = drive(SEED, ROTATION - 1, 30, 20, 0, body, false);
        assertTrue("The swirl is already winding up before the rotation", late[2] > 0.3);
    }

    @Test public void fragmentsTearOutOrbitAndLandBackAtTheImpact() {
        for (int i = 0; i < FRAGMENTS; i++) {
            double[] anchor = anchor(SEED, i);
            assertTrue(Math.hypot(anchor[0], anchor[1]) < RADIUS);
            assertTrue(tear(SEED, i) >= ASCENSION && tear(SEED, i) < ROTATION);
            double ground = -3 + i % 7;
            double[] rest = fragment(SEED, i, tear(SEED, i), ground);
            assertTrue("Starts buried at its anchor", rest[1] < ground);
            double[] up = fragment(SEED, i, ROTATION + 50, ground);
            assertTrue("Floats during the rotation", up[1] > ground + 5);
            double[] landed = fragment(SEED, i, IMPACT, ground);
            assertEquals(ground - anchor[2] * 0.35, landed[1], 1e-9);
            assertEquals(1, landed[5], 0);
            assertEquals(0, fragment(SEED, i, END, ground)[5], 1e-9);
        }
    }

    @Test public void shockwaveExpandsMonotonicallyPastTheField() {
        assertEquals(0, shock(IMPACT), 0);
        double last = 0;
        for (double age = IMPACT + 1; age <= END; age++) {
            double r = shock(age);
            assertTrue(r >= last);
            last = r;
        }
        assertEquals(SHOCK_RADIUS, last, 1e-9);
        assertTrue(SHOCK_RADIUS > RADIUS);
    }

    @Test public void axisIsTiltedUnitVector() {
        double[] k = axis(SEED);
        assertEquals(1, Math.sqrt(k[0] * k[0] + k[1] * k[1] + k[2] * k[2]), 1e-9);
        assertEquals(Math.cos(AXIS_TILT), k[1], 1e-9);
        assertEquals(0, orbit(ROTATION), 0);
        assertTrue(orbit(COLLAPSE) >= ORBIT);
        assertEquals(orbit(COLLAPSE), orbit(END), 0);
    }
}
