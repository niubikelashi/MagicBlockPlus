package io.github.niubikelashi.magicblockplus;

import io.github.niubikelashi.magicblockplus.core.BlockStore;
import io.github.niubikelashi.magicblockplus.core.FoodDef;
import io.github.niubikelashi.magicblockplus.core.ItemFactory;
import io.github.niubikelashi.magicblockplus.core.Settings;
import io.github.niubikelashi.magicblockplus.gui.GuiManager;
import io.github.niubikelashi.magicblockplus.listener.GuiListener;
import io.github.niubikelashi.magicblockplus.listener.ItemRulesListener;
import io.github.niubikelashi.magicblockplus.listener.NameCacheListener;
import io.github.niubikelashi.magicblockplus.listener.RulesListener;
import io.github.niubikelashi.magicblockplus.command.MainCommand;
import io.github.niubikelashi.magicblockplus.test.SelfTest;
import io.github.niubikelashi.magicblockplus.util.FileUtil;
import io.github.niubikelashi.magicblockplus.util.Stats;
import io.github.niubikelashi.magicblockplus.util.Text;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MagicBlockPlusPlugin extends JavaPlugin {

    private Settings settings;
    private ItemFactory items;
    private BlockStore store;
    private Text text;
    private Stats stats;
    private GuiManager gui;
    private final Map<String, FoodDef> foods = new LinkedHashMap<>();
    private final Map<String, String> nameCache = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());
        stats = new Stats();
        items = new ItemFactory(this);
        applyItemSettings();
        text = new Text(settings.messages, settings.prefix, settings.language);

        store = new BlockStore(this);
        store.load();
        if (settings.validateOnStartup) {
            store.validateAgainstWorlds();
        }

        gui = new GuiManager(this);
        gui.rebuildMaterials();
        loadFoods();

        getServer().getPluginManager().registerEvents(new RulesListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemRulesListener(this), this);
        getServer().getPluginManager().registerEvents(new NameCacheListener(this), this);

        PluginCommand command = getCommand("magicblock");
        if (command == null) {
            getLogger().severe("plugin.yml 里缺少 magicblock 命令，插件功能不可用");
            return;
        }
        MainCommand executor = new MainCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        startFlushTask();

        if (settings.checkUpdates) {
            checkForUpdates();
        }
        if (settings.selfTest) {
            runSelfTest();
        }

        getLogger().info("启用完成：可用材料 " + gui.materialCount() + " 个，已知魔法方块记录 "
                + store.total() + " 条，语言 " + settings.language
                + (store.skippedOnLoad() > 0 ? "（载入时跳过 " + store.skippedOnLoad() + " 条不支持的记录）" : ""));
    }

    @Override
    public void onDisable() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (settings != null && settings.saveOnDisable && store != null) {
            store.flush();
            getLogger().info("已保存 " + store.total() + " 条魔法方块记录");
        }
        if (gui != null) {
            gui.clearSessions();
        }
    }

    /**
     * 重新读取配置（/mb reload）。数据文件不受影响。
     * 相对原版的修复：所有运行时状态（物品样式、消息、食物表、材料表、存盘周期）都在这里重建，
     * 原版 reload 只换配置对象，新的食物监听/开关在 reload 后仍按旧值运行。
     */
    public int reloadAll() {
        reloadConfig();
        settings = Settings.from(getConfig());
        applyItemSettings();
        text = new Text(settings.messages, settings.prefix, settings.language);
        gui.rebuildMaterials();
        loadFoods();
        if (flushTask != null) {
            flushTask.cancel();
        }
        startFlushTask();
        return gui.materialCount();
    }

    private void applyItemSettings() {
        items.applySettings(settings.translations, settings.nameFormat, settings.showBoundPlayer,
                settings.showUsageCount, settings.showProgressBar, settings.decorativeEnabled,
                settings.decorativeLines, settings.progressBarLength, "&7绑定到: &b", "&7使用次数:");
    }

    private void startFlushTask() {
        long periodTicks = settings.saveIntervalSeconds * 20L;
        flushTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (store.shouldSave(settings.saveIntervalSeconds * 1000L)) {
                String snapshot = store.snapshot();
                getServer().getScheduler().runTaskAsynchronously(this, () -> store.writeSnapshot(snapshot));
            }
        }, periodTicks, periodTicks);
    }

    private void loadFoods() {
        foods.clear();
        File foodFile = new File(getDataFolder(), "food.yml");
        if (!foodFile.exists()) {
            try {
                FileUtil.writeAtomically(foodFile, resourceText("food.yml"));
            } catch (Exception ex) {
                error(ex, "无法写出 food.yml");
                return;
            }
        }
        YamlConfiguration yaml = FileUtil.loadYaml(foodFile);
        ConfigurationSection section = yaml.getConfigurationSection("foods");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null || !material.isEdible()) {
                debug("跳过不可食用的食物定义: " + key);
                continue;
            }
            foods.put(key.toUpperCase(Locale.ROOT), new FoodDef(key.toUpperCase(Locale.ROOT), material,
                    entry.getInt("uses", 5), entry.getString("name", ""),
                    new ArrayList<>(entry.getStringList("lore"))));
        }
        debug("加载了 " + foods.size() + " 条魔法食物定义");
    }

    /** Paper 的 getTextResource 返回 Reader；这里直接用 InputStream，跨 Paper/Spigot 都可用。 */
    private String resourceText(String name) {
        try (java.io.InputStream in = getResource(name)) {
            if (in == null) {
                return "foods: {}\n";
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            error(ex, "读取插件内置文件失败: " + name);
            return "foods: {}\n";
        }
    }

    public void checkForUpdates() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String url = "https://api.github.com/repos/" + settings.updateOwner + "/" + settings.updateRepo
                        + "/releases/latest";
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "MagicBlockPlus");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                byte[] body = conn.getInputStream().readAllBytes();
                String json = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                String tag = extractTag(json);
                String current = getDescription().getVersion();
                getServer().getScheduler().runTask(this, () -> {
                    if (tag == null) {
                        getLogger().info(text.message("update-check-failed", Text.ph("error", "没有 release")));
                    } else if (!tag.equals(current) && !tag.equals("v" + current)) {
                        getLogger().info(text.message("updated", Text.ph("version", tag, "current", current)));
                    } else {
                        getLogger().info(text.message("up-to-date", Text.ph("current", current)));
                    }
                });
            } catch (Exception ex) {
                error(ex, "更新检查失败");
                getServer().getScheduler().runTask(this,
                        () -> getLogger().info(text.message("update-check-failed", Text.ph("error", ex.getMessage()))));
            }
        });
    }

    private static String extractTag(String json) {
        int idx = json.indexOf("\"tag_name\"");
        if (idx < 0) {
            return null;
        }
        int start = json.indexOf('"', idx + 10) + 1;
        int end = json.indexOf('"', start);
        if (start <= 0 || end <= start) {
            return null;
        }
        return json.substring(start, end);
    }

    public void runSelfTest() {
        List<String> problems = new SelfTest(this).run();
        if (problems.isEmpty()) {
            getLogger().info(text.message("self-test-pass", Text.ph("details", "全部检查通过")));
        } else {
            getLogger().warning(text.message("self-test-fail", Text.ph("details", String.join("；", problems))));
        }
    }

    public void debug(String message) {
        if (settings != null && settings.debugMode) {
            getLogger().info("[debug] " + message);
        }
    }

    public void error(Exception ex, String context) {
        stats.error();
        getLogger().warning(context + ": " + ex.getMessage());
        if (settings != null && settings.debugMode) {
            getLogger().warning("stack: " + java.util.Arrays.toString(ex.getStackTrace()));
        }
    }

    public void logUsage(Player player, String what) {
        if (settings != null && settings.logUsage) {
            getLogger().info("usage: " + player.getName() + " -> " + what);
        }
    }

    public Settings settings() {
        return settings;
    }

    public ItemFactory items() {
        return items;
    }

    public BlockStore store() {
        return store;
    }

    public Text text() {
        return text;
    }

    public Stats stats() {
        return stats;
    }

    public GuiManager gui() {
        return gui;
    }

    public Map<String, FoodDef> foods() {
        return foods;
    }

    public int maxUsesLimit() {
        return settings == null ? 100000 : settings.maxUsesLimit;
    }

    public int defaultUses(Material material) {
        if (settings == null) {
            return 50;
        }
        return settings.usesFor(material.name(), settings.defaultUses);
    }

    public String offlineName(UUID uuid) {
        String cached = nameCache.get(uuid.toString());
        return cached == null ? uuid.toString() : cached;
    }

    public void cacheName(UUID uuid, String name) {
        nameCache.put(uuid.toString(), name);
    }

    public Plugin placeholderApi() {
        return getServer().getPluginManager().getPlugin("PlaceholderAPI");
    }

}
