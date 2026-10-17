package fr.frabous.stellar.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class CinematicSoundtrackTest {
    @Test public void pendingAudioSurvivesUntilEngineStartsThenRetiresOnActualEnd() {
        CinematicSoundtrack.LocalSound sound = new CinematicSoundtrack.LocalSound("blackhole_return", false, 0.38F, 1, 3.5F);
        assertFalse(sound.hasFinished(false));
        assertFalse(sound.hasFinished(false));
        assertFalse(sound.hasFinished(true));
        // The engine can finish before its estimated duration stops ticking.
        assertTrue(sound.hasFinished(false));
        assertTrue(sound.isDonePlaying());
    }

    @Test public void estimatedDurationStillRetiresAudioWithoutEngineObservation() {
        CinematicSoundtrack.LocalSound sound = new CinematicSoundtrack.LocalSound("blackhole_return", false, 0.38F, 1, 3.5F);
        for (int tick = 0; tick < 74; tick++) sound.update();
        assertTrue(sound.hasFinished(false));
    }

    @Test public void runningLoopDoesNotExpireAtTheTransientDuration() {
        CinematicSoundtrack.LocalSound sound = new CinematicSoundtrack.LocalSound("blackhole_space_bed", true, 0.12F, 1, 0);
        for (int tick = 0; tick < 800; tick++) {
            sound.update();
            assertFalse(sound.hasFinished(true));
        }
        assertTrue(sound.hasFinished(false));
    }

    @Test public void mutedOrFailedSourceCannotStayOwnedForever() {
        CinematicSoundtrack.LocalSound sound = new CinematicSoundtrack.LocalSound("blackhole_space_bed", true, 0.12F, 1, 0);
        for (int tick = 0; tick < 99; tick++) assertFalse(sound.hasFinished(false));
        assertTrue(sound.hasFinished(false));
    }
}
