package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaTiming;

/**
 * Non-diegetic score only: space carries no physical boom, so the hits are stylised stings.
 * Everything is cut just before the detonation to leave the blackout frame silent.
 * The world-owned witness mix supplies the atmospheric sound once the light arrives.
 */
final class SupernovaSoundtrack {
    static final double INHALE = 306;
    static final double SILENCE = 319;
    static final double HAND_OFF = 396;

    private final CinematicSoundtrack audio = new CinematicSoundtrack();
    private double previousAge = -1;
    private CinematicSoundtrack.LocalSound charge;
    private CinematicSoundtrack.LocalSound bed;
    private CinematicSoundtrack.LocalSound tension;

    void update(double age) {
        if (age < previousAge || age >= SupernovaTiming.END) { stop(); return; }
        if (previousAge >= 0 && age-previousAge > 4) {
            audio.stop(); charge = null; bed = null; tension = null; previousAge = Math.nextDown(age-1);
        }
        audio.prune();
        float heat = (float)SupernovaVisuals.heat(age);

        if (age >= SupernovaTiming.STAR && age < SILENCE) {
            if (bed == null) bed = audio.play("blackhole_space_bed",true,0.01F,0.7F,0);
            float swell = (float)Math.max(0,(age-SupernovaTiming.SHOCK_REVIVAL)/(SILENCE-SupernovaTiming.SHOCK_REVIVAL));
            bed.setMix(0.10F+0.12F*swell,0.7F-0.12F*swell);
        }
        if (age >= SupernovaTiming.STAR && age < INHALE) {
            if (charge == null) charge = audio.play("charge_loop",true,0.01F,0.7F,0);
            charge.setMix(0.045F+heat*0.06F,0.6F+heat*0.16F);
        }
        if (age >= INHALE && previousAge < INHALE && charge != null) { charge.fadeOut(); charge = null; }

        cue(age, SupernovaTiming.STAR, "blackhole_reveal", 4.0F, 0.42F, 0.72F);
        cue(age, SupernovaTiming.COLLAPSE, "blackhole_tidal", 3.75F, 0.38F, 0.78F);
        cue(age, SupernovaTiming.COLLAPSE+40, "electric_snap", 0.32F, 0.22F, 0.6F);
        cue(age, SupernovaTiming.SHOCK_REVIVAL, "moon_attract_gravity_charge", 5.0F, 0.42F, 0.86F);
        if (previousAge < INHALE && INHALE <= age && age-INHALE <= 1) {
            tension = audio.play("moon_attract_pre_impact_tension",false,0.55F,1.2F,1.7F);
        }
        if (tension != null && age >= INHALE) tension.setMix(0.55F+0.35F*(float)SupernovaVisuals.inhale(age),1.2F+0.25F*(float)SupernovaVisuals.inhale(age));
        cue(age, 312, "electric_snap", 0.32F, 0.30F, 0.9F);
        cue(age, 315, "electric_snap", 0.32F, 0.36F, 1.2F);
        cue(age, 317.5, "electric_snap", 0.32F, 0.42F, 1.6F);
        cue(age, 318, "moon_attract_maximum_charge", 1.0F, 0.5F, 1.3F);

        // Hard cut: one tick of silence under the blackout makes the detonation land.
        if (age >= SILENCE && previousAge < SILENCE) silence();

        cue(age, SupernovaTiming.DETONATE, "impact_hit", 2.0F, 1.0F, 0.55F);
        cue(age, SupernovaTiming.DETONATE, "cannon_shot", 2.0F, 0.72F, 0.5F);
        cue(age, SupernovaTiming.DETONATE, "moon_attract_moon_impact", 6.0F, 0.62F, 0.82F);
        cue(age, SupernovaTiming.DETONATE+3, "moon_attract_shockwave_expansion", 4.0F, 0.6F, 0.8F);
        cue(age, SupernovaTiming.DETONATE+10, "rumble_tail", 2.6F, 0.5F, 0.66F);
        cue(age, SupernovaTiming.DETONATE+34, "blackhole_horizon", 3.0F, 0.3F, 0.7F);
        cue(age, SupernovaTiming.WORLD_RETURN, "warp", 1.9F, 0.45F, 0.78F);
        cue(age, SupernovaTiming.WORLD_RETURN+8, "descent", 1.1F, 0.42F, 0.85F);

        if (age >= HAND_OFF && previousAge < HAND_OFF) silence();
        previousAge = age;
    }

    private void cue(double age, double tick, String name, float seconds, float volume, float pitch) {
        if (previousAge < tick && tick <= age && age - tick <= 1) audio.play(name,false,volume,pitch,seconds);
    }

    private void silence() {
        audio.stop(); charge = null; bed = null; tension = null;
    }

    void stop() { silence(); previousAge = -1; }
}
