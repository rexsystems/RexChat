package me.rexsystems.rexChat.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * @deprecated Use {@link ContainerPreviewUtils} instead.
 */
@Deprecated
public final class ShulkerBoxUtils {

    private ShulkerBoxUtils() {
    }

    public static boolean isShulkerBox(ItemStack item) {
        return ContainerPreviewUtils.isContainer(item)
                && item != null
                && item.getType() != Material.AIR
                && org.bukkit.Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    public static boolean isShulkerBox(Material material) {
        return material != null && material != Material.AIR
                && org.bukkit.Tag.SHULKER_BOXES.isTagged(material);
    }

    public static ItemStack[] getContents(ItemStack shulker) {
        ContainerPreviewUtils.ContainerView view = ContainerPreviewUtils.read(shulker);
        return view != null ? view.contents() : new ItemStack[0];
    }

    public static String getDisplayTitle(ItemStack shulker) {
        return ContainerPreviewUtils.getDisplayTitle(shulker);
    }
}
