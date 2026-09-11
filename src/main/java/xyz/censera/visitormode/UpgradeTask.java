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

        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            tryUpgrade(player);
        }
    }

    /**
     * Upgrades player out of Visitor Mode if they're both authenticated and whitelisted.
     * Called from the periodic tick, and also called directly right after a successful
     * login or registration so a player who is already whitelisted doesn't sit in
     * Visitor Mode waiting for the next tick.
     */
    void tryUpgrade(Player player) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getRegistry().contains(uuid)) return;
        if (!player.isWhitelisted() || !plugin.getAuthenticated().contains(uuid)) return;

        PluginConfig config = plugin.getPluginConfig();
        plugin.exitVisitor(player);

        String broadcast = config.getBroadcastOnUpgrade();
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                    '&', broadcast.replace("%player%", player.getName())));
        }

        plugin.getLogger().info(player.getName()
                + " is trusted and authenticated; upgraded from Visitor Mode.");
    }
}
