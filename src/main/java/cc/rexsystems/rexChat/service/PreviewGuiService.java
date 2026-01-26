package cc.rexsystems.rexChat.service;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.utils.ColorUtils;
import cc.rexsystems.rexChat.utils.MessageUtils;
import cc.rexsystems.rexChat.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and opens safe, read-only preview GUIs for player inventories and held
 * items.
 */
public class PreviewGuiService {
    private final RexChat plugin;

    public PreviewGuiService(RexChat plugin) {
        this.plugin = plugin;
    }

    public void openInventoryPreview(Player viewer, Player target) {
        if (viewer == null || target == null)
            return;
        String title = plugin.getConfigManager().getConfig().getString(
                "messages.preview.inventory.title",
                "§6Inventory: §f{player}");
        title = title.replace("{player}", target.getName());
        // Process color codes in title
        title = ColorUtils.translateLegacyColors(title);

        Inventory inv = Bukkit
                .createInventory(new PreviewHolder(PreviewType.INVENTORY, target.getUniqueId().toString()), 54, title);

        // CRITICAL: Use snapshot if available, otherwise use current inventory
        cc.rexsystems.rexChat.service.InventorySnapshotService.InventorySnapshot snapshot = plugin
                .getInventorySnapshotService().getSnapshot(target);

        if (snapshot != null) {
            // Use snapshot
            ItemStack[] storage = snapshot.getStorage();
            for (int i = 0; i < Math.min(storage.length, 36); i++) {
                ItemStack it = cloneSafe(storage[i]);
                inv.setItem(i, it);
            }

            ItemStack[] armor = snapshot.getArmor();
            inv.setItem(45, labeled(cloneSafe(armor[0]), "§7Helmet"));
            inv.setItem(46, labeled(cloneSafe(armor[1]), "§7Chestplate"));
            inv.setItem(47, labeled(cloneSafe(armor[2]), "§7Leggings"));
            inv.setItem(48, labeled(cloneSafe(armor[3]), "§7Boots"));
            inv.setItem(49, labeled(cloneSafe(snapshot.getOffhand()), "§7Offhand"));
        } else {
            // Fallback to current inventory
            PlayerInventory pinv = target.getInventory();

            // Storage contents (0-35) copied into 0-35
            ItemStack[] storage = pinv.getStorageContents();
            for (int i = 0; i < Math.min(storage.length, 36); i++) {
                ItemStack it = cloneSafe(storage[i]);
                inv.setItem(i, it);
            }

            // Armor & offhand in top row (positions 45-49)
            inv.setItem(45, labeled(cloneSafe(pinv.getHelmet()), "§7Helmet"));
            inv.setItem(46, labeled(cloneSafe(pinv.getChestplate()), "§7Chestplate"));
            inv.setItem(47, labeled(cloneSafe(pinv.getLeggings()), "§7Leggings"));
            inv.setItem(48, labeled(cloneSafe(pinv.getBoots()), "§7Boots"));
            inv.setItem(49, labeled(cloneSafe(pinv.getItemInOffHand()), "§7Offhand"));
        }

        // Fill separators with glass panes for clarity
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 36; i < 45; i++)
            inv.setItem(i, filler);

        SchedulerUtils.runForPlayer(plugin, viewer, () -> viewer.openInventory(inv));
    }

    public void openItemPreview(Player viewer, Player target) {
        if (viewer == null || target == null)
            return;

        // CRITICAL: Use saved snapshot if available
        ItemStack hand = plugin.getInventorySnapshotService().getItemSnapshot(target);
        if (hand == null) {
            // Fallback to current item if no snapshot
            hand = cloneSafe(target.getInventory().getItemInMainHand());
        } else {
            hand = cloneSafe(hand); // Clone the snapshot
        }

        if (isEmpty(hand)) {
            String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
            String msg = plugin.getConfigManager().getConfig().getString(
                    "messages.preview.item.none",
                    "%rc_prefix%&7You are not holding any item.");
            msg = msg.replace("%rc_prefix%", prefix);
            MessageUtils.sendMessage(viewer, msg);
            return;
        }

        String title = plugin.getConfigManager().getConfig().getString(
                "messages.preview.item.title",
                "§6Item: §f{player}");
        title = title.replace("{player}", target.getName());
        // Process color codes in title
        title = ColorUtils.translateLegacyColors(title);
        Inventory inv = Bukkit.createInventory(new PreviewHolder(PreviewType.ITEM, target.getUniqueId().toString()), 9,
                title);

        // Center item at slot 4
        inv.setItem(4, hand);

        // Add filler for borders
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < 9; i++)
            if (i != 4)
                inv.setItem(i, filler);

        SchedulerUtils.runForPlayer(plugin, viewer, () -> viewer.openInventory(inv));
    }

    private ItemStack cloneSafe(ItemStack in) {
        if (in == null)
            return null;
        return in.clone();
    }

    private boolean isEmpty(ItemStack it) {
        return it == null || it.getType() == Material.AIR || it.getAmount() <= 0;
    }

    private ItemStack labeled(ItemStack it, String name) {
        if (isEmpty(it))
            return null;
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            try {
                m.setDisplayName(name);
                it.setItemMeta(m);
            } catch (Throwable ignored) {
            }
        }
        return it;
    }

    public static boolean isPreviewInventory(Inventory inv) {
        if (inv == null)
            return false;
        InventoryHolder holder = inv.getHolder();
        return holder instanceof PreviewHolder;
    }

    private enum PreviewType {
        INVENTORY, ITEM
    }

    private static class PreviewHolder implements InventoryHolder {
        private final PreviewType type;
        private final String targetId;

        private PreviewHolder(PreviewType type, String targetId) {
            this.type = type;
            this.targetId = targetId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}