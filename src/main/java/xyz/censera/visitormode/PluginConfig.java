package xyz.censera.visitormode;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

final class PluginConfig {
    private final String visitorJoinMessage;
    private final String upgradeMessage;
    private final String broadcastOnUpgrade;
    private final GameMode upgradeGameMode;
    private final String twoFactorWebHost;
    private final int twoFactorWebPort;
    private final int twoFactorWebExpirySeconds;

    PluginConfig(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        visitorJoinMessage = requiredString(cfg, "visitor-join-message");
        upgradeMessage = requiredString(cfg, "upgrade-message");
        broadcastOnUpgrade = cfg.getString("broadcast-on-upgrade", "");
        upgradeGameMode = parseGameMode(cfg, "upgrade-gamemode", GameMode.SURVIVAL, GameMode.CREATIVE);
        twoFactorWebHost = requiredString(cfg, "two-factor-web-host");
        twoFactorWebPort = cfg.getInt("two-factor-web-port", 0);
        twoFactorWebExpirySeconds = cfg.getInt("two-factor-web-expiry-seconds", 300);

        if (twoFactorWebPort < 0 || twoFactorWebPort > 65535) {
            throw new IllegalArgumentException("Invalid two-factor-web-port: " + twoFactorWebPort);
        }
        if (twoFactorWebExpirySeconds <= 0) {
            throw new IllegalArgumentException("Invalid two-factor-web-expiry-seconds: " + twoFactorWebExpirySeconds);
        }
    }

    private static String requiredString(FileConfiguration cfg, String key) {
        String value = cfg.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or empty required configuration: " + key);
        }
        return value;
    }

    private static GameMode parseGameMode(FileConfiguration cfg, String key, GameMode firstAllowed, GameMode secondAllowed) {
        String raw = requiredString(cfg, key);
        GameMode mode;
        try {
            mode = GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid game mode for " + key + ": " + raw, e);
        }
        if (mode != firstAllowed && mode != secondAllowed) {
            throw new IllegalArgumentException("Invalid game mode for " + key + ": " + raw);
        }
        return mode;
    }

    String getVisitorJoinMessage() { return visitorJoinMessage; }
    String getUpgradeMessage() { return upgradeMessage; }
    String getBroadcastOnUpgrade() { return broadcastOnUpgrade; }
    GameMode getUpgradeGameMode() { return upgradeGameMode; }
    String getTwoFactorWebHost() { return twoFactorWebHost; }
    int getTwoFactorWebPort() { return twoFactorWebPort; }
    int getTwoFactorWebExpirySeconds() { return twoFactorWebExpirySeconds; }
}
