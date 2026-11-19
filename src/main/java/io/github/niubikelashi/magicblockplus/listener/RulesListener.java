package io.github.niubikelashi.magicblockplus.listener;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.core.BlockRecord;
import io.github.niubikelashi.magicblockplus.core.ItemFactory;
import io.github.niubikelashi.magicblockplus.core.Settings;
import io.github.niubikelashi.magicblockplus.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.UUID;

/**
 * 方块规则：放置计数、破坏返还、防摧毁、防活塞、防爆炸。
 *
 * 相对原版的关键修复：
 * - 破坏时不再「什么都不掉落」：按记录里的剩余次数掉落一个魔法方块，
 *   记录删除与掉落是一次性的（原版破坏后方块直接消失且拿不回物品）。
 * - 放置的方块会带着「递减后的剩余次数」被记录，总量守恒，
 *   不会像原版那样破坏后按默认次数重新生成 → 无限刷方块。
 * - 多方块放置（沙子/砂砾一次性放多个）只把主方块记为魔法方块，
 *   其它副本保持普通方块，避免一次消耗换来多个带次数的方块。
 * - 爆炸/活塞/水流/生长/燃烧/物理更新不再悄悄摧毁魔法方块。
 */
public class RulesListener implements Listener {

    private final MagicBlockPlusPlugin plugin;

    public RulesListener(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 放置 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        handlePlace(event, event.getBlockPlaced(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        // 一次性放多个方块时只追踪主方块
        handlePlace(event, event.getBlockPlaced(), true);
    }

    private void handlePlace(BlockPlaceEvent event, Block block, boolean multiPlace) {
        ItemStack item = event.getItemInHand();
        if (plugin.items().kind(item) != ItemFactory.KIND_BLOCK) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Settings settings = plugin.settings();
        Player player = event.getPlayer();

        if (!player.hasPermission("magicblock.use")) {
            event.setCancelled(true);
            plugin.text().send(player, "no-permission-use");
            return;
        }
        if (settings.isWorldBlacklisted(block.getWorld().getName())) {
            event.setCancelled(true);
            plugin.text().send(player, "world-blacklisted");
            return;
        }

        int uses = plugin.items().uses(item);
        if (uses == 0) {
            event.setCancelled(true);
            plugin.stats().depleted();
            plugin.text().send(player, "depleted");
            applyDepleted(player, item);
            return;
        }

        UUID owner = plugin.items().owner(item);
        if (settings.enableBinding) {
            if (owner == null) {
                plugin.items().setOwner(item, player.getUniqueId());
                plugin.cacheName(player.getUniqueId(), player.getName());
                owner = player.getUniqueId();
                plugin.text().send(player, "bound-to-you");
            } else if (!owner.equals(player.getUniqueId()) && !settings.allowUseBoundBlocks) {
                event.setCancelled(true);
                plugin.text().send(player, "not-bound-to-you", Text.ph("owner", plugin.offlineName(owner)));
                return;
            }
        }

        int remaining = plugin.items().decrement(item);
        BlockRecord record = new BlockRecord(block.getLocation(), item.getType(),
                plugin.items().blockId(item), owner == null ? null : owner.toString(),
                remaining, plugin.items().maxUses(item), false);
        plugin.store().register(record);

        plugin.stats().place();
        plugin.logUsage(player, "place " + item.getType().name() + " @ " + record.key() + " 剩余 " + remaining);
        if (multiPlace) {
            plugin.debug("多方块放置：只追踪主方块 " + record.key());
        }
    }

    private void applyDepleted(Player player, ItemStack item) {
        if (plugin.settings().depletedAction.equals("delete")) {
            player.getInventory().remove(item);
        } else {
            ItemStack plain = plugin.items().toPlain(item);
            player.getInventory().setItemInMainHand(plain);
        }
    }

    // ---------- 破坏 ----------

    /**
     * MONITOR：其他插件已经做完判断，这里做最终决定。
     * 如果别的插件已经取消了破坏事件，我们不动记录（方块仍是魔法方块）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockRecord record = plugin.store().get(block.getLocation());
        if (record == null) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("magicblock.break")) {
            event.setCancelled(true);
            plugin.text().send(player, "no-permission-break");
            return;
        }
        UUID owner = record.ownerUuid();
        if (owner != null && !owner.equals(player.getUniqueId())
                && !plugin.settings().allowBreakBoundBlocks
                && !player.hasPermission("magicblock.breakprotected")) {
            event.setCancelled(true);
            plugin.text().send(player, "not-bound-to-you", Text.ph("owner", plugin.offlineName(owner)));
            return;
        }

        // 一次性消费记录：先删记录，再按剩余次数掉落，避免重复掉落
        BlockRecord removed = plugin.store().remove(block.getLocation());
        if (removed == null) {
            return;
        }
        plugin.stats().aBreak();

        if (plugin.settings().returnItemOnBreak) {
            event.setDropItems(false);
            ItemStack drop = plugin.items().createBlockItem(removed.materialEnum(), removed.uses,
                    removed.ownerUuid());
            Location dropAt = block.getLocation().add(0.5, 0.1, 0.5);
            block.getWorld().dropItem(dropAt, drop);
        }
        plugin.logUsage(player, "break " + removed.key() + " 返还次数 " + removed.uses);
    }

    // ---------- 防摧毁 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent event) {
        String world = event.getBlock().getWorld().getName();
        if (!plugin.store().worldHasMagicBlocks(world)) {
            plugin.stats().physicsSkipped();
            return;
        }
        if (plugin.settings().protectFromPhysics && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFromTo(BlockFromToEvent event) {
        if (plugin.settings().protectFromFluid && plugin.store().contains(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrow(BlockGrowEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForm(BlockFormEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityForm(EntityBlockFormEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpread(BlockSpreadEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFade(BlockFadeEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (plugin.settings().protectFromGrowth && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoisture(MoistureChangeEvent event) {
        if (plugin.settings().protectFromPhysics && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.settings().protectFromBurn && plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntPrime(org.bukkit.event.block.TNTPrimeEvent event) {
        if (plugin.store().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ---------- 活塞 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event, event.getBlocks());
    }

    private void handlePiston(org.bukkit.event.block.BlockPistonEvent event, java.util.List<Block> moved) {
        if (plugin.settings().pistonBehavior != "cancel") {
            // forget：活塞可以推走它，但记录会被清掉，避免位置错位后身份对不上
            for (Block block : moved) {
                plugin.store().remove(block.getLocation());
            }
            return;
        }
        for (Block block : moved) {
            if (plugin.store().contains(block.getLocation())) {
                event.setCancelled(true);
                plugin.stats().pistonBlocked();
                plugin.debug("阻止活塞推动魔法方块 " + block.getLocation().getBlockX() + ","
                        + block.getLocation().getBlockY() + "," + block.getLocation().getBlockZ());
                return;
            }
        }
    }

    // ---------- 爆炸 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList(), event.getBlock().getLocation(), plugin.settings().dropOnExplosion);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList(), event.getLocation(), plugin.settings().dropOnExplosion);
    }

    private void handleExplosion(java.util.List<Block> blocks, Location origin, boolean dropItems) {
        boolean touched = false;
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            BlockRecord record = plugin.store().get(block.getLocation());
            if (record == null) {
                continue;
            }
            touched = true;
            if (dropItems) {
                // 方块会被炸掉：返还带剩余次数的物品并删除记录（只返还一次，方块留在爆炸列表里）
                plugin.store().remove(block.getLocation());
                ItemStack drop = plugin.items().createBlockItem(record.materialEnum(), record.uses, record.ownerUuid());
                block.getWorld().dropItem(block.getLocation().add(0.5, 0.1, 0.5), drop);
            } else {
                // 不掉落：把魔法方块从爆炸列表里摘掉，保住方块与次数
                it.remove();
            }
        }
        if (touched) {
            plugin.stats().explosion();
            plugin.debug("爆炸处理于 " + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
        }
    }

    // ---------- 发射器 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDispense(BlockDispenseEvent event) {
        if (plugin.items().isMagic(event.getItem())) {
            // 发射器放出的魔法方块没有事件与记录，会「放出去就变普通方块」→ 直接禁止
            event.setCancelled(true);
            plugin.debug("禁止发射器发放魔法物品");
        }
    }

    // ---------- 工具：判断某方块是否为魔法方块（给 GUI/命令用） ----------

    public boolean isMagicBlock(Block block) {
        return plugin.store().contains(block.getLocation());
    }

    public Material magicBlockMaterial(Block block) {
        BlockRecord record = plugin.store().get(block.getLocation());
        return record == null ? Material.AIR : record.materialEnum();
    }
}
