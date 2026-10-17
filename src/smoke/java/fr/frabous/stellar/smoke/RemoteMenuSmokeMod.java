package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.client.RemoteMenuLayout;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Real client/server, GUI input, sound-engine and framebuffer checks. Never packaged in the mod. */
@Mod(modid="stellarremotesmoke", name="Stellar Remote Menu Smoke", version="1", acceptableRemoteVersions="*",
        dependencies="after:stellar")
public final class RemoteMenuSmokeMod {
    private final File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke-remote"));
    private final List<ISound> sounds = new CopyOnWriteArrayList<>();
    private final Set<String> cues = ConcurrentHashMap.newKeySet();
    private final AtomicInteger hoverCues = new AtomicInteger(), openCues = new AtomicInteger();
    private final FloatBuffer beforeProjection = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer beforeModelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer afterMatrix = BufferUtils.createFloatBuffer(16);
    private GuiScreen menu;
    private Entity view;
    private boolean hideGUI;
    private float fov;
    private int thirdPerson, originalScale, stage, attackIndex, stableHoverCues, stableOpenCues, glChecks, captures;
    private long notBefore, started, worldTime, menuOpenedAt;
    private AttackId selectedBefore;
    private String pendingCapture;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.remote")) return;
        require(output.isDirectory(), "Missing artifact directory");
        MinecraftForge.EVENT_BUS.register(this);
        log("READY");
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void isolate(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        KeyBinding.unPressAllKeys();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent event) { event.setCanceled(true); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (stage == 0) {
            if (!new File(output, "ready").isFile() || LaunchController.remote(mc.player).isEmpty()) return;
            mc.gameSettings.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            view = mc.getRenderViewEntity(); thirdPerson = mc.gameSettings.thirdPersonView;
            hideGUI = mc.gameSettings.hideGUI; fov = mc.gameSettings.fovSetting; originalScale = mc.gameSettings.guiScale;
            started = System.nanoTime(); worldTime = mc.world.getTotalWorldTime(); selectedBefore = selected(mc);
            open(mc); advance(1, 90); return;
        }
        require(System.nanoTime() - started < 75_000_000_000L, "Menu test timeout at stage " + stage);
        if (stage < 26) require(mc.getRenderViewEntity() == view && mc.gameSettings.thirdPersonView == thirdPerson
                && mc.gameSettings.hideGUI == hideGUI && mc.gameSettings.fovSetting == fov, "Menu changed camera or HUD settings");
        if (mc.currentScreen == null) mc.setIngameNotInFocus();
        if (System.nanoTime() < notBefore || pendingCapture != null) return;
        switch (stage) {
            case 1:
                capture("remote-" + attackIndex + "-opening.png"); advance(2, 550); break;
            case 2:
                pointAt(mc, cards(), RemoteMenuLayout.cardY(attackIndex) + 23);
                advance(3, 220); break;
            case 3:
                require(field(menu, "preview") == AttackId.values()[attackIndex], "Hover preview did not follow row " + attackIndex);
                stableHoverCues = hoverCues.get();
                capture("remote-" + attackIndex + "-hover.png"); advance(4, 180); break;
            case 4:
                require(stableHoverCues == hoverCues.get(), "Stationary pointer repeated hover audio");
                clickLocal(cards(), RemoteMenuLayout.cardY(attackIndex) + 23, 1);
                require(Boolean.TRUE.equals(field(menu, "details")), "Right click did not open briefing");
                require(selected(mc) == selectedBefore, "Hover or right click changed authoritative selection");
                capture("remote-" + attackIndex + "-briefing.png"); advance(5, 180); break;
            case 5:
                key(Keyboard.KEY_ESCAPE);
                require(mc.currentScreen == menu && Boolean.FALSE.equals(field(menu, "details")), "Escape did not return from briefing");
                double briefing = 174 + layout().panelShift();
                clickLocal(briefing, 99, 0); require(Boolean.TRUE.equals(field(menu, "details")), "Briefing button did not open");
                clickLocal(briefing, 99, 0); require(Boolean.FALSE.equals(field(menu, "details")), "Briefing button did not close");
                clickLocal(cards(), -55, 0);
                require(((Number) field(menu, "closingAt")).longValue() == 0, "Gap click selected a strike");
                clickLocal(cards(), RemoteMenuLayout.cardY(attackIndex) + 23, 0);
                clickLocal(cards(), RemoteMenuLayout.cardY((attackIndex + 1) % 4) + 23, 0);
                require(field(menu, "linked") == AttackId.values()[attackIndex], "Double click changed pending selection");
                advance(6, 450); break;
            case 6:
                closed(mc);
                require(selected(mc) == AttackId.values()[attackIndex], "Server did not acknowledge menu selection");
                selectedBefore = selected(mc);
                log("SELECT row=" + attackIndex + " wireId=" + selectedBefore.getWireId());
                if (++attackIndex < 4) { open(mc); advance(1, 90); }
                else { open(mc); advance(7, 650); }
                break;
            case 7:
                stableOpenCues = openCues.get(); menuOpenedAt = ((Number) field(menu, "openedAt")).longValue();
                pointAt(mc, 0, -140); // Keep resize from legitimately focusing the card under the pointer.
                scale(mc, 1); capture("remote-scale-1.png"); advance(8, 180); break;
            case 8:
                scale(mc, 3); capture("remote-scale-3.png"); advance(9, 180); break;
            case 9:
                scale(mc, 2);
                require(openCues.get() == stableOpenCues && ((Number) field(menu, "openedAt")).longValue() == menuOpenedAt,
                        "GUI resize restarted opening audio or animation");
                int focused = ((Number) field(menu, "focused")).intValue();
                for (int i = 0; i < (3 - focused + 4) % 4; i++) key(Keyboard.KEY_DOWN);
                key(Keyboard.KEY_DOWN); require(field(menu, "preview") == AttackId.RAILGUN, "Keyboard wrap did not reach first row");
                key(Keyboard.KEY_UP); require(field(menu, "preview") == AttackId.SUPERNOVA, "Keyboard wrap did not reach fourth row");
                require(thumbPressing("UP"), "Arrow key did not move the remote thumb");
                advance(91, 190); break;
            case 91:
                capture("remote-menu-dpad-press.png"); advance(92, 80); break;
            case 92:
                key(Keyboard.KEY_RETURN); key(Keyboard.KEY_RETURN); advance(10, 400); break;
            case 10:
                closed(mc); require(selected(mc) == AttackId.SUPERNOVA, "Enter selected the wrong attack");
                open(mc); advance(11, 650); break;
            case 11:
                key(binding(mc, "menu").getKeyCode()); advance(12, 400); break;
            case 12:
                closed(mc); open(mc); advance(13, 650); break;
            case 13:
                clickLocal(306, -160, 0); advance(14, 400); break;
            case 14:
                closed(mc); open(mc); advance(15, 650); break;
            case 15:
                key(Keyboard.KEY_ESCAPE); advance(16, 400); break;
            case 16:
                closed(mc); open(mc); advance(17, 100); break;
            case 17:
                mc.displayGuiScreen(null); advance(18, 150); break;
            case 18:
                closed(mc);
                require(mc.world.getTotalWorldTime() > worldTime + 60, "GUI paused the live world");
                require(selected(mc) == selectedBefore, "Closing menu changed selection");
                for (String cue : new String[] {"open", "hover", "select", "details", "close"})
                    require(cues.contains("remote." + cue), "Sound engine did not play remote." + cue);
                require(glChecks > 20, "Not enough real GUI frames checked");
                mc.gameSettings.guiScale = originalScale;
                capture("remote-world-restored.png"); advance(19, 400); break;
            case 19:
                capture("remote-fp-idle.png"); advance(20, 250); break;
            case 20:
                pressBinding(mc, "railgun"); advance(21, 190); break;
            case 21:
                require(thumbPressing("RAILGUN"), "K did not press the railgun key in first person");
                capture("remote-fp-press-railgun.png"); advance(22, 750); break;
            case 22:
                pressBinding(mc, "supernova"); advance(23, 190); break;
            case 23:
                require(thumbPressing("SUPERNOVA"), "N did not press the supernova key in first person");
                capture("remote-fp-press-supernova.png"); advance(24, 750); break;
            case 24:
                pressRemote("FIRE"); advance(25, 190); break;
            case 25:
                require(thumbPressing("FIRE"), "Fire key did not animate");
                capture("remote-fp-press-fire.png"); advance(26, 700); break;
            case 26:
                mc.gameSettings.thirdPersonView = 2; mc.gameSettings.fovSetting = 30; advance(27, 450); break;
            case 27:
                capture("remote-third-front.png"); advance(28, 150); break;
            case 28:
                mc.gameSettings.thirdPersonView = 1; advance(29, 450); break;
            case 29:
                capture("remote-third-back.png"); advance(32, 150); break;
            case 32:
                mc.player.rotationYaw += 90; mc.player.prevRotationYaw = mc.player.rotationYaw; advance(33, 450); break;
            case 33:
                capture("remote-third-side.png"); advance(30, 150); break;
            case 30:
                mc.player.rotationYaw -= 90; mc.player.prevRotationYaw = mc.player.rotationYaw;
                mc.gameSettings.thirdPersonView = thirdPerson; mc.gameSettings.fovSetting = fov;
                require(selected(mc) == AttackId.SUPERNOVA, "Server did not acknowledge the first-person N press");
                log("PASS selections=4 keyboard=true resize=true hoverDebounced=true sounds=" + sounds.size()
                        + " glFrames=" + glChecks + " captures=" + captures + " cameraUnchanged=true worldRunning=true thumbPresses=true");
                mc.shutdown(); stage = 34; break;
            default: break;
        }
    }

    private void open(Minecraft mc) {
        KeyBinding.onTick(binding(mc, "menu").getKeyCode());
        MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
        menu = mc.currentScreen;
        require(menu != null && menu.getClass().getSimpleName().equals("SkillMenu"), "Menu binding did not open remote");
        require(!menu.doesGuiPauseGame(), "Remote must not pause singleplayer");
        require(field(menu, "linked") == selected(mc), "Reopened menu lost the server selection");
    }

    private void scale(Minecraft mc, int value) {
        mc.gameSettings.guiScale = value;
        ScaledResolution resolution = new ScaledResolution(mc);
        menu.setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private RemoteMenuLayout layout() { return (RemoteMenuLayout) field(menu, "layout"); }

    /** Inside the card column, whichever hand holds the remote. */
    private double cards() { return layout().cardsLeft() + 110; }

    private void pointAt(Minecraft mc, double x, double y) {
        double[] point = layout().project(x, y);
        Mouse.setCursorPosition((int) Math.round(point[0] * mc.displayWidth / menu.width),
                mc.displayHeight - 1 - (int) Math.round(point[1] * mc.displayHeight / menu.height));
    }

    private void clickLocal(double x, double y, int button) {
        double[] point = layout().project(x, y);
        call("mouseClicked", new Class<?>[] {int.class, int.class, int.class},
                (int) Math.round(point[0]), (int) Math.round(point[1]), button);
    }

    private void key(int code) { call("keyTyped", new Class<?>[] {char.class, int.class}, '\0', code); }

    private static void pressBinding(Minecraft mc, String name) {
        KeyBinding.onTick(binding(mc, name).getKeyCode());
        MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
    }

    /** Thumb choreography of the shared first-person remote, read without widening the client API. */
    private static Object thumb() {
        try {
            Field field = Class.forName("fr.frabous.stellar.client.RemoteFirstPerson").getDeclaredField("THUMB");
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static Object remoteKey(String name) {
        try {
            for (Object key : Class.forName("fr.frabous.stellar.client.RemoteKey").getEnumConstants())
                if (((Enum<?>) key).name().equals(name)) return key;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        throw new IllegalStateException("Missing remote key " + name);
    }

    private static boolean thumbPressing(String name) {
        Object thumb = thumb(), key = remoteKey(name);
        try {
            Method pressed = thumb.getClass().getDeclaredMethod("pressed");
            pressed.setAccessible(true);
            return pressed.invoke(thumb) == key;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void pressRemote(String name) {
        try {
            Method press = Class.forName("fr.frabous.stellar.client.RemoteFirstPerson")
                    .getDeclaredMethod("press", Class.forName("fr.frabous.stellar.client.RemoteKey"));
            press.setAccessible(true);
            press.invoke(null, remoteKey(name));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private void call(String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = menu.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(menu, arguments);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void closed(Minecraft mc) {
        require(mc.currentScreen == null, "Remote failed to close at stage " + stage);
        require(((List<?>) field(field(menu, "audio"), "owned")).isEmpty(), "Menu retained audio ownership after closing");
        for (ISound sound : sounds) require(!mc.getSoundHandler().isSoundPlaying(sound), "UI sound survived menu close");
    }

    private static AttackId selected(Minecraft mc) { return ItemStellarRemote.selected(LaunchController.remote(mc.player)); }
    private static KeyBinding binding(Minecraft mc, String name) {
        for (KeyBinding key : mc.gameSettings.keyBindings) if (key.getKeyDescription().equals("key.stellar." + name)) return key;
        throw new IllegalStateException("Missing binding " + name);
    }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private void advance(int next, int milliseconds) { stage = next; notBefore = System.nanoTime() + milliseconds * 1_000_000L; }
    private void capture(String name) { pendingCapture = name; }
    private static void log(String message) { System.out.println("STELLAR_REMOTE_SMOKE_" + message); }
    private static void require(boolean value, String message) {
        if (!value) { log("FAIL " + message); throw new IllegalStateException(message); }
    }

    @SubscribeEvent public void sound(PlaySoundSourceEvent event) {
        ISound sound = event.getSound();
        if (!sound.getSoundLocation().getResourceDomain().equals("stellar")) return;
        String name = sound.getSoundLocation().getResourcePath();
        if (!name.startsWith("remote.")) return;
        sounds.add(sound); cues.add(name);
        if (name.equals("remote.hover")) hoverCues.incrementAndGet();
        if (name.equals("remote.open")) openCues.incrementAndGet();
        log("SOUND event=" + name);
    }

    @SubscribeEvent public void beforeGui(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.getGui() != menu) return;
        beforeProjection.clear(); beforeModelview.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, beforeProjection);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, beforeModelview);
    }

    @SubscribeEvent public void afterGui(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() != menu) return;
        compareMatrix(GL11.GL_PROJECTION_MATRIX, beforeProjection);
        compareMatrix(GL11.GL_MODELVIEW_MATRIX, beforeModelview);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GUI produced an OpenGL error");
        glChecks++;
    }

    private void compareMatrix(int mode, FloatBuffer before) {
        afterMatrix.clear(); GL11.glGetFloat(mode, afterMatrix);
        for (int i = 0; i < 16; i++) require(Math.abs(before.get(i) - afterMatrix.get(i)) < 0.0001F, "GUI leaked matrix " + mode);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingCapture == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(output, pendingCapture, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        log("FRAME file=" + pendingCapture + " width=" + mc.displayWidth + " height=" + mc.displayHeight);
        captures++; pendingCapture = null;
    }
}
