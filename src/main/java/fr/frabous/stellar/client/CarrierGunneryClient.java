package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import fr.frabous.stellar.api.network.*;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Gondola seat: normal Minecraft mouse-look, with movement and vanilla item actions suppressed. The
 * crosshair is the fire-control designator; the batteries on the hull lay themselves on it.
 */
final class CarrierGunneryClient {
    private static final int TEXT = 0x8FEAFF, WARN = 0xFFC078, ALERT = 0xFF6A5A, GOOD = 0x9DFFB4;
    private static LaunchState command;
    private static WorldClient world;
    private static int previousView;
    private static long lastRequest = -100, lastReport = -100;
    private static float reportedYaw = Float.NaN, reportedPitch;
    private static boolean leaving, zoomHeld;
    private static Boolean frameOcclusion;
    private static double[] localAim;

    static boolean active() { return command != null; }
    static boolean aboard() {
        Minecraft mc = Minecraft.getMinecraft();
        return active() && mc.world == world && CarrierGunneryRules.controls(age()) && mc.player != null
                && Math.abs(mc.player.posY - CarrierGunneryRules.SEAT_Y) < 3;
    }
    static boolean owns(UUID id) { return aboard() && command.getLaunchId().equals(id); }
    /** This client commands the launch, boarded or not yet. */
    static boolean commands(UUID id) { return active() && command.getLaunchId().equals(id); }
    static boolean optic() { return aboard() && zoomHeld; }
    /** Crosshair point traced on the client this tick, or null when not seated. */
    static double[] localAim() { return aboard() ? localAim : null; }
    private static long age() { return world == null || command == null ? -1 : world.getTotalWorldTime() - command.getStartTick(); }

    static void begin(LaunchState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (command != null && command.getLaunchId().equals(state.getLaunchId())) return;
        reset();
        command = state; world = mc.world; previousView = mc.gameSettings.thirdPersonView;
        lastRequest = lastReport = -100; reportedYaw = Float.NaN; leaving = false;
        if (mc.currentScreen != null) mc.displayGuiScreen(null);
    }
    static void end(UUID id) { if (command != null && command.getLaunchId().equals(id)) close(); }
    private static void close() {
        restoreFrame();
        if (command != null) Minecraft.getMinecraft().gameSettings.thirdPersonView = previousView;
        command = null; leaving = false; zoomHeld = false; localAim = null;
    }
    static void reset() { close(); world = null; }

    private static boolean down(KeyBinding key) {
        if (key.isKeyDown()) return true;
        int code = key.getKeyCode();
        return code < 0 ? code >= -100 && Mouse.isButtonDown(code + 100)
                : code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }
    private static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active() || leaving || mc.player == null) return;
        leaving = true;
        StellarNetwork.CHANNEL.sendToServer(new CarrierControlMessage(command.getLaunchId(), CarrierControlMessage.EXIT, 0, 0));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != null && mc.world != world) { reset(); return; }
        if (!active()) return;
        if (mc.player == null || !mc.player.isEntityAlive()) { close(); return; }
        if (age() >= CarrierGunneryRules.RETURN) { close(); return; }
        if (ClientControls.SKIP.isPressed() || mc.currentScreen instanceof GuiIngameMenu) {
            exit();
            if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);
        }
        if (!aboard()) { localAim = null; return; }
        mc.gameSettings.thirdPersonView = 0;
        localAim = trace(mc);
        boolean firing = down(mc.gameSettings.keyBindAttack);
        zoomHeld = mc.currentScreen == null && down(mc.gameSettings.keyBindUseItem);
        // Drain vanilla item actions; the command packets below are the only gameplay input at this seat.
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        while (mc.gameSettings.keyBindAttack.isPressed()) { }
        while (mc.gameSettings.keyBindUseItem.isPressed()) { }
        if (leaving) return;
        float yaw = MathHelper.wrapDegrees(mc.player.rotationYaw), pitch = mc.player.rotationPitch;
        long age = age();
        if (mc.currentScreen == null && firing && age - lastRequest >= CarrierGunneryRules.SHOT_INTERVAL) {
            lastRequest = lastReport = age;
            BroadsideFireControl.Fleet fleet = BroadsideFireControl.find(command.getLaunchId());
            if (fleet != null && localAim != null && !BroadsideFireControl.inArc(fleet, localAim)) BroadsideAudio.denied();
            report(CarrierControlMessage.FIRE, yaw, pitch);
        } else if (age - lastReport >= CarrierGunneryRules.AIM_INTERVAL
                && (Float.isNaN(reportedYaw) || Math.abs(MathHelper.wrapDegrees(yaw - reportedYaw)) > 0.05F || Math.abs(pitch - reportedPitch) > 0.05F)) {
            lastReport = age;
            report(CarrierControlMessage.AIM, yaw, pitch);
        }
    }

    private static void report(int action, float yaw, float pitch) {
        reportedYaw = yaw; reportedPitch = pitch;
        StellarNetwork.CHANNEL.sendToServer(new CarrierControlMessage(command.getLaunchId(), action, yaw, pitch));
    }

    /** Terrain under the crosshair, walked like the server's trace: it stops at the first unloaded chunk. */
    private static double[] trace(Minecraft mc) {
        Vec3d eye = mc.player.getPositionEyes(1), look = mc.player.getLook(1), previous = eye;
        for (int step = 1; ; step++) {
            double distance = Math.min(CarrierGunneryRules.RANGE, step * 8.0);
            Vec3d next = eye.add(look.scale(distance));
            if (mc.world.getChunkProvider().getLoadedChunk(MathHelper.floor(next.x) >> 4, MathHelper.floor(next.z) >> 4) == null)
                return new double[] {previous.x, previous.y, previous.z};
            if (previous.y < 256 || next.y < 256) {
                RayTraceResult hit = mc.world.rayTraceBlocks(previous, next, false, true, false);
                if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) return new double[] {hit.hitVec.x, hit.hitVec.y, hit.hitVec.z};
            }
            if (next.y < 0 || distance >= CarrierGunneryRules.RANGE) return new double[] {next.x, next.y, next.z};
            previous = next;
        }
    }

    @SubscribeEvent public void movement(InputUpdateEvent event) {
        if (!aboard()) return;
        event.getMovementInput().moveForward = event.getMovementInput().moveStrafe = 0;
        event.getMovementInput().jump = event.getMovementInput().sneak = false;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void mouse(MouseEvent event) {
        if (active() && (event.getButton() == 0 || event.getButton() == 1)) event.setCanceled(true);
    }
    @SubscribeEvent public void hand(RenderHandEvent event) { if (aboard()) event.setCanceled(true); }
    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier event) {
        if (aboard()) event.setFOV(optic() ? 30 : 78);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { restoreFrame(); return; }
        if (!aboard()) return;
        Minecraft mc = Minecraft.getMinecraft();
        frameOcclusion = mc.renderChunksMany;
        // From the gondola, vanilla's visibility traversal can omit the ground far below.
        mc.renderChunksMany = false;
    }
    private static void restoreFrame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (frameOcclusion != null) { mc.renderChunksMany = frameOcclusion; frameOcclusion = null; }
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Pre event) {
        if (active() && event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS) event.setCanceled(true);
        if (aboard() && event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) event.setCanceled(true);
    }

    @SubscribeEvent public void hud(RenderGameOverlayEvent.Post event) {
        if (!active() || event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int w = event.getResolution().getScaledWidth(), h = event.getResolution().getScaledHeight();
        double age = age() + event.getPartialTicks();
        // Transfer flash: the beam whites out the view as the gondola takes the pilot aboard.
        double flash = Math.max(0, 1 - Math.abs(age - CarrierGunneryRules.BOARD) / 10);
        if (flash > 0) Gui.drawRect(0, 0, w, h, (int) (flash * flash * 235) << 24 | 0xEAF8FF);
        int accent = leaving ? WARN : TEXT;
        Gui.drawRect(0, 0, w, 28, 0xD0081420);
        font.drawStringWithShadow("SS-03B / BROADSIDE COMMAND", 12, 7, accent);
        String timer = String.format(Locale.ROOT, "%.1fs", Math.max(0, Math.min(CarrierGunneryRules.CONTROL_TICKS, CarrierGunneryRules.RETURN - age)) / 20.0);
        font.drawStringWithShadow(timer, w - 12 - font.getStringWidth(timer), 7, accent);
        String hint = leaving ? "RETURNING TO DEPLOYMENT POINT" : aboard()
                ? "ATTACK: fire  /  USE: gun optic  /  J or Esc: return"
                : String.format(Locale.ROOT, "WARP ARRIVAL / Transfer beam in %.1fs / J or Esc: cancel", Math.max(0, CarrierGunneryRules.BOARD - age) / 20.0);
        Gui.drawRect(0, h - 24, w, h, 0xD0081420);
        font.drawStringWithShadow(hint, Math.max(4, (w - font.getStringWidth(hint)) / 2), h - 16, accent);
        if (!aboard()) return;
        int x = w / 2, y = h / 2;
        if (optic()) {
            int edge = Math.max(18, w / 8);
            Gui.drawRect(0, 28, edge, h - 24, 0xDD030A12);
            Gui.drawRect(w - edge, 28, w, h - 24, 0xDD030A12);
            Gui.drawRect(edge, 28, edge + 1, h - 24, 0xAA8FEAFF);
            Gui.drawRect(w - edge - 1, 28, w - edge, h - 24, 0xAA8FEAFF);
            for (int offset = -3; offset <= 3; offset++)
                Gui.drawRect(x + offset * 18, y + 32, x + offset * 18 + 1, y + (offset == 0 ? 42 : 37), 0xAA8FEAFF);
        }
        BroadsideFireControl.Fleet fleet = BroadsideFireControl.find(command.getLaunchId());
        long now = world.getTotalWorldTime();
        String readout = "ACQUIRING";
        int readoutColor = TEXT;
        if (fleet != null && localAim != null) {
            int battery = BroadsideFireControl.solutionFor(fleet, localAim, now);
            double range = Math.sqrt(mc.player.getDistanceSq(localAim[0], localAim[1], localAim[2]));
            if (battery >= 0) {
                double[] ring = BroadsideBatteries.ring(fleet.frame, battery);
                double tof = BroadsideBatteries.flightTicks(Math.sqrt(sq(ring[0] - localAim[0]) + sq(ring[1] - localAim[1]) + sq(ring[2] - localAim[2]))) / 20.0;
                readout = String.format(Locale.ROOT, "%s  RNG %dm  TOF %.2fs", BroadsideBatteries.NAMES[battery], Math.round(range), tof);
                readoutColor = GOOD;
            } else if (BroadsideFireControl.inArc(fleet, localAim)) {
                readout = String.format(Locale.ROOT, "RELOADING  RNG %dm", Math.round(range)); readoutColor = WARN;
            } else { readout = "NO FIRING SOLUTION"; readoutColor = ALERT; }
        }
        int reticle = readoutColor | 0xDD000000;
        Gui.drawRect(x - 20, y, x - 6, y + 1, reticle);
        Gui.drawRect(x + 6, y, x + 20, y + 1, reticle);
        Gui.drawRect(x, y - 20, x + 1, y - 6, reticle);
        Gui.drawRect(x, y + 6, x + 1, y + 20, reticle);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
        font.drawStringWithShadow(readout, x - font.getStringWidth(readout) / 2, y + 26, readoutColor);
        String mode = optic() ? "OPTICAL LINK x2.6" : "FIRE-CONTROL GONDOLA / HOLD USE FOR OPTIC";
        font.drawStringWithShadow(mode, Math.max(4, (w - font.getStringWidth(mode)) / 2), 35, accent);
        if (fleet != null) batteries(font, fleet, age + command.getStartTick(), 8, 50);
    }

    /** One row per battery: status lamp, name, state and its reload bar. */
    private static void batteries(FontRenderer font, BroadsideFireControl.Fleet fleet, double time, int left, int top) {
        DestroyerBatteryModel.Pose[] poses = BroadsideFireControl.poses(fleet, time);
        Gui.drawRect(left - 4, top - 4, left + 150, top + 11 * poses.length + 14, 0x900A1620);
        font.drawStringWithShadow("VENTRAL BATTERIES", left, top, TEXT);
        int inFlight = 0;
        for (CarrierShot shot : fleet.shots) if (time >= shot.fire && time < shot.impact) inFlight++;
        if (inFlight > 0) font.drawStringWithShadow(inFlight + " IN FLIGHT", left + 100, top, GOOD);
        for (int i = 0; i < poses.length; i++) {
            DestroyerBatteryModel.Pose p = poses[i];
            int row = top + 12 + i * 11, color = DestroyerBatteryModel.statusColor(p);
            Gui.drawRect(left, row + 1, left + 5, row + 6, 0xFF000000 | color);
            font.drawStringWithShadow(BroadsideBatteries.NAMES[i], left + 9, row, TEXT);
            String state;
            switch (p.status) {
                case DestroyerBatteryModel.READY: state = "READY"; break;
                case DestroyerBatteryModel.LAYING: state = "LAYING"; break;
                case DestroyerBatteryModel.RELOAD: state = "RELOAD"; break;
                case DestroyerBatteryModel.NO_ARC: state = "NO ARC"; break;
                default: state = "STANDBY";
            }
            font.drawStringWithShadow(state, left + 86, row, color);
            int bar = (int) Math.round(22 * p.charge);
            Gui.drawRect(left + 124, row + 2, left + 146, row + 5, 0x60000000);
            Gui.drawRect(left + 124, row + 2, left + 124 + bar, row + 5, 0xC0000000 | color);
        }
    }

    private static double sq(double value) { return value * value; }
}
