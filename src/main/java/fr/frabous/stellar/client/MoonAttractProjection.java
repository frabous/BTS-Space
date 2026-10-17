package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackFamily;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchState;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Observers share the terrain depth projection with distant lunar and stellar models. */
final class MoonAttractProjection {
    private static final Field FAR = ReflectionHelper.findField(EntityRenderer.class,
            new String[]{"farPlaneDistance", "field_78530_s"});
    private final LocalClientBridge bridge;
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private Float previousFar;

    MoonAttractProjection(LocalClientBridge bridge) { this.bridge = bridge; }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.currentScreen instanceof AsteroidPullerCinema
                || mc.currentScreen instanceof RailgunCinema || mc.currentScreen instanceof SupernovaCinema
                || mc.currentScreen instanceof GravityInversionCinema) return;
        boolean carrier = false, distant = false;
        for (LaunchState state : bridge.launches.values()) {
            carrier |= state.getAttack().getFamily() == AttackFamily.CARRIER;
            distant |= carrier || state.getAttack() == AttackId.SUPERNOVA;
        }
        if (!distant) return;
        try {
            float current = FAR.getFloat(mc.entityRenderer);
            if (previousFar == null) previousFar = current;
            // Carrier strikes approach from about 2.6 km and jump out 1 km away; keep them in the frustum.
            float extended = Math.max(current, carrier ? 2048 : 1450);
            FAR.setFloat(mc.entityRenderer, extended);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            float near = projection.get(14) / (projection.get(10) - 1), far = extended * 2;
            projection.put(10, -(far + near) / (far - near)); projection.put(14, -2 * far * near / (far - near));
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadMatrix(projection); GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot set lunar projection", exception); }
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || previousFar == null) return;
        try { FAR.setFloat(Minecraft.getMinecraft().entityRenderer, previousFar); previousFar = null; }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot restore lunar projection", exception); }
    }
}
