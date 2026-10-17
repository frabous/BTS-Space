package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchTimeline;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiPredicate;
import org.junit.Test;
import static org.junit.Assert.*;

public class RailgunCameraPathTest {
    @Test public void referenceCutsSelectTheNewShotAtEveryBoundary() {
        String[] shots = {"WORLD_MARK", "WORLD_REAR", "ASCENT", "CLOUDS", "EARTH", "SATURN", "SOLAR_SYSTEM", "GALAXY",
                "CANNON_WIDE", "BARREL_FLYBY", "REAR", "CHARGE", "CHAMBER", "MUZZLE", "WARP", "SKY", "DESCENT", "IMPACT", "AFTERMATH"};
        double[] cuts = {0, 10, 44, 58, 68, 120, 128, 158, 194, 210, 226, 236, 244, 254, 262, 274, 282, 286, 386, 514};
        assertArrayEquals(cuts, RailgunCameraPath.CUTS, 0);
        assertEquals(shots.length, RailgunCameraPath.Shot.values().length);
        for (int i = 0; i < shots.length; i++) {
            assertEquals(shots[i], RailgunCameraPath.Shot.values()[i].name());
            assertEquals(shots[i], RailgunCameraPath.shot(cuts[i]).name());
            assertEquals(0, RailgunCameraPath.progress(cuts[i]), 0);
            assertEquals(shots[i], RailgunCameraPath.shot(cuts[i + 1] - 0.001).name());
            assertTrue(RailgunCameraPath.progress(cuts[i + 1] - 0.001) > 0.99);
        }
        assertEquals(RailgunCameraPath.Shot.WORLD_MARK, RailgunCameraPath.shot(-1));
        assertEquals(RailgunCameraPath.Shot.AFTERMATH, RailgunCameraPath.shot(514));
        assertEquals(1, RailgunCameraPath.progress(514), 0);
    }

    @Test public void terrainContinuesBehindTheCloudHandoffAndReturnsAfterSpace() {
        RailgunCameraPath.Shot[] worldShots = {RailgunCameraPath.Shot.WORLD_MARK, RailgunCameraPath.Shot.WORLD_REAR,
                RailgunCameraPath.Shot.ASCENT, RailgunCameraPath.Shot.CLOUDS, RailgunCameraPath.Shot.SKY, RailgunCameraPath.Shot.DESCENT,
                RailgunCameraPath.Shot.IMPACT, RailgunCameraPath.Shot.AFTERMATH};
        for (RailgunCameraPath.Shot shot : RailgunCameraPath.Shot.values()) {
            boolean expected = false;
            for (RailgunCameraPath.Shot worldShot : worldShots) if (shot == worldShot) expected = true;
            assertEquals(shot.name(), expected, RailgunCameraPath.isWorldShot(shot));
        }
    }

    @Test public void visualBeamEndsWhileTheAuthoritativeImpactWindowContinues() {
        assertEquals(44, LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.ASCENT));
        assertEquals(186, LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.CHARGING));
        assertEquals(254, LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.FIRING));
        assertEquals(286, LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.IMPACT));
        assertEquals(454, LaunchTimeline.phaseStartTicks(AttackId.RAILGUN, LaunchPhase.AFTERMATH));
        assertEquals(514, LaunchTimeline.totalTicks(AttackId.RAILGUN));
        assertEquals(RailgunCameraPath.Shot.MUZZLE, RailgunCameraPath.shot(254));
        assertEquals(RailgunCameraPath.Shot.IMPACT, RailgunCameraPath.shot(286));
        assertEquals(RailgunCameraPath.Shot.GALAXY, RailgunCameraPath.shot(186));
        assertEquals(RailgunCameraPath.Shot.AFTERMATH, RailgunCameraPath.shot(386));
        assertEquals(RailgunCameraPath.Shot.AFTERMATH, RailgunCameraPath.shot(454));
        assertEquals(LaunchPhase.IMPACT, LaunchTimeline.phaseAt(AttackId.RAILGUN, 386));
        assertEquals(LaunchPhase.IMPACT, LaunchTimeline.phaseAt(AttackId.RAILGUN, 453));
        assertEquals(LaunchPhase.AFTERMATH, LaunchTimeline.phaseAt(AttackId.RAILGUN, 454));
        for (double age = 386; age <= 514; age += 0.25) assertEquals(0, RailgunVisualTiming.beamAlpha(age), 0);
        assertTrue(RailgunVisualTiming.residue(386) > 0.9);
        assertEquals(RailgunVisualTiming.residue(453.75), RailgunVisualTiming.residue(454), 0);
        assertEquals(RailgunVisualTiming.residue(454), RailgunVisualTiming.residue(454.25), 0);
        assertTrue(RailgunVisualTiming.residue(504) > 0);
        assertEquals(0, RailgunVisualTiming.residue(514), 0);
    }

    @Test public void contractionIsMonotonicAtFractionalRenderAges() {
        double previousAlpha = 1, previousRadius = 1;
        assertEquals(1, RailgunVisualTiming.beamAlpha(374), 0);
        for (double age = 374; age <= 386; age += 0.25) {
            double alpha = RailgunVisualTiming.beamAlpha(age), radius = RailgunVisualTiming.radiusScale(age);
            assertTrue(alpha >= 0 && alpha <= previousAlpha);
            assertTrue(radius > 0 && radius <= previousRadius);
            assertTrue(previousAlpha - alpha < 0.04);
            assertTrue(previousRadius - radius < 0.04);
            previousAlpha = alpha; previousRadius = radius;
        }
        assertEquals(0, previousAlpha, 0);
        assertEquals(0.002, previousRadius, 0.000001);
    }

    @Test public void reworkedBeatsChainWithoutJumps() {
        assertEquals(0, RailgunVisualTiming.lock(RailgunVisualTiming.MARK_START), 0);
        assertEquals(1, RailgunVisualTiming.lock(RailgunVisualTiming.LOCK), 0);
        assertEquals(1, RailgunVisualTiming.perimeter(RailgunVisualTiming.PERIMETER_END), 0);
        for (double x = -65; x <= 60; x += 5) {
            assertEquals(0, RailgunVisualTiming.railOpening(x, RailgunVisualTiming.DEPLOY_START), 0);
            assertEquals("Rails must be open before the charge close-up", 1, RailgunVisualTiming.railOpening(x, 236), 0);
        }
        assertEquals(1, RailgunVisualTiming.charge(RailgunVisualTiming.CHARGE_FULL), 0.000001);
        assertEquals(0, RailgunVisualTiming.discharge(RailgunVisualTiming.ARC_END), 0);
        assertTrue(RailgunVisualTiming.inhale(RailgunVisualTiming.FIRE - 0.01) > 0.99);
        assertEquals(0, RailgunVisualTiming.inhale(RailgunVisualTiming.FIRE + 1), 0);
        assertEquals(0, RailgunVisualTiming.recoil(RailgunVisualTiming.FIRE), 0);
        assertTrue(RailgunVisualTiming.recoil(RailgunVisualTiming.FIRE + 1) > 3);
        assertTrue(RailgunVisualTiming.recoil(RailgunVisualTiming.TRANSIT) < RailgunVisualTiming.recoil(RailgunVisualTiming.FIRE + 1));
        double previous = Double.POSITIVE_INFINITY;
        for (double age = RailgunVisualTiming.SKY; age <= RailgunVisualTiming.IMPACT; age += 0.125) {
            double head = RailgunVisualTiming.headHeight(age);
            assertTrue("Beam front must keep falling at " + age, head < previous || head == 0);
            assertEquals(age, RailgunVisualTiming.ageAtHeight(head), 0.000001);
            previous = head;
        }
        assertEquals(0, RailgunVisualTiming.headHeight(RailgunVisualTiming.IMPACT), 0);
        double radius = RailgunVisualTiming.blossom(RailgunVisualTiming.IMPACT);
        assertTrue("Contact opens at the punch-through radius", radius > 0.15 && radius < 0.3);
        for (double age = RailgunVisualTiming.IMPACT; age <= RailgunVisualTiming.SHRINK; age += 0.125) {
            double next = RailgunVisualTiming.blossom(age);
            assertTrue(next >= radius && next - radius < 0.06);
            radius = next;
        }
        assertEquals(1, radius, 0.000001);
    }

    @Test public void chamberStaysOutsideTheRailsAndChargeReadsLongitudinally() {
        for (double age = 244; age < 254; age += 0.25) {
            RailgunCameraPath.Pose chamber = RailgunCameraPath.pose(age);
            assertTrue(Math.hypot(chamber.y, chamber.z) > 40);
            assertTrue(Math.abs(chamber.z - chamber.targetZ) > Math.abs(chamber.x - chamber.targetX) * 3);
        }
        for (double age = 236; age < 244; age += 0.25) {
            RailgunCameraPath.Pose charge = RailgunCameraPath.pose(age);
            assertTrue(charge.x < -70);
            assertTrue(Math.abs(charge.x - charge.targetX) > Math.abs(charge.z - charge.targetZ) * 3);
        }
    }

    @Test public void chamberSightlinesClearExpandedOpaqueRailsPanelsAndTrusses() throws IOException {
        double[][] parts = meshBounds();
        RailgunCameraPath.Pose old = new RailgunCameraPath.Pose(20, 9, 48, 12, 0, 0, -4, 60);
        boolean oldBlocked = false;
        for (double[] part : parts) if (intersectsPart(old, old.targetX, part, 244)) oldBlocked = true;
        assertTrue("The mesh fixture must detect the reviewed +Z rail obstruction", oldBlocked);
        for (double age = 244; age < 254; age += 0.125) {
            RailgunCameraPath.Pose pose = RailgunCameraPath.pose(age);
            for (int part = 0; part < parts.length; part++) {
                for (double offset : new double[] {-4, 0, 4}) {
                    assertFalse("Chamber sightline blocked at " + age + " by mesh part " + part,
                            intersectsPart(pose, pose.targetX + offset, parts[part], age));
                }
            }
        }
    }

    @Test public void shortViewDistanceKeepsFinalCutsAerialOverLoadedTargetTerrain() {
        BiPredicate<Double, Double> loaded = (x, z) -> x >= -64 && x < 64 && z >= -64 && z < 64;
        RailgunCameraPath.Pose exterior = RailgunCameraPath.planAftermath(396, 0, 0, 0, loaded).pose(396);
        RailgunCameraPath.Pose rim = RailgunCameraPath.planAftermath(454, 0, 0, 0, loaded).pose(454);
        RailgunCameraPath.Pose close = RailgunCameraPath.planAftermath(504, 0, 0, 0, loaded).pose(504);
        for (RailgunCameraPath.Pose pose : new RailgunCameraPath.Pose[] {exterior, rim, close}) {
            assertNotNull(pose);
            assertEquals(0, pose.targetX, 0); assertEquals(0, pose.targetZ, 0);
            assertTrue(pose.y >= 24);
            assertTrue(Math.hypot(pose.x, pose.z) < 64);
            assertTrue(RailgunCameraPath.loadedRectangle(Math.min(pose.x, pose.targetX) - 8,
                    Math.min(pose.z, pose.targetZ) - 8, Math.max(pose.x, pose.targetX) + 8,
                    Math.max(pose.z, pose.targetZ) + 8, loaded));
        }
        assertTrue(exterior.x > 0 && exterior.z > 0);
        assertTrue(rim.x < 0 && rim.z > 0);
        assertTrue(close.x > 0 && close.z < 0);
        assertTrue(exterior.y > rim.y && rim.y > close.y);
    }

    @Test public void availableRimSectorIsPreferredOverLocalFallback() {
        BiPredicate<Double, Double> loaded = (x, z) -> (x >= -64 && x < 64 && z >= -64 && z < 64)
                || (x >= 160 && x < 272 && z >= -80 && z < 80);
        RailgunCameraPath.Pose requested = new RailgunCameraPath.Pose(Math.cos(1.8) * 237, 38, Math.sin(1.8) * 237,
                Math.cos(2.04) * 194, 1, Math.sin(2.04) * 194, 0, 64);
        RailgunCameraPath.AftermathPlan plan = RailgunCameraPath.planAftermath(454, 0, 0, 0, loaded);
        RailgunCameraPath.Pose pose = plan.pose(454);
        assertNotNull(pose);
        assertTrue(pose.x > 200);
        assertEquals(237, Math.hypot(pose.x, pose.z), 0.000001);
        assertEquals(194, Math.hypot(pose.targetX, pose.targetZ), 0.000001);
        assertEquals(requested.y, pose.y, 0);
        assertTrue(RailgunCameraPath.loadedRectangle(Math.min(pose.x, pose.targetX) - 8,
                Math.min(pose.z, pose.targetZ) - 8, Math.max(pose.x, pose.targetX) + 8,
                Math.max(pose.z, pose.targetZ) + 8, loaded));
        RailgunCameraPath.AftermathPlan full = RailgunCameraPath.planAftermath(454, 0, 0, 0, (x, z) -> true);
        assertEquals(0, full.rotation, 0);
        assertEquals(requested.x, full.pose(454).x, 0.000001);
        assertEquals(requested.z, full.pose(454).z, 0.000001);
        assertNull(RailgunCameraPath.planAftermath(454, 0, 0, 0, (x, z) -> false));
    }

    @Test public void latchedAftermathTrajectoriesStayContinuousAtFractionalAges() {
        for (int fixture = 0; fixture < 3; fixture++) {
            final int id = fixture;
            BiPredicate<Double, Double> loaded = (x, z) -> id == 0 || (x >= -64 && x < 64 && z >= -64 && z < 64)
                    || (id == 2 && x >= 160 && x < 272 && z >= -80 && z < 80);
            double cut = -1;
            RailgunCameraPath.AftermathPlan plan = null;
            RailgunCameraPath.Pose previous = null;
            for (double age = 396; age < 514; age += 0.125) {
                double nextCut = RailgunCameraPath.aftermathCutStart(age);
                if (cut != nextCut) {
                    cut = nextCut;
                    plan = RailgunCameraPath.planAftermath(age, 0, 0, 0, loaded);
                    previous = null;
                }
                assertNotNull(plan);
                RailgunCameraPath.Pose pose = plan.pose(age);
                assertTrue("Unloaded composition at " + age, RailgunCameraPath.loadedView(pose, loaded));
                if (previous != null) {
                    assertTrue("Camera jump at " + age + " in fixture " + fixture, cameraDelta(previous, pose) < 1);
                    assertTrue("Target jump at " + age, Math.hypot(pose.targetX - previous.targetX, pose.targetZ - previous.targetZ) < 1);
                }
                previous = pose;
            }
        }
    }

    @Test public void laterChunkLoadsCannotReplaceTheLatchedSector() {
        boolean[] full = {false};
        int[] queries = {0};
        BiPredicate<Double, Double> loaded = (x, z) -> {
            queries[0]++;
            return full[0] || (x >= -64 && x < 64 && z >= -64 && z < 64)
                    || (x >= 160 && x < 272 && z >= -80 && z < 80);
        };
        RailgunCameraPath.AftermathPlan plan = RailgunCameraPath.planAftermath(454, 0, 0, 0, loaded);
        assertNotNull(plan);
        assertFalse(Double.isNaN(plan.rotation));
        int initialQueries = queries[0];
        RailgunCameraPath.Pose before = plan.pose(460);
        full[0] = true;
        RailgunCameraPath.Pose after = plan.pose(460);
        assertEquals(0, cameraDelta(before, after), 0);
        assertTrue(cameraDelta(after, plan.pose(460.125)) < 1);
        assertTrue(cameraDelta(plan.pose(464), plan.pose(464.125)) < 1);
        assertEquals("Evaluating a latched plan must not rescan chunk availability", initialQueries, queries[0]);
    }

    @Test public void lostRimSectorBlendsContinuouslyToItsPreselectedAerialFallback() {
        BiPredicate<Double, Double> initial = (x, z) -> (x >= -64 && x < 64 && z >= -64 && z < 64)
                || (x >= 160 && x < 272 && z >= -80 && z < 80);
        BiPredicate<Double, Double> remaining = (x, z) -> x >= -64 && x < 64 && z >= -64 && z < 64;
        RailgunCameraPath.AftermathPlan plan = RailgunCameraPath.planAftermath(454, 0, 0, 0, initial);
        RailgunCameraPath.Pose from = plan.pose(459.875), previous = from;
        assertFalse(RailgunCameraPath.loadedView(plan.pose(460), remaining));
        assertTrue(RailgunCameraPath.loadedView(plan.aerial, remaining));
        for (double age = 460; age <= 480; age += 0.125) {
            RailgunCameraPath.Pose pose = RailgunCameraPath.blendPoses(from, plan.aerial, (age - 460) / 20);
            assertTrue("Chunk-loss blend jumped at " + age, cameraDelta(previous, pose) < 4);
            assertTrue(Double.isFinite(pose.x) && Double.isFinite(pose.y) && Double.isFinite(pose.z));
            previous = pose;
        }
        assertEquals(0, cameraDelta(plan.aerial, previous), 0.000001);
    }

    private static double cameraDelta(RailgunCameraPath.Pose a, RailgunCameraPath.Pose b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2));
    }

    @Test public void groundGeometryCannotBridgeAnUnloadedInteriorChunk() {
        BiPredicate<Double, Double> hole = (x, z) -> !(x >= 16 && x < 32 && z >= 16 && z < 32);
        assertFalse(RailgunCameraPath.loadedRectangle(0, 0, 48, 48, hole));
        assertTrue(RailgunCameraPath.loadedRectangle(0, 0, 15.9, 15.9, hole));
        assertTrue(RailgunCameraPath.loadedRectangle(-15.9, -15.9, -0.1, -0.1, (x, z) -> x < 0 && z < 0));
        assertFalse(RailgunCameraPath.loadedRectangle(-0.1, -0.1, 0.1, 0.1, (x, z) -> x < 0 && z < 0));
    }

    @Test public void hazardRimHoldsThroughContractionAndFinalCutThenFades() {
        assertEquals(0, WorldEffects.hazardAlpha(282), 0);
        assertTrue(WorldEffects.hazardAlpha(284) > 0 && WorldEffects.hazardAlpha(284) < 1);
        for (double age = 286; age <= 500; age += 0.125) assertEquals(1, WorldEffects.hazardAlpha(age), 0);
        assertEquals(1, WorldEffects.hazardAlpha(381), 0);
        assertEquals(WorldEffects.hazardAlpha(453.875), WorldEffects.hazardAlpha(454.125), 0);
        double previous = 1;
        for (double age = 500; age <= 514; age += 0.125) {
            double alpha = WorldEffects.hazardAlpha(age);
            assertTrue(alpha >= 0 && alpha <= previous);
            assertTrue(previous - alpha < 0.02);
            previous = alpha;
        }
        assertEquals(0, previous, 0);
    }

    @Test public void groundSurfaceHasCircularCoverageAndAContinuousWarmEdge() {
        assertEquals(1, WorldEffects.groundAlpha(0, 200, 0), 0);
        assertEquals(1, WorldEffects.groundAlpha(198.5, 200, 0), 0);
        assertEquals(0, WorldEffects.groundAlpha(200, 200, 0), 0);
        assertEquals(0, WorldEffects.groundAlpha(210, 200, 0), 0);
        assertEquals(1, WorldEffects.groundAlpha(200.5, 200.5, 0.7), 0);
        assertEquals(0, WorldEffects.groundAlpha(202, 200.5, 0.7), 0);
        for (double angle = 0; angle < Math.PI * 2; angle += 0.03) {
            double radius = Math.hypot(Math.cos(angle) * 199.25, Math.sin(angle) * 199.25);
            assertEquals(0.5, WorldEffects.groundAlpha(radius, 200, 0), 0.000001);
        }
        double previous = 1;
        for (double distance = 198.5; distance <= 200; distance += 0.03125) {
            double alpha = WorldEffects.groundAlpha(distance, 200, 0);
            assertTrue(alpha >= 0 && alpha <= previous);
            assertTrue(previous - alpha < 0.04);
            previous = alpha;
        }
    }

    private static double[][] meshBounds() throws IOException {
        InputStream resource = RailgunCameraPathTest.class.getResourceAsStream("/assets/stellar/models/orbital_railgun.mesh");
        assertNotNull("Runtime railgun mesh must be on the test classpath", resource);
        try (DataInputStream input = new DataInputStream(resource)) {
            assertEquals(0x53524731, input.readInt());
            int count = input.readInt();
            assertTrue(count > 0 && count % 24 == 0);
            double[][] bounds = new double[count / 24][9];
            for (double[] part : bounds) {
                for (int axis = 0; axis < 3; axis++) { part[axis] = Double.POSITIVE_INFINITY; part[axis + 3] = Double.NEGATIVE_INFINITY; }
                float[] center = new float[3];
                for (int vertex = 0; vertex < 24; vertex++) {
                    for (int axis = 0; axis < 3; axis++) {
                        float value = input.readFloat();
                        part[axis] = Math.min(part[axis], value); part[axis + 3] = Math.max(part[axis + 3], value);
                        center[axis] += value / 24;
                    }
                    input.readInt(); input.readUnsignedByte();
                }
                for (int axis = 0; axis < 3; axis++) part[axis + 6] = center[axis];
            }
            return bounds;
        }
    }

    private static boolean intersectsPart(RailgunCameraPath.Pose pose, double targetX, double[] part, double age) {
        double radial = Math.hypot(part[7], part[8]);
        double expansion = part[6] >= -32 && radial > 4 && radial < 18
                ? RailgunVisualTiming.railOpening(part[6], age) * 0.55 : 0;
        double[] origin = {pose.x, pose.y, pose.z}, target = {targetX, pose.targetY, pose.targetZ};
        double enter = 0, exit = 1;
        for (int axis = 0; axis < 3; axis++) {
            double shift = axis == 0 ? 0 : part[axis + 6] * expansion;
            double lo = part[axis] + shift, hi = part[axis + 3] + shift, direction = target[axis] - origin[axis];
            if (Math.abs(direction) < 0.000000001) {
                if (origin[axis] < lo || origin[axis] > hi) return false;
            } else {
                double a = (lo - origin[axis]) / direction, b = (hi - origin[axis]) / direction;
                enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return false;
            }
        }
        return true;
    }

    @Test public void spacePullbackNeverResetsPositionDirectionOrZoomSpeedAtFormerCuts() {
        double epsilon = 0.0001;
        for (double boundary : new double[] {68, 108, 120, 128, 144, 158, 178}) {
            RailgunCameraPath.Pose before = RailgunCameraPath.pose(boundary - epsilon);
            RailgunCameraPath.Pose at = RailgunCameraPath.pose(boundary);
            RailgunCameraPath.Pose after = RailgunCameraPath.pose(boundary + epsilon);
            double distance = RailgunPullback.distance(boundary);
            assertTrue("Position cut at " + boundary, cameraDelta(before, after) / distance < 0.0001);
            double[] a = {before.targetX - before.x, before.targetY - before.y, before.targetZ - before.z};
            double[] b = {after.targetX - after.x, after.targetY - after.y, after.targetZ - after.z};
            double la = Math.sqrt(a[0]*a[0] + a[1]*a[1] + a[2]*a[2]);
            double lb = Math.sqrt(b[0]*b[0] + b[1]*b[1] + b[2]*b[2]);
            for (int axis = 0; axis < 3; axis++) assertEquals(a[axis] / la, b[axis] / lb, 0.0001);
            assertEquals(before.roll, after.roll, 0.0001);
            assertEquals(before.fov, after.fov, 0);
            double incoming = Math.log(distance / RailgunPullback.distance(boundary - epsilon)) / epsilon;
            double outgoing = Math.log(RailgunPullback.distance(boundary + epsilon) / distance) / epsilon;
            assertTrue("Zoom stopped at " + boundary, incoming > 0 && outgoing > 0);
            assertEquals("Zoom speed cut at " + boundary, incoming, outgoing, 0.0001);
            assertTrue(cameraDelta(before, at) > 0);
        }
    }

    @Test public void earthShrinksThroughAllSpacePhasesAndRenderScalingPreservesPerspective() {
        double previousDistance = RailgunPullback.distance(58), previousEyeDistance = 0;
        for (double age = 58; age <= 194; age += 1.0 / 3) {
            double distance = RailgunPullback.distance(age), scale = RailgunPullback.renderScale(age);
            RailgunCameraPath.Pose pose = RailgunPullback.pose(age);
            double eyeDistance = Math.sqrt(pose.x*pose.x + pose.y*pose.y + pose.z*pose.z);
            assertTrue("Earth zoom reversed at " + age, eyeDistance >= previousEyeDistance);
            assertTrue(distance >= previousDistance);
            assertTrue("Single-frame zoom jump at " + age, Math.log(distance / previousDistance) < 0.09);
            assertTrue(scale > 0 && scale <= 1);
            assertEquals(RailgunPullback.EARTH_RADIUS / eyeDistance,
                    (RailgunPullback.EARTH_RADIUS * scale) / (eyeDistance * scale), 0.000000001);
            assertTrue(distance * scale <= 160.000001);
            previousDistance = distance; previousEyeDistance = eyeDistance;
        }
        double a = RailgunPullback.orbitAngle(2), r = RailgunPullback.ORBITS[2];
        assertEquals("Solar view must keep the original Earth", 0, RailgunPullback.SUN_X + Math.cos(a) * r, 0.000001);
        assertEquals(0, RailgunPullback.SUN_Z + Math.sin(a) * r, 0.000001);
        assertTrue(previousDistance / RailgunPullback.distance(58) > 100000);
    }

    @Test public void cloudsAndSpaceOpacityBridgeTheWorldWithoutAVisibilityCut() {
        assertEquals(0, RailgunPullback.opacity(58), 0);
        assertEquals(1, RailgunPullback.opacity(68), 0);
        assertEquals(0, RailgunPullback.clouds(52), 0);
        assertEquals(0, RailgunPullback.clouds(78), 0);
        for (double age = 58; age <= 68; age += 0.125) assertTrue(RailgunPullback.clouds(age) > 0.75);
        for (double boundary : new double[] {58, 64, 68, 78}) {
            assertEquals(RailgunPullback.clouds(boundary - 0.0001), RailgunPullback.clouds(boundary + 0.0001), 0.0001);
            assertEquals(RailgunPullback.opacity(boundary - 0.0001), RailgunPullback.opacity(boundary + 0.0001), 0.0001);
        }
        assertFalse(RailgunPullback.active(57.999));
        assertTrue(RailgunPullback.active(58));
        assertTrue(RailgunPullback.active(193.999));
        assertFalse(RailgunPullback.active(194));
    }

    @Test public void fractionalFramesHaveFinitePerspectiveAndNonzeroViewDirection() {
        for (double age = 0; age <= 514; age += 0.25) {
            RailgunCameraPath.Pose p = RailgunCameraPath.pose(age);
            assertTrue(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z));
            assertTrue(Double.isFinite(p.targetX) && Double.isFinite(p.targetY) && Double.isFinite(p.targetZ));
            assertTrue(Double.isFinite(p.roll) && p.fov >= 40 && p.fov <= 80);
            double distance = Math.pow(p.x - p.targetX, 2) + Math.pow(p.y - p.targetY, 2) + Math.pow(p.z - p.targetZ, 2);
            assertTrue(distance > 1);
            assertTrue(RailgunCameraPath.progress(age) >= 0 && RailgunCameraPath.progress(age) <= 1);
        }
    }
}
