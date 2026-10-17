package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.AsteroidPullerTiming.*;

/**
 * Absolute world-tick cues; never replay missed transients after a stall or late join.
 * carrier_*, lance_*, tether_* and asteroid_* are the synthesized SS-03 set
 * (scripts/generate-asteroid-puller-sfx.py); offsets put each sound's accent on its animation tick.
 */
final class AsteroidPullerSoundtrack {
    private final CinematicSoundtrack audio = new CinematicSoundtrack();
    private double previousAge = -1;
    private CinematicSoundtrack.LocalSound pull, strain, drive, aftermath;

    void update(double age) {
        if (age < previousAge || age >= END) { stop(); return; }
        if (previousAge >= 0 && age - previousAge > 4) {
            audio.stop(); pull = strain = drive = aftermath = null;
            previousAge = Math.nextDown(age - 1);
        }
        audio.prune();
        // Designation and carrier arrival: exit burst 0.8 s in, collapse pinch 0.55 s in.
        cue(age, UPLINK, "electric_snap", 0.32F, 0.32F, 0.75F);
        cue(age, UPLINK + 2, "ascent", 2.7F, 0.28F, 1.25F);
        cue(age, WARP_EXIT - 16, "carrier_warp_open", 2.4F, 0.75F, 1);
        cue(age, WARP_EXIT + 6, "carrier_brake", 2.6F, 0.5F, 1);
        cue(age, WARP_CLEAR, "carrier_warp_collapse", 2, 0.6F, 1);
        cue(age, BAY_OPEN, "carrier_bay_open", 2.8F, 0.6F, 1);
        cue(age, RELEASE, "carrier_clamp_release", 1.6F, 0.65F, 1);
        // Drop, landing and the carrier leaving: retro cut-off 1.1 s in, spool ignition 0.7 s in,
        // unfold locks 0.8/1.3/1.5/1.9 s in (barrel stages, fins, crown).
        cue(age, RELEASE + 4, "lance_reentry", 2.8F, 0.55F, 1);
        cue(age, RETRO, "lance_retro", 1.8F, 0.6F, 1);
        cue(age, LAND, "lance_touchdown", 2.6F, 0.8F, 1);
        cue(age, ANCHOR, "lance_anchor", 1.4F, 0.6F, 1);
        cue(age, SHIP_SPOOL, "carrier_spool", 3.2F, 0.5F, 1);
        cue(age, UNFOLD + 4, "lance_unfold", 2.8F, 0.45F, 1);
        cue(age, SHIP_JUMP - 16, "carrier_warp_open", 2.4F, 0.55F, 1);
        cue(age, SHIP_JUMP + 3, "carrier_warp_collapse", 2, 0.45F, 1);
        // Inbound asteroid, charge, harpoon and lock.
        cue(age, APPEAR + 10, "moon_attract_moon_approaching", 11.7F, 0.3F, 1);
        cue(age, CHARGE, "moon_attract_gravity_charge", 2, 0.44F, 1);
        cue(age, CHARGE + 22, "electric_snap", 0.32F, 0.24F, 1.05F);
        cue(age, MAXIMUM_CHARGE, "moon_attract_maximum_charge", 1, 0.44F, 1);
        cue(age, FIRE, "lance_harpoon", 2.6F, 0.85F, 1);
        cue(age, FIRE, "moon_attract_gravity_beam_fire", 3, 0.45F, 1);
        cue(age, LOCK, "tether_lock", 3.4F, 0.85F, 1);
        cue(age, LOCK + 2, "moon_attract_moon_fragmentation", 4.7F, 0.22F, 0.8F);
        // Entry and collision: bow shock then fragment shock 0.34 s apart.
        cue(age, ATMOSPHERE, "moon_attract_atmospheric_entry", 5.7F, 0.55F, 1);
        cue(age, 526, "asteroid_sonic_boom", 2.8F, 0.75F, 1);
        cue(age, 530, "moon_attract_moon_fragmentation", 4.7F, 0.25F, 1);
        cue(age, 538, "moon_attract_pre_impact_tension", 1.7F, 0.5F, 1);
        cue(age, SEVER, "tether_snap", 2.4F, 0.8F, 1);
        if (age >= SILENCE && previousAge < SILENCE) { audio.stop(); pull = strain = drive = null; }
        cue(age, IMPACT, "moon_attract_moon_impact", 6, 0.92F, 1);
        cue(age, SHOCKWAVE, "moon_attract_shockwave_expansion", 4, 0.58F, 1);
        cue(age, DEBRIS, "moon_attract_debris_rain", 8, 0.32F, 1);
        // Beds: carrier drive, gravitational pull, tether strain, aftermath air.
        if (age >= WARP_EXIT && age < SHIP_JUMP + 4) {
            if (drive == null) drive = audio.play("carrier_drive_loop", true, 0.01F, 0.85F, 0);
            double arrive = AsteroidPullerScene.smooth((age - WARP_EXIT) / 10);
            double spool = AsteroidPullerScene.smooth((age - SHIP_SPOOL) / 40);
            drive.setMix((float) (0.14 * arrive + 0.24 * spool), (float) (0.85 + 0.45 * spool));
        } else if (drive != null) { drive.fadeOut(); drive = null; }
        if (age >= LOCK && age < SILENCE) {
            if (pull == null) pull = audio.play("moon_attract_moon_gravitational_pull", true, 0.01F, 1, 0);
            float rise = (float) AsteroidPullerScene.smooth((age - LOCK) / 160);
            pull.setMix(0.1F + rise * 0.2F, 0.9F + rise * 0.2F);
        }
        if (age >= LOCK && age < SEVER) {
            if (strain == null) strain = audio.play("tether_strain_loop", true, 0.01F, 1, 0);
            // The groan deepens as the tether takes the asteroid's full momentum.
            float load = (float) AsteroidPullerScene.smooth((age - LOCK) / 120);
            strain.setMix(0.12F + 0.3F * load, 1 - 0.15F * load);
        } else if (strain != null) { strain.fadeOut(); strain = null; }
        if (age >= AFTERMATH) {
            if (aftermath == null) aftermath = audio.play("moon_attract_aftermath_ambience", true, 0.01F, 1, 0);
            aftermath.setMix((float) (0.22 * (1 - AsteroidPullerScene.smooth((age - FADE) / 40))), 1);
        }
        previousAge = age;
    }

    private void cue(double age, int tick, String name, float duration, float volume, float pitch) {
        if (previousAge < tick && age >= tick && age - tick <= 1) audio.play(name, false, volume, pitch, duration);
    }

    void stop() { audio.stop(); previousAge = -1; pull = strain = drive = aftermath = null; }
}
