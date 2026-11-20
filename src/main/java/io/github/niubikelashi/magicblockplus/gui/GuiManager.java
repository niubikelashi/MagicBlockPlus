package io.github.niubikelashi.magicblockplus.gui;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.core.BlockRecord;
import io.github.niubikelashi.magicblockplus.core.Settings;
import io.github.niubikelashi.magicblockplus.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI：材料选择页 + 我的收藏列表。
 *
 * 相对原版的修复：
 * - 展示物品是「普通物品 + 魔法样式」，不带 PersistentDataContainer，
 *   所以点开 GUI 不会往背包里塞进可被追踪的魔法物品，也不会被 GUI 里的点击
 *   当作真实记录反复取回（原版 GUI 展示物品带真实记录，左键就能反复刷同一个方块）。
 * - 取回（retrieve）是「收回世界里的方块」：删记录 + 方块变空气 + 给物品，
 *   一次性完成，重复点击只会拿到空气提示，不会刷方块。
 * - 会话（session）在关闭时清理；不再维护 lastGuiOpenTime / chunkLocationCache 这类永不释放的缓存。
 */
public class GuiManager {

    public enum Kind {
        SELECTION,
        FAVORITES
    }

    public static class Session {
        public final Kind kind;
        public final UUID player;
        public int page;

        public Session(Kind kind, UUID player) {
            this.kind = kind;
            this.player = player;
        }
        public String keyword;
        public final List<String> filtered = new ArrayList<>();
        public final List<BlockRecord> records = new ArrayList<>();
        public int perPage;
        public int contentSlots;
        public int reserved;
    }

    private final MagicBlockPlusPlugin plugin;
    private final Map<Inventory, Session> sessions = new ConcurrentHashMap<>();
    private final List<Material> materials = new ArrayList<>();
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();

    public GuiManager(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 材料表来自 BlockCatalog：内置常见建筑方块（按类别）+ 自动发现的同类新方块 +
     * 用户在 gui.materials / gui.group 里手动加的材料。
     */
    public void rebuildMaterials() {
        materials.clear();
        materials.addAll(plugin.catalog().materials());
    }

    public String categoryOf(Material material) {
        return plugin.catalog().categoryOf(material);
    }

    /** 中文名或英文材料名都算命中，所以 /mb search 玻璃 能搜到 GLASS。 */
    private boolean matches(Material material, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        if (material.name().toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        String chinese = plugin.items().plainName(material);
        if (chinese != null && chinese.toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        return plugin.catalog().categoryOf(material).contains(filter);
    }

    public int materialCount() {
        return materials.size();
    }

    public List<Material> materials() {
        return materials;
    }

    // ---------- 打开 ----------

    public void openSelection(Player player, int page, String keyword) {
        Settings settings = plugin.settings();
        int rows = Math.max(2, settings.guiRows);
        int size = rows * 9;
        int contentSlots = size - 9;
        int reserved = size - 9;
        String filter = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        Session session = new Session(Kind.SELECTION, player.getUniqueId());
        session.keyword = filter;
        session.contentSlots = contentSlots;
        session.perPage = contentSlots;
        session.reserved = reserved;
        for (Material material : materials) {
            if (matches(material, filter)) {
                session.filtered.add(material.name());
            }
        }
        int pages = Math.max(1, (int) Math.ceil(session.filtered.size() / (double) contentSlots));
        session.page = Math.max(0, Math.min(page, pages - 1));

        Inventory inventory = Bukkit.createInventory(null, size, Text.color(settings.guiTitle));
        fillSelection(inventory, session);
        sessions.put(inventory, session);
        player.openInventory(inventory);
        plugin.stats().guiOpen();
    }

    private void fillSelection(Inventory inventory, Session session) {
        Settings settings = plugin.settings();
        int start = session.page * session.perPage;
        for (int slot = 0; slot < session.contentSlots; slot++) {
            int index = start + slot;
            if (index >= session.filtered.size()) {
                continue;
            }
            Material material = Material.matchMaterial(session.filtered.get(index));
            if (material == null) {
                continue;
            }
            int uses = plugin.defaultUses(material);
        String chinese = plugin.items().plainName(material);
        List<String> extra = new ArrayList<>();
        extra.add("&7左键领取 1 个，次数 " + uses);
        if (plugin.settings().showCategory) {
            extra.add("&8类别: &7" + categoryOf(material));
        }
        if (!chinese.equals(material.name())) {
            extra.add("&8枚举名: &7" + material.name());
        }
        inventory.setItem(slot, plugin.items().previewItem(material, uses, uses, null, extra));
        }
        int reserved = inventory.getSize() - 9;
        putButton(inventory, reserved, "previous-page", session.page > 0);
        putButton(inventory, reserved + 1, "page-info", true);
        putButton(inventory, reserved + 2, "next-page", session.page < pageCount(session) - 1);
        putButton(inventory, reserved + 8, "close", true);
    }

    private int pageCount(Session session) {
        return Math.max(1, (int) Math.ceil(session.filtered.size() / (double) Math.max(1, session.perPage)));
    }

    private void putButton(Inventory inventory, int slot, String key, boolean enabled) {
        Settings.Button button = plugin.settings().buttons.get(key);
        if (button == null) {
            return;
        }
        Material material = enabled ? Material.matchMaterial(button.material) : Material.matchMaterial(button.disabledMaterial);
        if (material == null || material.isAir()) {
            material = enabled ? Material.ARROW : Material.GRAY_DYE;
        }
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(enabled ? button.name : button.disabledName));
            List<String> lore = new ArrayList<>();
            for (String line : button.lore) {
                lore.add(Text.color(line.replace("%page%", String.valueOf(1))));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public void openFavorites(Player player) {
        Settings settings = plugin.settings();
        int rows = Math.max(2, settings.guiRows);
        int size = rows * 9;

        Session session = new Session(Kind.FAVORITES, player.getUniqueId());
        session.reserved = size - 9;
        List<BlockRecord> owned = plugin.store().recordsOf(player.getUniqueId());
        owned.sort(Comparator.comparingLong(r -> r.createdAt));
        session.records.addAll(owned);

        Inventory inventory = Bukkit.createInventory(null, size, Text.color(settings.guiTitle));
        int limit = Math.min(session.reserved, session.records.size());
        for (int slot = 0; slot < limit; slot++) {
            BlockRecord record = session.records.get(slot);
            inventory.setItem(slot, plugin.items().previewItem(record.materialEnum(), record.uses, record.maxUses,
                    record.ownerUuid(), List.of("&7坐标: " + record.world + " " + record.x + "," + record.y + "," + record.z,
                    record.hidden ? "&c已隐藏（右键切换）" : "&a左键取回方块")));
        }
        int reserved = size - 9;
        putButton(inventory, reserved + 8, "close", true);
        if (session.records.size() > limit) {
            plugin.text().send(player, "gui-page-info", Text.ph("page", String.valueOf(limit),
                    "total", String.valueOf(session.records.size())));
        }
        sessions.put(inventory, session);
        player.openInventory(inventory);
        plugin.stats().guiOpen();
    }

    // ---------- 点击 ----------

    public Session sessionOf(Inventory inventory) {
        return sessions.get(inventory);
    }

    public void closeSession(Inventory inventory) {
        sessions.remove(inventory);
    }

    public void clearSessions() {
        sessions.clear();
        lastAction.clear();
    }

    public void handleClick(Player player, Session session, int slot, boolean rightClick) {
        plugin.stats().guiClick();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = lastAction.get(uuid);
        if (previous != null && now - previous < 250) {
            return;
        }
        lastAction.put(uuid, now);

        if (slot >= session.reserved) {
            handleButton(player, session, slot - session.reserved);
            return;
        }

        if (session.kind == Kind.SELECTION) {
            int index = session.page * session.perPage + slot;
            if (index >= session.filtered.size()) {
                return;
            }
            Material material = Material.matchMaterial(session.filtered.get(index));
            if (material == null) {
                return;
            }
            if (!player.hasPermission("magicblock.use")) {
                plugin.text().send(player, "no-permission-use");
                return;
            }
            int uses = plugin.defaultUses(material);
            ItemStack item = plugin.items().createBlockItem(material, uses,
                    plugin.settings().enableBinding ? uuid : null);
            if (plugin.settings().enableBinding) {
                plugin.text().send(player, "bound-to-you");
            }
            give(player, item);
            plugin.stats().give();
            plugin.logUsage(player, "gui 领取 " + material.name() + " 次数 " + uses);
            return;
        }

        if (slot >= session.records.size()) {
            return;
        }
        BlockRecord record = session.records.get(slot);
        if (rightClick) {
            boolean hidden = !record.hidden;
            plugin.store().setHidden(record.world, record.x, record.y, record.z, hidden);
            plugin.text().send(player, "toggled-hidden", Text.ph("state", hidden ? "已隐藏" : "已显示"));
            refreshFavorites(player, session);
            return;
        }
        retrieve(player, record);
        refreshFavorites(player, session);
    }

    private void handleButton(Player player, Session session, int offset) {
        if (offset == 8) {
            player.closeInventory();
            return;
        }
        if (session.kind != Kind.SELECTION) {
            return;
        }
        int pages = pageCount(session);
        if (offset == 0) {
            if (session.page > 0) {
                session.page--;
                reopenSelection(player, session);
            }
        } else if (offset == 2) {
            if (session.page < pages - 1) {
                session.page++;
                reopenSelection(player, session);
            }
        } else if (offset == 1) {
            plugin.text().send(player, "gui-page-info",
                    Text.ph("page", String.valueOf(session.page + 1), "total", String.valueOf(pages)));
        }
    }

    private void reopenSelection(Player player, Session session) {
        int size = session.contentSlots + session.reserved;
        Inventory inventory = Bukkit.createInventory(null, size,
                Text.color(plugin.settings().guiTitle));
        fillSelection(inventory, session);
        sessions.put(inventory, session);
        player.openInventory(inventory);
    }

    private void refreshFavorites(Player player, Session session) {
        List<BlockRecord> owned = plugin.store().recordsOf(player.getUniqueId());
        owned.sort(Comparator.comparingLong(r -> r.createdAt));
        session.records.clear();
        session.records.addAll(owned);
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (sessionOf(inventory) != session) {
            return;
        }
        int limit = Math.min(session.reserved, session.records.size());
        for (int slot = 0; slot < session.reserved; slot++) {
            if (slot < limit) {
                BlockRecord record = session.records.get(slot);
                inventory.setItem(slot, plugin.items().previewItem(record.materialEnum(), record.uses, record.maxUses,
                        record.ownerUuid(), List.of("&7坐标: " + record.world + " " + record.x + "," + record.y + "," + record.z,
                        record.hidden ? "&c已隐藏（右键切换）" : "&a左键取回方块")));
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    /**
     * 取回：把世界里的魔法方块收回成物品。记录先删除，所以第二次点击只会拿到「取回失败」，
     * 不会像原版那样按 material 连带删除其它方块与绑定，也不会重复生成物品。
     */
    public void retrieve(Player player, BlockRecord record) {
        BlockRecord removed = plugin.store().removeIfPresent(record.world, record.x, record.y, record.z);
        if (removed == null) {
            plugin.text().send(player, "retrieve-failed", Text.ph("reason", "记录已不存在"));
            return;
        }
        var world = Bukkit.getWorld(record.world);
        if (world == null) {
            plugin.text().send(player, "retrieve-failed", Text.ph("reason", "世界不存在"));
            return;
        }
        var block = world.getBlockAt(record.x, record.y, record.z);
        if (block.getType() != record.materialEnum()) {
            // 方块已被别的东西替换：记录是陈旧数据，直接丢弃，不凭空给物品
            plugin.text().send(player, "retrieve-failed", Text.ph("reason", "方块已被替换"));
            return;
        }
        block.setType(Material.AIR);
        give(player, plugin.items().createBlockItem(record.materialEnum(), record.uses, record.ownerUuid()));
        plugin.stats().retrieve();
        plugin.text().send(player, "retrieved", Text.ph("block", plugin.items().displayName(record.materialEnum())));
        plugin.logUsage(player, "retrieve " + record.key());
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            Location at = player.getLocation().add(0, 1, 0);
            player.getWorld().dropItem(at, rest);
        }
    }
}
