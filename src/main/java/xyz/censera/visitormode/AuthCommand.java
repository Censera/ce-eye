package xyz.censera.visitormode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

final class AuthCommand implements CommandExecutor {
    private final VisitorMode plugin;

    AuthCommand(VisitorMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        return switch (command.getName().toLowerCase()) {
            case "register" -> register(player, args);
            case "login" -> login(player, args);
            case "2fa" -> twoFactor(player, args);
            default -> false;
        };
    }

    private boolean register(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /register <password>");
            return true;
        }
        if (args[0].length() < 8) {
            player.sendMessage(ChatColor.RED + "Password must be at least 8 characters.");
            return true;
        }
        plugin.getAuth().register(player, args[0], result -> {
            switch (result) {
                case "ok" -> {
                    player.sendMessage(ChatColor.GREEN + "Registered and logged in.");
                    plugin.tryUpgrade(player);
                }
                case "already-registered" -> player.sendMessage(ChatColor.RED + "You are already registered.");
                case "rate-limited" -> player.sendMessage(ChatColor.RED + "Please wait before trying again.");
                default -> player.sendMessage(ChatColor.RED + "Registration failed.");
            }
        });
        return true;
    }

    private boolean login(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /login <password> [2FA code]");
            return true;
        }
        plugin.getAuth().login(player, args[0], args.length == 2 ? args[1] : null, result -> {
            if (result.startsWith("rate-limited:")) {
                player.sendMessage(ChatColor.RED + "Please wait " + result.substring("rate-limited:".length()) + " seconds before trying again.");
                return;
            }
            switch (result) {
                case "ok" -> {
                    player.sendMessage(ChatColor.GREEN + "Logged in.");
                    plugin.tryUpgrade(player);
                }
                case "not-registered" -> player.sendMessage(ChatColor.RED + "You are not registered. Use /register <password>.");
                case "2fa-required" -> player.sendMessage(ChatColor.RED + "Your account requires a 2FA code.");
                case "invalid-2fa" -> player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
                default -> player.sendMessage(ChatColor.RED + "Invalid password.");
            }
        });
        return true;
    }

    private boolean twoFactor(Player player, String[] args) {
        if (!plugin.getAuth().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be logged in.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("enable")) {
            String secret = plugin.getAuth().beginTotp(player);
            if (secret == null) {
                player.sendMessage(ChatColor.RED + "2FA is already enabled or setup is already in progress.");
                return true;
            }

            String url;
            try {
                url = plugin.startTwoFactorSetup(player, secret);
            } catch (IOException e) {
                plugin.getAuth().cancelTotp(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Could not start the 2FA setup page.");
                plugin.getLogger().warning("Could not start 2FA setup page for " + player.getUniqueId() + ": " + e.getMessage());
                return true;
            }

            Component open = Component.text("[Open 2FA setup page]", NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text("Open the temporary setup page")));
            Component copy = Component.text("[Copy setup key]", NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.copyToClipboard(secret))
                    .hoverEvent(HoverEvent.showText(Component.text("Copy the setup key")));

            player.sendMessage(Component.text().append(open).append(Component.text("  ")).append(copy).build());
            player.sendMessage(ChatColor.YELLOW + "Add the account with your authenticator, then use /2fa confirm <code>.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            if (plugin.getAuth().confirmTotp(player, args[1])) {
                player.sendMessage(ChatColor.GREEN + "2FA enabled.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("disable")) {
            if (plugin.getAuth().disableTotp(player, args[1])) {
                player.sendMessage(ChatColor.GREEN + "2FA disabled.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
            }
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /2fa <enable|confirm|disable> [code]");
        return true;
    }
}
