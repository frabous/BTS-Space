package fr.frabous.stellar.client;

import static fr.frabous.stellar.api.GravityInversionRules.*;
import static fr.frabous.stellar.client.GravityInversionVisuals.*;

import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.LaunchState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * SS-05 in the world, for every client: glowing fissures, rising debris, illusory terrain fragments torn out of
 * the ground and spun around an invisible axis, then the collapse and its ground shockwave. Everything derives
 * from the launch seed; the fragments copy the blocks found under their anchors.
 */
final class GravityInversionScene {
    private static final int CORE = 0xFFE4FA, DUST = 0x5C4B3C;
    private static final int[] EARTH = {0x6B4F36, 0x777777, 0x5E8A3A, 0x4A3A2C};
    private static final double STEP = 2.6, CELL = 6, SPAN = SHOCK_RADIUS + 12;
    private static final int MOTES = 320, DEBRIS = 90, STREAKS = 140;
    private static final Map<UUID, Cache> CACHES = new HashMap<>();

    private GravityInversionScene() { }

    static void draw(LaunchState launch, double age, boolean emission) {
        Minecraft mc = Minecraft.getMinecraft();
        if (age < 0 || age >= END || mc.world == null || mc.getRenderViewEntity() == null) return;
        Cache c = cache(launch, mc.world.getTotalWorldTime());
        boolean ownFilm = mc.currentScreen instanceof GravityInversionCinema
                && ((GravityInversionCinema) mc.currentScreen).matches(launch.getLaunchId());
        boolean otherFilm = !ownFilm && (mc.currentScreen instanceof RailgunCinema || mc.currentScreen instanceof AsteroidPullerCinema
                || mc.currentScreen instanceof SupernovaCinema || mc.currentScreen instanceof GravityInversionCinema);
        if (!emission) {
            if (!ownFilm && !otherFilm) witness(c, age);
            // Opaque bodies first, so luminous geometry in front of them is not painted over.
            debris(c, age);
            fragments(c, age);
            holes(c, age);
            entities(launch, age);
        }
        perimeter(c, age);
        fissures(c, age);
        motes(c, age);
        glows(c, age);
        axisRings(c, age);
        flips(c, age);
        slam(c, age);
        impact(c, age, emission);
    }

    private static Cache cache(LaunchState launch, long now) {
        Cache c = CACHES.get(launch.getLaunchId());
        if (c == null) {
            CACHES.values().removeIf(old -> now - old.used > 200 || now < old.used);
            c = new Cache(launch);
            CACHES.put(launch.getLaunchId(), c);
        }
        c.used = now;
        return c;
    }

    /** Witnesses outside the film get the flip pops, the held breath and the collapse flash on their own screen. */
    private static void witness(Cache c, double age) {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        double near = 1 - smooth((Math.hypot(view.posX - c.cx, view.posZ - c.cz) - RADIUS) / (RADIUS * 2));
        if (near <= 0) return;
        int k = flip(age);
        if (k >= 0) SupernovaScene.overlay(MAGENTA, (float) (0.16 * near * Math.exp(-(age - ROTATION - k * FLIP) * 0.5)));
        if (age >= COLLAPSE && age < SLAM) SupernovaScene.overlay(VOID, (float) (0.3 * near * smooth((age - COLLAPSE) / 2)));
        if (age >= IMPACT) SupernovaScene.overlay(WHITE, (float) (0.8 * near * Math.exp(-(age - IMPACT) * 0.3)));
    }

    /** Fissure front: cracks race out from the aim to the edge of the field during the gravity failure. */
    private static double front(double age) { return RADIUS * 1.04 * Math.pow(smooth((age - 2) / 62), 0.65); }

    private static double crackGlow(double age) {
        if (age < ASCENSION) return 1;
        if (age < ROTATION) return 0.9;
        if (age < COLLAPSE) return 0.9 + 0.9 * Math.exp(-(age - ROTATION - flip(age) * FLIP) * 0.35);
        if (age < SLAM) return 1.5;
        if (age < IMPACT) return 1.1;
        double t = age - IMPACT;
        return 2.6 * Math.exp(-t * 0.12) + 0.6 * (1 - smooth(t / (END - IMPACT)));
    }

    private static void fissures(Cache c, double age) {
        double front = front(age), glow = crackGlow(age);
        if (front <= 0) return;
        double burst = age >= IMPACT ? 7 * Math.exp(-(age - IMPACT) * 0.15) : 0;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (double[] s : c.cracks) {
            if (s[4] > front) continue;
            double tip = Math.exp(-(front - s[4]) / 8);
            float a = (float) Math.min(1, glow * (0.5 + 0.5 * tip));
            double x0 = c.cx + s[0], z0 = c.cz + s[1], x1 = c.cx + s[2], z1 = c.cz + s[3];
            double y0 = c.crack(s, 6, x0, z0) + 0.05, y1 = c.crack(s, 7, x1, z1) + 0.05;
            double dx = x1 - x0, dz = z1 - z0, length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1e-6) continue;
            double nx = -dz / length * s[5] * 0.5, nz = dx / length * s[5] * 0.5;
            softRibbon(b, x0, y0, z0, x1, y1, z1, nx * 4, nz * 4, MAGENTA, a * 0.45F);
            softRibbon(b, x0, y0 + 0.01, z0, x1, y1 + 0.01, z1, nx, nz, CORE, a);
            double sheet = 0.5 + 2.2 * tip + burst;
            EffectMesh.vertex(b, x0, y0, z0, MAGENTA, a * 0.5F);
            EffectMesh.vertex(b, x1, y1, z1, MAGENTA, a * 0.5F);
            EffectMesh.vertex(b, x1, y1 + sheet, z1, MAGENTA, 0);
            EffectMesh.vertex(b, x0, y0 + sheet, z0, MAGENTA, 0);
        }
        Tessellator.getInstance().draw();
    }

    /** Flat ribbon bright along its spine and transparent at both edges. */
    private static void softRibbon(BufferBuilder b, double x0, double y0, double z0, double x1, double y1, double z1,
                                   double ox, double oz, int rgb, float alpha) {
        EffectMesh.vertex(b, x0 - ox, y0, z0 - oz, rgb, 0);
        EffectMesh.vertex(b, x0, y0, z0, rgb, alpha);
        EffectMesh.vertex(b, x1, y1, z1, rgb, alpha);
        EffectMesh.vertex(b, x1 - ox, y1, z1 - oz, rgb, 0);
        EffectMesh.vertex(b, x0, y0, z0, rgb, alpha);
        EffectMesh.vertex(b, x0 + ox, y0, z0 + oz, rgb, 0);
        EffectMesh.vertex(b, x1 + ox, y1, z1 + oz, rgb, 0);
        EffectMesh.vertex(b, x1, y1, z1, rgb, alpha);
    }

    /** Sparks leaking up out of the cracks, swept into the rotation and frozen by the collapse. */
    private static void motes(Cache c, double age) {
        int n = c.cracks.size();
        if (age < 8 || age >= IMPACT || n == 0) return;
        double front = front(age), hold = held(age);
        GL11.glPointSize(2.8F);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < MOTES; i++) {
            double[] s = c.cracks.get((int) (unit(c.seed, 20000 + i) * n));
            if (s[4] > front) continue;
            double px = s[0] + (unit(c.seed, 21000 + i) - 0.5) * 3, pz = s[1] + (unit(c.seed, 22000 + i) - 0.5) * 3;
            double g = c.crack(s, 6, c.cx + s[0], c.cz + s[1]);
            double speed = 0.06 + 0.1 * unit(c.seed, 23000 + i), height = 10 + 40 * unit(c.seed, 24000 + i);
            double phase = (hold * speed * (1 + 1.5 * lift(hold)) / height + unit(c.seed, 25000 + i)) % 1;
            double y = g + phase * height;
            if (age >= SLAM) {
                double t = Math.min(1, (age - SLAM) / (IMPACT - SLAM));
                y += (g + 0.3 - y) * t * t;
            }
            double theta = orbit(Math.min(age, SLAM)) * 0.5 * phase, cos = Math.cos(theta), sin = Math.sin(theta);
            EffectMesh.vertex(b, c.cx + px * cos - pz * sin, y, c.cz + px * sin + pz * cos,
                    i % 5 == 0 ? WHITE : i % 2 == 0 ? MAGENTA : CYAN, (float) Math.sin(phase * Math.PI) * 0.9F);
        }
        Tessellator.getInstance().draw();
    }

    /** Animation clock that nearly stops while the world hangs before the slam. */
    private static double held(double age) {
        return age < COLLAPSE ? age : COLLAPSE + (Math.min(age, SLAM) - COLLAPSE) * 0.04 + Math.max(0, age - SLAM) * 2.5;
    }

    /** Small rocks hopping out of the cracks, lifted, orbited and thrown back down. */
    private static void debris(Cache c, double age) {
        int n = c.cracks.size();
        if (age < 6 || age >= IMPACT + 1 || n == 0) return;
        double front = front(age), spin = held(age);
        double[] k = axis(c.seed);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < DEBRIS; i++) {
                double[] s = c.cracks.get((int) (unit(c.seed, 30000 + i) * n));
                double start = Math.max(6 + unit(c.seed, 31000 + i) * 60, s[4] / RADIUS * 64);
                if (s[4] > front || age < start) continue;
                double size = 0.25 + Math.pow(unit(c.seed, 33000 + i), 2) * 1.1;
                double g = c.crack(s, 6, c.cx + s[0], c.cz + s[1]) - c.aimY;
                double u = unit(c.seed, 34000 + i);
                double y = g + size * 0.5 - 0.4 + smooth((age - start) / 40) * (1 + 3 * u)
                        + lift(age) * (6 + 40 * unit(c.seed, 35000 + i)) + Math.sin(spin * 0.08 + i) * 0.5;
                double[] p = {s[0] + (unit(c.seed, 32000 + i) - 0.5) * 4, y, s[1] + (unit(c.seed, 36000 + i) - 0.5) * 4};
                double theta = orbit(Math.min(age, SLAM)) * (0.6 + 0.4 * u);
                if (theta != 0) p = turn(p, k, theta);
                p[1] = Math.max(p[1], g + size);
                if (age >= SLAM) {
                    double t = Math.min(1, (age - SLAM) / (IMPACT - SLAM));
                    p[1] += (g + size * 0.5 - p[1]) * t * t;
                }
                cube(b, c.cx + p[0], c.aimY + p[1], c.cz + p[2], size, spin * (0.05 + 0.1 * u) + i,
                        spin * (0.04 + 0.08 * unit(c.seed, 37000 + i)), EARTH[i % EARTH.length]);
            }
            Tessellator.getInstance().draw();
        } finally { GL11.glPopAttrib(); }
    }

    /** Rotates a point relative to the aim around the invisible axis through AXIS_HEIGHT (Rodrigues). */
    private static double[] turn(double[] p, double[] k, double theta) {
        double px = p[0], py = p[1] - AXIS_HEIGHT, pz = p[2];
        double cos = Math.cos(theta), sin = Math.sin(theta), dot = k[0] * px + k[1] * py + k[2] * pz;
        return new double[] {px * cos + (k[1] * pz - k[2] * py) * sin + k[0] * dot * (1 - cos),
                AXIS_HEIGHT + py * cos + (k[2] * px - k[0] * pz) * sin + k[1] * dot * (1 - cos),
                pz * cos + (k[0] * py - k[1] * px) * sin + k[2] * dot * (1 - cos)};
    }

    private static final double[][] CUBE_FACES = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Tumbling shaded cube; yaw around Y after pitch around X. */
    private static void cube(BufferBuilder b, double x, double y, double z, double size, double yaw, double pitch, int rgb) {
        double h = size * 0.5, cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        for (double[] n : CUBE_FACES) {
            // Two tangents spanning the face, then the four corners in winding order.
            double[] t1 = n[1] != 0 ? new double[] {1, 0, 0} : new double[] {0, 1, 0};
            double[] t2 = {n[1] * t1[2] - n[2] * t1[1], n[2] * t1[0] - n[0] * t1[2], n[0] * t1[1] - n[1] * t1[0]};
            double ny = n[1] * cp - n[2] * sp;
            double shade = 0.55 + 0.45 * (ny * 0.5 + 0.5);
            int color = scale(rgb, shade);
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 0 || corner == 3 ? -1 : 1, d = corner < 2 ? -1 : 1;
                double lx = (n[0] + t1[0] * a + t2[0] * d) * h, ly = (n[1] + t1[1] * a + t2[1] * d) * h;
                double lz = (n[2] + t1[2] * a + t2[2] * d) * h;
                double ry = ly * cp - lz * sp, rz = ly * sp + lz * cp;
                EffectMesh.vertex(b, x + lx * cy + rz * sy, y + ry, z - lx * sy + rz * cy, color, 1);
            }
        }
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.min(255, (rgb >> 16 & 255) * factor), g = (int) Math.min(255, (rgb >> 8 & 255) * factor);
        return r << 16 | g << 8 | (int) Math.min(255, (rgb & 255) * factor);
    }

    /** Illusory terrain fragments: block-textured chunks of the ground under each anchor. */
    private static void fragments(Cache c, double age) {
        if (age < ASCENSION) return;
        Minecraft mc = Minecraft.getMinecraft();
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glEnable(GL11.GL_ALPHA_TEST); GL11.glAlphaFunc(GL11.GL_GREATER, 0.05F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(true);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            for (int i = 0; i < FRAGMENTS; i++) {
                if (age < tear(c.seed, i)) continue;
                double[] f = fragment(c.seed, i, age, c.fragmentGround(i));
                if (f[5] <= 0.01) continue;
                float[] mesh = c.mesh(i);
                GL11.glPushMatrix();
                try {
                    GL11.glTranslated(c.cx + f[0], c.aimY + f[1], c.cz + f[2]);
                    GL11.glRotated(Math.toDegrees(f[3]), 0, 1, 0);
                    GL11.glRotated(Math.toDegrees(f[4]), 1, 0, 0);
                    b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                    for (int v = 0; v < mesh.length; v += 8) {
                        b.pos(mesh[v], mesh[v + 1], mesh[v + 2]).tex(mesh[v + 3], mesh[v + 4])
                                .color(mesh[v + 5], mesh[v + 6], mesh[v + 7], (float) f[5]).endVertex();
                    }
                    Tessellator.getInstance().draw();
                } finally { GL11.glPopMatrix(); }
            }
        } finally {
            GlStateManager.bindTexture(binding);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    /** The torn-out ground leaves dark glowing scars until the fragments come back down. */
    private static void holes(Cache c, double age) {
        if (age < ASCENSION) return;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        double[] alphas = new double[FRAGMENTS];
        for (int i = 0; i < FRAGMENTS; i++) {
            alphas[i] = smooth((age - tear(c.seed, i)) / 12) * (1 - smooth((age - IMPACT) / 3));
            if (alphas[i] <= 0) continue;
            double[] anchor = anchor(c.seed, i);
            double x = c.cx + anchor[0], z = c.cz + anchor[1], y = c.aimY + c.fragmentGround(i) + 0.04, r = anchor[2] * 0.42;
            for (int s = 0; s < 16; s++) {
                double a0 = s * Math.PI / 8, a1 = (s + 1) * Math.PI / 8;
                EffectMesh.vertex(b, x, y, z, VOID, (float) alphas[i] * 0.9F);
                EffectMesh.vertex(b, x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, VOID, (float) alphas[i] * 0.6F);
                EffectMesh.vertex(b, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, VOID, (float) alphas[i] * 0.6F);
            }
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int i = 0; i < FRAGMENTS; i++) {
            if (alphas[i] <= 0) continue;
            double[] anchor = anchor(c.seed, i);
            EffectMesh.glowRing(c.cx + anchor[0], c.aimY + c.fragmentGround(i) + 0.08, c.cz + anchor[1],
                    anchor[2] * 0.45, 0.9, MAGENTA, (float) alphas[i] * 0.8F);
        }
    }

    /** Captured bodies glow and leave streaks along their motion; the server moves them, this only marks them. */
    private static void entities(LaunchState launch, double age) {
        if (age < 20 || age >= IMPACT) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        float partial = mc.getRenderPartialTicks();
        BlockTarget target = launch.getTarget();
        List<double[]> marks = new ArrayList<>();
        for (Entity e : mc.world.loadedEntityList) {
            if (e == view || e.isDead || e.onGround) continue;
            boolean living = e instanceof EntityLivingBase;
            if (!living && !(e instanceof IProjectile) && !(e instanceof EntityItem)) continue;
            if (living && age < ASCENSION) continue;
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * partial;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * partial + e.height * 0.5;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial;
            if (!contains(target, x, y, z)) continue;
            marks.add(new double[] {x, y, z, e.width, e.posX - e.lastTickPosX, e.posY - e.lastTickPosY, e.posZ - e.lastTickPosZ});
            if (marks.size() >= 160) break;
        }
        if (marks.isEmpty()) return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] m : marks) {
            EffectMesh.vertex(b, m[0], m[1], m[2], CYAN, 0.7F);
            EffectMesh.vertex(b, m[0] - m[4] * 7, m[1] - m[5] * 7, m[2] - m[6] * 7, CYAN, 0);
        }
        Tessellator.getInstance().draw();
        for (double[] m : marks) EffectMesh.billboard(m[0], m[1], m[2], 0.8 + m[3] * 1.3, MAGENTA, 0.5F);
    }

    /** Fragment underglow, tethers to the axis during the rotation, and slam streaks. */
    private static void glows(Cache c, double age) {
        if (age < ASCENSION || age >= SHOCK) return;
        double[] k = axis(c.seed);
        double tether = smooth((age - ROTATION) / 12) * (1 - smooth((age - COLLAPSE) / 4));
        boolean slamming = age >= SLAM && age < IMPACT;
        double ox = c.cx, oy = c.aimY + AXIS_HEIGHT, oz = c.cz;
        double[][] bodies = new double[FRAGMENTS][];
        for (int i = 0; i < FRAGMENTS; i++) {
            double tear = tear(c.seed, i);
            if (age < tear) continue;
            double[] f = fragment(c.seed, i, age, c.fragmentGround(i));
            double size = anchor(c.seed, i)[2], rise = smooth((age - tear) / 55) * f[5];
            bodies[i] = new double[] {c.cx + f[0], c.aimY + f[1], c.cz + f[2], size, rise};
            EffectMesh.billboard(c.cx + f[0], c.aimY + f[1] - size * 0.45, c.cz + f[2], size * 0.9, MAGENTA, (float) (0.28 * rise));
        }
        if (tether <= 0 && !slamming) return;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : bodies) {
            if (p == null) continue;
            if (tether > 0) {
                double t = (p[0] - ox) * k[0] + (p[1] - oy) * k[1] + (p[2] - oz) * k[2];
                EffectMesh.vertex(b, p[0], p[1], p[2], MAGENTA, (float) (0.2 * tether * p[4]));
                EffectMesh.vertex(b, ox + k[0] * t, oy + k[1] * t, oz + k[2] * t, CYAN, 0);
            }
            if (slamming) {
                for (int corner = 0; corner < 4; corner++) {
                    double a = corner * Math.PI / 2 + 0.6, r = p[3] * 0.4;
                    double x = p[0] + Math.cos(a) * r, z = p[2] + Math.sin(a) * r;
                    EffectMesh.vertex(b, x, p[1], z, WHITE, 0.7F);
                    EffectMesh.vertex(b, x, p[1] + 34, z, MAGENTA, 0);
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    /** Broken gyroscope arcs in the plane of the rotation make the invisible axis readable. */
    private static void axisRings(Cache c, double age) {
        double show = smooth((age - ROTATION + 16) / 16) * (1 - smooth((age - COLLAPSE) / 6));
        if (show <= 0) return;
        double[] k = axis(c.seed);
        double ox = c.cx, oy = c.aimY + AXIS_HEIGHT, oz = c.cz, down = AXIS_HEIGHT / k[1];
        int f = flip(age);
        double pop = f < 0 ? 0 : Math.exp(-(age - ROTATION - f * FLIP) * 0.25);
        RailgunCinema.beam(ox - k[0] * down, oy - k[1] * down, oz - k[2] * down, ox + k[0] * 170, oy + k[1] * 170,
                oz + k[2] * 170, 0.25 + pop, WHITE, (float) (show * (0.12 + 0.55 * pop)));
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(ox, oy, oz);
            alignY(k);
            double theta = orbit(age);
            double[] radii = {22, 48, 82, 124};
            for (int j = 0; j < radii.length; j++) {
                double spin = theta * (1 + j * 0.25) * (j % 2 == 0 ? 1 : -1);
                float alpha = (float) (show * (0.35 + 0.5 * pop) * (1 - j * 0.15));
                for (int s = 0; s < 3; s++) {
                    double a0 = spin + s * Math.PI * 2 / 3 + j;
                    EffectMesh.arc(0, 0, 0, radii[j], 0.6 + pop * 1.2 + j * 0.2, a0, a0 + 1.4, j % 2 == 0 ? MAGENTA : CYAN, alpha);
                }
            }
        } finally { GL11.glPopMatrix(); }
    }

    /** Rotates the current frame so its Y axis points along a unit direction. */
    private static void alignY(double[] d) {
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d[1]))));
        if (Math.abs(d[0]) + Math.abs(d[2]) < 1e-6) {
            if (d[1] < 0) GL11.glRotated(180, 1, 0, 0);
            return;
        }
        GL11.glRotated(angle, d[2], 0, -d[0]);
    }

    /** Each gravity flip: a ripple across the new down and a sheet of streaks falling along it. */
    private static void flips(Cache c, double age) {
        int f = flip(age);
        if (f < 0) return;
        double into = age - ROTATION - f * FLIP;
        float a = (float) (Math.exp(-into * 0.12) * 0.75);
        double[] g = gravity(c.seed, f);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < STREAKS; i++) {
            int key = 40000 + f * 1000 + i * 6;
            double r = Math.sqrt(unit(c.seed, key)) * RADIUS * 0.85, angle = unit(c.seed, key + 1) * Math.PI * 2;
            double px = c.cx + Math.cos(angle) * r, pz = c.cz + Math.sin(angle) * r;
            double py = c.ground(px, pz) + 4 + unit(c.seed, key + 2) * 60;
            double travel = into * (2.4 + unit(c.seed, key + 3) * 1.5), length = 5 + unit(c.seed, key + 4) * 16;
            double hx = px + g[0] * travel, hy = py + g[1] * travel, hz = pz + g[2] * travel;
            int rgb = i % 3 == 0 ? WHITE : i % 3 == 1 ? CYAN : MAGENTA;
            EffectMesh.vertex(b, hx, hy, hz, rgb, a);
            EffectMesh.vertex(b, hx - g[0] * length, hy - g[1] * length, hz - g[2] * length, rgb, 0);
        }
        Tessellator.getInstance().draw();
        if (into >= 16) return;
        double t = into / 16;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(c.cx, c.aimY + AXIS_HEIGHT * 0.6, c.cz);
            alignY(g);
            EffectMesh.glowRing(0, 0, 0, 12 + t * 190, 2 + t * 4, CYAN, (float) ((1 - t) * 0.7));
            EffectMesh.glowRing(0, 0, 0, Math.max(0, 6 + t * 150), 1.2, WHITE, (float) ((1 - t) * 0.5));
        } finally { GL11.glPopMatrix(); }
    }

    /** The held breath charges a plate in the sky, which then crushes down onto the field. */
    private static void slam(Cache c, double age) {
        if (age >= COLLAPSE && age < SLAM) {
            float charge = (float) smooth((age - COLLAPSE) / (SLAM - COLLAPSE));
            EffectMesh.glowRing(c.cx, c.aimY + 112, c.cz, RADIUS * 0.98, 3, WHITE, charge * 0.6F);
            EffectMesh.glowRing(c.cx, c.aimY + 112, c.cz, RADIUS * 0.7, 2, MAGENTA, charge * 0.4F);
        }
        if (age < SLAM || age >= IMPACT + 2) return;
        double t = Math.min(1, (age - SLAM) / (IMPACT - SLAM));
        float out = (float) (age > IMPACT ? 1 - (age - IMPACT) / 2 : 1);
        for (int j = 0; j < 4; j++) {
            double y = c.aimY + 0.6 + (112 - j * 16) * (1 - t * t);
            EffectMesh.glowRing(c.cx, y, c.cz, RADIUS * (0.98 - j * 0.12), 3 + j, j == 0 ? WHITE : MAGENTA,
                    out * (1 - j * 0.2F) * 0.75F);
        }
    }

    /** Total collapse: a white core, a released gravity column and the ground shockwave racing past the field. */
    private static void impact(Cache c, double age, boolean emission) {
        if (age < IMPACT) return;
        double t = age - IMPACT, fade = 1 - smooth(t / (END - IMPACT));
        EffectMesh.billboard(c.cx, c.aimY + 8, c.cz, 50 + t * 6, WHITE, (float) Math.exp(-t * 0.2));
        EffectMesh.billboard(c.cx, c.aimY + 4, c.cz, 110 + t * 3, MAGENTA, (float) (0.55 * Math.exp(-t * 0.09)));
        RailgunCinema.beam(c.cx, c.aimY, c.cz, c.cx, c.aimY + 320, c.cz, 0.6 + 10 * Math.exp(-t * 0.14), MAGENTA,
                (float) (Math.exp(-t * 0.07) * 0.9));
        double r = shock(age);
        drapedRing(c, r, 4 + t * 0.15, 0.7, 2 + 16 * Math.exp(-t * 0.05), WHITE, (float) fade);
        drapedRing(c, r * 0.86, 2.5, 0.5, 4 * Math.exp(-t * 0.05), MAGENTA, (float) (fade * 0.6));
        double second = shock(age - 7);
        if (second > 0) drapedRing(c, second, 2.5, 0.5, 3, CYAN, (float) (fade * 0.45));
        EffectMesh.glowRing(c.cx, c.aimY + 0.3, c.cz, 14 + t * 0.4, 4, MAGENTA, (float) fade);
        if (!emission) dust(c, t, r, fade);
    }

    private static void dust(Cache c, double t, double r, double fade) {
        MoonAttractVfx.Sprites smoke = new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE, false);
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2 / 96 + unit(c.seed, 50000 + i) * 0.05;
            double rr = Math.max(0, r - 3 - unit(c.seed, 51000 + i) * 12);
            double x = c.cx + Math.cos(a) * rr, z = c.cz + Math.sin(a) * rr;
            double y = c.ground(x, z) + 2 + unit(c.seed, 52000 + i) * 4 + t * 0.05;
            double size = 6 + unit(c.seed, 53000 + i) * 6 + t * 0.12;
            smoke.add(x, y, z, size, size * 0.7, a + t * 0.01, DUST, (float) (fade * 0.55), i);
        }
        float settle = (float) (1 - smooth(t / 45));
        for (int i = 0; i < FRAGMENTS; i++) {
            double[] anchor = anchor(c.seed, i);
            double ground = c.aimY + c.fragmentGround(i);
            for (int j = 0; j < 4; j++) {
                double a = j * Math.PI / 2 + i, rr = anchor[2] * 0.5 + t * 0.25;
                double size = anchor[2] * 0.5 + t * 0.1;
                smoke.add(c.cx + anchor[0] + Math.cos(a) * rr, ground + 1.5 + t * 0.06, c.cz + anchor[1] + Math.sin(a) * rr,
                        size, size * 0.75, a, DUST, settle * 0.6F, i + j);
            }
        }
        smoke.draw();
    }

    /** Field boundary, draped on the terrain; it flares on every gravity flip. */
    private static void perimeter(Cache c, double age) {
        double mark = smooth(age / 24) * (1 - smooth((age - IMPACT) / 8));
        if (mark <= 0) return;
        int f = flip(age);
        double pop = f < 0 ? 0 : Math.exp(-(age - ROTATION - f * FLIP) * 0.3);
        drapedRing(c, RADIUS, 1.4 + pop * 2, 0.4, 1.5 + pop * 6, MAGENTA, (float) (mark * (0.45 + 0.5 * pop)));
    }

    /** Soft ring following the terrain, with an optional light wall fading upward from its spine. */
    private static void drapedRing(Cache c, double radius, double width, double lift, double wall, int rgb, float alpha) {
        if (alpha <= 0 || radius <= 0.5) return;
        int steps = 180;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double pc = 1, ps = 0, py = c.ground(c.cx + radius, c.cz) + lift;
        for (int i = 1; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps, cos = Math.cos(a), sin = Math.sin(a);
            double y = c.ground(c.cx + cos * radius, c.cz + sin * radius) + lift;
            double in = Math.max(0, radius - width), out = radius + width;
            EffectMesh.vertex(b, c.cx + pc * in, py, c.cz + ps * in, rgb, 0);
            EffectMesh.vertex(b, c.cx + cos * in, y, c.cz + sin * in, rgb, 0);
            EffectMesh.vertex(b, c.cx + cos * radius, y, c.cz + sin * radius, rgb, alpha);
            EffectMesh.vertex(b, c.cx + pc * radius, py, c.cz + ps * radius, rgb, alpha);
            EffectMesh.vertex(b, c.cx + pc * radius, py, c.cz + ps * radius, rgb, alpha);
            EffectMesh.vertex(b, c.cx + cos * radius, y, c.cz + sin * radius, rgb, alpha);
            EffectMesh.vertex(b, c.cx + cos * out, y, c.cz + sin * out, rgb, 0);
            EffectMesh.vertex(b, c.cx + pc * out, py, c.cz + ps * out, rgb, 0);
            if (wall > 0) {
                EffectMesh.vertex(b, c.cx + pc * radius, py, c.cz + ps * radius, rgb, alpha * 0.6F);
                EffectMesh.vertex(b, c.cx + cos * radius, y, c.cz + sin * radius, rgb, alpha * 0.6F);
                EffectMesh.vertex(b, c.cx + cos * radius, y + wall, c.cz + sin * radius, rgb, 0);
                EffectMesh.vertex(b, c.cx + pc * radius, py + wall, c.cz + ps * radius, rgb, 0);
            }
            pc = cos; ps = sin; py = y;
        }
        Tessellator.getInstance().draw();
    }

    /** Top of the terrain at a column, or NaN when its chunk is not loaded. */
    private static double surface(double x, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (mc.world.getChunkProvider().getLoadedChunk(bx >> 4, bz >> 4) == null) return Double.NaN;
        int top = mc.world.getHeight(bx, bz) - 1;
        if (top <= 0) return Double.NaN;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, top, bz);
        IBlockState state = mc.world.getBlockState(pos);
        int floor = Math.max(1, top - 48);
        while (pos.getY() > floor && (state.getMaterial() == Material.LEAVES
                || !state.getMaterial().blocksMovement() && !state.getMaterial().isLiquid())) {
            pos.setPos(bx, pos.getY() - 1, bz);
            state = mc.world.getBlockState(pos);
        }
        return pos.getY() + 1;
    }

    /** Branching cracks from the aim outward: {x0, z0, x1, z1, distance, width, y0, y1} relative to the aim. */
    private static List<double[]> cracks(long seed) {
        List<double[]> out = new ArrayList<>();
        Deque<double[]> pending = new ArrayDeque<>();
        int arms = 18, key = 1000;
        for (int arm = 0; arm < arms; arm++) {
            double a = (arm + unit(seed, key++) * 0.8) * Math.PI * 2 / arms, r0 = 1.5 + unit(seed, key++) * 3;
            pending.push(new double[] {Math.cos(a) * r0, Math.sin(a) * r0, a, RADIUS * (0.6 + 0.4 * unit(seed, key++)), 1.6, 1});
        }
        while (!pending.isEmpty() && out.size() < 4200) {
            double[] p = pending.pop();
            double x = p[0], z = p[1], a = p[2];
            int steps = (int) (p[3] / STEP);
            for (int s = 0; s < steps; s++) {
                a += (unit(seed, key++) - 0.5) * 0.6;
                double nx = x + Math.cos(a) * STEP, nz = z + Math.sin(a) * STEP;
                if (nx * nx + nz * nz > RADIUS * RADIUS) break;
                double width = p[4] * (1 - 0.7 * s / steps);
                out.add(new double[] {x, z, nx, nz, Math.sqrt(x * x + z * z), width, Double.NaN, Double.NaN});
                if (p[5] > 0 && unit(seed, key++) < 0.12) {
                    double side = unit(seed, key++) < 0.5 ? -1 : 1;
                    pending.push(new double[] {nx, nz, a + side * (0.5 + 0.5 * unit(seed, key++)),
                            (steps - s) * STEP * (0.2 + 0.25 * unit(seed, key++)), width * 0.65, 0});
                }
                x = nx; z = nz;
            }
        }
        return out;
    }

    /** Column depth of a fragment cell, 0 when the cell is outside its ragged outline. Deepest at the centre. */
    private static int depth(long seed, int i, int u, int v, double size) {
        double r = size / 2, d = Math.hypot(u + 0.5, v + 0.5);
        int key = 1_000_000 + i * 4096 + (u + 32) * 64 + (v + 32);
        if (d > r * (0.75 + 0.3 * unit(seed, key))) return 0;
        double core = Math.max(0, 1 - d / (r * 1.05));
        return Math.min(18, 1 + (int) (Math.pow(core, 0.85) * size * 0.8 * (0.7 + 0.6 * unit(seed, key + 2_000_000))));
    }

    private static float[] buildMesh(Cache c, int i, IBlockState[] layers, BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        Skin[] skins = new Skin[layers.length];
        for (int d = 0; d < layers.length; d++) skins[d] = new Skin(mc, layers[d], pos.down(d + 1));
        double size = anchor(c.seed, i)[2];
        int n = (int) Math.ceil(size / 2) + 1;
        MeshBuilder out = new MeshBuilder();
        int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int u = -n; u < n; u++) {
            for (int v = -n; v < n; v++) {
                int depth = depth(c.seed, i, u, v, size);
                if (depth == 0) continue;
                Skin top = skins[0], bottom = skins[Math.min(depth - 1, skins.length - 1)];
                out.face(u, 0, v, u + 1, 0, v, u + 1, 0, v + 1, u, 0, v + 1, top.top, top.topTint, 1);
                out.face(u, -depth, v + 1, u + 1, -depth, v + 1, u + 1, -depth, v, u, -depth, v, bottom.bottom, bottom.bottomTint, 0.45);
                for (int[] side : sides) {
                    int neighbour = depth(c.seed, i, u + side[0], v + side[1], size);
                    double shade = side[0] == 0 ? 0.8 : 0.62;
                    for (int layer = neighbour; layer < depth; layer++) {
                        Skin skin = skins[Math.min(layer, skins.length - 1)];
                        double yt = -layer, yb = -layer - 1;
                        if (side[1] < 0) out.face(u + 1, yt, v, u, yt, v, u, yb, v, u + 1, yb, v, skin.side, skin.sideTint, shade);
                        else if (side[1] > 0) out.face(u, yt, v + 1, u + 1, yt, v + 1, u + 1, yb, v + 1, u, yb, v + 1, skin.side, skin.sideTint, shade);
                        else if (side[0] < 0) out.face(u, yt, v, u, yt, v + 1, u, yb, v + 1, u, yb, v, skin.side, skin.sideTint, shade);
                        else out.face(u + 1, yt, v + 1, u + 1, yt, v, u + 1, yb, v, u + 1, yb, v + 1, skin.side, skin.sideTint, shade);
                    }
                }
            }
        }
        return out.toArray();
    }

    private static final class Cache {
        final long seed;
        final double cx, cz, aimY;
        final int cells = (int) Math.ceil(SPAN * 2 / CELL) + 1;
        final double[] grid = new double[cells * cells];
        final List<double[]> cracks;
        final double[] fragmentGround = new double[FRAGMENTS];
        final float[][] meshes = new float[FRAGMENTS][];
        long used;

        Cache(LaunchState launch) {
            seed = launch.getSeed();
            BlockTarget t = launch.getTarget();
            cx = t.getX() + 0.5; cz = t.getZ() + 0.5; aimY = t.getY() + 1;
            Arrays.fill(grid, Double.NaN);
            Arrays.fill(fragmentGround, Double.NaN);
            cracks = cracks(seed);
        }

        /** Terrain top, bilinear over a coarse lazily sampled grid; the aim height where chunks are missing. */
        double ground(double x, double z) {
            double gx = (x - cx + SPAN) / CELL, gz = (z - cz + SPAN) / CELL;
            int i = Math.max(0, Math.min(cells - 2, (int) Math.floor(gx)));
            int j = Math.max(0, Math.min(cells - 2, (int) Math.floor(gz)));
            double fx = Math.max(0, Math.min(1, gx - i)), fz = Math.max(0, Math.min(1, gz - j));
            double a = cell(i, j), b = cell(i + 1, j), c = cell(i, j + 1), d = cell(i + 1, j + 1);
            return mix(mix(a, b, fx), mix(c, d, fx), fz);
        }

        private double cell(int i, int j) {
            double h = grid[i * cells + j];
            if (!Double.isNaN(h)) return h;
            h = surface(cx - SPAN + i * CELL, cz - SPAN + j * CELL);
            if (Double.isNaN(h)) return aimY;
            grid[i * cells + j] = h;
            return h;
        }

        /** Exact terrain top under a crack end point, stored in the segment once its chunk is loaded. */
        double crack(double[] segment, int slot, double x, double z) {
            if (!Double.isNaN(segment[slot])) return segment[slot];
            double h = surface(x, z);
            if (Double.isNaN(h)) return ground(x, z);
            segment[slot] = h;
            return h;
        }

        /** Fragment anchor ground relative to the aim height. */
        double fragmentGround(int i) {
            if (!Double.isNaN(fragmentGround[i])) return fragmentGround[i];
            double[] anchor = anchor(seed, i);
            double h = surface(cx + anchor[0], cz + anchor[1]);
            if (Double.isNaN(h)) return ground(cx + anchor[0], cz + anchor[1]) - aimY;
            return fragmentGround[i] = h - aimY;
        }

        float[] mesh(int i) {
            if (meshes[i] != null) return meshes[i];
            Minecraft mc = Minecraft.getMinecraft();
            double[] anchor = anchor(seed, i);
            int ax = (int) Math.floor(cx + anchor[0]), az = (int) Math.floor(cz + anchor[1]);
            double top = surface(ax + 0.5, az + 0.5);
            boolean loaded = !Double.isNaN(top);
            BlockPos pos = new BlockPos(ax, loaded ? (int) top : (int) aimY, az);
            IBlockState[] layers = new IBlockState[6];
            for (int d = 0; d < layers.length; d++) {
                IBlockState state = loaded ? mc.world.getBlockState(pos.down(d + 1)) : null;
                if (state == null || !state.isOpaqueCube() || state.getRenderType() != EnumBlockRenderType.MODEL)
                    state = (d == 0 ? Blocks.GRASS : d < 4 ? Blocks.DIRT : Blocks.STONE).getDefaultState();
                layers[d] = state;
            }
            float[] mesh = buildMesh(this, i, layers, pos);
            // Without the real column the fallback mesh is drawn but rebuilt once the chunk arrives.
            if (loaded) meshes[i] = mesh;
            return mesh;
        }
    }

    /** Atlas sprites and biome tints of one block for the faces a fragment shows. */
    private static final class Skin {
        final TextureAtlasSprite top, side, bottom;
        final int topTint, sideTint, bottomTint;

        Skin(Minecraft mc, IBlockState state, BlockPos pos) {
            IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
            BakedQuad up = quad(model, state, EnumFacing.UP), north = quad(model, state, EnumFacing.NORTH);
            BakedQuad down = quad(model, state, EnumFacing.DOWN);
            TextureAtlasSprite particle = model.getParticleTexture();
            top = up != null ? up.getSprite() : particle;
            side = north != null ? north.getSprite() : particle;
            bottom = down != null ? down.getSprite() : particle;
            topTint = tint(mc, state, pos, up);
            sideTint = tint(mc, state, pos, north);
            bottomTint = tint(mc, state, pos, down);
        }

        private static BakedQuad quad(IBakedModel model, IBlockState state, EnumFacing face) {
            try {
                List<BakedQuad> quads = model.getQuads(state, face, 0);
                if (!quads.isEmpty()) return quads.get(0);
                for (BakedQuad quad : model.getQuads(state, null, 0)) if (quad.getFace() == face) return quad;
            } catch (RuntimeException ignored) {
                // Some modded models need extended state; the particle sprite stands in.
            }
            return null;
        }

        private static int tint(Minecraft mc, IBlockState state, BlockPos pos, BakedQuad quad) {
            if (quad == null || !quad.hasTintIndex()) return 0xFFFFFF;
            int color = mc.getBlockColors().colorMultiplier(state, mc.world, pos, quad.getTintIndex());
            return color == -1 ? 0xFFFFFF : color & 0xFFFFFF;
        }
    }

    /** x, y, z, u, v, r, g, b per vertex. */
    private static final class MeshBuilder {
        private float[] data = new float[1024];
        private int size;

        void face(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
                  double x3, double y3, double z3, TextureAtlasSprite sprite, int tint, double shade) {
            float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();
            float r = (float) ((tint >> 16 & 255) / 255.0 * shade), g = (float) ((tint >> 8 & 255) / 255.0 * shade);
            float b = (float) ((tint & 255) / 255.0 * shade);
            put(x0, y0, z0, u0, v0, r, g, b);
            put(x1, y1, z1, u1, v0, r, g, b);
            put(x2, y2, z2, u1, v1, r, g, b);
            put(x3, y3, z3, u0, v1, r, g, b);
        }

        private void put(double x, double y, double z, float u, float v, float r, float g, float b) {
            if (size + 8 > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = (float) x; data[size++] = (float) y; data[size++] = (float) z;
            data[size++] = u; data[size++] = v; data[size++] = r; data[size++] = g; data[size++] = b;
        }

        float[] toArray() { return Arrays.copyOf(data, size); }
    }
}
