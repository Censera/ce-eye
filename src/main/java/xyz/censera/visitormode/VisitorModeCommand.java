package xyz.censera.visitormode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class VisitorModeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "kick-visitors");

    private final VisitorMode plugin;

    VisitorModeCommand(VisitorMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eyec.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "kick-visitors" -> handleKickVisitors(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(ChatColor.GREEN + "Censera's Eye config reloaded.");
        plugin.getLogger().info(sender.getName() + " reloaded Censera's Eye config");
    }

    private void handleList(CommandSender sender) {
        Set<UUID> visitors = plugin.getRegistry().snapshot();

        if (visitors.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No visitors are currently online");
            return;
        }

        List<String> names = new ArrayList<>(visitors.size());
        for (UUID uuid : visitors) {
            Player player = Bukkit.getPlayer(uuid);
            names.add(player != null ? player.getName() : "(offline:" + uuid + ")");
        }

        sender.sendMessage(ChatColor.GOLD + "Online visitors (" + visitors.size() + "): "
                + ChatColor.WHITE + String.join(", ", names));
    }

    private void handleKickVisitors(CommandSender sender) {
        Set<UUID> visitors = plugin.getRegistry().snapshot();

        if (visitors.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No visitors to kick");
            return;
        }

        int kicked = 0;
        for (UUID uuid : visitors) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.kickPlayer(ChatColor.RED + "You have been removed from the server");
                kicked++;
            }
            plugin.getRegistry().remove(uuid);
        }

        sender.sendMessage(ChatColor.GREEN + "Kicked " + kicked + " visitor(s)");
        plugin.getLogger().info(sender.getName() + " kicked " + kicked + " visitor(s)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("eyec.admin") || args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(partial)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Censera's Eye administration:");
        sender.sendMessage(ChatColor.YELLOW + "  /eyec reload" + ChatColor.GRAY + " to reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "  /eyec list" + ChatColor.GRAY + " to list online visitors");
        sender.sendMessage(ChatColor.YELLOW + "  /eyec kick-visitors" + ChatColor.GRAY + " to kick all online visitors");
        sender.sendMessage(ChatColor.GOLD + "Visitor utilities:");
        sender.sendMessage(ChatColor.YELLOW + "  /guest unstuck" + ChatColor.GRAY + " is teleport to bed spawn or world spawn");
        sender.sendMessage(ChatColor.YELLOW + "  /guest nudge" + ChatColor.GRAY + " is teleport 10 blocks upward (30s cooldown)");
        sender.sendMessage(ChatColor.GOLD + "Authentication:");
        sender.sendMessage(ChatColor.YELLOW + "  /register <password>" + ChatColor.GRAY + " to register an offline account");
        sender.sendMessage(ChatColor.YELLOW + "  /login <password> [2fa-code]" + ChatColor.GRAY + " to log in");
        sender.sendMessage(ChatColor.YELLOW + "  /2fa <enable|confirm|disable> [code]" + ChatColor.GRAY + " to manage 2FA");
    }
}
