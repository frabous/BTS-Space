package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Ground rumble, flip jolts and the collapse quake for players near an SS-05 strike, outside any film. */
final class GravityInversionWitness {
    private final LocalClientBridge bridge;

    GravityInversionWitness(LocalClientBridge bridge) { this.bridge = bridge; }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        // Films drive a detached camera of their own.
        if (mc.world == null || mc.player == null || view != mc.player || bridge.launches.isEmpty()) return;
        double partial = event.getRenderPartialTicks(), shake = 0;
        for (LaunchState state : bridge.launches.values()) {
            if (state.getAttack() != AttackId.GRAVITY_INVERSION || state.getDimension() != mc.world.provider.getDimension()) continue;
            double age = mc.world.getTotalWorldTime() - state.getStartTick() + partial;
            double distance = Math.hypot(view.posX - (state.getTarget().getX() + 0.5), view.posZ - (state.getTarget().getZ() + 0.5));
            shake = Math.max(shake, (1 - smooth((distance - RADIUS) / (RADIUS * 2.5))) * intensity(age));
        }
        if (shake <= 0.001) return;
        double t = mc.world.getTotalWorldTime() + partial;
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 1.9) * 2.5 * shake));
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 2.7 + 1) * 1.6 * shake));
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 2.3 + 2) * 1.2 * shake));
    }

    static double intensity(double age) {
        if (age < 8 || age >= END) return 0;
        if (age >= IMPACT) return 1.4 * Math.exp(-(age - IMPACT) * 0.08);
        if (age >= SLAM) return 0.35;
        if (age >= COLLAPSE) return 0;
        int k = flip(age);
        if (k >= 0) return Math.max(0.2, Math.exp(-(age - ROTATION - k * FLIP) * 0.25));
        return age < ASCENSION ? 0.12 * smooth((age - 8) / 40) : 0.18;
    }
}
