package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackFamily;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.GravityInversionRules;
import fr.frabous.stellar.api.LaunchRequest;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
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
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Connected SS-05 checks on the real renderer. Screenshots are review artifacts, not beauty assertions. */
@Mod(modid="stellarinversionsmoke",name="Stellar Gravity Inversion Smoke",version="1",
        acceptableRemoteVersions="*",dependencies="after:stellar")
public final class GravityInversionClientSmokeMod {
    private static final int[] FRAMES = {10,40,70,84,120,160,196,205,225,240,250,270,290,302,306,310,
            316,317,318,319,321,324,330,340,356,375};
    private static final int SKIP_FRAMES = 4;
    private static final int[] OBSERVER_FRAMES = {120,170,210,228,252};
    private static final int ROW = AttackFamily.INVERSION.ordinal();
    private final File output = new File(System.getProperty("stellar.smoke.output","../build/smoke-inversion"));
    private final List<ISound> sounds = new ArrayList<>();
    private CompletableFuture<Void> screenshotWrites=CompletableFuture.completedFuture(null);
    private Object bridge, cinema, soundtrack, witness;
    private LaunchState launch;
    private Entity previousView;
    private GuiScreen menu;
    private int sequence, stage, frameIndex, observerFrame, glChecks, cleanCameraFrames, playingSources, witnessFrames;
    private long readyAt, castAt, skippedAt=-1, endedAt=-1, lastBloom;
    private boolean menuCaptured, baseline, cast, skipped, skipCaptured, cancelRequested, hideGUI, impactCaptured;
    private int thirdPerson;
    private float incomingFov, incomingRoll;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.inversion")) return;
        org.lwjgl.opengl.Display.setResizable(false);
        bridge=StellarServices.client();
        while (!(bridge instanceof LocalClientBridge)) bridge=field(bridge,"bridge");
        for (Object listener:((Map<?,?>)field(MinecraftForge.EVENT_BUS,"listeners")).keySet())
            if (listener.getClass().getSimpleName().equals("GravityInversionWitness")) witness=listener;
        require(witness!=null,"Witness shake is not registered");
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
            readyAt=now+80;
            if (sequence==0) {
                press(mc,"menu"); require(screen(mc,"SkillMenu"),"H did not open the menu");
                menu=mc.currentScreen; stage=-1;
            } else { StellarNetwork.requestSelection(AttackId.GRAVITY_INVERSION); stage=3; }
            return;
        }
        if (stage==-1) {
            require(now<readyAt,"Menu capture timeout");
            if (!menuCaptured || System.nanoTime()-((Number)field(menu,"openedAt")).longValue()<600_000_000L) return;
            require(RemoteMenuLayout.ROWS==5 && ROW==4,"SS-05 is not the fifth menu row");
            RemoteMenuLayout layout=(RemoteMenuLayout)field(menu,"layout");
            double[] point=layout.project(layout.cardsLeft()+110,RemoteMenuLayout.cardY(ROW)+RemoteMenuLayout.CARD_HEIGHT/2);
            call(menu,"mouseClicked",new Class<?>[]{int.class,int.class,int.class},
                    (int)Math.round(point[0]),(int)Math.round(point[1]),0);
            stage=-2; readyAt=now+80; return;
        }
        if (stage==-2) {
            require(now<readyAt,"Fifth-row selection did not finish its close animation");
            if (mc.currentScreen!=null) return;
            stage=1;
        }
        if (stage<4) {
            require(now<readyAt,"Selection acknowledgement timeout");
            AttackId selected=ItemStellarRemote.selected(LaunchController.remote(mc.player));
            if (stage==1 && selected==AttackId.GRAVITY_INVERSION) {
                log("MENU_SELECTED sequence=0 wireId=9 row="+ROW);
                StellarNetwork.requestSelection(AttackId.RAILGUN); stage=2;
            } else if (stage==2 && selected==AttackId.RAILGUN) {
                require(binding(mc,"gravity_inversion").getKeyCode()==Keyboard.KEY_G,"Gravity Inversion default key is not G");
                press(mc,"gravity_inversion"); stage=3;
            } else if (stage==3 && selected==AttackId.GRAVITY_INVERSION) {
                stage=4; aim(mc); readyAt=now+10;
                log("SELECT sequence="+sequence+" wireId=9 key="+(sequence==0?"G":"network"));
            }
            return;
        }
        if (!cast) {
            aim(mc);
            if (!baseline || now<readyAt) return;
            BlockPos hit=LaunchController.aimedBlock(mc.player,256);
            require(new BlockPos(256,3,256).equals(hit),"Server aim setup missed the test target: "+hit);
            StellarServices.client().preparePresentation();
            StellarNetwork.requestLaunch(new LaunchRequest(AttackId.GRAVITY_INVERSION,mc.player.dimension,new BlockTarget(256,3,256)));
            cast=true; castAt=now; log("CAST sequence="+sequence); return;
        }
        observe(mc);
        if (launch==null) {
            for (LaunchState state:launches().values()) if (state.getAttack()==AttackId.GRAVITY_INVERSION) launch=state;
            require(launch!=null || now-castAt<60,"No authoritative Gravity Inversion state");
            if (launch==null) return;
            require(launch.getTarget().equals(new BlockTarget(256,3,256)),"Server changed target");
            log("LAUNCH sequence="+sequence+" id="+launch.getLaunchId());
        }
        require(cinema!=null || now-castAt<12,"Own launch did not open GravityInversionCinema");
        if (cinema==null) return;
        double age=Math.max(0,now-launch.getStartTick());
        for (ISound sound:sounds) {
            boolean playing=mc.getSoundHandler().isSoundPlaying(sound);
            if (playing) playingSources++;
            if ((skipped && now-skippedAt>6) || (endedAt>=0 && now-endedAt>6))
                require(!playing,"Sound survived presentation close");
        }
        if (launches().containsKey(launch.getLaunchId())) {
            require(age<GravityInversionRules.END+20,"Launch did not finish at 380 ticks");
            if (!skipped && age<GravityInversionRules.END-1) require(mc.currentScreen==cinema,"Cinema closed early");
            if (sequence==1 && age>=90 && frameIndex==SKIP_FRAMES && !skipped) {
                press(mc,"skip"); // Exercise the actual GuiScreen J handler, not just the bridge helper.
                if (mc.currentScreen==cinema) call(cinema,"keyTyped",new Class<?>[]{char.class,int.class},'j',Keyboard.KEY_J);
                skipped=true; skippedAt=now; restored(mc);
                require(launches().containsKey(launch.getLaunchId()),"Skip removed the server launch");
                log("SKIP sequence=1 launchRetained=true age="+age);
            }
            if (skipped) {
                restored(mc);
                if (observerFrame<OBSERVER_FRAMES.length) observerAim(mc);
                if (skipCaptured && observerFrame==OBSERVER_FRAMES.length && !cancelRequested) {
                    cancelRequested=true; log("CANCEL_REQUEST sequence=1 age="+age);
                }
                require(age<GravityInversionRules.SLAM,"Cancellation acknowledgement timeout");
            }
            return;
        }
        restored(mc);
        if (endedAt<0) {
            require(sequence==0 ? age>=GravityInversionRules.END-4 && age<=GravityInversionRules.END+20
                    : skipped && cancelRequested,"Unexpected completion at "+age);
            endedAt=now; log((sequence==0?"NATURAL_END":"CANCELED")+" sequence="+sequence+" age="+age);
        }
        if (now-endedAt<60) return;
        require(launches().isEmpty() && ((Number)field(bridge,"awaitingUntil")).longValue()==-1,"Client state leaked");
        require(frameIndex==(sequence==0?FRAMES.length:SKIP_FRAMES),"Missing film captures");
        if (sequence==0) require(impactCaptured,"No capture landed inside the collapse impact frames");
        if (sequence==1) require(observerFrame==OBSERVER_FRAMES.length && witnessFrames>10,"Missing witness evidence");
        require(playingSources>0 && cleanCameraFrames>10 && glChecks>10,"Missing audio/render/cleanup evidence");
        capture(mc,"sequence-"+sequence+"-restored.png");
        log("FINISHED sequence="+sequence+" cameraRestored=true sounds=0 glChecks="+glChecks);
        sequence++;
        if (sequence==2) { screenshotWrites.join(); log("PASS"); mc.shutdown(); return; }
        stage=frameIndex=observerFrame=glChecks=cleanCameraFrames=playingSources=witnessFrames=0;
        menuCaptured=baseline=cast=skipped=skipCaptured=cancelRequested=impactCaptured=false;
        cinema=soundtrack=null; launch=null; menu=null; skippedAt=endedAt=-1; sounds.clear();
    }

    @SubscribeEvent public void sound(PlaySoundSourceEvent event) {
        ISound sound=event.getSound();
        if (!cast || !"stellar".equals(sound.getSoundLocation().getResourceDomain())) return;
        if (!sound.getSoundLocation().getResourcePath().startsWith("cinematic.")) return;
        require(!skipped && endedAt<0,"Cinematic audio restarted after close"); sounds.add(sound);
        log("AUDIO_SOURCE sequence="+sequence+" name="+sound.getSoundLocation());
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void incomingFov(EntityViewRenderEvent.FOVModifier event) {
        incomingFov=event.getFOV();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void outgoingFov(EntityViewRenderEvent.FOVModifier event) {
        if (cast && (skipped || endedAt>=0)) require(Math.abs(event.getFOV()-incomingFov)<0.0001,"FOV leaked");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void incomingCamera(EntityViewRenderEvent.CameraSetup event) {
        incomingRoll=event.getRoll();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void camera(EntityViewRenderEvent.CameraSetup event) {
        if (!cast || !(skipped || endedAt>=0)) return;
        require(event.getEntity()==previousView,"Camera view leaked");
        if (endedAt<0) {
            // A skipped caster becomes an ordinary witness: the ground shake must reach them.
            if (Math.abs(event.getRoll()-incomingRoll)>0.0001) witnessFrames++;
            return;
        }
        if (Minecraft.getMinecraft().world.getTotalWorldTime()-endedAt<2) return;
        require(Math.abs(event.getRoll()-incomingRoll)<0.0001,"Witness shake survived the launch");
        cleanCameraFrames++;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void render(TickEvent.RenderTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || sequence>=2 || stage==0) return;
        Minecraft mc=Minecraft.getMinecraft(); if (mc.world==null || mc.player==null) return;
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL render error"); glChecks++;
        require(mc.getRenderViewEntity()==previousView,"View leaked outside render frame");
        if (stage==-1 && !menuCaptured && System.nanoTime()-((Number)field(menu,"openedAt")).longValue()>=600_000_000L) {
            capture(mc,"inversion-skill-menu.png"); menuCaptured=true; return;
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
            require(age-requested<=6,"Missed witness tick "+requested);
            String name="sequence-1-witness-"+requested+"ticks.png"; capture(mc,name); observerFrame++;
            log("OBSERVER_FRAME sequence=1 file="+name+" requestedTicks="+requested+" actualTicks="+age+" cinema=false glError=0");
            return;
        }
        if (mc.currentScreen!=cinema || frameIndex>=(sequence==0?FRAMES.length:SKIP_FRAMES)) return;
        double age=((Number)call(cinema,"age",new Class<?>[]{float.class},event.renderTickTime)).doubleValue();
        int requested=FRAMES[frameIndex];
        if (age<requested) return;
        require(Double.isFinite(age) && age-requested<=6,"Missed capture tick "+requested+" actual="+age);
        try {
            Class<?> type=Class.forName("fr.frabous.stellar.client.EmissionBloom");
            require((Boolean)callStatic(type,"isReady"),"Bloom not ready");
            long passes=((Number)callStatic(type,"getCompletedPassCount")).longValue();
            require(passes>lastBloom,"Bloom stopped submitting"); lastBloom=passes;
        } catch (ClassNotFoundException exception) { throw new IllegalStateException(exception); }
        if (age>=GravityInversionRules.IMPACT && age<GravityInversionRules.IMPACT+5) impactCaptured=true;
        String name="sequence-"+sequence+"-inversion-"+requested+"ticks.png";
        capture(mc,name); frameIndex++;
        log("FRAME sequence="+sequence+" file="+name+" requestedTicks="+requested+" actualTicks="+age+" glError=0 bloomReady=true");
    }

    private void observe(Minecraft mc) {
        if (!screen(mc,"GravityInversionCinema")) return;
        if (cinema!=null) { require(cinema==mc.currentScreen,"Cinema replaced"); return; }
        cinema=mc.currentScreen; soundtrack=field(cinema,"soundtrack");
    }
    private void restored(Minecraft mc) {
        require(mc.currentScreen==null && mc.getRenderViewEntity()==previousView
                && mc.gameSettings.hideGUI==hideGUI && mc.gameSettings.thirdPersonView==thirdPerson,"Presentation not restored");
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
    /** Looks back across the field and up into the floating band, as a bystander would. */
    private void observerAim(Minecraft mc) {
        double dx=launch.getTarget().getX()+0.5-mc.player.posX-40;
        double dz=launch.getTarget().getZ()+0.5-mc.player.posZ;
        double dy=launch.getTarget().getY()+22-mc.player.posY-mc.player.getEyeHeight();
        mc.player.rotationYaw=mc.player.prevRotationYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        mc.player.rotationPitch=mc.player.prevRotationPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
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
    private static void log(String message) { System.out.println("STELLAR_INVERSION_SMOKE_"+message); }
    private static void require(boolean condition,String message) {
        if (!condition) { log("FAIL "+message); throw new IllegalStateException(message); }
    }
}
