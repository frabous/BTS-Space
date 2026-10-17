package fr.frabous.stellar.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;

/** Inventory, third-person, ground and frame views; the JSON model supplies the display transforms. */
final class RemoteItemRenderer extends TileEntityItemStackRenderer {
    @Override public void renderByItem(ItemStack stack) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            RemoteModel.draw(RemoteFirstPerson.look(stack, false));
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
