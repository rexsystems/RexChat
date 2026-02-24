package cc.rexsystems.rexChat.service;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages inventory and item snapshots for players when they use [inventory] or
 * [item] tokens in chat.
 * Snapshots are preserved until the player uses the token again.
 */
public class InventorySnapshotService {
    private final RexChat plugin;
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> itemSnapshots = new ConcurrentHashMap<>();

    // ID-based storage (for unique IDs per [inventory] usage)
    private final Map<String, InventorySnapshot> snapshotsById = new ConcurrentHashMap<>();
    private final Map<String, String> playerNameById = new ConcurrentHashMap<>();
    private static final long EXPIRY_MS = 30 * 60 * 1000; // 30 minutes
    private final Map<String, Long> timestampsById = new ConcurrentHashMap<>();

    public InventorySnapshotService(RexChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Store inventory snapshot with unique ID.
     * 
     * @return unique ID for retrieval
     */
    public String storeSnapshotWithId(Player player) {
        if (player == null)
            return null;

        String id = UUID.randomUUID().toString().substring(0, 8);

        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        ItemStack[] storageContents = inv.getStorageContents();
        for (int i = 0; i < Math.min(storageContents.length, 36); i++) {
            storage[i] = storageContents[i] != null ? storageContents[i].clone() : null;
        }

        armor[0] = inv.getHelmet() != null ? inv.getHelmet().clone() : null;
        armor[1] = inv.getChestplate() != null ? inv.getChestplate().clone() : null;
        armor[2] = inv.getLeggings() != null ? inv.getLeggings().clone() : null;
        armor[3] = inv.getBoots() != null ? inv.getBoots().clone() : null;
        offhand = inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null;

        InventorySnapshot snapshot = new InventorySnapshot(storage, armor, offhand);
        snapshotsById.put(id, snapshot);
        playerNameById.put(id, player.getName());
        timestampsById.put(id, System.currentTimeMillis());

        cleanupExpired();
        return id;
    }

    /**
     * Get inventory snapshot by unique ID.
     */
    public InventorySnapshot getSnapshotById(String id) {
        if (id == null)
            return null;
        Long timestamp = timestampsById.get(id);
        if (timestamp == null || System.currentTimeMillis() - timestamp > EXPIRY_MS) {
            snapshotsById.remove(id);
            playerNameById.remove(id);
            timestampsById.remove(id);
            return null;
        }
        return snapshotsById.get(id);
    }

    /**
     * Get player name by snapshot ID.
     */
    public String getPlayerNameById(String id) {
        return playerNameById.get(id);
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        timestampsById.entrySet().removeIf(e -> {
            if (now - e.getValue() > EXPIRY_MS) {
                snapshotsById.remove(e.getKey());
                playerNameById.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Creates a snapshot of the player's current inventory.
     * This should be called when the player uses [inventory] in chat.
     */
    public void createSnapshot(Player player) {
        if (player == null)
            return;

        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        // Clone storage contents (0-35)
        ItemStack[] storageContents = inv.getStorageContents();
        for (int i = 0; i < Math.min(storageContents.length, 36); i++) {
            storage[i] = storageContents[i] != null ? storageContents[i].clone() : null;
        }

        // Clone armor
        armor[0] = inv.getHelmet() != null ? inv.getHelmet().clone() : null;
        armor[1] = inv.getChestplate() != null ? inv.getChestplate().clone() : null;
        armor[2] = inv.getLeggings() != null ? inv.getLeggings().clone() : null;
        armor[3] = inv.getBoots() != null ? inv.getBoots().clone() : null;

        // Clone offhand
        offhand = inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null;

        snapshots.put(player.getUniqueId(), new InventorySnapshot(storage, armor, offhand));
    }

    /**
     * Creates a snapshot of the player's main hand item.
     * This should be called when the player uses [item] in chat.
     */
    public void createItemSnapshot(Player player) {
        if (player == null)
            return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        itemSnapshots.put(player.getUniqueId(), hand != null ? hand.clone() : null);
    }

    /**
     * Gets the saved snapshot for a player, or null if no snapshot exists.
     */
    public InventorySnapshot getSnapshot(Player player) {
        if (player == null)
            return null;
        return snapshots.get(player.getUniqueId());
    }

    /**
     * Gets the saved item snapshot for a player, or null if no snapshot exists.
     */
    public ItemStack getItemSnapshot(Player player) {
        if (player == null)
            return null;
        return itemSnapshots.get(player.getUniqueId());
    }

    /**
     * Removes the snapshot for a player (e.g., on logout).
     */
    public void removeSnapshot(Player player) {
        if (player == null)
            return;
        snapshots.remove(player.getUniqueId());
        itemSnapshots.remove(player.getUniqueId());
    }

    /**
     * Clears all snapshots (e.g., on plugin disable).
     */
    public void clearAll() {
        snapshots.clear();
        itemSnapshots.clear();
    }

    /**
     * Represents a snapshot of a player's inventory at a specific point in time.
     */
    public static class InventorySnapshot {
        private final ItemStack[] storage; // 36 slots (0-35)
        private final ItemStack[] armor; // 4 slots (helmet, chestplate, leggings, boots)
        private final ItemStack offhand;

        public InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
        }

        public ItemStack[] getStorage() {
            return storage;
        }

        public ItemStack[] getArmor() {
            return armor;
        }

        public ItemStack getOffhand() {
            return offhand;
        }
    }
}
