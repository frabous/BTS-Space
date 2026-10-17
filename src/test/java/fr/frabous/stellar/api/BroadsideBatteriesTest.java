package fr.frabous.stellar.api;

import static fr.frabous.stellar.api.BroadsideBatteries.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BroadsideBatteriesTest {
    private static final BlockTarget DEPLOYMENT = new BlockTarget(-81, 71, 243);

    @Test public void gondolaHangsOverTheDeploymentColumnWithTheBowTowardTheTarget() {
        for (int[] offset : new int[][]{{100, 0}, {0, 100}, {-70, -30}, {13, -250}, {-1, 1}}) {
            BlockTarget target = new BlockTarget(DEPLOYMENT.getX() + offset[0], 64, DEPLOYMENT.getZ() + offset[1]);
            Frame f = frame(DEPLOYMENT, target);
            double[] seat = f.world(GONDOLA_X, GONDOLA_FLOOR, 0);
            assertEquals(-80.5, seat[0], 1.0E-9);
            assertEquals(CarrierGunneryRules.SEAT_Y, seat[1], 1.0E-9);
            assertEquals(243.5, seat[2], 1.0E-9);
            double[] bow = f.world(GONDOLA_X + 100, GONDOLA_FLOOR, 0);
            double bx = bow[0] - seat[0], bz = bow[2] - seat[2], length = Math.hypot(offset[0], offset[1]);
            assertEquals(1, (bx * offset[0] + bz * offset[1]) / (100 * length), 1.0E-9);
            double[] local = f.local(f.world(-126, -60, 33)[0], f.world(-126, -60, 33)[1], f.world(-126, -60, 33)[2]);
            assertArrayEquals(new double[]{-126, -60, 33}, local, 1.0E-9);
        }
        assertEquals(0, frame(DEPLOYMENT, DEPLOYMENT).heading, 0);
    }

    @Test public void ringCentresSitOnTheMountsAuthoredInTheDestroyerModel() throws Exception {
        List<double[]> mounts = new ArrayList<>();
        try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("models/orbital_destroyer.bbmodel")), StandardCharsets.UTF_8)) {
            JsonObject model = new JsonParser().parse(reader).getAsJsonObject();
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject e = element.getAsJsonObject();
                if (e.get("name").getAsString().startsWith("turret_mount_")) mounts.add(bounds(e));
            }
        }
        assertEquals(COUNT, mounts.size());
        for (int i = 0; i < COUNT; i++) {
            int matches = 0;
            for (double[] m : mounts) {
                if (MOUNT_X[i] < m[0] || MOUNT_X[i] > m[3] || MOUNT_Z[i] < m[2] || MOUNT_Z[i] > m[5]) continue;
                matches++;
                assertEquals("Battery " + i + " ring must hang from the mount's underside", m[1], RING_Y[i], 1.0E-6);
                assertTrue(HULL_Y[i] > RING_Y[i]);
            }
            assertEquals("Battery " + i + " must sit on exactly one authored mount", 1, matches);
        }
    }

    @Test public void everyBatteryLaysOnTheGroundUnderTheGondolaAndItsBoreMeetsThePoint() {
        Frame f = frame(new BlockTarget(0, 64, 0), new BlockTarget(100, 64, 0));
        double[] point = {0.5, 64, 0.5};
        for (int i = 0; i < COUNT; i++) {
            double[] s = solution(f, i, point[0], point[1], point[2]);
            assertNotNull("Battery " + i + " cannot bear under the gondola", s);
            double[] t = trunnion(f, i, s[0]), d = direction(s[0], s[1]);
            double dx = point[0] - t[0], dy = point[1] - t[1], dz = point[2] - t[2], length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(1, (dx * d[0] + dy * d[1] + dz * d[2]) / length, 1.0E-9);
            assertTrue(s[1] >= MIN_PITCH && s[1] <= MAX_PITCH);
        }
    }

    @Test public void pointsAboveTheDepressionLimitUnderTheTrunnionOrBehindTheHullHaveNoSolution() {
        Frame f = frame(new BlockTarget(0, 64, 0), new BlockTarget(100, 64, 0));
        for (int i = 0; i < COUNT; i++) {
            double[] ahead = f.world(400, 10, 0);
            assertNull(solution(f, i, ahead[0], ahead[1], ahead[2]));
            double[] ring = ring(f, i);
            assertNull(solution(f, i, ring[0], 0, ring[2]));
        }
        // Aft wing batteries cannot fire shallow across the keel ramp and reactor keel, only on their own side.
        double[] starboard = f.world(-140, -50, 150), port = f.world(-140, -50, -150);
        assertNull(solution(f, 1, starboard[0], starboard[1], starboard[2]));
        assertNull(solution(f, 2, port[0], port[1], port[2]));
        double[] abeam = f.world(-48, -60, -200);
        assertNotNull(solution(f, 1, abeam[0], abeam[1], abeam[2]));
    }

    @Test public void stowedBarrelsAreClearOfTheHull() {
        Frame f = frame(new BlockTarget(0, 64, 0), new BlockTarget(0, 64, -100));
        for (int i = 0; i < COUNT; i++) assertTrue(clear(f, i, rest(f)[0], rest(f)[1]));
    }

    @Test public void drivesAreRateLimitedTakeTheShortWayAndSlewTicksMatchTheSteps() {
        double[] bearing = {Math.PI - 0.01, -0.3};
        step(bearing, -Math.PI + 0.01, -0.3);
        assertEquals(-Math.PI + 0.01, bearing[0], 1.0E-12);
        bearing = new double[]{0, REST_PITCH};
        double yaw = 2.0, pitch = Math.toRadians(-80);
        int expected = slewTicks(bearing, yaw, pitch), steps = 0;
        double previousYaw = bearing[0], previousPitch = bearing[1];
        while (Math.abs(wrap(yaw - bearing[0])) > 1.0E-12 || Math.abs(pitch - bearing[1]) > 1.0E-12) {
            step(bearing, yaw, pitch); steps++;
            assertTrue(Math.abs(wrap(bearing[0] - previousYaw)) <= YAW_RATE + 1.0E-12);
            assertTrue(Math.abs(bearing[1] - previousPitch) <= PITCH_RATE + 1.0E-12);
            previousYaw = bearing[0]; previousPitch = bearing[1];
            assertTrue(steps < 100);
        }
        assertEquals(expected, steps);
        assertEquals(0, slewTicks(bearing, yaw, pitch));
    }

    @Test public void twinMuzzlesStraddleTheBoreAtBarrelLength() {
        Frame f = frame(new BlockTarget(5, 64, 5), new BlockTarget(-40, 64, 90));
        double yaw = 0.7, pitch = -1.1;
        double[] a = muzzle(f, 3, 0, yaw, pitch), b = muzzle(f, 3, 1, yaw, pitch), t = trunnion(f, 3, yaw), d = direction(yaw, pitch);
        assertEquals(2 * BARREL_SPACING, Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])), 1.0E-9);
        for (int axis = 0; axis < 3; axis++) assertEquals(t[axis] + d[axis] * BARREL_LENGTH, (a[axis] + b[axis]) / 2, 1.0E-9);
        assertEquals(1, flightTicks(0));
        assertEquals(1, flightTicks(SHELL_SPEED));
        assertEquals(11, flightTicks(SHELL_SPEED * 10 + 0.1));
    }

    private static double[] bounds(JsonObject e) {
        List<double[]> points = new ArrayList<>();
        if (e.has("vertices")) {
            JsonArray origin = e.getAsJsonArray("origin");
            for (Map.Entry<String, JsonElement> v : e.getAsJsonObject("vertices").entrySet()) {
                JsonArray p = v.getValue().getAsJsonArray();
                points.add(new double[]{p.get(0).getAsDouble() + origin.get(0).getAsDouble(),
                        p.get(1).getAsDouble() + origin.get(1).getAsDouble(), p.get(2).getAsDouble() + origin.get(2).getAsDouble()});
            }
        } else for (String corner : new String[]{"from", "to"}) {
            JsonArray p = e.getAsJsonArray(corner);
            points.add(new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()});
        }
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : points) for (int axis = 0; axis < 3; axis++) {
            box[axis] = Math.min(box[axis], p[axis]); box[axis + 3] = Math.max(box[axis + 3], p[axis]);
        }
        return box;
    }

    private static double sq(double value) { return value * value; }
}
