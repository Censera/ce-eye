package xyz.censera.visitormode;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisitorMode extends JavaPlugin {
    private static final double MAX_DISTANCE_SQUARED = 200.0 * 200.0;

    private VisitorRegistry registry;
    private PluginConfig pluginConfig;
    private UpgradeTask upgradeTask;
    private AuthManager auth;
    private TwoFactorSetupServer twoFactorSetupServer;
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);

        registry = new VisitorRegistry();
        auth = new AuthManager(this);
        twoFactorSetupServer = new TwoFactorSetupServer(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new VisitorProtectionListener(this), this);

        upgradeTask = new UpgradeTask(this);
        upgradeTask.start();

        VisitorModeCommand executor = new VisitorModeCommand(this);
        requireCommand("eyec").setExecutor(executor);
        requireCommand("eyec").setTabCompleter(executor);

        AuthCommand authCommand = new AuthCommand(this);
        requireCommand("register").setExecutor(authCommand);
        requireCommand("login").setExecutor(authCommand);
        requireCommand("2fa").setExecutor(authCommand);

        VisitorCommand visitorCommand = new VisitorCommand(this);
        requireCommand("guest").setExecutor(visitorCommand);

        getLogger().info("Censera's Eye enabled.");
    }

    @Override
    public void onDisable() {
        if (upgradeTask != null) {
            upgradeTask.cancel();
            upgradeTask = null;
        }
        if (twoFactorSetupServer != null) {
            twoFactorSetupServer.stop();
            twoFactorSetupServer = null;
        }
        authenticated.clear();
        getLogger().info("Censera's Eye disabled.");
    }

    void enterVisitor(Player player) {
        UUID uuid = player.getUniqueId();
        if (registry.contains(uuid)) return;

        registry.add(uuid, player.getLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&', pluginConfig.getVisitorJoinMessage().replace("%player%", player.getName())));
    }

    void exitVisitor(Player player) {
        UUID uuid = player.getUniqueId();
        auth.cancelTotp(uuid);
        twoFactorSetupServer.stopFor(uuid);
        registry.remove(uuid);
        player.setGameMode(pluginConfig.getUpgradeGameMode());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', pluginConfig.getUpgradeMessage()));
    }

    void moveVisitorToSafeLocation(Player player) {
        UUID uuid = player.getUniqueId();
        Location anchor = registry.anchor(uuid);
        if (anchor == null) {
            throw new IllegalStateException("No visitor anchor is available for " + player.getName());
        }

        Location target = findSafeLocation(anchor);
        if (target == null) {
            throw new IllegalStateException("No safe visitor location is available for " + player.getName());
        }

        player.teleport(target);
    }

    private Location findSafeLocation(Location anchor) {
        if (isValidVisitorLocation(anchor, anchor)) return anchor;

        org.bukkit.World world = anchor.getWorld();
        if (world == null) return null;

        int baseX = anchor.getBlockX();
        int baseZ = anchor.getBlockZ();
        for (int radius = 1; radius <= 16; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    int blockX = baseX + x;
                    int blockZ = baseZ + z;
                    int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
                    Location candidate = new Location(world, blockX + 0.5, y, blockZ + 0.5);
                    if (isValidVisitorLocation(candidate, anchor)) return candidate;
                }
            }
        }
        return null;
    }

    private boolean isValidVisitorLocation(Location location, Location anchor) {
        return location != null
                && anchor != null
                && isWithinVisitorBoundary(anchor, location)
                && !isDangerous(location)
                && location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    boolean isWithinVisitorBoundary(Player player, Location location) {
        return isWithinVisitorBoundary(registry.anchor(player.getUniqueId()), location);
    }

    private boolean isWithinVisitorBoundary(Location anchor, Location location) {
        if (anchor == null || location == null || anchor.getWorld() != location.getWorld()) return false;
        double dx = location.getX() - anchor.getX();
        double dz = location.getZ() - anchor.getZ();
        return dx * dx + dz * dz <= MAX_DISTANCE_SQUARED;
    }

    private boolean isDangerous(Location location) {
        String type = location.getBlock().getType().toString();
        String above = location.clone().add(0, 1, 0).getBlock().getType().toString();
        return location.getBlock().isLiquid()
                || type.contains("FIRE")
                || type.contains("MAGMA")
                || type.contains("CAMPFIRE")
                || above.contains("FIRE")
                || location.getY() < location.getWorld().getMinHeight() + 1;
    }

    boolean isFloodgatePlayer(UUID uuid) {
        if (getServer().getPluginManager().getPlugin("floodgate") == null) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, uuid));
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("Floodgate integration failed; requiring normal authentication for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    boolean isPremiumPlayer(UUID uuid) {
        if (getServer().getPluginManager().getPlugin("FastLogin") == null) return false;
        try {
            Class<?> pluginClass = Class.forName("com.github.games647.fastlogin.bukkit.FastLoginBukkit");
            Object plugin = getServer().getPluginManager().getPlugin("FastLogin");
            if (plugin == null || !pluginClass.isInstance(plugin)) return false;
            Method getStatus = pluginClass.getMethod("getStatus", UUID.class);
            Object status = getStatus.invoke(plugin, uuid);
            return status != null && "PREMIUM".equals(status.toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("FastLogin integration failed; requiring normal authentication for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    String startTwoFactorSetup(Player player, String secret) throws java.io.IOException {
        return twoFactorSetupServer.start(new TwoFactorSetupServer.PlayerSetup(
                player.getUniqueId(), player.getName(), secret, auth.totpUri(player, secret)));
    }

    void tryUpgrade(Player player) {
        if (upgradeTask != null) {
            upgradeTask.tryUpgrade(player);
        }
    }

    void reload() {
        reloadConfig();
        pluginConfig = new PluginConfig(this);
        getLogger().info("Configuration reloaded.");
    }

    private org.bukkit.command.PluginCommand requireCommand(String name) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Required command '" + name + "' is missing from plugin.yml");
        return command;
    }

    VisitorRegistry getRegistry() { return registry; }
    PluginConfig getPluginConfig() { return pluginConfig; }
    AuthManager getAuth() { return auth; }
    Set<UUID> getAuthenticated() { return authenticated; }
}
