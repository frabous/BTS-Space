package fr.frabous.stellar.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteThumbTest {
    private static final double FRAME = 1 / 120.0;

    @Test public void everyKeyIsPushedFullyWithRigidPhalangesFromBothHands() {
        for (int side : new int[] {1, -1})
            for (RemoteKey key : RemoteKey.KEYS) {
                RemoteThumb thumb = new RemoteThumb();
                thumb.side(side);
                thumb.press(key);
                boolean pushed = false;
                for (int frame = 0; frame < 150; frame++) {
                    thumb.update(FRAME);
                    assertEquals(RemoteThumb.PROXIMAL, distance(thumb.base(), thumb.knuckle()), 1.0e-6);
                    assertEquals(RemoteThumb.DISTAL, distance(thumb.knuckle(), thumb.tip()), 1.0e-6);
                    if (thumb.depress[key.ordinal()] >= 1) {
                        pushed = true;
                        double[] tip = thumb.tip();
                        assertEquals(key + " out of reach", side * key.x, tip[0], 0.02);
                        assertEquals(key.y, tip[1], 0.02);
                        assertEquals(key.top(1) + RemoteThumb.PAD, tip[2], 0.02);
                    }
                }
                assertTrue(key + " was never fully pressed", pushed);
            }
    }

    @Test public void aPressSinksOnlyItsKeyFlashesOnContactAndReleases() {
        RemoteThumb thumb = new RemoteThumb();
        thumb.press(RemoteKey.SUPERNOVA);
        float deepest = 0, brightest = 0;
        for (int frame = 0; frame < 120; frame++) {
            thumb.update(FRAME);
            for (RemoteKey key : RemoteKey.KEYS)
                if (key != RemoteKey.SUPERNOVA) assertEquals(0, thumb.depress[key.ordinal()], 0);
            deepest = Math.max(deepest, thumb.depress[RemoteKey.SUPERNOVA.ordinal()]);
            brightest = Math.max(brightest, thumb.flash[RemoteKey.SUPERNOVA.ordinal()]);
        }
        assertEquals(1, deepest, 0);
        assertTrue(brightest > 0.95F);
        assertEquals(0, thumb.depress[RemoteKey.SUPERNOVA.ordinal()], 0);
        assertNull("Thumb finished lingering", thumb.pressed());
    }

    @Test public void theWholePressReadsWithinAQuarterSecond() {
        RemoteThumb thumb = new RemoteThumb();
        thumb.press(RemoteKey.MENU);
        double time = 0;
        while (thumb.depress[RemoteKey.MENU.ordinal()] < 1 && time < 1) { thumb.update(FRAME); time += FRAME; }
        assertTrue("Contact took " + time + "s", time < 0.3);
    }

    @Test public void aNewPressReleasesThePreviousKeyAtOnce() {
        RemoteThumb thumb = new RemoteThumb();
        thumb.press(RemoteKey.RAILGUN);
        for (int frame = 0; frame < 120 && thumb.depress[RemoteKey.RAILGUN.ordinal()] < 1; frame++) thumb.update(FRAME);
        assertEquals(1, thumb.depress[RemoteKey.RAILGUN.ordinal()], 0);
        thumb.press(RemoteKey.DOWN);
        for (int frame = 0; frame < 15; frame++) thumb.update(FRAME);
        assertEquals(0, thumb.depress[RemoteKey.RAILGUN.ordinal()], 0);
        float flash = 0;
        for (int frame = 0; frame < 60; frame++) { thumb.update(FRAME); flash = Math.max(flash, thumb.flash[RemoteKey.DOWN.ordinal()]); }
        assertTrue(flash > 0.95F);
    }

    @Test public void idleThumbSettlesAboveTheFocusedKeyThenRests() {
        RemoteThumb thumb = new RemoteThumb();
        for (RemoteKey key : RemoteKey.KEYS) {
            thumb.hover(key);
            for (int frame = 0; frame < 240; frame++) thumb.update(FRAME);
            double[] above = thumb.above(key), tip = thumb.tip();
            for (int i = 0; i < 3; i++) assertEquals(key + " axis " + i, above[i], tip[i], 0.02);
        }
        thumb.hover(RemoteKey.CARRIER);
        for (int frame = 0; frame < 240; frame++) thumb.update(FRAME);
        thumb.hover(null);
        for (int frame = 0; frame < 240; frame++) thumb.update(FRAME);
        assertTrue("Rest pose sits off the strike keys", thumb.tip()[1] < RemoteKey.CARRIER.y - 0.5);
    }

    @Test public void solverClampsTargetsOutsideTheReachableShell() {
        double[] joint = new double[3];
        double[] far = RemoteThumb.solve(new double[3], new double[] {20, 0, 0}, 3, 2, new double[] {0, 0, 1}, joint);
        assertEquals(5, distance(new double[3], far), 1.0e-3);
        double[] near = RemoteThumb.solve(new double[3], new double[] {0.1, 0, 0}, 3, 2, new double[] {0, 0, 1}, joint);
        assertEquals(1, distance(new double[3], near), 1.0e-3);
        assertEquals(3, distance(new double[3], joint), 1.0e-9);
        assertTrue("Joint bends towards the pole", joint[2] > 0);
    }

    private static double distance(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
}
