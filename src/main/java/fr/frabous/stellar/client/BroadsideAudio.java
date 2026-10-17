package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

/**
 * SS-03B sound field (scripts/generate-broadside-sfx.py). Gun reports, shell cracks and impacts are
 * positional and reach each listener at the speed of sound, so a witness on the ground sees the muzzle
 * flash before the report arrives. Battery drives hum while they traverse; the seated pilot also hears
 * the gondola bed and fire-control tones.
 */
final class BroadsideAudio {
    /** 343 m/s at one block per metre, in blocks per tick. */
    static final double SOUND_SPEED = 343 / 20.0;
    private static final List<Cue> CUES = new ArrayList<>();
    private static final Map<UUID, Servo[]> SERVOS = new HashMap<>();
    private static final Map<UUID, Long> AGES = new HashMap<>();
    private static Bed bed;

    private BroadsideAudio() { }

    private static final class Cue {
        final long tick; final String name; final double x, y, z; final float range, pitch;
        Cue(long tick, String name, double x, double y, double z, float range, float pitch) {
            this.tick = tick; this.name = name; this.x = x; this.y = y; this.z = z; this.range = range; this.pitch = pitch;
        }
    }

    static void shot(BroadsideFireControl.Fleet fleet, CarrierShot shot) {
        Entity listener = Minecraft.getMinecraft().getRenderViewEntity();
        if (listener == null) return;
        double lx = listener.posX, ly = listener.posY + listener.getEyeHeight(), lz = listener.posZ;
        float pitch = (float) (0.93 + 0.14 * CarrierRenderer.seed((int) shot.fire * 7 + shot.battery * 13 + shot.barrel));
        double muzzle = distance(lx, ly, lz, shot.x, shot.y, shot.z), impact = distance(lx, ly, lz, shot.hitX, shot.hitY, shot.hitZ);
        cue(shot.fire + delay(muzzle), "broadside_fire", shot.x, shot.y, shot.z, 200, pitch);
        cue(shot.fire + delay(muzzle), "broadside_fire_far", shot.x, shot.y, shot.z, 900, pitch);
        cue(shot.impact + delay(impact), "broadside_impact", shot.hitX, shot.hitY, shot.hitZ, 160, pitch);
        cue(shot.impact + delay(impact), "broadside_impact_far", shot.hitX, shot.hitY, shot.hitZ, 800, pitch);
        // The slug outruns its own report: listeners near the path hear its crack as it passes.
        double dx = shot.hitX - shot.x, dy = shot.hitY - shot.y, dz = shot.hitZ - shot.z, length = dx * dx + dy * dy + dz * dz;
        double u = length < 1.0E-6 ? 0 : ((lx - shot.x) * dx + (ly - shot.y) * dy + (lz - shot.z) * dz) / length;
        double px = shot.x + dx * u, py = shot.y + dy * u, pz = shot.z + dz * u, miss = distance(lx, ly, lz, px, py, pz);
        if (u > 0.08 && u < 0.9 && miss < 48)
            cue(shot.fire + Math.round(u * (shot.impact - shot.fire)) + delay(miss), "broadside_shell_flyby", px, py, pz, 64, pitch);
        double[] ring = BroadsideBatteries.ring(fleet.frame, shot.battery);
        cue(shot.fire + 6 + delay(distance(lx, ly, lz, ring[0], ring[1], ring[2])), "broadside_reload", ring[0], ring[1], ring[2], 80, 1);
        if (CarrierGunneryClient.owns(shot.launchId)) local("broadside_lock", 0.55F, 1);
    }

    /** No battery can bear on the crosshair. */
    static void denied() { local("broadside_lock", 0.5F, 0.55F); }

    static void tick(WorldClient world, Collection<BroadsideFireControl.Fleet> fleets) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity listener = mc.getRenderViewEntity();
        if (world == null || listener == null) { reset(); return; }
        long now = world.getTotalWorldTime();
        for (Iterator<Cue> it = CUES.iterator(); it.hasNext(); ) {
            Cue cue = it.next();
            if (cue.tick > now) continue;
            it.remove();
            // A cue missed by a stall is dropped rather than replayed late.
            if (now - cue.tick <= 3) play(new WorldSound(cue.name, cue.x, cue.y, cue.z, cue.range, cue.pitch));
        }
        Map<UUID, BroadsideFireControl.Fleet> live = new HashMap<>();
        for (BroadsideFireControl.Fleet fleet : fleets) live.put(fleet.launch.getLaunchId(), fleet);
        for (Iterator<Map.Entry<UUID, Servo[]>> it = SERVOS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Servo[]> entry = it.next();
            if (live.containsKey(entry.getKey())) continue;
            for (Servo servo : entry.getValue()) servo.done = true;
            it.remove();
        }
        AGES.keySet().retainAll(live.keySet());
        for (BroadsideFireControl.Fleet fleet : live.values()) {
            UUID id = fleet.launch.getLaunchId();
            long age = now - fleet.launch.getStartTick();
            Long previous = AGES.put(id, age);
            transfers(fleet, previous == null ? age - 1 : previous, age, listener);
            if (!SERVOS.containsKey(id) && age >= CarrierGunneryRules.BOARD - 20 && age < CarrierGunneryRules.END) {
                Servo[] servos = new Servo[BroadsideBatteries.COUNT];
                for (int i = 0; i < servos.length; i++) { servos[i] = new Servo(fleet, i); play(servos[i]); }
                SERVOS.put(id, servos);
            }
        }
        boolean aboard = CarrierGunneryClient.aboard();
        if (aboard && (bed == null || bed.done)) play(bed = new Bed());
        if (!aboard && bed != null) { bed.fading = true; bed = null; }
        if (CUES.size() > 256) CUES.subList(0, CUES.size() - 256).clear();
    }

    /** Boarding and return beams; the pilot hears the transfer at full level wherever the beam is. */
    private static void transfers(BroadsideFireControl.Fleet fleet, long previous, long age, Entity listener) {
        BlockTarget d = fleet.launch.getDeployment();
        double x = d.getX() + 0.5, z = d.getZ() + 0.5, y = (d.getY() + CarrierGunneryRules.SEAT_Y) / 2;
        boolean pilot = CarrierGunneryClient.commands(fleet.launch.getLaunchId());
        for (long tick : new long[] {CarrierGunneryRules.BOARD - 12, CarrierGunneryRules.RETURN - 2}) {
            if (previous >= tick || age < tick) continue;
            if (pilot) local("broadside_board", 0.8F, 1);
            else play(new WorldSound("broadside_board", x, y, z, 128, 1));
        }
    }

    static void reset() {
        CUES.clear();
        for (Servo[] servos : SERVOS.values()) for (Servo servo : servos) servo.done = true;
        SERVOS.clear(); AGES.clear();
        if (bed != null) bed.done = true;
        bed = null;
    }

    static void cue(long tick, String name, double x, double y, double z, float range, float pitch) {
        CUES.add(new Cue(tick, name, x, y, z, range, pitch));
    }

    static long delay(double distance) { return Math.round(distance / SOUND_SPEED); }

    static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Pilot-only one-shot placed on the listener, so it is neither attenuated nor panned. */
    private static void local(String name, float volume, float pitch) {
        Entity listener = Minecraft.getMinecraft().getRenderViewEntity();
        if (listener == null) return;
        WorldSound sound = new WorldSound(name, listener.posX, listener.posY + listener.getEyeHeight(), listener.posZ, 16, pitch);
        sound.level(volume);
        play(sound);
    }

    private static void play(ISound sound) { Minecraft.getMinecraft().getSoundHandler().playSound(sound); }

    private static ResourceLocation location(String name) { return new ResourceLocation(StellarIds.MOD_ID, "cinematic." + name); }

    /** Positional one-shot: full level at the source, linear fall-off to its range. */
    private static final class WorldSound extends PositionedSound {
        WorldSound(String name, double x, double y, double z, float range, float pitch) {
            super(location(name), SoundCategory.PLAYERS);
            xPosF = (float) x; yPosF = (float) y; zPosF = (float) z;
            volume = range / 16F; this.pitch = pitch;
            attenuationType = ISound.AttenuationType.LINEAR;
        }
        void level(float gain) { volume = gain; }
    }

    /**
     * Looping source. SoundManager reads the volume once for the attenuation distance, then again for
     * the gain: the first read returns the range, every later one the loop's own level.
     */
    private abstract static class Loop extends PositionedSound implements ITickableSound {
        private final float range;
        private boolean created;
        float gain = 0.002F;
        boolean done, fading;

        Loop(String name, float range) {
            super(location(name), SoundCategory.PLAYERS);
            this.range = range; repeat = true; repeatDelay = 0;
            attenuationType = ISound.AttenuationType.LINEAR;
        }

        @Override public float getVolume() {
            if (!created) { created = true; return range / 16F; }
            return gain;
        }

        void ease(float target, float rate) {
            gain += ((fading ? 0 : target) - gain) * rate;
            gain = Math.max(0.002F, gain);
            if (fading && gain < 0.005F) done = true;
        }

        @Override public boolean isDonePlaying() { return done; }
    }

    /** Traverse and elevation drive of one battery, louder and higher while it slews fast. */
    private static final class Servo extends Loop {
        private final BroadsideFireControl.Fleet fleet;
        private final int battery;

        Servo(BroadsideFireControl.Fleet fleet, int battery) {
            super("broadside_servo_loop", 96);
            this.fleet = fleet; this.battery = battery;
            place();
        }

        private void place() {
            double[] ring = BroadsideBatteries.ring(fleet.frame, battery);
            xPosF = (float) ring[0]; yPosF = (float) ring[1]; zPosF = (float) ring[2];
        }

        @Override public void update() {
            place();
            double speed = fleet.batteries[battery].speed / BroadsideBatteries.YAW_RATE;
            float drive = (float) Math.min(1, speed * 1.6);
            ease(0.85F * drive, drive > gain ? 0.5F : 0.25F);
            pitch = 0.8F + 0.35F * drive;
        }
    }

    /** Gondola bed: hull hum, air handling and telemetry, on the pilot. */
    private static final class Bed extends Loop {
        Bed() { super("broadside_bridge_loop", 16); follow(); }

        private void follow() {
            Entity listener = Minecraft.getMinecraft().getRenderViewEntity();
            if (listener == null) { done = true; return; }
            xPosF = (float) listener.posX; yPosF = (float) (listener.posY + listener.getEyeHeight()); zPosF = (float) listener.posZ;
        }

        @Override public void update() {
            follow();
            ease(CarrierGunneryClient.optic() ? 0.22F : 0.45F, 0.15F);
        }
    }
}
