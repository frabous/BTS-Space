package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import java.util.UUID;
import org.junit.Test;
import static fr.frabous.stellar.api.AsteroidPullerTiming.*;
import static org.junit.Assert.*;

/** Graviton Lance delivery, deployment and aiming; no Minecraft instance or GL context. */
public class AsteroidPullerLanceTest {
    private static final double STEP = 0.125;

    @Test public void lanceRidesTheCradleThenFallsWithoutSnapsAndLandsSoftly() {
        CarrierFlight.Plan plan = AsteroidPullerLance.plan(launch(new BlockTarget(256, 3, 256)));
        double carried = AsteroidPullerLance.lift(plan, RELEASE - 1.0E-7), dropped = AsteroidPullerLance.lift(plan, RELEASE);
        assertEquals("Lance jumps at release", carried, dropped, 1.0E-3);
        double previous = dropped;
        for (double age = RELEASE + STEP; age <= LAND; age += STEP) {
            double lift = AsteroidPullerLance.lift(plan, age);
            assertTrue("Lance rises during the drop at " + age, lift < previous + 1.0E-9);
            assertTrue("Lance snaps at " + age, previous - lift < 2.5);
            previous = lift;
        }
        assertEquals(0, AsteroidPullerLance.lift(plan, LAND), 1.0E-9);
        assertEquals(0, AsteroidPullerLance.fallSpeed(plan, LAND), 1.0E-9);
        assertEquals(0, AsteroidPullerLance.fallSpeed(plan, RELEASE), 1.0E-9);
        assertTrue("Retro burn must start while the lance is still fast", AsteroidPullerLance.fallSpeed(plan, RETRO) > 10);
    }

    @Test public void legsPlantBeforeTouchdownAndFeetRestOnTheGround() {
        LaunchState launch = launch(new BlockTarget(256, 3, 256));
        GravitonLanceModel.Pose landed = AsteroidPullerLance.pose(launch, LAND + 30);
        assertEquals(1, AsteroidPullerLance.pose(launch, LAND).legs, 1.0E-9);
        assertEquals(1, landed.anchors, 1.0E-9);
        for (int leg = 0; leg < 4; leg++) {
            double[] foot = GravitonLanceModel.foot(landed, leg);
            assertEquals("Foot " + leg + " floats or sinks", 0, foot[1] - 0.55 - landed.squat, 0.06);
            assertEquals(GravitonLanceModel.FOOT_RADIUS, Math.hypot(foot[0], foot[2]), 0.1);
        }
    }

    @Test public void barrelLeadsItsShotOntoTheInterceptAndThenHoldsTheTether() {
        LaunchState launch = launch(new BlockTarget(256, 3, 256));
        GravitonLanceModel.Pose fire = AsteroidPullerLance.pose(launch, FIRE);
        assertAimed(fire, AsteroidTrajectory.interceptPoint(), 0.05);
        for (double age = LOCK; age < SEVER; age += 0.5)
            assertAimed(AsteroidPullerLance.pose(launch, age), AsteroidTrajectory.position(age), 0.8);
        double[] muzzle = GravitonLanceModel.muzzle(fire);
        assertTrue("Extended lance stands tall", muzzle[1] > 18 && muzzle[1] < 32);
        assertTrue(Math.hypot(muzzle[0], muzzle[2]) < 22);
    }

    @Test public void turretSlewsWithoutJumps() {
        LaunchState launch = launch(new BlockTarget(256, 3, 256));
        GravitonLanceModel.Pose previous = AsteroidPullerLance.pose(launch, 192);
        for (double age = 192 + STEP; age < IMPACT; age += STEP) {
            GravitonLanceModel.Pose pose = AsteroidPullerLance.pose(launch, age);
            double yaw = Math.abs(Math.atan2(Math.sin(pose.yaw - previous.yaw), Math.cos(pose.yaw - previous.yaw)));
            assertTrue("Yaw jump at " + age, yaw < Math.toRadians(1.5));
            assertTrue("Pitch jump at " + age, Math.abs(pose.pitch - previous.pitch) < Math.toRadians(1.5));
            previous = pose;
        }
        assertFalse(AsteroidPullerLance.pose(launch, IMPACT).visible);
    }

    private static void assertAimed(GravitonLanceModel.Pose pose, double[] target, double degrees) {
        double[] muzzle = GravitonLanceModel.muzzle(pose), axis = GravitonLanceModel.axis(pose);
        double dx = target[0] - muzzle[0], dy = target[1] - muzzle[1], dz = target[2] - muzzle[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angle = Math.toDegrees(Math.acos(Math.min(1, (dx * axis[0] + dy * axis[1] + dz * axis[2]) / length)));
        assertTrue("Barrel off target by " + angle + " degrees at " + pose.age, angle < degrees);
    }

    private static LaunchState launch(BlockTarget target) {
        return new LaunchState(new UUID(1, 2), AttackId.ASTEROID_PULLER, 0, target, target, 42, 100, LaunchPhase.MARKING);
    }
}
