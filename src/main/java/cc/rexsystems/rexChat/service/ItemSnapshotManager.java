package cc.rexsystems.rexChat.service;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item snapshots with unique IDs.
 * Each [item] in chat gets a unique ID so multiple items can be previewed.
 * Items expire after a set time to prevent memory leaks.
 */
public class ItemSnapshotManager {
    private static final long EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    private final Map<String, SnapshotEntry> snapshots = new ConcurrentHashMap<>();

    /**
     * Store an item and return unique ID for retrieval.
     */
    public String storeItem(ItemStack item, String playerName) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        snapshots.put(id, new SnapshotEntry(item.clone(), playerName, System.currentTimeMillis()));
        cleanupExpired();
        return id;
    }

    /**
     * Get an item by its unique ID.
     */
    public ItemStack getItem(String id) {
        SnapshotEntry entry = snapshots.get(id);
        if (entry == null)
            return null;
        if (System.currentTimeMillis() - entry.timestamp > EXPIRY_MS) {
            snapshots.remove(id);
            return null;
        }
        return entry.item.clone();
    }

    /**
     * Get the player name who stored the item.
     */
    public String getPlayerName(String id) {
        SnapshotEntry entry = snapshots.get(id);
        return entry != null ? entry.playerName : null;
    }

    /**
     * Clean up expired entries.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        snapshots.entrySet().removeIf(e -> now - e.getValue().timestamp > EXPIRY_MS);
    }

    /**
     * Clear all snapshots (on plugin disable).
     */
    public void clear() {
        snapshots.clear();
    }

    private static class SnapshotEntry {
        final ItemStack item;
        final String playerName;
        final long timestamp;

        SnapshotEntry(ItemStack item, String playerName, long timestamp) {
            this.item = item;
            this.playerName = playerName;
            this.timestamp = timestamp;
        }
    }
}
