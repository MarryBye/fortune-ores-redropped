package io.github.marrybye;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** ItemBlock giving each metadata of {@link BlockFortuneOre} its own name and inventory icon. */
public class ItemBlockFortuneOre extends ItemBlock {

    public ItemBlockFortuneOre(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        Block block = Block.getBlockFromItem(this);
        if (block instanceof BlockFortuneOre) {
            return super.getUnlocalizedName() + "." + ((BlockFortuneOre) block).nameSuffix(stack.getItemDamage());
        }
        return super.getUnlocalizedName();
    }
}
