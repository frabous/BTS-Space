package fr.frabous.stellar.client;

import fr.frabous.stellar.api.AttackFamily;
import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.StellarServices;
import fr.frabous.stellar.api.network.StellarNetwork;
import fr.frabous.stellar.common.ItemStellarRemote;
import fr.frabous.stellar.common.LaunchController;
import fr.frabous.stellar.common.StellarRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class ClientControls {
    static final KeyBinding MENU = key("menu", Keyboard.KEY_H);
    static final KeyBinding RAILGUN = key("railgun", Keyboard.KEY_K);
    static final KeyBinding BLACKHOLE = key("blackhole", Keyboard.KEY_B);
    /** Carrier family; the historic id keeps players' saved binding. Repeated presses cycle payloads. */
    static final KeyBinding CARRIER = key("moon_attract", Keyboard.KEY_M);
    static final KeyBinding SUPERNOVA = key("supernova", Keyboard.KEY_N);
    static final KeyBinding GRAVITY = key("gravity_inversion", Keyboard.KEY_G);
    static final KeyBinding SKIP = key("skip", Keyboard.KEY_J);

    private ClientControls() { }
    private static KeyBinding key(String name, int code) { return new KeyBinding("key.stellar." + name, code, "key.categories.stellar"); }
    static void register() { for (KeyBinding key : new KeyBinding[]{MENU, RAILGUN, BLACKHOLE, CARRIER, SUPERNOVA, GRAVITY, SKIP}) ClientRegistry.registerKeyBinding(key); }

    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (Item item : new Item[]{StellarRegistry.REMOTE, Item.getItemFromBlock(StellarRegistry.NEEDLE), Item.getItemFromBlock(StellarRegistry.CORE)})
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @SubscribeEvent public static void input(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.currentScreen != null) return;
        if (CarrierGunneryClient.active()) return;
        if (SKIP.isPressed()) {
            RemoteFirstPerson.press(RemoteKey.SKIP);
            LocalClientBridge.filmsEnabled = !LocalClientBridge.filmsEnabled;
            StellarServices.client().stopPresentation();
            mc.player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Beyond The Space films: "
                    + (LocalClientBridge.filmsEnabled ? "ON" : "OFF")), true);
        }
        if (LaunchController.remote(mc.player).isEmpty() || !ItemStellarRemote.authorized(mc.player)) return;
        if (RAILGUN.isPressed()) { RemoteFirstPerson.press(RemoteKey.RAILGUN); StellarNetwork.requestSelection(AttackId.RAILGUN); }
        if (BLACKHOLE.isPressed()) { RemoteFirstPerson.press(RemoteKey.BLACKHOLE); StellarNetwork.requestSelection(AttackId.BLACKHOLE); }
        if (CARRIER.isPressed()) {
            RemoteFirstPerson.press(RemoteKey.CARRIER);
            StellarNetwork.requestSelection(AttackFamily.CARRIER.next(ItemStellarRemote.selected(LaunchController.remote(mc.player))));
        }
        if (SUPERNOVA.isPressed()) { RemoteFirstPerson.press(RemoteKey.SUPERNOVA); StellarNetwork.requestSelection(AttackId.SUPERNOVA); }
        if (GRAVITY.isPressed()) { RemoteFirstPerson.press(RemoteKey.GRAVITY); StellarNetwork.requestSelection(AttackId.GRAVITY_INVERSION); }
        if (MENU.isPressed()) { RemoteFirstPerson.press(RemoteKey.MENU); mc.displayGuiScreen(new SkillMenu()); }
    }

    @SubscribeEvent public static void hud(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.player == null || mc.currentScreen != null) return;
        // In first person the remote's own screen and key legends carry this readout.
        if (mc.gameSettings.thirdPersonView == 0) return;
        ItemStack stack = LaunchController.remote(mc.player);
        if (stack.isEmpty()) return;
        AttackId attack = ItemStellarRemote.selected(stack);
        String name = attack.getDisplayId() + "  " + attack.getDisplayName();
        int x = event.getResolution().getScaledWidth() - mc.fontRenderer.getStringWidth(name) - 14;
        int y = event.getResolution().getScaledHeight() - 65;
        mc.fontRenderer.drawStringWithShadow(name, x, y, WorldEffects.color(attack));
        String hint = "H: menu / Right click: launch";
        mc.fontRenderer.drawStringWithShadow(hint,
                Math.max(8, event.getResolution().getScaledWidth() - mc.fontRenderer.getStringWidth(hint) - 14), y + 12, 0xD6DDEA);
    }
}
