package io.github.marrybye;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

import cpw.mods.fml.common.FMLLog;

/**
 * Which biomes an ore is allowed to generate in, parsed from its {@code Biomes} config list.
 *
 * <p>
 * A rule is one of:
 * <ul>
 * <li>{@code type:MOUNTAIN} - a Forge {@link BiomeDictionary.Type}, so it covers every modded biome tagged with
 * it;</li>
 * <li>{@code Extreme Hills} - a biome name, matched ignoring case, spaces and underscores;</li>
 * <li>{@code id:35} - a raw biome id, for the rare biome that has neither a useful tag nor a stable name.</li>
 * </ul>
 *
 * <p>
 * A rule prefixed with {@code !} excludes instead of including. Exclusions win over inclusions, and a list holding only
 * exclusions means "everywhere except these". An empty list means no restriction at all, which is also the cheap path:
 * {@link #isUnrestricted()} lets the world generator skip the biome lookup entirely.
 */
public final class BiomeFilter {

    private static final BiomeFilter UNRESTRICTED = new BiomeFilter();

    private final List<BiomeDictionary.Type> allowTypes = new ArrayList<>();
    private final List<BiomeDictionary.Type> denyTypes = new ArrayList<>();
    private final List<String> allowNames = new ArrayList<>();
    private final List<String> denyNames = new ArrayList<>();
    private final List<Integer> allowIds = new ArrayList<>();
    private final List<Integer> denyIds = new ArrayList<>();

    private BiomeFilter() {}

    /** The filter that lets every biome through; shared, since it holds no state. */
    public static BiomeFilter unrestricted() {
        return UNRESTRICTED;
    }

    /**
     * Parses an ore's biome rules. Unparsable rules are reported and skipped rather than failing the ore, so a typo in
     * the config costs one rule instead of the whole world generator. {@code oreName} only names the ore in those
     * messages.
     */
    public static BiomeFilter parse(String oreName, String[] rules) {
        if (rules == null || rules.length == 0) return UNRESTRICTED;

        BiomeFilter filter = new BiomeFilter();
        for (String raw : rules) {
            if (raw == null) continue;

            String rule = raw.trim();
            if (rule.isEmpty()) continue;

            boolean deny = rule.startsWith("!");
            if (deny) rule = rule.substring(1)
                .trim();
            if (rule.isEmpty()) continue;

            if (startsWithIgnoreCase(rule, "type:")) {
                BiomeDictionary.Type type = resolveType(oreName, rule.substring(5));
                if (type != null) (deny ? filter.denyTypes : filter.allowTypes).add(type);
            } else if (startsWithIgnoreCase(rule, "id:")) {
                Integer id = resolveId(oreName, rule.substring(3));
                if (id != null) (deny ? filter.denyIds : filter.allowIds).add(id);
            } else {
                (deny ? filter.denyNames : filter.allowNames).add(normalise(rule));
            }
        }

        return filter.isUnrestricted() ? UNRESTRICTED : filter;
    }

    /**
     * Resolves a {@link BiomeDictionary.Type} by name. Deliberately not {@code Type.getType}, which would silently
     * create the type a typo names - and a type nothing is tagged with matches no biome at all.
     */
    private static BiomeDictionary.Type resolveType(String oreName, String name) {
        String trimmed = name.trim();
        try {
            return BiomeDictionary.Type.valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            FMLLog.warning(
                "[FortuneOres] %s: unknown biome type '%s' in its Biomes list; rule ignored.",
                oreName,
                trimmed);
            return null;
        }
    }

    private static Integer resolveId(String oreName, String name) {
        String trimmed = name.trim();
        try {
            return Integer.valueOf(Integer.parseInt(trimmed));
        } catch (NumberFormatException notANumber) {
            FMLLog.warning(
                "[FortuneOres] %s: '%s' is not a biome id in its Biomes list; rule ignored.",
                oreName,
                trimmed);
            return null;
        }
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length() && value.substring(0, prefix.length())
            .equalsIgnoreCase(prefix);
    }

    /** Biome names are compared without case, spaces or underscores, so "Extreme Hills" == "extreme_hills". */
    private static String normalise(String name) {
        return name.toLowerCase()
            .replace(" ", "")
            .replace("_", "");
    }

    /** True when this filter lets every biome through, which is the world generator's signal to skip the lookup. */
    public boolean isUnrestricted() {
        return allowTypes.isEmpty() && denyTypes
            .isEmpty() && allowNames.isEmpty() && denyNames.isEmpty() && allowIds.isEmpty() && denyIds.isEmpty();
    }

    /** Whether the ore may generate in this biome. An unknown (null) biome is never restricted. */
    public boolean matches(BiomeGenBase biome) {
        if (biome == null) return true;

        if (matchesAny(biome, denyTypes, denyNames, denyIds)) return false;
        // Exclusions only: everything that survived them is allowed.
        if (allowTypes.isEmpty() && allowNames.isEmpty() && allowIds.isEmpty()) return true;

        return matchesAny(biome, allowTypes, allowNames, allowIds);
    }

    private static boolean matchesAny(BiomeGenBase biome, List<BiomeDictionary.Type> types, List<String> names,
        List<Integer> ids) {
        for (BiomeDictionary.Type type : types) {
            if (BiomeDictionary.isBiomeOfType(biome, type)) return true;
        }
        if (!names.isEmpty() && biome.biomeName != null) {
            String actual = normalise(biome.biomeName);
            for (String name : names) {
                if (actual.equals(name)) return true;
            }
        }
        for (Integer id : ids) {
            if (biome.biomeID == id.intValue()) return true;
        }
        return false;
    }
}
