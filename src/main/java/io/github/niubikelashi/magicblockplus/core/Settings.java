package io.github.niubikelashi.magicblockplus.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置解析结果。解析时把非法值收敛成安全默认值，绝不抛异常（原版很多配置项
 * 缺失时直接 NPE，例如 display.show-info 少一个键就崩）。
 */
public class Settings {

    public String prefix = "&7[MagicBlockPlus] ";
    public String language = "zh_CN";
    public Map<String, String> translations = new LinkedHashMap<>();
    public Map<String, String> messages = new LinkedHashMap<>();

    public boolean debugMode = false;
    public int defaultUses = 50;
    public int maxUsesLimit = 100000;
    public boolean enableBinding = true;
    public boolean allowUseBoundBlocks = false;
    public boolean allowBreakBoundBlocks = false;
    public final Set<String> blacklistedWorlds = ConcurrentHashMap.newKeySet();
    public String depletedAction = "convert";
    public boolean returnItemOnBreak = true;
    public boolean dropOnExplosion = true;
    public String pistonBehavior = "cancel";
    public boolean protectFromFluid = true;
    public boolean protectFromGrowth = true;
    public boolean protectFromBurn = true;
    public boolean protectFromPhysics = true;

    public String nameFormat = "&b✦ %s ✦";
    public boolean showBoundPlayer = true;
    public boolean showUsageCount = true;
    public boolean showProgressBar = true;
    public boolean decorativeEnabled = false;
    public List<String> decorativeLines = new ArrayList<>();
    public int progressBarLength = 10;

    public String guiTitle = "&8⚡ &bMagicBlockPlus 选择";
    public int guiRows = 6;
    public final Map<String, Button> buttons = new LinkedHashMap<>();
    public List<String> materialNames = new ArrayList<>();
    public final Map<String, List<String>> groupMaterials = new LinkedHashMap<>();
    public final Map<String, Integer> materialUses = new LinkedHashMap<>();

    public int saveIntervalSeconds = 5;
    public boolean saveOnDisable = true;
    public boolean validateOnStartup = false;

    public boolean logUsage = false;
    public boolean enableStatistics = true;
    public boolean checkUpdates = false;
    public String updateOwner = "niubikelashi";
    public String updateRepo = "MagicBlockPlus";
    public boolean selfTest = false;

    public static class Button {
        public String material = "ARROW";
        public String name = "";
        public List<String> lore = new ArrayList<>();
        public int slot = 0;
        public String disabledMaterial = "GRAY_DYE";
        public String disabledName = "";
    }

    public static Settings from(FileConfiguration config) {
        Settings s = new Settings();
        s.prefix = config.getString("prefix", s.prefix);
        s.language = config.getString("language", s.language);

        ConfigurationSection translations = config.getConfigurationSection("custom-block-translations");
        if (translations != null) {
            for (String key : translations.getKeys(false)) {
                s.translations.put(key.toUpperCase(Locale.ROOT), translations.getString(key, ""));
            }
        }
        s.messages = readMessages(config, s.language);

        s.defaultUses = config.getInt("default-uses", s.defaultUses);
        s.maxUsesLimit = Math.max(1, config.getInt("max-uses-limit", s.maxUsesLimit));
        s.enableBinding = config.getBoolean("enable-binding-system", s.enableBinding);
        s.allowUseBoundBlocks = config.getBoolean("allow-use-bound-blocks", s.allowUseBoundBlocks);
        s.allowBreakBoundBlocks = config.getBoolean("allow-break-bound-blocks", s.allowBreakBoundBlocks);
        s.blacklistedWorlds.clear();
        for (String world : config.getStringList("blacklisted-worlds")) {
            s.blacklistedWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        s.depletedAction = config.getString("depleted-action", "convert").toLowerCase(Locale.ROOT);
        if (!s.depletedAction.equals("delete")) {
            s.depletedAction = "convert";
        }
        s.returnItemOnBreak = config.getBoolean("return-item-on-break", s.returnItemOnBreak);
        s.dropOnExplosion = config.getBoolean("drop-on-explosion", s.dropOnExplosion);
        s.pistonBehavior = config.getString("piston-behavior", "cancel").toLowerCase(Locale.ROOT);
        if (!s.pistonBehavior.equals("forget")) {
            s.pistonBehavior = "cancel";
        }
        s.protectFromFluid = config.getBoolean("protect-from-fluid", s.protectFromFluid);
        s.protectFromGrowth = config.getBoolean("protect-from-growth", s.protectFromGrowth);
        s.protectFromBurn = config.getBoolean("protect-from-burn", s.protectFromBurn);
        s.protectFromPhysics = config.getBoolean("protect-from-physics", s.protectFromPhysics);

        s.nameFormat = config.getString("display.block-name-format", s.nameFormat);
        s.showBoundPlayer = config.getBoolean("display.show-info.bound-player", s.showBoundPlayer);
        s.showUsageCount = config.getBoolean("display.show-info.usage-count", s.showUsageCount);
        s.showProgressBar = config.getBoolean("display.show-info.progress-bar", s.showProgressBar);
        s.decorativeEnabled = config.getBoolean("display.decorative-lore.enabled", false);
        s.decorativeLines = new ArrayList<>(config.getStringList("display.decorative-lore.lines"));
        s.progressBarLength = Math.max(1, Math.min(40, config.getInt("display.progress-bar-length", 10)));

        s.guiTitle = config.getString("gui.title", s.guiTitle);
        s.guiRows = Math.max(1, Math.min(6, config.getInt("gui.rows", 6)));
        s.buttons.clear();
        ConfigurationSection buttons = config.getConfigurationSection("gui.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection section = buttons.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Button button = new Button();
                button.material = section.getString("material", "ARROW");
                button.name = section.getString("name", "");
                button.lore = new ArrayList<>(section.getStringList("lore"));
                button.slot = Math.max(0, Math.min(s.guiRows * 9 - 1, section.getInt("slot", 0)));
                button.disabledMaterial = section.getString("disabled-material", "GRAY_DYE");
                button.disabledName = section.getString("disabled-name", "");
                s.buttons.put(key.toLowerCase(Locale.ROOT), button);
            }
        }
        s.materialNames = new ArrayList<>(config.getStringList("gui.materials"));
        s.groupMaterials.clear();
        ConfigurationSection groups = config.getConfigurationSection("gui.group");
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                ConfigurationSection section = groups.getConfigurationSection(group);
                if (section != null) {
                    s.groupMaterials.put(group.toLowerCase(Locale.ROOT), new ArrayList<>(section.getStringList("")));
                }
            }
        }
        ConfigurationSection usesOverride = config.getConfigurationSection("material-uses");
        if (usesOverride != null) {
            for (String key : usesOverride.getKeys(false)) {
                s.materialUses.put(key.toUpperCase(Locale.ROOT), usesOverride.getInt(key, s.defaultUses));
            }
        }

        s.saveIntervalSeconds = Math.max(1, config.getInt("storage.save-interval-seconds", 5));
        s.saveOnDisable = config.getBoolean("storage.save-on-disable", true);
        s.validateOnStartup = config.getBoolean("storage.validate-on-startup", false);

        s.logUsage = config.getBoolean("log-usage", false);
        s.enableStatistics = config.getBoolean("enable-statistics", true);
        s.checkUpdates = config.getBoolean("check-updates", false);
        s.updateOwner = config.getString("update-check-owner", "niubikelashi");
        s.updateRepo = config.getString("update-check-repo", "MagicBlockPlus");
        s.selfTest = config.getBoolean("self-test", false);
        return s;
    }

    private static Map<String, String> readMessages(FileConfiguration config, String language) {
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages." + language);
        if (section == null) {
            section = config.getConfigurationSection("messages.zh_CN");
        }
        if (section == null) {
            section = config.getConfigurationSection("messages");
        }
        if (section != null) {
            for (String key : section.getKeys(false)) {
                out.put(key, section.getString(key, key));
            }
        }
        return out;
    }

    public int usesFor(String materialName, int fallback) {
        Integer override = materialUses.get(materialName.toUpperCase(Locale.ROOT));
        return override == null ? fallback : override;
    }

    public boolean isWorldBlacklisted(String worldName) {
        return blacklistedWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
