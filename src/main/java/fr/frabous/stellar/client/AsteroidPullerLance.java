package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AsteroidTrajectory;
import fr.frabous.stellar.api.LaunchState;

import static fr.frabous.stellar.api.AsteroidPullerTiming.*;

/** Graviton Lance articulation for SS-03A: carried, dropped, planted, unfolded, aimed, fired, overloaded. */
final class AsteroidPullerLance {
    static final double LEGS = 148;

    private AsteroidPullerLance() { }

    static CarrierFlight.Plan plan(LaunchState launch) {
        double dx = launch.getDeployment().getX() - launch.getTarget().getX();
        double dy = launch.getDeployment().getY() - launch.getTarget().getY();
        double dz = launch.getDeployment().getZ() - launch.getTarget().getZ();
        return new CarrierFlight.Plan(WARP_OPEN, WARP_EXIT, STATION, BAY_OPEN, RELEASE, 118,
                SHIP_SPOOL, SHIP_DEPART, SHIP_JUMP, dx, dy, dz, 0, 640, 760);
    }

    static GravitonLanceModel.Pose pose(LaunchState launch, double age) {
        return pose(plan(launch), age);
    }

    static GravitonLanceModel.Pose pose(CarrierFlight.Plan plan, double age) {
        GravitonLanceModel.Pose p = new GravitonLanceModel.Pose();
        p.age = age;
        p.visible = age >= STATION && age < IMPACT;
        p.x = plan.anchorX; p.y = plan.anchorY; p.z = plan.anchorZ;
        p.lift = lift(plan, age);
        if (age < RELEASE) {
            double[] hard = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, age));
            p.x = hard[0]; p.z = hard[2];
        }
        p.legs = smooth((age - LEGS) / 18);
        p.anchors = smooth((age - ANCHOR) / 12);
        double touchdown = age - LAND;
        p.squat = -0.55 * smooth(touchdown / 2) * (1 - smooth((touchdown - 2) / 12)) - 0.12 * p.anchors;
        double boot = smooth((age - SHIP_SPOOL) / 14);
        double blink = age >= SHIP_SPOOL && age < SHIP_SPOOL + 14 && ((int) Math.floor((age - SHIP_SPOOL) / 2)) % 3 == 1 ? 0.4 : 1;
        p.lights = (0.3 + 0.7 * boot) * blink;
        p.telescope2 = smooth((age - 198) / 14);
        p.telescope3 = smooth((age - 208) / 14);
        p.fins = smooth((age - 214) / 12);
        p.crown = smooth((age - 222) / 12);
        double[] aim = aim(plan, age, p);
        p.yaw = aim[0]; p.pitch = aim[1];
        p.coil = coil(age);
        double sinceFire = age - FIRE;
        if (age < FIRE) p.charge = smooth((age - CHARGE) / (FIRE - CHARGE));
        else if (age < SEVER) p.charge = 0.55 + 0.2 * Math.sin(age * 0.9) * Math.sin(age * 0.37);
        else p.charge = 0.55 * (1 - smooth((age - SEVER) / 8));
        p.recoil = sinceFire < 0 ? 0 : sinceFire < 1.5 ? 1.8 * smooth(sinceFire / 1.5) : 1.8 * (1 - smooth((sinceFire - 1.5) / 18));
        p.heat = Math.max(smooth((age - 400) / 140) * 0.8, smooth((age - SEVER + 4) / 6));
        p.tremor = age < LOCK ? 0 : 0.6 + 1.4 * smooth((age - 500) / 60);
        return p;
    }

    /** Vertical offset of the whole machine above its ground anchor. */
    static double lift(CarrierFlight.Plan plan, double age) {
        double release = releaseLift(plan);
        if (age < RELEASE) {
            double[] hard = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, age));
            return hard[1] - GravitonLanceModel.TOP - plan.anchorY;
        }
        double u = Math.max(0, Math.min(1, (age - RELEASE) / (LAND - RELEASE)));
        // Free fall from rest, peak speed two thirds down, then a hard retro burn to zero speed.
        return release * (1 - u * u * u * (4 - 3 * u));
    }

    static double releaseLift(CarrierFlight.Plan plan) {
        double[] hard = CarrierFlight.hardPoint(plan, CarrierFlight.at(plan, RELEASE));
        return hard[1] - GravitonLanceModel.TOP - plan.anchorY;
    }

    /** Fall speed in blocks per tick, positive downward. */
    static double fallSpeed(CarrierFlight.Plan plan, double age) {
        if (age < RELEASE || age > LAND) return 0;
        double u = (age - RELEASE) / (LAND - RELEASE);
        return releaseLift(plan) * 12 * u * u * (1 - u) / (LAND - RELEASE);
    }

    /** Yaw and pitch toward the rest pose, the current asteroid, its lead point or the tether. */
    private static double[] aim(CarrierFlight.Plan plan, double age, GravitonLanceModel.Pose p) {
        if (age < 192) return new double[] {0, 0};
        double[] target;
        if (age < CHARGE) target = AsteroidTrajectory.position(Math.max(age, APPEAR));
        else if (age < LOCK) {
            double[] now = AsteroidTrajectory.position(age), lead = AsteroidTrajectory.interceptPoint();
            double k = smooth((age - CHARGE) / 26);
            target = new double[] {now[0] + (lead[0] - now[0]) * k, now[1] + (lead[1] - now[1]) * k, now[2] + (lead[2] - now[2]) * k};
        } else target = AsteroidTrajectory.position(Math.min(age, SEVER));
        double pivotY = p.y + p.lift + GravitonLanceModel.BODY + p.squat + GravitonLanceModel.PIVOT;
        double dx = target[0] - p.x, dy = target[1] - pivotY, dz = target[2] - p.z;
        double yaw = Math.atan2(-dz, dx), pitch = Math.atan2(dy, Math.hypot(dx, dz));
        double raise = smooth((age - 196) / 36);
        double wrapped = Math.atan2(Math.sin(yaw), Math.cos(yaw));
        // Once the tether snaps the drives die: the barrel freezes on its last bearing and sags.
        double droop = 0.14 * smooth((age - SEVER) / 10);
        return new double[] {wrapped * raise, pitch * raise - droop};
    }

    /** Integrated coil angle: idle spin after boot, spun hard through the charge. */
    static double coil(double age) {
        double idle = 0.05 * Math.max(0, age - 200);
        double x = (age - CHARGE) / 40.0, ramp = x <= 0 ? 0 : x < 1 ? x * x * x - x * x * x * x / 2 : 0.5 + (x - 1);
        return idle + 0.75 * 40 * ramp;
    }

    static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }
}
