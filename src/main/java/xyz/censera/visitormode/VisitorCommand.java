package xyz.censera.visitormode;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class VisitorCommand implements CommandExecutor {
    private static final long NUDGE_COOLDOWN_MS = 30_000L;
    private final VisitorMode plugin;
    private final Map<UUID, Long> nudgeCooldowns = new HashMap<>();

    VisitorCommand(VisitorMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!plugin.getRegistry().contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "This command is only available in Visitor Mode.");
            return true;
        }

        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unstuck" -> unstuck(player);
            case "nudge" -> nudge(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void unstuck(Player player) {
        plugin.moveVisitorToSafeLocation(player);
        player.sendMessage(ChatColor.GREEN + "Teleported to a safe location.");
    }

    private void nudge(Player player) {
        long now = System.currentTimeMillis();
        long last = nudgeCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = NUDGE_COOLDOWN_MS - (now - last);
        if (remaining > 0) {
            player.sendMessage(ChatColor.YELLOW + "Nudge is on cooldown for "
                    + ((remaining + 999) / 1000) + " seconds.");
            return;
        }

        Location target = player.getLocation().clone().add(0, 10, 0);
        if (!plugin.isVisitorWorld(target.getWorld()) || !plugin.isWithinVisitorBoundary(target)) {
            player.sendMessage(ChatColor.RED + "You cannot move outside Visitor Mode's safe area.");
            return;
        }
        player.teleport(target);
        nudgeCooldowns.put(player.getUniqueId(), now);
        player.sendMessage(ChatColor.GREEN + "Nudged 10 blocks upward.");
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Visitor commands:");
        player.sendMessage(ChatColor.YELLOW + "  /guest unstuck" + ChatColor.GRAY + "  Return to a safe location.");
        player.sendMessage(ChatColor.YELLOW + "  /guest nudge" + ChatColor.GRAY + "  Teleport 10 blocks upward. 30 second cooldown.");
    }
}
