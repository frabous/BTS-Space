package fr.frabous.stellar.client;

import fr.frabous.stellar.api.LaunchState;
import java.util.UUID;

/** Non-positional player-local mix, driven by the actual ascent and visual entry. */
final class BlackholeSoundtrack {
    private final CinematicSoundtrack audio = new CinematicSoundtrack();
    private UUID launchId;
    private double previousAge = -1;
    private boolean wasInSpace, crossed, released, suspended;
    private CinematicSoundtrack.LocalSound space, accretion, tidal;

    void update(LaunchState state, double age, double spaceStrength, double pull, double entry, boolean revealComplete) {
        audio.prune();
        if (state == null) {
            if (!released) suspend();
            return;
        }
        if (!state.getLaunchId().equals(launchId)) {
            stop();
            launchId = state.getLaunchId();
            previousAge = Math.nextDown(age - 1);
        }
        if (released) return;
        suspended = false;
        if (previousAge < 60 && age >= 60 && age - 60 <= 1) {
            audio.play("blackhole_ascent", false, 0.48F, 1, 3);
        }
        if (spaceStrength > 0.01 && !wasInSpace) {
            wasInSpace = true;
            audio.play("blackhole_reveal", false, 0.40F, 1, 4);
        }
        if (wasInSpace) {
            if (age >= 740) {
                finish(state.getLaunchId());
                return;
            }
            if (space == null) {
                space = audio.play("blackhole_space_bed", true, 0.01F, 1, 0);
                accretion = audio.play("blackhole_accretion", true, 0.01F, 1, 0);
                tidal = audio.play("blackhole_tidal", true, 0.01F, 1, 0);
            }
            double collapse = revealComplete ? entry : 0;
            double ending = 1 - BlackholeVisualMath.smooth((age - 700) / 40);
            float strength = (float) (spaceStrength * ending);
            space.setMix(strength * 0.12F * (float) (1 - collapse * 0.85), (float) (1 - collapse * 0.06));
            accretion.setMix(strength * 0.28F * (float) (1 - collapse * 0.92), (float) (1 - pull * 0.12 - collapse * 0.08));
            tidal.setMix(strength * 0.24F * (float) (pull * (1 - collapse * 0.85)), (float) (1 + pull * 0.08 - collapse * 0.16));
            if (revealComplete && entry >= 0.65 && !crossed && age < 700) {
                crossed = true;
                audio.play("blackhole_horizon", false, 0.55F, 1, 3);
            }
        }
        previousAge = Math.max(previousAge, age);
    }

    /** Server cancellation also lands the player; allow its short release tail to finish. */
    void finish(UUID id) {
        if (!id.equals(launchId) || !wasInSpace || released) return;
        audio.stop();
        space = accretion = tidal = null;
        released = true;
        if (!suspended) audio.play("blackhole_return", false, 0.38F, 1, 3.5F);
    }

    /** Temporary loss of presentation keeps cues consumed for this launch. */
    void suspend() {
        audio.stop();
        space = accretion = tidal = null;
        suspended = true;
    }

    void stop() {
        audio.stop();
        launchId = null;
        previousAge = -1;
        wasInSpace = crossed = released = suspended = false;
        space = accretion = tidal = null;
    }
}
