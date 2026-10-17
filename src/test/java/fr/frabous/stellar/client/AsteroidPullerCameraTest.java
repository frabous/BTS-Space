package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import java.util.UUID;
import org.junit.Test;
import static fr.frabous.stellar.api.AsteroidPullerTiming.*;
import static org.junit.Assert.*;

/** One continuous camera: no hard cuts, no lens inside the asteroid, subjects kept in frame. */
public class AsteroidPullerCameraTest {
    private static final double STEP = 0.125;
    private static final CarrierFlight.Plan PLAN = AsteroidPullerLance.plan(new LaunchState(new UUID(1, 2),
            AttackId.ASTEROID_PULLER, 0, new BlockTarget(256, 3, 256), new BlockTarget(256, 3, 256), 42, 100, LaunchPhase.MARKING));

    @Test public void worldCameraMovesWithoutHardCutsOutsideDeliberateJolts() {
        for (double reach : new double[] {1, 0.55}) {
            AsteroidPullerCamera.Layout layout = new AsteroidPullerCamera.Layout(18, 1.6, 30, 140, reach);
            RailgunCameraPath.Pose previous = AsteroidPullerCamera.world(PLAN, layout, 0);
            for (double age = STEP; age < END; age += STEP) {
                RailgunCameraPath.Pose pose = AsteroidPullerCamera.world(PLAN, layout, age);
                boolean lensChange = age > SPACE_CUT - 1 && age < WORLD_CUT + 1;
                boolean jolt = near(age, LAND) || near(age, FIRE) || near(age, LOCK) || near(age, IMPACT) || near(age, IMPACT + 8);
                if (!lensChange && !jolt) {
                    double move = Math.sqrt(sq(pose.x - previous.x) + sq(pose.y - previous.y) + sq(pose.z - previous.z));
                    assertTrue("Camera jumps " + move + " blocks at " + age, move < limit(age));
                    assertTrue("Look direction snaps at " + age, angle(previous, pose) < 4);
                }
                previous = pose;
            }
        }
    }

    @Test public void orbitalLensHandsOverToTheWorldWithTheSameChaseFraming() {
        assertFalse(AsteroidPullerCamera.isSpaceShot(SPACE_CUT - 0.01));
        assertTrue(AsteroidPullerCamera.isSpaceShot(SPACE_CUT));
        assertFalse(AsteroidPullerCamera.isSpaceShot(WORLD_CUT));
        assertEquals(1, AsteroidPullerCamera.spaceFlash(SPACE_CUT), 1.0E-9);
        assertEquals(1, AsteroidPullerCamera.cloudWipe(WORLD_CUT), 1.0E-9);
        assertEquals(0, AsteroidPullerCamera.cloudWipe(WORLD_CUT + 16), 1.0E-9);
        RailgunCameraPath.Pose orbit = AsteroidPullerCamera.orbital(PLAN, WORLD_CUT - 1.0E-6);
        RailgunCameraPath.Pose world = AsteroidPullerCamera.world(PLAN, new AsteroidPullerCamera.Layout(0, 0, 0, 0, 1), WORLD_CUT);
        assertEquals(orbit.x, world.x, 0.01);
        assertEquals(orbit.y, world.y, 0.05);
        assertEquals(orbit.z, world.z, 0.01);
        assertTrue(angle(orbit, world) < 0.5);
    }

    @Test public void noLensEverEntersTheAsteroid() {
        AsteroidPullerCamera.Layout layout = new AsteroidPullerCamera.Layout(18, 1.6, 30, 140, 0.55);
        for (double age = 90; age < IMPACT; age += STEP) {
            double[] c = AsteroidTrajectory.position(age);
            RailgunCameraPath.Pose pose = AsteroidPullerCamera.isSpaceShot(age) ? AsteroidPullerCamera.orbital(PLAN, age)
                    : AsteroidPullerCamera.world(PLAN, layout, age);
            double distance = Math.sqrt(sq(pose.x - c[0]) + sq(pose.y - c[1]) + sq(pose.z - c[2]));
            assertTrue("Lens inside the asteroid at " + age, distance >= AsteroidModel.MAX_RADIUS + 5);
        }
    }

    @Test public void eachShotKeepsItsSubjectInFrame() {
        AsteroidPullerCamera.Layout layout = new AsteroidPullerCamera.Layout(18, 1.6, 30, 140, 1);
        for (double age = WARP_EXIT + 8; age < STATION; age += STEP) {
            CarrierFlight.State ship = CarrierFlight.at(PLAN, age);
            assertInFrame("carrier", AsteroidPullerCamera.orbital(PLAN, age), ship.x + 42, ship.y - 10, ship.z, age);
        }
        for (double age = RELEASE + 12; age < LAND; age += STEP) {
            double y = PLAN.anchorY + AsteroidPullerLance.lift(PLAN, age) + 5;
            RailgunCameraPath.Pose pose = AsteroidPullerCamera.isSpaceShot(age) ? AsteroidPullerCamera.orbital(PLAN, age)
                    : AsteroidPullerCamera.world(PLAN, layout, age);
            assertInFrame("falling lance", pose, PLAN.anchorX, y, PLAN.anchorZ, age);
        }
        for (double age = 256; age < FIRE; age += STEP) {
            double[] c = AsteroidTrajectory.position(age);
            assertInFrame("asteroid during tracking", AsteroidPullerCamera.world(PLAN, layout, age), c[0], c[1], c[2], age);
        }
        for (double age = 520; age < IMPACT - 8; age += STEP) {
            double[] c = AsteroidTrajectory.position(age);
            assertInFrame("diving asteroid", AsteroidPullerCamera.world(PLAN, layout, age), c[0], c[1], c[2], age);
        }
    }

    private static void assertInFrame(String subject, RailgunCameraPath.Pose pose, double x, double y, double z, double age) {
        double[] screen = AsteroidPullerCamera.project(pose, x, y, z, 1280, 720);
        assertNotNull(subject + " behind the lens at " + age, screen);
        assertTrue(subject + " leaves the frame at " + age + ": " + screen[0] + "," + screen[1],
                screen[0] > 40 && screen[0] < 1240 && screen[1] > 60 && screen[1] < 660);
    }

    private static double limit(double age) {
        // The departure tilt and the dive dolly are fast but continuous moves.
        if (age >= 486 && age < 530) return 3.5;
        if (age >= LAND && age < 260) return 1.6;
        return 2.5;
    }

    private static boolean near(double age, double cue) { return age >= cue - 0.5 && age < cue + 30; }

    private static double angle(RailgunCameraPath.Pose a, RailgunCameraPath.Pose b) {
        double[] da = direction(a), db = direction(b);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, da[0] * db[0] + da[1] * db[1] + da[2] * db[2]))));
    }

    private static double[] direction(RailgunCameraPath.Pose p) {
        double x = p.targetX - p.x, y = p.targetY - p.y, z = p.targetZ - p.z, length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    private static double sq(double value) { return value * value; }
}
