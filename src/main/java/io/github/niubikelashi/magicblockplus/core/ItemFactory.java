package io.github.niubikelashi.magicblockplus.core;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 魔法方块/魔法食物物品的创建与识别。
 *
 * 关键修复：物品身份用 PersistentDataContainer 标记，而不是匹配 lore 字符串。
 * 原版靠 lore 文本判定魔法方块，改了配置文案（或语言）后物品就不再被识别，
 * 方块次数与绑定会「莫名其妙消失」。
 */
public class ItemFactory {

    public static final String KIND_BLOCK = "block";
    public static final String KIND_FOOD = "food";

    /** -1 表示无限次数（不再递减）。 */
    public static final int INFINITE = -1;

    private final MagicBlockPlusPlugin plugin;
    private final NamespacedKey keyMarker;
    private final NamespacedKey keyBlockId;
    private final NamespacedKey keyOwner;
    private final NamespacedKey keyUses;
    private final NamespacedKey keyMaxUses;

    private Map<String, String> translations = new LinkedHashMap<>();
    private String nameFormat = "&b✦ %s ✦";
    private boolean showBoundPlayer = true;
    private boolean showUsageCount = true;
    private boolean showProgressBar = true;
    private boolean decorativeEnabled = true;
    private List<String> decorativeLines = new ArrayList<>();
    private int barLength = 10;
    private String boundPrefix = "&7绑定到: &b";
    private String usagePrefix = "&7使用次数:";

    public ItemFactory(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
        this.keyMarker = new NamespacedKey(plugin, "mbp_marker");
        this.keyBlockId = new NamespacedKey(plugin, "mbp_block_id");
        this.keyOwner = new NamespacedKey(plugin, "mbp_owner");
        this.keyUses = new NamespacedKey(plugin, "mbp_uses");
        this.keyMaxUses = new NamespacedKey(plugin, "mbp_max_uses");
    }

    public void applySettings(Map<String, String> translationMap, String format, boolean boundPlayer,
                              boolean usageCount, boolean progressBar, boolean decorative,
                              List<String> lines, int length, String boundLorePrefix, String usageLorePrefix) {
        this.translations = new LinkedHashMap<>(translationMap);
        this.nameFormat = format;
        this.showBoundPlayer = boundPlayer;
        this.showUsageCount = usageCount;
        this.showProgressBar = progressBar;
        this.decorativeEnabled = decorative;
        this.decorativeLines = new ArrayList<>(lines);
        this.barLength = Math.max(1, Math.min(40, length));
        this.boundPrefix = boundLorePrefix;
        this.usagePrefix = usageLorePrefix;
    }

    public ItemStack createBlockItem(Material material, int uses, UUID owner) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int clamped = uses == INFINITE ? INFINITE : Math.max(0, Math.min(plugin.maxUsesLimit(), uses));
        int max = uses == INFINITE ? INFINITE : Math.max(clamped, 1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyMarker, PersistentDataType.STRING, KIND_BLOCK);
        pdc.set(keyBlockId, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(keyUses, PersistentDataType.INTEGER, clamped);
        pdc.set(keyMaxUses, PersistentDataType.INTEGER, max);
        if (owner != null) {
            pdc.set(keyOwner, PersistentDataType.STRING, owner.toString());
        }
        meta.setDisplayName(Text.color(nameFormat.replace("%s", displayName(material))));
        meta.setLore(buildLore(material, KIND_BLOCK, owner, clamped, max));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createFoodItem(Material material, int uses, String customName, List<String> baseLore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int clamped = uses == INFINITE ? INFINITE : Math.max(0, Math.min(plugin.maxUsesLimit(), uses));
        int max = uses == INFINITE ? INFINITE : Math.max(clamped, 1);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyMarker, PersistentDataType.STRING, KIND_FOOD);
        pdc.set(keyBlockId, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(keyUses, PersistentDataType.INTEGER, clamped);
        pdc.set(keyMaxUses, PersistentDataType.INTEGER, max);

        String name = customName == null || customName.isEmpty()
                ? Text.color(nameFormat.replace("%s", displayName(material)))
                : Text.color(customName);
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        if (baseLore != null) {
            for (String line : baseLore) {
                lore.add(resolveVariables(Text.color(line), clamped, max, null));
            }
        }
        if (showUsageCount) {
            lore.add(usageLine(clamped, max));
        }
        if (showProgressBar && max != INFINITE) {
            lore.add(progressBar(clamped, max));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMagic(ItemStack item) {
        return kind(item) != null;
    }

    public String kind(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyMarker, PersistentDataType.STRING);
    }

    public int uses(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return meta.getPersistentDataContainer().getOrDefault(keyUses, PersistentDataType.INTEGER, 0);
    }

    public int maxUses(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        int max = meta.getPersistentDataContainer().getOrDefault(keyMaxUses, PersistentDataType.INTEGER, 0);
        return max <= 0 ? Math.max(uses(item), 1) : max;
    }

    public String blockId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyBlockId, PersistentDataType.STRING);
    }

    public UUID owner(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 递减一次；无限次数不递减。返回递减后的剩余次数（不会变成负数）。 */
    public int decrement(ItemStack item) {
        int current = uses(item);
        if (current == INFINITE) {
            return INFINITE;
        }
        int next = Math.max(0, current - 1);
        setUses(item, next);
        return next;
    }

    public void setUses(ItemStack item, int newUses) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return;
        }
        int max = maxUses(item);
        int clamped = newUses == INFINITE ? INFINITE : Math.max(0, Math.min(plugin.maxUsesLimit(), newUses));
        meta.getPersistentDataContainer().set(keyUses, PersistentDataType.INTEGER, clamped);
        meta.setLore(buildLore(item.getType(), kind(item), owner(item), clamped, max));
        item.setItemMeta(meta);
    }

    public void setOwner(ItemStack item, UUID owner) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (owner == null) {
            meta.getPersistentDataContainer().remove(keyOwner);
        } else {
            meta.getPersistentDataContainer().set(keyOwner, PersistentDataType.STRING, owner.toString());
        }
        meta.setLore(buildLore(item.getType(), kind(item), owner, uses(item), maxUses(item)));
        item.setItemMeta(meta);
    }

    /** 次数用尽后变成普通方块物品（不再被插件追踪）。 */
    public ItemStack toPlain(ItemStack item) {
        return new ItemStack(item.getType(), 1);
    }

    public String displayName(Material material) {
        String custom = translations.get(material.name());
        String base = custom != null && !custom.isEmpty() ? custom : humanize(material.name());
        String template = nameFormat == null || nameFormat.isEmpty() ? base : Text.color(nameFormat);
        String out = template.replace("%material", base).replace("%block", base)
                .replace("%type", base).replace("%s", base);
        return Text.color(out);
    }

    /** 展示用物品：普通方块 + 魔法样式的名/lore，没有 PDC，所以不会被误认成魔法方块。 */
    public ItemStack previewItem(Material material, int remaining, int max, UUID owner, List<String> extraLore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName(material));
            List<String> lore = new ArrayList<>(buildLore(material, KIND_BLOCK, owner, remaining, max));
            if (extraLore != null) {
                for (String line : extraLore) {
                    lore.add(Text.color(line));
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public List<String> previewLore(Material material, int remaining, int max, UUID owner) {
        return buildLore(material, KIND_BLOCK, owner, remaining, max);
    }

    private static String humanize(String materialName) {
        String lower = materialName.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if (lower.isEmpty()) {
            return "AIR";
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : lower.toCharArray()) {
            if (c == ' ') {
                sb.append(' ');
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }

    private List<String> buildLore(Material material, String kind, UUID owner, int remaining, int max) {
        List<String> lore = new ArrayList<>();
        String ownerName = ownerName(owner);

        if (decorativeEnabled) {
            for (String line : decorativeLines) {
                lore.add(withPlaceholders(resolveVariables(Text.color(line), remaining, max, ownerName),
                        remaining, max, ownerName));
            }
        }
        if (showBoundPlayer && owner != null) {
            lore.add(boundPrefix + ownerName);
        }
        if (showUsageCount) {
            lore.add(usageLine(remaining, max));
        }
        if (showProgressBar && max != INFINITE) {
            lore.add(progressBar(remaining, max));
        }
        if (kind == null) {
            kind = KIND_BLOCK;
        }
        // 末行：类型说明，方便玩家区分魔法方块与魔法食物
        lore.add("&8✦ " + (KIND_FOOD.equals(kind) ? "魔法食物" : "魔法方块") + " ✦");
        return lore;
    }

    private String ownerName(UUID owner) {
        if (owner == null) {
            return "";
        }
        var online = Bukkit.getPlayer(owner);
        if (online != null) {
            return online.getName();
        }
        String cached = plugin.offlineName(owner);
        return cached != null ? cached : owner.toString();
    }

    private String usageLine(int remaining, int max) {
        StringBuilder sb = new StringBuilder();
        sb.append(Text.color("&7" + usagePrefix + " "));
        if (max == INFINITE) {
            sb.append(Text.color("&b∞"));
        } else {
            sb.append(Text.color("&b" + remaining + "&7/&f" + max));
        }
        return sb.toString();
    }

    private String progressBar(int remaining, int max) {
        double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, (double) remaining / max));
        int filled = (int) Math.round(ratio * barLength);
        StringBuilder sb = new StringBuilder(Text.color("&7["));
        for (int i = 0; i < barLength; i++) {
            sb.append(i < filled ? Text.color("&a■") : Text.color("&8■"));
        }
        sb.append(Text.color("&7]"));
        return sb.toString();
    }

    private String resolveVariables(String line, int remaining, int max, String ownerName) {
        if (remaining == INFINITE || max == INFINITE) {
            line = line.replace("%remaining%", "∞").replace("%max%", "∞")
                    .replace("%uses%", "∞").replace("%progress%", "∞").replace("%bar%", "∞");
        } else {
            int used = Math.max(0, max - remaining);
            line = line.replace("%remaining%", String.valueOf(remaining))
                    .replace("%max%", String.valueOf(max))
                    .replace("%uses%", String.valueOf(used))
                    .replace("%progress%", String.valueOf(Math.round((double) remaining / Math.max(1, max) * 100)))
                    .replace("%bar%", progressBar(remaining, max));
        }
        line = line.replace("%owner%", ownerName == null ? "" : ownerName);
        return line;
    }

    /**
     * 若装了 PlaceholderAPI，再把剩下的变量交给它解析；没装就原样保留，不会崩溃。
     * 原版直接 import me.clip.placeholderapi，没装 PAPI 时插件启动就 NoClassDefFoundError。
     */
    private String withPlaceholders(String line, int remaining, int max, String ownerName) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return line;
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object result = api.getMethod("setPlaceholders", String.class, org.bukkit.entity.Player.class)
                    .invoke(null, line, null);
            return result == null ? line : result.toString();
        } catch (Throwable t) {
            return line;
        }
    }
}
