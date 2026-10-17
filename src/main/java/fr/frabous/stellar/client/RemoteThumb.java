package fr.frabous.stellar.client;

/**
 * Thumb choreography for the remote: travel to a key, push it, linger, then drift back to the
 * focused key or to rest. Pure math in remote pixels; side -1 mirrors X for a left hand. A
 * two-bone solve keeps both phalanges at constant length, and the grip slides a little along
 * the body so far keys stay within reach.
 */
final class RemoteThumb {
    static final double PROXIMAL = 3.7, DISTAL = 3.3, PAD = 0.55, HOVER = 0.85;
    static final double APPROACH = 0.045, PUSH = 0.05, HOLD = 0.07, RELEASE = 0.085, LINGER = 0.3;
    static final double GRIP_LIMIT = 1.6, GRIP_FOLLOW = 0.3, COMFORT = 5;
    /** Thumb root on the right hand at zero grip shift: low on the right edge, level with the faceplate. */
    static final double[] BASE = {3.75, -2.2, 1.1};
    private static final double[] POLE = normalize(0.3, -0.3, 1);
    private static final double[] REST = {2.2, -0.6, RemoteKey.FACE + RemoteKey.CAP + PAD + 0.5};

    final float[] depress = new float[RemoteKey.KEYS.length];
    final float[] flash = new float[RemoteKey.KEYS.length];
    private final double[] tip = REST.clone(), from = new double[3], knuckle = new double[3], base = new double[3];
    private RemoteKey pressed, hovered;
    private boolean contacted;
    private double age, travel, grip, time;
    private int side = 1, presses;

    RemoteThumb() { solve(); }

    void press(RemoteKey key) {
        pressed = key; age = 0; contacted = false;
        System.arraycopy(tip, 0, from, 0, 3);
        travel = clamp(0.05 + distance(tip, above(key)) * 0.017, 0.06, 0.17);
        presses++;
    }

    /** Key the idle thumb hovers over; null rests it beside the d-pad. */
    void hover(RemoteKey key) { hovered = key; }

    void side(int value) {
        int next = value < 0 ? -1 : 1;
        if (next == side) return;
        side = next; tip[0] = -tip[0]; from[0] = -from[0];
        solve();
    }

    void update(double dt) {
        dt = clamp(dt, 0, 0.1);
        time += dt;
        for (RemoteKey key : RemoteKey.KEYS) {
            flash[key.ordinal()] *= (float) Math.exp(-dt * 4.5);
            if (key != pressed || !contacted) depress[key.ordinal()] = (float) Math.max(0, depress[key.ordinal()] - dt / RELEASE);
        }
        if (pressed != null) pressStep(dt);
        else {
            double[] target = hovered != null ? above(hovered) : rest();
            follow(target, 1 - Math.exp(-dt * 9));
        }
        double want = clamp((tip[1] - BASE[1]) * GRIP_FOLLOW, -GRIP_LIMIT, GRIP_LIMIT);
        grip += (want - grip) * (1 - Math.exp(-dt * 10));
        solve();
    }

    private void pressStep(double dt) {
        age += dt;
        RemoteKey key = pressed;
        double[] above = above(key);
        double t = age - travel, top = key.top(0) + PAD;
        if (age < travel) {
            double u = ease(age / travel);
            for (int i = 0; i < 3; i++) tip[i] = from[i] + (above[i] - from[i]) * u;
            tip[2] += Math.sin(Math.PI * u) * 0.6;
        } else if (t < APPROACH) {
            place(above, top + HOVER * (1 - t / APPROACH * t / APPROACH));
        } else if (t < APPROACH + PUSH) {
            if (!contacted) { contacted = true; flash[key.ordinal()] = 1; }
            push(key, above, (t - APPROACH) / PUSH);
        } else if (t < APPROACH + PUSH + HOLD) {
            push(key, above, 1);
        } else if (t < APPROACH + PUSH + HOLD + RELEASE) {
            push(key, above, 1 - (t - APPROACH - PUSH - HOLD) / RELEASE);
        } else if (t < APPROACH + PUSH + HOLD + RELEASE + LINGER) {
            contacted = false;
            follow(above, 1 - Math.exp(-dt * 14));
        } else {
            pressed = null; contacted = false;
        }
    }

    private void push(RemoteKey key, double[] above, double amount) {
        depress[key.ordinal()] = (float) clamp(amount, 0, 1);
        place(above, key.top(amount) + PAD);
    }

    private void place(double[] above, double z) { tip[0] = above[0]; tip[1] = above[1]; tip[2] = z; }

    private void follow(double[] target, double k) {
        for (int i = 0; i < 3; i++) tip[i] += (target[i] - tip[i]) * k;
    }

    private double[] rest() {
        return new double[] {side * REST[0] + Math.sin(time * 1.3) * 0.08, REST[1] + Math.sin(time * 0.9) * 0.07,
                REST[2] + Math.sin(time * 1.7) * 0.05};
    }

    double[] above(RemoteKey key) { return new double[] {side * key.x, key.y, key.top(0) + PAD + HOVER}; }

    private void solve() {
        base[0] = side * BASE[0]; base[1] = BASE[1] + grip;
        // Near keys: the root sinks behind the edge so the thumb flexes instead of rearing up.
        double planar = Math.hypot(tip[0] - base[0], tip[1] - base[1]);
        base[2] = BASE[2] - clamp((COMFORT - planar) * 0.3, 0, 0.8);
        double[] pole = {side * POLE[0], POLE[1], POLE[2]};
        double[] reached = solve(base, tip, PROXIMAL, DISTAL, pole, knuckle);
        System.arraycopy(reached, 0, tip, 0, 3);
    }

    /**
     * Two-bone solve. Writes the middle joint into {@code joint} and returns the reached end,
     * pulled back onto the reachable shell when the target is too close or too far.
     */
    static double[] solve(double[] root, double[] target, double first, double second, double[] pole, double[] joint) {
        double[] d = {target[0] - root[0], target[1] - root[1], target[2] - root[2]};
        double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double[] dir = length < 1.0e-9 ? new double[] {0, 1, 0} : new double[] {d[0] / length, d[1] / length, d[2] / length};
        double reach = clamp(length, Math.abs(first - second) + 1.0e-4, first + second - 1.0e-4);
        double along = (first * first - second * second + reach * reach) / (2 * reach);
        double height = Math.sqrt(Math.max(0, first * first - along * along));
        double dot = pole[0] * dir[0] + pole[1] * dir[1] + pole[2] * dir[2];
        double[] bend = {pole[0] - dir[0] * dot, pole[1] - dir[1] * dot, pole[2] - dir[2] * dot};
        double bendLength = Math.sqrt(bend[0] * bend[0] + bend[1] * bend[1] + bend[2] * bend[2]);
        if (bendLength < 1.0e-9) bend = new double[] {0, 0, 1}; else for (int i = 0; i < 3; i++) bend[i] /= bendLength;
        for (int i = 0; i < 3; i++) joint[i] = root[i] + dir[i] * along + bend[i] * height;
        return new double[] {root[0] + dir[0] * reach, root[1] + dir[1] * reach, root[2] + dir[2] * reach};
    }

    double[] tip() { return tip.clone(); }
    double[] knuckle() { return knuckle.clone(); }
    double[] base() { return base.clone(); }
    /** Grip slide along the body, shared by the arm so the thumb root never detaches. */
    double grip() { return grip; }
    int side() { return side; }
    int presses() { return presses; }
    RemoteKey pressed() { return pressed; }
    RemoteKey hovered() { return hovered; }

    private static double ease(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double distance(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
