package me.rexsystems.rexChat.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores coordinate snapshots for clickable {@code [coords]} / {@code [here]} tokens.
 */
public class CoordsSnapshotManager {

    private static final long EXPIRY_MS = 30 * 60 * 1000L;

    public record CoordsSnapshot(String world, int x, int y, int z, String playerName, long timestamp) {
        public Location toLocation(org.bukkit.Server server) {
            if (server == null || world == null) {
                return null;
            }
            var bukkitWorld = server.getWorld(world);
            if (bukkitWorld == null) {
                return null;
            }
            return new Location(bukkitWorld, x + 0.5, y, z + 0.5);
        }
    }

    private final Map<String, CoordsSnapshot> snapshots = new ConcurrentHashMap<>();

    public String store(Player player) {
        Location loc = player.getLocation();
        String id = UUID.randomUUID().toString().substring(0, 8);
        snapshots.put(id, new CoordsSnapshot(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                player.getName(),
                System.currentTimeMillis()));
        cleanupExpired();
        return id;
    }

    public CoordsSnapshot get(String id) {
        CoordsSnapshot snapshot = snapshots.get(id);
        if (snapshot == null) {
            return null;
        }
        if (System.currentTimeMillis() - snapshot.timestamp() > EXPIRY_MS) {
            snapshots.remove(id);
            return null;
        }
        return snapshot;
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        snapshots.entrySet().removeIf(entry -> now - entry.getValue().timestamp() > EXPIRY_MS);
    }

    public void clear() {
        snapshots.clear();
    }
}
