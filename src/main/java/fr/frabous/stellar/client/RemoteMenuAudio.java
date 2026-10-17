package fr.frabous.stellar.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/** Short, local UI cues. Never stops the world's sounds or any other screen's audio. */
final class RemoteMenuAudio {
    private final List<Cue> owned = new ArrayList<>();
    private long lastHover;
    private int hovered = -1;

    void hover(int row, long now) {
        if (row == hovered) return;
        hovered = row;
        if (row < 0 || now - lastHover < 75_000_000L) return;
        lastHover = now;
        play("hover", 0.19F, 0.96F + row * 0.055F);
    }

    void play(String name, float volume, float pitch) {
        long now = System.nanoTime();
        owned.removeIf(cue -> now - cue.started > 1_000_000_000L);
        Cue cue = new Cue(name, volume, pitch, now);
        owned.add(cue);
        Minecraft.getMinecraft().getSoundHandler().playSound(cue);
    }

    void stop() {
        for (Cue cue : owned) Minecraft.getMinecraft().getSoundHandler().stopSound(cue);
        owned.clear();
    }

    private static final class Cue extends PositionedSound {
        private final long started;

        Cue(String name, float volume, float pitch, long started) {
            super(new ResourceLocation("stellar", "remote." + name), SoundCategory.MASTER);
            this.volume = volume;
            this.pitch = pitch;
            this.repeat = false;
            this.attenuationType = ISound.AttenuationType.NONE;
            this.started = started;
        }
    }
}
