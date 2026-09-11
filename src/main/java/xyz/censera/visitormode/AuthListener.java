package xyz.censera.visitormode;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.UUID;

final class AuthListener implements Listener {
    private final VisitorMode plugin;

    AuthListener(VisitorMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("eyes.bypass") || plugin.isFloodgatePlayer(uuid)) {
            plugin.getAuthenticated().add(uuid);
            return;
        }

        plugin.enterVisitor(player);
        player.sendMessage(ChatColor.YELLOW + "Please log in with /login <password> or register with /register <password>.");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.getAuthenticated().contains(uuid)) {
                return;
            }

            if (plugin.isPremiumPlayer(uuid)) {
                plugin.getAuthenticated().add(uuid);
                player.sendMessage(ChatColor.GREEN + "Premium account authenticated.");
            } else if (plugin.getAuth().isRegistered(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "Please log in with /login <password> [2fa-code].");
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAuthenticated().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAuthenticated().contains(player.getUniqueId())) {
            return;
        }

        String command = event.getMessage().toLowerCase(Locale.ROOT);
        if (!command.startsWith("/login ") && !command.equals("/login")
                && !command.startsWith("/register ") && !command.equals("/register")
                && !command.startsWith("/guest ") && !command.equals("/guest")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You must authenticate first.");
        }
    }
}
