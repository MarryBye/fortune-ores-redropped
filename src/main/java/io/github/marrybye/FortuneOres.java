package io.github.marrybye;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = FortuneOres.MODID, name = FortuneOres.NAME, version = FortuneOres.VERSION)
public class FortuneOres {

    public enum OreRarity {

        // xpMin, xpMax, xpSmelt, then world-gen defaults: minY, maxY, veinSize, veinsPerChunk, harvestLevel.
        COMMON(1, 4, 0.4f, 0, 80, 9, 12, 1),
        UNCOMMON(2, 5, 0.6f, 0, 64, 8, 8, 1),
        RARE(3, 7, 0.9f, 4, 32, 6, 4, 2),
        EPIC(4, 8, 1.2f, 4, 24, 5, 2, 2);

        public final int xpMin;
        public final int xpMax;
        public final float xpSmelt;
        public final int minY;
        public final int maxY;
        public final int veinSize;
        public final int veinsPerChunk;
        public final int harvestLevel;

        OreRarity(int xpMin, int xpMax, float xpSmelt, int minY, int maxY, int veinSize, int veinsPerChunk,
            int harvestLevel) {
            this.xpMin = xpMin;
            this.xpMax = xpMax;
            this.xpSmelt = xpSmelt;
            this.minY = minY;
            this.maxY = maxY;
            this.veinSize = veinSize;
            this.veinsPerChunk = veinsPerChunk;
            this.harvestLevel = harvestLevel;
        }
    }

    // Mod Info
    /** Also the resource domain: every asset lives under {@code assets/fortuneores/}. */
    public static final String MODID = "fortuneores";
    public static final String NAME = "Fortune Ores Redropped";
    public static final String VERSION = "1.0.6";
    // Mod Info End

    // Singleton
    @Instance(FortuneOres.MODID)
    public static FortuneOres instance;
    public static Config config;
    public static ArrayList<Ore> oreStorage;

    public static CreativeTabs creativeTab;
    public static Item itemChunk;

    // Every ore now generates its own block. The concrete blocks live on the OreHost enum values (host.groupBlocks),
    // one block per group of 16 ores; oreGroupCount is how many groups (and therefore blocks per host) there are.
    public static int oreGroupCount;
    public static String deepslateBlockId;
    public static Block deepslateBlock;
    /** Overworld height at/below which ores take their deepslate variant; above it they use the stone variant. */
    public static int deepslateMaxY;

    public static int nextMeta;

    public static boolean allowProcessing;

    /**
     * When on, an ore with EnableOreGen is also denied at block-placement level during chunk generation, which catches
     * the generators that never fire the ore-gen event (vanilla emerald, mods running their own world generator). See
     * {@link OreGenSuppressor#shouldSuppress}.
     */
    public static boolean strictOreGenSuppression;

    public FortuneOres() {
        nextMeta = 0;
        oreStorage = new ArrayList<>();

        setupOres();
    }

    /** Looks up a registered ore by (case-insensitive) name; used by the ore blocks and the world generator. */
    public static Ore getOre(String name) {
        for (Ore ore : oreStorage) {
            if (ore.name.equalsIgnoreCase(name)) return ore;
        }
        return null;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Config(new Configuration(event.getSuggestedConfigurationFile()));
        creativeTab = new CTabChunks(CreativeTabs.getNextID(), "OreChunks");
        itemChunk = new ItemChunk();
        GameRegistry.registerItem(itemChunk, "oreChunk");

        oreGroupCount = (oreStorage.size() + BlockFortuneOre.GROUP_SIZE - 1) / BlockFortuneOre.GROUP_SIZE;
        for (OreHost host : OreHost.values()) {
            host.groupBlocks = new Block[oreGroupCount];
            for (int g = 0; g < oreGroupCount; g++) {
                Block block = new BlockFortuneOre(host, g);
                host.groupBlocks[g] = block;
                GameRegistry.registerBlock(block, ItemBlockFortuneOre.class, host.registryName + "_g" + g);
            }
        }

        MinecraftForge.EVENT_BUS.register(new OreDictHandler());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        addOreDicting();
        addBlockOreDicting();
        // Every mod has registered its blocks by now, so the "modid:name[:meta]" ore blocks can be looked up.
        resolveForeignBlocks();
        // Builds the drop-swap tables that both the harvest event and the Block#getDrops mixin read.
        OreSwapper.build();
        MinecraftForge.EVENT_BUS.register(new OreSwapper());

        deepslateBlock = resolveBlock(deepslateBlockId);
        if (deepslateBlock != null) {
            FMLLog.info(
                "[FortuneOres] Deepslate host block resolved to '%s'; deepslate ore variants will generate.",
                deepslateBlockId);
        } else {
            FMLLog.info(
                "[FortuneOres] Deepslate host block '%s' not found - only stone-hosted ores will generate. "
                    + "Install the providing mod (e.g. Et Futurum Requiem) or set 'DeepslateBlockId' in the config.",
                deepslateBlockId);
        }
        // Run late (high weight) so any mod that turns deep stone into deepslate (e.g. Et Futurum Requiem) has already
        // done so by the time our host-aware vein generator reads the terrain.
        GameRegistry.registerWorldGenerator(new WorldGenOres(), 2000);
        MinecraftForge.ORE_GEN_BUS.register(new OreGenSuppressor());
    }

    /**
     * Turns every ore's declared {@code "modid:name[:meta]"} ore blocks into block references. Ids belonging to a mod
     * that is not installed are simply dropped, which is what makes these declarations soft dependencies.
     */
    private void resolveForeignBlocks() {
        for (Ore ore : oreStorage) {
            if (!ore.enabled) continue;

            for (String blockId : ore.foreignBlockIds) {
                ForeignOreBlock resolved = ForeignOreBlock.resolve(blockId);
                if (resolved == null) continue;

                ore.foreignBlocks.add(resolved);
                FMLLog.info(
                    "[FortuneOres] Foreign ore block '%s' found; it is now handled as %s ore.",
                    blockId,
                    ore.name);
            }
        }
    }

    /**
     * Resolves a "modid:name" registry id to a block (soft dependency); returns null if absent. Accepts a
     * comma-separated list of candidate ids and returns the first one that exists, so a single config value can cover
     * differently-named deepslate blocks across modpacks.
     */
    private Block resolveBlock(String id) {
        if (id == null || id.isEmpty()) return null;
        for (String candidate : id.split(",")) {
            candidate = candidate.trim();
            int sep = candidate.indexOf(':');
            if (sep < 0) continue;
            String domain = candidate.substring(0, sep);
            String name = candidate.substring(sep + 1);
            Block block = GameRegistry.findBlock(domain, name);
            if (block != null) return block;
        }
        return null;
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        addSmelting();
    }

    public void addUniversalOre(String oreName, OreRarity rarity, String... customSmeltTargets) {
        Ore newOre = new Ore(oreName, nextMeta, rarity.xpMin, rarity.xpMax, rarity.xpSmelt);
        // Seed the ore's world-gen defaults from its rarity; the config can override every one of these per ore.
        newOre.minY = rarity.minY;
        newOre.maxY = rarity.maxY;
        newOre.veinSize = rarity.veinSize;
        newOre.veinsPerChunk = rarity.veinsPerChunk;
        newOre.harvestLevel = rarity.harvestLevel;
        newOre.addOreName(oreName);
        // The ingot/gem/dust names are derived from this at smelting time, in that priority order.
        newOre.addSmeltBase(oreName);

        // Explicit smelting targets, checked before the derived ones (Cinnabar -> quicksilver, ...).
        for (String customTarget : customSmeltTargets) {
            newOre.addSmeltName(customTarget);
        }

        oreStorage.add(newOre);
        nextMeta++;
    }

    public void addAlias(String... aliases) {
        Ore ore = oreStorage.get(nextMeta - 1);
        for (String alias : aliases) {
            ore.addOreName(alias);
            ore.addSmeltBase(alias);
        }
    }

    /**
     * Points the last-added ore at another mod's ore block, given as {@code "modid:name"} or {@code "modid:name:meta"}.
     * Needed for the mods whose ore is not in the ore dictionary at all, and for the ones packing several unrelated
     * ores into one block where only some metadata values may be touched. Ids whose mod is not installed are ignored.
     */
    public void addForeignBlock(String... blockIds) {
        Ore ore = oreStorage.get(nextMeta - 1);
        for (String blockId : blockIds) {
            ore.addForeignBlockId(blockId);
        }
    }

    /**
     * Overrides the texture file used for the last-added ore. Needed when the texture file name differs from the
     * lower-cased ore-dictionary name (e.g. multi-word "certus_quartz" or the intentionally misspelled "mangnanese").
     */
    public void setTexture(String texture) {
        oreStorage.get(nextMeta - 1).texture = texture;
    }

    /**
     * Also makes the given vanilla ore block(s) drop the last-added ore's chunk, on top of its ore-dictionary matching.
     * Used for vanilla ores (e.g. emerald) whose block drops a finished item and therefore cannot be matched through
     * the
     * ore dictionary in {@link OreSwapper}.
     */
    public void addVanillaBlock(Block... blocks) {
        oreStorage.get(nextMeta - 1).vanillaBlocks = blocks;
    }

    /**
     * Registers a vanilla ore whose block drops a finished item (coal, diamond, redstone, lapis) rather than an
     * ore-dicted ItemBlock. Such ores are matched by block instance in {@link OreSwapper} and smelt straight into
     * {@code smeltResult}.
     *
     * @param smeltResult item produced per chunk when smelted (its stack size is taken from {@code SmeltCount}).
     * @param xpSmelt     experience granted per smelted chunk.
     * @param dropMin     minimum chunks dropped when mined (before Fortune).
     * @param dropMax     maximum chunks dropped when mined; a value {@code > dropMin} makes the drop random.
     * @param smeltCount  items produced per chunk in the furnace.
     * @param blocks      the vanilla ore block variants that should drop this chunk (e.g. lit + unlit redstone).
     */
    public void addVanillaOre(String oreName, ItemStack smeltResult, float xpSmelt, int dropMin, int dropMax,
        int smeltCount, int xpDropMin, int xpDropMax, int minY, int maxY, int veinSize, int veinsPerChunk,
        int harvestLevel, Block... blocks) {
        Ore ore = new Ore(oreName, nextMeta, xpDropMin, xpDropMax, xpSmelt);
        ore.isVanilla = true;
        ore.vanillaBlocks = blocks;
        ore.vanillaSmeltResult = smeltResult;
        ore.dropCount = dropMin;
        ore.dropCountMax = dropMax;
        ore.smeltCount = smeltCount;
        ore.minY = minY;
        ore.maxY = maxY;
        ore.veinSize = veinSize;
        ore.veinsPerChunk = veinsPerChunk;
        ore.harvestLevel = harvestLevel;

        oreStorage.add(ore);
        nextMeta++;
    }

    private void setupOres() {
        // ---- Common metals ----------------------------------------------------------------------------------------
        addUniversalOre("Copper", OreRarity.COMMON);
        addUniversalOre("Iron", OreRarity.COMMON);
        addUniversalOre("Tin", OreRarity.COMMON);
        addUniversalOre("Lead", OreRarity.COMMON);
        addUniversalOre("Osmium", OreRarity.COMMON);
        addUniversalOre("Aluminum", OreRarity.COMMON);
        addAlias("Aluminium", "NaturalAluminum");
        addUniversalOre("Bauxite", OreRarity.COMMON);
        addUniversalOre("Silicon", OreRarity.COMMON);
        addUniversalOre("Cheese", OreRarity.COMMON);
        addUniversalOre("Fossil", OreRarity.COMMON);

        // ---- Uncommon metals & industrial minerals ----------------------------------------------------------------
        addUniversalOre("Silver", OreRarity.UNCOMMON);
        addUniversalOre("Nickel", OreRarity.UNCOMMON);
        addUniversalOre("Zinc", OreRarity.UNCOMMON);
        addUniversalOre("Boron", OreRarity.UNCOMMON);
        addUniversalOre("Lithium", OreRarity.UNCOMMON);
        addUniversalOre("Magnesium", OreRarity.UNCOMMON);
        addUniversalOre("Manganese", OreRarity.UNCOMMON);
        setTexture("mangnanese"); // texture ships under the misspelled "mangnanese" file name
        addAlias("Mangnanese");
        addUniversalOre("Cinnabar", OreRarity.UNCOMMON, "quicksilver");
        // Thaumcraft packs its ores into one "blockCustomOre" (0 = cinnabar, 1-6 = infused stone, 7 = amber-bearing
        // stone), so both are pinned down by metadata; the infused ores in between are none of our business.
        addForeignBlock("Thaumcraft:blockCustomOre:0");
        addUniversalOre("Pyrite", OreRarity.UNCOMMON);
        addUniversalOre("Apatite", OreRarity.UNCOMMON);
        addUniversalOre("Saltpeter", OreRarity.UNCOMMON);
        addAlias("Saltpetre");
        addUniversalOre("Potash", OreRarity.UNCOMMON);
        // Thaumcraft's amber-bearing stone drops finished amber rather than an ore-dicted ore block; the chunk smelts
        // back into that amber through the ore dictionary's "gemAmber".
        addUniversalOre("Amber", OreRarity.UNCOMMON);
        addForeignBlock("Thaumcraft:blockCustomOre:7");
        addUniversalOre("Biotite", OreRarity.UNCOMMON);
        addUniversalOre("Linium", OreRarity.UNCOMMON);
        addUniversalOre("Crystal", OreRarity.UNCOMMON);
        addUniversalOre("Dark", OreRarity.UNCOMMON);

        // ---- Rare metals ------------------------------------------------------------------------------------------
        addUniversalOre("Gold", OreRarity.RARE);
        addUniversalOre("Platinum", OreRarity.RARE);
        addUniversalOre("Titanium", OreRarity.RARE);
        addUniversalOre("Tungsten", OreRarity.RARE);
        addAlias("Wolfram");
        addUniversalOre("Cobalt", OreRarity.RARE);
        addUniversalOre("Ardite", OreRarity.RARE);
        addUniversalOre("Thorium", OreRarity.RARE);
        addUniversalOre("Uranium", OreRarity.RARE);
        addUniversalOre("Rutile", OreRarity.RARE);
        addUniversalOre("Dilithium", OreRarity.RARE);
        addUniversalOre("Lutetium", OreRarity.RARE);
        addUniversalOre("Adamantium", OreRarity.RARE);
        addUniversalOre("CrimsonIron", OreRarity.RARE);
        setTexture("crimson_iron");
        addUniversalOre("DeepIron", OreRarity.RARE);
        setTexture("deep_iron");
        addUniversalOre("ShadowIron", OreRarity.RARE);
        setTexture("shadow_iron");
        addUniversalOre("AstralSilver", OreRarity.RARE);
        setTexture("astral_silver");

        // ---- Metallurgy metals ------------------------------------------------------------------------------------
        addUniversalOre("Carmot", OreRarity.RARE);
        addUniversalOre("Ignatius", OreRarity.RARE);
        addUniversalOre("Midasium", OreRarity.RARE);
        addUniversalOre("Kalendrite", OreRarity.RARE);
        addUniversalOre("Alduorite", OreRarity.RARE);
        addUniversalOre("Ceruclase", OreRarity.RARE);
        addUniversalOre("Oureclase", OreRarity.RARE);
        addUniversalOre("Infuscolium", OreRarity.RARE);
        addUniversalOre("Eximite", OreRarity.RARE);
        addUniversalOre("Meutoite", OreRarity.RARE);
        addUniversalOre("Rubracium", OreRarity.RARE);
        addUniversalOre("Promethium", OreRarity.RARE);
        addAlias("Prometheum");
        addUniversalOre("Pyridium", OreRarity.RARE);
        addUniversalOre("Vulcanite", OreRarity.RARE);
        addUniversalOre("Neridium", OreRarity.RARE);

        // ---- Dimensional / mod-specific metals --------------------------------------------------------------------
        addUniversalOre("Abyssalnite", OreRarity.RARE);
        addUniversalOre("Ambrosium", OreRarity.UNCOMMON);
        addUniversalOre("Atlarus", OreRarity.EPIC);
        addUniversalOre("Cincinnasite", OreRarity.RARE);
        addUniversalOre("Foulite", OreRarity.RARE);
        addUniversalOre("Lemurite", OreRarity.RARE);
        addUniversalOre("Zanite", OreRarity.RARE);
        addUniversalOre("Gravitite", OreRarity.EPIC);
        addAlias("EnchantedGravitite");
        addUniversalOre("Desh", OreRarity.EPIC);
        addUniversalOre("Resonating", OreRarity.RARE);
        addUniversalOre("Rune", OreRarity.RARE);
        addUniversalOre("Solar", OreRarity.RARE);
        addUniversalOre("Virox", OreRarity.RARE);
        setTexture("viroxores");

        // ---- Epic / high-tier metals ------------------------------------------------------------------------------
        addUniversalOre("Iridium", OreRarity.EPIC);
        addUniversalOre("Mithril", OreRarity.EPIC);
        addAlias("Mythril");
        addUniversalOre("Orichalcum", OreRarity.EPIC);
        addAlias("Orichalcium");
        addUniversalOre("Adamantine", OreRarity.EPIC);
        addAlias("Adamantite");
        addUniversalOre("Sanguinite", OreRarity.EPIC);
        addUniversalOre("Starsteel", OreRarity.EPIC);
        addAlias("StarSteel");
        addUniversalOre("AstralStarmetal", OreRarity.EPIC);
        setTexture("astral_starmetal");
        addAlias("Starmetal");
        addUniversalOre("Draconium", OreRarity.EPIC);
        addAlias("DraconiumEnd");
        addUniversalOre("Yellorium", OreRarity.EPIC);
        addAlias("Yellorite");

        // ---- AbyssalCraft coralium tiers --------------------------------------------------------------------------
        addUniversalOre("Coralium", OreRarity.RARE);
        addUniversalOre("CoraliumPearl", OreRarity.EPIC);
        setTexture("coralium_pearl");
        addUniversalOre("PearlescentCoralium", OreRarity.EPIC);
        setTexture("pearlescent_coralium");
        addUniversalOre("LiquifiedCoralium", OreRarity.EPIC);
        setTexture("liquified_coralium");

        // ---- Gems -------------------------------------------------------------------------------------------------
        addUniversalOre("Emerald", OreRarity.RARE);
        // Vanilla emerald ore drops a finished emerald item, so it is matched by block instance (like coal/diamond) in
        // addition to the ore-dictionary matching above; mining it yields the emerald chunk.
        addVanillaBlock(Blocks.emerald_ore);
        addUniversalOre("Ruby", OreRarity.RARE);
        addUniversalOre("Sapphire", OreRarity.RARE);
        addUniversalOre("Peridot", OreRarity.RARE);
        addAlias("Olivine");
        addUniversalOre("Amethyst", OreRarity.RARE);
        addUniversalOre("Aquamarine", OreRarity.RARE);
        addUniversalOre("Onyx", OreRarity.RARE);
        addUniversalOre("Anglesite", OreRarity.RARE);
        addUniversalOre("Benitoite", OreRarity.RARE);
        addUniversalOre("CertusQuartz", OreRarity.RARE, "crystalCertusQuartz");
        setTexture("certus_quartz");
        addUniversalOre("ChargedCertusQuartz", OreRarity.RARE, "crystalChargedCertusQuartz");
        setTexture("charged_certus_quartz");
        addUniversalOre("ArcaneCrystal", OreRarity.RARE);
        setTexture("arcane_crystal");
        addUniversalOre("RockCrystal", OreRarity.RARE);
        setTexture("rock_crystal");

        // ---- Vanilla ores (drop a finished item, matched by block instead of ore dictionary) ----------------------
        // A chunk always smelts 1:1; the "how many" lives on the drop side as a random range, so redstone/lapis get a
        // genuine 4-8 spread (impossible with a fixed furnace recipe) that also scales with Fortune. Diamond and coal
        // drop a single chunk. All four ranges plus a deterministic SmeltCount multiplier are overridable in the
        // config.
        // Trailing args after smeltCount: xpDropMin, xpDropMax, minY, maxY, veinSize, veinsPerChunk, harvestLevel,
        // then the vanilla block(s). Mining XP mirrors vanilla; the vein tuning is the mod's historical world-gen.
        addVanillaOre("Coal", new ItemStack(Items.coal), 0.1f, 1, 1, 1, 0, 2, 0, 128, 17, 20, 0, Blocks.coal_ore);
        addVanillaOre("Diamond", new ItemStack(Items.diamond), 0.5f, 1, 1, 1, 3, 7, 1, 16, 8, 1, 2, Blocks.diamond_ore);
        addVanillaOre(
            "Redstone",
            new ItemStack(Items.redstone),
            0.1f,
            4,
            8,
            1,
            1,
            5,
            1,
            16,
            8,
            8,
            2,
            Blocks.redstone_ore,
            Blocks.lit_redstone_ore);
        addVanillaOre("Lapis", new ItemStack(Items.dye, 1, 4), 0.1f, 4, 8, 1, 2, 5, 1, 31, 7, 1, 1, Blocks.lapis_ore);

        // ---- Later additions (always append; inserting mid-list would shift existing chunk metadata) ---------------
        addUniversalOre("Topaz", OreRarity.RARE); // Ruby/Sapphire/Emerald already exist above

        // Biomes O' Plenty gems (ore/gem dictionary: oreMalachite/gemMalachite, oreTanzanite/gemTanzanite).
        addUniversalOre("Malachite", OreRarity.RARE);
        addUniversalOre("Tanzanite", OreRarity.RARE);
        // Tainted Magic Shadow Ore. Expected ore-dict "oreShadow"; smelt result is matched as gem/dust/ingot Shadow.
        // If that mod uses a different ore-dictionary name, nothing breaks - the chunk simply won't match until the
        // name is added here (see addOreName/addSmeltName below).
        // "gemShadow" needs no listing here - it is one of the names derived from the ore's own name.
        addUniversalOre("Shadow", OreRarity.EPIC, "ingotShadowmetal");
        // Draconium (Draconic Evolution) already exists above as an ore-dicted chunk; only its world-gen block is new.

        // Nether Quartz - the mod's own ore/chunk (overworld, deepslate, nether and end variants, like every ore now).
        // Its chunk drops 1-2 per block before Fortune, grants mining XP and smelts into a single vanilla quartz. It is
        // also matched to the vanilla nether-quartz ore block, so if foreign ore generation is re-enabled, mining that
        // block yields our chunk too. Args after smeltCount: xpDropMin, xpDropMax, minY, maxY, veinSize, veinsPerChunk,
        // harvestLevel, then the vanilla block(s).
        addVanillaOre("Quartz", new ItemStack(Items.quartz), 0.2f, 1, 2, 1, 2, 5, 10, 118, 14, 8, 0, Blocks.quartz_ore);
    }

    private void addOreDicting() {
        for (Ore ore : oreStorage) {
            boolean doOreDict = ore.enabled;

            boolean matched = false;
            for (String oreDict : ore.oreNames) {
                if (!(OreDictionary.getOres(oreDict)
                    .isEmpty())) matched = true;
            }

            if (!matched) doOreDict = false;

            if (doOreDict) {
                ItemStack chunk = new ItemStack(itemChunk, 1, ore.meta);
                for (String oreName : ore.oreNames) {
                    if (!oreName.contains("Nether")) OreDictionary.registerOre(oreName, chunk);
                }
            }
        }
    }

    /**
     * Registers every generated ore <em>block</em> (all hosts: stone / deepslate / netherrack / end-stone, all group
     * metadata) into the ore dictionary under each of the ore's "oreX" names. {@link #addOreDicting()} only ore-dicts
     * the chunk <em>item</em>, which world-block scanners like Mekanism's Digital Miner cannot detect - they look up
     * the
     * block placed in the world. Every alias already collected in {@link Ore#oreNames} (e.g. oreMithril + oreMythril,
     * oreAluminum + oreAluminium) is used, so the block matches whichever spelling a scanning mod expects. The legacy
     * "oreNether*" variants are skipped, matching {@link #addOreDicting()}.
     */
    private void addBlockOreDicting() {
        for (Ore ore : oreStorage) {
            if (!ore.enabled) continue;

            int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
            for (String oreName : ore.oreNames) {
                if (oreName.contains("Nether")) continue;
                for (OreHost host : OreHost.values()) {
                    Block block = host.blockFor(ore);
                    if (block != null) OreDictionary.registerOre(oreName, new ItemStack(block, 1, offset));
                }
            }
        }
    }

    /**
     * Smelting output priority: a chunk becomes an ingot when the modpack has one, otherwise a gem, and only a dust
     * when it has neither. So iron yields an ingot, emerald yields a gem, and an ore that is neither smeltable nor
     * cuttable still yields its dust.
     */
    private static final String[] SMELT_PREFIXES = { "ingot", "gem", "dust" };

    /**
     * Resolves what a chunk/ore of the given ore should smelt into: a fixed vanilla item, otherwise the first match
     * among the ore's explicit smelting targets, and finally its name (and aliases) run through
     * {@link #SMELT_PREFIXES}. Always returns a copy so callers can freely set the stack size.
     */
    private ItemStack resolveSmeltResult(Ore ore) {
        if (ore.isVanilla) {
            return ore.vanillaSmeltResult != null ? ore.vanillaSmeltResult.copy() : null;
        }

        // Explicit targets (Cinnabar -> quicksilver, CertusQuartz -> crystalCertusQuartz) win over anything derived
        // from the ore's own name.
        for (String smeltName : ore.smeltNames) {
            ItemStack match = firstOreDictItem(smeltName);
            if (match != null) return match;
        }

        for (String prefix : SMELT_PREFIXES) {
            for (String base : ore.smeltBases) {
                ItemStack match = firstOreDictItem(prefix + base);
                if (match != null) return match;
            }
        }
        return null;
    }

    /**
     * The first usable item registered under an ore-dictionary name, as a copy, or null when there is none. This mod's
     * own chunks are skipped: with AllowProcessing off they are registered as {@code dust*} themselves, and a chunk
     * that smelts into a chunk is not a recipe.
     */
    private static ItemStack firstOreDictItem(String oreName) {
        for (ItemStack stack : OreDictionary.getOres(oreName)) {
            if (stack == null || stack.getItem() == null) continue;
            if (stack.getItem() == itemChunk) continue;

            ItemStack result = stack.copy();
            // A wildcard registration means "any variant"; a furnace output has to name one, so take the first.
            if (result.getItemDamage() == OreDictionary.WILDCARD_VALUE) result.setItemDamage(0);
            return result;
        }
        return null;
    }

    private void addSmelting() {
        for (Ore ore : oreStorage) {
            if (!ore.enabled) continue;

            ItemStack smeltResult = resolveSmeltResult(ore);
            if (smeltResult != null) {
                smeltResult.stackSize = Math.max(1, ore.smeltCount);
                ItemStack chunk = new ItemStack(itemChunk, 1, ore.meta);
                GameRegistry.addSmelting(chunk, smeltResult, ore.xpSmelt);
            }
        }

        // Silk-touched ore blocks smelt straight into a single result item, like vanilla ore blocks do.
        for (Ore ore : oreStorage) {
            if (!ore.enabled) continue;

            ItemStack result = resolveSmeltResult(ore);
            if (result == null) continue;
            result.stackSize = 1;

            int offset = ore.meta % BlockFortuneOre.GROUP_SIZE;
            for (OreHost host : OreHost.values()) {
                GameRegistry.addSmelting(new ItemStack(host.blockFor(ore), 1, offset), result.copy(), ore.xpSmelt);
            }
        }
    }
}
