package fr.frabous.stellar.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteMenuLayoutTest {
    @Test public void inversePickingMatchesAnIndependentThreeDimensionalProjection() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {1920, 1080}}) {
            for (double yaw : new double[] {-22, -11, -3, 3, 11, 22}) for (double pitch : new double[] {0, 3, 6}) {
                RemoteMenuLayout layout = layout(size[0], size[1], yaw, pitch);
                for (double x : new double[] {-330, -280, -150, 0, 174, 318})
                    for (double y : new double[] {-174, -102, 0, 106, 170}) {
                        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
                        double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
                        double rotatedX = cy * x + sy * sp * y;
                        double rotatedY = cp * y;
                        double rotatedZ = -sy * x + cy * sp * y;
                        double scale = Math.min((size[0] - 24) / 700.0, (size[1] - 20) / 380.0);
                        double screenX = size[0] * 0.5 + scale * rotatedX / (1 - rotatedZ / 1000);
                        double screenY = size[1] * 0.5 + scale * rotatedY / (1 - rotatedZ / 1000);
                        double[] picked = layout.unproject(screenX, screenY);
                        assertEquals(x, picked[0], 1.0e-9);
                        assertEquals(y, picked[1], 1.0e-9);
                    }
            }
        }
    }

    @Test public void gpuHomogeneousMatrixAndCpuPickingUseTheSamePlane() {
        RemoteMenuLayout layout = new RemoteMenuLayout();
        layout.update(427, 240, -13, 5, 0.75, 0.15, 1.1);
        float[] m = layout.matrix();
        for (int x = -330; x <= 330; x += 30) for (int y = -170; y <= 170; y += 20) {
            double w = m[3] * x + m[7] * y + m[15];
            double screenX = (m[0] * x + m[4] * y + m[12]) / w;
            double screenY = (m[1] * x + m[5] * y + m[13]) / w;
            double[] cpu = layout.project(x, y);
            assertEquals(cpu[0], screenX, 0.0001);
            assertEquals(cpu[1], screenY, 0.0001);
            double[] picked = layout.unproject(screenX, screenY);
            assertEquals(x, picked[0], 0.0002);
            assertEquals(y, picked[1], 0.0002);
        }
    }

    @Test public void allFiveCardsRemainClickableWhenRaisedAndTilted() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {960, 540}})
            for (int side : SIDES) for (double yaw : new double[] {-11, -7, -3}) {
                RemoteMenuLayout layout = layout(side, size[0], size[1], side * -yaw, 5);
                float[] hover = {0.0F, 0.3F, 0.7F, 1.0F, 0.5F};
                for (int row = 0; row < RemoteMenuLayout.ROWS; row++) {
                    double x = layout.cardX(1, row, hover[row]);
                    double y = RemoteMenuLayout.cardY(row);
                    for (double dx : new double[] {1, 131, 261}) for (double dy : new double[] {1, 20, 39}) {
                        double[] point = layout.project(x + dx, y + dy);
                        assertEquals(row, layout.rowAt(point[0], point[1], 1, hover));
                    }
                }
            }
    }

    @Test public void everyRemoteKeyIsPickedOnItsOwnCapAtEveryScaleAndLean() {
        double top = RemoteKey.FACE + RemoteKey.CAP;
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {960, 540}, {1920, 1080}})
            for (int side : SIDES) for (double lean : new double[] {-1, 0, 1}) {
                RemoteMenuLayout layout = leaned(side, size, lean);
                for (RemoteKey key : RemoteKey.KEYS) {
                    for (double fx : new double[] {-0.8, 0, 0.8}) for (double fy : new double[] {-0.8, 0, 0.8}) {
                        double x = key.x + fx * key.halfWidth, y = key.y + fy * key.halfHeight;
                        double[] screen = layout.projectRemote(x, y, top);
                        assertEquals(key, layout.keyAt(screen[0], screen[1]));
                        double[] back = layout.unprojectRemote(screen[0], screen[1], top);
                        assertEquals(x, back[0], 1.0e-9);
                        assertEquals(y, back[1], 1.0e-9);
                    }
                }
                double[] between = layout.projectRemote(0, (RemoteKey.FIRE.y + RemoteKey.INFO.y) / 2, top);
                assertNull(layout.keyAt(between[0], between[1]));
                double[] screen = layout.projectRemote(0, 6.6, top);
                assertNull("The status screen is not a key", layout.keyAt(screen[0], screen[1]));
            }
    }

    @Test public void gpuRemoteMatrixMatchesCpuProjection() {
        RemoteMenuLayout layout = layout(427, 240, -11, 6);
        layout.updateRemote(20, -12, 4, 9);
        float[] m = layout.remoteMatrix();
        for (double x = -4; x <= 4; x += 2) for (double y = -9; y <= 9; y += 3) for (double z = -1.5; z <= 2.5; z += 1) {
            double[] cpu = layout.projectRemote(x, y, z);
            assertEquals(cpu[0], m[0] * x + m[4] * y + m[8] * z + m[12], 1.0e-4);
            assertEquals(cpu[1], m[1] * x + m[5] * y + m[9] * z + m[13], 1.0e-4);
            assertEquals(1, m[15], 0);
        }
    }

    @Test public void remoteStandsOnTheGripSideBeyondTheSocketsAndTheThumbNeverCoversACard() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {1920, 360}})
            for (int side : SIDES) for (double lean : new double[] {-1, 1}) {
                RemoteMenuLayout layout = leaned(side, size, lean);
                double socket = layout.socketX(layout.cardsLeft());
                check(layout, size, bodyExtent(), socket + side * 3.6);
                check(layout, size, thumbExtent(side), socket - side * 4);
            }
    }

    @Test public void theRemoteFollowsTheHandHoldingIt() {
        RemoteMenuLayout right = layout(1, 640, 360, 7, 3), left = layout(-1, 640, 360, -7, 3);
        assertTrue(right.remoteX() > right.cardsLeft() + RemoteMenuLayout.CARD_WIDTH);
        assertTrue(left.remoteX() < left.cardsLeft());
        assertTrue("Right grip shows the panel left of the cards", right.panelShift() + 34 + 280 < right.cardsLeft());
        assertTrue("Left grip shows the panel right of the cards", left.panelShift() + 34 > left.cardsLeft() + RemoteMenuLayout.CARD_WIDTH);
        assertEquals(-left.yaw(0.5, 0), right.yaw(0.5, 0), 0);
        assertEquals(right.yaw(1, 0.4) - right.yaw(1, 0), left.yaw(1, 0.4) - left.yaw(1, 0), 1.0e-12);
    }

    private static void check(RemoteMenuLayout layout, int[] size, java.util.List<double[]> points, double limit) {
        int side = layout.side();
        for (double[] local : points) {
            double[] point = layout.projectRemote(local[0], local[1], local[2]);
            String where = (side > 0 ? "right " : "left ") + size[0] + "x" + size[1] + " local " + local[0] + "," + local[1] + "," + local[2];
            assertTrue(where + " off screen", point[0] > 0 && point[0] < size[0] && point[1] > 0 && point[1] < size[1]);
            for (int row = 0; row < RemoteMenuLayout.ROWS; row++) {
                double[] edge = layout.project(limit, RemoteMenuLayout.cardY(row) + RemoteMenuLayout.CARD_HEIGHT / 2);
                assertTrue(where + " reaches card " + row, side * point[0] > side * edge[0]);
            }
        }
    }

    @Test public void gapsAndPointsAboveTheFirstRowNeverSelectAStrike() {
        for (int side : SIDES) {
            RemoteMenuLayout layout = layout(side, 640, 360, side * 7, 3);
            float[] hover = new float[RemoteMenuLayout.ROWS];
            for (double y : new double[] {-108, -63, -17, 29, 75, 120}) {
                double[] point = layout.project(-side * -150, y);
                assertEquals("y=" + y, -1, layout.rowAt(point[0], point[1], 1, hover));
            }
            for (double x : new double[] {-290, 13, 35, 200}) {
                double[] point = layout.project(-side * x, -80);
                assertEquals(-1, layout.rowAt(point[0], point[1], 1, hover));
            }
        }
    }

    @Test public void initialOpeningCannotAccidentallyConsumeTheClickThatOpenedIt() {
        for (int side : SIDES) {
            RemoteMenuLayout layout = layout(side, 640, 360, side * 7, 3);
            double[] point = layout.project(-side * -150, -80);
            assertEquals(-1, layout.rowAt(point[0], point[1], 0.1, new float[RemoteMenuLayout.ROWS]));
            assertEquals(0, layout.rowAt(point[0], point[1], RemoteMenuLayout.OPEN_SECONDS, new float[RemoteMenuLayout.ROWS]));
        }
        assertEquals(0, RemoteMenuLayout.ease(-1), 0);
        assertEquals(1, RemoteMenuLayout.ease(2), 0);
        for (int row = 0; row < RemoteMenuLayout.ROWS; row++) assertEquals(1, RemoteMenuLayout.reveal(0.48, row), 0);
    }

    @Test public void briefingAndCloseButtonsArePickedInPerspectiveRatherThanScreenSpace() {
        for (int side : SIDES) {
            RemoteMenuLayout layout = layout(side, 427, 240, side * 11, 6);
            double[] briefing = layout.project(-side * 174, 99), close = layout.project(306, -160);
            assertTrue(layout.briefingAt(briefing[0], briefing[1]));
            assertFalse(layout.closeAt(briefing[0], briefing[1]));
            assertTrue(layout.closeAt(close[0], close[1]));
            assertFalse(layout.briefingAt(close[0], close[1]));
        }
    }

    @Test public void allInteractiveContentFitsInsideSmallAndUltrawideScreens() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {1920, 360}})
            for (double yaw : new double[] {-11, -3, 3, 11}) {
                RemoteMenuLayout layout = layout(size[0], size[1], yaw, 6);
                for (double x : new double[] {-330, 330}) for (double y : new double[] {-174, 170}) {
                    double[] point = layout.project(x, y);
                    assertTrue(point[0] > 0 && point[0] < size[0]);
                    assertTrue(point[1] > 0 && point[1] < size[1]);
                }
            }
    }

    /** Shell and emitter corners. */
    private static java.util.List<double[]> bodyExtent() {
        java.util.List<double[]> points = new java.util.ArrayList<>();
        for (double x : new double[] {-3.45, 3.45}) for (double y : new double[] {-8.8, 8.85}) for (double z : new double[] {-1.25, 1.2})
            points.add(new double[] {x, y, z});
        for (double x : new double[] {-1.45, 1.45}) for (double z : new double[] {-1.45, 1.45}) points.add(new double[] {x, 9.63, z});
        return points;
    }

    /** Every thumb joint hovering over every key, with a skin margin. */
    private static java.util.List<double[]> thumbExtent(int side) {
        java.util.List<double[]> points = new java.util.ArrayList<>();
        for (RemoteKey key : RemoteKey.KEYS) {
            RemoteThumb thumb = new RemoteThumb();
            thumb.side(side);
            thumb.hover(key);
            for (int frame = 0; frame < 240; frame++) thumb.update(1 / 120.0);
            for (double[] joint : new double[][] {thumb.base(), thumb.knuckle(), thumb.tip()})
                points.add(new double[] {joint[0] + side * 0.9, joint[1], joint[2] + 0.8});
        }
        return points;
    }

    private static final int[] SIDES = {1, -1};

    /** The menu's own pose: canvas and remote leaned together by the pointer. */
    private static RemoteMenuLayout leaned(int side, int[] size, double lean) {
        RemoteMenuLayout layout = new RemoteMenuLayout();
        layout.side(side);
        layout.update(size[0], size[1], layout.yaw(1, lean), 3 + lean * 3, 1, 0, 0);
        layout.poseRemote(lean, lean, 0);
        return layout;
    }

    private static RemoteMenuLayout layout(int width, int height, double yaw, double pitch) {
        return layout(1, width, height, yaw, pitch);
    }

    private static RemoteMenuLayout layout(int side, int width, int height, double yaw, double pitch) {
        RemoteMenuLayout layout = new RemoteMenuLayout();
        layout.side(side);
        layout.update(width, height, yaw, pitch, 1, 0, 0);
        return layout;
    }
}
