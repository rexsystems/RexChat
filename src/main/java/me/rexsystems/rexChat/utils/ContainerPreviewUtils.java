package me.rexsystems.rexChat.utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads stored contents from portable container items (shulker boxes, barrels,
 * bundles, decorated pots).
 */
public final class ContainerPreviewUtils {

    public enum ContainerKind {
        SHULKER(27),
        BARREL(27),
        BUNDLE(54),
        DECORATED_POT(9);

        private final int guiSize;

        ContainerKind(int guiSize) {
            this.guiSize = guiSize;
        }

        public int guiSize() {
            return guiSize;
        }
    }

    public record ContainerView(ContainerKind kind, ItemStack[] contents, String title, int guiSize,
            boolean centerSingleItem) {
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection()
            .toBuilder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ContainerPreviewUtils() {
    }

    public static boolean isContainer(ItemStack item) {
        return read(item) != null;
    }

    public static ContainerView read(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        Material type = item.getType();
        if (Tag.SHULKER_BOXES.isTagged(type)) {
            return readBlockInventory(item, ContainerKind.SHULKER, ShulkerBox.class);
        }
        if (type == Material.BARREL) {
            return readBlockInventory(item, ContainerKind.BARREL, Barrel.class);
        }
        if (type == Material.DECORATED_POT) {
            return readBlockInventory(item, ContainerKind.DECORATED_POT, DecoratedPot.class);
        }
        if (type == Material.BUNDLE) {
            return readBundle(item);
        }
        return null;
    }

    private static ContainerView readBlockInventory(ItemStack item, ContainerKind kind,
            Class<? extends org.bukkit.block.TileState> stateClass) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return emptyView(kind, item);
        }

        if (!stateClass.isInstance(blockStateMeta.getBlockState())) {
            return emptyView(kind, item);
        }

        org.bukkit.block.TileState state = (org.bukkit.block.TileState) blockStateMeta.getBlockState();
        if (!(state instanceof org.bukkit.inventory.InventoryHolder holder)) {
            return emptyView(kind, item);
        }

        ItemStack[] contents = cloneContents(holder.getInventory().getContents());
        boolean center = kind == ContainerKind.DECORATED_POT;
        return new ContainerView(kind, contents, getDisplayTitle(item), kind.guiSize(), center);
    }

    private static ContainerView readBundle(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta)) {
            return emptyView(ContainerKind.BUNDLE, item);
        }

        List<ItemStack> items = bundleMeta.getItems();
        ItemStack[] contents = new ItemStack[Math.max(items.size(), ContainerKind.BUNDLE.guiSize())];
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            contents[i] = stack != null ? stack.clone() : null;
        }
        return new ContainerView(ContainerKind.BUNDLE, contents, getDisplayTitle(item),
                ContainerKind.BUNDLE.guiSize(), false);
    }

    private static ContainerView emptyView(ContainerKind kind, ItemStack item) {
        return new ContainerView(kind, new ItemStack[kind.guiSize()], getDisplayTitle(item), kind.guiSize(),
                kind == ContainerKind.DECORATED_POT);
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] cloned = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            cloned[i] = source[i] != null ? source[i].clone() : null;
        }
        return cloned;
    }

    public static String getDisplayTitle(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "Container";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ColorUtils.translateLegacyColors(meta.getDisplayName());
        }

        return LEGACY.serialize(item.displayName());
    }

    /** @deprecated use {@link #isContainer(ItemStack)} */
    @Deprecated
    public static boolean isShulkerBox(ItemStack item) {
        return item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    /** @deprecated use {@link #read(ItemStack)} */
    @Deprecated
    public static ItemStack[] getShulkerContents(ItemStack shulker) {
        ContainerView view = read(shulker);
        return view != null ? view.contents() : new ItemStack[0];
    }
}
