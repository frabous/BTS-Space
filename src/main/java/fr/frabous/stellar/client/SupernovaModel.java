package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaPhysics;
import fr.frabous.stellar.api.SupernovaTiming;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/** Time-varying convection, a labelled core cutaway and optically thin, spherical ejecta. */
final class SupernovaModel {
    private static final ResourceLocation PHOTOSPHERE = new ResourceLocation("stellar", "textures/cinema/supernova_convection.png");
    static final ResourceLocation PLASMA = new ResourceLocation("stellar", "textures/cinema/supernova_plasma.png");
    private static final double[][] STAR = sphere(96, 48);
    private static final double[][] SHELL = sphere(64, 32);
    private static final double[][] GAS_AGE=new double[3][65*33],GAS_DENSITY=new double[3][65*33],GAS_SHAPE=new double[3][65*33];
    private static final long[] GAS_SEED=new long[3];
    static { for (double[] ages:GAS_AGE) java.util.Arrays.fill(ages,Double.NaN); }
    private SupernovaModel() { }

    static void prepareTextures() {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (ResourceLocation texture : new ResourceLocation[]{PHOTOSPHERE, PLASMA, MoonAttractVfx.GLOW,
                    MoonAttractVfx.FIRE, MoonAttractVfx.SMOKE})
                Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        } finally { GlStateManager.bindTexture(binding); GlStateManager.setActiveTexture(active); }
    }

    static void star(long seed, double age, boolean emission, boolean schematic) {
        double radius = SupernovaVisuals.starRadius(age), alpha = SupernovaVisuals.starAlpha(age);
        if (alpha <= 0) return;
        double heat = SupernovaVisuals.heat(age);
        int color = SupernovaVisuals.color(0xFFCF91, 0xD8ECFF, SupernovaVisuals.smooth((age-308)/12));
        double cut=schematic ? SupernovaVisuals.cutaway(age) : 0;
        GL11.glPushMatrix(); GL11.glTranslated(0, SupernovaVisuals.STAR_Y, 0);
        GL11.glRotated(age * 0.055, 0, 1, 0);
        try {
            if (!emission) {
                photosphere(radius, color, (float) alpha, age, cut);
                MoonAttractVfx.glow(0, 0, 0, radius * 2.9, color, (float) (alpha * 0.18*(1-cut*0.7)),true);
                MoonAttractVfx.glow(0, 0, 0, radius * 1.75, color, (float) (alpha * 0.12*(1-cut*0.7)),true);
            } else {
                shell(radius * 1.012, radius * 1.012, color, age, (float) (alpha * 0.22*(1-cut)), false, false);
            }
            loops(seed, radius, age, color, (float) (alpha * (0.40 + heat * 0.18)));
            if (cut > 0) {
                double core = SupernovaPhysics.coreRadius(age);
                shell(core, core, 0xD4E9FF, age, (float)(cut*0.85), false, false);
                if (!emission) MoonAttractVfx.glow(0,0,0,core*2.2,0xC5DFFF,(float)(cut*0.5),true);
                if (age >= SupernovaTiming.SHOCK_REVIVAL) {
                    double shock = SupernovaPhysics.interiorShockRadius(age);
                    shell(shock,shock,0xC1DFFF,age,(float)(cut*0.8),false,true);
                }
            }
        } finally { GL11.glPopMatrix(); }
    }

    private static void photosphere(double radius, int color, float alpha, double age, double cut) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(PHOTOSPHERE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glEnable(GL11.GL_TEXTURE_2D);
            // Fixed-function overbright preserves dark convection channels without a shader dependency.
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, 2);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(cut < 0.001);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_CULL_FACE); GL11.glCullFace(GL11.GL_BACK);
            double[] eye = eyeDirection();
            int frame = SupernovaVisuals.convectionFrame(age);
            double blend = SupernovaVisuals.convectionBlend(age);
            for (int pass=0; pass<2; pass++) {
                if (pass==1 && blend<=0) continue;
                int tile=(frame+pass)%SupernovaVisuals.CONVECTION_FRAMES;
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_COLOR);
                for (double[] v : STAR) {
                    // Shared grid vertices cache the deformation across both atlas layers in this frame.
                    if (v[6]!=age) { v[5]=1+SupernovaVisuals.surfaceDisplacement(v[0],v[1],v[2],age); v[6]=age; }
                    double facing = Math.max(0, v[0]*eye[0]+v[1]*eye[1]+v[2]*eye[2]);
                    float light = (float) (0.34+0.66*Math.sqrt(facing));
                    float visible = (float)(1-cut*SupernovaVisuals.smooth((facing-0.28)/0.22));
                    // Half-texel insets prevent linear filtering from sampling a neighboring animation tile.
                    double u=(tile%4*512+0.5+v[3]*511)/2048;
                    double w=(tile/4*256+0.5+v[4]*255)/1024;
                    double r=radius*v[5];
                    b.pos(v[0]*r,v[1]*r,v[2]*r).tex(u,w)
                            .color((color>>16&255)/255F*light,(color>>8&255)/255F*light,
                                    (color&255)/255F*light,alpha*visible*(float)(pass==0 ? 1 : blend)).endVertex();
                }
                Tessellator.getInstance().draw();
            }
        } finally {
            GlStateManager.bindTexture(binding);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GlStateManager.setActiveTexture(active); GL11.glMatrixMode(mode);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GlStateManager.resetColor();
        }
    }

    /** Raw POSITION_COLOR only: safe inside the existing selective bloom callback. */
    static void gas(long seed, double radius, double age, int color, float alpha, int layer) {
        if (alpha<=0) return;
        if (GAS_SEED[layer]!=seed) { java.util.Arrays.fill(GAS_AGE[layer],Double.NaN);GAS_SEED[layer]=seed; }
        double[] eye=eyeDirection();
        double lightLimit=SupernovaPhysics.radiationRadius(age)*0.94;
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
        for (double[] v:SHELL) {
            int index=(int)v[7];
            if (GAS_AGE[layer][index]!=age) {
                GAS_DENSITY[layer][index]=SupernovaVisuals.ejectaDensity(v[0],v[1],v[2],age,seed,layer);
                GAS_SHAPE[layer][index]=SupernovaVisuals.ejectaScale(v[0],v[1],v[2],age,seed,layer);
                GAS_AGE[layer][index]=age;
            }
            double density=GAS_DENSITY[layer][index];
            double r=Math.min(radius*GAS_SHAPE[layer][index],lightLimit);
            double rim=1-Math.min(1,Math.abs(v[0]*eye[0]+v[1]*eye[1]+v[2]*eye[2]));
            int shade=SupernovaVisuals.color(color,0xFFF2D6,density*0.16);
            EffectMesh.vertex(b,v[0]*r,v[1]*r,v[2]*r,shade,(float)(alpha*density*(0.22+rim*0.55)));
        }
        Tessellator.getInstance().draw();
    }

    /** Broken spherical shock rims; no planar rings or texture state inside the bloom pass. */
    static void shock(long seed, double radius, double age, int color, float alpha, int layer) {
        if (alpha<=0) return;
        double[] eye=eyeDirection();
        double lightLimit=SupernovaPhysics.radiationRadius(age)*0.985;
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
        for (double[] v:SHELL) {
            double density=SupernovaVisuals.ejectaDensity(v[0],v[1],v[2],age,seed,layer);
            double r=radius*(1+SupernovaVisuals.ejectaDisplacement(v[0],v[1],v[2],seed,layer)*0.42);
            if (age>=SupernovaTiming.DETONATE) r=Math.min(r,lightLimit);
            double rim=Math.pow(1-Math.min(1,Math.abs(v[0]*eye[0]+v[1]*eye[1]+v[2]*eye[2])),4);
            EffectMesh.vertex(b,v[0]*r,v[1]*r,v[2]*r,color,(float)(alpha*rim*(0.12+density*0.88)));
        }
        Tessellator.getInstance().draw();
    }

    /** Raw POSITION_COLOR only: used for the compact core, atmosphere and labelled schematic guides. */
    static void shell(double radius, double height, int color, double age, float alpha, boolean hemisphere, boolean turbulent) {
        if (alpha <= 0 || radius <= 0) return;
        double[] eye = eyeDirection();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int index = 0; index < SHELL.length; index += 3) {
            if (hemisphere && (SHELL[index][1] < -0.00001 || SHELL[index + 1][1] < -0.00001
                    || SHELL[index + 2][1] < -0.00001)) continue;
            for (int corner = 0; corner < 3; corner++) {
                double[] v = SHELL[index + corner];
                double dot = Math.abs(v[0] * eye[0] + v[1] * eye[1] + v[2] * eye[2]);
                double rim = Math.pow(1 - Math.min(1, dot), 2.5);
                double noise = turbulent ? Math.sin(v[0] * 21 + age * 0.023)
                        * Math.sin(v[1] * 17 - age * 0.017) * Math.sin(v[2] * 23 + age * 0.009) : 0;
                double scale = 1 + noise * 0.045;
                int shade = SupernovaVisuals.color(color, 0xFFF7E4, (noise + 1) * 0.18);
                EffectMesh.vertex(b, v[0] * radius * scale, v[1] * height * scale, v[2] * radius * scale,
                        shade, (float) (alpha * (0.04 + rim * 0.62) * (turbulent ? 0.75+noise*0.25 : 1)));
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void loops(long seed, double radius, double age, int color, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 18; i++) {
            double azimuth = SupernovaVisuals.random(seed, i * 3) * Math.PI * 2;
            double ny = SupernovaVisuals.random(seed, i * 3 + 1) * 1.6 - 0.8, nr = Math.sqrt(1 - ny * ny);
            double nx = Math.cos(azimuth) * nr, nz = Math.sin(azimuth) * nr;
            double tx = -Math.sin(azimuth), tz = Math.cos(azimuth);
            double bx = -ny * Math.cos(azimuth), by = nr, bz = -ny * Math.sin(azimuth);
            double pulse = 0.7 + Math.sin(age * 0.022 + i * 2.4) * 0.3;
            double reach = radius * (0.15 + SupernovaVisuals.random(seed, i * 3 + 2) * 0.34);
            for (int segment = 0; segment < 32; segment++) {
                double a = segment * Math.PI / 32, next = (segment + 1) * Math.PI / 32;
                double h0 = radius * 0.985 + Math.sin(a) * reach * pulse;
                double h1 = radius * 0.985 + Math.sin(next) * reach * pulse;
                double t0 = Math.cos(a) * reach * 0.65, t1 = Math.cos(next) * reach * 0.65;
                double width = radius * 0.009;
                float opacity = alpha * (float) Math.pow(Math.sin((a + next) * 0.5), 0.35);
                EffectMesh.vertex(b, nx*h0+tx*t0-bx*width, ny*h0-by*width, nz*h0+tz*t0-bz*width, color, opacity);
                EffectMesh.vertex(b, nx*h0+tx*t0+bx*width, ny*h0+by*width, nz*h0+tz*t0+bz*width, 0xFFF6DF, opacity);
                EffectMesh.vertex(b, nx*h1+tx*t1+bx*width, ny*h1+by*width, nz*h1+tz*t1+bz*width, 0xFFF6DF, opacity);
                EffectMesh.vertex(b, nx*h1+tx*t1-bx*width, ny*h1-by*width, nz*h1+tz*t1-bz*width, color, opacity);
            }
        }
        Tessellator.getInstance().draw();
    }

    static double[] eyeDirection() {
        FloatBuffer m = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, m);
        double x = -(m.get(0)*m.get(12) + m.get(1)*m.get(13) + m.get(2)*m.get(14));
        double y = -(m.get(4)*m.get(12) + m.get(5)*m.get(13) + m.get(6)*m.get(14));
        double z = -(m.get(8)*m.get(12) + m.get(9)*m.get(13) + m.get(10)*m.get(14));
        double length = Math.max(0.001, Math.sqrt(x*x + y*y + z*z));
        return new double[]{x/length, y/length, z/length};
    }

    private static double[][] sphere(int longitudes, int latitudes) {
        double[][] grid = new double[(longitudes + 1) * (latitudes + 1)][];
        for (int v = 0; v <= latitudes; v++) for (int u = 0; u <= longitudes; u++) {
            double theta = v * Math.PI / latitudes, phi = u * Math.PI * 2 / longitudes;
            grid[v * (longitudes + 1) + u] = new double[]{Math.sin(theta)*Math.cos(phi), Math.cos(theta),
                    Math.sin(theta)*Math.sin(phi), (double)u/longitudes, (double)v/latitudes, 1, Double.NaN,
                    v*(longitudes+1)+u};
        }
        double[][] triangles = new double[longitudes * latitudes * 6][];
        int index = 0;
        for (int v = 0; v < latitudes; v++) for (int u = 0; u < longitudes; u++) {
            int a = v * (longitudes + 1) + u, b = a + longitudes + 1;
            triangles[index++] = grid[a]; triangles[index++] = grid[a + 1]; triangles[index++] = grid[b];
            triangles[index++] = grid[a + 1]; triangles[index++] = grid[b + 1]; triangles[index++] = grid[b];
        }
        return triangles;
    }
}
