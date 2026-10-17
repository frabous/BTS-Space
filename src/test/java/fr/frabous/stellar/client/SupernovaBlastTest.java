package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaPhysics;
import fr.frabous.stellar.api.SupernovaTiming;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupernovaBlastTest {
    @Test public void breakoutHasOneBriefImpactDrawingAndNoRepeatingStrobe() {
        assertEquals(0,SupernovaVisuals.impactFrame(319.999),0);
        assertTrue(SupernovaVisuals.impactBlackout(319.5)>0.95);
        assertEquals(1,SupernovaVisuals.impactFrame(320),0);
        assertTrue(SupernovaVisuals.impactFrame(321)>0.5);
        assertEquals(0,SupernovaVisuals.impactFrame(322.6),0);
        assertEquals(0,SupernovaVisuals.impactBlackout(320.6),0);
        double previous=1;
        for (double age=320;age<=640;age+=0.125) {
            double frame=SupernovaVisuals.impactFrame(age);
            assertTrue(frame<=previous);previous=frame;
            if (age>=323) assertEquals(0,frame,0);
        }
        assertTrue(SupernovaVisuals.breakout(317)>0.9);
        assertEquals(0,SupernovaVisuals.breakout(328),0);
        assertEquals(0,SupernovaVisuals.starAlpha(324),0);
    }

    @Test public void staggeredSphericalShocksAndFragmentsNeverOvertakeTheLightFront() {
        for (double age=300;age<640;age+=0.5) {
            double light=SupernovaPhysics.radiationRadius(age);
            for (int layer=0;layer<3;layer++) {
                double alpha=SupernovaVisuals.shockAlpha(age,layer);
                assertUnit(alpha);
                if (age<=SupernovaTiming.DETONATE+layer*4) assertEquals(0,alpha,0);
                assertTrue(SupernovaVisuals.shockRadius(age,layer)<light);
            }
            for (int i=0;i<144;i++) {
                double r=SupernovaVisuals.fragmentRadius(age,42,i);
                assertTrue(Double.isFinite(r) && r>0 && r<light);
                assertTrue("The sprite's edge must also stay behind the light",
                        r+SupernovaPhysics.ejectaRadius(age)*0.14<light);
                double alpha=SupernovaVisuals.fragmentAlpha(age,42,i);
                assertUnit(alpha);
                if (age<320) assertEquals(0,alpha,0);
            }
        }
        assertTrue(SupernovaVisuals.shockAlpha(322,0)>0.8);
        assertEquals(0,SupernovaVisuals.shockAlpha(322,1),0);
        assertTrue(SupernovaVisuals.shockAlpha(326,1)>0.8);
        assertEquals(0,SupernovaVisuals.shockAlpha(326,2),0);
    }

    @Test public void gasRollsAndSeparatesInsteadOfOnlyScalingOneFrozenSphere() {
        double difference=0;
        for (int i=0;i<128;i++) {
            double y=1-2*(i+0.5)/128,r=Math.sqrt(1-y*y),a=i*2.3999632297;
            double x=Math.cos(a)*r,z=Math.sin(a)*r;
            for (int layer=0;layer<3;layer++) {
                double scale=SupernovaVisuals.ejectaScale(x,y,z,350,42,layer);
                assertTrue(Double.isFinite(scale) && scale>0.33 && scale<1.5);
                difference+=Math.abs(scale-SupernovaVisuals.ejectaScale(x,y,z,365,42,layer));
                assertEquals(scale,SupernovaVisuals.ejectaScale(x,y,z,350,42,layer),0);
            }
        }
        assertTrue("The flow field must evolve after the initial expansion",difference>1);
        assertTrue(SupernovaVisuals.ejectaScale(0.6,0.8,0,350,42,2)
                -SupernovaVisuals.ejectaScale(0.6,0.8,0,350,42,0)>0.15);
        double minimum=Double.MAX_VALUE,maximum=0;
        for (int i=0;i<144;i++) {
            double radius=SupernovaVisuals.fragmentRadius(365,42,i);
            minimum=Math.min(minimum,radius);maximum=Math.max(maximum,radius);
        }
        assertTrue(maximum-minimum>SupernovaPhysics.ejectaRadius(365)*0.3);
    }

    @Test public void newEnvelopesAreBoundedAndEverythingReleasesAtTheOriginalEndTick() {
        for (double age=-20;age<=660;age+=0.125) {
            assertUnit(SupernovaVisuals.impactFrame(age));
            assertUnit(SupernovaVisuals.impactBlackout(age));
            assertUnit(SupernovaVisuals.breakout(age));
            for (int layer=0;layer<3;layer++) assertUnit(SupernovaVisuals.shockAlpha(age,layer));
            if (age>=640) {
                assertEquals(0,SupernovaVisuals.fragmentAlpha(age,Long.MIN_VALUE,143),0);
                assertEquals(0,SupernovaVisuals.ejectaAlpha(age),0);
            }
        }
        // Joining late or rendering ages out of order cannot restart an impact or accumulate camera drift.
        double frame=SupernovaVisuals.impactFrame(321),radius=SupernovaVisuals.fragmentRadius(350,42,17);
        double flow=SupernovaVisuals.ejectaScale(0.6,0.8,0,350,42,2);
        for (double age:new double[]{321,620,350,320,640,321}) {
            SupernovaVisuals.impactFrame(age);
            SupernovaVisuals.fragmentRadius(age,Long.MIN_VALUE,17);
            SupernovaVisuals.ejectaScale(0.6,0.8,0,age,Long.MAX_VALUE,2);
        }
        assertEquals(frame,SupernovaVisuals.impactFrame(321),0);
        assertEquals(radius,SupernovaVisuals.fragmentRadius(350,42,17),0);
        assertEquals(flow,SupernovaVisuals.ejectaScale(0.6,0.8,0,350,42,2),0);
    }

    private static void assertUnit(double value) {
        assertTrue(Double.isFinite(value) && value>=0 && value<=1);
    }
}
