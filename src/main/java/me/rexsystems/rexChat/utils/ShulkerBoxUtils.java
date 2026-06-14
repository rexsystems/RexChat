package me.rexsystems.rexChat.utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Helpers for reading shulker box contents from item stacks.
 */
public final class ShulkerBoxUtils {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection()
            .toBuilder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ShulkerBoxUtils() {
    }

    public static boolean isShulkerBox(ItemStack item) {
        return item != null && isShulkerBox(item.getType());
    }

    public static boolean isShulkerBox(Material material) {
        return material != null && material != Material.AIR && Tag.SHULKER_BOXES.isTagged(material);
    }

    public static ItemStack[] getContents(ItemStack shulker) {
        if (!isShulkerBox(shulker)) {
            return new ItemStack[0];
        }

        ItemMeta meta = shulker.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return new ItemStack[0];
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox box)) {
            return new ItemStack[0];
        }

        ItemStack[] contents = box.getInventory().getContents();
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return cloned;
    }

    public static String getDisplayTitle(ItemStack shulker) {
        if (!isShulkerBox(shulker)) {
            return "Shulker Box";
        }

        ItemMeta meta = shulker.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ColorUtils.translateLegacyColors(meta.getDisplayName());
        }

        return LEGACY.serialize(shulker.displayName());
    }
}
