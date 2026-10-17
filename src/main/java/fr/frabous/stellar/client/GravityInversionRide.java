package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchState;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Riding SS-05: a captured local player whirls and banks with the field, every captured body tumbles. */
final class GravityInversionRide {
    private final LocalClientBridge bridge;
    private final Set<EntityLivingBase> turned = Collections.newSetFromMap(new IdentityHashMap<>());

    GravityInversionRide(LocalClientBridge bridge) { this.bridge = bridge; }

    /** Client mirror of the server's capture test, good enough to dress what the server already moves. */
    static boolean captured(LaunchState state, Entity entity, double age) {
        if (state.getAttack() != AttackId.GRAVITY_INVERSION || age < ASCENSION || age >= IMPACT) return false;
        if (entity.world == null || entity.world.provider.getDimension() != state.getDimension() || entity.isRiding()) return false;
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) return false;
        return contains(state.getTarget(), entity.posX, entity.posY, entity.posZ);
    }

    /** The body really turns, so other players see it spin too. */
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.world == null || mc.player == null || mc.isGamePaused()) return;
        double yaw = 0;
        for (LaunchState state : bridge.launches.values()) {
            double age = mc.world.getTotalWorldTime() - state.getStartTick();
            if (captured(state, mc.player, age)) yaw = Math.max(yaw, GravityInversionVisuals.rideYaw(age));
        }
        if (yaw > 0) mc.player.rotationYaw += (float) yaw;
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        // Films drive a detached camera of their own.
        if (mc.world == null || mc.player == null || mc.getRenderViewEntity() != mc.player) return;
        double roll = 0;
        for (LaunchState state : bridge.launches.values()) {
            double age = mc.world.getTotalWorldTime() - state.getStartTick() + event.getRenderPartialTicks();
            if (!captured(state, mc.player, age)) continue;
            double bank = GravityInversionVisuals.rideRoll(age);
            if (Math.abs(bank) > Math.abs(roll)) roll = bank;
        }
        if (roll != 0) event.setRoll(event.getRoll() + (float) roll);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void tumble(RenderLivingEvent.Pre<?> event) {
        if (bridge.launches.isEmpty()) return;
        EntityLivingBase entity = event.getEntity();
        double angle = 0;
        for (LaunchState state : bridge.launches.values()) {
            double age = entity.world.getTotalWorldTime() - state.getStartTick() + event.getPartialRenderTick();
            if (!captured(state, entity, age)) continue;
            long key = entity.getEntityId() * 0x9E3779B97F4A7C15L;
            double scale = (0.7 + 0.6 * unit(key, 3)) * (unit(key, 5) < 0.5 ? -1 : 1);
            angle = GravityInversionVisuals.tumble(age, scale);
        }
        if (Math.abs(angle) < 0.01) return;
        // Each body spins about its own horizontal axis, through its middle.
        double heading = unit(entity.getEntityId() * 0x2545F4914F6CDD1DL, 4) * Math.PI * 2;
        double cy = event.getY() + entity.height * 0.5;
        GlStateManager.pushMatrix();
        GlStateManager.translate(event.getX(), cy, event.getZ());
        GlStateManager.rotate((float) angle, (float) Math.cos(heading), 0, (float) Math.sin(heading));
        GlStateManager.translate(-event.getX(), -cy, -event.getZ());
        turned.add(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void untumble(RenderLivingEvent.Post<?> event) {
        if (turned.remove(event.getEntity())) GlStateManager.popMatrix();
    }
}
