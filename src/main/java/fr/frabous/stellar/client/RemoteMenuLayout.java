package fr.frabous.stellar.client;

/** The same projective plane drives rendering and pointer picking, at every GUI scale. */
public final class RemoteMenuLayout {
    public static final int ROWS = 5;
    public static final double OPEN_SECONDS = 0.48, CLOSE_SECONDS = 0.22;
    public static final double CARD_WIDTH = 262, CARD_HEIGHT = 40, CARD_PITCH = 46;
    /**
     * Columns for a remote held in the right hand: projection panel, cards, then the remote on the right.
     * A left-hand grip mirrors them. The panel and status blocks are authored right of the cards.
     */
    private static final double CARDS = -12, REMOTE_X = 312, PANEL_SHIFT = -348;
    /** Remote height in plane units; one remote pixel spans REMOTE_UNITS. */
    public static final double REMOTE_Y = -6, REMOTE_UNITS = 8.4;
    /** Resting pose, turned towards the cards with its top tipped back; the pointer leans it further. */
    public static final double REMOTE_YAW = 4, REMOTE_PITCH = -9, REMOTE_ROLL = 4, LEAN_YAW = 3, LEAN_PITCH = 4;
    private static final double DISTANCE = 1000;
    private final float[] matrix = new float[16];
    private final float[] remoteMatrix = new float[16];
    private final double[] remote = new double[9];
    private double centerX, centerY, scale, cy, sy, cp, sp, pitch, remoteX, remoteY;
    private int side = 1;

    /** Side of the hand holding the remote: 1 right, -1 left. */
    public void side(int value) { side = value < 0 ? -1 : 1; }
    public int side() { return side; }
    public double cardsLeft() { return side > 0 ? CARDS : -CARDS - CARD_WIDTH; }
    public double remoteX() { return side * REMOTE_X; }
    public double panelShift() { return side > 0 ? PANEL_SHIFT : 0; }
    /** The card edge facing the remote, where its link line plugs in. */
    public double socketX(double cardX) { return side > 0 ? cardX + CARD_WIDTH + 4 : cardX - 4; }

    /** Canvas yaw turning the remote's column away, easing in while opening; the pointer leans it. */
    public double yaw(double opening, double lean) { return side * (7 + 15 * (1 - opening)) + lean * 4; }

    /** Rest pose mirrored with the grip, leaned by the pointer in [-1, 1]. */
    public void poseRemote(double leanX, double leanY, double lift) {
        updateRemote(-side * REMOTE_YAW + leanX * LEAN_YAW, REMOTE_PITCH - leanY * LEAN_PITCH, -side * REMOTE_ROLL, lift);
    }

    public void update(int width, int height, double yaw, double pitch, double opening, double closing,
                       double floatOffset) {
        centerX = width * 0.5;
        centerY = height * 0.5 + (18 * (1 - opening) - 12 * closing + floatOffset)
                * Math.min(width / 700.0, height / 380.0);
        scale = Math.min((width - 24) / 700.0, (height - 20) / 380.0)
                * (0.94 + 0.06 * opening) * (1 - 0.08 * closing);
        scale = Math.max(0.01, scale);
        this.pitch = pitch;
        cy = Math.cos(Math.toRadians(yaw)); sy = Math.sin(Math.toRadians(yaw));
        cp = Math.cos(Math.toRadians(pitch)); sp = Math.sin(Math.toRadians(pitch));
        // Homogeneous screen coordinates, consumed under Minecraft's existing GUI orthographic projection.
        // Keep its projection/viewport intact, and let w supply genuine perspective foreshortening.
        putColumn(0, cy, 0, -sy);
        putColumn(4, sy * sp, cp, cy * sp);
        putColumn(8, sy * cp, -sp, cy * cp);
        matrix[12] = (float) centerX; matrix[13] = (float) centerY;
        matrix[14] = 0; matrix[15] = 1;
    }

    private void putColumn(int offset, double x, double y, double z) {
        double w = -z / DISTANCE;
        matrix[offset] = (float) (scale * x + centerX * w);
        matrix[offset + 1] = (float) (scale * y + centerY * w);
        matrix[offset + 2] = 0;
        matrix[offset + 3] = (float) w;
    }

    public float[] matrix() { return matrix; }

    public double[] project(double x, double y) {
        double w = 1 + sy * x / DISTANCE - cy * sp * y / DISTANCE;
        return new double[] {centerX + scale * (cy * x + sy * sp * y) / w,
                centerY + scale * cp * y / w};
    }

    public double[] unproject(double x, double y) {
        double u = (x - centerX) / scale, v = (y - centerY) / scale;
        double a = cy - u * sy / DISTANCE, b = sy * sp + u * cy * sp / DISTANCE;
        double c = -v * sy / DISTANCE, d = cp + v * cy * sp / DISTANCE;
        double determinant = a * d - b * c;
        if (Math.abs(determinant) < 1.0e-8) return new double[] {Double.NaN, Double.NaN};
        return new double[] {(u * d - b * v) / determinant, (a * v - u * c) / determinant};
    }

    /**
     * Places the remote on the plane's anchor at its perspective scale, then turns it by the plane's
     * yaw and pitch plus its own pose. Orthographic like inventory items, so depth sorts the hand.
     * Call after {@link #update}; lift slides it down in plane units.
     */
    public void updateRemote(double yaw, double pitchOffset, double roll, double lift) {
        double x = remoteX(), y = REMOTE_Y + lift;
        double[] anchor = project(x, y);
        double w = 1 + sy * x / DISTANCE - cy * sp * y / DISTANCE;
        double k = scale * REMOTE_UNITS / w;
        double canvasYaw = Math.toDegrees(Math.atan2(sy, cy));
        double a = Math.toRadians(canvasYaw + yaw), b = Math.toRadians(pitchOffset - pitch), c = Math.toRadians(roll);
        double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b), cc = Math.cos(c), sc = Math.sin(c);
        // R = Ry(a) * Rx(b) * Rz(c), row-major.
        double[] r = {
                ca * cc + sa * sb * sc, -ca * sc + sa * sb * cc, sa * cb,
                cb * sc, cb * cc, -sb,
                -sa * cc + ca * sb * sc, sa * sc + ca * sb * cc, ca * cb};
        for (int i = 0; i < 3; i++) {
            remote[i] = k * r[i];
            remote[3 + i] = -k * r[3 + i];
            remote[6 + i] = k * r[6 + i];
        }
        remoteX = anchor[0]; remoteY = anchor[1];
        for (int column = 0; column < 3; column++) {
            remoteMatrix[column * 4] = (float) remote[column];
            remoteMatrix[column * 4 + 1] = (float) remote[3 + column];
            remoteMatrix[column * 4 + 2] = (float) remote[6 + column];
            remoteMatrix[column * 4 + 3] = 0;
        }
        remoteMatrix[12] = (float) remoteX; remoteMatrix[13] = (float) remoteY; remoteMatrix[14] = 0; remoteMatrix[15] = 1;
    }

    public float[] remoteMatrix() { return remoteMatrix; }

    /** Screen position of a point in remote pixels. */
    public double[] projectRemote(double x, double y, double z) {
        return new double[] {remoteX + remote[0] * x + remote[1] * y + remote[2] * z,
                remoteY + remote[3] * x + remote[4] * y + remote[5] * z};
    }

    /** Point on the remote plane at height z that lands under the screen position. */
    public double[] unprojectRemote(double screenX, double screenY, double z) {
        double u = screenX - remoteX - remote[2] * z, v = screenY - remoteY - remote[5] * z;
        double determinant = remote[0] * remote[4] - remote[1] * remote[3];
        if (Math.abs(determinant) < 1.0e-9) return new double[] {Double.NaN, Double.NaN};
        return new double[] {(u * remote[4] - remote[1] * v) / determinant, (remote[0] * v - u * remote[3]) / determinant};
    }

    /** Key whose cap top lies under the pointer, or null. */
    RemoteKey keyAt(double screenX, double screenY) {
        double[] point = unprojectRemote(screenX, screenY, RemoteKey.FACE + RemoteKey.CAP);
        for (RemoteKey key : RemoteKey.KEYS) if (key.contains(point[0], point[1])) return key;
        return null;
    }

    public static double reveal(double age, int row) { return ease((age - row * 0.035) / 0.34); }
    /** Cards slide in from the panel side and lean towards the remote when focused. */
    public double cardX(double age, int row, double hover) {
        return cardsLeft() - side * ((1 - reveal(age, row)) * 42 - hover * 6);
    }
    public static double cardY(int row) { return -106 + row * CARD_PITCH; }
    public static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public int rowAt(double screenX, double screenY, double age, float[] hover) {
        if (age < OPEN_SECONDS) return -1;
        double[] local = unproject(screenX, screenY);
        for (int row = 0; row < ROWS; row++)
            if (inside(local, cardX(age, row, hover[row]), cardY(row), CARD_WIDTH, CARD_HEIGHT)) return row;
        return -1;
    }

    public boolean briefingAt(double x, double y) { return inside(unproject(x, y), 48 + panelShift(), 88, 252, 22); }
    public boolean closeAt(double x, double y) { return inside(unproject(x, y), 295, -170, 23, 21); }

    private static boolean inside(double[] point, double x, double y, double width, double height) {
        return point[0] >= x && point[0] < x + width && point[1] >= y && point[1] < y + height;
    }
}
