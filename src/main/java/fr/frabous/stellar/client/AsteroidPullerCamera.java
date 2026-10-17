package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AsteroidTrajectory;

import static fr.frabous.stellar.api.AsteroidPullerTiming.*;

/**
 * SS-03A shot list as one continuous camera. Every pose is anchor-relative. The only two lens changes
 * (world to orbit, orbit to world) hide behind a beacon flash and a cloud punch; all other changes
 * are blended moves, so there is no hard cut. Pure math: no Minecraft or OpenGL dependency.
 */
final class AsteroidPullerCamera {
    /** Ground framing inputs, latched once per film. Offsets are anchor-relative. */
    static final class Layout {
        final double originX, originY, originZ, yaw, reach;
        Layout(double originX, double originY, double originZ, double yaw, double reach) {
            this.originX = originX; this.originY = originY; this.originZ = originZ; this.yaw = yaw; this.reach = reach;
        }
    }

    static final double CLEARANCE = AsteroidModel.MAX_RADIUS + 18;

    private AsteroidPullerCamera() { }

    static boolean isSpaceShot(double age) { return age >= SPACE_CUT && age < WORLD_CUT; }

    /** White flash hiding the world-to-orbit lens change; peaks on the cut. */
    static double spaceFlash(double age) {
        if (age < SPACE_CUT) return smooth((age - SPACE_CUT + 5) / 5);
        return 1 - smooth((age - SPACE_CUT) / 7);
    }

    /** Cloud layer the falling camera punches through; opaque across the orbit-to-world change. */
    static double cloudWipe(double age) {
        if (age < WORLD_CUT) return smooth((age - WORLD_CUT + 12) / 11);
        return 1 - smooth((age - WORLD_CUT - 1) / 13);
    }

    // --- Orbital lens ----------------------------------------------------------------------

    static RailgunCameraPath.Pose orbital(CarrierFlight.Plan plan, double age) {
        CarrierFlight.State ship = CarrierFlight.at(plan, Math.max(age, plan.exitStart));
        double apertureX = ship.arrivalPlaneX, apertureY = ship.arrival.y, apertureZ = ship.arrivalPlaneZ;
        double ax = plan.anchorX, ay = plan.anchorY, az = plan.anchorZ;
        // Three-quarter view downrange of the aperture, tilted so the planet fills the lower third.
        RailgunCameraPath.Pose wide = pose(ax + 380, ay + 1000, az + 780, apertureX, apertureY - 300, apertureZ, -3, 58);
        RailgunCameraPath.Pose drift = pose(ax + 340, ay + 960, az + 720, apertureX + 62, apertureY - 280, apertureZ, -2, 58);
        double shipCentre = ship.x + 42 * plan.hx();
        RailgunCameraPath.Pose follow = pose(ax + 120, ay + 650, az + 420, shipCentre, ship.y - 25, ship.z, 0, 58);
        RailgunCameraPath.Pose bay = pose(ax + 58, ay + 676, az + 112, ax, ay + 708, az, 2, 56);
        if (age < WARP_EXIT) return blend(wide, drift, (age - SPACE_CUT) / (WARP_EXIT - SPACE_CUT));
        if (age < STATION) return blend(drift, follow, (age - WARP_EXIT) / (STATION - WARP_EXIT));
        if (age < RELEASE) return blend(follow, bay, (age - STATION) / (RELEASE - STATION - 4));
        return blend(bay, chase(plan, age), (age - RELEASE) / 10);
    }

    /** Camera riding beside the falling lance; the world shot starts with the same offset. */
    static RailgunCameraPath.Pose chase(CarrierFlight.Plan plan, double age) {
        double centre = plan.anchorY + AsteroidPullerLance.lift(plan, age) + 5;
        double low = plan.anchorY + 6.5;
        double height = softMax(centre + 8, low, 4);
        double settle = smooth((age - 150) / 22);
        double ox = 14 + 4 * settle, oz = 22 + 5 * settle;
        double look = softMax(centre - 2 * (1 - settle), plan.anchorY + 4.5, 2);
        return pose(plan.anchorX + ox, height, plan.anchorZ + oz, plan.anchorX, look, plan.anchorZ, 0, 62 + 4 * settle);
    }

    // --- World lens ------------------------------------------------------------------------

    static RailgunCameraPath.Pose world(CarrierFlight.Plan plan, Layout layout, double age) {
        RailgunCameraPath.Pose pose;
        if (age < SPACE_CUT) pose = designation(plan, layout, age);
        else if (age < LAND) pose = chase(plan, age);
        else if (age < 236) pose = blend(chase(plan, LAND), deploy(plan, layout, age), (age - LAND) / 18);
        else if (age < 256) pose = blend(deploy(plan, layout, age), track(plan, layout, age), (age - 236) / 20);
        else if (age < FIRE) pose = track(plan, layout, age);
        else if (age < 486) pose = blend(track(plan, layout, age), crane(plan, layout, age), (age - FIRE) / 50);
        else if (age < AFTERMATH) pose = blend(crane(plan, layout, age), dive(plan, layout, age), (age - 486) / 40);
        else pose = blend(dive(plan, layout, age), aftermath(plan, layout, age), (age - AFTERMATH) / 120);
        pose = shake(pose, age);
        return clear(pose, age);
    }

    /** From the caster toward the beacon, then up the uplink beam into the flash. */
    private static RailgunCameraPath.Pose designation(CarrierFlight.Plan plan, Layout layout, double age) {
        double dx = plan.anchorX - layout.originX, dz = plan.anchorZ - layout.originZ, length = Math.max(1, Math.hypot(dx, dz));
        double cx = layout.originX + dx / length * 1.4 - dz / length * 0.9, cz = layout.originZ + dz / length * 1.4 + dx / length * 0.9;
        double rise = smooth((age - 10) / 14);
        double lookY = plan.anchorY + 1.5 + rise * rise * 320;
        return pose(cx, layout.originY + 1.2, cz, plan.anchorX, lookY, plan.anchorZ, 0, 70 - 6 * rise);
    }

    /** Low orbit around the planted lance, then the lens tilts up to the departing carrier. */
    private static RailgunCameraPath.Pose deploy(CarrierFlight.Plan plan, Layout layout, double age) {
        double u = smooth((age - LAND) / 60), angle = Math.atan2(27, 18) + u * 1.15;
        double radius = (32 - 6 * u) * layout.reach, height = 6.5 - 2.5 * u;
        double cx = plan.anchorX + Math.cos(angle) * radius, cz = plan.anchorZ + Math.sin(angle) * radius;
        RailgunCameraPath.Pose lance = pose(cx, plan.anchorY + height, cz, plan.anchorX, plan.anchorY + 6, plan.anchorZ, 0, 66);
        CarrierFlight.State ship = CarrierFlight.at(plan, Math.min(age, plan.jump));
        double shipX = ship.x + 120 * plan.hx(), shipZ = ship.z + 120 * plan.hz();
        RailgunCameraPath.Pose sky = pose(cx, plan.anchorY + height, cz, shipX, ship.y - 20, shipZ, -4, 74);
        return blend(lance, sky, (age - 194) / 22);
    }

    /** Over-the-shoulder behind the lance, kept opposite the asteroid so both stay framed. */
    private static RailgunCameraPath.Pose track(CarrierFlight.Plan plan, Layout layout, double age) {
        double[] rock = AsteroidTrajectory.position(Math.min(age, LOCK + 10));
        double hx = rock[0] - plan.anchorX, hz = rock[2] - plan.anchorZ, length = Math.max(1, Math.hypot(hx, hz));
        hx /= length; hz /= length;
        double radius = 24 * layout.reach;
        double cx = plan.anchorX - hx * radius - hz * 7, cz = plan.anchorZ - hz * radius + hx * 7;
        double cy = plan.anchorY + 4.5 + 2.5 * smooth((age - CHARGE) / 40);
        double[] toRock = normalize(rock[0] - cx, rock[1] - cy, rock[2] - cz);
        double[] toLance = normalize(plan.anchorX - cx, plan.anchorY + 9 - cy, plan.anchorZ - cz);
        // Long-lens insert while the rock crosses the sky, racked back out so the charge shares the frame.
        double zoom = smooth((age - 250) / 30) * (1 - smooth((age - 318) / 28));
        double weight = 0.66 + 0.1 * smooth((age - 300) / 60);
        weight += (0.97 - weight) * zoom;
        double[] look = normalize(toRock[0] * weight + toLance[0] * (1 - weight), toRock[1] * weight + toLance[1] * (1 - weight),
                toRock[2] * weight + toLance[2] * (1 - weight));
        return pose(cx, cy, cz, cx + look[0] * 100, cy + look[1] * 100, cz + look[2] * 100, 0, 76 - 50 * zoom);
    }

    /** After the shot: rise and pull back until the tether and the bending path share the frame. */
    private static RailgunCameraPath.Pose crane(CarrierFlight.Plan plan, Layout layout, double age) {
        double u = smooth((age - FIRE) / 120);
        double cx = plan.anchorX + (-10 + 50 * u) * layout.reach, cz = plan.anchorZ + (40 + 140 * u) * layout.reach;
        double cy = plan.anchorY + 12 + 66 * u;
        double[] rock = AsteroidTrajectory.position(age);
        double[] toRock = normalize(rock[0] - cx, rock[1] - cy, rock[2] - cz);
        double[] toLance = normalize(plan.anchorX - cx, plan.anchorY + 8 - cy, plan.anchorZ - cz);
        // Once hooked, the lens tightens on the rock so the field rings and the bend read at 1 km.
        double zoom = smooth((age - LOCK - 6) / 40), weight = 0.8 + 0.14 * zoom;
        double[] look = normalize(toRock[0] * weight + toLance[0] * (1 - weight), toRock[1] * weight + toLance[1] * (1 - weight),
                toRock[2] * weight + toLance[2] * (1 - weight));
        return pose(cx, cy, cz, cx + look[0] * 100, cy + look[1] * 100, cz + look[2] * 100, -2, 74 - 34 * zoom);
    }

    /** Ground witness south-east of the strike: the dive comes down across the frame. */
    private static RailgunCameraPath.Pose dive(CarrierFlight.Plan plan, Layout layout, double age) {
        double cx = plan.anchorX + 95 * layout.reach, cz = plan.anchorZ + 225 * layout.reach, cy = plan.anchorY + 34;
        double[] rock = AsteroidTrajectory.position(Math.min(age, IMPACT));
        double follow = 1 - smooth((age - 540) / 30);
        double[] toRock = normalize(rock[0] - cx, rock[1] - cy, rock[2] - cz);
        double[] toZero = normalize(plan.anchorX - cx, plan.anchorY + 40 - cy, plan.anchorZ - cz);
        double rise = age < IMPACT ? 0 : smooth((age - IMPACT) / 60);
        double[] toCloud = normalize(plan.anchorX - cx, plan.anchorY + 70 + 90 * rise - cy, plan.anchorZ - cz);
        double w = 0.55 * follow + 0.25;
        double[] look = age < IMPACT ? normalize(toRock[0] * w + toZero[0] * (1 - w), toRock[1] * w + toZero[1] * (1 - w),
                toRock[2] * w + toZero[2] * (1 - w)) : toCloud;
        if (age >= IMPACT - 6 && age < IMPACT) {
            double k = smooth((age - IMPACT + 6) / 6);
            look = normalize(look[0] * (1 - k) + toCloud[0] * k, look[1] * (1 - k) + toCloud[1] * k, look[2] * (1 - k) + toCloud[2] * k);
        }
        double fov = 72 + 6 * smooth((age - 530) / 40);
        if (age >= IMPACT) fov += 10 * Math.exp(-(age - IMPACT) / 6);
        return pose(cx, cy, cz, cx + look[0] * 100, cy + look[1] * 100, cz + look[2] * 100, -3 * smooth((age - 520) / 40), fov);
    }

    private static RailgunCameraPath.Pose aftermath(CarrierFlight.Plan plan, Layout layout, double age) {
        double u = smooth((age - AFTERMATH) / 200);
        double cx = plan.anchorX + (95 + 130 * u) * layout.reach, cz = plan.anchorZ + (225 + 150 * u) * layout.reach;
        return pose(cx, plan.anchorY + 34 + 150 * u, cz, plan.anchorX, plan.anchorY + 120 + 40 * u, plan.anchorZ, 0, 74);
    }

    /** Short decaying jolts: touchdown, shot, lock, the dive rumble and the impact. */
    private static RailgunCameraPath.Pose shake(RailgunCameraPath.Pose p, double age) {
        double amount = jolt(age, LAND, 0.35, 5) + jolt(age, FIRE, 0.7, 6) + jolt(age, LOCK, 0.25, 8)
                + 0.45 * smooth((age - 520) / 50) * (age < IMPACT ? 1 : 0) + jolt(age, IMPACT, 2.6, 14)
                + jolt(age, IMPACT + 8, 1.2, 30);
        if (amount <= 0) return p;
        double sx = Math.sin(age * 2.3) + 0.5 * Math.sin(age * 5.1), sy = Math.sin(age * 2.9 + 1) + 0.5 * Math.sin(age * 4.3);
        return new RailgunCameraPath.Pose(p.x + sx * amount * 0.6, p.y + sy * amount * 0.6, p.z, p.targetX + sx * amount,
                p.targetY + sy * amount, p.targetZ, p.roll + Math.sin(age * 1.9) * amount * 0.8, p.fov);
    }

    private static double jolt(double age, double at, double strength, double decay) {
        return age < at ? 0 : strength * Math.exp(-(age - at) / decay);
    }

    /** Keeps the lens outside the asteroid's bounding sphere, raising it radially if needed. */
    static RailgunCameraPath.Pose clear(RailgunCameraPath.Pose p, double age) {
        if (age >= IMPACT || age < 90) return p;
        double[] c = AsteroidTrajectory.position(age);
        double dx = p.x - c[0], dy = p.y - c[1], dz = p.z - c[2], distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance >= CLEARANCE) return p;
        double scale = CLEARANCE / Math.max(1.0E-6, distance);
        return new RailgunCameraPath.Pose(c[0] + dx * scale, c[1] + dy * scale, c[2] + dz * scale,
                p.targetX, p.targetY, p.targetZ, p.roll, p.fov);
    }

    /** Screen point for an anchor-relative position under gluLookAt with a pre-applied roll; null behind. */
    static double[] project(RailgunCameraPath.Pose p, double x, double y, double z, double w, double h) {
        double[] f = normalize(p.targetX - p.x, p.targetY - p.y, p.targetZ - p.z);
        double sl = Math.hypot(f[0], f[2]);
        if (sl < 1.0E-9) return null;
        double sx = -f[2] / sl, sz = f[0] / sl;
        double ux = -sz * f[1], uy = sz * f[0] - sx * f[2], uz = sx * f[1];
        double dx = x - p.x, dy = y - p.y, dz = z - p.z;
        double depth = dx * f[0] + dy * f[1] + dz * f[2];
        if (depth <= 0.2) return null;
        double right = dx * sx + dz * sz, up = dx * ux + dy * uy + dz * uz;
        double roll = Math.toRadians(p.roll), rr = right * Math.cos(roll) - up * Math.sin(roll), ru = right * Math.sin(roll) + up * Math.cos(roll);
        double scale = h / (2 * Math.tan(Math.toRadians(p.fov) / 2));
        return new double[] {w * 0.5 + rr / depth * scale, h * 0.5 - ru / depth * scale};
    }

    // --- Helpers ---------------------------------------------------------------------------

    private static RailgunCameraPath.Pose pose(double x, double y, double z, double tx, double ty, double tz, double roll, double fov) {
        return new RailgunCameraPath.Pose(x, y, z, tx, ty, tz, roll, fov);
    }

    /** Position lerp with normalized look-direction blending, so far and near subjects swing evenly. */
    static RailgunCameraPath.Pose blend(RailgunCameraPath.Pose a, RailgunCameraPath.Pose b, double progress) {
        double p = smooth(progress);
        if (p <= 0) return a;
        if (p >= 1) return b;
        double x = a.x + (b.x - a.x) * p, y = a.y + (b.y - a.y) * p, z = a.z + (b.z - a.z) * p;
        double[] da = normalize(a.targetX - a.x, a.targetY - a.y, a.targetZ - a.z);
        double[] db = normalize(b.targetX - b.x, b.targetY - b.y, b.targetZ - b.z);
        double[] d = normalize(da[0] + (db[0] - da[0]) * p, da[1] + (db[1] - da[1]) * p, da[2] + (db[2] - da[2]) * p);
        return new RailgunCameraPath.Pose(x, y, z, x + d[0] * 100, y + d[1] * 100, z + d[2] * 100,
                a.roll + (b.roll - a.roll) * p, a.fov + (b.fov - a.fov) * p);
    }

    private static double softMax(double a, double b, double k) { return (a + b + Math.sqrt((a - b) * (a - b) + k * k)) / 2; }

    static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-12) return new double[] {0, 0, 1};
        return new double[] {x / length, y / length, z / length};
    }
}
