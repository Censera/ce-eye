package xyz.censera.visitormode;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VisitorRegistry {
    private final Set<UUID> visitors = ConcurrentHashMap.newKeySet();

    void add(UUID uuid) {
        visitors.add(uuid);
    }

    void remove(UUID uuid) {
        visitors.remove(uuid);
    }

    boolean contains(UUID uuid) {
        return visitors.contains(uuid);
    }

    Set<UUID> snapshot() {
        return Set.copyOf(visitors);
    }
}
