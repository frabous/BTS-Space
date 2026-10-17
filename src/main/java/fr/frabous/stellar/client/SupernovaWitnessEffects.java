package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.SupernovaTiming;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Environmental audio belongs to the world launch, not to its optional GUI or the viewer's F5 mode. */
final class SupernovaWitnessEffects {
    private final LocalClientBridge bridge;
    private final Map<UUID,Track> tracks=new HashMap<>();
    private WorldClient world;

    SupernovaWitnessEffects(LocalClientBridge bridge) { this.bridge=bridge; }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();
        if (world!=mc.world || mc.player==null) { clear();world=mc.world; }
        if (world==null || mc.player==null) return;
        Set<UUID> active=new HashSet<>();
        boolean underwater=mc.player.isInWater();
        Chunk chunk=world.getChunkProvider().getLoadedChunk(MathHelper.floor(mc.player.posX)>>4,MathHelper.floor(mc.player.posZ)>>4);
        boolean covered=chunk==null || !chunk.canSeeSky(new BlockPos(mc.player.posX,
                mc.player.posY+mc.player.getEyeHeight(),mc.player.posZ));
        for (LaunchState launch:bridge.launches.values()) {
            if (launch.getAttack()!=AttackId.SUPERNOVA) continue;
            double age=world.getTotalWorldTime()-launch.getStartTick();
            if (age<0) continue;
            active.add(launch.getLaunchId());
            tracks.computeIfAbsent(launch.getLaunchId(),id->new Track()).update(age,covered||underwater,underwater);
        }
        tracks.entrySet().removeIf(entry->{
            if (active.contains(entry.getKey())) return false;
            entry.getValue().stop();return true;
        });
    }

    private void clear() { for (Track track:tracks.values()) track.stop();tracks.clear(); }

    private static final class Track {
        final CinematicSoundtrack audio=new CinematicSoundtrack();
        private final SupernovaAudioTiming.Clock clock=new SupernovaAudioTiming.Clock();
        private CinematicSoundtrack.LocalSound[] pressure,rumble,wind;
        private double shelter;
        private boolean initialized,finished;

        void update(double worldAge, boolean covered, boolean underwater) {
            if (finished) return;
            double age=clock.advance(worldAge);
            if (age>=SupernovaTiming.END) { stop();finished=true;return; }
            if (!initialized) { shelter=covered?1:0;initialized=true; }
            shelter+=((covered?1:0)-shelter)*0.16;
            audio.prune();
            if (clock.pressureDue()) pressure=pair("pressure_hit",false,3.2F);
            if (age>=SupernovaTiming.AFTERMATH) {
                if (rumble==null) rumble=pair("rumble_loop",true,0);
                if (age>=492 && wind==null) wind=pair("wind_loop",true,0);
                mix(pressure,0.74,underwater);
                mix(rumble,SupernovaAudioTiming.rumble(age),underwater);
                mix(wind,SupernovaAudioTiming.wind(age),underwater);
            }
        }

        private CinematicSoundtrack.LocalSound[] pair(String name, boolean loop, float seconds) {
            return new CinematicSoundtrack.LocalSound[]{
                audio.playEvent(new ResourceLocation("stellar","supernova."+name),loop,0.001F,1,seconds),
                audio.playEvent(new ResourceLocation("stellar","supernova."+name+"_muffled"),loop,0.001F,1,seconds)};
        }

        private void mix(CinematicSoundtrack.LocalSound[] pair, double gain, boolean underwater) {
            if (pair==null) return;
            pair[0].setMix((float)(gain*SupernovaAudioTiming.openness(shelter)),1);
            pair[1].setMix((float)(gain*SupernovaAudioTiming.muffled(shelter,underwater)),1);
        }

        void stop() {
            audio.stop();pressure=rumble=wind=null;initialized=false;
        }
    }
}
