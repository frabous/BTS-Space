package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * Client mirror of the server battery drives, for animation, consoles and sound only: the same traverse
 * limits and goals (laid shell, shared aim point, rest), stepped once per world tick. The server owns
 * every shell; a mirrored battery is snapped onto the confirmed bearing when it fires.
 */
final class BroadsideFireControl {
    /** Muzzle smoke and impact dust outlive the shell. */
    static final int LINGER = 240;
    private static final int CATCH_UP = 40;

    static final class Battery {
        final double[] bearing, previous;
        CarrierShot laying;
        final long[] fired = {Long.MIN_VALUE / 4, Long.MIN_VALUE / 4};
        long readyAt = Long.MIN_VALUE / 4;
        boolean tracking, outOfArc;
        /** Angular speed of the last step, radians per tick. */
        double speed;

        Battery(double[] rest) { bearing = rest.clone(); previous = rest.clone(); }
    }

    static final class Fleet {
        final LaunchState launch;
        final BroadsideBatteries.Frame frame;
        final Battery[] batteries = new Battery[BroadsideBatteries.COUNT];
        final List<CarrierShot> shots = new ArrayList<>();
        /** Server-traced aim point, followed by witnesses; the pilot follows its own crosshair. */
        double[] aim;
        long tick = Long.MIN_VALUE;

        Fleet(LaunchState launch) {
            this.launch = launch; frame = BroadsideBatteries.frame(launch);
            for (int i = 0; i < batteries.length; i++) batteries[i] = new Battery(BroadsideBatteries.rest(frame));
        }
    }

    private static final Map<UUID, Fleet> FLEETS = new LinkedHashMap<>();

    private BroadsideFireControl() { }

    static Fleet fleet(LaunchState state) { return FLEETS.computeIfAbsent(state.getLaunchId(), id -> new Fleet(state)); }
    static Fleet find(UUID id) { return FLEETS.get(id); }
    static Collection<Fleet> fleets() { return FLEETS.values(); }

    /** Every confirmed shell currently remembered, oldest first. */
    static List<CarrierShot> shots() {
        List<CarrierShot> all = new ArrayList<>();
        for (Fleet fleet : FLEETS.values()) all.addAll(fleet.shots);
        return all;
    }

    static void shot(LaunchState state, CarrierShot shot) {
        Fleet fleet = fleet(state);
        fleet.shots.add(shot);
        if (fleet.shots.size() > 96) fleet.shots.remove(0);
        Battery battery = fleet.batteries[shot.battery];
        battery.laying = shot;
        battery.readyAt = shot.fire + BroadsideBatteries.RELOAD_TICKS;
        BroadsideAudio.shot(fleet, shot);
    }

    static void aim(LaunchState state, boolean valid, double x, double y, double z) {
        fleet(state).aim = valid ? new double[] {x, y, z} : null;
    }

    static void tick(WorldClient world, Collection<LaunchState> launches) {
        Set<UUID> live = new HashSet<>();
        for (LaunchState state : launches) if (state.getAttack() == AttackId.CARRIER_GUNNERY) live.add(state.getLaunchId());
        FLEETS.keySet().retainAll(live);
        if (world == null) return;
        long now = world.getTotalWorldTime();
        for (LaunchState state : launches) {
            if (state.getAttack() != AttackId.CARRIER_GUNNERY) continue;
            Fleet fleet = fleet(state);
            if (fleet.tick == Long.MIN_VALUE || now - fleet.tick > CATCH_UP) fleet.tick = now - 1;
            while (fleet.tick < now) step(fleet, ++fleet.tick);
            fleet.shots.removeIf(shot -> now - shot.impact > LINGER);
        }
    }

    static void reset() { FLEETS.clear(); }

    private static void step(Fleet fleet, long now) {
        long age = now - fleet.launch.getStartTick();
        boolean pilot = CarrierGunneryClient.owns(fleet.launch.getLaunchId());
        double[] aim = age >= CarrierGunneryRules.RETURN ? null : pilot ? CarrierGunneryClient.localAim() : fleet.aim;
        for (int i = 0; i < fleet.batteries.length; i++) {
            Battery b = fleet.batteries[i];
            b.previous[0] = b.bearing[0]; b.previous[1] = b.bearing[1];
            double[] goal = b.laying != null ? bearing(b.laying)
                    : aim == null ? null : BroadsideBatteries.solution(fleet.frame, i, aim[0], aim[1], aim[2]);
            b.tracking = goal != null;
            b.outOfArc = aim != null && goal == null;
            if (goal == null) goal = BroadsideBatteries.rest(fleet.frame);
            BroadsideBatteries.step(b.bearing, goal[0], goal[1]);
            if (b.laying != null && now >= b.laying.fire) {
                b.bearing[0] = goal[0]; b.bearing[1] = goal[1];
                b.fired[b.laying.barrel] = b.laying.fire;
                b.laying = null;
            }
            b.speed = Math.max(Math.abs(BroadsideBatteries.wrap(b.bearing[0] - b.previous[0])), Math.abs(b.bearing[1] - b.previous[1]));
        }
    }

    /** World bearing of the bore that sent this shell: muzzle toward impact. */
    static double[] bearing(CarrierShot shot) {
        double dx = shot.hitX - shot.x, dy = shot.hitY - shot.y, dz = shot.hitZ - shot.z;
        return new double[] {Math.atan2(-dz, dx), Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))};
    }

    /** Interpolated, ship-local poses for the model; time is world ticks plus the partial tick. */
    static DestroyerBatteryModel.Pose[] poses(Fleet fleet, double time) {
        DestroyerBatteryModel.Pose[] poses = DestroyerBatteryModel.rest();
        double partial = Math.max(0, Math.min(1, time - fleet.tick));
        for (int i = 0; i < poses.length; i++) {
            Battery b = fleet.batteries[i];
            DestroyerBatteryModel.Pose p = poses[i];
            double yaw = b.previous[0] + BroadsideBatteries.wrap(b.bearing[0] - b.previous[0]) * partial;
            p.yaw = BroadsideBatteries.wrap(yaw - fleet.frame.heading);
            p.pitch = b.previous[1] + (b.bearing[1] - b.previous[1]) * partial;
            p.age = time;
            for (int k = 0; k < 2; k++) {
                double since = time - b.fired[k];
                p.recoil[k] = recoil(since);
                p.heat[k] = since < 0 ? 0 : Math.exp(-since / 50);
                p.flash[k] = since < 0 ? 0 : Math.exp(-since / 1.6);
            }
            double reload = (b.readyAt - time) / BroadsideBatteries.RELOAD_TICKS;
            p.charge = b.laying != null ? 1 : 1 - Math.max(0, Math.min(1, reload));
            p.status = b.laying != null ? DestroyerBatteryModel.LAYING : reload > 0 ? DestroyerBatteryModel.RELOAD
                    : b.outOfArc ? DestroyerBatteryModel.NO_ARC : b.tracking ? DestroyerBatteryModel.READY : DestroyerBatteryModel.IDLE;
        }
        return poses;
    }

    /** Barrel run-out: a hard kick in a little over half a tick, then a damped return to battery. */
    static double recoil(double since) {
        if (since < 0) return 0;
        if (since < 0.6) return 2.6 * since / 0.6;
        double back = (since - 0.6) / 14;
        return back >= 1 ? 0 : 2.6 * (1 - back * back * (3 - 2 * back));
    }

    /** First free battery that can take the point, or -1; mirrors the server choice for HUD feedback. */
    static int solutionFor(Fleet fleet, double[] point, long now) {
        int best = -1, slew = Integer.MAX_VALUE;
        for (int i = 0; i < fleet.batteries.length; i++) {
            Battery b = fleet.batteries[i];
            if (b.laying != null || b.readyAt > now) continue;
            double[] solution = BroadsideBatteries.solution(fleet.frame, i, point[0], point[1], point[2]);
            if (solution == null) continue;
            int ticks = BroadsideBatteries.slewTicks(b.bearing, solution[0], solution[1]);
            if (ticks < slew) { best = i; slew = ticks; }
        }
        return best;
    }

    /** True when at least one battery could ever bear on the point, reloaded or not. */
    static boolean inArc(Fleet fleet, double[] point) {
        for (int i = 0; i < fleet.batteries.length; i++)
            if (BroadsideBatteries.solution(fleet.frame, i, point[0], point[1], point[2]) != null) return true;
        return false;
    }
}
