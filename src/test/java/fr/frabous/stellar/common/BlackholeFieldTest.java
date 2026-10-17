package fr.frabous.stellar.common;

import fr.frabous.stellar.api.*;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlackholeFieldTest {
    private final BlockTarget target = new BlockTarget(0, 64, 0);

    @Test public void nearestActiveFieldWinsWithoutStackingAndRailgunNeverOwns() {
        LaunchState first = launch(AttackId.BLACKHOLE, target, 0);
        LaunchState second = launch(AttackId.BLACKHOLE, new BlockTarget(500, 64, 0), 0);
        LaunchState railgun = launch(AttackId.RAILGUN, target, 0);
        assertNull(BlackholeField.ownerAt(Arrays.asList(first), 59, 0.5, 0.5));
        assertSame(first, BlackholeField.ownerAt(Arrays.asList(railgun, first, second), 60, 10.5, 0.5));
        assertSame(second, BlackholeField.ownerAt(Arrays.asList(first, second), 739, 450.5, 0.5));
        assertNull(BlackholeField.ownerAt(Arrays.asList(first), 740, 0.5, 0.5));
        assertNull(BlackholeField.ownerAt(Arrays.asList(first), 100, 1001.5, 0.5));
        assertNull(BlackholeField.ownerAt(Arrays.asList(first.withPhase(LaunchPhase.COMPLETE)), 100, 0.5, 0.5));
        LaunchState later = launch(AttackId.BLACKHOLE, target, 680);
        assertSame(later, BlackholeField.ownerAt(Arrays.asList(first, later), 740, 0.5, 0.5));
    }

    @Test public void velocityIsFiniteAndCappedAtAllRadiiHeightsAndResidenceTimes() {
        for (double radius : new double[]{0, 0.00001, 10, 52.5, 70, 1000}) {
            for (double height : new double[]{-10, 0, 64, 300, 420, 512, 10000}) {
                for (long residence : new long[]{0, 80, 240, 400, 739}) {
                    Vec3d velocity = BlackholeField.pullVelocity(target, radius + 0.5, height, 0.5, residence, Long.MIN_VALUE);
                    assertTrue(Double.isFinite(velocity.lengthVector()));
                    assertTrue(velocity.lengthVector() <= BlackholeField.MAX_SPEED + 0.000001);
                    if (height < 300) assertTrue(velocity.y > 0);
                    if (height > 512) assertTrue(velocity.y < 0);
                }
            }
        }
    }

    @Test public void unobstructedOuterArrivalReachesSpaceAndHasSustainedHorizonResidence() {
        Vec3d position = new Vec3d(1000.5, 64, 0.5), velocity = Vec3d.ZERO;
        int spaceTicks = 0, horizonTicks = 0;
        long residence = 0;
        for (int age = 60; age < 740; age++) {
            if (position.y >= 300 && radius(position) < 180) residence++;
            velocity = BlackholeField.approachVelocity(velocity, BlackholeField.pullVelocity(target,
                    position.x, position.y, position.z, residence, 1));
            position = position.add(velocity);
            double dx = position.x - 0.5, dz = position.z - 0.5;
            assertTrue(AttackGeometry.blackholeContains(target, position.x, position.z));
            if (position.y >= 300) spaceTicks++;
            if (position.y >= 300 && dx * dx + dz * dz < 70 * 70) horizonTicks++;
        }
        assertTrue("Outer arrivals need substantial time in space", spaceTicks >= 400);
        assertTrue("Horizon residence must be noticeable: " + horizonTicks, horizonTicks >= 200);
        assertTrue(position.y >= 300 && position.y < 440);
    }

    @Test public void lateArrivalsAreLiftedImmediatelyAndTheCenterDoesNotBecomeASingularNaN() {
        Vec3d lift = BlackholeField.pullVelocity(target, 500, 64, 500, 0, 7);
        assertTrue(lift.y > 0);
        Vec3d center = BlackholeField.pullVelocity(target, 0.5, 420, 0.5, 300, 7);
        assertTrue(center.lengthVector() > 0);
        assertTrue(Double.isFinite(center.lengthVector()));
    }

    @Test public void ellipsesHaveFastClosePassesAndSlowWidePasses() {
        Vec3d position = new Vec3d(100.5, 420, 0.5), velocity = Vec3d.ZERO;
        double minimum = Double.POSITIVE_INFINITY, maximum = 0, closeSpeed = 0, wideSpeed = 0;
        double minHeight = 1000, maxHeight = 0;
        // Hold the dissipation clock still to isolate one complete eccentric orbit.
        for (int tick = 0; tick < 1200; tick++) {
            velocity = BlackholeField.approachVelocity(velocity, BlackholeField.pullVelocity(target,
                    position.x, position.y, position.z, 0, 7));
            position = position.add(velocity);
            if (tick < 200) continue;
            double radius = radius(position), speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (radius < minimum) { minimum = radius; closeSpeed = speed; }
            if (radius > maximum) { maximum = radius; wideSpeed = speed; }
            minHeight = Math.min(minHeight, position.y); maxHeight = Math.max(maxHeight, position.y);
        }
        assertTrue("An orbit must visibly depart from a circle", maximum - minimum > 25);
        assertTrue("Periapsis must be faster than apoapsis", closeSpeed > wideSpeed * 1.3);
        assertTrue("An inclined orbit must cross above and below the disk", minHeight < 410 && maxHeight > 430);
    }

    @Test public void nearbyEntitiesKeepDistinctOrbitsAndSettleIntoAnInnerDisk() {
        Vec3d first = new Vec3d(90.5, 420, 0.5), second = first;
        Vec3d firstVelocity = Vec3d.ZERO, secondVelocity = Vec3d.ZERO;
        double earlyRadius = 0, lateRadius = 0, earlyHeight = 0, lateHeight = 0, separation = 0;
        for (int tick = 0; tick < 680; tick++) {
            firstVelocity = BlackholeField.approachVelocity(firstVelocity, BlackholeField.pullVelocity(target,
                    first.x, first.y, first.z, tick, 7));
            secondVelocity = BlackholeField.approachVelocity(secondVelocity, BlackholeField.pullVelocity(target,
                    second.x, second.y, second.z, tick, 8));
            first = first.add(firstVelocity); second = second.add(secondVelocity);
            separation = Math.max(separation, first.distanceTo(second));
            if (tick < 80) { earlyRadius += radius(first); earlyHeight = Math.max(earlyHeight, Math.abs(first.y - 420)); }
            if (tick >= 600) { lateRadius += radius(first); lateHeight = Math.max(lateHeight, Math.abs(first.y - 420)); }
            assertTrue(radius(first) > 25 && radius(first) < 150);
        }
        assertTrue("Neighbours must not form a synchronized ring", separation > 30);
        assertTrue("Dissipation must visibly contract the orbit", lateRadius < earlyRadius * 0.75);
        assertTrue("Disk settling must damp the inclination", lateHeight < earlyHeight * 0.65);
        assertTrue("The inner orbit must remain inside the horizon", radius(first) < 70);
    }

    @Test public void inertiaLimitsAccelerationWhenAnOverlappingFieldChangesDirection() {
        Vec3d velocity = new Vec3d(2, 1, 0);
        Vec3d desired = new Vec3d(-2, -1, 1);
        for (int tick = 0; tick < 40; tick++) {
            Vec3d next = BlackholeField.approachVelocity(velocity, desired);
            assertTrue(next.subtract(velocity).lengthVector() <= 0.3 + 0.000001);
            assertTrue(next.lengthVector() <= BlackholeField.MAX_SPEED);
            velocity = next;
        }
        assertEquals(0, velocity.distanceTo(desired), 0.000001);
    }

    @Test public void bodiesThatReachTheHorizonAreShreddedOnlyDuringTheLethalWindow() {
        for (double start : new double[] {12, 90, 400, 1000}) {
            Vec3d position = new Vec3d(start + 0.5, 64, 0.5), velocity = Vec3d.ZERO;
            long residence = 0;
            float health = 20;
            int deathAge = -1;
            for (int age = 60; age < 740 && deathAge < 0; age++) {
                if (position.y >= 300 && radius(position) < 180) residence++;
                velocity = BlackholeField.approachVelocity(velocity, BlackholeField.pullVelocity(target,
                        position.x, position.y, position.z, residence, 3));
                position = position.add(velocity);
                if (age % BlackholeTides.DAMAGE_INTERVAL_TICKS != 0) continue;
                double dy = position.y + 0.9 - AttackParameters.BLACKHOLE_CENTER_Y;
                double distance = Math.sqrt(radius(position) * radius(position) + dy * dy);
                float damage = BlackholeTides.pulseDamage(age, distance, 20);
                if (age < BlackholeTides.LETHAL_START_TICKS) assertEquals(0, damage, 0);
                health -= damage;
                if (health <= 0) deathAge = age;
            }
            assertTrue("Arrival from " + start + " blocks must be spaghettified, died at " + deathAge,
                    deathAge >= BlackholeTides.LETHAL_START_TICKS && deathAge < 740);
        }
    }

    private static double radius(Vec3d position) {
        double dx = position.x - 0.5, dz = position.z - 0.5;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static LaunchState launch(AttackId attack, BlockTarget target, long start) {
        return new LaunchState(UUID.randomUUID(), attack, 0, target, 1, start, LaunchPhase.MARKING);
    }
}
