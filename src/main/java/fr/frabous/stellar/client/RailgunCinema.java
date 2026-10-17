package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackParameters;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.LaunchTimeline;
import fr.frabous.stellar.api.StellarServices;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;

/** Perspective montage with world-camera bookends, synchronized to the authoritative launch. */
final class RailgunCinema extends GuiScreen {
    private final LaunchState launch;
    private final CinematicSoundtrack soundtrack = new CinematicSoundtrack();
    private final double[][] stars = new double[4500][4];
    private CinematicCamera camera;
    private double renderedAge;
    private static final FloatBuffer BEAM_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECT_MODEL = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECT_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECT_OUT = BufferUtils.createFloatBuffer(3);
    private static final IntBuffer PROJECT_VIEWPORT = BufferUtils.createIntBuffer(16);
    private double[] muzzleScreen;
    private static final double[] BEAM_OFFSETS = {-6, -3, -1, -0.36, -0.22, 0.22, 0.36, 1, 3, 6};
    private static final float[] BEAM_ALPHA = {0, 0.045F, 0.25F, 0.7F, 1, 1, 0.7F, 0.25F, 0.045F, 0};
    private static final double[] WARP_X = new double[600], WARP_Y = new double[600];
    // Direction rows: cos/sin(a), cos/sin(a - spread), cos/sin(a + spread).
    private static final double[][] SPEED_LINE_DIRECTIONS = new double[6][180];
    private static final double[] GLOW_COS = new double[65], GLOW_SIN = new double[65];
    private static final double[] TUNNEL_ANGLES = new double[361];
    private static final double[] TUNNEL_COS = new double[361], TUNNEL_SIN = new double[361];
    private static final double[] TUNNEL_MID_COS = new double[360], TUNNEL_MID_SIN = new double[360];
    private static final double GALAXY_LOG_DISTANCE_RANGE = Math.log(180000.0 / 14000);
    private static final double ORBIT_LOG_DISTANCE_RANGE = Math.log(5600.0 / 400);
    private static final int UPLINK_RGB = AttackParameters.RAILGUN_COLOR_RGB;

    static {
        for (int i = 0; i < WARP_X.length; i++) {
            double a = i * 2.39996323, r = 1 + i % 43 * 0.55;
            WARP_X[i] = Math.cos(a) * r; WARP_Y[i] = Math.sin(a) * r;
        }
        for (int i = 0; i < SPEED_LINE_DIRECTIONS[0].length; i++) {
            double a = i * 2.39996323 + Math.sin(i * 8.71) * 0.18;
            double spread = 0.001 + (i % 4) * 0.0006;
            SPEED_LINE_DIRECTIONS[0][i] = Math.cos(a);
            SPEED_LINE_DIRECTIONS[1][i] = Math.sin(a);
            SPEED_LINE_DIRECTIONS[2][i] = Math.cos(a - spread);
            SPEED_LINE_DIRECTIONS[3][i] = Math.sin(a - spread);
            SPEED_LINE_DIRECTIONS[4][i] = Math.cos(a + spread);
            SPEED_LINE_DIRECTIONS[5][i] = Math.sin(a + spread);
        }
        for (int i = 0; i < GLOW_COS.length; i++) {
            double a = i * Math.PI / 32;
            GLOW_COS[i] = Math.cos(a); GLOW_SIN[i] = Math.sin(a);
        }
        for (int i = 0; i < TUNNEL_ANGLES.length; i++) {
            double a = i * Math.PI / 180;
            TUNNEL_ANGLES[i] = a; TUNNEL_COS[i] = Math.cos(a); TUNNEL_SIN[i] = Math.sin(a);
        }
        for (int i = 0; i < TUNNEL_MID_COS.length; i++) {
            double a = (TUNNEL_ANGLES[i] + TUNNEL_ANGLES[i + 1]) * 0.5;
            TUNNEL_MID_COS[i] = Math.cos(a); TUNNEL_MID_SIN[i] = Math.sin(a);
        }
    }

    RailgunCinema(LaunchState launch) {
        this.launch = launch;
        Random random = new Random(launch.getSeed());
        for (double[] star : stars) {
            double a = random.nextDouble() * Math.PI * 2, y = random.nextDouble() * 2 - 1;
            double r = Math.sqrt(1 - y * y);
            star[0] = Math.cos(a) * r * 2800; star[1] = y * 2800; star[2] = Math.sin(a) * r * 2800;
            star[3] = random.nextDouble();
        }
    }

    boolean matches(UUID id) { return launch.getLaunchId().equals(id); }
    double age(float partialTicks) { return Math.max(0, mc.world.getTotalWorldTime() - launch.getStartTick() + partialTicks); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() { if (camera == null && mc.player != null) camera = new CinematicCamera(this, launch); }
    @Override public void onGuiClosed() {
        soundtrack.stop();
        if (camera != null) { camera.close(); camera = null; }
    }
    @Override public void updateScreen() {
        if (mc.world == null || mc.world.provider.getDimension() != launch.getDimension()
                || age(0) >= LaunchTimeline.totalTicks(launch.getAttack())) {
            mc.displayGuiScreen(null); return;
        }
        soundtrack.update(launch, age(0));
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == ClientControls.SKIP.getKeyCode() || keyCode == 1) StellarServices.client().stopPresentation();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.world == null) return;
        double age = age(partialTicks), p = RailgunCameraPath.progress(age);
        renderedAge = age;
        RailgunCameraPath.Shot shot = RailgunCameraPath.shot(age);
        boolean pullback = RailgunPullback.active(age);
        if (RailgunCameraPath.isWorldShot(shot) && !pullback) {
            overlay(age, p, shot); return;
        }
        float opacity = pullback ? (float) RailgunPullback.opacity(age) : 1;
        drawRect(0, 0, width, height, ((int) (opacity * 255) << 24) | 0x020309);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        RailgunCameraPath.Pose pose = RailgunCameraPath.pose(age);
        double scale = pullback ? RailgunPullback.renderScale(age) : 1;
        GLU.gluPerspective((float) pose.fov, (float) width / Math.max(1, height), 0.15F, 6000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double shake = cannonShake(age, shot);
            GL11.glRotated(pose.roll + Math.sin(age * 3.1) * shake, 0, 0, 1);
            GL11.glTranslated(Math.sin(age * 4.3) * shake * 0.25, Math.sin(age * 5.9 + 1) * shake * 0.25, 0);
            GLU.gluLookAt((float) (pose.x * scale), (float) (pose.y * scale), (float) (pose.z * scale),
                    (float) (pose.targetX * scale), (float) (pose.targetY * scale), (float) (pose.targetZ * scale), 0, 1, 0);
            muzzleScreen = shot == RailgunCameraPath.Shot.MUZZLE ? project(61, 0, 0) : null;
            GL11.glPushMatrix();
            if (pullback) GL11.glTranslated(pose.x * scale, pose.y * scale, pose.z * scale);
            starfield(opacity);
            GL11.glPopMatrix();
            scene(age, p, shot, false);
            EmissionBloom.render(() -> scene(age, p, shot, true));
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(active);
        }
        overlay(age, p, shot);
    }

    /** Scene point to GUI coordinates with the current matrices, or null behind the lens. */
    private double[] project(double x, double y, double z) {
        PROJECT_MODEL.clear(); PROJECT_PROJECTION.clear(); PROJECT_VIEWPORT.clear(); PROJECT_OUT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, PROJECT_MODEL);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECT_PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, PROJECT_VIEWPORT);
        // Reject points behind the camera: eye-space z must be negative.
        double eyeZ = PROJECT_MODEL.get(2) * x + PROJECT_MODEL.get(6) * y + PROJECT_MODEL.get(10) * z + PROJECT_MODEL.get(14);
        if (eyeZ >= 0 || !GLU.gluProject((float) x, (float) y, (float) z, PROJECT_MODEL, PROJECT_PROJECTION,
                PROJECT_VIEWPORT, PROJECT_OUT)) return null;
        double vw = Math.max(1, PROJECT_VIEWPORT.get(2)), vh = Math.max(1, PROJECT_VIEWPORT.get(3));
        return new double[] {(PROJECT_OUT.get(0) - PROJECT_VIEWPORT.get(0)) / vw * width,
                height - (PROJECT_OUT.get(1) - PROJECT_VIEWPORT.get(1)) / vh * height};
    }

    /** Hum that builds with the charge, a dead-still inhale, then a hard decaying kick at release. */
    private static double cannonShake(double age, RailgunCameraPath.Shot shot) {
        if (shot == RailgunCameraPath.Shot.MUZZLE) return 2.4 * Math.exp(-(age - RailgunVisualTiming.FIRE) * 0.45);
        if (shot == RailgunCameraPath.Shot.CHARGE || shot == RailgunCameraPath.Shot.CHAMBER)
            return 0.35 * RailgunVisualTiming.charge(age) * (1 - RailgunVisualTiming.inhale(age));
        return 0;
    }

    private void scene(double age, double p, RailgunCameraPath.Shot shot, boolean emission) {
        if (RailgunPullback.active(age)) { pullbackScene(age, emission); return; }
        switch (shot) {
            case CANNON_WIDE: case BARREL_FLYBY: case REAR: case CHARGE: case CHAMBER: case MUZZLE:
                if (!emission) {
                    GL11.glPushMatrix(); GL11.glTranslated(-120, -280, -250);
                    CelestialModels.galaxy(800, age); GL11.glPopMatrix();
                }
                cannon(age, emission);
                break;
            case WARP:
                if (!emission) {
                    if (age >= 262 && age < 269) {
                        // The camera races back to Earth before entering the atmospheric tunnel.
                        double approach = RailgunCameraPath.smooth((age - 262.5) / 4.5);
                        GL11.glPushMatrix(); GL11.glTranslated(0, -4, RailgunCameraPath.mix(-140, 80, approach));
                        CelestialModels.earth(32, age); GL11.glPopMatrix();
                    }
                    warp(age, p);
                } else beam(-180, -95, 80, -2, -4, 105, 0.22, 0xFF3158, 1);
                // The beam front leads the camera: a hot spearhead just ahead of the lens.
                EffectMesh.billboard(-2, -4, 105, 3.5 + Math.sin(age * 2.7) * 0.4, 0xFF3158, 0.9F);
                break;
            default: break;
        }
    }

    /** Cannon close-ups: spool, inhale, release. Model space +X is the firing direction. */
    private void cannon(double age, boolean emission) {
        double charge = RailgunVisualTiming.charge(age);
        double firing = RailgunVisualTiming.firing(age), inhale = RailgunVisualTiming.inhale(age);
        double since = age - RailgunVisualTiming.FIRE;
        GL11.glPushMatrix();
        GL11.glTranslated(-RailgunVisualTiming.recoil(age), 0, 0);
        if (emission) OrbitalRailgunModel.emission(age, charge, firing);
        else OrbitalRailgunModel.render(age, charge, firing);
        if (charge > 0 && firing <= 0) beam(-38, 0, 0, 54, 0, 0,
                0.08 + charge * 0.22 + inhale * 0.3, 0xFF3158, (float) charge);
        GL11.glPopMatrix();
        uplinkGuide(age);
        if (since < 0) return;
        // Released column: punches out in a fraction of a tick, overshoots, then settles to its sustained width.
        double width = 0.6 + firing * (2.0 + 1.6 * Math.exp(-since * 0.7));
        double reach = 54 + RailgunCameraPath.smooth(since / 1.4) * 2600;
        beam(-38 - RailgunVisualTiming.recoil(age), 0, 0, reach, 0, 0, width, 0xFF3158, 1);
        EffectMesh.billboard(61, 0, 0, 18 * Math.exp(-since * 0.45) + 5, 0xFF3158, (float) Math.min(1, 0.4 + Math.exp(-since * 0.5)));
        // Mach discs: staggered pressure rings race down the beam and spread as they slow.
        for (int ring = 0; ring < 4; ring++) {
            double t = since - ring * 1.1;
            if (t <= 0) continue;
            double travel = 55 * (1 - Math.exp(-t * 0.35)) * (1 + ring * 0.6);
            float alpha = (float) (Math.exp(-t * 0.32) * (ring == 0 ? 1 : 0.75));
            GL11.glPushMatrix(); GL11.glTranslated(60 + travel, 0, 0); GL11.glRotated(90, 0, 0, 1);
            EffectMesh.glowRing(0, 0, 0, 5 + t * (6 + ring * 2.5), 0.7 + t * 0.25, ring % 2 == 0 ? 0xFF547C : 0xFFB9D1, alpha);
            GL11.glPopMatrix();
        }
        // Backblast collar around the barrel: the recoil wave rolling toward the breech.
        double back = since * 9;
        if (back < 110) {
            GL11.glPushMatrix(); GL11.glTranslated(50 - back, 0, 0); GL11.glRotated(90, 0, 0, 1);
            EffectMesh.glowRing(0, 0, 0, 16 + back * 0.08, 0.9, 0xFF2448, (float) (0.55 * (1 - back / 110)));
            GL11.glPopMatrix();
        }
        if (!emission) muzzleSparks(since);
    }

    /** Ballistic plasma droplets thrown out of the muzzle in a forward cone. */
    private static void muzzleSparks(double since) {
        if (since > 14) return;
        GL11.glPointSize(2.2F);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 90; i++) {
            double a = i * 2.39996323, spread = 0.15 + (i % 9) * 0.07, speed = 4 + (i % 13) * 0.9;
            double d = speed * since * (1 - since / 40);
            float alpha = (float) (Math.max(0, 1 - since / (6 + i % 8)));
            EffectMesh.vertex(b, 61 + d, Math.cos(a) * d * spread, Math.sin(a) * d * spread,
                    i % 3 == 0 ? 0xFFF4F8 : 0xFF6A88, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private void starfield(float opacity) {
        GL11.glPointSize(1.6F);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (double[] star : stars) {
            EffectMesh.vertex(b, star[0], star[1], star[2], star[3] > 0.96 ? 0xC0D9FF : 0xE7EBF4,
                    (float) (0.18 + star[3] * 0.7) * opacity);
        }
        Tessellator.getInstance().draw();
    }

    private void pullbackScene(double age, boolean emission) {
        double distance = RailgunPullback.distance(age), scale = RailgunPullback.renderScaleForDistance(distance);
        float opacity = (float) RailgunPullback.opacity(age);
        double galaxyProgress = !emission || distance < 180000
                ? RailgunCameraPath.smooth(Math.log(distance / 14000) / GALAXY_LOG_DISTANCE_RANGE) : 0;
        GL11.glPushMatrix(); GL11.glScaled(scale, scale, scale);
        try {
            if (!emission) {
                RailgunCameraPath.Pose pose = RailgunPullback.pose(age, distance);
                float galaxy = (float) galaxyProgress;
                if (galaxy > 0) {
                    GL11.glPushMatrix(); GL11.glTranslated(RailgunPullback.GALAXY_X, -300, RailgunPullback.GALAXY_Z);
                    CelestialModels.galaxy(RailgunPullback.GALAXY_RADIUS, age, galaxy);
                    GL11.glPopMatrix();
                }
                float earth = opacity * (float) RailgunCameraPath.smooth((RailgunPullback.EARTH_RADIUS / distance - 0.0002) / 0.0004);
                if (earth > 0) CelestialModels.earth(RailgunPullback.EARTH_RADIUS, age, earth);
                GL11.glPushMatrix(); GL11.glTranslated(RailgunPullback.SUN_X, 0, RailgunPullback.SUN_Z);
                double orbitProgress = RailgunCameraPath.smooth(Math.log(distance / 400) / ORBIT_LOG_DISTANCE_RANGE);
                float orbitAlpha = (float) (orbitProgress * (1 - galaxyProgress));
                for (int i = 0; i < RailgunPullback.ORBITS.length; i++) {
                    double r = RailgunPullback.ORBITS[i], a = RailgunPullback.orbitAngle(i);
                    if (orbitAlpha > 0) EffectMesh.ring(0, 0, 0, r, r * 0.002, 0x8CF7EB, orbitAlpha * 0.65F);
                    if (i == 2) continue; // Earth remains at its original position throughout the zoom.
                    double px = Math.cos(a) * r, pz = Math.sin(a) * r;
                    double bodyDistance = Math.sqrt(Math.pow(pose.x - RailgunPullback.SUN_X - px, 2)
                            + pose.y * pose.y + Math.pow(pose.z - RailgunPullback.SUN_Z - pz, 2));
                    double radius = i == 5 ? 200 : i == 4 ? 140 : 25;
                    if (radius / bodyDistance < 0.0005 || bodyDistance * scale > 5500) continue;
                    GL11.glPushMatrix(); GL11.glTranslated(px, 0, pz);
                    if (i == 5) CelestialModels.saturn(radius, age);
                    else EffectMesh.crystal(0, -radius, 0, radius, radius * 2, 0xC5DAED, 1);
                    GL11.glPopMatrix();
                }
                GL11.glPopMatrix();
            }
            if (distance < 180000) {
                GL11.glPushMatrix(); GL11.glTranslated(RailgunPullback.SUN_X, 0, RailgunPullback.SUN_Z);
                float sun = (float) (1 - galaxyProgress);
                EffectMesh.crystal(0, -110, 0, 110, 220, 0xFFF5DA, opacity * sun);
                GL11.glPopMatrix();
            }
            uplink(age, distance, opacity);
        } finally { GL11.glPopMatrix(); }
    }

    /** Designation uplink: leaves the target radially and pulses out toward the cannon, pinned at every scale. */
    private static void uplink(double age, double distance, float opacity) {
        double[] n = RailgunPullback.UPLINK;
        double r = RailgunPullback.EARTH_RADIUS * 1.01, x = n[0] * r, y = n[1] * r, z = n[2] * r;
        // Sizes scale with the eye distance so the line, packets and pin keep the same on-screen weight.
        double reach = RailgunPullback.GALAXY_RADIUS * 4, width = Math.max(0.1, distance * 0.0006);
        beam(x, y, z, x + n[0] * reach, y + n[1] * reach, z + n[2] * reach, width, UPLINK_RGB, opacity * 0.5F);
        for (int k = 0; k < 5; k++) {
            double phase = (age * 0.04 + k / 5.0) % 1, s = distance * 3.5 * phase * phase;
            double end = s + distance * (0.04 + 0.3 * phase);
            beam(x + n[0] * s, y + n[1] * s, z + n[2] * s, x + n[0] * end, y + n[1] * end, z + n[2] * end,
                    width * 2.4, UPLINK_RGB, opacity * (float) Math.sin(phase * Math.PI));
        }
        double pin = Math.max(0.8, distance * 0.012);
        EffectMesh.billboard(x, y, z, pin, UPLINK_RGB, opacity);
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z);
        GL11.glRotated(Math.toDegrees(Math.acos(n[1])), n[2], 0, -n[0]);
        for (int k = 0; k < 2; k++) {
            double ping = ((age - RailgunVisualTiming.LOCK) / 20 + k * 0.5) % 1;
            EffectMesh.glowRing(0, 0, 0, pin * (0.4 + ping * 1.6), pin * 0.06, UPLINK_RGB, opacity * (float) (1 - ping) * 0.8F);
        }
        GL11.glPopMatrix();
    }

    /** The uplink arrives down the firing line and stays on it: the shot will ride the same path back. */
    private static void uplinkGuide(double age) {
        float fade = (float) (1 - RailgunCameraPath.smooth((age - RailgunVisualTiming.FIRE) / 0.5));
        if (fade <= 0) return;
        double since = age - RailgunVisualTiming.UPLINK;
        double front = 61 + 2600 * (1 - RailgunCameraPath.smooth((age - 194) / (RailgunVisualTiming.UPLINK - 194)));
        beam(front, 0, 0, 2600, 0, 0, 0.12, UPLINK_RGB, 0.55F * fade);
        for (int k = 0; k < 4; k++) {
            double phase = (age * 0.05 + k / 4.0) % 1, at = 61 + 900 * (1 - phase) * (1 - phase);
            if (at < front) continue;
            beam(at, 0, 0, at + 6 + 60 * (1 - phase), 0, 0, 0.28, UPLINK_RGB, 0.8F * fade * (float) Math.sin(phase * Math.PI));
        }
        if (since < 0) { EffectMesh.billboard(front, 0, 0, 6, UPLINK_RGB, 0.9F); return; }
        // Contact: the muzzle acknowledges the lock with one ping, then keeps a small pilot light.
        float ping = (float) Math.exp(-since * 0.35);
        EffectMesh.billboard(61, 0, 0, 3 + 11 * ping, UPLINK_RGB, fade * (0.35F + 0.65F * ping));
        GL11.glPushMatrix(); GL11.glTranslated(61, 0, 0); GL11.glRotated(90, 0, 0, 1);
        EffectMesh.glowRing(0, 0, 0, 3 + since * 2.2, 0.5, UPLINK_RGB, ping * 0.9F);
        GL11.glPopMatrix();
    }

    private void warp(double age, double p) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 600; i++) {
            double z = -550 + (i * 19 + age * 100) % 680;
            float visibility = (float) (RailgunCameraPath.smooth((z + 550) / 90)
                    * (1 - RailgunCameraPath.smooth((z - 30) / 100)));
            EffectMesh.vertex(b, WARP_X[i], WARP_Y[i], z, 0xFFC2D5, visibility * 0.05F);
            EffectMesh.vertex(b, WARP_X[i], WARP_Y[i], z + 30 + p * 100, 0xFFFFFF, visibility * 0.9F);
        }
        Tessellator.getInstance().draw();
        beam(-180, -95, 80, -2, -4, 105, 0.22, 0xFF3158, 1);
    }

    static void beam(double x0, double y0, double z0, double x1, double y1, double z1,
                     double radius, int color, float alpha) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0 || alpha <= 0) return;
        GL11.glPushMatrix(); GL11.glTranslated(x0, y0, z0);
        if (Math.abs(dy / length) < 0.99999) GL11.glRotated(Math.toDegrees(Math.acos(dy / length)), dz, 0, -dx);
        else if (dy < 0) GL11.glRotated(180, 1, 0, 0);
        // Derive the halo plane from this scene's view, including the cinema's private camera.
        BEAM_VIEW.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, BEAM_VIEW);
        double sx = BEAM_VIEW.get(10), sz = -BEAM_VIEW.get(2), sideLength = Math.hypot(sx, sz);
        if (sideLength < 0.0001) { sx = BEAM_VIEW.get(0); sz = BEAM_VIEW.get(8); sideLength = Math.hypot(sx, sz); }
        sx /= sideLength; sz /= sideLength;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < BEAM_OFFSETS.length; i++) {
            int rgb = Math.abs(BEAM_OFFSETS[i]) <= 0.22 ? 0xFFF6FA
                    : Math.abs(BEAM_OFFSETS[i]) <= 0.36 ? 0xFFB9D1 : color;
            double offset = radius * BEAM_OFFSETS[i];
            EffectMesh.vertex(b, sx * offset, 0, sz * offset, rgb, alpha * BEAM_ALPHA[i]);
            EffectMesh.vertex(b, sx * offset, length, sz * offset, rgb, alpha * BEAM_ALPHA[i]);
        }
        Tessellator.getInstance().draw();
        EffectMesh.column(0, 0, 0, length, radius * 0.22, 0xFFF6FA, alpha * 0.8F);
        GL11.glPopMatrix();
    }

    private void overlay(double age, double p, RailgunCameraPath.Shot shot) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double cloud = RailgunPullback.clouds(age);
            if (cloud > 0) {
                double travel = (age - 52) / 26;
                wash(0xC4D8EF, (float) cloud * 0.92F);
                for (int i = 0; i < 22; i++) {
                    double y = (i * 0.17 - travel * 1.2 + 2) % 1.4;
                    float alpha = (float) (Math.sin(y * Math.PI / 1.4) * 0.32 * cloud);
                    glow(width * (0.5 + 0.7 * Math.sin(i * 8.1)), height * y,
                            height * (0.25 + (i % 3) * 0.1), travel < 0.4 ? 0xEEF0FA : 0xFFBCCF, alpha);
                }
                // Seen from below while climbing; once in space the uplink is drawn in the scene instead.
                flare(width * 0.5, height * 0.5, height * 0.12, 0xFF3158,
                        (float) (cloud * 0.65 * (1 - RailgunCameraPath.smooth((age - RailgunPullback.START) / 5))));
            }
            if (shot == RailgunCameraPath.Shot.CHARGE || shot == RailgunCameraPath.Shot.CHAMBER) {
                double charge = RailgunVisualTiming.charge(age), inhale = RailgunVisualTiming.inhale(age);
                glow(width * 0.5, height * 0.5, height * (0.10 + charge * 0.3), 0xFF204B, (float) (charge * 0.12 * (1 - inhale)));
                vignette((float) (0.25 + charge * 0.3 + inhale * 0.4));
            }
            if (shot == RailgunCameraPath.Shot.MUZZLE) {
                double since = age - RailgunVisualTiming.FIRE;
                wash(0xFF2A4C, (float) (0.3 * Math.exp(-since * 0.5)));
                if (muzzleScreen != null) {
                    anamorphic(muzzleScreen[0], muzzleScreen[1], (float) Math.exp(-since * 0.35));
                    flare(muzzleScreen[0], muzzleScreen[1], height * (0.12 + 0.2 * Math.exp(-since * 0.6)), 0xFF3158,
                            (float) Math.min(1, 0.5 + Math.exp(-since * 0.5)));
                }
                vignette((float) (0.55 * Math.exp(-since * 0.3)));
            }
            if (shot == RailgunCameraPath.Shot.WARP) {
                speedLines(age, p);
                transitFlare(width * 0.49, height * 0.55);
                double tunnel = RailgunCameraPath.smooth((age - 268) / 0.8)
                        * (1 - RailgunCameraPath.smooth((age - 272) / 2));
                if (tunnel > 0) shockTunnel(age, tunnel);
            }
            if (shot == RailgunCameraPath.Shot.SKY || shot == RailgunCameraPath.Shot.DESCENT) {
                double head = RailgunVisualTiming.headHeight(age);
                double near = 1 - head / RailgunVisualTiming.DESCENT_HEIGHT;
                if (shot == RailgunCameraPath.Shot.SKY) wash(0x70549E, (float) (0.12 * (1 - p)));
                double[] point = camera == null ? null : camera.projectBeamPoint(head, width, height);
                if (point != null) flare(point[0], point[1], height * (0.05 + near * 0.13), 0xFF3158, 1);
                vignette((float) (near * 0.45));
            }
            if (shot == RailgunCameraPath.Shot.DESCENT) {
                double arrival = RailgunCameraPath.smooth((age - RailgunVisualTiming.ARRIVAL) / 0.7);
                wash(0xFF2145, (float) (arrival * 0.38));
                halftone((float) (arrival * (1 - p) * 0.65));
            }
            if (shot == RailgunCameraPath.Shot.IMPACT) {
                double since = age - RailgunVisualTiming.IMPACT;
                double fade = 1 - RailgunCameraPath.smooth(since / 4);
                wash(0xFFD5DF, (float) fade * 0.62F);
                glow(width * 0.52, height * 0.58, height * 0.6, 0xFF1034,
                        (float) (0.09 * RailgunVisualTiming.beamAlpha(age)));
                // Pressure ring sweeping across the frame as the ground shock passes the camera.
                if (since < 14) shockFront(width * 0.52, height * 0.58, since);
            }
            double terminal = RailgunVisualTiming.terminalFlash(age);
            if (terminal > 0) wash(0xFFF1F6, (float) terminal * 0.9F);
            letterbox(age);
        } finally { GL11.glPopAttrib(); GL13.glActiveTexture(active); }
        // One-frame hard whites sell the release and the contact before any colour returns.
        double whiteout = 0;
        if (shot == RailgunCameraPath.Shot.MUZZLE && age >= RailgunVisualTiming.FIRE)
            whiteout = 1 - RailgunCameraPath.smooth((age - RailgunVisualTiming.FIRE - 0.3) / 1.1);
        else if (shot == RailgunCameraPath.Shot.IMPACT && age >= RailgunVisualTiming.IMPACT)
            whiteout = 1 - RailgunCameraPath.smooth((age - RailgunVisualTiming.IMPACT - 0.2) / 1.0);
        int whiteAlpha = (int) (255 * whiteout);
        if (whiteAlpha > 0) drawRect(0, 0, width, height, whiteAlpha << 24 | 0xFFFFFF);
    }

    /** Cinematic bars ease in with the lock and out with the final fade. */
    private void letterbox(double age) {
        double amount = RailgunCameraPath.smooth(age / 8)
                * (1 - RailgunCameraPath.smooth((age - (RailgunVisualTiming.END - 12)) / 12));
        double bar = height * 0.085 * amount;
        if (bar < 0.5) return;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int side = 0; side < 2; side++) {
            double y0 = side == 0 ? 0 : height - bar, y1 = side == 0 ? bar : height;
            EffectMesh.vertex(b, 0, y0, 0, 0, 1); EffectMesh.vertex(b, 0, y1, 0, 0, 1);
            EffectMesh.vertex(b, width, y1, 0, 0, 1); EffectMesh.vertex(b, width, y0, 0, 0, 1);
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    /** Darkened frame edges that pull the eye toward the energy source. */
    private void vignette(float strength) {
        if (strength <= 0) return;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double cx = width * 0.5, cy = height * 0.5, inner = height * 0.42, outer = Math.hypot(width, height) * 0.62;
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 64; i++) {
            double ax = GLOW_COS[i] * width / height, ay = GLOW_SIN[i], bx = GLOW_COS[i + 1] * width / height, by = GLOW_SIN[i + 1];
            EffectMesh.vertex(b, cx + ax * inner, cy + ay * inner, 0, 0, 0);
            EffectMesh.vertex(b, cx + ax * outer, cy + ay * outer, 0, 0x050002, strength);
            EffectMesh.vertex(b, cx + bx * outer, cy + by * outer, 0, 0x050002, strength);
            EffectMesh.vertex(b, cx + ax * inner, cy + ay * inner, 0, 0, 0);
            EffectMesh.vertex(b, cx + bx * outer, cy + by * outer, 0, 0x050002, strength);
            EffectMesh.vertex(b, cx + bx * inner, cy + by * inner, 0, 0, 0);
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    /** Horizontal lens streak through the muzzle flash. */
    private void anamorphic(double x, double y, float alpha) {
        if (alpha <= 0.01F) return;
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int layer = 0; layer < 2; layer++) {
            double thick = layer == 0 ? height * 0.012 : height * 0.045;
            float core = alpha * (layer == 0 ? 1 : 0.35F);
            int rgb = layer == 0 ? 0xFFE8F0 : 0xFF3158;
            for (int side = -1; side <= 1; side += 2) {
                double end = side < 0 ? 0 : width;
                EffectMesh.vertex(b, x, y - thick, 0, rgb, 0);
                EffectMesh.vertex(b, x, y, 0, rgb, core);
                EffectMesh.vertex(b, end, y, 0, rgb, 0);
                EffectMesh.vertex(b, end, y - thick * 0.2, 0, rgb, 0);
                EffectMesh.vertex(b, x, y, 0, rgb, core);
                EffectMesh.vertex(b, x, y + thick, 0, rgb, 0);
                EffectMesh.vertex(b, end, y + thick * 0.2, 0, rgb, 0);
                EffectMesh.vertex(b, end, y, 0, rgb, 0);
            }
        }
        Tessellator.getInstance().draw();
    }

    /** Thin bright ring expanding past the frame edges. */
    private void shockFront(double x, double y, double since) {
        double r = height * (0.15 + since * 0.16), w = height * 0.05;
        float alpha = (float) (0.55 * (1 - since / 14));
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 64; i++) {
            double c = GLOW_COS[i], s = GLOW_SIN[i], nc = GLOW_COS[i + 1], ns = GLOW_SIN[i + 1];
            for (int half = 0; half < 2; half++) {
                double r0 = half == 0 ? r - w : r, r1 = half == 0 ? r : r + w;
                float a0 = half == 0 ? 0 : alpha, a1 = half == 0 ? alpha : 0;
                EffectMesh.vertex(b, x + c * r0, y + s * r0, 0, 0xFFE3EA, a0);
                EffectMesh.vertex(b, x + nc * r0, y + ns * r0, 0, 0xFFE3EA, a0);
                EffectMesh.vertex(b, x + nc * r1, y + ns * r1, 0, 0xFFE3EA, a1);
                EffectMesh.vertex(b, x + c * r1, y + s * r1, 0, 0xFFE3EA, a1);
            }
        }
        Tessellator.getInstance().draw();
    }

    private void transitFlare(double x, double y) {
        flare(x, y, height * 0.09, 0xFF3158, 0.85F);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        EffectMesh.vertex(b, 0, y - 1, 0, 0xFF3158, 0.12F);
        EffectMesh.vertex(b, x, y - 1, 0, 0xFFE1EE, 0.95F);
        EffectMesh.vertex(b, x, y + 1, 0, 0xFFE1EE, 0.95F);
        EffectMesh.vertex(b, 0, y + 1, 0, 0xFF3158, 0.12F);
        EffectMesh.vertex(b, x, y - 1, 0, 0xFFE1EE, 0.95F);
        EffectMesh.vertex(b, width, y - 1, 0, 0xFF3158, 0.12F);
        EffectMesh.vertex(b, width, y + 1, 0, 0xFF3158, 0.12F);
        EffectMesh.vertex(b, x, y + 1, 0, 0xFFE1EE, 0.95F);
        Tessellator.getInstance().draw();
    }

    private void halftone(float alpha) {
        if (alpha <= 0) return;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double step = Math.max(3, height / 100.0), size = step * 0.42;
        for (int row = 0; row * step < height; row++) {
            for (int column = 0; column * step < width; column++) {
                double x = column * step + (row % 2) * step * 0.5, y = row * step;
                EffectMesh.vertex(b, x - size, y, 0, 0xFF1745, alpha);
                EffectMesh.vertex(b, x, y - size, 0, 0xFF1745, alpha);
                EffectMesh.vertex(b, x + size, y, 0, 0xFF1745, alpha);
                EffectMesh.vertex(b, x, y + size, 0, 0xFF1745, alpha);
            }
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private void wash(int color, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        EffectMesh.vertex(b, 0, 0, 0, color, alpha); EffectMesh.vertex(b, width, 0, 0, color, alpha);
        EffectMesh.vertex(b, width, height, 0, color, alpha); EffectMesh.vertex(b, 0, height, 0, color, alpha);
        Tessellator.getInstance().draw();
    }

    private static void glow(double x, double y, double r, int color, float alpha) {
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 64; i++) {
            EffectMesh.vertex(b, x, y, 0, color, alpha);
            EffectMesh.vertex(b, x + GLOW_COS[i] * r, y + GLOW_SIN[i] * r, 0, color, 0);
            EffectMesh.vertex(b, x + GLOW_COS[i + 1] * r, y + GLOW_SIN[i + 1] * r, 0, color, 0);
        }
        Tessellator.getInstance().draw();
    }

    private void flare(double x, double y, double r, int color, float alpha) {
        glow(x, y, r, color, alpha * 0.7F);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            EffectMesh.vertex(b, x + Math.sin(a) * 2, y - Math.cos(a) * 2, 0, 0xFFFFFF, alpha);
            EffectMesh.vertex(b, x + Math.cos(a) * r * 2, y + Math.sin(a) * r * 2, 0, color, 0);
            EffectMesh.vertex(b, x - Math.sin(a) * 2, y + Math.cos(a) * 2, 0, 0xFFFFFF, alpha);
        }
        Tessellator.getInstance().draw();
    }

    private void speedLines(double age, double p) {
        double cx = width * 0.52, cy = height * 0.47, radius = Math.hypot(width, height);
        glow(cx, cy, radius, 0xFF1641, 0.24F);
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 180; i++) {
            double offset = (i * 0.6180339 + age * 0.12) % 1;
            double r = radius * (0.04 + offset * 0.72);
            float opacity = (float) Math.pow(Math.sin(offset * Math.PI), 2);
            EffectMesh.vertex(b, cx + SPEED_LINE_DIRECTIONS[0][i] * r, cy + SPEED_LINE_DIRECTIONS[1][i] * r, 0, 0xFFD8E2, 0);
            EffectMesh.vertex(b, cx + SPEED_LINE_DIRECTIONS[2][i] * radius, cy + SPEED_LINE_DIRECTIONS[3][i] * radius, 0, 0xFFEAF1, opacity * 0.8F);
            EffectMesh.vertex(b, cx + SPEED_LINE_DIRECTIONS[4][i] * radius, cy + SPEED_LINE_DIRECTIONS[5][i] * radius, 0, 0xFF647F, opacity * 0.6F);
        }
        Tessellator.getInstance().draw();
    }

    private void shockTunnel(double age, double intensity) {
        wash(0xFFF3F5, (float) intensity * 0.95F);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double cx = width * 0.52, cy = height * 0.47, r = height * 0.24;
        BufferBuilder b = Tessellator.getInstance().getBuffer(); b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 360; i++) {
            double edge = tunnelEdge(TUNNEL_ANGLES[i], age, r), next = tunnelEdge(TUNNEL_ANGLES[i + 1], age, r);
            float alpha = (float) intensity;
            EffectMesh.vertex(b, cx, cy, 0, 0x140007, alpha);
            EffectMesh.vertex(b, cx + TUNNEL_COS[i] * edge, cy + TUNNEL_SIN[i] * edge, 0, 0x480516, alpha);
            EffectMesh.vertex(b, cx + TUNNEL_COS[i + 1] * next, cy + TUNNEL_SIN[i + 1] * next, 0, 0x480516, alpha);
            double reach = edge + height * (0.06 + (0.5 + 0.5 * Math.sin(i * 2.39996323 + age * 0.18)) * 0.47);
            EffectMesh.vertex(b, cx + TUNNEL_COS[i] * edge * 0.88, cy + TUNNEL_SIN[i] * edge * 0.88, 0, 0x43000D, alpha);
            EffectMesh.vertex(b, cx + TUNNEL_MID_COS[i] * reach, cy + TUNNEL_MID_SIN[i] * reach, 0, 0x720D24, 0);
            EffectMesh.vertex(b, cx + TUNNEL_COS[i + 1] * next * 0.88, cy + TUNNEL_SIN[i + 1] * next * 0.88, 0, 0x43000D, alpha);
        }
        Tessellator.getInstance().draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        flare(cx, cy, height * 0.08, 0xFF3158, (float) (0.4 * intensity));
    }

    private static double tunnelEdge(double angle, double age, double radius) {
        return radius * (1 + 0.28 * Math.sin(angle * 3 + age * 0.13)
                + 0.17 * Math.sin(angle * 7 - age * 0.09)
                + 0.06 * Math.sin(angle * 19 + age * 0.21));
    }
}
