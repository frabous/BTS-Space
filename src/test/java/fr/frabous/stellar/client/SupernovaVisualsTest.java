package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaPhysics;
import fr.frabous.stellar.api.SupernovaTiming;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupernovaVisualsTest {
    @Test public void onlyTheCoreCollapsesWhileTheVisibleEnvelopeKeepsItsSize() {
        double previous=19;
        for (double age=0;age<=640;age+=0.125) {
            double core=SupernovaPhysics.coreRadius(age);
            assertTrue(core<=previous && core>=2.799999999);
            assertTrue(SupernovaVisuals.starRadius(age)>81 && SupernovaVisuals.starRadius(age)<83);
            previous=core;
        }
        assertEquals(19,SupernovaPhysics.coreRadius(180),0);
        assertEquals(2.8,SupernovaPhysics.coreRadius(260),0.0000001);
        assertEquals(2.8,SupernovaPhysics.interiorShockRadius(260),0);
        assertEquals(82,SupernovaPhysics.interiorShockRadius(320),0.0000001);
    }

    @Test public void convectionChangesTextureFramesAndDeformsTheSurfaceNotJustItsRotation() {
        assertEquals(16,SupernovaVisuals.CONVECTION_FRAMES);
        assertEquals(16,SupernovaVisuals.CONVECTION_COLUMNS*SupernovaVisuals.CONVECTION_ROWS);
        for (int i=0;i<16;i++) assertEquals(i,SupernovaVisuals.convectionFrame(i*6));
        assertEquals(0,SupernovaVisuals.convectionFrame(96));
        assertEquals(15,SupernovaVisuals.convectionFrame(-0.01));
        assertEquals(0.5,SupernovaVisuals.convectionBlend(3),0);
        assertEquals(0,SupernovaVisuals.convectionBlend(6),0);
        assertNotEquals(SupernovaVisuals.surfaceDisplacement(0.6,0.8,0,90),
                SupernovaVisuals.surfaceDisplacement(0.6,0.8,0,96),0.000001);
        for (double age=0;age<=320;age+=0.125) {
            double d=SupernovaVisuals.surfaceDisplacement(0.6,0.8,0,age);
            assertTrue(Double.isFinite(d) && Math.abs(d)<=0.011);
            assertEquals(d,SupernovaVisuals.surfaceDisplacement(0.6,0.8,0,age),0);
        }
    }

    @Test public void radiationPrecedesMatterAndGroundEffectsCannotArriveEarly() {
        for (double age=320;age<400;age+=0.125) {
            assertTrue(SupernovaPhysics.radiationRadius(age)<640);
            assertTrue(SupernovaPhysics.ejectaRadius(age)<640);
            assertEquals(0,SupernovaVisuals.irradiation(age),0);
            assertEquals(0,SupernovaVisuals.atmosphere(age),0);
        }
        for (double age=400;age<480;age+=0.125) {
            assertTrue(SupernovaPhysics.ejectaRadius(age)<640);
            assertEquals(0,SupernovaVisuals.atmosphere(age),0);
        }
        assertEquals(640,SupernovaPhysics.radiationRadius(400),0.0000001);
        assertEquals(640,SupernovaPhysics.ejectaRadius(480),0.0000001);
    }

    @Test public void everyEffectIsBoundedAndFadesByCleanup() {
        for (double age=-20;age<=660;age+=0.125)
            for (double alpha : new double[]{SupernovaVisuals.starAlpha(age),SupernovaVisuals.cutaway(age),
                    SupernovaVisuals.ejectaAlpha(age),SupernovaVisuals.radiationAlpha(age),SupernovaVisuals.irradiation(age),
                    SupernovaVisuals.atmosphere(age),SupernovaVisuals.flash(age),SupernovaVisuals.transition(age)})
                assertTrue(Double.isFinite(alpha) && alpha>=0 && alpha<=1);
        for (double alpha : new double[]{SupernovaVisuals.starAlpha(640),SupernovaVisuals.ejectaAlpha(640),
                SupernovaVisuals.radiationAlpha(640),SupernovaVisuals.irradiation(640),SupernovaVisuals.atmosphere(640),
                SupernovaVisuals.darkness(640),SupernovaVisuals.cutaway(640)}) assertEquals(0,alpha,0);
    }

    @Test public void theCoreIsShownOnlyInTheLabelledSchematicInterval() {
        assertEquals(0,SupernovaVisuals.cutaway(180),0);
        assertEquals(1,SupernovaVisuals.cutaway(220),0);
        assertEquals(0,SupernovaVisuals.cutaway(312),0);
        assertEquals(0,SupernovaVisuals.cutaway(320),0);
        assertTrue(SupernovaVisuals.isSpaceShot(260));
    }

    @Test public void backgroundExposureIsFiniteAndRestoredBeforeTheFilmEnds() {
        for (double age=-20;age<=660;age+=0.125) {
            double exposure=SupernovaVisuals.exposure(age);
            assertTrue(Double.isFinite(exposure) && exposure>=0 && exposure<=0.60);
            if (age<=SupernovaTiming.WORLD_RETURN || age>=540) assertEquals(0,exposure,0);
        }
        assertEquals(0.60,SupernovaVisuals.exposure(400),0);
        assertEquals(0.60,SupernovaVisuals.exposure(480),0);
    }

    @Test public void theStarAndBothEarlySphericalFrontsRemainFramedDuringTheOrbitalShot() {
        RailgunCameraPath.Pose previous=SupernovaVisuals.spaceCamera(60);
        for (double age=60;age<SupernovaTiming.WORLD_RETURN;age+=0.125) {
            RailgunCameraPath.Pose pose=SupernovaVisuals.spaceCamera(age);
            double dy=pose.y-SupernovaVisuals.STAR_Y;
            double distance=Math.sqrt(pose.x*pose.x+dy*dy+pose.z*pose.z);
            double radius=age<320 ? SupernovaVisuals.starRadius(age) : SupernovaPhysics.radiationRadius(age);
            assertTrue(distance>radius+10);
            assertTrue("Sphere clipped by letterbox at "+age,
                    distance*Math.sin(Math.atan(Math.tan(Math.toRadians(pose.fov)/2)*0.85))>radius+10);
            assertTrue(Math.abs(pose.x-previous.x)<4 && Math.abs(pose.y-previous.y)<4 && Math.abs(pose.z-previous.z)<4);
            for (double value : values(pose)) assertTrue(Double.isFinite(value));
            previous=pose;
        }
    }

    @Test public void groundViewStaysOnReceivedTerrainAndInitiallyWatchesTheSkyNotALocalBlast() {
        for (double[] offset : new double[][]{{135,140},{50,50},{8,0},{0,0}})
            for (double minimum : new double[]{0,80,190}) for (double age=382;age<=640;age+=0.125) {
                RailgunCameraPath.Pose pose=SupernovaVisuals.groundCamera(age,offset[0],offset[1],minimum);
                assertTrue(pose.y>=minimum-0.41);
                for (double value : values(pose)) assertTrue(Double.isFinite(value));
            }
        RailgunCameraPath.Pose sky=SupernovaVisuals.groundCamera(400,50,50,6);
        assertEquals(12,sky.y,0);assertEquals(600,sky.targetY,0);
        RailgunCameraPath.Pose settled=SupernovaVisuals.groundCamera(620,50,50,6);
        assertEquals(8,settled.y,0);assertEquals(10,settled.targetY,0);assertEquals(55,settled.fov,0);
        assertTrue(Math.atan(Math.tan(Math.toRadians(settled.fov)/2)*16/9)<Math.PI/4);
        assertEquals(69,SupernovaVisuals.groundCamera(620,135,140,6).fov,0);
    }

    @Test public void cutsAreHiddenAndWorldViewReturnsBeforeRadiationArrival() {
        assertFalse(SupernovaVisuals.isSpaceShot(59.99));assertTrue(SupernovaVisuals.isSpaceShot(60));
        assertTrue(SupernovaVisuals.isSpaceShot(381.99));assertFalse(SupernovaVisuals.isSpaceShot(382));
        assertEquals(1,SupernovaVisuals.transition(60),0);assertEquals(1,SupernovaVisuals.transition(382),0);
        assertFalse(SupernovaVisuals.isSpaceShot(SupernovaTiming.IMPACT));
    }

    @Test public void allClientsReconstructIdenticalSeededEjecta() {
        for (long seed : new long[]{Long.MIN_VALUE,0,42,Long.MAX_VALUE}) for (int i=0;i<1000;i++) {
            double value=SupernovaVisuals.random(seed,i);
            assertTrue(value>=0 && value<1);assertEquals(value,SupernovaVisuals.random(seed,i),0);
        }
        assertNotEquals(SupernovaVisuals.random(42,0),SupernovaVisuals.random(43,0),0);
    }

    private static double[] values(RailgunCameraPath.Pose p) {
        return new double[]{p.x,p.y,p.z,p.targetX,p.targetY,p.targetZ,p.roll,p.fov};
    }
}
