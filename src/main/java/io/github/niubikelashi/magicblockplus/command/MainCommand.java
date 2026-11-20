package io.github.niubikelashi.magicblockplus.command;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.core.BlockRecord;
import io.github.niubikelashi.magicblockplus.core.FoodDef;
import io.github.niubikelashi.magicblockplus.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "gui", "search", "list", "get", "give", "food", "retrieve",
            "settimes", "addtimes", "stats", "reload", "version");

    private final MagicBlockPlusPlugin plugin;

    public MainCommand(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;
            case "gui":
                if (!requirePlayer(sender, "magicblock.gui")) {
                    return true;
                }
                plugin.gui().openSelection((Player) sender, 0, "");
                return true;
            case "search":
                if (!requirePlayer(sender, "magicblock.gui")) {
                    return true;
                }
                String keyword = args.length > 1 ? args[1] : "";
                plugin.gui().openSelection((Player) sender, 0, keyword);
                return true;
            case "list":
                if (!requirePlayer(sender, "magicblock.list")) {
                    return true;
                }
                plugin.gui().openFavorites((Player) sender);
                return true;
            case "retrieve":
                return handleRetrieve(sender, args);
            case "get":
                return handleGet(sender, args);
            case "give":
                return handleGive(sender, args);
            case "food":
                return handleFood(sender, args);
            case "settimes":
                return handleSetTimes(sender, args, false);
            case "addtimes":
                return handleSetTimes(sender, args, true);
            case "stats":
                return handleStats(sender);
            case "reload":
                return handleReload(sender);
            case "version":
                plugin.text().send(sender, "version", Text.ph("version", plugin.getDescription().getVersion(),
                        "api", plugin.getDescription().getAPIVersion()));
                return true;
            default:
                plugin.text().send(sender, "unknown-command", Text.ph("label", sub));
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        plugin.text().send(sender, "help");
        plugin.text().send(sender, "help-commands");
    }

    private boolean requirePlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            plugin.text().send(sender, "player-required");
            return false;
        }
        if (!sender.hasPermission(permission)) {
            plugin.text().send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private boolean handleRetrieve(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.text().send(sender, "player-required");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("magicblock.retrieve")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        if (args.length < 5) {
            plugin.text().send(sender, "usage");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.text().send(sender, "retrieve-failed", Text.ph("reason", "世界不存在"));
            return true;
        }
        int x = parseInt(sender, args[2]);
        int y = parseInt(sender, args[3]);
        int z = parseInt(sender, args[4]);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            plugin.text().send(sender, "usage");
            return true;
        }
        BlockRecord record = plugin.store().get(world.getName(), x, y, z);
        if (record == null) {
            plugin.text().send(sender, "retrieve-failed", Text.ph("reason", "该坐标没有魔法方块"));
            return true;
        }
        UUID owner = record.ownerUuid();
        if (owner != null && !owner.equals(player.getUniqueId()) && !player.hasPermission("magicblock.admin")) {
            plugin.text().send(sender, "not-bound-to-you", Text.ph("owner", plugin.offlineName(owner)));
            return true;
        }
        plugin.gui().retrieve(player, record);
        return true;
    }

    private boolean handleGet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.text().send(sender, "player-required");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("magicblock.get")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.text().send(sender, "usage");
            return true;
        }
        Material material = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            plugin.text().send(sender, "unknown-material", Text.ph("material", args[1]));
            return true;
        }
        int uses = args.length > 2 ? parseInt(sender, args[2]) : plugin.defaultUses(material);
        if (uses == Integer.MIN_VALUE) {
            plugin.text().send(sender, "usage");
            return true;
        }
        ItemStack item = plugin.items().createBlockItem(material, uses,
                plugin.settings().enableBinding ? player.getUniqueId() : null);
        plugin.text().send(sender, "got-with-count", Text.ph("count", String.valueOf(item.getAmount()),
                "remaining", String.valueOf(plugin.items().uses(item))));
        giveTo(player, item);
        plugin.stats().give();
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("magicblock.give")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.text().send(sender, "player-not-found", Text.ph("player", args[1]));
            return true;
        }
        Material material = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            plugin.text().send(sender, "unknown-material", Text.ph("material", args[2]));
            return true;
        }
        int uses = args.length > 3 ? parseInt(sender, args[3]) : plugin.defaultUses(material);
        if (uses == Integer.MIN_VALUE) {
            plugin.text().send(sender, "usage");
            return true;
        }
        giveTo(target, plugin.items().createBlockItem(material, uses,
                plugin.settings().enableBinding ? target.getUniqueId() : null));
        plugin.text().send(sender, "gave-to-player", Text.ph("player", target.getName(),
                "remaining", String.valueOf(plugin.defaultUses(material))));
        plugin.stats().give();
        return true;
    }

    private boolean handleFood(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.text().send(sender, "player-required");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("magicblock.get")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.text().send(sender, "usage");
            return true;
        }
        String key = args[1].toUpperCase(Locale.ROOT);
        FoodDef def = plugin.foods().get(key);
        if (def == null) {
            plugin.text().send(sender, "unknown-food", Text.ph("food", args[1]));
            return true;
        }
        int uses = args.length > 2 ? parseInt(sender, args[2]) : def.uses;
        if (uses == Integer.MIN_VALUE) {
            plugin.text().send(sender, "usage");
            return true;
        }
        giveTo(player, plugin.items().createFoodItem(def.material, uses, def.name, def.lore));
        plugin.text().send(sender, "got-with-count", Text.ph("count", "1", "remaining", String.valueOf(uses)));
        return true;
    }

    private boolean handleSetTimes(CommandSender sender, String[] args, boolean additive) {
        if (!sender.hasPermission("magicblock.settimes")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        if (args.length < 5) {
            plugin.text().send(sender, "usage");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.text().send(sender, "retrieve-failed", Text.ph("reason", "世界不存在"));
            return true;
        }
        int x = parseInt(sender, args[2]);
        int y = parseInt(sender, args[3]);
        int z = parseInt(sender, args[4]);
        int value = args.length > 5 ? parseInt(sender, args[5]) : 0;
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE || value == Integer.MIN_VALUE) {
            plugin.text().send(sender, "usage");
            return true;
        }
        BlockRecord record = plugin.store().get(world.getName(), x, y, z);
        if (record == null) {
            plugin.text().send(sender, "retrieve-failed", Text.ph("reason", "该坐标没有魔法方块"));
            return true;
        }
        int target = additive ? Math.max(0, record.uses + value) : value;
        plugin.store().setUses(world.getName(), x, y, z, target);
        plugin.text().send(sender, "set-times", Text.ph("count", String.valueOf(target)));
        return true;
    }

    private boolean handleStats(CommandSender sender) {
        if (!sender.hasPermission("magicblock.stats")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : plugin.stats().snapshot().entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
        }
        plugin.text().send(sender, "stats", Text.ph("stats", sb.toString().trim()));
        plugin.text().send(sender, "block-count", Text.ph("count", String.valueOf(plugin.store().total())));
        plugin.text().send(sender, "material-count", Text.ph("total", String.valueOf(plugin.gui().materialCount())));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("magicblock.reload")) {
            plugin.text().send(sender, "no-permission");
            return true;
        }
        int materials = plugin.reloadAll();
        plugin.text().send(sender, "reloaded", Text.ph("count", String.valueOf(materials)));
        plugin.store().markDirty();
        return true;
    }

    private void giveTo(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItem(player.getLocation().add(0, 1, 0), rest);
        }
    }

    private int parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            plugin.debug("无法解析数字: " + raw);
            return Integer.MIN_VALUE;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("give")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(online.getName());
                    }
                }
                return out;
            }
            if (sub.equals("get") || sub.equals("search")) {
                return materialSuggestions(prefix);
            }
            if (sub.equals("food")) {
                for (String key : plugin.foods().keySet()) {
                    if (key.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(key);
                    }
                }
                return out;
            }
            if (sub.equals("retrieve") || sub.equals("settimes") || sub.equals("addtimes")) {
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(world.getName());
                    }
                }
                return out;
            }
        }
        if (args.length == 3 && (args[0].toLowerCase(Locale.ROOT).equals("get")
                || args[0].toLowerCase(Locale.ROOT).equals("give"))) {
            return materialSuggestions(args[2].toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * 材料补全：英文枚举名前缀、中文名包含都算命中（最多 30 条）。
     * 只给目录里的建筑方块，不会把箱子/小麦/水之类的补出来。
     */
    private List<String> materialSuggestions(String prefix) {
        List<String> out = new ArrayList<>();
        for (Material material : plugin.catalog().materials()) {
            String name = material.name().toLowerCase(Locale.ROOT);
            String chinese = plugin.items().plainName(material);
            boolean hit = prefix.isEmpty() || name.startsWith(prefix)
                    || (chinese != null && chinese.toLowerCase(Locale.ROOT).contains(prefix));
            if (!hit) {
                continue;
            }
            out.add(material.name());
            if (out.size() >= 30) {
                return out;
            }
        }
        return out;
    }
}
