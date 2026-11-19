package io.github.niubikelashi.magicblockplus.listener;

import io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** 记下 UUID→名字，离线玩家的归属提示才不会显示一串 UUID（原版靠 OfflinePlayer 名字，常常是 null）。 */
public class NameCacheListener implements Listener {

    private final MagicBlockPlusPlugin plugin;

    public NameCacheListener(MagicBlockPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.cacheName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
