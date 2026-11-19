package io.github.niubikelashi.magicblockplus.core;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * 世界里一个已放置魔法方块的记录：位置 + 材质 + 剩余次数 + 归属。
 *
 * 次数保存在记录里，破坏时会按这个剩余次数掉落物品，因此「放置 1 次消耗 1 次」
 * 与「破坏拿回剩余次数」总量守恒，不会凭空多出次数（原版破坏后按默认次数
 * 重新生成物品 → 无限刷方块）。
 */
public class BlockRecord {

    public final String world;
    public final int x;
    public final int y;
    public final int z;
    public final String material;
    public final String blockId;
    public final String owner;
    public int uses;
    public int maxUses;
    public boolean hidden;
    public long createdAt;

    public BlockRecord(Location location, Material material, String blockId, String owner,
                       int uses, int maxUses, boolean hidden) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.material = material.name();
        this.blockId = blockId;
        this.owner = owner;
        this.uses = uses;
        this.maxUses = maxUses;
        this.hidden = hidden;
        this.createdAt = System.currentTimeMillis();
    }

    public BlockRecord(String world, int x, int y, int z, String material, String blockId, String owner,
                       int uses, int maxUses, boolean hidden, long createdAt) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.blockId = blockId;
        this.owner = owner;
        this.uses = uses;
        this.maxUses = maxUses;
        this.hidden = hidden;
        this.createdAt = createdAt;
    }

    public String key() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public UUID ownerUuid() {
        if (owner == null || owner.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Material materialEnum() {
        Material matched = Material.matchMaterial(material);
        return matched == null ? Material.AIR : matched;
    }
}
