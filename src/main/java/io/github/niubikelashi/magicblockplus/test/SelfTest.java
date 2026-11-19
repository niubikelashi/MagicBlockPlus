package io.github.niubikelashi.magicblockplus.test;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.core.BlockRecord;
import io.github.niubikelashi.magicblockplus.core.BlockStore;
import io.github.niubikelashi.magicblockplus.core.ItemFactory;
import io.github.niubikelashi.magicblockplus.core.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自检（config.yml 的 self-test: true，或 /mb 之后手动触发）。
 * 最关键的一条是「次数守恒」：一个用 3 次的魔法方块，最多只能放 3 次，
 * 第 4 次必须被拒绝。原版的破坏逻辑会按默认次数重新生成物品，所以能无限刷。
 */
public class SelfTest {

    private final MagicBlockPlusPlugin plugin;

    public SelfTest(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> run() {
        List<String> problems = new ArrayList<>();
        UUID owner = UUID.randomUUID();
        ItemFactory items = plugin.items();

        // 1) 身份靠 PDC，不靠 lore 文本
        ItemStack item = items.createBlockItem(Material.STONE, 3, owner);
        if (!items.isMagic(item)) {
            problems.add("魔法方块物品没有被标记");
        }
        if (!ItemFactory.KIND_BLOCK.equals(items.kind(item))) {
            problems.add("物品类型标记错误");
        }
        if (items.uses(item) != 3 || items.maxUses(item) != 3) {
            problems.add("次数写入错误: " + items.uses(item) + "/" + items.maxUses(item));
        }
        if (!owner.equals(items.owner(item))) {
            problems.add("归属写入错误");
        }
        if (items.blockId(item) == null) {
            problems.add("blockId 缺失");
        }

        // 2) 次数守恒：3 次可用 → 正好 3 次放置，第 4 次拒绝
        int placements = 0;
        ItemStack current = item;
        while (items.uses(current) > 0) {
            int remaining = items.decrement(current);
            placements++;
            // 模拟破坏返还：按记录里的剩余次数重新生成一个物品
            current = items.createBlockItem(Material.STONE, remaining, owner);
            if (items.uses(current) != remaining) {
                problems.add("破坏返还的次数与记录不一致: " + items.uses(current) + " != " + remaining);
                break;
            }
        }
        if (placements != 3) {
            problems.add("次数不守恒：放出了 " + placements + " 次，应该是 3 次");
        }
        if (items.uses(current) != 0) {
            problems.add("用尽后剩余次数不是 0: " + items.uses(current));
        }

        // 3) 递减不会变成负数
        ItemStack small = items.createBlockItem(Material.DIRT, 1, owner);
        items.setUses(small, -5);
        if (items.uses(small) != 0) {
            problems.add("负数次数没有被收敛为 0: " + items.uses(small));
        }

        // 4) 次数用尽后不再是魔法物品
        ItemStack plain = items.toPlain(current);
        if (items.isMagic(plain)) {
            problems.add("toPlain 之后仍被当成魔法物品");
        }
        if (plain.getType() != Material.STONE) {
            problems.add("toPlain 丢失了材料");
        }

        // 5) 记录与快照的往返（不写文件，只验证序列化结构）
        List<org.bukkit.World> worlds = org.bukkit.Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            problems.add("自检时还没有加载世界");
        } else {
            org.bukkit.World world = worlds.get(0);
            BlockStore probe = new BlockStore(plugin);
            probe.register(new BlockRecord(new org.bukkit.Location(world, 1, 2, 3), Material.STONE,
                    "abc", owner.toString(), 4, 50, false));
            probe.register(new org.bukkit.Location(world, 9, 9, 9), Material.OAK_PLANKS, "def", null, 7, 50, true);
            String snapshot = probe.snapshot();
            YamlConfiguration parsed = YamlConfiguration.loadConfiguration(new java.io.StringReader(snapshot));
            ConfigurationSection blocks = parsed.getConfigurationSection("blocks");
            if (blocks == null) {
                problems.add("快照没有 blocks 段");
            } else {
                int worldGroups = blocks.getKeys(false).size();
                int records = 0;
                for (String worldKey : blocks.getKeys(false)) {
                    ConfigurationSection worldSection = blocks.getConfigurationSection(worldKey);
                    if (worldSection != null) {
                        records += worldSection.getKeys(false).size();
                    }
                }
                if (worldGroups < 1) {
                    problems.add("快照没有世界分组");
                }
                if (records != 2) {
                    problems.add("快照记录数量错误: " + records);
                }
            }
            if (probe.total() != 2) {
                problems.add("记录总数错误: " + probe.total());
            }
            BlockRecord stale = new BlockRecord(world.getName(), 1, 1, 1, "NOT_A_MATERIAL",
                    "x", null, 1, 1, false, System.currentTimeMillis());
            if (stale.materialEnum() != Material.AIR) {
                problems.add("未知材料没有收敛为 AIR");
            }
        }

        // 6) 配置解析容错：非法值必须收敛，不能抛异常
        YamlConfiguration junk = new YamlConfiguration();
        junk.set("default-uses", 1_000_000_000);
        junk.set("max-uses-limit", 0);
        junk.set("gui.rows", 99);
        junk.set("depleted-action", "banana");
        junk.set("piston-behavior", "sideways");
        junk.set("display.progress-bar-length", 999);
        Settings safe = Settings.from(junk);
        if (safe.maxUsesLimit < 1) {
            problems.add("max-uses-limit=0 没有被收敛");
        }
        if (safe.guiRows > 6) {
            problems.add("gui.rows=99 没有被收敛");
        }
        if (!safe.depletedAction.equals("convert")) {
            problems.add("非法 depleted-action 没有被收敛");
        }
        if (!safe.pistonBehavior.equals("cancel")) {
            problems.add("非法 piston-behavior 没有被收敛");
        }
        if (safe.progressBarLength > 40) {
            problems.add("进度条长度没有被收敛");
        }

        // 7) GUI 材料全部有效
        if (plugin.gui().materialCount() == 0) {
            problems.add("config.yml 的 gui.materials 里没有一个有效材料");
        }
        for (Material material : plugin.gui().materials()) {
            if (!material.isBlock() || material.isAir()) {
                problems.add("GUI 里有无效材料: " + material.name());
            }
        }

        // 8) 文案存在
        if (plugin.text().message("help").isEmpty()) {
            problems.add("messages 缺少 help");
        }
        if (plugin.text().message("retrieve-failed").isEmpty()) {
            problems.add("messages 缺少 retrieve-failed");
        }

        // 9) 魔法食物可食用
        for (var def : plugin.foods().values()) {
            if (!def.material.isEdible()) {
                problems.add("食物不可食用: " + def.materialName);
            }
        }

        // 10) 无限次数不递减
        ItemStack infinite = items.createBlockItem(Material.GLOWSTONE, ItemFactory.INFINITE, owner);
        if (items.decrement(infinite) != ItemFactory.INFINITE) {
            problems.add("无限次数被递减了");
        }

        // 11) 命令接线：控制台执行不应抛异常（非玩家分支应给出提示而不是崩溃）
        String[] probes = {"mb version", "mb list", "mb stats", "mb get STONE 1", "mb retrieve world 1 2 3",
                "mb food", "mb unknown-subcommand"};
        org.bukkit.command.CommandSender console = plugin.getServer().getConsoleSender();
        for (String line : probes) {
            try {
                plugin.getServer().dispatchCommand(console, line);
            } catch (Throwable throwable) {
                problems.add("命令 '" + line + "' 抛异常: " + throwable.getClass().getSimpleName());
            }
        }
        return problems;
    }
}
