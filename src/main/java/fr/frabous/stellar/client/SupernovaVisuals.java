package fr.frabous.stellar.client;

import fr.frabous.stellar.api.SupernovaTiming;
import fr.frabous.stellar.api.SupernovaPhysics;

/** Pure, absolute-age animation math. No accumulated particles, camera drift or tick-local randomness. */
final class SupernovaVisuals {
    static final double STAR_Y = SupernovaPhysics.STAR_HEIGHT;
    static final int CONVECTION_FRAMES = 16;
    static final int CONVECTION_COLUMNS = 4;
    static final int CONVECTION_ROWS = 4;
    private SupernovaVisuals() { }

    static double smooth(double value) { return RailgunCameraPath.smooth(value); }
    static double mix(double a, double b, double value) { return a + (b - a) * value; }
    static double heat(double age) { return smooth((age - SupernovaTiming.SHOCK_REVIVAL) / 60); }
    // The iron core collapses, not the entire visible envelope. Small surface pulsations remain.
    static double starRadius(double age) { return SupernovaPhysics.ENVELOPE_RADIUS * (1+0.004*Math.sin(age*0.036)); }
    static double starAlpha(double age) {
        return smooth((age - 42) / 18) * (1 - smooth((age - SupernovaTiming.DETONATE) / 4));
    }
    static double cutaway(double age) {
        return smooth((age-SupernovaTiming.COLLAPSE)/18)*(1-smooth((age-294)/18));
    }
    static int convectionFrame(double age) {
        return Math.floorMod((int)Math.floor(age/6),CONVECTION_FRAMES);
    }
    static double convectionBlend(double age) { return smooth(age/6-Math.floor(age/6)); }
    static double surfaceDisplacement(double x, double y, double z, double age) {
        return 0.008*Math.sin(x*13+y*5+age*0.045)*Math.sin(y*11-z*7-age*0.033)
                + 0.003*Math.sin(z*19+x*8+age*0.021);
    }
    static double radiationAlpha(double age) {
        return smooth((age-SupernovaTiming.DETONATE)/2)*(1-smooth((age-420)/20));
    }
    static double ejectaRadius(double age) { return SupernovaPhysics.ejectaRadius(age); }
    static double ejectaAlpha(double age) {
        return smooth((age - SupernovaTiming.DETONATE) / 3) * (1 - smooth((age - 560) / 80));
    }
    static double ejectaDensity(double x, double y, double z, double age, long seed, int layer) {
        double phase=random(seed,3100+layer)*Math.PI*2;
        double broad=Math.sin(x*4.7+y*2.3+phase)*Math.cos(y*5.3-z*3.1-phase*0.7);
        double fine=Math.sin(x*18-z*7+phase+age*0.004)*Math.sin(y*15+x*6-phase);
        return smooth((0.58+broad*0.32+fine*0.25-0.25)/0.6)*smooth((broad+0.6)/0.35);
    }
    static double ejectaDisplacement(double x, double y, double z, long seed, int layer) {
        double phase=random(seed,3100+layer)*Math.PI*2;
        return 0.09*Math.sin(x*4.7+y*2.3+phase)*Math.cos(y*5.3-z*3.1-phase*0.7)
                +0.035*Math.sin(x*12-z*9+phase)*Math.sin(y*11+x*4-phase);
    }
    /** Separating velocity layers and rolling fingers, reconstructed directly from the launch clock. */
    static double ejectaScale(double x, double y, double z, double age, long seed, int layer) {
        double unfold=smooth((age-SupernovaTiming.DETONATE)/24);
        double phase=random(seed,3400+layer)*Math.PI*2;
        double curl=Math.sin(x*9+z*5+phase+age*0.047)*Math.sin(y*8-z*4-phase-age*0.031);
        return mix(0.98,0.66+layer*0.19,unfold)
                *(1+ejectaDisplacement(x,y,z,seed,layer)*(1+unfold*1.5)+curl*unfold*0.10);
    }
    static double shockAlpha(double age, int layer) {
        double t=age-SupernovaTiming.DETONATE-layer*4;
        return smooth(t/1.2)*(1-smooth(t/(24+layer*9)));
    }
    static double shockRadius(double age, int layer) {
        double t=Math.max(0,age-SupernovaTiming.DETONATE-layer*4);
        return Math.min(SupernovaPhysics.radiationRadius(age)*0.96,
                ejectaRadius(age)*(0.94+smooth(t/20)*(0.36-layer*0.09)));
    }
    static double fragmentRadius(double age, long seed, int index) {
        double t=Math.max(0,age-SupernovaTiming.DETONATE-random(seed,4100+index)*7);
        double velocity=0.82+random(seed,4300+index)*0.80;
        // Reserve room for the whole soft sprite, not just its center, behind the light front.
        return Math.min(SupernovaPhysics.radiationRadius(age)*0.84,
                ejectaRadius(age)*mix(0.96,velocity,smooth(t/22)));
    }
    static double fragmentAlpha(double age, long seed, int index) {
        double t=age-SupernovaTiming.DETONATE-random(seed,4100+index)*7;
        return smooth(t/3)*(1-smooth((age-540)/100));
    }
    static double breakout(double age) {
        return smooth((age-(SupernovaTiming.DETONATE-12))/10)
                *(1-smooth((age-SupernovaTiming.DETONATE)/8));
    }
    /** Two held monochrome drawings (negative, then positive), cut after 2.6 ticks; never repeated. */
    static double impactFrame(double age) {
        return age<SupernovaTiming.DETONATE ? 0 : 1-smooth((age-SupernovaTiming.DETONATE-1.9)/0.7);
    }
    static boolean impactNegative(double age) {
        return age>=SupernovaTiming.DETONATE && age<SupernovaTiming.DETONATE+IMPACT_NEGATIVE;
    }
    static final double IMPACT_NEGATIVE = 1.2;
    /** The lens tightens for the 0.7 s before the bounce, then releases on the impact cut. */
    static double inhale(double age) {
        return smooth((age-306)/13)*(1-smooth((age-SupernovaTiming.DETONATE)/0.5));
    }
    /** Manga focus lines: they gather during the inhale and recoil after the drawing. */
    static double focusLines(double age) {
        double gather=0.45*smooth((age-309)/9)*(1-smooth((age-318.4)/0.6));
        double recoil=smooth((age-322.6)/0.4)*(1-smooth((age-324)/20));
        return Math.max(gather,recoil);
    }
    static double lensStreak(double age) {
        return smooth((age-SupernovaTiming.DETONATE-2.6)/1.2)*(1-smooth((age-324)/34));
    }
    /** Film rays follow the breakout; witnesses only see them when the light actually arrives. */
    static double rays(double age, boolean film) {
        double start=film ? SupernovaTiming.DETONATE+1 : SupernovaTiming.IMPACT;
        return smooth((age-start)/3)*(1-smooth((age-start-6)/(film ? 44 : 30)));
    }
    static double coreFlash(double age) {
        return smooth((age-(SupernovaTiming.DETONATE-0.4))/0.4)*(1-smooth((age-SupernovaTiming.DETONATE)/16));
    }
    /** Letterbox tightening during the inhale, released at the impact cut. */
    static double letterbox(double age) {
        return smooth((age-300)/18)*(1-smooth((age-SupernovaTiming.DETONATE)/1.5));
    }
    static double vignette(double age) {
        return isSpaceShot(age) ? Math.min(1,0.32+inhale(age)*0.6) : 0;
    }
    static double sparkRadius(double age, long seed, int index) {
        double t=Math.max(0,age-SupernovaTiming.DETONATE-random(seed,8100+index)*3);
        double velocity=1.05+random(seed,8300+index)*0.85;
        return Math.min(SupernovaPhysics.radiationRadius(age)*0.9,
                ejectaRadius(age)*mix(0.9,velocity,smooth(t/14)));
    }
    static double sparkAlpha(double age, long seed, int index) {
        double t=age-SupernovaTiming.DETONATE-random(seed,8100+index)*3;
        return smooth(t/1.5)*(1-smooth((t-20-random(seed,8500+index)*60)/30));
    }
    static double card(double age, double start) {
        return smooth((age-start)/6)*(1-smooth((age-start-44)/10));
    }
    static double impactBlackout(double age) {
        return smooth((age-(SupernovaTiming.DETONATE-1.5))/0.8)
                *(1-smooth((age-SupernovaTiming.DETONATE)/0.6));
    }
    static double irradiation(double age) {
        return smooth((age-SupernovaTiming.IMPACT)/6)*(1-smooth((age-SupernovaTiming.AFTERMATH)/32));
    }
    static double atmosphere(double age) {
        return smooth((age-SupernovaTiming.AFTERMATH)/12)*(1-smooth((age-550)/90));
    }
    static double flash(double age) {
        return pulse(age,SupernovaTiming.DETONATE,2)*0.76+pulse(age,SupernovaTiming.DETONATE,15)*0.18
                +pulse(age,SupernovaTiming.IMPACT,2.5)*0.78+pulse(age,SupernovaTiming.IMPACT,14)*0.20;
    }
    private static double pulse(double age, double start, double duration) {
        return age < start ? 0 : 1 - smooth((age - start) / duration);
    }
    static double darkness(double age) {
        return smooth((age - 34) / 26) * (1 - smooth((age - 480) / 130));
    }
    static double exposure(double age) {
        return 0.60 * smooth((age - SupernovaTiming.WORLD_RETURN) / 16)
                * (1 - smooth((age - SupernovaTiming.AFTERMATH) / 60));
    }
    static boolean isSpaceShot(double age) { return age >= SupernovaTiming.STAR && age < SupernovaTiming.WORLD_RETURN; }
    static double transition(double age) {
        double entrance = Math.max(0, 1 - Math.abs(age - SupernovaTiming.STAR) / 7);
        double exit = Math.max(0, 1 - Math.abs(age - SupernovaTiming.WORLD_RETURN) / 7);
        return smooth(Math.max(entrance, exit));
    }

    static RailgunCameraPath.Pose spaceCamera(double age) {
        double settle=smooth((age-60)/30),reframe=smooth((age-150)/30);
        double angle=mix(mix(0.88,1.05,settle),1.18,reframe);
        double radius=mix(mix(345,305,settle),300,reframe);
        RailgunCameraPath.Pose close = new RailgunCameraPath.Pose(Math.cos(angle) * radius,
                STAR_Y+mix(mix(240,210,settle),180,reframe),Math.sin(angle)*radius,0,STAR_Y-6,0,
                mix(-3,0,settle),mix(mix(57,52,settle),50,reframe));
        // Hold 90..150 for convection, and 180..304 for the core. Pull straight back, not around the explosion.
        double pullback=smooth((age-304)/78),wideRadius=mix(365,1220,pullback);
        // The blast punches the lens outward and jolts the aim; the position itself never shakes.
        double kick=pulse(age,SupernovaTiming.DETONATE,9),punch=pulse(age,SupernovaTiming.DETONATE,16);
        double jolt=pulse(age,SupernovaTiming.DETONATE,30)*(age>=SupernovaTiming.DETONATE+IMPACT_NEGATIVE ? 1 : 0);
        double shake=age-SupernovaTiming.DETONATE;
        RailgunCameraPath.Pose wide=new RailgunCameraPath.Pose(Math.cos(1.18)*wideRadius,
                STAR_Y+mix(135,350,pullback),Math.sin(1.18)*wideRadius,
                Math.sin(shake*2.9)*jolt*11,STAR_Y+Math.sin(shake*3.7+1)*jolt*8,Math.cos(shake*2.3)*jolt*11,
                Math.sin(shake*1.7)*kick*2.6,66+punch*12);
        RailgunCameraPath.Pose blended=RailgunCameraPath.blendPoses(close,wide,(age-304)/28);
        double squeeze=inhale(age)*12;
        if (squeeze<=0) return blended;
        return new RailgunCameraPath.Pose(blended.x,blended.y,blended.z,blended.targetX,blended.targetY,
                blended.targetZ,blended.roll,blended.fov-squeeze);
    }

    static RailgunCameraPath.Pose groundCamera(double age, double offsetX, double offsetZ, double minimumY) {
        double impact = Math.max(0, age - SupernovaTiming.AFTERMATH);
        double look = mix(600, 24, smooth((age - 435) / 80));
        boolean limitedTerrain = Math.hypot(offsetX, offsetZ) < 125;
        double settle = limitedTerrain ? smooth((age - 500) / 70) : 0;
        double height = Math.max(minimumY, mix(12,8,smooth((age-480)/100)));
        double shake = age < SupernovaTiming.AFTERMATH ? 0 : (1 - smooth(impact / 30)) * 0.4;
        double pressure=pulse(age,SupernovaTiming.AFTERMATH,18);
        double lens = mix(69, 55, settle)+pressure*4;
        return new RailgunCameraPath.Pose(offsetX + Math.sin(age * 2.1) * shake,
                height + Math.sin(age * 2.7) * shake, offsetZ,
                0, mix(look,10,settle), 0, Math.sin(age * 1.9) * (shake+pressure*0.8), lens);
    }

    static double random(long seed, int index) {
        long value = seed + index * 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    static int color(int warm, int hot, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        int r = (int) mix(warm >> 16 & 255, hot >> 16 & 255, t);
        int g = (int) mix(warm >> 8 & 255, hot >> 8 & 255, t);
        int b = (int) mix(warm & 255, hot & 255, t);
        return r << 16 | g << 8 | b;
    }
}
