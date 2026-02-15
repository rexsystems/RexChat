package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Creates item display components
 * Uses Adventure Components directly, not String manipulation.
 */
public class ItemDisplayBuilder {
    private final RexChat plugin;

    public ItemDisplayBuilder(RexChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a complete item display component with hover and click events.
     * Uses config template for label formatting.
     */
    public Component createItemDisplay(ItemStack item, Player player, String itemId) {
        if (item == null || item.getType() == Material.AIR) {
            return Component.text("[Air]", NamedTextColor.GRAY);
        }

        int amount = item.getAmount();

        // Get item display name as Component - PRESERVES HEX COLORS!
        Component itemName = item.displayName();

        // Paper returns [Item Name] with brackets - we need to strip them
        String miniSerialized = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .builder()
                .hexColors() // IMPORTANT: Preserve hex colors!
                .useUnusualXRepeatedCharacterHexFormat() // Use &#RRGGBB format
                .build()
                .serialize(itemName);

        // Strip brackets from serialized version
        if (miniSerialized.startsWith("[") || miniSerialized.matches("^§[0-9a-fk-or]\\[.*")) {
            miniSerialized = miniSerialized.replaceFirst("^(§[0-9a-fk-orx]|&#[0-9a-fA-F]{6})*\\[", "");
        }
        if (miniSerialized.endsWith("]") || miniSerialized.matches(".*§[0-9a-fk-or]\\]$")) {
            miniSerialized = miniSerialized.replaceFirst("(§[0-9a-fk-orx]|&#[0-9a-fA-F]{6})*\\]$", "");
        }

        // Get label template from config
        String labelTemplate = plugin.getConfigManager().getConfig()
                .getString("messages.preview.item.label-template", "&7[&f{label}&7]");

        // Build label with item name and amount
        String itemLabel;
        if (amount == 1) {
            itemLabel = miniSerialized;
        } else {
            itemLabel = miniSerialized + " &bx" + amount;
        }

        // Replace {label} placeholder in template
        String finalLabel = labelTemplate.replace("{label}", itemLabel);

        // Parse the final label with colors from config
        Component display = ColorUtils.parseComponent(finalLabel);

        // Create NATIVE item hover event (shows actual item tooltip!)
        HoverEvent<HoverEvent.ShowItem> itemHover = createItemHoverEvent(item);
        display = display.hoverEvent(itemHover);

        // Add click event to open preview GUI
        if (itemId != null) {
            display = display.clickEvent(ClickEvent.runCommand("/rexchat viewitem " + itemId));
        }

        return display;
    }

    /**
     * Get the display name of an item as a Component.
     * Paper API provides this directly via ItemStack.displayName()
     */
    private Component getItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Component.translatable("block.minecraft.air");
        }

        try {
            // Paper API - returns the item's display name as Component
            Component displayName = item.displayName();

            // Paper's displayName() returns something like [Item Name]
            // We want just the name, so we need to handle this
            // Actually on 1.21.4 it should return the actual styled name

            return displayName;
        } catch (Throwable t) {
            // Fallback - create translatable component
            String key = item.getType().isBlock()
                    ? "block.minecraft." + item.getType().getKey().getKey()
                    : "item.minecraft." + item.getType().getKey().getKey();
            return Component.translatable(key);
        }
    }

    /**
     * Create a native HoverEvent.showItem() that shows the actual item tooltip.
     */
    private HoverEvent<HoverEvent.ShowItem> createItemHoverEvent(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return HoverEvent.showItem(Key.key("minecraft:air"), 1);
        }

        try {
            // For modern versions (1.20.5+), we use asHoverEvent() which Paper provides
            // This automatically creates the proper ShowItem with all NBT/DataComponents
            return item.asHoverEvent();
        } catch (Throwable t) {
            // Fallback - basic item key without NBT
            Key itemKey = Key.key(item.getType().getKey().toString());
            return HoverEvent.showItem(itemKey, item.getAmount());
        }
    }

    /**
     * Create inventory display component.
     */
    public Component createInventoryDisplay(Player player, String invId) {
        String label = plugin.getConfigManager().getConfig().getString(
                "messages.preview.inventory.label", "[Inventory]");
        String hover = plugin.getConfigManager().getConfig().getString(
                "messages.preview.inventory.hover", "Click to view inventory");

        Component display = Component.text(label, NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(hover.replace("{player}", player.getName()))))
                .clickEvent(ClickEvent.runCommand("/rexchat viewinv " + invId));

        return display;
    }
}
