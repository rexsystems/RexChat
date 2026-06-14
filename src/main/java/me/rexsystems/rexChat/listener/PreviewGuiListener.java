package me.rexsystems.rexChat.listener;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.service.PreviewGuiService;
import me.rexsystems.rexChat.utils.ContainerPreviewUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents interaction in preview GUIs to keep them read-only.
 * Shulker boxes inside previews can be clicked to inspect their contents.
 */
public class PreviewGuiListener implements Listener {

    private final RexChat plugin;

    public PreviewGuiListener(RexChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isPreviewInventory(top)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() != top) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (!ContainerPreviewUtils.isContainer(clicked)) {
            return;
        }

        plugin.getPreviewGuiService().openContainerPreview(player, clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isPreviewInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isPreviewInventory(Inventory inventory) {
        return inventory != null && (PreviewGuiService.isPreviewInventory(inventory)
                || inventory.getHolder() instanceof PreviewGuiHolder);
    }

    /**
     * Holder for preview GUIs created by viewitem command.
     */
    public static class PreviewGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
