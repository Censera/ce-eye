package xyz.censera.visitormode;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class AuthManager {
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final long RATE_LIMIT_BASE_MS = 2_000L;
    private static final long RATE_LIMIT_MAX_MS = 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final VisitorMode plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Set<UUID> registrationsInProgress = new HashSet<>();
    private final Map<UUID, String> pendingTotp = new HashMap<>();
    private final Map<UUID, FailureState> failures = new HashMap<>();

    AuthManager(VisitorMode plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "accounts.yml");
        migrateLegacyAccounts();
        data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void migrateLegacyAccounts() {
        if (file.exists()) return;

        File oldFile = new File(plugin.getDataFolder().getParentFile(), "GuestMode/accounts.yml");
        if (!oldFile.isFile()) return;

        try {
            plugin.getDataFolder().mkdirs();
            Files.copy(oldFile.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Migrated accounts.yml from the previous GuestMode data folder.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not migrate GuestMode accounts.yml: " + e.getMessage());
        }
    }

    private void load() {
        for (String raw : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                String hash = data.getString(raw + ".password");
                String salt = data.getString(raw + ".salt");
                if (hash != null && salt != null) {
                    accounts.put(uuid, new Account(hash, salt,
                            data.getString(raw + ".totp"), data.getBoolean(raw + ".totp-enabled")));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid account entry: " + raw);
            }
        }
    }

    boolean isRegistered(UUID uuid) {
        return accounts.containsKey(uuid);
    }

    boolean isAuthenticated(UUID uuid) {
        return plugin.getAuthenticated().contains(uuid);
    }

    void register(Player player, String password, Consumer<String> result) {
        UUID uuid = player.getUniqueId();
        if (isRegistered(uuid) || !registrationsInProgress.add(uuid)) {
            result.accept("already-registered");
            return;
        }

        hashAsync(password, hash -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            registrationsInProgress.remove(uuid);
            if (hash == null) {
                result.accept("hash-failed");
                return;
            }
            if (isRegistered(uuid)) {
                result.accept("already-registered");
                return;
            }

            String[] parts = hash.split("\\$", 2);
            Account account = new Account(parts[1], parts[0], null, false);
            try {
                write(uuid, account);
                accounts.put(uuid, account);
                plugin.getAuthenticated().add(uuid);
                failures.remove(uuid);
                result.accept("ok");
            } catch (IllegalStateException e) {
                result.accept("storage-failed");
            }
        }));
    }

    void login(Player player, String password, String code, Consumer<String> result) {
        UUID uuid = player.getUniqueId();
        Account account = accounts.get(uuid);
        if (account == null) {
            result.accept("not-registered");
            return;
        }

        long remaining = rateLimitRemaining(uuid);
        if (remaining > 0) {
            result.accept("rate-limited:" + ((remaining + 999) / 1000));
            return;
        }

        verifyPasswordAsync(password, account.salt, account.password, valid ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!valid) {
                        recordFailure(uuid);
                        result.accept("invalid-password");
                        return;
                    }
                    if (account.totpEnabled && !verifyTotp(account.totp, code)) {
                        recordFailure(uuid);
                        result.accept(code == null || code.isBlank() ? "2fa-required" : "invalid-2fa");
                        return;
                    }
                    failures.remove(uuid);
                    plugin.getAuthenticated().add(uuid);
                    result.accept("ok");
                }));
    }

    String beginTotp(Player player) {
        UUID uuid = player.getUniqueId();
        Account account = accounts.get(uuid);
        if (account == null || !isAuthenticated(uuid) || account.totpEnabled) {
            return null;
        }
        String secret = randomBase32(20);
        pendingTotp.put(uuid, secret);
        return secret;
    }

    boolean confirmTotp(Player player, String code) {
        UUID uuid = player.getUniqueId();
        Account account = accounts.get(uuid);
        String secret = pendingTotp.get(uuid);
        if (account == null || secret == null || account.totpEnabled || !isAuthenticated(uuid)) {
            return false;
        }
        if (!verifyTotp(secret, code)) {
            return false;
        }

        account.totp = secret;
        account.totpEnabled = true;
        write(uuid, account);
        pendingTotp.remove(uuid);
        return true;
    }

    void cancelTotp(UUID uuid) {
        pendingTotp.remove(uuid);
    }

    boolean disableTotp(Player player, String code) {
        UUID uuid = player.getUniqueId();
        Account account = accounts.get(uuid);
        if (account == null || !account.totpEnabled || !isAuthenticated(uuid)) return false;
        if (!verifyTotp(account.totp, code)) return false;
        account.totp = null;
        account.totpEnabled = false;
        write(uuid, account);
        return true;
    }

    private void recordFailure(UUID uuid) {
        FailureState state = failures.computeIfAbsent(uuid, ignored -> new FailureState());
        state.attempts = Math.min(state.attempts + 1, 31);
        long delay = RATE_LIMIT_BASE_MS << Math.min(state.attempts - 1, 5);
        state.retryAt = System.currentTimeMillis() + Math.min(delay, RATE_LIMIT_MAX_MS);
    }

    private long rateLimitRemaining(UUID uuid) {
        FailureState state = failures.get(uuid);
        if (state == null) return 0;

        long remaining = state.retryAt - System.currentTimeMillis();
        if (remaining <= 0) {
            failures.remove(uuid);
            return 0;
        }
        return remaining;
    }

    private void write(UUID uuid, Account account) {
        String path = uuid.toString();
        data.set(path + ".password", account.password);
        data.set(path + ".salt", account.salt);
        data.set(path + ".totp", account.totp);
        data.set(path + ".totp-enabled", account.totpEnabled);
        save();
    }

    private void save() {
        File temp = new File(file.getParentFile(), "accounts.yml.tmp");
        try {
            file.getParentFile().mkdirs();
            data.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Could not save accounts.yml", e);
        }
    }

    private static void hashAsync(String password, Consumer<String> callback) {
        Thread.startVirtualThread(() -> {
            try {
                byte[] salt = new byte[16];
                RANDOM.nextBytes(salt);
                byte[] hash = derive(password, salt);
                callback.accept(Base64.getEncoder().encodeToString(salt) + "$"
                        + Base64.getEncoder().encodeToString(hash));
            } catch (GeneralSecurityException e) {
                callback.accept(null);
            }
        });
    }

    private static void verifyPasswordAsync(String password, String salt, String expected, Consumer<Boolean> callback) {
        Thread.startVirtualThread(() -> {
            try {
                byte[] actual = derive(password, Base64.getDecoder().decode(salt));
                byte[] wanted = Base64.getDecoder().decode(expected);
                callback.accept(MessageDigest.isEqual(actual, wanted));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                callback.accept(false);
            }
        });
    }

    private static byte[] derive(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String randomBase32(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base32(value);
    }

    private static boolean verifyTotp(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) return false;
        long counter = System.currentTimeMillis() / 30_000L;
        for (long offset = -1; offset <= 1; offset++) {
            if (totp(secret, counter + offset).equals(code)) return true;
        }
        return false;
    }

    private static String totp(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] message = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int index = hash[hash.length - 1] & 0x0f;
            int value = ((hash[index] & 0x7f) << 24)
                    | ((hash[index + 1] & 0xff) << 16)
                    | ((hash[index + 2] & 0xff) << 8)
                    | (hash[index + 3] & 0xff);
            return "%06d".formatted(value % 1_000_000);
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    private static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(BASE32.charAt((buffer >>> bits) & 31));
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] base32Decode(String value) {
        byte[] out = new byte[value.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : value.toUpperCase().toCharArray()) {
            int digit = BASE32.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[index++] = (byte) ((buffer >>> bits) & 0xff);
            }
        }
        return out;
    }

    String totpUri(Player player, String secret) {
        return "otpauth://totp/Eyes:" + player.getName()
                + "?secret=" + secret + "&issuer=Eyes&algorithm=SHA1&digits=6&period=30";
    }

    private static final class Account {
        String password;
        String salt;
        String totp;
        boolean totpEnabled;

        Account(String password, String salt, String totp, boolean totpEnabled) {
            this.password = password;
            this.salt = salt;
            this.totp = totp;
            this.totpEnabled = totpEnabled;
        }
    }

    private static final class FailureState {
        int attempts;
        long retryAt;
    }
}
