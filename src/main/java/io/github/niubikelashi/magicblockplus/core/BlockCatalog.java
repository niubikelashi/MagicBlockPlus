package io.github.niubikelashi.magicblockplus.core;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 建筑方块目录。
 *
 * 两件事：
 * 1. 内置「常见建筑方块」，按类别分组（石头与地形 / 木板与木质 / 砖瓦与台阶 / 金属与矿物 /
 *    陶瓦·玻璃·羊毛·混凝土 / 下界与末地 / 植物类建材）。
 * 2. 随版本更新自动发现新的建筑方块：在 Material.values() 里按命名规则挑，
 *    命中 include 家族且不在 exclude 名单里的才算建筑方块。
 *
 * 为什么用命名规则而不是原版 Tag/Registry：Registry 的 API 在 1.20.1 与 1.21.x 之间改动过，
 * 跨版本不稳；命名规则只依赖 Material 名字，新版本（苍白橡木、树脂块之类）会自动进来，
 * 而箱子/熔炉/小麦/水/告示牌这类「奇奇怪怪的方块」被显式排除，不会进来。
 */
public class BlockCatalog {

    public static final String AUTO_CATEGORY = "自动发现";
    public static final String EXTRA_CATEGORY = "手动追加";

    /** 建筑方块的命名家族（正则，匹配 Material 枚举名）。 */
    public static List<String> defaultInclude() {
        return List.of(
                "^.*_PLANKS$", "^.*_STAIRS$", "^.*_SLAB$", "^.*_WALL$", "^.*_FENCE$", "^.*_FENCE_GATE$",
                "^.*_TRAPDOOR$", "^.*_BRICKS$", "^.*_TILES$", "^.*_PILLAR$", "^.*_MOSAIC$",
                "^.*_WOOD$", "^.*_LOG$", "^.*_BLOCK$", "^.*_HYPHAE$", "^.*_STEM$",
                "^.*_GLASS$", "^.*_GLASS_PANE$", "^.*_WOOL$", "^.*_CARPET$", "^.*_CONCRETE$", "^.*_TERRACOTTA$",
                "^BRICKS$", "^STONE$", "^COBBLESTONE$", "^MOSSY_COBBLESTONE$",
                "^GRANITE$", "^POLISHED_GRANITE$", "^DIORITE$", "^POLISHED_DIORITE$",
                "^ANDESITE$", "^POLISHED_ANDESITE$", "^DEEPSLATE$", "^COBBLED_DEEPSLATE$", "^POLISHED_DEEPSLATE$",
                "^TUFF$", "^CALCITE$", "^SANDSTONE$", "^RED_SANDSTONE$", "^SMOOTH_SANDSTONE$", "^SMOOTH_RED_SANDSTONE$",
                "^STONE_BRICKS$", "^MOSSY_STONE_BRICKS$",
                "^DIRT$", "^COARSE_DIRT$", "^ROOTED_DIRT$", "^CLAY$", "^MUD$", "^PACKED_MUD$", "^MUD_BRICKS$",
                "^SAND$", "^GRAVEL$", "^SOUL_SAND$", "^SOUL_SOIL$", "^NETHERRACK$",
                "^OBSIDIAN$", "^COPPER_BLOCK$", "^GLOWSTONE$", "^SEA_LANTERN$", "^BONE_BLOCK$",
                "^PRISMARINE$", "^PRISMARINE_BRICKS$", "^END_STONE$", "^NETHER_BRICKS$"
        );
    }

    /** 「奇怪方块」排除名单（正则）。 */
    public static List<String> defaultExclude() {
        return List.of(
                "^AIR$", "^CAVE_AIR$", "^VOID_AIR$",
                "^.*_CHEST$", "^TRAPPED_CHEST$", "^BARREL$", "^ENDER_CHEST$", "^.*_SHULKER_BOX$",
                "^FURNACE$", "^BLAST_FURNACE$", "^SMOKER$", "^CRAFTING_TABLE$", "^ENCHANTING_TABLE$",
                "^CARTOGRAPHY_TABLE$", "^FLETCHING_TABLE$", "^GRINDSTONE$", "^STONECUTTER$", "^SMITHING_TABLE$",
                "^LOOM$", "^LECTERN$", "^ANVIL$", "^.*_ANVIL$", "^BEACON$", "^BELL$", "^CAKE$",
                "^HOPPER$", "^OBSERVER$", "^DISPENSER$", "^DROPPER$", "^JUKEBOX$", "^NOTE_BLOCK$",
                "^COMPOSTER$", "^RESPAWN_ANCHOR$", "^.*_SPAWNER$", "^SPAWNER$",
                "^REDSTONE_TORCH$", "^REDSTONE_WALL_TORCH$", "^REDSTONE_WIRE$", "^REPEATER$", "^COMPARATOR$",
                "^TARGET$", "^LIGHTNING_ROD$", "^LEVER$", "^.*_BUTTON$", "^.*_PRESSURE_PLATE$", "^.*_RAIL$",
                "^TRIPWIRE$", "^TRIPWIRE_HOOK$", "^.*_DOOR$",
                "^.*_SIGN$", "^.*_HANGING_SIGN$", "^.*_BED$",
                "^.*COMMAND_BLOCK$",
                "^LEGACY_.*",
                "^.*_TORCH$", "^SOUL_TORCH$", "^FIRE$", "^SOUL_FIRE$",
                "^CAMPFIRE$", "^SOUL_CAMPFIRE$",
                "^WATER$", "^LAVA$", "^SPONGE$", "^WET_SPONGE$", "^HONEY_BLOCK$", "^SLIME_BLOCK$",
                "^SCAFFOLDING$", "^STRUCTURE_BLOCK$", "^JIGSAW_BLOCK$", "^BARRIER$", "^LIGHT$",
                "^STRUCTURE_VOID$", "^END_PORTAL$", "^END_GATE$", "^END_PORTAL_FRAME$", "^DRAGON_EGG$",
                "^.*_ORE$", "^RAW_.*", "^.*_SHROOM$", "^.*_FUNGUS$", "^.*_SPROUT$", "^SPORE_BLOSSOM$",
                "^.*_PROPAGULE$", "^.*_LEAVES$", "^.*_SAPLING$", "^POTTED_.*", "^AZALEA.*",
                "^.*_ROOTS$", "^.*_CORAL$", "^CORAL_BLOCK$", "^.*_CORAL_BLOCK$", "^.*_FAN$", "^.*_GRASS$",
                "^.*_FERN$", "^.*_SHRUB$", "^.*_BUSH$", "^.*_PETALS$", "^WHEAT$", "^CARROTS$", "^POTATOES$",
                "^BEETROOTS$", "^PUMPKIN_STEM$", "^MELON_STEM$", "^ATTACHED_.*", "^PUMPKIN$", "^CARVED_PUMPKIN$",
                "^MELON$", "^SUGAR_CANE$", "^CACTUS$", "^BAMBOO$", "^BAMBOO_SPROUT$", "^CHORUS_.*", "^FROGSPAWN$",
                "^.*_MUSHROOM.*", "^MUSHROOM_STEM$", "^.*_DRIPLEAF.*", "^DRIED_KELP_BLOCK$"
        );
    }

    /** 配置缺失时的兜底类别表；config.yml 里显式写出来，用户可以直接改。 */
    public static Map<String, List<String>> defaultCategories() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("石头与地形", List.of(
                "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "STONE_BRICKS", "MOSSY_STONE_BRICKS",
                "GRANITE", "POLISHED_GRANITE", "DIORITE", "POLISHED_DIORITE", "ANDESITE", "POLISHED_ANDESITE",
                "DEEPSLATE", "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES",
                "TUFF", "CALCITE", "SAND", "SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE", "SMOOTH_RED_SANDSTONE",
                "GRAVEL", "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK", "MUD", "PACKED_MUD", "MUD_BRICKS",
                "CLAY", "SNOW_BLOCK", "ICE", "PACKED_ICE", "BLUE_ICE", "MAGMA_BLOCK"));
        out.put("木板与木质", List.of(
                "OAK_PLANKS", "OAK_LOG", "STRIPPED_OAK_LOG", "OAK_WOOD", "OAK_STAIRS", "OAK_SLAB", "OAK_FENCE",
                "OAK_FENCE_GATE", "OAK_TRAPDOOR", "SPRUCE_PLANKS", "SPRUCE_LOG", "STRIPPED_SPRUCE_LOG",
                "SPRUCE_STAIRS", "SPRUCE_SLAB", "SPRUCE_FENCE", "BIRCH_PLANKS", "BIRCH_LOG", "BIRCH_STAIRS",
                "BIRCH_SLAB", "JUNGLE_PLANKS", "JUNGLE_LOG", "JUNGLE_STAIRS",
                "ACACIA_PLANKS", "ACACIA_LOG", "ACACIA_STAIRS", "DARK_OAK_PLANKS", "DARK_OAK_LOG",
                "DARK_OAK_STAIRS", "MANGROVE_PLANKS", "MANGROVE_LOG", "CHERRY_PLANKS", "CHERRY_LOG",
                "CHERRY_STAIRS", "BAMBOO_PLANKS", "BAMBOO_STAIRS"));
        out.put("砖瓦与装饰", List.of(
                "BRICKS", "COBBLED_DEEPSLATE", "QUARTZ_BLOCK", "QUARTZ_PILLAR", "SMOOTH_QUARTZ",
                "PURPUR_BLOCK", "PURPUR_PILLAR", "PURPUR_STAIRS", "PRISMARINE", "PRISMARINE_BRICKS",
                "PRISMARINE_STAIRS", "SEA_LANTERN", "BONE_BLOCK", "GLOWSTONE", "OBSIDIAN", "CUT_COPPER",
                "EXPOSED_COPPER", "WEATHERED_COPPER", "OXIDIZED_COPPER", "COPPER_BLOCK"));
        out.put("金属与矿物", List.of(
                "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "LAPIS_BLOCK",
                "REDSTONE_BLOCK", "NETHERITE_BLOCK", "COPPER_BLOCK"));
        out.put("陶瓦·玻璃·羊毛·混凝土", List.of(
                "GLASS", "GLASS_PANE", "WHITE_TERRACOTTA", "WHITE_WOOL", "WHITE_CARPET", "WHITE_CONCRETE",
                "MOSS_BLOCK", "MOSS_CARPET"));
        out.put("下界与末地", List.of(
                "NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "NETHER_BRICKS", "RED_NETHER_BRICKS",
                "WARPED_STEM", "CRIMSON_STEM", "WARPED_HYPHAE", "CRIMSON_HYPHAE", "WARPED_PLANKS",
                "CRIMSON_PLANKS", "END_STONE", "END_STONE_BRICKS", "END_STONE_BRICK_STAIRS"));
        out.put("植物类建材", List.of(
                "MOSS_BLOCK", "MOSS_CARPET", "BAMBOO_BLOCK", "BAMBOO_MOSAIC", "RESIN_BLOCK", "RESIN_BRICKS"));
        return out;
    }

    private final List<Material> ordered = new ArrayList<>();
    private final Map<Material, String> category = new LinkedHashMap<>();
    private final List<String> unknownNames = new ArrayList<>();
    private int curatedCount;
    private int autoAdded;
    private int extras;

    public List<Material> materials() {
        return ordered;
    }

    public int size() {
        return ordered.size();
    }

    public int curatedCount() {
        return curatedCount;
    }

    public int autoAdded() {
        return autoAdded;
    }

    public int extrasCount() {
        return extras;
    }

    /** 目录里没能翻出中文名的材料名；用户可以用 custom-block-translations 补。 */
    public List<String> unknownNames() {
        return unknownNames;
    }

    public String categoryOf(Material material) {
        String name = category.get(material);
        return name == null ? "其它" : name;
    }

    public boolean contains(Material material) {
        return category.containsKey(material);
    }

    public void rebuild(Settings settings, Consumer<String> debug) {
        ordered.clear();
        category.clear();
        unknownNames.clear();
        curatedCount = 0;
        autoAdded = 0;
        extras = 0;

        if (settings.buildingEnabled) {
            for (Map.Entry<String, List<String>> entry : settings.categories.entrySet()) {
                for (String raw : entry.getValue()) {
                    Material material = parse(raw, debug);
                    if (material != null && add(material, entry.getKey())) {
                        curatedCount++;
                    }
                }
            }
        }

        // 用户在 gui.materials / gui.group 里手动加的材料照旧生效（归类到「手动追加」）。
        for (String raw : settings.materialNames) {
            Material material = parse(raw, debug);
            if (material != null && add(material, EXTRA_CATEGORY)) {
                extras++;
            }
        }
        for (List<String> group : settings.groupMaterials.values()) {
            for (String raw : group) {
                Material material = parse(raw, debug);
                if (material != null && add(material, EXTRA_CATEGORY)) {
                    extras++;
                }
            }
        }

        if (settings.buildingEnabled && settings.autoAdd) {
            List<Pattern> include = compile(settings.includePatterns, debug);
            List<Pattern> exclude = compile(settings.excludePatterns, debug);
            for (Material material : Material.values()) {
                if (ordered.size() >= settings.autoMax) {
                    debug.accept("建筑方块目录到达上限 " + settings.autoMax + "，停止自动发现");
                    break;
                }
                if (category.containsKey(material)) {
                    continue;
                }
                if (!isBuildingCandidate(material, include, exclude)) {
                    continue;
                }
                if (add(material, AUTO_CATEGORY)) {
                    autoAdded++;
                }
            }
        }

        for (Material material : ordered) {
            if (BlockNames.translate(material.name()) == null) {
                unknownNames.add(material.name());
            }
        }
    }

    /** 该方块是否有物品形态（能进背包）；新版本里有些「只有方块形态」的材料会抛异常，这里当成不可用。 */
    private static boolean hasItemForm(Material material) {
        try {
            return new ItemStack(material).getType() != Material.AIR;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return false;
        }
    }

    /** 候选：必须是方块、不是空气、有物品形态（能进背包），且符合命名规则。 */
    private static boolean isBuildingCandidate(Material material, List<Pattern> include, List<Pattern> exclude) {
        if (!material.isBlock() || material.isAir()) {
            return false;
        }
        String name = material.name();
        for (Pattern pattern : exclude) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        for (Pattern pattern : include) {
            if (pattern.matcher(name).matches()) {
                return hasItemForm(material);
            }
        }
        return false;
    }

    private Material parse(String raw, Consumer<String> debug) {
        String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isBlock()) {
            debug.accept("跳过无效材料: " + raw);
            return null;
        }
        if (!hasItemForm(material)) {
            debug.accept("跳过没有物品形态的方块: " + raw);
            return null;
        }
        return material;
    }

    private boolean add(Material material, String categoryName) {
        if (category.containsKey(material)) {
            return false;
        }
        ordered.add(material);
        category.put(material, categoryName);
        return true;
    }

    private static List<Pattern> compile(List<String> raw, Consumer<String> debug) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pattern> out = new ArrayList<>();
        for (String text : raw) {
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            try {
                out.add(Pattern.compile(text.trim()));
            } catch (IllegalArgumentException ex) {
                debug.accept("无效的正则（已忽略）: " + text);
            }
        }
        return out;
    }
}
