package fr.frabous.stellar.client;

import fr.frabous.stellar.api.BlackholeTides;

/** Pure visual envelopes, in server ticks. No simulation or player state is changed. */
final class BlackholeVisualMath {
    static final double DISK_SIN = Math.sin(Math.toRadians(15));
    static final double DISK_COS = Math.cos(Math.toRadians(15));
    static final double SPAGHETTI_LEAD = 1.3;
    private static final double[] RIPPLE_STARTS = {60, 110, 160, 700};
    private BlackholeVisualMath() { }

    static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    static double visibility(double age) {
        return smooth(age / 60) * (1 - smooth((age - 740) / 60));
    }

    static double pull(double age) {
        return smooth((age - 60) / 40) * (1 - smooth((age - 700) / 40));
    }

    static double range(double distance, double radius) {
        return 1 - smooth(distance / radius);
    }

    static double space(double altitude, double threshold) {
        return smooth((altitude - threshold) / 40);
    }

    static double tidal(double distance, double horizon) {
        return 1 - smooth((distance - horizon) / (horizon * 5));
    }

    static double entry(double distance, double horizon) {
        return 1 - smooth((distance - horizon * 0.75) / (horizon * 0.85));
    }

    static double horizonRadius(double age, double radius) {
        return BlackholeTides.horizonRadius(age, radius);
    }

    static double diskHeat(double radial) {
        return Math.pow(Math.max(0, 1 - radial), 1.6);
    }

    static double[] diskView(double dx, double dy, double dz) {
        double distance = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new double[] {dx / distance, (dy * DISK_COS + dz * DISK_SIN) / distance,
                (-dy * DISK_SIN + dz * DISK_COS) / distance};
    }

    /** Project the tilted disk normal onto the observer's sky, including the polar-view limit. */
    static double[] lensBasis(double nx, double ny, double nz) {
        double rx = DISK_COS * nz - DISK_SIN * ny, ry = DISK_SIN * nx, rz = -DISK_COS * nx;
        double inclination = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (inclination < 0.001) {
            double horizontal = Math.max(0.001, Math.hypot(nx, nz));
            rx = nz / horizontal; ry = 0; rz = -nx / horizontal;
        }
        else { rx /= inclination; ry /= inclination; rz /= inclination; }
        return new double[] {rx, ry, rz, ny * rz - nz * ry, nz * rx - nx * rz,
                nx * ry - ny * rx, inclination};
    }

    static double modelStrength(double age, double altitude, double horizontalDistance, double centerDistance,
                                double radius, double threshold, double horizon) {
        double nearHorizon = 1 - smooth((centerDistance - horizon) / (horizon * 2));
        return visibility(age) * pull(age) * space(altitude, threshold) * range(horizontalDistance, radius)
                * (0.06 + 0.94 * nearHorizon * nearHorizon);
    }

    static double horizonOpacity(double distance, double radius) {
        return smooth((distance - radius) / (radius * 0.2));
    }

    static double readableModelStrength(double strength, double eyeDistance, double bodySize) {
        return strength * smooth((eyeDistance - bodySize * 0.75) / (bodySize * 2.25));
    }

    static double lensRadius(double distance, double radius) {
        double interior = 1 - smooth((distance - radius * 0.85) / (radius * 0.15));
        double insideRadius = Math.max(radius * 0.18, distance * 0.5);
        return radius * (1 - interior) + insideRadius * interior;
    }

    /** Column-major radial deformation; retain transverse thickness so bodies remain recognizable. */
    static double[] tidalTransform(double dx, double dy, double dz, double ox, double oy, double oz, double strength) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double nx = length > 0 ? dx / length : 0;
        double ny = length > 0 ? dy / length : 1;
        double nz = length > 0 ? dz / length : 0;
        double amount = Math.max(0, Math.min(1, strength));
        double along = 1 + 2.5 * amount;
        double across = 1 - 0.12 * amount, difference = along - across;
        double xx = across + difference * nx * nx, xy = difference * nx * ny, xz = difference * nx * nz;
        double yy = across + difference * ny * ny, yz = difference * ny * nz, zz = across + difference * nz * nz;
        return new double[] {
                xx, xy, xz, 0, xy, yy, yz, 0, xz, yz, zz, 0,
                ox - xx * ox - xy * oy - xz * oz,
                oy - xy * ox - yy * oy - yz * oz,
                oz - xz * ox - yz * oy - zz * oz, 1
        };
    }

    /** Pre-multiply in eye space: transform the world-relative radial axis and model origin through the live view. */
    static double[] tidalInView(double[] view, double dx, double dy, double dz,
                               double ox, double oy, double oz, double strength) {
        double radialX = view[0] * dx + view[4] * dy + view[8] * dz;
        double radialY = view[1] * dx + view[5] * dy + view[9] * dz;
        double radialZ = view[2] * dx + view[6] * dy + view[10] * dz;
        double originX = view[0] * ox + view[4] * oy + view[8] * oz + view[12];
        double originY = view[1] * ox + view[5] * oy + view[9] * oz + view[13];
        double originZ = view[2] * ox + view[6] * oy + view[10] * oz + view[14];
        double length = Math.sqrt(radialX * radialX + radialY * radialY + radialZ * radialZ);
        double nx = length > 0 ? radialX / length : 0;
        double ny = length > 0 ? radialY / length : 1;
        double nz = length > 0 ? radialZ / length : 0;
        double amount = Math.max(0, Math.min(1, strength));
        double along = 1 + 2.5 * amount;
        double across = 1 - 0.12 * amount, difference = along - across;
        double xx = across + difference * nx * nx, xy = difference * nx * ny, xz = difference * nx * nz;
        double yy = across + difference * ny * ny, yz = difference * ny * nz, zz = across + difference * nz * nz;
        double tx = originX - xx * originX - xy * originY - xz * originZ;
        double ty = originY - xy * originX - yy * originY - yz * originZ;
        double tz = originZ - xz * originX - yz * originY - zz * originZ;
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) {
            int base = column * 4;
            result[base] += xx * view[base];
            result[base] += xy * view[base + 1];
            result[base] += xz * view[base + 2];
            result[base] += tx * view[base + 3];
            result[base + 1] += xy * view[base];
            result[base + 1] += yy * view[base + 1];
            result[base + 1] += yz * view[base + 2];
            result[base + 1] += ty * view[base + 3];
            result[base + 2] += xz * view[base];
            result[base + 2] += yz * view[base + 1];
            result[base + 2] += zz * view[base + 2];
            result[base + 2] += tz * view[base + 3];
            result[base + 3] += 0 * view[base];
            result[base + 3] += 0 * view[base + 1];
            result[base + 3] += 0 * view[base + 2];
            result[base + 3] += 1 * view[base + 3];
        }
        return result;
    }

    static double unwrapAngle(double angle, double previous) {
        return previous + ((angle - previous) % 360 + 540) % 360 - 180;
    }

    private static double unit(double value) { return Math.max(0, Math.min(1, value)); }

    /** Lethal unravelling: shredding follows the health already torn away, then the whole death animation. */
    static double unravel(double exposure, double healthFraction, double dying) {
        return Math.max(unit(exposure) * (1 - unit(healthFraction)), unit(dying));
    }

    static double spaghettiAmount(double tide, double unravel) {
        return 6 * unit(tide) + 18 * unit(unravel);
    }

    /** Body coordinate s is the radial offset over half the body length, positive toward the hole. */
    static double spaghettiStretch(double s, double amount) {
        double c = Math.max(-1, Math.min(SPAGHETTI_LEAD, s)), u = (c + 1) * 0.5;
        return 1 + amount * (0.3 + 0.7 * u * u);
    }

    /** Exact integral of the stretch from the body centre: the near side runs away faster than the far side. */
    static double spaghettiAlong(double s, double amount) {
        double c = Math.max(-1, Math.min(SPAGHETTI_LEAD, s));
        return c + amount * (0.3 * c + 0.7 / 12 * ((c + 1) * (c + 1) * (c + 1) - 1))
                + spaghettiStretch(c, amount) * (s - c);
    }

    /** Volume-preserving section, floored for readability; a dying body thins into a thread. */
    static double spaghettiWidth(double s, double amount, double unravel) {
        return Math.max(spaghettiFloor(unravel), 1 / Math.sqrt(spaghettiStretch(s, amount)));
    }

    static double spaghettiFloor(double unravel) {
        return 0.2 - 0.12 * unit(unravel);
    }

    /** Tidal torque turns the long body axis toward the hole before the stretch dominates. */
    static double spaghettiAlignment(double tide, double unravel) {
        return smooth(unit(tide) * 1.4 + unit(unravel));
    }

    /**
     * Column-major rotation turning unit vector u by the given fraction of its angle toward the
     * nearer end of unit axis a (head-first or feet-first, whichever needs the smaller turn).
     */
    static double[] alignTo(double ux, double uy, double uz, double ax, double ay, double az, double amount) {
        double cos = ux * ax + uy * ay + uz * az;
        if (cos < 0) { ax = -ax; ay = -ay; az = -az; cos = -cos; }
        double kx = uy * az - uz * ay, ky = uz * ax - ux * az, kz = ux * ay - uy * ax;
        double sin = Math.sqrt(kx * kx + ky * ky + kz * kz);
        double angle = Math.atan2(sin, cos) * unit(amount);
        if (sin < 1e-9 || angle == 0) return new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        kx /= sin; ky /= sin; kz /= sin;
        double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        return new double[] {
                c + t * kx * kx, t * kx * ky + s * kz, t * kx * kz - s * ky,
                t * kx * ky - s * kz, c + t * ky * ky, t * ky * kz + s * kx,
                t * kx * kz + s * ky, t * ky * kz - s * kx, c + t * kz * kz};
    }

    /** Orbital shear in blocks per squared body coordinate: the inner end leads, the outer end lags. */
    static double spaghettiBend(double size, double tide, double unravel) {
        return size * 0.35 * (unit(tide) + unit(unravel)) / (1 + spaghettiAmount(tide, unravel));
    }

    /** {shadow angle, Einstein angle, shadow darkness}; interior views keep a smaller, translucent shadow. */
    static double[] lensAngles(double distance, double radius) {
        double photon = lensRadius(distance, radius);
        if (distance <= radius) photon = Math.min(photon, distance * 0.5);
        double shadow = Math.asin(Math.min(0.9, photon / Math.max(0.001, distance)));
        double interior = 1 - smooth((distance - radius * 0.85) / (radius * 0.3));
        return new double[] {shadow, Math.min(1.25, shadow * (1.55 - 0.3 * interior)), 1 - 0.4 * interior};
    }

    /** {amplitude, travelled angle} of the latest gravitational-wave pulse, in radians. */
    static double[] ripple(double age) {
        double since = -1;
        for (double start : RIPPLE_STARTS) if (age >= start) since = age - start;
        if (since < 0) return new double[] {0, 0};
        return new double[] {0.012 * Math.exp(-since / 45) * smooth(since / 4), since * 0.02};
    }
}
