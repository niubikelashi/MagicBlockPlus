package io.github.niubikelashi.magicblockplus.listener;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.core.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 物品规则：魔法物品不能被合成/烧炼「洗」成普通物品或无限复制，
 * 吃魔法食物会递减次数（原版只对方块计数，食物次数从不消耗）。
 */
public class ItemRulesListener implements Listener {

    private final MagicBlockPlusPlugin plugin;

    public ItemRulesListener(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsMagic(event.getInventory())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        if (containsMagic(event.getInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                plugin.text().send((Player) event.getWhoClicked(), "no-permission-use");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCook(BlockCookEvent event) {
        if (plugin.items().isMagic(event.getSource())) {
            event.setCancelled(true);
            plugin.debug("禁止烧炼魔法物品");
        }
    }

    /**
     * 原版只拦 FurnaceSmeltEvent：魔法方块当燃料会被烧掉并产出普通产物（绕过次数、等于复制）。
     * BlastFurnaceBurnEvent / SmokerBurnEvent 都继承 FurnaceBurnEvent，因此这一个处理器覆盖三种熔炉。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(FurnaceBurnEvent event) {
        if (plugin.items().isMagic(event.getFuel())) {
            event.setCancelled(true);
            plugin.debug("禁止把魔法物品当燃料");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack consumed = event.getItem();
        if (plugin.items().kind(consumed) != ItemFactory.KIND_FOOD) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        ItemStack target = null;
        if (plugin.items().kind(main) == ItemFactory.KIND_FOOD && main.getType() == consumed.getType()) {
            target = main;
        } else if (plugin.items().kind(off) == ItemFactory.KIND_FOOD && off.getType() == consumed.getType()) {
            target = off;
        } else {
            target = consumed;
        }
        int remaining = plugin.items().decrement(target);
        plugin.stats().consume();
        plugin.logUsage(player, "eat " + consumed.getType().name() + " 剩余 " + remaining);
        if (remaining == 0) {
            plugin.stats().depleted();
            plugin.text().send(player, "depleted");
            if (plugin.settings().depletedAction.equals("delete")) {
                if (target == main) {
                    player.getInventory().setItemInMainHand(null);
                } else if (target == off) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    plugin.items().toPlain(target);
                }
            } else {
                ItemStack plain = plugin.items().toPlain(target);
                if (target == main) {
                    player.getInventory().setItemInMainHand(plain);
                } else if (target == off) {
                    player.getInventory().setItemInOffHand(plain);
                }
            }
        }
    }

    private boolean containsMagic(org.bukkit.inventory.Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && plugin.items().isMagic(item)) {
                return true;
            }
        }
        return false;
    }
}
