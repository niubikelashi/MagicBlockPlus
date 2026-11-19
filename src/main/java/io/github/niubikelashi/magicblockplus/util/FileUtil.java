package io.github.niubikelashi.magicblockplus.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子写入：先写临时文件再改名替换。
 * 原版直接 FileWriter 覆盖目标文件，进程中断或并发写入会留下半截文件，
 * 下次启动解析失败 → 所有绑定/索引丢失（魔法方块「莫名其妙消失」的另一根因）。
 */
public final class FileUtil {

    private FileUtil() {
    }

    public static void writeAtomically(File target, String content) throws IOException {
        Path dir = target.getParentFile() == null ? Path.of(".") : target.getParentFile().toPath();
        Files.createDirectories(dir);
        Path temp = dir.resolve(target.getName() + ".tmp");
        Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void saveYamlAtomically(File target, YamlConfiguration yaml) throws IOException {
        writeAtomically(target, yaml.saveToString());
    }

    public static YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return yaml;
    }
}
