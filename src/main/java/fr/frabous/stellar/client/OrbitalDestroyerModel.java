package fr.frabous.stellar.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Reusable autonomous capital ship. Local +X is the bow; its keel is sealed. */
public final class OrbitalDestroyerModel {
    private static final int STRIDE = 5;
    private static final float[] MESH = loadMesh();

    private OrbitalDestroyerModel() { }

    public static void prepare() { /* Load the immutable mesh before the first cinematic frame. */ }

    /** Caller supplies position, rotation and scale; no cargo or spell state is required. */
    public static void render(boolean emissionOnly) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            for (int pass = emissionOnly ? 2 : 0; pass < 3; pass++) {
                int wantedTag = pass == 0 ? 0 : (pass == 1 ? 2 : 1);
                GL11.glDepthMask(pass == 0);
                if (pass == 1) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else if (pass == 2) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                } else GL11.glDisable(GL11.GL_BLEND);
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i < MESH.length; i += STRIDE) {
                    int tag = (int) MESH[i+4];
                    if (tag != wantedTag) continue;
                    float alpha = pass == 1 ? 80.0F / 255.0F : (pass == 2 ? 0.4F : 1.0F);
                    EffectMesh.vertex(b,MESH[i],MESH[i+1],MESH[i+2],(int) MESH[i+3],alpha);
                }
                Tessellator.getInstance().draw();
            }
        } finally {
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(activeTexture);
        }
    }

    private static float[] loadMesh() {
        try (InputStream stream = OrbitalDestroyerModel.class.getResourceAsStream("/assets/stellar/models/orbital_destroyer.mesh")) {
            if (stream == null) throw new IOException("Missing orbital destroyer mesh");
            DataInputStream in = new DataInputStream(stream);
            if (in.readInt() != 0x534F4431) throw new IOException("Invalid orbital destroyer mesh");
            int count = in.readInt();
            if (count <= 0 || count % 4 != 0 || count > 100000) throw new IOException("Invalid destroyer vertex count");
            float[] mesh = new float[count*STRIDE];
            for (int i = 0; i < mesh.length; i += STRIDE) {
                mesh[i] = in.readFloat(); mesh[i+1] = in.readFloat(); mesh[i+2] = in.readFloat();
                mesh[i+3] = in.readInt(); mesh[i+4] = in.readUnsignedByte();
                if (mesh[i+4] > 2) throw new IOException("Invalid autonomous ship material tag");
            }
            return mesh;
        } catch (IOException exception) { throw new IllegalStateException("Cannot load orbital destroyer",exception); }
    }
}
