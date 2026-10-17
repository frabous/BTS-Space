package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaPhysics;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupernovaWitnessTest {
    @Test public void theProxyUsesTheActualF5EyeRatherThanThePlayersHead() {
        double[] camera={4099,7,4093};
        for (float stretch:new float[]{1,0.7F}) {
            float[] m={0.6F*stretch,0,-0.8F*stretch,0, 0,1,0,0, 0.8F,0,0.6F,0, 0,0,0,1};
            m[12]=(float)-(m[0]*camera[0]+m[4]*camera[1]+m[8]*camera[2]);
            m[13]=(float)-(m[1]*camera[0]+m[5]*camera[1]+m[9]*camera[2]);
            m[14]=(float)-(m[2]*camera[0]+m[6]*camera[1]+m[10]*camera[2]);
            assertArrayEquals(camera,SupernovaSkyProjection.eye(m),0.001);
        }
        assertNull(SupernovaSkyProjection.eye(new float[16]));
    }

    @Test public void celestialProxyPreservesEveryDirectionAndAngularSizeAtWorldScale() {
        for (double[] eye:new double[][]{{0,5,0},{4096.5,5.62,4096.5},{-30000000,255,30000000},{256.5,644.1,256.5}})
            for (double age:new double[]{60,300,350,480,620,639.99}) {
                double radius=Math.max(260,SupernovaPhysics.ejectaRadius(age)*1.6);
                SupernovaSkyProjection p=SupernovaSkyProjection.project(256.5,644.1,256.5,eye[0],eye[1],eye[2],radius);
                assertTrue(p.scale>0 && p.scale<=1);
                assertTrue(p.distance*p.scale<=1100.000001);
                assertTrue((p.distance+radius)*p.scale<=2700.000001);
                for (double[] point:new double[][]{{256.5,644.1,256.5},{338.5,644.1,256.5},{256.5,644.1+radius,256.5}}) {
                    double[] actual={point[0]-eye[0],point[1]-eye[1],point[2]-eye[2]};
                    double[] projected={p.x+(point[0]-256.5)*p.scale-eye[0],
                            p.y+(point[1]-644.1)*p.scale-eye[1],p.z+(point[2]-256.5)*p.scale-eye[2]};
                    for (int i=0;i<3;i++) assertEquals(actual[i]*p.scale,projected[i],0.00000002);
                }
            }
        SupernovaSkyProjection near=SupernovaSkyProjection.project(0,640,0,0,5,0,260);
        assertEquals(1,near.scale,0);
        assertEquals(640,near.y,0);
    }

    @Test public void gasHasSeededHolesAndCoherentNonSphericalShapeWithoutUnboundedNoise() {
        double minimum=1,maximum=0,smallest=1,largest=-1;
        for (int i=0;i<512;i++) {
            double y=1-2*(i+0.5)/512,r=Math.sqrt(1-y*y),angle=i*2.3999632297;
            double x=Math.cos(angle)*r,z=Math.sin(angle)*r;
            for (int layer=0;layer<3;layer++) {
                double density=SupernovaVisuals.ejectaDensity(x,y,z,350,42,layer);
                double shape=SupernovaVisuals.ejectaDisplacement(x,y,z,42,layer);
                assertTrue(Double.isFinite(density) && density>=0 && density<=1);
                assertTrue(Double.isFinite(shape) && Math.abs(shape)<=0.125);
                assertEquals(density,SupernovaVisuals.ejectaDensity(x,y,z,350,42,layer),0);
                assertEquals(shape,SupernovaVisuals.ejectaDisplacement(x,y,z,42,layer),0);
                assertTrue(Math.abs(density-SupernovaVisuals.ejectaDensity(x+0.000001,y,z,350,42,layer))<0.001);
                minimum=Math.min(minimum,density);maximum=Math.max(maximum,density);
                smallest=Math.min(smallest,shape);largest=Math.max(largest,shape);
            }
        }
        assertEquals(0,minimum,0);assertTrue(maximum>0.9);
        assertTrue(largest-smallest>0.15);
        assertNotEquals(SupernovaVisuals.ejectaDensity(0.6,0.8,0,350,42,0),
                SupernovaVisuals.ejectaDensity(0.6,0.8,0,350,43,0),0);
    }

    @Test public void theCameraReallyHoldsForConvectionAndBeforeRupture() {
        double[] convection=pose(SupernovaVisuals.spaceCamera(90));
        for (double age=90;age<=150;age+=0.125) assertArrayEquals(convection,pose(SupernovaVisuals.spaceCamera(age)),0);
        double[] core=pose(SupernovaVisuals.spaceCamera(180));
        for (double age=180;age<=304;age+=0.125) assertArrayEquals(core,pose(SupernovaVisuals.spaceCamera(age)),0);
        double previous=0;
        for (double age=320;age<382;age+=0.125) {
            RailgunCameraPath.Pose p=SupernovaVisuals.spaceCamera(age);
            double distance=Math.hypot(Math.hypot(p.x,p.z),p.y-SupernovaVisuals.STAR_Y);
            assertTrue(distance>=previous);previous=distance;
        }
    }

    @Test public void lightCannotProduceAnEarlyPressureCueOrReplayAnOldOne() {
        for (int age=0;age<480;age++) {
            assertFalse(SupernovaAudioTiming.pressureDue(age-1,age));
            assertEquals(0,SupernovaAudioTiming.rumble(age),0);
            assertEquals(0,SupernovaAudioTiming.wind(age),0);
        }
        assertTrue(SupernovaAudioTiming.pressureDue(479,480));
        assertFalse(SupernovaAudioTiming.pressureDue(480,481));
        assertFalse(SupernovaAudioTiming.pressureDue(400,500));
        for (double age=0;age<=660;age+=0.125) {
            assertTrue(SupernovaAudioTiming.rumble(age)>=0 && SupernovaAudioTiming.rumble(age)<=0.32);
            assertTrue(SupernovaAudioTiming.wind(age)>=0 && SupernovaAudioTiming.wind(age)<=0.24);
            if (age>=640) {
                assertEquals(0,SupernovaAudioTiming.rumble(age),0);
                assertEquals(0,SupernovaAudioTiming.wind(age),0);
            }
        }
    }

    @Test public void aRoofCrossfadesToTheQuieterFilteredAssetWithoutChangingPitch() {
        assertEquals(1,SupernovaAudioTiming.openness(0),0);
        assertEquals(0,SupernovaAudioTiming.muffled(0,false),0);
        assertEquals(0,SupernovaAudioTiming.openness(1),0);
        assertEquals(0.36,SupernovaAudioTiming.muffled(1,false),0);
        assertEquals(0.18,SupernovaAudioTiming.muffled(1,true),0);
        for (double shelter=0;shelter<=1;shelter+=0.01)
            assertTrue(SupernovaAudioTiming.openness(shelter)+SupernovaAudioTiming.muffled(shelter,false)<=1.000001);
    }

    @Test public void networkTimeCorrectionsDoNotReplayPressureOrRewindTheAudioMix() {
        SupernovaAudioTiming.Clock clock=new SupernovaAudioTiming.Clock();
        int cues=0;double previous=-1;
        for (double age:new double[]{470,479,480,479,480,481,500,540,585,604,600,601,605,640,638,640}) {
            double audioAge=clock.advance(age);
            assertTrue(audioAge>=previous);
            assertEquals(Math.max(previous,age),audioAge,0);
            if (clock.pressureDue()) cues++;
            previous=audioAge;
        }
        assertEquals(1,cues);
        SupernovaAudioTiming.Clock late=new SupernovaAudioTiming.Clock();
        assertEquals(520,late.advance(520),0);
        assertFalse(late.pressureDue());
        late.advance(480);assertFalse(late.pressureDue());
        late.advance(550);assertFalse(late.pressureDue());
    }

    private static double[] pose(RailgunCameraPath.Pose p) {
        return new double[]{p.x,p.y,p.z,p.targetX,p.targetY,p.targetZ,p.roll,p.fov};
    }
}
