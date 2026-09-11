package xyz.censera.visitormode;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class VisitorRegistry {
    private final Map<UUID, Location> anchors = new HashMap<>();

    void add(UUID uuid, Location anchor) {
        anchors.put(uuid, anchor.clone());
    }

    void remove(UUID uuid) {
        anchors.remove(uuid);
    }

    boolean contains(UUID uuid) {
        return anchors.containsKey(uuid);
    }

    Location anchor(UUID uuid) {
        Location anchor = anchors.get(uuid);
        return anchor == null ? null : anchor.clone();
    }

    Set<UUID> snapshot() {
        return Set.copyOf(anchors.keySet());
    }
}
