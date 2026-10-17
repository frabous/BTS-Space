package fr.frabous.stellar.client;

import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.SupernovaTiming;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;

/** Labelled stellar cutaway, spherical explosion, then global atmospheric exposure; the server never pauses. */
final class SupernovaCinema extends GuiScreen {
    private final LaunchState launch;
    private final SupernovaSoundtrack soundtrack = new SupernovaSoundtrack();
    private final double[][] stars = new double[1200][4];
    private CinematicCamera camera;
    private double renderedAge;

    SupernovaCinema(LaunchState launch) {
        this.launch = launch;
        Random random = new Random(launch.getSeed());
        for (double[] star : stars) {
            double angle = random.nextDouble()*Math.PI*2, y = random.nextDouble()*2-1, r = Math.sqrt(1-y*y);
            star[0] = Math.cos(angle)*r*4600; star[1] = y*4600; star[2] = Math.sin(angle)*r*4600;
            star[3] = random.nextDouble();
        }
    }
    boolean matches(UUID id) { return launch.getLaunchId().equals(id); }
    double age(float partial) { return Math.max(0, mc.world.getTotalWorldTime()-launch.getStartTick()+partial); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() {
        if (camera == null && mc.player != null) {
            camera = new CinematicCamera(this, launch);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }
    @Override public void onGuiClosed() {
        soundtrack.stop();
        MinecraftForge.EVENT_BUS.unregister(this);
        if (camera != null) { camera.close(); camera = null; }
    }
    @Override public void updateScreen() {
        if (mc.world == null || mc.player == null || !mc.player.isEntityAlive()
                || mc.world.provider.getDimension() != launch.getDimension() || age(0) >= SupernovaTiming.END)
            mc.displayGuiScreen(null);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && mc.currentScreen == this && mc.world != null)
            soundtrack.update(age(0));
    }
    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == ClientControls.SKIP.getKeyCode() || key == 1) StellarServices.client().stopPresentation();
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        if (mc.world == null) return;
        renderedAge = age(partial);
        if (SupernovaVisuals.isSpaceShot(renderedAge)) orbitalScene(renderedAge);
        double darkness = SupernovaVisuals.darkness(renderedAge);
        drawRect(0, 0, width, height, (int)(darkness*72)<<24 | 0x061226);
        SupernovaImpactFrames.vignette(renderedAge,width,height);
        double flash = SupernovaVisuals.flash(renderedAge);
        if (flash > 0) drawRect(0, 0, width, height, (int)(flash*205)<<24 | 0xEFF8FF);
        if (SupernovaVisuals.isSpaceShot(renderedAge)) SupernovaImpactFrames.lensStreak(renderedAge,width,height);
        SupernovaImpactFrames.focusLines(launch.getSeed(),renderedAge,width,height);
        SupernovaImpactFrames.draw(launch.getSeed(),renderedAge,width,height);
        double transition = SupernovaVisuals.transition(renderedAge);
        if (transition > 0) drawRect(0, 0, width, height, (int)(transition*255)<<24);
        int base = Math.max(24, (int)(height*0.075));
        int bars = (int)(base*(1+SupernovaVisuals.letterbox(renderedAge)*1.1));
        drawRect(0, 0, width, bars, 0xFF04080F);
        drawRect(0, height-bars, width, height, 0xFF04080F);
        int lineY = height-bars-5;
        drawRect(12,lineY,width-12,lineY+1,0xFF343D49);
        drawRect(12,lineY,12+(int)((width-24)*Math.min(1,renderedAge/SupernovaTiming.END)),lineY+1,0xFFFFD28C);
        fontRenderer.drawString("SS-04 / SUPERNOVA",12,height-bars+4,0xFFE2B4);
        fontRenderer.drawString(phaseLabel(renderedAge),12,height-bars+14,0x9BADC5);
        fontRenderer.drawString("J / ESC: SKIP",width-fontRenderer.getStringWidth("J / ESC: SKIP")-12,8,0x9BADC5);
        countdown(renderedAge,bars);
        card(renderedAge,SupernovaTiming.STAR+2,"SUPERNOVA","MASSIVE STAR / FINAL HOURS");
        card(renderedAge,SupernovaTiming.COLLAPSE+2,"CORE COLLAPSE","IRON CORE / SCHEMATIC CUTAWAY");
        card(renderedAge,SupernovaTiming.WORLD_RETURN+2,"LIGHT FRONT","30x FASTER THAN THE EJECTA");
    }

    /** Time to the bounce, shown while the revived shock crosses the envelope. */
    private void countdown(double age, int bars) {
        if (age < SupernovaTiming.SHOCK_REVIVAL || age >= SupernovaTiming.DETONATE) return;
        double seconds = (SupernovaTiming.DETONATE-age)/20;
        String text = String.format(java.util.Locale.ROOT,"T-%04.2f",seconds);
        int color = age >= 306 && ((int)(age/2)&1) == 0 ? 0xFF6A4A : 0xFFD28C;
        GL11.glPushMatrix();
        GL11.glTranslated(width-12-fontRenderer.getStringWidth(text)*2,bars+8,0);
        GL11.glScaled(2,2,1);
        fontRenderer.drawStringWithShadow(text,0,0,color);
        GL11.glPopMatrix();
    }

    private void card(double age, double start, String title, String subtitle) {
        double alpha = SupernovaVisuals.card(age,start);
        if (alpha < 0.02) return;
        int a = Math.max(4,(int)(alpha*255))<<24;
        double spread = 1+(1-alpha)*0.25;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glTranslated(width/2.0,height*0.40,0);
        GL11.glScaled(3*spread,3,1);
        fontRenderer.drawStringWithShadow(title,-fontRenderer.getStringWidth(title)/2F,0,a|0xFFF1DA);
        GL11.glPopMatrix();
        int lineWidth = (int)(fontRenderer.getStringWidth(title)*1.5*alpha);
        int y = (int)(height*0.40)+31;
        drawRect(width/2-lineWidth,y,width/2+lineWidth,y+1,a|0xFFD28C);
        fontRenderer.drawString(subtitle,(width-fontRenderer.getStringWidth(subtitle))/2,y+5,a|0x9BADC5);
    }

    private static String phaseLabel(double age) {
        if (age < SupernovaTiming.STAR) return "GLOBAL STELLAR EVENT";
        if (age < SupernovaTiming.COLLAPSE) return "CONVECTION";
        if (age < SupernovaTiming.SHOCK_REVIVAL) return "CORE COLLAPSE / SCHEMATIC";
        if (age < 312) return "REVIVED SHOCK / SCHEMATIC";
        if (age < SupernovaTiming.DETONATE) return "BREAKOUT";
        if (age < SupernovaTiming.IMPACT) return "LIGHT FRONT / SCHEMATIC";
        if (age < SupernovaTiming.AFTERMATH) return "IRRADIATION / TIME-LAPSE x29";
        return "ATMOSPHERIC SHOCK";
    }

    private void orbitalScene(double age) {
        drawRect(0,0,width,height,0xFF01040A);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        RailgunCameraPath.Pose pose = SupernovaVisuals.spaceCamera(age);
        GLU.gluPerspective((float)pose.fov,(float)width/Math.max(1,height),0.15F,6000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            GL11.glRotated(pose.roll,0,0,1);
            GLU.gluLookAt((float)pose.x,(float)pose.y,(float)pose.z,
                    (float)pose.targetX,(float)pose.targetY,(float)pose.targetZ,0,1,0);
            GL11.glPointSize(1.4F);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_POINTS,DefaultVertexFormats.POSITION_COLOR);
            for (double[] star : stars)
                EffectMesh.vertex(b,star[0],star[1],star[2],0xD3E4FF,(float)(0.12+star[3]*0.48));
            Tessellator.getInstance().draw();
            SupernovaScene.drawLocal(launch,age,false,true);
            EmissionBloom.render(() -> SupernovaScene.drawLocal(launch,age,true,true));
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopClientAttrib(); GL11.glPopAttrib(); GL13.glActiveTexture(active);
        }
    }
}
