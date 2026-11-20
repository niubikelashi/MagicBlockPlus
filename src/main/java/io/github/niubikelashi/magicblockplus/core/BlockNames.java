package io.github.niubikelashi.magicblockplus.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 材料名 → 中文显示名。
 *
 * 为什么不用硬编码的翻译表：原版只写死了一小批方块名，新服务端版本加入的方块
 * （1.19 的蘑菇/深板岩、1.20 的樱花/红树林、后续版本的新方块）就只显示英文。
 * 这里按 Minecraft 材料名的命名规律翻译：修饰词 + 词根 + 制品后缀，
 * 所以只要词根已知，新版本的新方块也能自动得到中文名。
 * 词根未知的方块会返回 null，由调用方退回英文并记日志，用户可以在
 * config.yml 的 custom-block-translations 里补上。
 */
public final class BlockNames {

    /** 整名翻译：不规则的方块名（无法按词根拼出来）。 */
    private static final Map<String, String> FULL = new LinkedHashMap<>();
    /** 词根翻译：木种、颜色、石材、矿物等。 */
    private static final Map<String, String> TOKEN = new LinkedHashMap<>();
    /** 制品后缀（长的先匹配）。 */
    private static final Map<String, String> SUFFIX = new LinkedHashMap<>();

    static {
        // ---------- 不规则整名 ----------
        FULL.put("COBBLESTONE", "圆石");
        FULL.put("STONE_BRICKS", "石砖");
        FULL.put("MOSSY_COBBLESTONE", "苔藓圆石");
        FULL.put("COBBLED_DEEPSLATE", "深板岩圆石");
        FULL.put("NETHERRACK", "地狱岩");
        FULL.put("GLOWSTONE", "荧石");
        FULL.put("SEA_LANTERN", "海晶灯");
        FULL.put("MYCELIUM", "菌丝体");
        FULL.put("PODZOLOS", "灰化土");
        FULL.put("COARSE_DIRT", "粗泥土");
        FULL.put("ROOTED_DIRT", "带根泥土");
        FULL.put("GRASS_BLOCK", "草方块");
        FULL.put("CLAY", "黏土块");
        FULL.put("GRAVEL", "砂砾");
        FULL.put("SAND", "沙子");
        FULL.put("RED_SAND", "红沙");
        FULL.put("PACKED_ICE", "浮冰");
        FULL.put("FROSTED_ICE", "磨砂冰");
        FULL.put("BLUE_ICE", "蓝冰");
        FULL.put("CRYING_OBSIDIAN", "哭泣的黑曜石");
        FULL.put("MOSS_BLOCK", "苔藓块");
        FULL.put("MOSS_CARPET", "苔藓地毯");
        FULL.put("NETHER_WART_BLOCK", "下界菌岩");
        FULL.put("WARPED_WART_BLOCK", "诡异菌岩");
        FULL.put("PRISMARINE", "海晶石");
        FULL.put("PRISMARINE_BRICKS", "海晶石砖");
        FULL.put("DARK_PRISMARINE", "暗海晶石");
        FULL.put("PURPUR_BLOCK", "紫珀块");
        FULL.put("PURPUR_PILLAR", "紫珀柱石");
        FULL.put("QUARTZ_PILLAR", "石英柱石");
        FULL.put("QUARTZ_BRICKS", "石英砖");
        FULL.put("MANGROVE_ROOTS", "红树林根");
        FULL.put("MUD_BRICKS", "泥砖");
        FULL.put("BAMBOO_MOSAIC", "竹片块");
        FULL.put("SNOW_BLOCK", "雪块");
        FULL.put("GLASS", "玻璃");
        FULL.put("GLASS_PANE", "玻璃板");
        FULL.put("BRICKS", "砖块");
        FULL.put("NETHERITE_BLOCK", "下界合金块");
        FULL.put("NETHERITE_INGOT_BLOCK", "下界合金块"); // 兼容别名
        FULL.put("CUT_COPPER", "切制铜块");
        FULL.put("CUT_COPPER_STAIRS", "切制铜楼梯");
        FULL.put("CUT_COPPER_SLAB", "切制铜台阶");
        FULL.put("WAXED_COPPER_BLOCK", "涂蜡铜块");
        FULL.put("EXPOSED_COPPER", "暴露的铜块");
        FULL.put("WEATHERED_COPPER", "风化的铜块");
        FULL.put("OXIDIZED_COPPER", "氧化的铜块");
        FULL.put("OXIDIZED_EXPOSED_COPPER", "氧化暴露的铜块");
        FULL.put("EXPOSED_OXIDIZED_COPPER", "氧化暴露的铜块"); // 兼容别名
        FULL.put("DEEPSLATE_TILES", "深板岩瓦");
        FULL.put("BAMBOO_MOSAIC_STAIRS", "竹片楼梯");
        FULL.put("BAMBOO_MOSAIC_SLAB", "竹片台阶");
        FULL.put("TINTED_GLASS", "暗晶玻璃");
        FULL.put("SOUL_SAND", "灵魂沙");
        FULL.put("SOUL_SOIL", "灵魂土");
        FULL.put("SHROOMLIGHT", "发光菌岩");
        FULL.put("HONEYCOMB_BLOCK", "蜂巢块");
        FULL.put("HAY_BLOCK", "干草块");
        FULL.put("DRIPSTONE_BLOCK", "钟乳石块");

        // ---------- 词根 ----------
        TOKEN.put("STONE", "石头");
        TOKEN.put("COBBLESTONE", "圆石");
        TOKEN.put("GRANITE", "花岗岩");
        TOKEN.put("DIORITE", "闪长岩");
        TOKEN.put("ANDESITE", "安山岩");
        TOKEN.put("DEEPSLATE", "深板岩");
        TOKEN.put("TUFF", "凝灰岩");
        TOKEN.put("CALCITE", "方解石");
        TOKEN.put("STONE_BRICK", "石砖");
        TOKEN.put("SANDSTONE", "砂岩");
        TOKEN.put("RED_SANDSTONE", "红砂岩");
        TOKEN.put("OBSIDIAN", "黑曜石");
        TOKEN.put("QUARTZ", "下界石英");
        TOKEN.put("END_STONE", "末地石");
        TOKEN.put("END", "末地");
        TOKEN.put("BRICK", "砖");
        TOKEN.put("MUD", "泥");
        TOKEN.put("DIRT", "泥土");
        TOKEN.put("SNOW", "雪");
        TOKEN.put("ICE", "冰");
        TOKEN.put("MAGMA", "岩浆");
        TOKEN.put("SOUL", "灵魂");
        TOKEN.put("NETHER", "下界");
        TOKEN.put("WARPED", "诡异");
        TOKEN.put("CRIMSON", "猩红");
        TOKEN.put("STEM", "菌杆");
        TOKEN.put("HYPHAE", "菌丝");
        TOKEN.put("SHROOMLIGHT", "发光菌岩");
        TOKEN.put("MOSS", "苔藓");
        TOKEN.put("BAMBOO", "竹");
        TOKEN.put("CHERRY", "樱花");
        TOKEN.put("MANGROVE", "红树林");
        TOKEN.put("PALE", "苍白");
        TOKEN.put("RESIN", "树脂");
        TOKEN.put("OAK", "橡木");
        TOKEN.put("SPRUCE", "云杉");
        TOKEN.put("BIRCH", "白桦");
        TOKEN.put("JUNGLE", "丛林");
        TOKEN.put("ACACIA", "金合欢");
        TOKEN.put("DARK_OAK", "深色橡木");
        TOKEN.put("SOUL_OAK", "灵魂橡木");
        TOKEN.put("IRON", "铁");
        TOKEN.put("GOLD", "金");
        TOKEN.put("NETHERITE", "下界合金");
        TOKEN.put("COPPER", "铜");
        TOKEN.put("DIAMOND", "钻石");
        TOKEN.put("EMERALD", "绿宝石");
        TOKEN.put("LAPIS", "青金石");
        TOKEN.put("REDSTONE", "红石");
        TOKEN.put("CORAL", "珊瑚");
        TOKEN.put("AZALEA", "杜鹃花丛");
        TOKEN.put("PRISMARINE", "海晶石");
        TOKEN.put("BLACKSTONE", "黑石");
        TOKEN.put("DRIPSTONE", "钟乳石");
        TOKEN.put("COAL", "煤");
        TOKEN.put("AMETHYST", "紫水晶");
        TOKEN.put("BONE", "骨");
        TOKEN.put("HAY", "干草");
        TOKEN.put("HONEYCOMB", "蜂巢");
        TOKEN.put("MOSAIC", "竹片");
        TOKEN.put("TILE", "瓦");
        TOKEN.put("SAND", "沙");
        TOKEN.put("SOIL", "土");
        TOKEN.put("PETRIFIED", "石化");
        TOKEN.put("PURPUR", "紫珀");
        TOKEN.put("INFESTED", "蛀蚀");
        TOKEN.put("CRACKED", "裂纹");
        TOKEN.put("CHISELED", "雕纹");
        TOKEN.put("PETRIFIED_OAK", "石化橡木");
        TOKEN.put("DEEPSLATE_TILE", "深板岩瓦");
        TOKEN.put("POLISHED_BLACKSTONE_BRICK", "磨制黑石砖");
        TOKEN.put("POLISHED_BLACKSTONE", "磨制黑石");

        TOKEN.put("WHITE", "白色");
        TOKEN.put("ORANGE", "橙色");
        TOKEN.put("MAGENTA", "品红色");
        TOKEN.put("LIGHT_BLUE", "淡蓝色");
        TOKEN.put("YELLOW", "黄色");
        TOKEN.put("LIME", "黄绿色");
        TOKEN.put("PINK", "粉红色");
        TOKEN.put("GRAY", "灰色");
        TOKEN.put("LIGHT_GRAY", "淡灰色");
        TOKEN.put("CYAN", "青色");
        TOKEN.put("PURPLE", "紫色");
        TOKEN.put("BLUE", "蓝色");
        TOKEN.put("BROWN", "棕色");
        TOKEN.put("GREEN", "绿色");
        TOKEN.put("RED", "红色");
        TOKEN.put("BLACK", "黑色");

        // ---------- 修饰词 ----------
        TOKEN.put("STRIPPED", "去皮");
        TOKEN.put("POLISHED", "磨制");
        TOKEN.put("SMOOTH", "平滑");
        TOKEN.put("WAXED", "涂蜡");
        TOKEN.put("CUT", "切制");
        TOKEN.put("GLAZED", "釉");
        TOKEN.put("STAINED", "染色");
        TOKEN.put("EXPOSED", "暴露");
        TOKEN.put("WEATHERED", "风化");
        TOKEN.put("OXIDIZED", "氧化");
        TOKEN.put("MOSSY", "苔藓");
        TOKEN.put("COBBLED", "铺圆石");
        TOKEN.put("PACKED", "压紧");
        TOKEN.put("DARK", "暗");
        TOKEN.put("COARSE", "粗");
        TOKEN.put("ROOTED", "带根");
        TOKEN.put("CRYING", "哭泣");
        TOKEN.put("FROSTED", "磨砂");

        // ---------- 制品后缀 ----------
        SUFFIX.put("_PRESSURE_PLATE", "压力板");
        SUFFIX.put("_HANGING_SIGN", "悬挂告示牌");
        SUFFIX.put("_FENCE_GATE", "栅栏门");
        SUFFIX.put("_GLASS_PANE", "玻璃板");
        SUFFIX.put("_CONCRETE_POWDER", "混凝土粉末");
        SUFFIX.put("_STAIRS", "楼梯");
        SUFFIX.put("_SLAB", "台阶");
        SUFFIX.put("_WALL", "墙");
        SUFFIX.put("_FENCE", "栅栏");
        SUFFIX.put("_DOOR", "门");
        SUFFIX.put("_TRAPDOOR", "活板门");
        SUFFIX.put("_BUTTON", "按钮");
        SUFFIX.put("_SIGN", "告示牌");
        SUFFIX.put("_PLANKS", "木板");
        SUFFIX.put("_TERRACOTTA", "陶瓦");
        SUFFIX.put("_CONCRETE", "混凝土");
        SUFFIX.put("_CARPET", "地毯");
        SUFFIX.put("_WOOL", "羊毛");
        SUFFIX.put("_GLASS", "玻璃");
        SUFFIX.put("_LEAVES", "树叶");
        SUFFIX.put("_SAPLING", "树苗");
        SUFFIX.put("_HYPHAE", "菌丝");
        SUFFIX.put("_STEM", "菌杆");
        SUFFIX.put("_TILES", "瓦");
        SUFFIX.put("_BRICKS", "砖");
        SUFFIX.put("_WOOD", "木");
        SUFFIX.put("_LOG", "原木");
        SUFFIX.put("_BLOCK", "块");
        SUFFIX.put("_PILLAR", "柱石");
    }

    private BlockNames() {
    }

    /**
     * 按命名规律翻译材料名；词根不认识时返回 null（调用方决定退回英文）。
     */
    public static String translate(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        String name = materialName.toUpperCase(Locale.ROOT);
        String full = FULL.get(name);
        if (full != null) {
            return full;
        }

        String suffix = "";
        String base = name;
        for (Map.Entry<String, String> entry : SUFFIX.entrySet()) {
            if (base.endsWith(entry.getKey()) && base.length() > entry.getKey().length()) {
                suffix = entry.getValue();
                base = base.substring(0, base.length() - entry.getKey().length());
                break;
            }
        }

        String prefix = translateTokens(base);
        if (prefix == null) {
            return null;
        }
        String out = prefix + suffix;
        return out.isEmpty() ? null : out;
    }

    /** 逐词根翻译（优先整词匹配，再按最长词根贪心拆分）；任一词根不认识就返回 null。 */
    private static String translateTokens(String base) {
        if (base.isEmpty()) {
            return "";
        }
        String direct = TOKEN.get(base);
        if (direct != null) {
            return direct;
        }
        StringBuilder out = new StringBuilder();
        String rest = base;
        while (!rest.isEmpty()) {
            String best = null;
            for (String key : TOKEN.keySet()) {
                if (rest.equals(key) || rest.startsWith(key + "_")) {
                    if (best == null || key.length() > best.length()) {
                        best = key;
                    }
                }
            }
            if (best == null) {
                return null;
            }
            out.append(TOKEN.get(best));
            rest = best.length() < rest.length() ? rest.substring(best.length() + 1) : "";
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** 字符串里是否含中文字符（用来校验最终显示名，和 hasChinese 不同：这里不查材料名）。 */
    public static boolean containsChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x4E00 && text.charAt(i) <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /** 该材料名能否翻译成中文（自检用）。 */
    public static boolean hasChinese(String materialName) {
        String out = translate(materialName);
        if (out == null) {
            return false;
        }
        for (int i = 0; i < out.length(); i++) {
            if (out.charAt(i) >= 0x4E00 && out.charAt(i) <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /** 词根表大小（自检/文档用）。 */
    public static int tokenCount() {
        return TOKEN.size() + FULL.size();
    }
}
