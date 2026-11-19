package io.github.niubikelashi.magicblockplus.core;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.util.FileUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 已放置魔法方块的索引 + 持久化。
 *
 * 修复点（相对原版 MagicBlockIndexManager）：
 * 1. 记录保存在插件自己的文件里，启动时全量读入，不依赖「只扫描已加载区块」，
 *    所以重启后未加载区块里的魔法方块不会丢身份（原版重启后它们变成普通方块，
 *    破坏时按原版掉落 → 看起来像「方块消失」）。
 * 2. 不再往区块 PDC 里塞拼接的超长字符串（无上限、并发写会互相覆盖）。
 * 3. 不做 5 分钟一次的「遍历所有索引并 getBlock()」——那会强制加载大量区块造成卡顿，
 *    并且把还没加载的区块误判成空气而删掉记录。清理只在事件确认方块真的没了时做。
 * 4. 写盘走单线程原子写，脏标记 + 节流；主线程只改内存，不在异步线程改 FileConfiguration。
 */
public class BlockStore {

    private final MagicBlockPlusPlugin plugin;
    private final File file;
    private final Map<String, ConcurrentHashMap<String, BlockRecord>> byWorld = new ConcurrentHashMap<>();
    private final AtomicInteger skippedOnLoad = new AtomicInteger();
    private volatile boolean dirty = false;
    private volatile long lastSaveMillis = 0;
    private final Object writeLock = new Object();

    public BlockStore(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "world-blocks.yml");
    }

    public File file() {
        return file;
    }

    public int skippedOnLoad() {
        return skippedOnLoad.get();
    }

    public void load() {
        byWorld.clear();
        skippedOnLoad.set(0);
        if (!file.exists()) {
            plugin.debug("还没有 world-blocks.yml，索引为空");
            return;
        }
        YamlConfiguration yaml = FileUtil.loadYaml(file);
        ConfigurationSection blocks = yaml.getConfigurationSection("blocks");
        if (blocks == null) {
            plugin.debug("world-blocks.yml 里没有 blocks 节点");
            return;
        }
        int loaded = 0;
        for (String worldName : blocks.getKeys(false)) {
            ConfigurationSection worldSection = blocks.getConfigurationSection(worldName);
            if (worldSection == null) {
                continue;
            }
            ConcurrentHashMap<String, BlockRecord> map = new ConcurrentHashMap<>();
            for (String coordKey : worldSection.getKeys(false)) {
                ConfigurationSection section = worldSection.getConfigurationSection(coordKey);
                if (section == null) {
                    continue;
                }
                int[] coords = parseCoords(coordKey);
                if (coords == null) {
                    skippedOnLoad.incrementAndGet();
                    continue;
                }
                String materialName = section.getString("material", "");
                Material material = Material.matchMaterial(materialName);
                if (material == null || material == Material.AIR) {
                    // 当前服务端版本不认识这个材料名：跳过而不是抛异常
                    skippedOnLoad.incrementAndGet();
                    plugin.debug("跳过不支持的材料: " + materialName + " @ " + worldName + " " + coordKey);
                    continue;
                }
                BlockRecord record = new BlockRecord(worldName, coords[0], coords[1], coords[2], material.name(),
                        section.getString("id"), section.getString("owner"),
                        section.getInt("uses", 0), section.getInt("max", 0),
                        section.getBoolean("hidden", false), section.getLong("created", 0L));
                map.put(record.key(), record);
                loaded++;
            }
            if (!map.isEmpty()) {
                byWorld.put(worldName, map);
            }
        }
        plugin.getLogger().info("已载入 " + loaded + " 个魔法方块记录"
                + (skippedOnLoad.get() > 0 ? "（跳过 " + skippedOnLoad.get() + " 条无法识别的记录）" : ""));
    }

    private static int[] parseCoords(String coordKey) {
        String[] parts = coordKey.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean worldHasMagicBlocks(String worldName) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(worldName);
        return map != null && !map.isEmpty();
    }

    public BlockRecord get(Location location) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(location.getWorld().getName());
        if (map == null) {
            return null;
        }
        return map.get(keyOf(location));
    }

    public BlockRecord get(String world, int x, int y, int z) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(world);
        if (map == null) {
            return null;
        }
        return map.get(world + ";" + x + ";" + y + ";" + z);
    }

    public boolean contains(Location location) {
        return get(location) != null;
    }

    public void register(BlockRecord record) {
        byWorld.computeIfAbsent(record.world, k -> new ConcurrentHashMap<>()).put(record.key(), record);
        dirty = true;
        plugin.debug("注册魔法方块 " + record.key() + "（剩余 " + record.uses + "）");
    }

    public void register(Location location, Material material, String blockId, String owner,
                         int uses, int maxUses, boolean hidden) {
        register(new BlockRecord(location, material, blockId, owner, uses, maxUses, hidden));
    }

    public BlockRecord remove(Location location) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(location.getWorld().getName());
        if (map == null) {
            return null;
        }
        BlockRecord removed = map.remove(keyOf(location));
        if (removed == null) {
            return null;
        }
        if (map.isEmpty()) {
            byWorld.remove(location.getWorld().getName());
        }
        dirty = true;
        plugin.debug("移除魔法方块记录 " + removed.key());
        return removed;
    }

    public BlockRecord removeIfPresent(String world, int x, int y, int z) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(world);
        if (map == null) {
            return null;
        }
        BlockRecord removed = map.remove(world + ";" + x + ";" + y + ";" + z);
        if (removed != null) {
            if (map.isEmpty()) {
                byWorld.remove(world);
            }
            dirty = true;
        }
        return removed;
    }

    public void setHidden(String world, int x, int y, int z, boolean hidden) {
        BlockRecord record = get(world, x, y, z);
        if (record == null) {
            return;
        }
        record.hidden = hidden;
        dirty = true;
    }

    public void setUses(String world, int x, int y, int z, int uses) {
        BlockRecord record = get(world, x, y, z);
        if (record == null) {
            return;
        }
        record.uses = uses == ItemFactory.INFINITE ? ItemFactory.INFINITE : Math.max(0, uses);
        dirty = true;
    }

    public List<BlockRecord> recordsOf(UUID owner) {
        List<BlockRecord> out = new ArrayList<>();
        String target = owner.toString();
        for (ConcurrentHashMap<String, BlockRecord> map : byWorld.values()) {
            for (BlockRecord record : map.values()) {
                if (target.equals(record.owner)) {
                    out.add(record);
                }
            }
        }
        return out;
    }

    public Collection<BlockRecord> allRecords() {
        List<BlockRecord> out = new ArrayList<>();
        for (ConcurrentHashMap<String, BlockRecord> map : byWorld.values()) {
            out.addAll(map.values());
        }
        return out;
    }

    public int total() {
        int count = 0;
        for (ConcurrentHashMap<String, BlockRecord> map : byWorld.values()) {
            count += map.size();
        }
        return count;
    }

    public int worldCount(String worldName) {
        ConcurrentHashMap<String, BlockRecord> map = byWorld.get(worldName);
        return map == null ? 0 : map.size();
    }

    public void clearDirty() {
        dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    /** 是否需要落盘：有脏数据且距上次落盘超过间隔。 */
    public boolean shouldSave(long intervalMillis) {
        return dirty && System.currentTimeMillis() - lastSaveMillis >= intervalMillis;
    }

    /** 主线程取内存快照（不调用任何 Bukkit 世界 API，可安全交给异步线程写）。 */
    public String snapshot() {
        return serialize();
    }

    /** 异步线程写快照；写完清脏标记。 */
    public void writeSnapshot(String text) {
        synchronized (writeLock) {
            try {
                FileUtil.saveYamlAtomically(file, parseable(text));
                dirty = false;
                lastSaveMillis = System.currentTimeMillis();
            } catch (IOException ex) {
                plugin.getLogger().warning("无法保存 world-blocks.yml: " + ex.getMessage());
            }
        }
    }

    /** 关闭插件时的同步落盘。 */
    public void flush() {
        writeSnapshot(snapshot());
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, ConcurrentHashMap<String, BlockRecord>> worldEntry : byWorld.entrySet()) {
            String worldName = worldEntry.getKey();
            for (BlockRecord record : worldEntry.getValue().values()) {
                String path = "blocks." + worldName + "." + record.x + "," + record.y + "," + record.z;
                yaml.set(path + ".material", record.material);
                yaml.set(path + ".id", record.blockId);
                yaml.set(path + ".owner", record.owner);
                yaml.set(path + ".uses", record.uses);
                yaml.set(path + ".max", record.maxUses);
                yaml.set(path + ".hidden", record.hidden);
                yaml.set(path + ".created", record.createdAt);
            }
        }
        return yaml.saveToString();
    }

    private YamlConfiguration parseable(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception ex) {
            plugin.getLogger().warning("序列化数据解析失败，跳过本次保存: " + ex.getMessage());
        }
        return yaml;
    }

    private static String keyOf(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";"
                + location.getBlockY() + ";" + location.getBlockZ();
    }

    /**
     * 启动时可选校验：把指向空气的记录清掉。会加载区块，默认关闭（配置 validate-on-startup）。
     */
    public void validateAgainstWorlds() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ConcurrentHashMap<String, BlockRecord>> worldEntry : byWorld.entrySet()) {
            org.bukkit.World world = plugin.getServer().getWorld(worldEntry.getKey());
            if (world == null) {
                continue;
            }
            for (BlockRecord record : worldEntry.getValue().values()) {
                if (world.getBlockAt(record.x, record.y, record.z).getType() == Material.AIR) {
                    toRemove.add(record.key());
                }
            }
        }
        for (String key : toRemove) {
            String[] parts = key.split(";");
            removeIfPresent(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("启动校验清理了 " + toRemove.size() + " 条指向空气的记录");
            flush();
        }
    }
}
