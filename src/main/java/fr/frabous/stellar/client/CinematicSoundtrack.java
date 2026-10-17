package fr.frabous.stellar.client;

import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.LaunchTimeline;
import fr.frabous.stellar.api.StellarIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/** Local cinematic audio. Call update once per client tick, and stop when the film closes. */
public final class CinematicSoundtrack {
    private final List<LocalSound> owned = new ArrayList<>();
    private UUID launchId;
    private double previousAge;
    private LocalSound charge;
    private LocalSound beam;

    public void update(LaunchState state, double ageTicks) {
        if (state == null || !Double.isFinite(ageTicks) || ageTicks < 0
                || state.getPhase() == LaunchPhase.COMPLETE
                || ageTicks >= LaunchTimeline.totalTicks(state.getAttack())) {
            stop();
            return;
        }
        if (!state.getLaunchId().equals(launchId)) {
            stop();
            launchId = state.getLaunchId();
            // A new/late presentation only plays a transient if it has just become due.
            previousAge = Math.nextDown(ageTicks - 1);
        } else if (ageTicks < previousAge || ageTicks - previousAge > 4) {
            stopOwned();
            previousAge = Math.nextDown(ageTicks - 1);
        }
        prune();
        LaunchPhase phase = LaunchTimeline.phaseAt(state.getAttack(), (long) ageTicks);
        double start = LaunchTimeline.phaseStartTicks(state.getAttack(), phase);
        double progress = (ageTicks - start) / LaunchTimeline.durationTicks(state.getAttack(), phase);

        cue(state, ageTicks, LaunchPhase.MARKING, 0, "ascent", 2.7F, 0.55F, 1);
        cue(state, ageTicks, LaunchPhase.ASCENT, 0.10, "warp", 1.9F, 0.38F, 0.85F);
        cue(state, ageTicks, LaunchPhase.ASCENT, 0.52, "warp", 1.9F, 0.42F, 1.15F);
        cue(ageTicks, RailgunVisualTiming.LOCK - 0.5, "electric_snap", 0.32F, 0.22F, 1.7F);
        cue(state, ageTicks, LaunchPhase.CHARGING, 0, "flyby", 2.2F, 0.45F, 1);
        cue(ageTicks, RailgunVisualTiming.UPLINK - 0.3, "electric_snap", 0.32F, 0.3F, 1.5F);
        cue(ageTicks, 207, "flyby", 2.2F, 0.3F, 0.8F);
        // Sparse cracks while the cannon spools, tightening as the arcs close their gaps.
        cue(ageTicks, 229, "electric_snap", 0.32F, 0.2F, 0.8F);
        cue(ageTicks, 237, "electric_snap", 0.32F, 0.26F, 0.95F);
        cue(ageTicks, 242, "electric_snap", 0.32F, 0.3F, 1.05F);
        cue(ageTicks, 245, "electric_snap", 0.32F, 0.34F, 0.9F);
        cue(ageTicks, 247.5, "electric_snap", 0.32F, 0.38F, 1.2F);
        cue(ageTicks, 249.5, "electric_snap", 0.32F, 0.42F, 1.35F);
        cue(ageTicks, RailgunVisualTiming.FIRE, "cannon_shot", 2.0F, 0.95F, 1);
        cue(ageTicks, RailgunVisualTiming.FIRE, "impact_hit", 2.0F, 0.35F, 1.4F);
        cue(ageTicks, RailgunVisualTiming.TRANSIT, "warp", 1.9F, 0.42F, 1.15F);
        cue(ageTicks, 268, "tunnel", 1.0F, 0.42F, 1);
        cue(ageTicks, RailgunVisualTiming.SKY, "descent", 1.1F, 0.48F, 1);
        cue(ageTicks, RailgunVisualTiming.IMPACT, "impact_hit", 2.0F, 0.8F, 1);
        cue(ageTicks, RailgunVisualTiming.IMPACT, "cannon_shot", 2.0F, 0.45F, 0.55F);
        cue(ageTicks, RailgunVisualTiming.RELEASE, "impact_hit", 2.0F, 0.4F, 0.75F);
        cue(ageTicks, RailgunVisualTiming.BEAM_END, "rumble_tail", 2.6F, 0.48F, 1);

        // Low spool through the cannon reveal, steep rise during the charge, then cut dead for the inhale.
        if (ageTicks >= RailgunVisualTiming.SPOOL_START && ageTicks < RailgunVisualTiming.INHALE + 1) {
            float level = (float) RailgunVisualTiming.charge(ageTicks);
            if (charge == null) charge = play("charge_loop", true, 0.01F, 0.6F, 0);
            charge.setMix(0.05F + 0.43F * level * level, 0.6F + 1.2F * level);
        } else if (charge != null) {
            charge.fadeOut();
            charge = null;
        }
        if (ageTicks >= RailgunVisualTiming.FIRE && ageTicks < RailgunVisualTiming.BEAM_END) {
            if (beam == null) beam = play("beam_loop", true, 0.01F, 1, 0);
            if (ageTicks < RailgunVisualTiming.IMPACT) {
                float flight = (float) ((ageTicks - RailgunVisualTiming.FIRE)
                        / (RailgunVisualTiming.IMPACT - RailgunVisualTiming.FIRE));
                beam.setMix(0.18F + 0.10F * flight, 1.05F);
            } else {
                float impact = (float) ((ageTicks - RailgunVisualTiming.IMPACT)
                        / (RailgunVisualTiming.BEAM_END - RailgunVisualTiming.IMPACT));
                float fade = (float) Math.max(0, (ageTicks - RailgunVisualTiming.SHRINK)
                        / (RailgunVisualTiming.BEAM_END - RailgunVisualTiming.SHRINK));
                beam.setMix(0.37F * (1 - fade), 0.94F - 0.28F * impact);
            }
        } else if (beam != null) {
            beam.done = true;
            Minecraft.getMinecraft().getSoundHandler().stopSound(beam);
            owned.remove(beam);
            beam = null;
        }
        if (phase == LaunchPhase.AFTERMATH && progress >= 0.80) {
            for (LocalSound sound : owned) sound.fadeOut();
        }
        previousAge = ageTicks;
    }

    /** Stops only the sound instances created by this soundtrack. */
    public void stop() {
        stopOwned();
        launchId = null;
        previousAge = -1;
    }

    private void stopOwned() {
        for (LocalSound sound : owned) {
            sound.done = true;
            Minecraft.getMinecraft().getSoundHandler().stopSound(sound);
        }
        owned.clear();
        charge = null;
        beam = null;
    }

    private void cue(LaunchState state, double age, LaunchPhase phase, double fraction,
                     String name, float seconds, float volume, float pitch) {
        double tick = LaunchTimeline.phaseStartTicks(state.getAttack(), phase)
                + fraction * LaunchTimeline.durationTicks(state.getAttack(), phase);
        cue(age, tick, name, seconds, volume, pitch);
    }

    private void cue(double age, double tick, String name, float seconds, float volume, float pitch) {
        if (previousAge < tick && tick <= age && age - tick <= 1) {
            play(name, false, volume, pitch, seconds);
        }
    }

    void prune() {
        // A sound queued on the asynchronous engine is not yet isSoundPlaying; keep ownership.
        owned.removeIf(sound -> sound.hasFinished(Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(sound)));
    }

    LocalSound play(String name, boolean loop, float volume, float pitch, float seconds) {
        return playEvent(new ResourceLocation(StellarIds.MOD_ID,"cinematic."+name),loop,volume,pitch,seconds);
    }

    LocalSound playEvent(ResourceLocation event, boolean loop, float volume, float pitch, float seconds) {
        LocalSound sound = new LocalSound(event, loop, volume, pitch, seconds);
        owned.add(sound);
        Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        return sound;
    }

    /** Direct ResourceLocation access requires sounds.json, but no server SoundEvent registry. */
    static final class LocalSound extends PositionedSound implements ITickableSound {
        private float targetVolume;
        private float targetPitch;
        private int ticksLeft;
        private boolean fading;
        private boolean done;
        private boolean started;
        private int pendingTicks;

        LocalSound(String name, boolean loop, float volume, float pitch, float seconds) {
            this(new ResourceLocation(StellarIds.MOD_ID,"cinematic."+name),loop,volume,pitch,seconds);
        }

        LocalSound(ResourceLocation event, boolean loop, float volume, float pitch, float seconds) {
            super(event, SoundCategory.PLAYERS);
            this.repeat = loop;
            this.repeatDelay = 0;
            this.attenuationType = ISound.AttenuationType.NONE;
            this.volume = volume;
            this.pitch = pitch;
            this.targetVolume = volume;
            this.targetPitch = pitch;
            this.ticksLeft = loop ? -1 : (int) Math.ceil(seconds * 20 / pitch) + 4;
        }

        void setMix(float volume, float pitch) {
            targetVolume = volume;
            targetPitch = pitch;
        }

        void fadeOut() {
            fading = true;
            targetVolume = 0;
        }

        boolean hasFinished(boolean playing) {
            if (playing) started = true;
            if (started && !playing) done = true;
            // Bound muted/failed sources while allowing the asynchronous engine five seconds to start.
            if (!started && ++pendingTicks >= 100) done = true;
            return done;
        }

        @Override
        public void update() {
            volume += (targetVolume - volume) * (fading ? 0.5F : 0.4F);
            pitch += (targetPitch - pitch) * 0.4F;
            if (fading && volume < 0.004F) done = true;
            if (ticksLeft > 0 && --ticksLeft == 0) done = true;
        }

        @Override
        public boolean isDonePlaying() { return done; }
    }
}
