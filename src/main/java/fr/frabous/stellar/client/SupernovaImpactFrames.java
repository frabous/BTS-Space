package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaTiming;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Screen-space film grammar: a negative/positive ink impact pair, focus lines, lens streak and vignette. */
final class SupernovaImpactFrames {
    private static final int INK = 0x02040A, PAPER = 0xF5FAFF;
    private SupernovaImpactFrames() { }

    static void draw(long seed, double age, int width, int height) {
        double blackout=SupernovaVisuals.impactBlackout(age);
        if (blackout>0) Gui.drawRect(0,0,width,height,(int)(blackout*235)<<24|INK);
        float alpha=(float)SupernovaVisuals.impactFrame(age);
        if (alpha<=0) return;
        // The negative drawing reads as the instant of rupture; the positive one as the outgoing burst.
        boolean negative=SupernovaVisuals.impactNegative(age);
        int ground=negative ? PAPER : INK, ink=negative ? INK : PAPER;
        Gui.drawRect(0,0,width,height,(int)(alpha*250)<<24|ground);
        begin(width,height,false);
        try {
            double t=SupernovaVisuals.smooth((age-SupernovaTiming.DETONATE)/2.6);
            int salt=negative ? 0 : 900;
            double cx=width*0.5,cy=height*0.5,radius=height*(negative ? 0.07+t*0.05 : 0.11+t*0.08);
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
            for (int i=0;i<96;i++) {
                double a=i*Math.PI*2/96,next=(i+1)*Math.PI*2/96;
                double r0=radius*(1+SupernovaVisuals.random(seed,6100+salt+i)*0.28);
                double r1=radius*(1+SupernovaVisuals.random(seed,6100+salt+(i+1)%96)*0.28);
                EffectMesh.vertex(b,cx,cy,0,ink,alpha);
                EffectMesh.vertex(b,cx+Math.cos(a)*r0,cy+Math.sin(a)*r0,0,ink,alpha);
                EffectMesh.vertex(b,cx+Math.cos(next)*r1,cy+Math.sin(next)*r1,0,ink,alpha);
            }
            int rays=negative ? 34 : 56;
            for (int i=0;i<rays;i++) {
                double a=i*Math.PI*2/rays+SupernovaVisuals.random(seed,6300+salt+i)*0.09;
                double reach=radius*(negative ? 2.2+SupernovaVisuals.random(seed,6500+salt+i)*5.5
                        : 1.6+SupernovaVisuals.random(seed,6500+salt+i)*3.6);
                double halfWidth=(negative ? 0.012 : 0.006)+SupernovaVisuals.random(seed,6700+salt+i)*0.022;
                triangle(b,cx,cy,a-halfWidth,radius*0.9,a,reach,a+halfWidth,radius*0.9,ink,alpha*0.9F);
            }
            if (!negative) crackedRing(b,seed,cx,cy,radius*(2.3+t*0.9),radius*0.05,alpha);
            Tessellator.getInstance().draw();
            b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
            for (int i=0;i<15;i++) {
                double a=i*Math.PI*2/15+SupernovaVisuals.random(seed,6900+salt+i)*0.23;
                double root=radius*(0.38+SupernovaVisuals.random(seed,7100+salt+i)*0.36);
                triangle(b,cx,cy,a-0.035,radius*1.3,a+0.10,root,a+0.045,radius*1.3,ground,alpha);
            }
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    /** A broken shock outline: seeded gaps keep it a drawn stroke, not a geometric UI ring. */
    private static void crackedRing(BufferBuilder b, long seed, double cx, double cy, double radius, double width, float alpha) {
        for (int i=0;i<72;i++) {
            if (SupernovaVisuals.random(seed,7300+i)<0.28) continue;
            double a=i*Math.PI*2/72,next=(i+0.8)*Math.PI*2/72;
            double w=width*(0.4+SupernovaVisuals.random(seed,7500+i)*1.6);
            double r=radius*(1+(SupernovaVisuals.random(seed,7700+i)-0.5)*0.08);
            quad(b,cx+Math.cos(a)*(r-w),cy+Math.sin(a)*(r-w),cx+Math.cos(a)*(r+w),cy+Math.sin(a)*(r+w),
                    cx+Math.cos(next)*(r+w),cy+Math.sin(next)*(r+w),cx+Math.cos(next)*(r-w),cy+Math.sin(next)*(r-w),
                    PAPER,alpha*0.85F);
        }
    }

    /** Gathering lines are pale and thin; recoil lines are ink. The set changes every 1.5 ticks, like drawn cels. */
    static void focusLines(long seed, double age, int width, int height) {
        float alpha=(float)SupernovaVisuals.focusLines(age);
        if (alpha<=0) return;
        boolean gather=age<SupernovaTiming.DETONATE;
        int cel=(int)Math.floor(age/1.5),color=gather ? 0xCFE6FF : INK;
        double cx=width*0.5,cy=height*0.5,outer=Math.hypot(width,height)*0.55;
        double inner=height*(gather ? 0.20+(1-SupernovaVisuals.inhale(age))*0.25 : 0.30);
        begin(width,height,gather);
        try {
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
            for (int i=0;i<84;i++) {
                int key=cel*131+i;
                double a=SupernovaVisuals.random(seed,9000+key)*Math.PI*2;
                double start=inner*(1+SupernovaVisuals.random(seed,9200+key)*0.9);
                double half=(gather ? 0.004 : 0.007)+SupernovaVisuals.random(seed,9400+key)*0.012;
                float a0=alpha*(gather ? 0.55F : 0.62F);
                EffectMesh.vertex(b,cx+Math.cos(a)*start,cy+Math.sin(a)*start,0,color,0);
                EffectMesh.vertex(b,cx+Math.cos(a-half)*outer,cy+Math.sin(a-half)*outer,0,color,a0);
                EffectMesh.vertex(b,cx+Math.cos(a+half)*outer,cy+Math.sin(a+half)*outer,0,color,a0);
            }
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    /** Horizontal anamorphic streak through the frame centre, where the space camera keeps the star. */
    static void lensStreak(double age, int width, int height) {
        float alpha=(float)SupernovaVisuals.lensStreak(age);
        if (alpha<=0) return;
        double cy=height*0.5;
        begin(width,height,true);
        try {
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            band(b,width,cy,height*0.05,0x6FB8FF,alpha*0.45F);
            band(b,width,cy,height*0.012,0xDDF1FF,alpha*0.9F);
            band(b,width,cy,height*0.003,0xFFFFFF,alpha);
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    static void vignette(double age, int width, int height) {
        float alpha=(float)SupernovaVisuals.vignette(age);
        if (alpha<=0) return;
        double cx=width*0.5,cy=height*0.5;
        begin(width,height,false);
        try {
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUAD_STRIP,DefaultVertexFormats.POSITION_COLOR);
            double squeeze=1-SupernovaVisuals.inhale(age)*0.35;
            for (int i=0;i<=64;i++) {
                double a=i*Math.PI*2/64,c=Math.cos(a),s=Math.sin(a);
                EffectMesh.vertex(b,cx+c*width*0.36*squeeze,cy+s*height*0.34*squeeze,0,INK,0);
                EffectMesh.vertex(b,cx+c*width*0.76,cy+s*height*0.76,0,INK,alpha);
            }
            Tessellator.getInstance().draw();
        } finally { end(); }
    }

    private static void band(BufferBuilder b, int width, double cy, double half, int color, float alpha) {
        // Edges taper to zero on both axes, so the streak has no hard rectangle.
        double[] xs={0,width*0.2,width*0.5,width*0.8,width};
        float[] fade={0,0.55F,1,0.55F,0};
        for (int i=0;i<4;i++) {
            EffectMesh.vertex(b,xs[i],cy-half,0,color,0);EffectMesh.vertex(b,xs[i],cy,0,color,alpha*fade[i]);
            EffectMesh.vertex(b,xs[i+1],cy,0,color,alpha*fade[i+1]);EffectMesh.vertex(b,xs[i+1],cy-half,0,color,0);
            EffectMesh.vertex(b,xs[i],cy,0,color,alpha*fade[i]);EffectMesh.vertex(b,xs[i],cy+half,0,color,0);
            EffectMesh.vertex(b,xs[i+1],cy+half,0,color,0);EffectMesh.vertex(b,xs[i+1],cy,0,color,alpha*fade[i+1]);
        }
    }

    private static void begin(int width, int height, boolean additive) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glOrtho(0,width,height,0,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);GL11.glEnable(GL11.GL_BLEND);GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** GUI screens keep MODELVIEW active, so restore the projection then return to it. */
    private static void end() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
        GL11.glPopClientAttrib();GL11.glPopAttrib();
    }

    private static void triangle(BufferBuilder b, double x, double y, double a, double r0,
                                 double c, double r1, double d, double r2, int color, float alpha) {
        EffectMesh.vertex(b,x+Math.cos(a)*r0,y+Math.sin(a)*r0,0,color,alpha);
        EffectMesh.vertex(b,x+Math.cos(c)*r1,y+Math.sin(c)*r1,0,color,alpha);
        EffectMesh.vertex(b,x+Math.cos(d)*r2,y+Math.sin(d)*r2,0,color,alpha);
    }

    private static void quad(BufferBuilder b, double x0, double y0, double x1, double y1,
                             double x2, double y2, double x3, double y3, int color, float alpha) {
        EffectMesh.vertex(b,x0,y0,0,color,alpha);EffectMesh.vertex(b,x1,y1,0,color,alpha);
        EffectMesh.vertex(b,x2,y2,0,color,alpha);
        EffectMesh.vertex(b,x0,y0,0,color,alpha);EffectMesh.vertex(b,x2,y2,0,color,alpha);
        EffectMesh.vertex(b,x3,y3,0,color,alpha);
    }
}
