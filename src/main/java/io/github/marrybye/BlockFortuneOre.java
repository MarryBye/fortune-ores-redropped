package io.github.marrybye;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The mod's world-gen ore block. Every registered {@link Ore} now has its own generated block, so a single block class
 * carries them in groups of {@link #GROUP_SIZE}: block <em>group g</em> holds the ores with {@code meta} in
 * {@code [g*16, g*16+16)}, and the block's own metadata (0-15) is the offset within that group. One block is registered
 * per {@link OreHost} and per group, which is what lets the same ore show a stone / deepslate / netherrack / end-stone
 * background depending on where it generated. Mining an enabled ore drops its chunk (drop range + Fortune); silk touch
 * yields the block itself.
 */
public class BlockFortuneOre extends Block {

    /** Materials per block: 4 bits of metadata → 16 ores. Group = ore.meta / 16, block metadata = ore.meta % 16. */
    public static final int GROUP_SIZE = 16;

    private static final Random XP_RAND = new Random();

    private final OreHost host;
    private final int group;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    /** Stands in for the metadata values this block carries no icon for; see {@link #registerBlockIcons}. */
    @SideOnly(Side.CLIENT)
    private IIcon fallbackIcon;

    public BlockFortuneOre(OreHost host, int group) {
        super(Material.rock);
        this.host = host;
        this.group = group;

        setHardness(3.0F);
        setResistance(5.0F);
        setStepSound(soundTypeStone);
        setCreativeTab(FortuneOres.creativeTab);
        setBlockName("fortuneores." + host.infix);

        for (int i = 0; i < GROUP_SIZE; i++) {
            Ore ore = oreForOffset(i);
            if (ore != null) setHarvestLevel("pickaxe", ore.harvestLevel, i);
        }
    }

    /** The ore held at block-metadata {@code offset} in this block's group, or {@code null} if that slot is unused. */
    private Ore oreForOffset(int offset) {
        int index = group * GROUP_SIZE + offset;
        if (index < 0 || index >= FortuneOres.oreStorage.size()) return null;
        return FortuneOres.oreStorage.get(index);
    }

    private static int clampMeta(int meta) {
        if (meta < 0) return 0;
        if (meta >= GROUP_SIZE) return GROUP_SIZE - 1;
        return meta;
    }

    @Override
    public int damageDropped(int meta) {
        // Used by silk-touch createStackedBlock so the harvested block keeps its material.
        return clampMeta(meta);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        int m = clampMeta(meta);

        Ore ore = oreForOffset(m);
        if (ore == null || !ore.isActive()) {
            // Chunk disabled/unknown: fall back to dropping the ore block itself so nothing is lost.
            drops.add(new ItemStack(this, 1, m));
            return drops;
        }

        int count = Drops.rollChunks(ore, fortune, world.rand);
        for (int i = 0; i < count; i++) drops.add(new ItemStack(FortuneOres.itemChunk, 1, ore.meta));
        return drops;
    }

    @Override
    public int getExpDrop(IBlockAccess world, int meta, int fortune) {
        Ore ore = oreForOffset(clampMeta(meta));
        if (ore == null || ore.xpDropMax <= 0) return 0;
        return MathHelper.getRandomIntegerInRange(XP_RAND, ore.xpDropMin, ore.xpDropMax);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        // The host's own terrain texture covers the metadata values with no icon of their own - an ore switched off
        // after its blocks were already generated, or an unused slot in the last group. It is a vanilla texture the
        // atlas already holds, so the stand-in costs nothing.
        fallbackIcon = reg.registerIcon(host.fallbackTexture());
        icons = new IIcon[GROUP_SIZE];
        for (int i = 0; i < GROUP_SIZE; i++) {
            Ore ore = oreForOffset(i);
            // Only the ores this host actually generates: four host variants of a hundred-odd ores is more than the
            // block atlas should carry for materials the world generator will never place.
            if (ore != null && ore.usesHost(host)) {
                icons[i] = reg.registerIcon(FortuneOres.MODID + ":" + host.infix + "_" + ore.texture);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        // Null until registerBlockIcons has run; asked for by anything rendering a block before the atlas is stitched.
        if (icons == null) return fallbackIcon;

        IIcon icon = icons[clampMeta(meta)];
        return icon != null ? icon : fallbackIcon;
    }

    /** Unlocalized suffix (the ore name) for an item stack of this block, used by {@link ItemBlockFortuneOre}. */
    public String nameSuffix(int meta) {
        Ore ore = oreForOffset(clampMeta(meta));
        return ore != null ? ore.name.toLowerCase() : "unknown";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SideOnly(Side.CLIENT)
    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < GROUP_SIZE; i++) {
            Ore ore = oreForOffset(i);
            // Only the materials this block really carries; the rest have no icon and no place in the world.
            if (ore != null && ore.usesHost(host)) list.add(new ItemStack(item, 1, i));
        }
    }
}
