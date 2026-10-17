package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;

/**
 * Caster's score. The field hums and strains while it climbs, every flip snaps, and the hang is cut to
 * near silence so the collapse lands on an empty track.
 */
final class GravityInversionSoundtrack {
    private final CinematicSoundtrack audio = new CinematicSoundtrack();
    private double previousAge = -1;
    private CinematicSoundtrack.LocalSound bed;
    private CinematicSoundtrack.LocalSound strain;

    void update(double age) {
        if (age < previousAge || age >= END) { stop(); return; }
        if (previousAge >= 0 && age - previousAge > 4) { silence(); previousAge = Math.nextDown(age - 1); }
        audio.prune();

        if (age < COLLAPSE) {
            if (bed == null) bed = audio.play("blackhole_space_bed", true, 0.01F, 0.55F, 0);
            float swell = (float) smooth(age / COLLAPSE);
            bed.setMix(0.08F + 0.16F * swell, 0.55F + 0.2F * swell);
        }
        if (age >= ASCENSION && age < COLLAPSE) {
            if (strain == null) strain = audio.play("tether_strain_loop", true, 0.01F, 0.6F, 0);
            float rise = (float) smooth((age - ASCENSION) / (COLLAPSE - ASCENSION));
            strain.setMix(0.08F + 0.26F * rise, 0.6F + 0.35F * rise);
        }

        // Gravity Failure: the ground cracks open one fissure at a time.
        cue(age, 2, "blackhole_reveal", 4.0F, 0.42F, 0.62F);
        for (int i = 0; i < 5; i++) cue(age, 8 + i * 13, "electric_snap", 0.32F, 0.18F + i * 0.04F, 0.5F + i * 0.08F);
        cue(age, 36, "moon_attract_gravity_charge", 5.0F, 0.42F, 0.72F);

        // World Ascension.
        cue(age, ASCENSION, "moon_attract_moon_gravitational_pull", 8.0F, 0.55F, 0.8F);
        cue(age, ASCENSION + 2, "asteroid_sonic_boom", 3.0F, 0.32F, 0.55F);
        cue(age, 120, "blackhole_tidal", 3.75F, 0.42F, 0.74F);
        cue(age, 170, "blackhole_horizon", 3.0F, 0.3F, 0.62F);

        // Axis Rotation: one hit per gravity flip, pitched up each time.
        for (int k = 0; k < FLIPS; k++) {
            double tick = ROTATION + k * FLIP;
            cue(age, tick, "tether_snap", 2.0F, 0.75F, 0.8F + 0.1F * k);
            cue(age, tick, "asteroid_sonic_boom", 3.0F, 0.5F, 0.7F + 0.08F * k);
            cue(age, tick + 1, "electric_snap", 0.32F, 0.4F, 1.1F + 0.15F * k);
        }
        cue(age, ROTATION + 6, "blackhole_horizon", 3.0F, 0.34F, 0.8F);
        cue(age, COLLAPSE - 16, "moon_attract_pre_impact_tension", 1.7F, 0.6F, 1.05F);

        // Total Collapse: hard cut to silence for the hang, then the slam.
        if (age >= COLLAPSE && previousAge < COLLAPSE) silence();
        cue(age, COLLAPSE, "tunnel", 1.0F, 0.3F, 0.45F);
        cue(age, SLAM, "kinetic_hammer_fall", 2.0F, 0.85F, 0.8F);
        cue(age, SLAM, "descent", 1.1F, 0.6F, 0.7F);
        cue(age, IMPACT, "impact_hit", 2.0F, 1.0F, 0.5F);
        cue(age, IMPACT, "kinetic_hammer_impact", 3.0F, 0.95F, 0.6F);
        cue(age, IMPACT, "moon_attract_moon_impact", 6.0F, 0.7F, 0.72F);
        cue(age, IMPACT, "cannon_shot", 2.0F, 0.6F, 0.42F);
        cue(age, IMPACT + 4, "moon_attract_shockwave_expansion", 4.0F, 0.62F, 0.75F);
        cue(age, IMPACT + 10, "moon_attract_debris_rain", 6.0F, 0.4F, 0.9F);
        cue(age, IMPACT + 16, "rumble_tail", 2.6F, 0.5F, 0.6F);
        cue(age, SHOCK + 6, "moon_attract_aftermath_ambience", 8.0F, 0.3F, 0.8F);
        previousAge = age;
    }

    private void cue(double age, double tick, String name, float seconds, float volume, float pitch) {
        // The film can open a few ticks late on a laggy link; the opening cues still play.
        double late = previousAge < 0 ? 6 : 1;
        if (previousAge < tick && tick <= age && age - tick <= late) audio.play(name, false, volume, pitch, seconds);
    }

    private void silence() { audio.stop(); bed = null; strain = null; }

    void stop() { silence(); previousAge = -1; }
}
