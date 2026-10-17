package fr.frabous.stellar.client;

import fr.frabous.stellar.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

final class WorldEffects {
    private static final BlockRenderLayer[] RENDER_LAYERS = BlockRenderLayer.values();
    private static final class TerrainReflection {
        // Explicit array selects the fallback overload, independent of FML's launch-only environment flag.
        static final Field RENDER_INFOS = ReflectionHelper.findField(RenderGlobal.class, new String[] {"renderInfos", "field_72755_R"});
        static final Field INFO_CHUNK = renderInfoChunkField();
    }
    private final Set<BlockPos> renderedGround = new HashSet<>();
    private final Set<BlockPos> renderedColumns = new HashSet<>();
    private final BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos sectionPos = new BlockPos.MutableBlockPos();
    private final Map<LaunchState, GroundSurfaces> groundSurfaces = new IdentityHashMap<>();
    private final LocalClientBridge bridge;
    private final BlackholeEffects blackhole;
    private boolean moonBloomPrepared;
    private boolean supernovaBloomPrepared;
    WorldEffects(LocalClientBridge bridge, BlackholeEffects blackhole) { this.bridge = bridge; this.blackhole = blackhole; }
    static int color(AttackId attack) { return attack.getFamily() == AttackFamily.CARRIER ? 0xB68BFF
            : attack == AttackId.SUPERNOVA ? 0xFFD28C
            : attack == AttackId.GRAVITY_INVERSION ? GravityInversionVisuals.MAGENTA
            : attack == AttackId.BLACKHOLE ? 0xFFB04A : AttackParameters.RAILGUN_COLOR_RGB; }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        if (mc.world == null || camera == null) return;
        if (bridge.launches.isEmpty()) {
            if (!moonBloomPrepared && mc.player != null && fr.frabous.stellar.common.ItemStellarRemote.selected(
                    fr.frabous.stellar.common.LaunchController.remote(mc.player)).getFamily() == AttackFamily.CARRIER) {
                CelestialModels.prepareEarth();
                AsteroidModel.prepareTextures();
                EmissionBloom.render(() -> { });
                moonBloomPrepared = EmissionBloom.isReady();
            }
            if (!supernovaBloomPrepared && mc.player != null && fr.frabous.stellar.common.ItemStellarRemote.selected(
                    fr.frabous.stellar.common.LaunchController.remote(mc.player)) == AttackId.SUPERNOVA) {
                SupernovaModel.prepareTextures();
                EmissionBloom.render(() -> { });
                supernovaBloomPrepared = EmissionBloom.isReady();
            }
            return;
        }
        float partial = event.getPartialTicks();
        blackhole.renderSpace(partial);
        // After terrain, bodies and sky, before any hole geometry: only the background is bent.
        blackhole.renderLens(partial);
        if (bridge.launches.values().stream().anyMatch(state -> state.getAttack() == AttackId.RAILGUN)) collectRenderedGround(mc);
        groundSurfaces.keySet().retainAll(bridge.launches.values());
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try {
            GL11.glTranslated(-(camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial),
                    -(camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial),
                    -(camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial));
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG); GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glDepthMask(false);
            GL11.glLineWidth(2);
            for (LaunchState state : bridge.launches.values()) {
                double age = mc.world.getTotalWorldTime() - state.getStartTick() + partial;
                if (age > LaunchTimeline.totalTicks(state.getAttack())) continue;
                if (state.getAttack() == AttackId.RAILGUN) collectGroundSurfaces(state, age);
                draw(state, age, false);
            }
            EmissionBloom.render(() -> {
                for (LaunchState state : bridge.launches.values()) {
                    double age = mc.world.getTotalWorldTime() - state.getStartTick() + partial;
                    if (age <= LaunchTimeline.totalTicks(state.getAttack())) draw(state, age, true);
                }
            });
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); GL13.glActiveTexture(activeTexture); }
    }

    private void draw(LaunchState state, double age, boolean emission) {
        AttackId attack = state.getAttack(); BlockTarget target = state.getTarget();
        if (attack == AttackId.BLACKHOLE) {
            Minecraft mc = Minecraft.getMinecraft();
            blackhole.renderWorld(state, age, mc.getRenderViewEntity(), mc.getRenderPartialTicks(), emission);
            return;
        }
        if (attack == AttackId.ASTEROID_PULLER) {
            // The film's orbital lens draws this launch itself; the world behind it is covered.
            if (Minecraft.getMinecraft().currentScreen instanceof AsteroidPullerCinema && AsteroidPullerCamera.isSpaceShot(age)
                    && ((AsteroidPullerCinema) Minecraft.getMinecraft().currentScreen).matches(state.getLaunchId())) return;
            AsteroidPullerScene.draw(state, age, emission);
            return;
        }
        if (attack == AttackId.CARRIER_GUNNERY) {
            CarrierGunneryScene.draw(state, age, emission);
            return;
        }
        if (attack == AttackId.KINETIC_RAIN) {
            KineticRainScene.draw(state, age, emission);
            return;
        }
        if (attack == AttackId.GRAVITY_INVERSION) {
            GravityInversionScene.draw(state, age, emission);
            return;
        }
        if (attack == AttackId.SUPERNOVA) {
            // The private orbital shot draws the same stellar geometry once, with explicit schematic guides.
            if (Minecraft.getMinecraft().currentScreen instanceof SupernovaCinema && SupernovaVisuals.isSpaceShot(age)
                    && ((SupernovaCinema) Minecraft.getMinecraft().currentScreen).matches(state.getLaunchId())) return;
            SupernovaScene.draw(state, age, emission);
            return;
        }
        double x = target.getX() + 0.5, z = target.getZ() + 0.5, y = target.getY() + 1.1;
        int rgb = color(attack);
        if (age < RailgunVisualTiming.IMPACT) designator(x, y, z, age, rgb);
        if (age >= RailgunVisualTiming.SKY - 2 && age < RailgunVisualTiming.IMPACT + 16) descendingFront(x, y, z, age);
        if (age >= RailgunVisualTiming.ARRIVAL && age < 288) arrivalEnvelopes(x, y, z, age, emission);
        double impact = age - RailgunVisualTiming.IMPACT;
        if (impact < 0) return;
        float beamAlpha = (float) RailgunVisualTiming.beamAlpha(age);
        double radius = 200 * RailgunVisualTiming.blossom(age) * RailgunVisualTiming.radiusScale(age);
        if (beamAlpha > 0) {
            if (!emission) railgunCurtain(x, y, z, radius, impact, beamAlpha);
            curtainFilaments(x, y, z, radius, impact, beamAlpha);
            worldBeam(x, z, y, (1.2 + 2.4 * Math.exp(-impact * 0.25)) * RailgunVisualTiming.radiusScale(age), 0xFF173C, beamAlpha);
            EffectMesh.glowRing(x, y, z, radius, 2 * RailgunVisualTiming.radiusScale(age), 0xFF3857, beamAlpha * 0.6F);
            columnPulses(x, y, z, radius, impact, beamAlpha);
            if (!emission) wallEmbers(x, y, z, radius, impact, beamAlpha);
        }
        impactShocks(x, y, z, impact, rgb);
        // Contact flash: a ground-level sun at the punch point, gone within a second.
        float contact = (float) Math.exp(-impact * 0.3);
        if (contact > 0.02F) EffectMesh.billboard(x, y + 6, z, 70 + impact * 6, 0xFF3158, contact);
        double release = age - RailgunVisualTiming.RELEASE;
        if (release > 0 && release < 30) {
            // The collapsing column lets go of its pressure in one last outward ring.
            float alpha = (float) (0.75 * (1 - release / 30));
            EffectMesh.glowRing(x, y + 0.6, z, release * 11, 2.5 + release * 0.15, 0xFF3857, alpha);
            EffectMesh.glowRing(x, y + 0.6, z, Math.max(0, release * 11 - 6), 1, 0xFFE6EE, alpha * 0.6F);
        }
        float residue = (float) RailgunVisualTiming.residue(age);
        GroundSurfaces surfaces = groundSurfaces.get(state);
        if (!emission && residue > 0 && surfaces != null) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawGroundSurfaces(surfaces.disk, x, z, 200, 0, 0x270008, residue * 0.68F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }
        // The fixed hazard rim outlives the contracting visual beam, with no reset at the final cut.
        float hazard = (float) hazardAlpha(age);
        if (hazard > 0 && surfaces != null) drawGroundSurfaces(surfaces.rim, x, z, 200, 1.4, 0xFF263D, hazard * 0.65F);
        if (residue > 0) {
            if (surfaces != null) drawGroundSurfaces(surfaces.warmRim, x, z, 200.5, 0.7, 0xFFC09A, residue * 0.7F);
            residualSparks(x, y, z, age, residue);
        }
    }

    private static Field renderInfoChunkField() {
        for (Class<?> nested : RenderGlobal.class.getDeclaredClasses()) {
            for (Field field : nested.getDeclaredFields()) {
                if (field.getType() == RenderChunk.class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new IllegalStateException("Cannot find visible terrain chunk field");
    }

    private void collectRenderedGround(Minecraft mc) {
        renderedGround.clear();
        renderedColumns.clear();
        try {
            for (Object info : (Iterable<?>) TerrainReflection.RENDER_INFOS.get(mc.renderGlobal)) {
                RenderChunk chunk = (RenderChunk) TerrainReflection.INFO_CHUNK.get(info);
                for (BlockRenderLayer layer : RENDER_LAYERS) {
                    if (!chunk.getCompiledChunk().isLayerEmpty(layer)) {
                        renderedGround.add(chunk.getPosition().toImmutable());
                        renderedColumns.add(new BlockPos(chunk.getPosition().getX(), 0, chunk.getPosition().getZ()));
                        break;
                    }
                }
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read visible terrain chunks", exception);
        }
    }

    static double hazardAlpha(double age) {
        return RailgunCameraPath.smooth((age - RailgunVisualTiming.ARRIVAL)
                / (RailgunVisualTiming.IMPACT - RailgunVisualTiming.ARRIVAL))
                * (1 - RailgunCameraPath.smooth((age - 500) / (RailgunVisualTiming.END - 500)));
    }

    /** Orbital designation: reticle spins in and locks, then the strike perimeter is traced on the ground. */
    private static void designator(double x, double y, double z, double age, int rgb) {
        float marker = (float) RailgunVisualTiming.marker(age);
        if (marker <= 0) return;
        double lock = RailgunVisualTiming.lock(age);
        float flash = (float) RailgunVisualTiming.lockFlash(age);
        worldBeam(x, z, y, 0.18 + flash * 0.5, rgb, marker * (0.45F + 0.2F * (float) lock + flash * 0.35F));
        double spin = (1 - lock) * Math.PI * 1.5 + age * 0.012;
        double bracket = RailgunCameraPath.mix(34, 9, lock);
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            EffectMesh.arc(x, y + 0.15, z, bracket, 0.35, a - 0.42, a + 0.42, rgb, marker * 0.95F);
        }
        EffectMesh.glowRing(x, y + 0.1, z, RailgunCameraPath.mix(60, 5, lock), 0.45, rgb, marker * (float) (0.35 + 0.65 * lock));
        EffectMesh.glowRing(x, y + 0.1, z, 2.2, 0.35 + flash * 0.6, 0xFFE6EE, marker * (0.6F + flash * 0.4F));
        if (age >= RailgunVisualTiming.LOCK) {
            double ping = ((age - RailgunVisualTiming.LOCK) % 20) / 20;
            EffectMesh.glowRing(x, y + 0.1, z, 9 + ping * 24, 0.4, rgb, marker * (float) (1 - ping) * 0.6F);
        }
        if (flash > 0) EffectMesh.billboard(x, y + 1, z, 4 + flash * 5, rgb, flash * marker * 0.6F);
        double sweep = RailgunVisualTiming.perimeter(age);
        if (sweep <= 0) return;
        double start = -Math.PI / 2, reached = sweep * Math.PI * 2;
        EffectMesh.arc(x, y + 0.2, z, 200, 0.6, start, start + reached, rgb, marker * 0.7F);
        // Light posts rise as the trace passes them, then settle into a low fence.
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 72; i++) {
            double offset = i * Math.PI * 2 / 72, behind = reached - offset;
            if (behind < 0) break;
            double a = start + offset, h = 3 + 10 * Math.exp(-behind * 3);
            double px = x + Math.cos(a) * 200, pz = z + Math.sin(a) * 200;
            EffectMesh.vertex(b, px, y - 1, pz, rgb, marker * 0.8F);
            EffectMesh.vertex(b, px, y + h, pz, rgb, 0);
        }
        Tessellator.getInstance().draw();
        if (sweep < 1) EffectMesh.billboard(x + Math.cos(start + reached) * 200, y + 1.5,
                z + Math.sin(start + reached) * 200, 7, rgb, marker);
    }

    /** The returning beam is seen falling: a hot front, a bow shock, and a hole punched in the cloud deck. */
    private static void descendingFront(double x, double y, double z, double age) {
        double head = RailgunVisualTiming.headHeight(age), near = 1 - head / RailgunVisualTiming.DESCENT_HEIGHT;
        if (age < RailgunVisualTiming.IMPACT + 1) {
            float alpha = (float) (RailgunCameraPath.smooth((age - RailgunVisualTiming.SKY + 2) / 2)
                    * (1 - RailgunCameraPath.smooth(age - RailgunVisualTiming.IMPACT)));
            double front = y + head;
            RailgunCinema.beam(x, front, z, x, y + 1900, z, 1.4 + near * 2.4, 0xFF3158, alpha);
            EffectMesh.billboard(x, front, z, 14 + near * 34, 0xFF3158, alpha);
            for (int k = 0; k < 3; k++) {
                double d = 5 + k * 9 + (age * 4) % 9;
                EffectMesh.glowRing(x, front + d, z, 2 + d * (0.45 + near * 0.3), 0.6 + near * 0.6, 0xFF547C,
                        alpha * (1 - k / 3F) * 0.7F);
            }
        }
        double cloud = Minecraft.getMinecraft().world.provider.getCloudHeight();
        if (cloud <= y + 2) return;
        double since = age - RailgunVisualTiming.ageAtHeight(cloud - y);
        if (since <= 0 || since > 16) return;
        float fade = (float) (1 - since / 16);
        EffectMesh.glowRing(x, cloud, z, 4 + since * 15, 2 + since * 0.5, 0xFFD5DF, fade * 0.8F);
        EffectMesh.glowRing(x, cloud + 1, z, 2 + since * 9, 1.2, 0xFF3158, fade * 0.6F);
    }

    /** Energy keeps pouring down the column as bright bands sliding toward the ground. */
    private static void columnPulses(double x, double y, double z, double radius, double impact, float alpha) {
        for (int k = 0; k < 5; k++) {
            double h = 700 - ((impact * 14 + k * 140) % 700);
            float fade = alpha * 0.35F * (float) (RailgunCameraPath.smooth(h / 30) * (1 - h / 700));
            EffectMesh.glowRing(x, y + h, z, roundedRadius(radius, h), 3 + h * 0.01, 0xFF6A88, fade);
        }
    }

    /** Plasma embers climbing the inside of the wall. */
    private static void wallEmbers(double x, double y, double z, double radius, double impact, float alpha) {
        GL11.glPointSize(2.6F);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 220; i++) {
            double phase = (impact * 0.03 + i * 0.6180339) % 1, a = i * 2.39996323 + phase * 0.05;
            double h = phase * (24 + (i % 7) * 11), r = roundedRadius(radius, h) * (0.985 - (i % 5) * 0.008);
            float fade = alpha * 0.8F * (float) Math.pow(Math.sin(phase * Math.PI), 2);
            EffectMesh.vertex(b, x + Math.cos(a) * r, y + h, z + Math.sin(a) * r, i % 4 == 0 ? 0xFFF3F5 : 0xFF5A78, fade);
        }
        Tessellator.getInstance().draw();
    }

    /** Ground shock fronts, always leading the opening column edge. */
    private static void impactShocks(double x, double y, double z, double impact, int rgb) {
        for (int ring = 0; ring < 3; ring++) {
            double t = impact - ring * 3;
            if (t <= 0 || t > 40) continue;
            double r = 200 * RailgunVisualTiming.blossom(RailgunVisualTiming.IMPACT + t)
                    + 220 * (1 - Math.exp(-t * 0.08)) * (1 - ring * 0.2);
            float alpha = (float) (Math.pow(1 - t / 40, 1.5) * (ring == 0 ? 0.9 : 0.5));
            EffectMesh.glowRing(x, y + 0.8 + ring * 0.4, z, r, 3 + t * 0.12, ring == 0 ? 0xFFE6EE : rgb, alpha);
        }
    }

    private static void railgunCurtain(double x, double bottom, double z, double radius, double age, float alpha) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[] heights = {0, 8, 20, 40, 70, 140, 500, 1800};
        for (int i = 0; i < 128; i++) {
            double a = i * Math.PI / 64, n = (i + 1) * Math.PI / 64;
            for (int band = 0; band < heights.length - 1; band++) {
                curtainVertex(b, x, bottom, z, radius, a, heights[band], age, alpha);
                curtainVertex(b, x, bottom, z, radius, n, heights[band], age, alpha);
                curtainVertex(b, x, bottom, z, radius, n, heights[band + 1], age, alpha);
                curtainVertex(b, x, bottom, z, radius, a, heights[band + 1], age, alpha);
            }
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private static void curtainVertex(BufferBuilder b, double x, double bottom, double z, double radius, double angle,
                                      double height, double age, float alpha) {
        double pulse = 0.5 + 0.5 * Math.sin(angle * 6 + age * 0.035 - height * 0.001);
        int red = (int) (145 + pulse * 70), green = (int) (2 + pulse * 5), blue = (int) (20 + pulse * 15);
        double heat = Math.exp(-height / 18);
        red += (int) ((255 - red) * heat); green += (int) ((62 - green) * heat); blue += (int) ((83 - blue) * heat);
        double r = roundedRadius(radius, height);
        EffectMesh.vertex(b, x + Math.cos(angle) * r, bottom + height, z + Math.sin(angle) * r,
                red << 16 | green << 8 | blue, alpha * (float) (0.8 + pulse * 0.08));
    }

    private static double roundedRadius(double radius, double height) {
        double h = Math.min(1, height / 70);
        return radius * Math.sqrt(h * (2 - h));
    }

    private static void curtainFilaments(double x, double bottom, double z, double radius, double age, float alpha) {
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.sin(age * 0.025 + i) * 0.015;
            double r = radius * (0.96 + Math.sin(i * 6.1) * 0.015);
            worldBeam(x + Math.cos(a) * r, z + Math.sin(a) * r, bottom + 70,
                    0.12 + Math.sin(i * 4.1) * 0.03, 0xFF4761, alpha * 0.12F);
        }
    }

    private static void arrivalEnvelopes(double x, double y, double z, double age, boolean emission) {
        float alpha = (float) (RailgunCameraPath.smooth((age - RailgunVisualTiming.ARRIVAL) / 0.8)
                * (1 - RailgunCameraPath.smooth((age - RailgunVisualTiming.IMPACT) / 2)));
        if (alpha <= 0) return;
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        double facing = Math.atan2(camera.posZ - z, camera.posX - x);
        for (int shell = 0; shell < 2; shell++) {
            double radius = (shell == 0 ? 170 : 255) * RailgunCameraPath.mix(0.85, 1, RailgunCameraPath.smooth((age - 282) / 4));
            if (!emission) railgunCurtain(x, y, z, radius, age, alpha * 0.52F);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int side = -1; side <= 1; side += 2) {
                double angle = facing + side * Math.PI / 2;
                for (int segment = 0; segment < 48; segment++) {
                    double h0 = segment < 16 ? segment * 70.0 / 16 : 70 + (segment - 16) * 1730.0 / 32;
                    double h1 = segment < 16 ? (segment + 1) * 70.0 / 16 : 70 + (segment - 15) * 1730.0 / 32;
                    double r0 = roundedRadius(radius, h0), r1 = roundedRadius(radius, h1);
                    EffectMesh.vertex(b, x + Math.cos(angle) * (r0 - 2.5), y + h0, z + Math.sin(angle) * (r0 - 2.5), 0xFFF1F5, alpha * 0.85F);
                    EffectMesh.vertex(b, x + Math.cos(angle) * (r0 + 2.5), y + h0, z + Math.sin(angle) * (r0 + 2.5), 0xFFF1F5, alpha * 0.85F);
                    EffectMesh.vertex(b, x + Math.cos(angle) * (r1 + 2.5), y + h1, z + Math.sin(angle) * (r1 + 2.5), 0xFFF1F5, alpha * 0.85F);
                    EffectMesh.vertex(b, x + Math.cos(angle) * (r1 - 2.5), y + h1, z + Math.sin(angle) * (r1 - 2.5), 0xFFF1F5, alpha * 0.85F);
                }
            }
            Tessellator.getInstance().draw();
        }
        worldBeam(x, z, y, 1.5, 0xFF3857, alpha);
    }

    private double groundHeight(double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!groundLoaded(x, z)) return Double.NaN;
        if (!renderedColumns.contains(sectionPos.setPos(bx >> 4 << 4, 0, bz >> 4 << 4))) return Double.NaN;
        BlockPos.MutableBlockPos pos = surfacePos.setPos(bx, mc.world.getHeight(bx, bz) - 1, bz);
        IBlockState state = mc.world.getBlockState(pos);
        while (pos.getY() > 0 && !state.getMaterial().isSolid() && !state.getMaterial().isLiquid()) {
            pos.setPos(bx, pos.getY() - 1, bz); state = mc.world.getBlockState(pos);
        }
        BlockPos section = sectionPos.setPos(bx >> 4 << 4, pos.getY() >> 4 << 4, bz >> 4 << 4);
        // Chunk data alone does not mean terrain was submitted in this render pass.
        if (!renderedGround.contains(section)) return Double.NaN;
        if (state.getMaterial().isLiquid()) return pos.getY() + 1.02;
        AxisAlignedBB box = state.getBoundingBox(mc.world, pos);
        if (box == null || !state.getMaterial().isSolid() || box.minX > 0 || box.minZ > 0 || box.maxX < 1 || box.maxZ < 1)
            return Double.NaN;
        return pos.getY() + box.maxY + 0.02;
    }

    private static boolean groundLoaded(double x, double z) {
        return Minecraft.getMinecraft().world.getChunkProvider()
                .getLoadedChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4) != null;
    }

    private void collectGroundSurfaces(LaunchState state, double age) {
        GroundSurfaces surfaces = groundSurfaces.get(state);
        if (surfaces != null) surfaces.clear();
        if (age < RailgunVisualTiming.IMPACT || hazardAlpha(age) <= 0 || renderedColumns.isEmpty()) return;
        if (surfaces == null) {
            surfaces = new GroundSurfaces();
            groundSurfaces.put(state, surfaces);
        }
        double x = state.getTarget().getX() + 0.5, z = state.getTarget().getZ() + 0.5;
        boolean diskVisible = RailgunVisualTiming.residue(age) > 0;
        Minecraft mc = Minecraft.getMinecraft();
        for (BlockPos column : renderedColumns) {
            double nearestX = Math.max(column.getX() - x, Math.max(0, x - column.getX() - 16));
            double nearestZ = Math.max(column.getZ() - z, Math.max(0, z - column.getZ() - 16));
            double farthestX = Math.max(Math.abs(column.getX() - x), Math.abs(column.getX() + 16 - x));
            double farthestZ = Math.max(Math.abs(column.getZ() - z), Math.abs(column.getZ() + 16 - z));
            if (nearestX * nearestX + nearestZ * nearestZ > 203.55 * 203.55
                    || (!diskVisible && farthestX * farthestX + farthestZ * farthestZ < 196.45 * 196.45)
                    || mc.world.getChunkProvider().getLoadedChunk(column.getX() >> 4, column.getZ() >> 4) == null) continue;
            for (int bx = column.getX(); bx < column.getX() + 16; bx++) {
                for (int bz = column.getZ(); bz < column.getZ() + 16; bz++) {
                    double dx = bx + 0.5 - x, dz = bz + 0.5 - z, distanceSq = dx * dx + dz * dz;
                    boolean disk = diskVisible && distanceSq <= 200.75 * 200.75;
                    boolean rim = distanceSq >= 196.45 * 196.45 && distanceSq <= 203.55 * 203.55;
                    boolean warm = diskVisible && distanceSq >= 198.35 * 198.35 && distanceSq <= 202.65 * 202.65;
                    // Annulus rejection precedes all block-state/height queries; each eligible surface is resolved once.
                    if (!disk && !rim && !warm) continue;
                    double height = groundHeight(bx + 0.5, 0, bz + 0.5);
                    if (!Double.isFinite(height)) continue;
                    if (disk) surfaces.disk.add(bx, bz, height);
                    if (rim) surfaces.rim.add(bx, bz, height);
                    if (warm) surfaces.warmRim.add(bx, bz, height);
                }
            }
        }
    }

    private static void drawGroundSurfaces(SurfaceList surfaces, double x, double z, double radius, double width, int rgb, float alpha) {
        if (surfaces.size == 0) return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < surfaces.size; i++) {
            int bx = surfaces.x[i], bz = surfaces.z[i];
            double height = surfaces.height[i];
            groundVertex(b, bx, height, bz, x, z, radius, width, rgb, alpha);
            groundVertex(b, bx, height, bz + 1, x, z, radius, width, rgb, alpha);
            groundVertex(b, bx + 1, height, bz + 1, x, z, radius, width, rgb, alpha);
            groundVertex(b, bx + 1, height, bz, x, z, radius, width, rgb, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private static final class GroundSurfaces {
        final SurfaceList disk = new SurfaceList(), rim = new SurfaceList(), warmRim = new SurfaceList();
        void clear() { disk.size = 0; rim.size = 0; warmRim.size = 0; }
    }

    private static final class SurfaceList {
        int size;
        int[] x = new int[256], z = new int[256];
        double[] height = new double[256];
        void add(int bx, int bz, double y) {
            if (size == x.length) {
                x = Arrays.copyOf(x, size * 2); z = Arrays.copyOf(z, size * 2);
                height = Arrays.copyOf(height, size * 2);
            }
            x[size] = bx; z[size] = bz; height[size] = y;
            size++;
        }
    }

    private static void groundVertex(BufferBuilder b, double px, double py, double pz, double x, double z,
                                     double radius, double width, int rgb, float alpha) {
        double distance = Math.hypot(px - x, pz - z);
        EffectMesh.vertex(b, px, py, pz, rgb, alpha * (float) groundAlpha(distance, radius, width));
    }

    static double groundAlpha(double distance, double radius, double width) {
        return width == 0 ? 1 - RailgunCameraPath.smooth((distance - radius + 1.5) / 1.5)
                : 1 - RailgunCameraPath.smooth(Math.abs(distance - radius) / (width * 2));
    }

    private void residualSparks(double x, double y, double z, double age, float alpha) {
        GL11.glPointSize(2.4F);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 28; i++) {
            double phase = (age * 0.015 + i * 0.6180339) % 1;
            double angle = i * 2.39996323, r = 186 + (i % 7) * 3;
            double px = x + Math.cos(angle) * r, pz = z + Math.sin(angle) * r;
            if (!groundLoaded(px, pz)) continue;
            double height = groundHeight(px, y, pz);
            if (!Double.isFinite(height)) continue;
            float fade = alpha * (float) Math.pow(Math.sin(phase * Math.PI), 2) * 0.65F;
            EffectMesh.vertex(b, px, height + 1 + phase * 9, pz,
                    i % 4 == 0 ? 0xFFF3F5 : 0xFF829A, fade);
        }
        Tessellator.getInstance().draw();
    }

    private static void worldBeam(double x, double z, double bottom, double radius, int rgb, float alpha) {
        if (alpha <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        double partial = mc.getRenderPartialTicks();
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        double distance = Math.hypot(cx - x, cz - z);
        double visibility = RailgunCameraPath.smooth(distance / (radius * 6 + 1.5));
        // A player inside the emitter must still see the world after leaving the film.
        RailgunCinema.beam(x, bottom, z, x, 1800, z, radius, rgb, alpha * (float) (0.02 + visibility * 0.98));
    }
}
