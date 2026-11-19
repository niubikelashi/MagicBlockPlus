package io.github.niubikelashi.magicblockplus.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息工具：把 config.yml 里的 messages.&lt;language&gt;.&lt;key&gt; 转成带前缀的彩色文本。
 */
public class Text {

    private final Map<String, String> messages;
    private final String prefix;
    private final String language;

    public Text(Map<String, String> messages, String prefix, String language) {
        this.messages = new LinkedHashMap<>(messages);
        this.prefix = color(prefix);
        this.language = language;
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public String message(String key, Map<String, String> placeholders) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = key;
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String name = entry.getKey();
                String wrapped = "%" + name + "%";
                if (raw.contains(wrapped)) {
                    raw = raw.replace(wrapped, entry.getValue());
                } else {
                    raw = raw.replace(name, entry.getValue());
                }
            }
        }
        return prefix + color(raw);
    }

    public String message(String key) {
        return message(key, null);
    }

    public String prefix() {
        return prefix;
    }

    public String language() {
        return language;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(message(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(message(key, placeholders));
    }

    /** 方便写法：text.ph("player", name).ph("uses", 5) */
    public static Map<String, String> ph(String k1, Object v1) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, String.valueOf(v1));
        return map;
    }

    public static Map<String, String> ph(String k1, Object v1, String k2, Object v2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, String.valueOf(v1));
        map.put(k2, String.valueOf(v2));
        return map;
    }

    public static Map<String, String> ph(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, String.valueOf(v1));
        map.put(k2, String.valueOf(v2));
        map.put(k3, String.valueOf(v3));
        return map;
    }

    public static Map<String, String> ph(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                                         String k4, Object v4) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, String.valueOf(v1));
        map.put(k2, String.valueOf(v2));
        map.put(k3, String.valueOf(v3));
        map.put(k4, String.valueOf(v4));
        return map;
    }
}
