package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaPhysics;
import fr.frabous.stellar.api.SupernovaTiming;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Finite, seeded ejecta: torn velocity layers, spherical shocks and curved, tapering plasma trails. */
final class SupernovaBlast {
    private static final int FRAGMENTS=144, TRAIL_SEGMENTS=6, RAYS=40, SPARKS=260;
    private SupernovaBlast() { }

    static void draw(long seed, double age, boolean emission, boolean schematic) {
        float gas=(float)SupernovaVisuals.ejectaAlpha(age),breakout=(float)SupernovaVisuals.breakout(age);
        if (gas<=0 && breakout<=0) return;
        GL11.glPushMatrix();GL11.glTranslated(0,SupernovaVisuals.STAR_Y,0);
        try {
            if (breakout>0) {
                double surface=SupernovaVisuals.starRadius(age)*1.025;
                SupernovaModel.shock(seed,surface,age,0xE2F4FF,breakout*(emission?0.32F:0.85F),0);
                if (!emission) MoonAttractVfx.glow(0,0,0,surface*1.9,0x8BCFFF,breakout*0.13F,true);
            }
            float flash=(float)SupernovaVisuals.coreFlash(age);
            if (flash>0) {
                SupernovaModel.shell(14+flash*30,14+flash*30,0xFFFFFF,age,flash*(emission?0.9F:0.7F),false,false);
                if (!emission) {
                    MoonAttractVfx.glow(0,0,0,120+flash*260,0xF4FAFF,flash*0.85F,true);
                    MoonAttractVfx.glow(0,0,0,420+flash*500,0x9FD2FF,flash*0.30F,true);
                }
            }
            if (gas<=0) return;
            rays(seed,age,(float)SupernovaVisuals.rays(age,schematic)*(emission?0.55F:0.8F));
            double radius=SupernovaPhysics.ejectaRadius(age);
            double cooling=SupernovaVisuals.smooth((age-SupernovaTiming.DETONATE-8)/110);
            int outer=SupernovaVisuals.color(0x4B9FFF,0xE76B2E,cooling);
            int inner=SupernovaVisuals.color(0xFFB544,0xED5827,cooling);
            for (int layer=0;layer<3;layer++)
                SupernovaModel.gas(seed,radius,age,layer==0?inner:outer,gas*(emission?0.14F:0.52F),layer);
            for (int layer=0;layer<3;layer++) {
                float shock=(float)SupernovaVisuals.shockAlpha(age,layer);
                int color=SupernovaVisuals.color(0xEAF8FF,0x82C4FF,layer/2.0);
                SupernovaModel.shock(seed,SupernovaVisuals.shockRadius(age,layer),age,color,
                        shock*(emission?0.24F:0.72F),layer);
            }
            // The light-speed guide stays subtle; the bright, broken rims above belong to the slower gas.
            float light=(float)SupernovaVisuals.radiationAlpha(age);
            double front=SupernovaPhysics.radiationRadius(age);
            if (schematic && light>0 && front<5500)
                SupernovaModel.shell(front,front,0xB6DDFF,age,light*(emission?0.008F:0.025F),false,false);
            trails(seed,age,radius,inner,outer,gas*(emission?0.40F:0.88F));
            SupernovaModel.shell(2.8,2.8,0xEBF6FF,age,gas*0.6F,false,false);
            if (!emission) {
                MoonAttractVfx.glow(0,0,0,radius*1.65,outer,gas*0.075F,true);
                MoonAttractVfx.glow(0,0,0,radius*0.48,inner,gas*0.16F,true);
                MoonAttractVfx.glow(0,0,0,9,0xC6E6FF,gas*0.45F,true);
                knots(seed,age,radius,inner,outer,gas);
                sparks(seed,age,radius,inner);
            }
        } finally { GL11.glPopMatrix(); }
    }

    /** Camera-facing starburst rays, raw POSITION_COLOR so the bloom pass can blur them too. */
    private static void rays(long seed, double age, float alpha) {
        if (alpha<=0) return;
        double[] eye=SupernovaModel.eyeDirection();
        double ux=-eye[2],uy=0,uz=eye[0],length=Math.sqrt(ux*ux+uz*uz);
        if (length<0.001) { ux=1;uz=0;length=1; }
        ux/=length;uz/=length;
        double vx=eye[1]*uz-eye[2]*uy,vy=eye[2]*ux-eye[0]*uz,vz=eye[0]*uy-eye[1]*ux;
        double spin=age*0.0035,grow=SupernovaVisuals.smooth((age-SupernovaTiming.DETONATE)/30);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
        for (int i=0;i<RAYS;i++) {
            double a=i*Math.PI*2/RAYS+SupernovaVisuals.random(seed,8700+i)*0.2+(i%2==0 ? spin : -spin);
            double reach=(260+SupernovaVisuals.random(seed,8900+i)*640)*(0.55+grow*0.45)*(i%3==0 ? 1.5 : 1);
            double half=0.012+SupernovaVisuals.random(seed,9100+i)*0.018;
            float shimmer=(float)(0.65+0.35*Math.sin(age*0.9+i*1.7));
            int color=i%4==0 ? 0xFFE2B0 : 0xD8EEFF;
            double c0=Math.cos(a-half),s0=Math.sin(a-half),c1=Math.cos(a+half),s1=Math.sin(a+half);
            double c=Math.cos(a),s=Math.sin(a);
            EffectMesh.vertex(b,(ux*c0+vx*s0)*8,(uy*c0+vy*s0)*8,(uz*c0+vz*s0)*8,0xFFFFFF,alpha*shimmer);
            EffectMesh.vertex(b,(ux*c1+vx*s1)*8,(uy*c1+vy*s1)*8,(uz*c1+vz*s1)*8,0xFFFFFF,alpha*shimmer);
            EffectMesh.vertex(b,(ux*c+vx*s)*reach,(uy*c+vy*s)*reach,(uz*c+vz*s)*reach,color,0);
        }
        Tessellator.getInstance().draw();
    }

    /** Small, fast incandescent sparks ahead of the gas, still behind the light front. */
    private static void sparks(long seed, double age, double radius, int inner) {
        MoonAttractVfx.Sprites hot=new MoonAttractVfx.Sprites(MoonAttractVfx.GLOW,true,true);
        for (int i=0;i<SPARKS;i++) {
            double alpha=SupernovaVisuals.sparkAlpha(age,seed,i);
            if (alpha<=0) continue;
            double y=SupernovaVisuals.random(seed,8200+i)*2-1,radial=Math.sqrt(1-y*y);
            double angle=SupernovaVisuals.random(seed,8400+i)*Math.PI*2;
            double tip=SupernovaVisuals.sparkRadius(age,seed,i);
            double size=radius*(0.006+SupernovaVisuals.random(seed,8600+i)*0.012);
            double cooling=SupernovaVisuals.smooth((age-SupernovaTiming.DETONATE-SupernovaVisuals.random(seed,8800+i)*40)/50);
            int color=SupernovaVisuals.color(0xFFFFFF,inner,cooling);
            hot.add(Math.cos(angle)*radial*tip,y*tip,Math.sin(angle)*radial*tip,size,size,0,color,
                    (float)(alpha*(0.9-cooling*0.5)),0);
        }
        hot.draw();
    }

    /** Two crossed ribbons per trail give thickness from every view, unlike one-pixel GL_LINES. */
    private static void trails(long seed, double age, double radius, int inner, int outer, float alpha) {
        double unfold=SupernovaVisuals.smooth((age-SupernovaTiming.DETONATE)/18);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for (int i=0;i<FRAGMENTS;i++) {
            double opacity=SupernovaVisuals.fragmentAlpha(age,seed,i);
            if (opacity<=0) continue;
            double y=1-2*(i+0.5)/FRAGMENTS,radial=Math.sqrt(1-y*y),angle=i*2.3999632297;
            double c=Math.cos(angle),s=Math.sin(angle),x=c*radial,z=s*radial;
            double tip=SupernovaVisuals.fragmentRadius(age,seed,i);
            double length=0.20+SupernovaVisuals.random(seed,4500+i)*0.42;
            double curl=radius*unfold*(0.025+SupernovaVisuals.random(seed,4700+i)*0.04)
                    *Math.sin(age*0.035+SupernovaVisuals.random(seed,4900+i)*Math.PI*2);
            double width=radius*(0.0018+SupernovaVisuals.random(seed,5100+i)*0.0045);
            int tailColor=SupernovaVisuals.random(seed,5300+i)>0.45?inner:outer;
            for (int segment=0;segment<TRAIL_SEGMENTS;segment++) {
                double u=(double)segment/TRAIL_SEGMENTS,v=(double)(segment+1)/TRAIL_SEGMENTS;
                double r0=tip*(1-length+length*u),r1=tip*(1-length+length*v);
                double bend0=Math.sin(u*Math.PI)*curl,bend1=Math.sin(v*Math.PI)*curl;
                double x0=x*r0-s*bend0,y0=y*r0,z0=z*r0+c*bend0;
                double x1=x*r1-s*bend1,y1=y*r1,z1=z*r1+c*bend1;
                double w0=width*Math.sin(u*Math.PI),w1=width*Math.sin(v*Math.PI);
                float a0=(float)(alpha*opacity*(0.06+u*0.65)),a1=(float)(alpha*opacity*(0.06+v*0.65));
                int color0=SupernovaVisuals.color(tailColor,0xF3FAFF,u*u);
                int color1=SupernovaVisuals.color(tailColor,0xF3FAFF,v*v);
                for (int plane=0;plane<2;plane++) {
                    double tx=plane==0?-s:-y*c,ty=plane==0?0:radial,tz=plane==0?c:-y*s;
                    EffectMesh.vertex(b,x0-tx*w0,y0-ty*w0,z0-tz*w0,color0,a0);
                    EffectMesh.vertex(b,x0+tx*w0,y0+ty*w0,z0+tz*w0,color0,a0);
                    EffectMesh.vertex(b,x1+tx*w1,y1+ty*w1,z1+tz*w1,color1,a1);
                    EffectMesh.vertex(b,x1-tx*w1,y1-ty*w1,z1-tz*w1,color1,a1);
                }
            }
        }
        Tessellator.getInstance().draw();
    }

    private static void knots(long seed, double age, double radius, int inner, int outer, float gas) {
        MoonAttractVfx.Sprites plasma=new MoonAttractVfx.Sprites(SupernovaModel.PLASMA,true,true);
        for (int i=0;i<FRAGMENTS;i++) {
            double opacity=SupernovaVisuals.fragmentAlpha(age,seed,i);
            double y=1-2*(i+0.5)/FRAGMENTS,radial=Math.sqrt(1-y*y),angle=i*2.3999632297;
            double x=Math.cos(angle)*radial,z=Math.sin(angle)*radial;
            double tip=SupernovaVisuals.fragmentRadius(age,seed,i);
            double size=radius*(0.035+SupernovaVisuals.random(seed,5500+i)*0.062);
            double density=SupernovaVisuals.ejectaDensity(x,y,z,age,seed,i%3);
            // Mixing complementary colors on every sprite made the entire burst converge toward grey.
            int color=SupernovaVisuals.random(seed,5300+i)>0.45?inner:outer;
            plasma.add(x*tip,y*tip,z*tip,size,size*(0.55+density*0.35),angle+age*0.009,
                    color,(float)(gas*opacity*(0.20+density*0.46)),i);
        }
        plasma.draw();
    }
}
