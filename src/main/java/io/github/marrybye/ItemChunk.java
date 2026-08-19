package io.github.marrybye;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * The ore chunks - the raw form every handled ore drops - as one item with a metadata value per ore. Only the enabled
 * ores take part: a disabled one keeps its metadata slot (the slots are baked into existing worlds) but gets no name,
 * no icon on the item atlas and no entry in the creative tab.
 */
public class ItemChunk extends Item {

    private ArrayList<IIcon> iconStorage;
    private ArrayList<String> nameStorage;
    private IIcon mysterious;

    public ItemChunk() {
        super();
        setMaxStackSize(64);
        setCreativeTab(FortuneOres.creativeTab);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);

        createNames();
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        // Null until registerIcons has run, which never happens on a dedicated server.
        if (iconStorage == null) return mysterious;
        if (meta < 0 || meta >= iconStorage.size() || iconStorage.get(meta) == null) {
            return mysterious;
        }
        return iconStorage.get(meta);
    }

    /** One entry per metadata value, so the list index stays the ore's meta; a disabled ore gets the fallback name. */
    public void createNames() {
        nameStorage = new ArrayList<String>();
        for (int i = 0; i < FortuneOres.nextMeta; i++) {
            Ore ore = FortuneOres.oreStorage.get(i);
            nameStorage
                .add(i, ore.isActive() ? "item.orechunks." + ore.name.toLowerCase() : "item.orechunks.mysterious");
        }
    }

    @Override
    public void registerIcons(IIconRegister iconRegister) {
        mysterious = iconRegister.registerIcon(FortuneOres.MODID + ":fallback");
        iconStorage = new ArrayList<IIcon>();
        // Only the enabled ores get a sprite. The mod ships well over a hundred chunk textures, and an ore that is
        // switched off has no way of ever reaching an inventory - registering its icon would only cost atlas space.
        for (int i = 0; i < FortuneOres.nextMeta; i++) {
            Ore ore = FortuneOres.oreStorage.get(i);
            iconStorage
                .add(i, ore.isActive() ? iconRegister.registerIcon(FortuneOres.MODID + ":" + ore.texture) : null);
        }
    }

    @Override
    public String getUnlocalizedName(ItemStack itemStack) {
        if (nameStorage == null) return "item.orechunks.mysterious";

        int meta = itemStack.getItemDamage();

        if (meta < 0 || meta >= nameStorage.size() || nameStorage.get(meta) == null) return "item.orechunks.mysterious";

        return nameStorage.get(meta);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void getSubItems(Item item, CreativeTabs tabs, List list) {
        for (int i = 0; i < nameStorage.size(); ++i) {
            // Disabled ores keep their metadata slot but are not part of the game any more, so they stay out of the
            // creative tab (and out of NEI, which reads the same list).
            if (!FortuneOres.oreStorage.get(i)
                .isActive()) continue;

            list.add(new ItemStack(this, 1, i));
        }
    }
}
