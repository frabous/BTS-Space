package fr.frabous.stellar.smoke;

import fr.frabous.stellar.api.*;
import fr.frabous.stellar.api.network.*;
import fr.frabous.stellar.client.LocalClientBridge;
import fr.frabous.stellar.common.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Connected gameplay driver. Isolated by smoke-carrier.py, never shipped in the production jar. */
@Mod(modid="stellarcarriersmoke", name="Stellar Carrier Gunnery Smoke", version="1",
        acceptableRemoteVersions="*", dependencies="after:stellar")
public final class CarrierGunnerySmokeMod {
    private final File output = new File(System.getProperty("stellar.smoke.output", "../build/smoke-carrier"));
    private Object bridge;
    private LaunchState launch;
    private int sequence, stage, glFrames, captureIndex;
    private final Set<Long> confirmed = new HashSet<>();
    private final Set<Integer> batteries = new HashSet<>();
    private long deadline, nextCastAfter;
    private boolean exited, boarded, firing, zoom, naturalReturned;
    private float incomingFov;
    private String capture;
    private final int[] frames = {30, 70, 90, 104, 120, 134, 142, 150, 306, 314, 322, 330, 660, 710};

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("stellar.smoke.carrier")) return;
        Display.setResizable(false);
        bridge = StellarServices.client();
        // The test instance must not read physical mouse clicks made in the user's other windows.
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.keyBindAttack.setKeyCode(Keyboard.KEY_F13);
        mc.gameSettings.keyBindUseItem.setKeyCode(Keyboard.KEY_F14);
        KeyBinding.resetKeyBindingArrayAndHash();
        while (!(bridge instanceof LocalClientBridge)) bridge = field(bridge, "bridge");
        MinecraftForge.EVENT_BUS.register(this);
        log("READY");
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void isolate(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        while (Keyboard.next()) { }
        while (Mouse.next()) { }
        // Production controls read the previous END's real keybinding state before this driver clears it.
        KeyBinding.unPressAllKeys();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent event) { event.setCanceled(true); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || stage == 99) return;
        long now = mc.world.getTotalWorldTime();
        mc.player.inventory.currentItem = 0; mc.playerController.updateController();
        mc.setIngameNotInFocus();
        if (stage == 0) {
            if (now < nextCastAfter) return; // Respect the production cooldown, including after an early exit.
            if (!new File(output, "ready-" + sequence).isFile() || LaunchController.remote(mc.player).isEmpty()) return;
            require(!mc.player.isCreative() && !mc.player.isSpectator(), "Survival baseline required");
            require(!mc.player.capabilities.allowFlying && !mc.player.capabilities.isFlying, "Flight leaked between passes");
            require(!active(), "Command controls leaked between passes");
            mc.displayGuiScreen(null);
            mc.gameSettings.pauseOnLostFocus = false; mc.gameSettings.thirdPersonView = 1;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            LocalClientBridge.filmsEnabled = false; // Gameplay must work with the optional films disabled.
            if (sequence == 0) {
                StellarNetwork.requestSelection(AttackId.ASTEROID_PULLER);
                stage = 1;
            } else { StellarNetwork.requestSelection(AttackId.CARRIER_GUNNERY); stage = 3; }
            deadline = now + 100; return;
        }
        require(now < deadline, "Timeout sequence=" + sequence + " stage=" + stage);
        if (stage == 1 && selected(mc) == AttackId.ASTEROID_PULLER) {
            press(mc, "moon_attract"); stage = 2; return;
        }
        if (stage == 2 && selected(mc) == AttackId.CARRIER_GUNNERY) {
            log("M_SELECTED wireId=7");
            press(mc, "menu"); require(mc.currentScreen != null, "H did not open menu");
            GuiScreen menu = mc.currentScreen;
            require(field(menu, "preview") == AttackId.CARRIER_GUNNERY, "Menu lost payload B");
            call(menu, "keyTyped", new Class<?>[]{char.class, int.class}, '\0', Keyboard.KEY_LEFT);
            require(field(menu, "preview") == AttackId.ASTEROID_PULLER, "Left did not cycle B to A");
            call(menu, "keyTyped", new Class<?>[]{char.class, int.class}, '\0', Keyboard.KEY_RIGHT);
            require(field(menu, "preview") == AttackId.CARRIER_GUNNERY, "Right did not cycle A to B");
            mc.displayGuiScreen(null); stage = 3;
            log("MENU_CYCLE payloads=A,B");
        }
        if (stage == 3) {
            if (selected(mc) != AttackId.CARRIER_GUNNERY) return;
            aim(mc, 256.5, 4.5, 256.5);
            stage = 4; deadline = now + 10; return;
        }
        if (stage == 4) {
            aim(mc, 256.5, 4.5, 256.5);
            if (now < deadline - 2) return;
            BlockPos hit = LaunchController.aimedBlock(mc.player, 256);
            require(hit != null, "No terrain under aim");
            StellarNetwork.requestLaunch(new LaunchRequest(AttackId.CARRIER_GUNNERY, mc.player.dimension,
                    new BlockTarget(hit.getX(), hit.getY(), hit.getZ())));
            stage = 5; deadline = now + 900; log("CAST sequence=" + sequence); return;
        }
        if (stage == 5) {
            if (launch == null) {
                for (LaunchState state : launches().values()) if (state.getAttack() == AttackId.CARRIER_GUNNERY) launch = state;
                if (launch == null) return;
                require(launch.getDeployment().getX() == 264 && launch.getDeployment().getZ() == 256, "Wrong seat column");
                log("LAUNCH sequence=" + sequence + " id=" + launch.getLaunchId());
                // Before boarding and with a forged ID: neither packet may produce a shot.
                StellarNetwork.CHANNEL.sendToServer(new CarrierControlMessage(launch.getLaunchId(), CarrierControlMessage.FIRE, 0, 90));
            }
            long age = now - launch.getStartTick();
            if (age < 75) {
                require(shots().isEmpty(), "Server accepted pre-board fire");
                aim(mc, 264.5, CarrierGunneryRules.SHIP_Y, 256.5);
            }
            if (age >= 85 && age < 675 && !exited) {
                require(active() && aboard(), "Controls/boarding not active with films disabled at " + age);
                require(Math.abs(mc.player.posY - CarrierGunneryRules.SEAT_Y) < 0.1, "Not physically teleported to gondola");
                require(mc.player.capabilities.allowFlying && mc.player.capabilities.isFlying, "Missing temporary flight exemption");
                require(mc.gameSettings.thirdPersonView == 0, "Gondola did not force first-person");
                if (!boarded) { boarded = true; log("BOARDED sequence=" + sequence + " age=" + age + " y=" + mc.player.posY); }
                // Look up at the port forward battery from the gondola, then down at the live target for firing.
                if (age < 110) {
                    double[] ring = BroadsideBatteries.ring(BroadsideBatteries.frame(launch), 3);
                    aim(mc, ring[0], ring[1] - 6, ring[2]);
                } else if (sequence == 0 && age >= 290 && age < 340) {
                    // Aft and down past the keel battery: it lays and fires inside the frame (airburst at the loaded edge).
                    mc.player.rotationYaw = -90; mc.player.rotationPitch = 25;
                } else aim(mc, 256.5, 5, 256.5);
                if (age == 128) {
                    double[] pitch = pitches();
                    int laid = 0;
                    StringBuilder text = new StringBuilder();
                    for (double p : pitch) { if (p < -0.6) laid++; text.append(String.format(java.util.Locale.ROOT, "%.1f,", Math.toDegrees(p))); }
                    log("TRAVERSE sequence=" + sequence + " pitch=" + text);
                    require(laid >= 3, "Batteries did not depress onto the designated target");
                }
                mc.player.prevRotationYaw = mc.player.rotationYaw; mc.player.prevRotationPitch = mc.player.rotationPitch;
                firing = age >= 130 && age < 180 || sequence == 0 && age >= 300 && age < 336;
                zoom = age >= 130 && age < 170;
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), firing);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), zoom);
                if (age >= 112 && age <= 115) {
                    // A wrong launch UUID must not fire. Then deliberately spam valid packets in one server tick.
                    StellarNetwork.CHANNEL.sendToServer(new CarrierControlMessage(new UUID(0, 0), CarrierControlMessage.FIRE, 0, 90));
                }
                if (age == 185) for (int i = 0; i < 25; i++) StellarNetwork.CHANNEL.sendToServer(new CarrierControlMessage(
                        launch.getLaunchId(), CarrierControlMessage.FIRE, mc.player.rotationYaw, mc.player.rotationPitch));
                if (sequence == 1 && age >= 200) {
                    KeyBinding.onTick(binding(mc, "skip").getKeyCode());
                    exited = true; log("EXIT_REQUEST sequence=1 key=J");
                } else if (sequence == 2 && age >= 200) {
                    exited = true; log("CANCEL_REQUEST sequence=2");
                }
            }
            List<CarrierShot> shots = shots();
            for (CarrierShot shot : shots) {
                if (confirmed.add(shot.issued)) {
                    require(shot.issued - launch.getStartTick() >= 130, "Preboard/forged-ID shot accepted");
                    // Muzzles are on the ventral batteries, well under the hull; the shell lands on the target.
                    require(shot.y < CarrierGunneryRules.SHIP_Y - 10 && shot.y > CarrierGunneryRules.SHIP_Y - 110, "Shell not fired from a battery");
                    require(shot.fire >= shot.issued && shot.impact > shot.fire, "Implausible shell timing");
                    require(shot.issued - launch.getStartTick() >= 290 || shot.hitY < 12, "Shell did not land on the designated target");
                    batteries.add(shot.battery);
                    log("SHOT sequence=" + sequence + " tick=" + shot.issued + " battery=" + shot.battery + " fire=" + shot.fire
                            + " impact=" + shot.impact + " kind=" + shot.kind + " muzzle=" + shot.x + "," + shot.y + "," + shot.z
                            + " hit=" + shot.hitX + "," + shot.hitY + "," + shot.hitZ);
                }
            }
            if (!active() && boarded && age >= (sequence == 0 ? 680 : 202)) {
                require(Math.abs(mc.player.posX - 264.5) < 0.1 && Math.abs(mc.player.posY - 4) < 0.1
                        && Math.abs(mc.player.posZ - 256.5) < 0.1, "Did not return to saved position");
                require(!mc.player.capabilities.allowFlying && !mc.player.capabilities.isFlying, "Temporary flight not restored");
                require(mc.gameSettings.thirdPersonView == 1, "Previous camera not restored");
                require(!LocalClientBridge.filmsEnabled, "J toggled optional films instead of exiting command");
                if (sequence == 0 && age >= 700) aim(mc, 264.5, CarrierGunneryRules.SHIP_Y, 256.5);
                if (!naturalReturned) { naturalReturned = true; log("RETURNED sequence=" + sequence + " age=" + age + " flightRestored=true"); }
                if (sequence != 0 || age >= 765) {
                    require(batteries.size() >= 3, "Fire was not distributed across the ventral batteries: " + batteries);
                    log("FINISHED sequence=" + sequence + " batteries=" + batteries.size());
                    if (++sequence >= 3) { require(glFrames > 100, "Too few GL frames"); log("PASS glFrames=" + glFrames); mc.shutdown(); stage = 99; }
                    else {
                        nextCastAfter = launch.getStartTick() + 610; stage = 0; launch = null; boarded = exited = naturalReturned = false;
                        confirmed.clear(); batteries.clear(); captureIndex = 0;
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public void beforeFov(EntityViewRenderEvent.FOVModifier event) { incomingFov = event.getFOV(); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void afterFov(EntityViewRenderEvent.FOVModifier event) {
        if (aboard()) {
            float expected = optic() ? 30 : 78;
            require(Math.abs(event.getFOV() - expected) < 0.01, "Incorrect command FOV");
        } else if (!active()) require(Math.abs(event.getFOV() - incomingFov) < 0.01, "Command FOV leaked");
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage != 5 || launch == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error"); glFrames++;
        long age = mc.world.getTotalWorldTime() - launch.getStartTick();
        if (captureIndex < frames.length && age >= frames[captureIndex] && (sequence == 0 || frames[captureIndex] < 200)) {
            String name = "carrier-" + sequence + "-" + frames[captureIndex++] + ".png";
            ScreenShotHelper.saveScreenshot(output, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            require(new File(output, "screenshots/" + name).isFile(), "Missing capture");
            int columns = 0;
            int cx = ((int) Math.floor(mc.player.posX)) >> 4, cz = ((int) Math.floor(mc.player.posZ)) >> 4;
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++)
                if (mc.world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz) != null) columns++;
            log("FRAME sequence=" + sequence + " age=" + age + " file=" + name + " columns=" + columns
                    + " distance=" + mc.gameSettings.renderDistanceChunks + " terrain=" + mc.renderGlobal.getDebugInfoRenders());
        }
    }
    private static AttackId selected(Minecraft mc) { return ItemStellarRemote.selected(LaunchController.remote(mc.player)); }
    @SuppressWarnings("unchecked") private Map<UUID, LaunchState> launches() { return (Map<UUID, LaunchState>) field(bridge, "launches"); }
    /** Server-confirmed shells of the current launch; an earlier launch can still be departing. */
    @SuppressWarnings("unchecked") private List<CarrierShot> shots() {
        List<CarrierShot> shots = new java.util.ArrayList<>((List<CarrierShot>) clientCall("BroadsideFireControl", "shots", new Class<?>[0]));
        shots.removeIf(shot -> !shot.launchId.equals(launch.getLaunchId()));
        return shots;
    }
    /** Mirrored battery elevations, radians; negative is depressed below the horizon. */
    private double[] pitches() {
        Object fleet = clientCall("BroadsideFireControl", "find", new Class<?>[]{UUID.class}, launch.getLaunchId());
        require(fleet != null, "No battery mirror for the launch");
        Object[] guns = (Object[]) field(fleet, "batteries");
        double[] pitch = new double[guns.length];
        for (int i = 0; i < guns.length; i++) pitch[i] = ((double[]) field(guns[i], "bearing"))[1];
        return pitch;
    }
    private static boolean active() { return (Boolean) controlCall("active"); }
    private static boolean aboard() { return (Boolean) controlCall("aboard"); }
    private static boolean optic() { return (Boolean) controlCall("optic"); }
    private static Object controlCall(String name) { return clientCall("CarrierGunneryClient", name, new Class<?>[0]); }
    private static Object clientCall(String type, String name, Class<?>[] args, Object... values) {
        try { Method m = Class.forName("fr.frabous.stellar.client." + type).getDeclaredMethod(name, args); m.setAccessible(true); return m.invoke(null, values); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void call(Object object, String name, Class<?>[] args, Object... values) {
        try { Method m = object.getClass().getDeclaredMethod(name, args); m.setAccessible(true); m.invoke(object, values); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static KeyBinding binding(Minecraft mc, String name) {
        for (KeyBinding k : mc.gameSettings.keyBindings) if (k.getKeyDescription().equals("key.stellar." + name)) return k;
        throw new IllegalStateException("Missing binding " + name);
    }
    private static void press(Minecraft mc, String name) { KeyBinding.onTick(binding(mc, name).getKeyCode()); MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent()); }
    private static void aim(Minecraft mc, double x, double y, double z) {
        double dx=x-mc.player.posX, dz=z-mc.player.posZ, dy=y-mc.player.posY-mc.player.getEyeHeight();
        mc.player.rotationYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        mc.player.rotationPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        mc.player.prevRotationYaw=mc.player.rotationYaw; mc.player.prevRotationPitch=mc.player.rotationPitch;
    }
    private static void require(boolean condition, String message) { if (!condition) { log("FAIL " + message); throw new IllegalStateException(message); } }
    private static void log(String message) { System.out.println("STELLAR_CARRIER_SMOKE_" + message); }
}
