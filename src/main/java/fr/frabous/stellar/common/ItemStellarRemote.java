package fr.frabous.stellar.common;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.SelectionRequest;
import fr.frabous.stellar.api.StellarIds;
import fr.frabous.stellar.api.StellarServices;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class ItemStellarRemote extends Item {
    public ItemStellarRemote() {
        setRegistryName(StellarIds.MOD_ID, StellarIds.REMOTE);
        setUnlocalizedName("stellar.stellar_remote");
        setCreativeTab(StellarRegistry.TAB);
        setMaxStackSize(1);
    }

    /** Operators only (level 2, like /stellar). Client players read the level the server syncs to them. */
    public static boolean authorized(EntityPlayer player) { return player.canUseCommand(2, "stellar"); }

    public static AttackId selected(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("StellarAttack", 3)) {
            try { return AttackId.fromWireId(stack.getTagCompound().getInteger("StellarAttack")); }
            catch (IllegalArgumentException ignored) { }
        }
        return AttackId.RAILGUN;
    }

    public static void select(ItemStack stack, AttackId attack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("StellarAttack", attack.getWireId());
    }

    /** A synced selection is a key press on the same remote, not a new item. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!authorized(player)) {
            if (!world.isRemote) player.sendStatusMessage(new TextComponentString(LaunchController.RESTRICTED), true);
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (world.isRemote) {
            if (!player.isSneaking()) StellarServices.client().preparePresentation();
        } else if (player instanceof EntityPlayerMP) {
            if (player.isSneaking()) LaunchController.INSTANCE.select((EntityPlayerMP) player,
                    new SelectionRequest(selected(stack).next()));
            else LaunchController.INSTANCE.launchAimed((EntityPlayerMP) player);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
