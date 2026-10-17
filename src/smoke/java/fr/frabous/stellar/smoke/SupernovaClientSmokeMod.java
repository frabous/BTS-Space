package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.LaunchRequest;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.SupernovaTiming;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.client.LocalClientBridge;
import fr.frabous.stellar.client.RemoteMenuLayout;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Connected real-renderer checks. Screenshots require human review; they are not beauty assertions. */
@Mod(modid="stellarsupernovasmoke",name="Stellar Supernova Smoke",version="1",
        acceptableRemoteVersions="*",dependencies="after:stellar")
public final class SupernovaClientSmokeMod {
    private static final int[] FRAMES = {24,62,92,140,180,220,280,304,316,319,320,321,324,328,338,350,365,385,400,403,414,435,472,480,486,500,540,585,620};
    private static final int SKIP_FRAMES = 7;
    private static final int[] OBSERVER_FRAMES = {300,316,338,350,403,500,540,562,590};
    private static final int[] OBSERVER_MODES = {0,1,2,1,0,0,1,2,0};
    private final File output = new File(System.getProperty("stellar.smoke.output","../build/smoke-supernova"));
    private final List<ISound> sounds = new ArrayList<>();
    private final List<ISound> witnessSounds = new ArrayList<>();
    private final int[] playerRenders = new int[3];
    private CompletableFuture<Void> screenshotWrites=CompletableFuture.completedFuture(null);
    private Object bridge, cinema, soundtrack, witness;
    private LaunchState launch;
    private Entity previousView;
    private GuiScreen menu;
    private int sequence, stage, frameIndex, glChecks, cleanCameraFrames, playingSources;
    private long readyAt, castAt, skippedAt=-1, endedAt=-1, lastBloom;
    private boolean menuCaptured, baseline, cast, skipped, skipCaptured, cancelRequested, hideGUI;
    private int thirdPerson,expectedThirdPerson,witnessPlaying;
    private boolean shelterRequested,openRequested;
    private float incomingFov;
    private float incomingFogRed,incomingFogGreen,incomingFogBlue;
    private int terrainCapture,observerFrame;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.supernova")) return;
        org.lwjgl.opengl.Display.setResizable(false);
        bridge=StellarServices.client();
        while (!(bridge instanceof LocalClientBridge)) bridge=field(bridge,"bridge");
        for (Object listener:((Map<?,?>)field(MinecraftForge.EVENT_BUS,"listeners")).keySet())
            if (listener.getClass().getSimpleName().equals("SupernovaWitnessEffects")) witness=listener;
        require(witness!=null,"Witness controller is not registered");
        require(output.isDirectory(),"Missing runner artifact directory");
        MinecraftForge.EVENT_BUS.register(this);
        log("READY modes=natural,skip-and-cancel");
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void input(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.START || sequence>=2) return;
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        KeyBinding.unPressAllKeys();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent event) {
        if (sequence<2) event.setCanceled(true);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || sequence>=2) return;
        require(!screenshotWrites.isCompletedExceptionally(),"Screenshot encoding failed");
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.world==null || mc.player==null) return;
        long now=mc.world.getTotalWorldTime();
        mc.player.inventory.currentItem=0; mc.playerController.updateController();
        if (stage!=0) mc.setIngameNotInFocus();
        if (stage==0) {
            if (!new File(output,"ready-"+sequence).isFile() || LaunchController.remote(mc.player).isEmpty()) return;
            require(mc.player.isCreative() && launches().isEmpty() && LocalClientBridge.filmsEnabled,"Unclean test setup");
            mc.displayGuiScreen(null); mc.gameSettings.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.setIngameNotInFocus(); previousView=mc.getRenderViewEntity();
            hideGUI=mc.gameSettings.hideGUI; thirdPerson=mc.gameSettings.thirdPersonView;
            expectedThirdPerson=thirdPerson;
            readyAt=now+80;
            if (sequence==0) {
                press(mc,"menu"); require(screen(mc,"SkillMenu"),"H did not open the menu");
                menu=mc.currentScreen; stage=-1;
            } else { StellarNetwork.requestSelection(AttackId.SUPERNOVA); stage=3; }
            return;
        }
        if (stage==-1) {
            require(now<readyAt,"Menu capture timeout");
            if (!menuCaptured || System.nanoTime()-((Number)field(menu,"openedAt")).longValue()<600_000_000L) return;
            require(AttackId.values()[3]==AttackId.SUPERNOVA,"Supernova is not the fourth menu row");
            RemoteMenuLayout layout=(RemoteMenuLayout)field(menu,"layout");
            double[] point=layout.project(layout.cardsLeft()+110,RemoteMenuLayout.cardY(3)+23);
            call(menu,"mouseClicked",new Class<?>[]{int.class,int.class,int.class},
                    (int)Math.round(point[0]),(int)Math.round(point[1]),0);
            stage=-2; readyAt=now+80; return;
        }
        if (stage==-2) {
            require(now<readyAt,"Fourth-row selection did not finish its close animation");
            if (mc.currentScreen!=null) return;
            stage=1;
        }
        if (stage<4) {
            require(now<readyAt,"Selection acknowledgement timeout");
            AttackId selected=ItemStellarRemote.selected(LaunchController.remote(mc.player));
            if (stage==1 && selected==AttackId.SUPERNOVA) {
                log("MENU_SELECTED sequence=0 wireId=6 row=3");
                StellarNetwork.requestSelection(AttackId.RAILGUN); stage=2;
            } else if (stage==2 && selected==AttackId.RAILGUN) {
                require(binding(mc,"supernova").getKeyCode()==Keyboard.KEY_N,"Supernova default key is not N");
                press(mc,"supernova"); stage=3;
            } else if (stage==3 && selected==AttackId.SUPERNOVA) {
                stage=4; aim(mc); readyAt=now+10;
                log("SELECT sequence="+sequence+" wireId=6 key="+(sequence==0?"N":"network"));
            }
            return;
        }
        if (!cast) {
            aim(mc);
            if (!baseline || now<readyAt) return;
            BlockPos hit=LaunchController.aimedBlock(mc.player,256);
            require(new BlockPos(256,3,256).equals(hit),"Server aim setup missed the test target: "+hit);
            StellarServices.client().preparePresentation();
            StellarNetwork.requestLaunch(new LaunchRequest(AttackId.SUPERNOVA,mc.player.dimension,new BlockTarget(256,3,256)));
            cast=true; castAt=now; log("CAST sequence="+sequence); return;
        }
        observe(mc);
        if (launch==null) {
            for (LaunchState state:launches().values()) if (state.getAttack()==AttackId.SUPERNOVA) launch=state;
            require(launch!=null || now-castAt<60,"No authoritative Supernova state");
            if (launch==null) return;
            require(launch.getTarget().equals(new BlockTarget(256,3,256)),"Server changed target");
            log("LAUNCH sequence="+sequence+" id="+launch.getLaunchId());
        }
        require(cinema!=null || now-castAt<12,"Own launch did not open SupernovaCinema");
        if (cinema==null) return;
        double age=Math.max(0,now-launch.getStartTick());
        for (ISound sound:sounds) {
            boolean playing=mc.getSoundHandler().isSoundPlaying(sound);
            if (playing) playingSources++;
            if ((skipped && now-skippedAt>6) || (endedAt>=0 && now-endedAt>6))
                require(!playing,"Sound survived presentation close");
        }
        for (ISound sound:witnessSounds) {
            boolean playing=mc.getSoundHandler().isSoundPlaying(sound);
            if (playing) witnessPlaying++;
            if (endedAt>=0 && now-endedAt>6) require(!playing,"Environmental audio survived launch completion");
        }
        if (launches().containsKey(launch.getLaunchId())) {
            require(age<SupernovaTiming.END+20,"Launch did not finish at 640 ticks");
            if (!skipped && age<639) require(mc.currentScreen==cinema,"Cinema closed early");
            if (sequence==1 && age>=284 && frameIndex==SKIP_FRAMES && !skipped) {
                press(mc,"skip"); // Exercise the actual GuiScreen J handler, not just the bridge helper.
                if (mc.currentScreen==cinema) call(cinema,"keyTyped",new Class<?>[]{char.class,int.class},'j',Keyboard.KEY_J);
                skipped=true; skippedAt=now; restored(mc);
                require(launches().containsKey(launch.getLaunchId()),"Skip removed the server launch");
                log("SKIP sequence=1 launchRetained=true age="+age);
            }
            if (skipped) {
                if (!cancelRequested && observerFrame<OBSERVER_FRAMES.length) {
                    // Test-controlled native F5 modes. Production must never overwrite this choice.
                    expectedThirdPerson=OBSERVER_MODES[observerFrame];
                    mc.gameSettings.thirdPersonView=expectedThirdPerson;
                    observerAim(mc,expectedThirdPerson==2);
                }
                restored(mc);
                if (age>=510 && !shelterRequested) {
                    shelterRequested=true;log("SHELTER_REQUEST sequence=1 state=closed");
                }
                if (age>=566 && !openRequested) {
                    openRequested=true;log("SHELTER_REQUEST sequence=1 state=open");
                }
                if (skipCaptured && age>=600 && observerFrame==OBSERVER_FRAMES.length && !cancelRequested) {
                    expectedThirdPerson=thirdPerson;mc.gameSettings.thirdPersonView=thirdPerson;
                    cancelRequested=true; log("CANCEL_REQUEST sequence=1");
                }
                require(age<625,"Cancellation acknowledgement timeout");
            }
            return;
        }
        restored(mc);
        if (endedAt<0) {
            require(sequence==0 ? age>=636 && age<=660 : skipped && cancelRequested,"Unexpected completion");
            endedAt=now; log((sequence==0?"NATURAL_END":"CANCELED")+" sequence="+sequence+" age="+age);
        }
        if (now-endedAt<60) return;
        require(launches().isEmpty() && ((Number)field(bridge,"awaitingUntil")).longValue()==-1,"Client state leaked");
        require(((Map<?,?>)field(witness,"tracks")).isEmpty(),"Environmental tracks leaked");
        require(witnessPlaying>0 && witnessSounds.size()==6,"Missing or duplicated witness audio sources: "+witnessSounds.size());
        require(frameIndex==(sequence==0?FRAMES.length:SKIP_FRAMES),"Missing film captures");
        if (sequence==1) require(observerFrame==OBSERVER_FRAMES.length && playerRenders[1]>0 && playerRenders[2]>0
                && shelterRequested && openRequested,"Missing distant F5/shelter evidence");
        require(playingSources>0 && cleanCameraFrames>10 && glChecks>10,"Missing audio/render/cleanup evidence");
        capture(mc,"sequence-"+sequence+"-restored.png");
        log("FINISHED sequence="+sequence+" cameraRestored=true effects=0 sounds=0 witnessSources="+witnessSounds.size()+" glChecks="+glChecks);
        sequence++;
        if (sequence==2) { screenshotWrites.join(); log("PASS"); mc.shutdown(); return; }
        stage=frameIndex=glChecks=cleanCameraFrames=playingSources=0;
        menuCaptured=baseline=cast=skipped=skipCaptured=cancelRequested=false;
        cinema=soundtrack=null; launch=null; menu=null; skippedAt=endedAt=-1; sounds.clear();witnessSounds.clear();
        observerFrame=witnessPlaying=0;shelterRequested=openRequested=false;
        java.util.Arrays.fill(playerRenders,0);
    }

    @SubscribeEvent public void sound(PlaySoundSourceEvent event) {
        ISound sound=event.getSound();
        if (!cast || !"stellar".equals(sound.getSoundLocation().getResourceDomain())) return;
        if (sound.getSoundLocation().getResourcePath().startsWith("supernova.")) {
            double age=Minecraft.getMinecraft().world.getTotalWorldTime()-launch.getStartTick();
            require(age>=480 && endedAt<0,"Physical witness sound started before pressure arrival or after completion");
            for (ISound existing:witnessSounds) require(!existing.getSoundLocation().equals(sound.getSoundLocation()),
                    "Witness cue duplicated: "+sound.getSoundLocation()+" at "+age);
            log("WITNESS_SOUND sequence="+sequence+" event="+sound.getSoundLocation()+" age="+age);
            witnessSounds.add(sound);return;
        }
        if (!sound.getSoundLocation().getResourcePath().startsWith("cinematic.")) return;
        require(!skipped && endedAt<0,"Cinematic audio restarted after close"); sounds.add(sound);
    }

    @SubscribeEvent public void playerRendered(RenderPlayerEvent.Post event) {
        Minecraft mc=Minecraft.getMinecraft();
        if (skipped && event.getEntityPlayer()==mc.player && mc.gameSettings.thirdPersonView>0)
            playerRenders[mc.gameSettings.thirdPersonView]++;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void incomingFov(EntityViewRenderEvent.FOVModifier event) {
        incomingFov=event.getFOV();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void outgoingFov(EntityViewRenderEvent.FOVModifier event) {
        if (cast && (skipped || endedAt>=0)) require(Math.abs(event.getFOV()-incomingFov)<0.0001,"FOV leaked");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void incomingFog(EntityViewRenderEvent.FogColors event) {
        incomingFogRed=event.getRed();incomingFogGreen=event.getGreen();incomingFogBlue=event.getBlue();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void outgoingFog(EntityViewRenderEvent.FogColors event) {
        if (cast && (skipped || endedAt>=0)) require(Math.abs(event.getRed()-incomingFogRed)<0.0001
                && Math.abs(event.getGreen()-incomingFogGreen)<0.0001 && Math.abs(event.getBlue()-incomingFogBlue)<0.0001,"Fog color leaked");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void camera(EntityViewRenderEvent.CameraSetup event) {
        if (!cast || !(skipped || endedAt>=0)) return;
        require(event.getEntity()==previousView && Math.abs(event.getRoll())<0.0001,"Camera roll/view leaked");
        cleanCameraFrames++;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void terrain(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if (sequence!=0 || launch==null || cinema!=mc.currentScreen || mc.world==null || terrainCapture>=2) return;
        double age=mc.world.getTotalWorldTime()-launch.getStartTick()+event.getPartialTicks();
        if (age<(terrainCapture==0 ? 435 : 620)) return;
        int loaded=0,minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        int tx=launch.getTarget().getX()>>4,tz=launch.getTarget().getZ()>>4;
        for (int x=tx-16;x<=tx+16;x++) for (int z=tz-16;z<=tz+16;z++) {
            net.minecraft.world.chunk.Chunk chunk=mc.world.getChunkProvider().getLoadedChunk(x,z);
            if (chunk==null || chunk.isEmpty()) continue;
            loaded++; minX=Math.min(minX,x*16);minZ=Math.min(minZ,z*16);
            maxX=Math.max(maxX,x*16+15);maxZ=Math.max(maxZ,z*16+15);
        }
        try {
            Field infos=ReflectionHelper.findField(RenderGlobal.class,new String[]{"renderInfos","field_72755_R"});
            int compiled=0;
            for (Object info:(Iterable<?>)infos.get(mc.renderGlobal)) for (Field f:info.getClass().getDeclaredFields()) {
                if (f.getType()!=RenderChunk.class) continue;
                f.setAccessible(true);RenderChunk chunk=(RenderChunk)f.get(info);
                if (!chunk.getCompiledChunk().isEmpty()) compiled++;
            }
            Entity view=mc.getRenderViewEntity();
            log("TERRAIN sequence=0 age="+age+" loaded="+loaded+" bounds="+minX+","+minZ+","+maxX+","+maxZ
                    +" compiledVisible="+compiled+" clientDistance="+mc.gameSettings.renderDistanceChunks
                    +" view="+view.posX+","+view.posY+","+view.posZ+" pitch="+view.rotationPitch);
            terrainCapture++;
        } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void render(TickEvent.RenderTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || sequence>=2 || stage==0) return;
        Minecraft mc=Minecraft.getMinecraft(); if (mc.world==null || mc.player==null) return;
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL render error"); glChecks++;
        require(mc.getRenderViewEntity()==previousView,"View leaked outside render frame");
        if (stage==-1 && !menuCaptured && System.nanoTime()-((Number)field(menu,"openedAt")).longValue()>=600_000_000L) {
            capture(mc,"supernova-skill-menu.png"); menuCaptured=true; return;
        }
        if (stage==4 && !cast && !baseline) { capture(mc,"sequence-"+sequence+"-baseline.png"); baseline=true; return; }
        observe(mc); if (cinema==null || launch==null) return;
        if (skipped && !skipCaptured) {
            restored(mc); capture(mc,"sequence-1-post-skip.png"); skipCaptured=true; return;
        }
        if (skipped) {
            restored(mc);
            if (endedAt>=0 || observerFrame>=OBSERVER_FRAMES.length) return;
            double age=mc.world.getTotalWorldTime()-launch.getStartTick()+event.renderTickTime;
            int requested=OBSERVER_FRAMES[observerFrame];
            if (age<requested) return;
            require(age-requested<=6,"Missed distant observer tick "+requested);
            double distance=Math.hypot(mc.player.posX-launch.getTarget().getX(),mc.player.posZ-launch.getTarget().getZ());
            require(distance>4000,"Observer was not beyond the stellar origin's far plane");
            require(mc.gameSettings.thirdPersonView==OBSERVER_MODES[observerFrame],"Witness perspective was forced");
            int mode=OBSERVER_MODES[observerFrame];
            if (mode>0) require(playerRenders[mode]>0,"Local player model was not rendered in native F5 mode "+mode);
            Object track=((Map<?,?>)field(witness,"tracks")).get(launch.getLaunchId());
            require(track!=null,"Witness track missing after local skip");
            double shelter=((Number)field(track,"shelter")).doubleValue();
            if (requested<480) require(witnessSounds.isEmpty(),"A light flash produced an early pressure sound");
            if (requested==500 || requested==590) require(shelter<0.05,"Open-air mix stayed muffled");
            if (requested==540 || requested==562) require(shelter>0.97,"Roof did not muffle the witness mix");
            double open=0,muffled=0;
            for (ISound sound:witnessSounds) if (mc.getSoundHandler().isSoundPlaying(sound)) {
                require(Math.abs(sound.getPitch()-1)<0.0001,"Shelter filtering was replaced by a pitch shift");
                if (sound.getSoundLocation().getResourcePath().endsWith("_muffled")) muffled+=sound.getVolume();
                else open+=sound.getVolume();
            }
            if (requested==500 || requested==590) require(open>0.1 && open>5*muffled,"Open-air assets were not audible");
            if (requested==540 || requested==562) require(muffled>0.04 && open<muffled*0.15,"Filtered assets were not crossfaded under the roof");
            String name="sequence-1-observer-"+requested+"ticks.png";capture(mc,name);observerFrame++;
            log("OBSERVER_FRAME sequence=1 file="+name+" requestedTicks="+requested+" actualTicks="+age
                    +" distance="+distance+" cinema=false perspective="+mode+" playerModel="+(mode>0)
                    +" shelter="+shelter+" openVolume="+open+" muffledVolume="+muffled+" glError=0");
            return;
        }
        if (mc.currentScreen!=cinema || frameIndex>=(sequence==0?FRAMES.length:SKIP_FRAMES)) return;
        double age=((Number)field(cinema,"renderedAge")).doubleValue(); int requested=FRAMES[frameIndex];
        if (age<requested) return;
        require(Double.isFinite(age) && age-requested<=6,"Missed capture tick "+requested+" actual="+age);
        try {
            Class<?> type=Class.forName("fr.frabous.stellar.client.EmissionBloom");
            require((Boolean)callStatic(type,"isReady"),"Bloom not ready");
            long passes=((Number)callStatic(type,"getCompletedPassCount")).longValue();
            require(passes>lastBloom,"Bloom stopped submitting"); lastBloom=passes;
        } catch (ClassNotFoundException exception) { throw new IllegalStateException(exception); }
        String name="sequence-"+sequence+"-supernova-"+requested+"ticks.png";
        capture(mc,name); frameIndex++;
        log("FRAME sequence="+sequence+" file="+name+" requestedTicks="+requested+" actualTicks="+age+" glError=0 bloomReady=true");
    }

    private void observe(Minecraft mc) {
        if (!screen(mc,"SupernovaCinema")) return;
        if (cinema!=null) { require(cinema==mc.currentScreen,"Cinema replaced"); return; }
        cinema=mc.currentScreen; soundtrack=field(cinema,"soundtrack");
    }
    private void restored(Minecraft mc) {
        require(mc.currentScreen==null && mc.getRenderViewEntity()==previousView
                && mc.gameSettings.hideGUI==hideGUI && mc.gameSettings.thirdPersonView==expectedThirdPerson,"Presentation not restored");
        require(((List<?>)field(field(soundtrack,"audio"),"owned")).isEmpty(),"Sounds still owned");
    }
    private void capture(Minecraft mc,String name) {
        BufferedImage image=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        File directory=new File(output,"screenshots");
        require(directory.isDirectory() || directory.mkdirs(),"Cannot create screenshot directory");
        // Keep PNG compression off the render thread so consecutive impact ticks can actually be sampled.
        screenshotWrites=screenshotWrites.thenRunAsync(() -> {
            try {
                if (!ImageIO.write(image,"png",new File(directory,name))) throw new IOException("PNG writer unavailable");
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
    }
    private static void aim(Minecraft mc) {
        double dx=256.5-mc.player.posX,dy=4-mc.player.posY-mc.player.getEyeHeight(),dz=256.5-mc.player.posZ;
        mc.player.rotationYaw=mc.player.prevRotationYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        mc.player.rotationPitch=mc.player.prevRotationPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        mc.player.rotationYawHead=mc.player.prevRotationYawHead=mc.player.rotationYaw;
    }
    private void observerAim(Minecraft mc,boolean front) {
        double dx=launch.getTarget().getX()+0.5-mc.player.posX;
        double dz=launch.getTarget().getZ()+0.5-mc.player.posZ;
        double dy=launch.getTarget().getY()+1.1+640-mc.player.posY-mc.player.getEyeHeight();
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        // Compose the test shot above the native F5 player's head instead of hiding the distant star behind it.
        if (mc.gameSettings.thirdPersonView>0) pitch+=9;
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw+(front?180:0);
        mc.player.rotationPitch=mc.player.prevRotationPitch=front?-pitch:pitch;
        mc.player.rotationYawHead=mc.player.prevRotationYawHead=mc.player.rotationYaw;
    }
    private static boolean screen(Minecraft mc,String name) {
        return mc.currentScreen!=null && name.equals(mc.currentScreen.getClass().getSimpleName());
    }
    private static KeyBinding binding(Minecraft mc,String name) {
        for (KeyBinding key:mc.gameSettings.keyBindings) if (("key.stellar."+name).equals(key.getKeyDescription())) return key;
        throw new IllegalStateException("Missing key "+name);
    }
    private static void press(Minecraft mc,String name) {
        KeyBinding.onTick(binding(mc,name).getKeyCode()); MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
    }
    @SuppressWarnings("unchecked") private Map<UUID,LaunchState> launches() { return (Map<UUID,LaunchState>)field(bridge,"launches"); }
    private static Object field(Object owner,String name) {
        try { Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object call(Object owner,String name,Class<?>[] parameters,Object... args) {
        try { Method m=owner.getClass().getDeclaredMethod(name,parameters); m.setAccessible(true); return m.invoke(owner,args); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object callStatic(Class<?> type,String name) {
        try { Method m=type.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(null); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static void log(String message) { System.out.println("STELLAR_SUPERNOVA_SMOKE_"+message); }
    private static void require(boolean condition,String message) {
        if (!condition) { log("FAIL "+message); throw new IllegalStateException(message); }
    }
}
