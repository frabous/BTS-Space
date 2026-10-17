package fr.frabous.stellar.client;

/**
 * Reusable destroyer choreography for every SS-03 carrier strike: warp exit, braking, station keeping
 * over a hard point, ventral bay cycle, engine spool, acceleration and warp jump. Pure math so payload
 * scenes, cameras and tests share the same ship. Ages are ticks; distances are blocks; the mesh is 1:1.
 */
final class CarrierFlight {
    /** Ship-local cargo hard point under the keel recess, in front of the armored keel. */
    static final double BAY_X = -198, BAY_Y = -42;
    static final double BOW = 395, STERN = 311, CENTER_Y = -10;
    static final double APERTURE_RADIUS = 150;
    /** Cradle travel: the payload hangs inside the hull at -RETRACTED and leaves at +DROP. */
    static final double RETRACTED = 11, DROP = 14;

    /** Timing and placement supplied by a payload scene. */
    static final class Plan {
        final double warpOpen, exitStart, station, bayOpen, release, bayClose, spool, depart, jump;
        final double anchorX, anchorY, anchorZ, heading, approach, altitude;

        Plan(double warpOpen, double exitStart, double station, double bayOpen, double release, double bayClose,
             double spool, double depart, double jump, double anchorX, double anchorY, double anchorZ,
             double heading, double approach, double altitude) {
            this.warpOpen = warpOpen; this.exitStart = exitStart; this.station = station; this.bayOpen = bayOpen;
            this.release = release; this.bayClose = bayClose; this.spool = spool; this.depart = depart; this.jump = jump;
            this.anchorX = anchorX; this.anchorY = anchorY; this.anchorZ = anchorZ;
            this.heading = heading; this.approach = approach; this.altitude = altitude;
        }

        double hx() { return Math.cos(heading); }
        double hz() { return -Math.sin(heading); }

        /** Ship mesh origin while holding station: the bay sits over the anchor. */
        double stationX() { return anchorX - BAY_X * hx(); }
        double stationY() { return anchorY + altitude; }
        double stationZ() { return anchorZ - BAY_X * hz(); }

        /** Along-heading coordinate of the arrival aperture, relative to the station origin. */
        double arrivalPlane() { return -approach; }

        double departurePlane() { return travel(jump) + BOW + 110; }

        /** Ship origin along the heading axis relative to the station origin. */
        double travel(double age) {
            if (age < exitStart) return -approach - BOW;
            if (age < station) {
                double u = (age - exitStart) / (station - exitStart), rest = 1 - u;
                return (-approach - BOW) * rest * rest * rest;
            }
            if (age < depart) return 0;
            double t = age - depart;
            if (age < jump) return 0.125 * t * t;
            double cruise = 0.125 * (jump - depart) * (jump - depart), speed = 0.25 * (jump - depart), j = age - jump;
            return cruise + speed * j + 75 * j * j;
        }

        double speed(double age) {
            if (age < exitStart || age >= station && age < depart) return 0;
            if (age < station) {
                double u = (age - exitStart) / (station - exitStart), rest = 1 - u;
                return 3 * (approach + BOW) * rest * rest / (station - exitStart);
            }
            if (age < jump) return 0.25 * (age - depart);
            return 0.25 * (jump - depart) + 150 * (age - jump);
        }
    }

    /** Derived per-frame state. */
    static final class State {
        double x, y, z, stretch = 1, stretchAnchor, engine, brake, bay, cradle, clamps, lights;
        boolean visible, clipArrival, clipDeparture;
        double arrivalPlaneX, arrivalPlaneZ, departurePlaneX, departurePlaneZ;
        final Gate arrival = new Gate(), departure = new Gate();
    }

    /** Aperture parameters: centre, radius scale, energy and the exit/entry flash age. */
    static final class Gate {
        double x, y, z, open, energy, flash = -1, collapse;
    }

    private CarrierFlight() { }

    static State at(Plan plan, double age) {
        State s = new State();
        double travel = plan.travel(age), hx = plan.hx(), hz = plan.hz();
        s.x = plan.stationX() + hx * travel; s.y = plan.stationY(); s.z = plan.stationZ() + hz * travel;
        double gone = plan.jump + 6;
        s.visible = age >= plan.exitStart && age < gone;
        s.lights = smooth((age - plan.exitStart) / 6);
        // Warp elongation: the hull leaves the aperture longer than it is, then settles.
        double arrive = age - plan.exitStart;
        if (arrive >= 0 && arrive < 12) { s.stretch = 1 + 1.7 * square(1 - arrive / 12); s.stretchAnchor = BOW; }
        if (age >= plan.jump) { s.stretch = 1 + 5 * smooth((age - plan.jump) / 4); s.stretchAnchor = -STERN; }
        s.clipArrival = age < plan.exitStart + (plan.station - plan.exitStart) * 0.5;
        s.arrivalPlaneX = plan.stationX() + hx * plan.arrivalPlane();
        s.arrivalPlaneZ = plan.stationZ() + hz * plan.arrivalPlane();
        s.clipDeparture = age >= plan.jump - 1;
        s.departurePlaneX = plan.stationX() + hx * plan.departurePlane();
        s.departurePlaneZ = plan.stationZ() + hz * plan.departurePlane();
        // Retro burn while braking, idle drives at station, then the departure spool.
        s.brake = smooth((age - plan.exitStart - 6) / 6) * (1 - smooth((age - plan.station + 8) / 10));
        double idle = 0.22 * smooth((age - plan.exitStart - 14) / 20);
        double spool = smooth((age - plan.spool) / (plan.depart - plan.spool));
        s.engine = idle + 0.78 * spool + 0.9 * smooth((age - plan.jump + 4) / 4);
        // Bay doors, cradle and clamps.
        s.bay = smooth((age - plan.bayOpen) / 10) * (1 - smooth((age - plan.bayClose) / 12));
        double lower = smooth((age - plan.bayOpen - 6) / (plan.release - plan.bayOpen - 8));
        double retract = smooth((age - plan.release - 2) / (plan.bayClose - plan.release - 2));
        s.cradle = -RETRACTED + (RETRACTED + DROP) * lower * (1 - retract);
        s.clamps = smooth((age - plan.release + 2) / 2);
        // Apertures share the ship's central height.
        Gate in = s.arrival;
        in.x = s.arrivalPlaneX; in.y = s.y + CENTER_Y; in.z = s.arrivalPlaneZ;
        double sternClear = clearTime(plan, -STERN, plan.arrivalPlane());
        in.open = smooth((age - plan.warpOpen) / 8) * (1 - smooth((age - sternClear - 2) / 12));
        in.energy = smooth((age - plan.warpOpen + 6) / 10) * (1 - smooth((age - sternClear - 8) / 10));
        in.flash = sternClear;
        in.collapse = smooth((age - sternClear - 2) / 12);
        Gate out = s.departure;
        out.x = s.departurePlaneX; out.y = s.y + CENTER_Y; out.z = s.departurePlaneZ;
        out.open = smooth((age - plan.jump + 8) / 6) * (1 - smooth((age - plan.jump - 6) / 10));
        out.energy = smooth((age - plan.jump + 12) / 8) * (1 - smooth((age - plan.jump - 12) / 10));
        out.flash = plan.jump + 3;
        out.collapse = smooth((age - plan.jump - 6) / 10);
        return s;
    }

    /** First age at which a ship-local x has crossed the arrival plane. */
    static double clearTime(Plan plan, double localX, double plane) {
        double low = plan.exitStart, high = plan.station;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (plan.travel(mid) + localX > plane) high = mid; else low = mid;
        }
        return high;
    }

    /** World-local cargo hard point (top of the payload) for the current ship state. */
    static double[] hardPoint(Plan plan, State s) {
        return new double[] {s.x + BAY_X * plan.hx(), s.y + BAY_Y - 1 - s.cradle, s.z + BAY_X * plan.hz()};
    }

    static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }
    private static double square(double value) { return value * value; }
}
