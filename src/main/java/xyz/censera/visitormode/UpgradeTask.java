package xyz.censera.visitormode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

final class UpgradeTask {
    private final VisitorMode plugin;
    private BukkitTask task;

    UpgradeTask(VisitorMode plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        VisitorRegistry registry = plugin.getRegistry();
        PluginConfig config = plugin.getPluginConfig();

        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            if (player.isWhitelisted() && plugin.getAuthenticated().contains(uuid)) {
                upgrade(player, config);
            }
        }
    }

    private void upgrade(Player player, PluginConfig config) {
        plugin.exitVisitor(player);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getUpgradeMessage()));

        String broadcast = config.getBroadcastOnUpgrade();
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                    '&', broadcast.replace("%player%", player.getName())));
        }

        plugin.getLogger().info(player.getName()
                + " is trusted and authenticated; upgraded from Visitor Mode.");
    }
}
