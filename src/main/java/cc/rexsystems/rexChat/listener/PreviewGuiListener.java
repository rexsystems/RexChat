package cc.rexsystems.rexChat.listener;

import cc.rexsystems.rexChat.service.PreviewGuiService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Prevents interaction in preview GUIs to keep them read-only.
 */
public class PreviewGuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null &&
                (PreviewGuiService.isPreviewInventory(event.getView().getTopInventory()) ||
                        event.getView().getTopInventory().getHolder() instanceof PreviewGuiHolder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (PreviewGuiService.isPreviewInventory(event.getView().getTopInventory()) ||
                event.getView().getTopInventory().getHolder() instanceof PreviewGuiHolder) {
            event.setCancelled(true);
        }
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