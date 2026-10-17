package fr.frabous.stellar.client;

import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.SupernovaPhysics;
import fr.frabous.stellar.api.SupernovaTiming;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Core-collapse illustration: isotropic light first, slower turbulent ejecta, then planetary atmosphere. */
final class SupernovaScene {
    private static final FloatBuffer VIEW=BufferUtils.createFloatBuffer(16);
    private static final float[] VIEW_VALUES=new float[16];
    private SupernovaScene() { }

    static void draw(LaunchState launch, double age, boolean emission) {
        Minecraft mc=Minecraft.getMinecraft();
        boolean ownFilm=mc.currentScreen instanceof SupernovaCinema
                && ((SupernovaCinema)mc.currentScreen).matches(launch.getLaunchId());
        boolean otherFilm=mc.currentScreen instanceof RailgunCinema || mc.currentScreen instanceof AsteroidPullerCinema
                || mc.currentScreen instanceof SupernovaCinema && !ownFilm;
        if (!emission && !otherFilm) {
            if (ownFilm || age>=SupernovaTiming.IMPACT) overlay(0,(float)SupernovaVisuals.exposure(age));
            if (!ownFilm && age>=SupernovaTiming.IMPACT) overlay(0xEDF6FF,(float)(SupernovaVisuals.flash(age)*0.72));
        }
        BlockTarget target=launch.getTarget();
        Entity camera=mc.getRenderViewEntity();
        if (camera==null) return;
        double partial=mc.getRenderPartialTicks();
        double eyeX=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double eyeY=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial+camera.getEyeHeight();
        double eyeZ=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        VIEW.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,VIEW);VIEW.get(VIEW_VALUES);VIEW.rewind();
        double[] actualEye=SupernovaSkyProjection.eye(VIEW_VALUES);
        if (actualEye!=null) { eyeX=actualEye[0];eyeY=actualEye[1];eyeZ=actualEye[2]; }
        SupernovaSkyProjection sky=SupernovaSkyProjection.project(target.getX()+0.5,
                target.getY()+1.1+SupernovaVisuals.STAR_Y,target.getZ()+0.5,eyeX,eyeY,eyeZ,
                Math.max(260,SupernovaPhysics.ejectaRadius(age)*2));
        GL11.glPushMatrix();
        GL11.glTranslated(sky.x,sky.y,sky.z);GL11.glScaled(sky.scale,sky.scale,sky.scale);
        GL11.glTranslated(0,-SupernovaVisuals.STAR_Y,0);
        try { drawLocal(launch,age,emission,false); }
        finally { GL11.glPopMatrix(); }
        if (!otherFilm) atmosphere(launch,age,emission);
    }

    /** A scoped exposure/flash, before luminous geometry; no framebuffer rebinding or depth clear. */
    static void overlay(int color, float alpha) {
        if (alpha<=0) return;
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            EffectMesh.vertex(b,-1,-1,0,color,alpha);EffectMesh.vertex(b,1,-1,0,color,alpha);
            EffectMesh.vertex(b,1,1,0,color,alpha);EffectMesh.vertex(b,-1,1,0,color,alpha);
            Tessellator.getInstance().draw();
        } finally {
            GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(mode);
            GL11.glPopClientAttrib();GL11.glPopAttrib();
        }
    }

    static void drawLocal(LaunchState launch, double age, boolean emission, boolean schematic) {
        if (age<0 || age>=SupernovaTiming.END) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            if (!emission) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);
                GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            }
            // EmissionBloom owns the emission blend/depth masks. Only raw POSITION_COLOR is submitted there.
            SupernovaModel.star(launch.getSeed(),age,emission,schematic);
            SupernovaBlast.draw(launch.getSeed(),age,emission,schematic);
        } finally { GL11.glPopClientAttrib();GL11.glPopAttrib(); }
    }

    /** Local atmospheric scattering is visible everywhere, even when the stellar origin is beyond the far plane. */
    private static void atmosphere(LaunchState launch, double age, boolean emission) {
        float light=(float)SupernovaVisuals.irradiation(age),dust=(float)SupernovaVisuals.atmosphere(age);
        if (light<=0 && dust<=0) return;
        Minecraft mc=Minecraft.getMinecraft();Entity camera=mc.getRenderViewEntity();
        if (camera==null) return;
        double partial=mc.getRenderPartialTicks();
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial+camera.getEyeHeight();
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);
        try {
            SupernovaModel.shell(650,650,0xD9E9FF,age,light*(emission?0.05F:0.20F),true,false);
            SupernovaModel.shell(610,610,0xD6AE80,age,dust*(emission?0.012F:0.13F),true,true);
            if (!emission && dust>0) {
                MoonAttractVfx.Sprites haze=new MoonAttractVfx.Sprites(MoonAttractVfx.SMOKE,false);
                for (int i=0;i<112;i++) {
                    double angle=i*2.3999632297;
                    double r=28+SupernovaVisuals.random(launch.getSeed(),i+1100)*150;
                    double h=4+SupernovaVisuals.random(launch.getSeed(),i+1300)*65;
                    double size=12+SupernovaVisuals.random(launch.getSeed(),i+1500)*18;
                    double drift=(age-SupernovaTiming.AFTERMATH)*0.16;
                    int color=SupernovaVisuals.color(0xD8BC92,0x737B87,SupernovaVisuals.smooth((age-500)/100));
                    haze.add(Math.cos(angle)*r+drift,h,Math.sin(angle)*r,size,size*0.66,
                            angle+age*0.001,color,dust*0.27F,i);
                }
                haze.draw();
            }
        } finally { GL11.glPopMatrix(); }
    }
}
