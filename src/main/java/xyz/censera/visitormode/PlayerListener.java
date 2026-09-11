package xyz.censera.visitormode;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class PlayerListener implements Listener {
    private final VisitorMode plugin;

    PlayerListener(VisitorMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("eyes.bypass")) {
            plugin.enterVisitor(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthenticated().remove(player.getUniqueId());
        plugin.getAuth().cancelTotp(player.getUniqueId());
        plugin.getRegistry().remove(player.getUniqueId());
    }
}
