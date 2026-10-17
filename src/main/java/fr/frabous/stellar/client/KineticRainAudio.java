package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.KineticRainRules.*;

import fr.frabous.stellar.api.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;

/**
 * SS-03C sound field (scripts/generate-kinetic-rain-sfx.py), queued on {@link BroadsideAudio}'s cues.
 * Ship cues reuse the carrier set. Rail salvos, rod impacts and the hammer reach each listener at the
 * speed of sound; the scream of the nearest rod is pinned to its mark and peaks as it lands, so the
 * listener hears it coming before the report of the impact.
 */
final class KineticRainAudio {
    private static final Map<UUID, Long> AGES = new HashMap<>();

    private KineticRainAudio() { }

    static void tick(WorldClient world, Collection<LaunchState> launches) {
        Entity listener = Minecraft.getMinecraft().getRenderViewEntity();
        if (world == null || listener == null) { reset(); return; }
        long now = world.getTotalWorldTime();
        double[] ear = {listener.posX, listener.posY + listener.getEyeHeight(), listener.posZ};
        Set<UUID> live = new HashSet<>();
        for (LaunchState state : launches) {
            if (state.getAttack() != AttackId.KINETIC_RAIN || state.getPhase() == LaunchPhase.COMPLETE) continue;
            long age = now - state.getStartTick();
            if (age < 0 || age > END) continue;
            live.add(state.getLaunchId());
            Long previous = AGES.put(state.getLaunchId(), age);
            // A launch first seen mid-way plays nothing already past; a stall replays at most two seconds.
            long from = previous == null ? age : Math.max(previous + 1, age - 40);
            CarrierFlight.Plan plan = KineticRainScene.plan(state);
            for (long tick = from; tick <= age; tick++) events(state, plan, (int) tick, state.getStartTick() + tick, ear);
        }
        AGES.keySet().retainAll(live);
    }

    static void reset() { AGES.clear(); }

    private static void events(LaunchState state, CarrierFlight.Plan plan, int age, long tick, double[] ear) {
        CarrierFlight.State s = CarrierFlight.at(plan, age);
        double[] bay = CarrierFlight.hardPoint(plan, s);
        double sternClear = CarrierFlight.clearTime(plan, -CarrierFlight.STERN, plan.arrivalPlane());
        if (age == 4) at(tick, "carrier_warp_open", s.arrival.x, s.arrival.y, s.arrival.z, 900, 1, ear);
        if (age == 22) at(tick, "carrier_brake", s.x, s.y, s.z, 700, 1, ear);
        if (age == (int) Math.round(sternClear) + 2) at(tick, "carrier_warp_collapse", s.arrival.x, s.arrival.y, s.arrival.z, 900, 1, ear);
        if (age == BAY_OPEN) at(tick, "carrier_bay_open", bay[0], bay[1], bay[2], 400, 1, ear);
        if (age == BAY_CLOSE) at(tick, "carrier_bay_open", bay[0], bay[1], bay[2], 400, 0.85F, ear);
        if (age == SPOOL) at(tick, "carrier_spool", s.x, s.y, s.z, 700, 1, ear);
        if (age == JUMP - 8) at(tick, "carrier_warp_open", s.departure.x, s.departure.y, s.departure.z, 900, 0.9F, ear);
        if (age == JUMP + 3) at(tick, "carrier_warp_collapse", s.departure.x, s.departure.y, s.departure.z, 900, 1, ear);
        rods(state, age, tick, bay, ear);
        double fallback = state.getTarget().getY() + 1;
        double x = state.getTarget().getX() + 0.5, z = state.getTarget().getZ() + 0.5, y = KineticRainVfx.ground(x, z, fallback);
        if (age == HAMMER_RELEASE) at(tick, "carrier_clamp_release", bay[0], bay[1], bay[2], 400, 0.8F, ear);
        // The fall builds on the target itself and peaks as the hammer lands.
        if (age == HAMMER_IMPACT - 50) BroadsideAudio.cue(tick, "kinetic_hammer_fall", x, y + 40, z, 480, 1);
        if (age == HAMMER_IMPACT) at(tick, "kinetic_hammer_impact", x, y + 2, z, 1200, 1, ear);
    }

    /** One voice per salvo, on its rod nearest the listener. */
    private static void rods(LaunchState state, int age, long tick, double[] bay, double[] ear) {
        int launched = salvo(age + FLIGHT), landed = salvo(age);
        double fallback = state.getTarget().getY() + 1;
        if (launched >= 0) {
            int rod = nearest(state, launched, ear);
            double[] m = mark(state, rod);
            float pitch = pitch(state, rod);
            at(tick, "kinetic_rod_launch", bay[0], bay[1], bay[2], 480, pitch, ear);
            if (Math.hypot(m[0] - ear[0], m[1] - ear[2]) < 110)
                BroadsideAudio.cue(tick + 1, "kinetic_rod_scream", m[0], KineticRainVfx.ground(m[0], m[1], fallback) + 10, m[1], 160, pitch);
        }
        if (landed >= 0) {
            int rod = nearest(state, landed, ear);
            double[] m = mark(state, rod);
            double y = KineticRainVfx.ground(m[0], m[1], fallback) + 1;
            at(tick, "kinetic_rod_impact", m[0], y, m[1], 256, pitch(state, rod), ear);
            if (landed / ARMS % 2 == 0) at(tick, "kinetic_rod_impact_far", m[0], y, m[1], 900, pitch(state, rod), ear);
        }
    }

    private static int nearest(LaunchState state, int first, double[] ear) {
        int nearest = first;
        double best = Double.MAX_VALUE;
        for (int rod = first; rod < first + ARMS; rod++) {
            double[] m = mark(state, rod);
            double distance = Math.hypot(m[0] - ear[0], m[1] - ear[2]);
            if (distance < best) { best = distance; nearest = rod; }
        }
        return nearest;
    }

    private static float pitch(LaunchState state, int rod) { return (float) (0.92 + 0.16 * unit(state.getSeed(), 500 + rod)); }

    /** Cue an event at a point, delayed by its distance to the listener. */
    private static void at(long tick, String name, double x, double y, double z, float range, float pitch, double[] ear) {
        BroadsideAudio.cue(tick + BroadsideAudio.delay(BroadsideAudio.distance(ear[0], ear[1], ear[2], x, y, z)), name, x, y, z, range, pitch);
    }
}
