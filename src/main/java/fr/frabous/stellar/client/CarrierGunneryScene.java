package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.CarrierGunneryRules.*;

import fr.frabous.stellar.api.*;

/**
 * SS-03B in the world: the destroyer warps in bow-first toward the designated area and holds station
 * with its fire-control gondola over the caster, a transfer beam lifts the pilot aboard, the five
 * ventral batteries lay on the pilot's designator and fire, then the beam returns the pilot and the
 * ship jumps out. Every client sees the same guns move: they follow the server's aim point.
 */
final class CarrierGunneryScene {
    private static final int CORE = 0xEAF8FF, PLASMA = 0x7FE6FF;

    private CarrierGunneryScene() { }

    static CarrierFlight.Plan plan(LaunchState state) {
        BroadsideBatteries.Frame f = BroadsideBatteries.frame(state);
        double hx = Math.cos(f.heading), hz = -Math.sin(f.heading);
        // Station by tick 60; the bay stays shut; spool, depart and jump once the pilot is home.
        return new CarrierFlight.Plan(4, 16, 60, 900, 930, 960, RETURN, RETURN + 24, RETURN + 58,
                f.x + CarrierFlight.BAY_X * hx, SHIP_Y, f.z + CarrierFlight.BAY_X * hz, f.heading, 420, 0);
    }

    static void draw(LaunchState state, double age, boolean emission) {
        BroadsideFireControl.Fleet fleet = BroadsideFireControl.fleet(state);
        double time = state.getStartTick() + age;
        boolean inside = CarrierGunneryClient.owns(state.getLaunchId());
        boolean interior = !(inside && CarrierGunneryClient.optic());
        DestroyerBatteryModel.Pose[] poses = BroadsideFireControl.poses(fleet, time);
        float beam = (float) beam(age);
        CarrierFlight.Plan plan = plan(state);
        CarrierRenderer.drawHull(plan, age, emission, (s, e) -> {
            DestroyerBatteryModel.draw(poses, e);
            FireControlGondolaModel.draw(poses, age, e, interior, inside, beam);
        });
        CarrierRenderer.drawEffects(plan, age, emission);
        transfer(state, age);
        BroadsideShotVfx.draw(fleet, time, emission);
    }

    /** Strength of the boarding beam (lit before BOARD) and of the return beam (lit after RETURN). */
    static double beam(double age) {
        double board = smooth((age - BOARD + BEAM) / 6) * (1 - smooth((age - BOARD - 2) / 6));
        double back = smooth((age - RETURN + 2) / 4) * (1 - smooth((age - RETURN - BEAM + 6) / 6));
        return Math.max(board, back);
    }

    /** Light column between the gondola bowl and the caster's column; rings travel with the pilot. */
    private static void transfer(LaunchState state, double age) {
        double strength = beam(age);
        if (strength <= 0.01) return;
        BlockTarget d = state.getDeployment();
        double x = d.getX() + 0.5, z = d.getZ() + 0.5, bottom = d.getY();
        double top = SHIP_Y + FireControlGondolaModel.CENTRE_Y - BroadsideBatteries.GONDOLA_RADIUS;
        boolean rising = age < (BOARD + RETURN) / 2.0;
        float a = (float) strength;
        EffectMesh.column(x, z, bottom, top, 0.55, CORE, a * 0.45F);
        EffectMesh.column(x, z, bottom, top, 1.5, PLASMA, a * 0.16F);
        EffectMesh.column(x, z, bottom, top, 2.6, PLASMA, a * 0.06F);
        for (int k = 0; k < 10; k++) {
            double phase = (age * 0.05 + k / 10.0) % 1;
            double y = rising ? bottom + (top - bottom) * phase : top - (top - bottom) * phase;
            EffectMesh.glowRing(x, y, z, 2.1, 0.3, PLASMA, a * 0.6F * (float) Math.sin(Math.PI * phase));
        }
        EffectMesh.glowRing(x, bottom + 0.08, z, 3 + 0.3 * Math.sin(age * 0.4), 0.6, PLASMA, a * 0.8F);
        MoonAttractVfx.glow(x, bottom + 1, z, 6, PLASMA, a * 0.5F);
        MoonAttractVfx.glow(x, top, z, 7, PLASMA, a * 0.5F);
        // The transfer itself: a flash at both ends of the column.
        double since = Math.min(Math.abs(age - BOARD), Math.abs(age - RETURN));
        float flash = (float) Math.exp(-since / 2.5);
        if (flash > 0.02F) {
            EffectMesh.billboard(x, bottom + 1, z, 9, CORE, flash);
            EffectMesh.billboard(x, top, z, 9, CORE, flash);
        }
    }

    static double smooth(double value) { return CarrierFlight.smooth(value); }
}
