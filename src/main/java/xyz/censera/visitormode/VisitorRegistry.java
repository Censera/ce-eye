package xyz.censera.visitormode;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class VisitorRegistry {
    private final Set<UUID> visitors = new HashSet<>();

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
