package io.github.marrybye;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary.OreRegisterEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class OreDictHandler {

    @SubscribeEvent
    public void Handle(OreRegisterEvent event) {
        if (!event.Name.startsWith("ore")) return;

        // Only the prefix is stripped: "ore" also occurs inside material names, and replacing every occurrence would
        // mangle them.
        String oreName = event.Name.substring("ore".length());

        for (Ore ore : FortuneOres.oreStorage) {
            if (ore.name.equals(oreName)) {
                registerOreDict(ore);
                return;
            }
        }
    }

    public void registerOreDict(Ore ore) {
        if (ore.oreDicted) return;

        ore.oreDicted = true;

        if (!ore.enabled) return;

        ItemStack chunk = new ItemStack(FortuneOres.itemChunk, 1, ore.meta);
        for (String oreName : ore.oreNames) {
            if (FortuneOres.allowProcessing) {
                if (!oreName.contains("Nether") && !oreName.contains("dense"))
                    FortuneOres.registerOreOnce(oreName, chunk);
            } else {
                String dustName = oreName.replace("ore", "")
                    .replace("dense", "")
                    .replace("Nether", "");
                FortuneOres.registerOreOnce("dust" + dustName, chunk);
            }
        }
    }
}
