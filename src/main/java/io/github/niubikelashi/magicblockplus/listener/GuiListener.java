package io.github.niubikelashi.magicblockplus.listener;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import io.github.niubikelashi.magicblockplus.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI 只读保护：任何点击/拖拽/移动都不允许改动 GUI 内容，
 * 所以 GUI 里的展示物品不可能被搬进背包或容器（原版会，导致玩家拿到「带真实记录的展示物品」并反复取回）。
 */
public class GuiListener implements Listener {

    private final MagicBlockPlusPlugin plugin;

    public GuiListener(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        GuiManager.Session session = plugin.gui().sessionOf(event.getInventory());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() != event.getInventory()) {
            // 玩家点的是自己下面的背包：已经取消，不再处理
            return;
        }
        boolean rightClick = event.isRightClick();
        plugin.gui().handleClick(player, session, event.getSlot(), rightClick);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (plugin.gui().sessionOf(event.getInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        plugin.gui().closeSession(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(InventoryMoveItemEvent event) {
        if (plugin.gui().sessionOf(event.getSource()) != null || plugin.gui().sessionOf(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }
}
