package fr.frabous.stellar.client;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Supplement vanilla's terrain-section and distance culling only during detached-camera shots. */
final class CinematicEntities {
    private static final Field RENDER_INFOS = ReflectionHelper.findField(RenderGlobal.class,
            new String[] {"renderInfos", "field_72755_R"});
    private static final Field STARTUP_COUNTER = ReflectionHelper.findField(RenderGlobal.class,
            new String[] {"renderEntitiesStartupCounter", "field_72740_G"});
    private static final Field INFO_CHUNK = renderChunkField();

    private static Field renderChunkField() {
        for (Class<?> type : RenderGlobal.class.getDeclaredClasses()) {
            for (Field field : type.getDeclaredFields()) if (field.getType() == RenderChunk.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Cannot find cinematic entity render sections");
    }

    static void render(Entity view, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.getRenderViewEntity() != view || view.world != mc.world) return;
        Set<BlockPos> sections = new HashSet<>();
        boolean vanillaReady;
        try {
            for (Object info : (List<?>) RENDER_INFOS.get(mc.renderGlobal))
                sections.add(((RenderChunk) INFO_CHUNK.get(info)).getPosition());
            vanillaReady = STARTUP_COUNTER.getInt(mc.renderGlobal) == 0;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect cinematic entity render sections", exception);
        }
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;
        Frustum frustum = new Frustum();
        frustum.setPosition(x, y, z);
        RenderManager manager = mc.getRenderManager();
        manager.cacheActiveRenderInfo(mc.world, mc.fontRenderer, view, mc.pointedEntity, mc.gameSettings, partial);
        manager.setRenderPosition(x, y, z);
        int previousPass = MinecraftForgeClient.getRenderPass();
        mc.entityRenderer.enableLightmap();
        RenderHelper.enableStandardItemLighting();
        try {
            for (Entity entity : mc.world.getLoadedEntityList()) {
                if (entity == view || entity.isDead
                        || mc.world.getChunkProvider().getLoadedChunk(entity.chunkCoordX, entity.chunkCoordZ) == null) continue;
                // Keep the real camera frustum, but do not discard a loaded model merely because the shot pulled back.
                if (!manager.shouldRender(entity, frustum, entity.posX, entity.posY, entity.posZ)) continue;
                boolean vanillaRendered = vanillaReady && entity != mc.player
                        && sections.contains(new BlockPos(entity.chunkCoordX * 16, entity.chunkCoordY * 16, entity.chunkCoordZ * 16))
                        && (manager.shouldRender(entity, frustum, x, y, z) || entity.isRidingOrBeingRiddenBy(mc.player));
                if (vanillaRendered) continue;
                Entity previousView = manager.renderViewEntity;
                // RenderPlayer otherwise refuses to draw the local user's model for a detached view entity.
                if (entity == mc.player) manager.renderViewEntity = mc.player;
                try {
                    for (int pass = 0; pass <= 1; pass++) {
                        if (!entity.shouldRenderInPass(pass)) continue;
                        ForgeHooksClient.setRenderPass(pass);
                        manager.renderEntityStatic(entity, partial, false);
                    }
                    if (entity.shouldRenderInPass(0) && manager.isRenderMultipass(entity)) {
                        ForgeHooksClient.setRenderPass(0);
                        manager.renderMultipass(entity, partial);
                    }
                } finally { manager.renderViewEntity = previousView; }
            }
        } finally {
            ForgeHooksClient.setRenderPass(previousPass);
            RenderHelper.disableStandardItemLighting();
            mc.entityRenderer.disableLightmap();
        }
    }
}
