package fr.frabous.stellar.client;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

/** Soft atlas particles, sorted in eye space; textures never change Minecraft's enable caches. */
final class MoonAttractVfx {
    static final ResourceLocation SMOKE = texture("smoke"), FIRE = texture("fire"), GLOW = texture("glow");
    private MoonAttractVfx() { }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("stellar", "textures/cinema/moon_" + name + ".png");
    }

    static void glow(double x, double y, double z, double radius, int color, float alpha) {
        glow(x,y,z,radius,color,alpha,false);
    }

    static void glow(double x, double y, double z, double radius, int color, float alpha, boolean scaledView) {
        if (alpha <= 0) return;
        Sprites sprites = new Sprites(GLOW, true, scaledView);
        sprites.add(x, y, z, radius, radius, 0, color, alpha, 0);
        sprites.draw();
    }

    static final class Sprites {
        private static final Comparator<double[]> DEPTH_ORDER = Comparator.comparingDouble(p -> p[9]);

        private final ResourceLocation texture;
        private final boolean additive;
        private final List<double[]> particles = new ArrayList<>();
        private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

        Sprites(ResourceLocation texture, boolean additive) {
            this(texture,additive,false);
        }

        Sprites(ResourceLocation texture, boolean additive, boolean scaledView) {
            this.texture = texture; this.additive = additive;
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
            // Celestial proxies use a uniform model scale. Do not apply that scale twice to their billboards.
            if (scaledView) for (int row=0;row<2;row++) {
                double length=Math.sqrt(matrix.get(row)*matrix.get(row)+matrix.get(row+4)*matrix.get(row+4)
                        +matrix.get(row+8)*matrix.get(row+8));
                for (int column=0;column<3;column++) {
                    int index=row+column*4;
                    matrix.put(index,(float)(matrix.get(index)/Math.max(0.000001,length)));
                }
            }
        }

        void add(double x, double y, double z, double rx, double ry, double rotation, int color, float alpha, int tile) {
            if (alpha <= 0 || rx <= 0 || ry <= 0) return;
            double depth = matrix.get(2)*x + matrix.get(6)*y + matrix.get(10)*z + matrix.get(14);
            particles.add(new double[] {x,y,z,rx,ry,rotation,color,Math.min(1,alpha),tile & 3,depth});
        }

        void draw() {
            if (particles.isEmpty()) return;
            if (!additive) particles.sort(DEPTH_ORDER);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix(); GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            try {
                Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
                GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
                GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                for (double[] p : particles) {
                    double c = Math.cos(p[5]), s = Math.sin(p[5]);
                    double ux = (matrix.get(0)*c + matrix.get(1)*s)*p[3];
                    double uy = (matrix.get(4)*c + matrix.get(5)*s)*p[3];
                    double uz = (matrix.get(8)*c + matrix.get(9)*s)*p[3];
                    double vx = (-matrix.get(0)*s + matrix.get(1)*c)*p[4];
                    double vy = (-matrix.get(4)*s + matrix.get(5)*c)*p[4];
                    double vz = (-matrix.get(8)*s + matrix.get(9)*c)*p[4];
                    double u = ((int) p[8] % 2)*0.5, v = ((int) p[8] / 2)*0.5;
                    vertex(b, p, -ux+vx,-uy+vy,-uz+vz, u,v);
                    vertex(b, p, -ux-vx,-uy-vy,-uz-vz, u,v+0.5);
                    vertex(b, p, ux-vx,uy-vy,uz-vz, u+0.5,v+0.5);
                    vertex(b, p, ux+vx,uy+vy,uz+vz, u+0.5,v);
                }
                Tessellator.getInstance().draw();
            } finally {
                GlStateManager.bindTexture(binding);
                GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
                GlStateManager.setActiveTexture(activeTexture);
                GL11.glMatrixMode(matrixMode);
                GL11.glPopClientAttrib(); GL11.glPopAttrib();
                GlStateManager.resetColor();
            }
        }

        private void vertex(BufferBuilder b, double[] p, double dx, double dy, double dz, double u, double v) {
            int rgb = (int) p[6];
            b.pos(p[0]+dx,p[1]+dy,p[2]+dz).tex(u,v)
                    .color((rgb>>16&255)/255F,(rgb>>8&255)/255F,(rgb&255)/255F,(float) p[7]).endVertex();
        }
    }
}
