package fr.frabous.stellar.client;

import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import fr.frabous.stellar.common.StellarRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The remote in the player's own hand: one shared thumb fed by every remote input, a first-person
 * pose that replaces the item swing-dip on use, and the menu hand-off (the menu draws it while open).
 */
final class RemoteFirstPerson {
    static final RemoteThumb THUMB = new RemoteThumb();
    /** Pose relative to vanilla's first-person hand anchor; remote pixels scale to blocks by SCALE. */
    static final float X = -0.1F, Y = 0.27F, Z = 0.02F, YAW = -15, PITCH = -27, ROLL = 9, SCALE = 0.42F / 16;
    private static final double RAISE_SECONDS = 0.28;
    private final RemoteThumb spare = new RemoteThumb();
    private final int[] heldTicks = {-1, -1}, slots = {-1, -1};
    private long lastFrame, raisedAt;
    private boolean menuOpen;

    static void press(RemoteKey key) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && !LaunchController.remote(mc.player).isEmpty()) THUMB.press(key);
    }

    /** Live state for the stack the local player is using; any other stack is drawn at rest. */
    static RemoteModel.Look look(ItemStack stack, boolean labels) {
        Minecraft mc = Minecraft.getMinecraft();
        RemoteModel.Look look = new RemoteModel.Look();
        look.selected = ItemStellarRemote.selected(stack);
        look.age = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9;
        look.labels = labels;
        if (mc.player != null && stack == LaunchController.remote(mc.player)) {
            look.depress = THUMB.depress;
            look.flash = THUMB.flash;
        }
        return look;
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1.0e9;
        lastFrame = now;
        THUMB.update(dt);
        spare.update(dt);
        boolean open = Minecraft.getMinecraft().currentScreen instanceof SkillMenu;
        if (menuOpen && !open) raisedAt = now;
        menuOpen = open;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        for (EnumHand hand : EnumHand.values()) {
            int i = hand.ordinal();
            boolean remote = player != null && player.getHeldItem(hand).getItem() == StellarRegistry.REMOTE;
            int slot = player == null ? -1 : hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            heldTicks[i] = remote ? (slot == slots[i] && heldTicks[i] >= 0 ? heldTicks[i] + 1 : 0) : -1;
            slots[i] = slot;
        }
    }

    @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.getWorld().isRemote || event.getEntityPlayer() != mc.player
                || event.getItemStack().getItem() != StellarRegistry.REMOTE) return;
        if (!mc.player.isSneaking()) press(RemoteKey.FIRE);
        else press(RemoteKey.attack(ItemStellarRemote.selected(event.getItemStack()).next().getFamily().ordinal()));
    }

    @SubscribeEvent public void hand(RenderSpecificHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != StellarRegistry.REMOTE) return;
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof SkillMenu || mc.player == null) return;
        EnumHand hand = event.getHand();
        EnumHandSide handSide = hand == EnumHand.MAIN_HAND ? mc.player.getPrimaryHand() : mc.player.getPrimaryHand().opposite();
        int side = handSide == EnumHandSide.RIGHT ? 1 : -1;
        // Once settled, ignore vanilla's re-equip dip from right clicks; keep it for real hand changes.
        float equip = heldTicks[hand.ordinal()] >= 5 ? 0 : event.getEquipProgress();
        double since = (System.nanoTime() - raisedAt) / 1.0e9;
        if (raisedAt != 0 && since < RAISE_SECONDS) {
            double t = since / RAISE_SECONDS;
            equip = Math.max(equip, (float) ((1 - t) * (1 - t) * (1 - t)));
        }
        float swing = event.getSwingProgress(), root = MathHelper.sqrt(swing), pi = (float) Math.PI;
        RemoteThumb thumb = stack == LaunchController.remote(mc.player) ? THUMB : spare;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(side * -0.4F * MathHelper.sin(root * pi), 0.2F * MathHelper.sin(root * pi * 2),
                    -0.2F * MathHelper.sin(swing * pi));
            GlStateManager.translate(side * 0.56F, -0.52F - 0.6F * equip, -0.72F);
            float arc = MathHelper.sin(swing * swing * pi), lift = MathHelper.sin(root * pi);
            GlStateManager.rotate(side * (45 - 20 * arc), 0, 1, 0);
            GlStateManager.rotate(side * -20 * lift, 0, 0, 1);
            GlStateManager.rotate(-80 * lift, 1, 0, 0);
            GlStateManager.rotate(side * -45, 0, 1, 0);
            GlStateManager.translate(side * X, Y, Z);
            GlStateManager.rotate(side * YAW, 0, 1, 0);
            GlStateManager.rotate(PITCH, 1, 0, 0);
            GlStateManager.rotate(side * ROLL, 0, 0, 1);
            GlStateManager.scale(SCALE, SCALE, SCALE);
            thumb.side(side);
            RemoteModel.Look look = look(stack, true);
            if (thumb == spare) { look.depress = spare.depress; look.flash = spare.flash; }
            RemoteModel.draw(look);
            RemoteHand.draw(mc.player, thumb, 18);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
