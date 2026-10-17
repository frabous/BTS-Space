package fr.frabous.stellar.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlackholeVisualMathTest {
    @Test public void rendererOriginStaysFixedForEveryRadialDirection() {
        double[] origin = {123.5, -7.25, 80};
        double[][] directions = {{1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {3, -4, 5}, {0, 0, 0}};
        for (double[] direction : directions) {
            for (double strength = 0; strength <= 1; strength += 0.125) {
                double[] matrix = BlackholeVisualMath.tidalTransform(direction[0], direction[1], direction[2],
                        origin[0], origin[1], origin[2], strength);
                assertArrayEquals(origin, point(matrix, origin), 1e-10);
            }
        }
    }

    @Test public void diagonalAxisStretchesWithoutRotatingAndPerpendicularAxisNarrows() {
        double[] matrix = BlackholeVisualMath.tidalTransform(3, 4, 0, 0, 0, 0, 1);
        assertArrayEquals(new double[] {10.5, 14, 0}, point(matrix, new double[] {3, 4, 0}), 1e-10);
        assertArrayEquals(new double[] {-4 * 0.88, 3 * 0.88, 0},
                point(matrix, new double[] {-4, 3, 0}), 1e-10);
        assertEquals(3.5 * 0.88 * 0.88, determinant(matrix), 1e-10);
    }

    @Test public void zeroStrengthIsIdentityAndCenterDirectionRemainsFinite() {
        assertArrayEquals(new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
                BlackholeVisualMath.tidalTransform(7, -13, 19, 30, -40, 50, 0), 1e-10);
        double[] center = BlackholeVisualMath.tidalTransform(0, 0, 0, 0, 0, 0, 1);
        for (double entry : center) assertTrue(Double.isFinite(entry));
        assertEquals(3.5, center[5], 0);
        assertTrue(determinant(center) > 0);
    }

    @Test public void strengthCannotInvertOrExpandWithoutBound() {
        double previous = 1;
        for (double strength = -0.25; strength <= 1.25; strength += 0.125) {
            double[] matrix = BlackholeVisualMath.tidalTransform(0, 1, 0, 0, 0, 0, strength);
            assertTrue(matrix[5] >= previous && matrix[5] <= 3.5);
            assertTrue(matrix[0] >= 0.88 && matrix[10] >= 0.88);
            assertTrue(determinant(matrix) > 0);
            previous = matrix[5];
        }
    }

    @Test public void modelEffectIsGatedBySpaceRadiusAndPullWindow() {
        assertEquals(0, strength(100, 299.99, 0, 120), 0);
        assertEquals(0, strength(100, 300, 0, 120), 0);
        assertTrue(strength(100, 301, 0, 120) > 0);
        assertEquals(0, strength(100, 420, 1000, 1000), 0);
        assertEquals(0, strength(100, 420, 1200, 1200), 0);
        assertEquals(0, strength(59, 420, 0, 0), 0);
        assertEquals(0, strength(740, 420, 0, 0), 0);
        assertEquals(0, strength(800, 420, 0, 0), 0);
        assertEquals(1, strength(100, 420, 0, 0), 0);
    }

    @Test public void approachingHorizonMakesModelElongationProgressivelyStronger() {
        double previous = 0;
        for (double distance = 1000; distance >= 0; distance -= 5) {
            double current = strength(300, 420, distance, distance);
            assertTrue(current >= previous && current <= 1);
            previous = current;
        }
    }

    @Test public void liveCameraBasisKeepsFeetFixedAndStretchesTheWorldRadialAxis() {
        double[][] views = {
                {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0, -0.5, -1.8, -4, 1},
                {0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 4, -7, 10, 1},
                {0.6, 0.8, 0, 0, -0.8, 0.6, 0, 0, 0, 0, 1, 0, -2, 3, -4, 1}
        };
        double[] origin = {12, -7, 80}, radialPoint = {15, -3, 80};
        for (double[] view : views) {
            double[] result = BlackholeVisualMath.tidalInView(view, 3, 4, 0, origin[0], origin[1], origin[2], 1);
            double[] feet = point(view, origin), radial = point(view, radialPoint);
            assertArrayEquals(feet, point(result, origin), 1e-10);
            assertArrayEquals(new double[] {feet[0] + 3.5 * (radial[0] - feet[0]),
                    feet[1] + 3.5 * (radial[1] - feet[1]), feet[2] + 3.5 * (radial[2] - feet[2])},
                    point(result, radialPoint), 1e-10);
            assertArrayEquals(view, BlackholeVisualMath.tidalInView(view, 3, 4, 0, 12, -7, 80, 0), 1e-10);
        }
    }

    @Test public void interiorOrbitCannotBecomeAnOpaqueHorizon() {
        assertEquals(0, BlackholeVisualMath.horizonOpacity(52.5, 70), 0);
        assertEquals(0, BlackholeVisualMath.horizonOpacity(70, 70), 0);
        assertTrue(BlackholeVisualMath.horizonOpacity(77, 70) > 0);
        assertTrue(BlackholeVisualMath.horizonOpacity(77, 70) < 1);
        assertEquals(1, BlackholeVisualMath.horizonOpacity(84, 70), 0);
        double previous = 0;
        for (double distance = 0; distance < 150; distance += 0.25) {
            double opacity = BlackholeVisualMath.horizonOpacity(distance, 70);
            assertTrue(opacity >= previous && opacity <= 1);
            previous = opacity;
        }
        assertEquals(26.25, BlackholeVisualMath.lensRadius(52.5, 70), 0);
        assertEquals(70, BlackholeVisualMath.lensRadius(84, 70), 0);
    }

    @Test public void strongModelDeformationConcentratesNearTheHorizon() {
        assertTrue(strength(300, 420, 140, 140) < 0.3);
        assertTrue(strength(300, 420, 210, 210) < 0.06);
        assertTrue(strength(300, 420, 70, 70) > 0.98);
    }

    @Test public void nearCameraModelsCannotExplodeAcrossTheViewport() {
        assertEquals(0, BlackholeVisualMath.readableModelStrength(1, 0.5, 1.8), 0);
        assertTrue(BlackholeVisualMath.readableModelStrength(1, 4, 1.8) < 0.8);
        assertEquals(1, BlackholeVisualMath.readableModelStrength(1, 5.4, 1.8), 1e-10);
        double previous = 0;
        for (double distance = 0; distance <= 10; distance += 0.125) {
            double current = BlackholeVisualMath.readableModelStrength(0.75, distance, 1.4);
            assertTrue(current >= previous && current <= 0.75);
            previous = current;
        }
    }

    @Test public void spaceRevealPrecedesTheVisualHorizonAndTunnel() {
        // The established server orbit is 52.5 blocks: allow a clear exterior view first.
        double revealRadius = BlackholeVisualMath.horizonRadius(200, 70);
        assertTrue(revealRadius < 52.5);
        assertTrue(BlackholeVisualMath.horizonRadius(260, 70) < 52.5);
        assertTrue(BlackholeVisualMath.entry(120, revealRadius) < 0.01);
        assertEquals(0, BlackholeVisualMath.entry(140, 70), 0);
        assertEquals(1, BlackholeVisualMath.entry(52.5, 70), 0);
        assertEquals(70, BlackholeVisualMath.horizonRadius(440, 70), 0);
        double previous = 0;
        for (int age = 0; age <= 800; age++) {
            double radius = BlackholeVisualMath.horizonRadius(age, 70);
            assertTrue(radius >= previous && radius <= 70);
            previous = radius;
        }
    }

    @Test public void spaceTransitionDoesNotPopAtTheCaptureAltitude() {
        assertEquals(0, BlackholeVisualMath.space(300, 300), 0);
        assertTrue(BlackholeVisualMath.space(300.001, 300) < 1e-8);
        assertEquals(0.5, BlackholeVisualMath.space(320, 300), 1e-10);
        assertEquals(1, BlackholeVisualMath.space(340, 300), 0);
    }

    @Test public void dopplerUsesTheTiltedDiskAndVanishesFromItsPoles() {
        double sin = BlackholeVisualMath.DISK_SIN, cos = BlackholeVisualMath.DISK_COS;
        assertArrayEquals(new double[] {0, 1, 0}, BlackholeVisualMath.diskView(0, cos, sin), 1e-10);
        assertArrayEquals(new double[] {0, -1, 0}, BlackholeVisualMath.diskView(0, -cos, -sin), 1e-10);
        assertArrayEquals(new double[] {0, 0, 1}, BlackholeVisualMath.diskView(0, -sin, cos), 1e-10);
        double[] view = BlackholeVisualMath.diskView(3, 4, 5);
        double[] opposite = BlackholeVisualMath.diskView(-3, -4, -5);
        for (int angle = 0; angle < 360; angle++) {
            double s = Math.sin(Math.toRadians(angle)), c = Math.cos(Math.toRadians(angle));
            double doppler = -s * view[0] + c * view[2];
            assertTrue(Math.abs(doppler) <= 1);
            assertEquals(-doppler, -s * opposite[0] + c * opposite[2], 1e-10);
        }
        for (double value : BlackholeVisualMath.diskView(0, 0, 0)) assertTrue(Double.isFinite(value));
    }

    @Test public void lensArcsFollowTheDiskAndDisappearFromItsPoles() {
        double sin = BlackholeVisualMath.DISK_SIN, cos = BlackholeVisualMath.DISK_COS;
        double[][] directions = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, cos, sin}, {0, -cos, -sin},
                {0.6, 0.8, 0}, {-2 / 3.0, 1 / 3.0, 2 / 3.0}};
        for (double[] n : directions) {
            double[] basis = BlackholeVisualMath.lensBasis(n[0], n[1], n[2]);
            for (double value : basis) assertTrue(Double.isFinite(value));
            assertEquals(1, basis[0] * basis[0] + basis[1] * basis[1] + basis[2] * basis[2], 1e-10);
            assertEquals(1, basis[3] * basis[3] + basis[4] * basis[4] + basis[5] * basis[5], 1e-10);
            assertEquals(0, basis[0] * n[0] + basis[1] * n[1] + basis[2] * n[2], 1e-10);
            assertEquals(0, basis[3] * n[0] + basis[4] * n[1] + basis[5] * n[2], 1e-10);
            // The disk's (-sin(angle), 0, cos(angle)) flow approaches the observer on the lens's right side.
            double tangentX = sin * basis[1] - cos * basis[2];
            double tangentY = -sin * basis[0], tangentZ = cos * basis[0];
            assertEquals(basis[6], tangentX * n[0] + tangentY * n[1] + tangentZ * n[2], 1e-10);
        }
        assertEquals(0, BlackholeVisualMath.lensBasis(0, cos, sin)[6], 1e-10);
        assertEquals(1, BlackholeVisualMath.lensBasis(1, 0, 0)[6], 1e-10);
    }

    @Test public void plasmaCoolsContinuouslyTowardTheDiskEdge() {
        double previous = 1;
        for (int band = 0; band <= 100; band++) {
            double heat = BlackholeVisualMath.diskHeat(band / 100.0);
            assertTrue(Double.isFinite(heat) && heat >= 0 && heat <= previous);
            previous = heat;
        }
        assertEquals(0, previous, 0);
    }

    @Test public void cameraYawRemainsContinuousAcrossWrappedHeadings() {
        assertEquals(181, BlackholeVisualMath.unwrapAngle(-179, 179), 0);
        assertEquals(-181, BlackholeVisualMath.unwrapAngle(179, -179), 0);
        assertEquals(721, BlackholeVisualMath.unwrapAngle(1, 719), 0);
        assertEquals(-721, BlackholeVisualMath.unwrapAngle(-1, -719), 0);

        double playerYaw = 170, revealYaw = 0, previous = 85;
        for (int frame = 1; frame <= 720; frame++) {
            playerYaw = BlackholeVisualMath.unwrapAngle((170 + frame + 180) % 360 - 180, playerYaw);
            revealYaw = BlackholeVisualMath.unwrapAngle((frame * 0.25 + 180) % 360 - 180, revealYaw);
            double cameraYaw = playerYaw + (revealYaw - playerYaw) * 0.5;
            assertEquals(0.625, cameraYaw - previous, 1e-10);
            previous = cameraYaw;
        }
    }

    @Test public void spaghettificationIsIdentityWithoutTidesAndKeepsTheBodyCentreFixed() {
        for (double s = -3; s <= 3; s += 0.125) {
            assertEquals(s, BlackholeVisualMath.spaghettiAlong(s, 0), 1e-12);
            assertEquals(1, BlackholeVisualMath.spaghettiStretch(s, 0), 0);
            assertEquals(1, BlackholeVisualMath.spaghettiWidth(s, 0, 0), 0);
        }
        for (double amount = 0; amount <= 24; amount += 1.5) assertEquals(0, BlackholeVisualMath.spaghettiAlong(0, amount), 1e-12);
        assertEquals(0, BlackholeVisualMath.spaghettiAmount(0, 0), 0);
        assertEquals(24, BlackholeVisualMath.spaghettiAmount(2, 3), 0);
    }

    @Test public void tidalStretchNeverCompressesAndPullsTheNearSideHarder() {
        for (double amount = 0.5; amount <= 24; amount += 0.5) {
            double previous = Double.NEGATIVE_INFINITY;
            for (double s = -4; s <= 4; s += 0.01) {
                double along = BlackholeVisualMath.spaghettiAlong(s, amount);
                assertTrue(Double.isFinite(along) && along > previous);
                assertTrue(BlackholeVisualMath.spaghettiStretch(s, amount) >= 1);
                // The integral matches its derivative (no seam at the clamps).
                double slope = (BlackholeVisualMath.spaghettiAlong(s + 1e-6, amount)
                        - BlackholeVisualMath.spaghettiAlong(s - 1e-6, amount)) / 2e-6;
                assertEquals(BlackholeVisualMath.spaghettiStretch(s, amount), slope, 1e-4 * slope);
                previous = along;
            }
            assertTrue(BlackholeVisualMath.spaghettiStretch(1, amount) > BlackholeVisualMath.spaghettiStretch(-1, amount));
            assertTrue(BlackholeVisualMath.spaghettiAlong(1, amount) > -BlackholeVisualMath.spaghettiAlong(-1, amount));
        }
        // Full horizon tide: about four body lengths; lethal unravelling: a thread over ten.
        double alive = (BlackholeVisualMath.spaghettiAlong(1, 6) - BlackholeVisualMath.spaghettiAlong(-1, 6)) / 2;
        double dying = (BlackholeVisualMath.spaghettiAlong(1, 24) - BlackholeVisualMath.spaghettiAlong(-1, 24)) / 2;
        assertTrue(alive > 3.5 && alive < 5);
        assertTrue(dying > 10 && dying < 16);
    }

    @Test public void crossSectionConservesVolumeUntilItsReadableFloor() {
        for (double amount = 0; amount <= 24; amount += 0.75) {
            for (double s = -1.5; s <= 1.5; s += 0.05) {
                double width = BlackholeVisualMath.spaghettiWidth(s, amount, 0);
                double stretch = BlackholeVisualMath.spaghettiStretch(s, amount);
                if (width > BlackholeVisualMath.spaghettiFloor(0)) assertEquals(1, width * width * stretch, 1e-10);
                else assertEquals(BlackholeVisualMath.spaghettiFloor(0), width, 0);
                assertTrue(BlackholeVisualMath.spaghettiWidth(s, amount, 1) <= width);
            }
            assertTrue(BlackholeVisualMath.spaghettiWidth(1, amount, 0) <= BlackholeVisualMath.spaghettiWidth(-1, amount, 0));
        }
        assertTrue(BlackholeVisualMath.spaghettiFloor(1) > 0 && BlackholeVisualMath.spaghettiFloor(1) < BlackholeVisualMath.spaghettiFloor(0));
    }

    @Test public void unravellingFollowsTornHealthThenTheDeathAnimation() {
        assertEquals(0, BlackholeVisualMath.unravel(1, 1, 0), 0);
        assertEquals(0, BlackholeVisualMath.unravel(0, 0.1, 0), 0);
        assertEquals(0.6, BlackholeVisualMath.unravel(1, 0.4, 0), 1e-12);
        assertEquals(0.3, BlackholeVisualMath.unravel(0.5, 0.4, 0), 1e-12);
        assertEquals(1, BlackholeVisualMath.unravel(0, 1, 1.4), 0);
        assertEquals(0.75, BlackholeVisualMath.unravel(0.2, 0.5, 0.75), 1e-12);
        assertEquals(0, BlackholeVisualMath.spaghettiBend(0.9, 0, 0), 0);
        assertTrue(BlackholeVisualMath.spaghettiBend(0.9, 1, 1) > 0);
        assertEquals(0, BlackholeVisualMath.spaghettiAlignment(0, 0), 0);
        assertEquals(1, BlackholeVisualMath.spaghettiAlignment(0, 1), 0);
    }

    @Test public void bodyAlignmentTurnsTowardTheNearerEndOfTheTidalAxis() {
        double[][] pairs = {{0, 1, 0, 1, 0, 0}, {0, 1, 0, 0, -0.6, 0.8}, {0, 1, 0, 0, 1, 0}, {0, 1, 0, 0, -1, 0},
                {0.6, 0.8, 0, 0, 0, 1}, {0, 0, 1, -2 / 3.0, 1 / 3.0, 2 / 3.0}};
        for (double[] pair : pairs) {
            double[] up = {pair[0], pair[1], pair[2]}, axis = {pair[3], pair[4], pair[5]};
            assertArrayEquals(new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1},
                    BlackholeVisualMath.alignTo(up[0], up[1], up[2], axis[0], axis[1], axis[2], 0), 1e-12);
            double[] full = BlackholeVisualMath.alignTo(up[0], up[1], up[2], axis[0], axis[1], axis[2], 1);
            double[] turned = rotate(full, up);
            double sign = up[0] * axis[0] + up[1] * axis[1] + up[2] * axis[2] < 0 ? -1 : 1;
            assertArrayEquals(new double[] {axis[0] * sign, axis[1] * sign, axis[2] * sign}, turned, 1e-10);
            double[] half = BlackholeVisualMath.alignTo(up[0], up[1], up[2], axis[0], axis[1], axis[2], 0.5);
            for (double[] matrix : new double[][] {full, half}) {
                for (double value : matrix) assertTrue(Double.isFinite(value));
                assertEquals(1, determinant3(matrix), 1e-10);
                for (double[] probe : new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {3, -4, 5}}) {
                    double[] r = rotate(matrix, probe);
                    assertEquals(Math.sqrt(dot(probe, probe)), Math.sqrt(dot(r, r)), 1e-10);
                }
            }
        }
    }

    @Test public void lensRingSurroundsTheShadowAndStaysBoundedInside() {
        double previous = 0;
        for (double distance = 2000; distance >= 1; distance -= 1) {
            double[] lens = BlackholeVisualMath.lensAngles(distance, 70);
            for (double value : lens) assertTrue(Double.isFinite(value));
            assertTrue(lens[1] > lens[0] && lens[1] <= 1.25 && lens[0] <= Math.asin(0.9) + 1e-12);
            assertTrue(lens[2] >= 0.6 && lens[2] <= 1);
            if (distance > 85) {
                assertTrue("Exterior shadow grows on approach", lens[0] >= previous);
                assertEquals(1, lens[2], 1e-12);
            }
            previous = lens[0];
        }
        assertEquals(Math.asin(70 / 356.0), BlackholeVisualMath.lensAngles(356, 70)[0], 1e-12);
        assertEquals(0.6, BlackholeVisualMath.lensAngles(52.5, 70)[2], 1e-12);
        assertEquals(0, BlackholeVisualMath.lensAngles(0, 70)[0], 0);
    }

    @Test public void gravitationalWavePulsesStartWithTheHoleAndDecay() {
        assertEquals(0, BlackholeVisualMath.ripple(59)[0], 0);
        assertTrue(BlackholeVisualMath.ripple(70)[0] > BlackholeVisualMath.ripple(105)[0]);
        assertTrue(BlackholeVisualMath.ripple(115)[1] < BlackholeVisualMath.ripple(105)[1]);
        assertTrue(BlackholeVisualMath.ripple(690)[0] < 1e-4);
        assertTrue(BlackholeVisualMath.ripple(710)[0] > 0.005);
        for (int age = 0; age <= 800; age++) assertTrue(BlackholeVisualMath.ripple(age)[0] <= 0.012);
    }

    private static double[] rotate(double[] m, double[] v) {
        return new double[] {m[0] * v[0] + m[3] * v[1] + m[6] * v[2], m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
                m[2] * v[0] + m[5] * v[1] + m[8] * v[2]};
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double determinant3(double[] m) {
        return m[0] * (m[4] * m[8] - m[7] * m[5]) - m[3] * (m[1] * m[8] - m[7] * m[2]) + m[6] * (m[1] * m[5] - m[4] * m[2]);
    }

    private static double strength(double age, double altitude, double horizontal, double center) {
        return BlackholeVisualMath.modelStrength(age, altitude, horizontal, center, 1000, 300, 70);
    }

    private static double[] point(double[] matrix, double[] point) {
        return new double[] {matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12],
                matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13],
                matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14]};
    }

    private static double determinant(double[] m) {
        return m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2])
                + m[8] * (m[1] * m[6] - m[5] * m[2]);
    }
}
