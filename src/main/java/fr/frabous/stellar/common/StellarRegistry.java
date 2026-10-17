package fr.frabous.stellar.common;

import fr.frabous.stellar.api.StellarIds;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class StellarRegistry {
    public static final CreativeTabs TAB = new CreativeTabs("stellar") {
        @Override public ItemStack getTabIconItem() { return new ItemStack(REMOTE); }
    };
    public static final ItemStellarRemote REMOTE = new ItemStellarRemote();
    // Keep old world saves readable; these legacy blocks are no longer offered or placed.
    public static final Block NEEDLE = block(StellarIds.NEEDLE, Material.IRON, -1.0F, 0.25F);
    public static final Block CORE = block(StellarIds.STAR_CORE, Material.ROCK, 5.0F, 1.0F);

    private StellarRegistry() { }

    private static Block block(String id, Material material, float hardness, float light) {
        return new Block(material).setRegistryName(StellarIds.MOD_ID, id).setUnlocalizedName("stellar." + id)
                .setHardness(hardness).setResistance(6000000.0F).setLightLevel(light);
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().registerAll(NEEDLE, CORE); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(REMOTE, new ItemBlock(NEEDLE).setRegistryName(NEEDLE.getRegistryName()),
                new ItemBlock(CORE).setRegistryName(CORE.getRegistryName()));
    }
}
