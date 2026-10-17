package fr.frabous.stellar.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupernovaPhysicsTest {
    @Test public void oneClockKeepsLightThirtyTimesFasterThanEjectaIncludingTheTimelapse() {
        for (double age=320;age<640;age+=0.125) {
            double light=SupernovaPhysics.radiationRadius(age+0.125)-SupernovaPhysics.radiationRadius(age);
            double matter=SupernovaPhysics.ejectaRadius(age+0.125)-SupernovaPhysics.ejectaRadius(age);
            assertTrue(matter>0);
            assertEquals(30*matter,light,0.00000001);
            assertTrue(SupernovaPhysics.radiationRadius(age)>=SupernovaPhysics.ejectaRadius(age));
        }
        assertEquals(0,SupernovaPhysics.elapsed(300),0);
        assertEquals(80,SupernovaPhysics.elapsed(400),0);
        assertEquals(2400,SupernovaPhysics.elapsed(480),0);
        assertEquals(640,SupernovaPhysics.radiationRadius(400),0.0000001);
        assertEquals(640,SupernovaPhysics.ejectaRadius(480),0.0000001);
    }

    @Test public void authoritativeGlobalIrradiationStillHasExactlyFortyPulsesAndNoAftermathDamage() {
        int count=0;
        for (long age=-20;age<=700;age++) if (SupernovaPhysics.damagePulse(age)) {
            assertTrue(age>=400 && age<480 && (age-400)%2==0);count++;
        }
        assertEquals(40,count);
        assertEquals(400F,count*AttackParameters.RAILGUN_DAMAGE_PER_PULSE,0);
        assertFalse(SupernovaPhysics.damagePulse(399));assertTrue(SupernovaPhysics.damagePulse(400));
        assertTrue(SupernovaPhysics.damagePulse(478));assertFalse(SupernovaPhysics.damagePulse(480));
    }

    @Test public void phaseBoundariesRemainUnchangedAndOnlyTheCameraCutMoves() {
        assertEquals(400,LaunchTimeline.phaseStartTicks(AttackId.SUPERNOVA,LaunchPhase.IMPACT));
        assertEquals(480,LaunchTimeline.phaseStartTicks(AttackId.SUPERNOVA,LaunchPhase.AFTERMATH));
        assertEquals(640,LaunchTimeline.totalTicks(AttackId.SUPERNOVA));
        assertTrue(SupernovaTiming.SHOCK_REVIVAL>SupernovaTiming.COLLAPSE);
        assertTrue(SupernovaTiming.SHOCK_REVIVAL<SupernovaTiming.DETONATE);
    }

}
