package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Renders a Discord-bound PNG of a player's inventory or ender chest using
 * the real Minecraft GUI sprite-sheets.
 *
 * <p>Approach mirrors what InteractiveChat-DiscordSRV-Addon does (composite
 * the actual {@code gui/container/...} chrome and overlay items with the
 * vanilla item / block textures), but with a 2D body sprite for the
 * character preview instead of full 3D model rendering. Output is rendered
 * directly at 4× scale using nearest-neighbour for crisp pixel art.
 *
 * <p>All slot positions below are pre-scaled by {@link #SCALE} from the
 * canonical 1× MC GUI layout.
 */
public final class InventoryImageRenderer {

    /** Output scale: each Minecraft pixel becomes {@code SCALE} screen pixels. */
    public static final int SCALE = 4;

    // ----- Player inventory: gui/container/inventory.png -----
    /** GUI region size of inventory.png within the 256x256 sprite sheet. */
    private static final int INV_W = 176;
    private static final int INV_H = 166;

    // Slot top-left coords (1×) relative to inventory.png — slot is 18×18,
    // item rendering area inside the slot is at (+1,+1) and 16×16 in size.
    private static final int[] HELMET_SLOT     = {8, 8};
    private static final int[] CHESTPLATE_SLOT = {8, 26};
    private static final int[] LEGGINGS_SLOT   = {8, 44};
    private static final int[] BOOTS_SLOT      = {8, 62};
    private static final int[] OFFHAND_SLOT    = {77, 62};
    private static final int   CRAFT_GRID_X    = 98;
    private static final int   CRAFT_GRID_Y    = 18;
    private static final int[] CRAFT_RESULT    = {154, 28};
    private static final int   MAIN_INV_X      = 8;
    private static final int   MAIN_INV_Y      = 84;
    private static final int   HOTBAR_X        = 8;
    private static final int   HOTBAR_Y        = 142;
    private static final int   SLOT_STRIDE     = 18;

    // Character preview area within inventory.png (1×).
    private static final int   PREVIEW_X       = 26;
    private static final int   PREVIEW_Y       = 8;
    private static final int   PREVIEW_W       = 44;
    private static final int   PREVIEW_H       = 60;

    // ----- Ender chest: cropped from gui/container/shulker_box.png -----
    /** Visible region for a 27-slot container header + 3 rows + bottom border. */
    private static final int   CHEST_W         = 176;
    private static final int   CHEST_H         = 78;
    /** Title text position inside chest GUI (1×). */
    private static final int   CHEST_TITLE_X   = 8;
    private static final int   CHEST_TITLE_Y   = 6;
    /** First slot top-left (1×) inside chest GUI. */
    private static final int   CHEST_SLOTS_X   = 8;
    private static final int   CHEST_SLOTS_Y   = 18;

    // Pixel-art palette for fallbacks.
    private static final Color FALLBACK_BG     = new Color(0xC6, 0xC6, 0xC6);
    private static final Color COUNT_COLOR     = new Color(0xFF, 0xFF, 0xFF);
    private static final Color COUNT_SHADOW    = new Color(0x3F, 0x3F, 0x3F);
    private static final Color TITLE_COLOR     = new Color(0x40, 0x40, 0x40);
    private static final Color DURABILITY_BG   = new Color(0x00, 0x00, 0x00);

    private final ItemTextureCache itemTextures;
    private final BlockIconRenderer blockIcons;
    private final GuiTextureCache guiTextures;
    private final PlayerBodyRenderer bodyRenderer;
    private final Font countFont;
    private final Font titleFont;

    public InventoryImageRenderer(ItemTextureCache itemTextures,
                                  GuiTextureCache guiTextures,
                                  PlayerBodyRenderer bodyRenderer) {
        this.itemTextures = itemTextures;
        this.blockIcons = new BlockIconRenderer(itemTextures);
        this.guiTextures = guiTextures;
        this.bodyRenderer = bodyRenderer;
        Font base = loadFont();
        this.countFont = base.deriveFont((float) (8 * SCALE));
        this.titleFont = base.deriveFont((float) (8 * SCALE));
    }

    /** Expose the embedded block-icon renderer so callers can hook a debug log. */
    public BlockIconRenderer getBlockIcons() {
        return blockIcons;
    }

    /**
     * Render the full player inventory using the vanilla
     * {@code gui/container/inventory.png} chrome.
     */
    public BufferedImage renderPlayerInventory(Player player, String title) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents(); // 36: 0-8 hotbar, 9-35 main

        int w = INV_W * SCALE;
        int h = INV_H * SCALE;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            configureGraphics(g);

            // 1) Background chrome: real inventory.png cropped to 176x166 then up-scaled.
            BufferedImage chrome = guiTextures.get("container/inventory");
            if (chrome != null) {
                BufferedImage region = chrome.getSubimage(
                        0, 0,
                        Math.min(INV_W, chrome.getWidth()),
                        Math.min(INV_H, chrome.getHeight()));
                g.drawImage(region, 0, 0, w, h, null);
            } else {
                g.setColor(FALLBACK_BG);
                g.fillRect(0, 0, w, h);
            }

            // 2) Character preview — front-facing 2D body composed from the
            // player's skin texture with armour overlays. Centred horizontally
            // inside the chrome's character preview rectangle and fitted by
            // height so we never distort the proportions.
            try {
                BufferedImage body = bodyRenderer.render(player, PREVIEW_H * SCALE);
                if (body != null) {
                    int dh = PREVIEW_H * SCALE;
                    int dw = (int) Math.round(
                            (double) body.getWidth() / body.getHeight() * dh);
                    int previewW = PREVIEW_W * SCALE;
                    int dx = PREVIEW_X * SCALE + Math.max(0, (previewW - dw) / 2);
                    int dy = PREVIEW_Y * SCALE;
                    drawScaled(g, body, dx, dy, dw, dh);
                }
            } catch (Throwable ignored) {
            }

            // 3) Armor + offhand
            drawItem(g, HELMET_SLOT[0], HELMET_SLOT[1], inv.getHelmet());
            drawItem(g, CHESTPLATE_SLOT[0], CHESTPLATE_SLOT[1], inv.getChestplate());
            drawItem(g, LEGGINGS_SLOT[0], LEGGINGS_SLOT[1], inv.getLeggings());
            drawItem(g, BOOTS_SLOT[0], BOOTS_SLOT[1], inv.getBoots());
            drawItem(g, OFFHAND_SLOT[0], OFFHAND_SLOT[1], inv.getItemInOffHand());

            // 4) Crafting grid (2x2) — best effort. Most servers don't expose it via
            //    PlayerInventory; left empty for parity with a closed inventory.
            //    If the API ever exposes it, drop it in here.
            // for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
            //     drawItem(g, CRAFT_GRID_X + col * SLOT_STRIDE,
            //                  CRAFT_GRID_Y + row * SLOT_STRIDE, null);
            // drawItem(g, CRAFT_RESULT[0], CRAFT_RESULT[1], null);

            // 5) Main inventory (slots 9..35)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slot = 9 + row * 9 + col;
                    drawItem(g,
                            MAIN_INV_X + col * SLOT_STRIDE,
                            MAIN_INV_Y + row * SLOT_STRIDE,
                            storage[slot]);
                }
            }

            // 6) Hotbar (slots 0..8)
            for (int col = 0; col < 9; col++) {
                drawItem(g,
                        HOTBAR_X + col * SLOT_STRIDE,
                        HOTBAR_Y,
                        storage[col]);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Render a 27-slot ender chest using the {@code shulker_box.png} chrome
     * cropped to the container portion. The title is drawn into the title
     * bar of the chest GUI.
     */
    public BufferedImage renderEnderChest(ItemStack[] contents, String title) {
        int w = CHEST_W * SCALE;
        int h = CHEST_H * SCALE;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            configureGraphics(g);

            // 1) Chest chrome: take the top of shulker_box.png (title + 3 rows + border).
            BufferedImage chrome = guiTextures.get("container/shulker_box");
            if (chrome != null) {
                int srcW = Math.min(CHEST_W, chrome.getWidth());
                int srcH = Math.min(CHEST_H, chrome.getHeight());
                BufferedImage region = chrome.getSubimage(0, 0, srcW, srcH);
                g.drawImage(region, 0, 0, w, h, null);
            } else {
                g.setColor(FALLBACK_BG);
                g.fillRect(0, 0, w, h);
            }

            // 2) Title text (vanilla GUI renders the title in the chest header).
            if (title != null && !title.isEmpty()) {
                g.setFont(titleFont);
                g.setColor(TITLE_COLOR);
                int tx = CHEST_TITLE_X * SCALE;
                // y is baseline; in MC the title is drawn near the top of the GUI.
                int ty = CHEST_TITLE_Y * SCALE + (8 * SCALE) - SCALE;
                g.drawString(title, tx, ty);
            }

            // 3) Items in 3×9 grid
            int rows = 3, cols = 9;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int idx = row * cols + col;
                    int x = CHEST_SLOTS_X + col * SLOT_STRIDE;
                    int y = CHEST_SLOTS_Y + row * SLOT_STRIDE;
                    if (idx < contents.length) {
                        drawItem(g, x, y, contents[idx]);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    // ---------- drawing primitives ----------

    /**
     * Draw an item at the given 1×-coordinate slot top-left. The item
     * texture occupies the slot's 16×16 inner area; in MC's standard
     * inventory.png that area starts exactly AT the slot top-left (the 1-pixel
     * bevel sits OUTSIDE the slot top-left coords). Drawing items at slot+0
     * matches the texture pixel-for-pixel; the previous slot+1 inset shifted
     * everything 1 px down-right of where the slot actually is.
     */
    private void drawItem(Graphics2D g, int slotX1x, int slotY1x, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        int tx = slotX1x * SCALE;
        int ty = slotY1x * SCALE;
        int tw = 16 * SCALE;
        int th = 16 * SCALE;

        BufferedImage tex = resolveItemIcon(item);
        if (tex != null) {
            drawScaled(g, tex, tx, ty, tw, th);
        }

        // Durability bar — vanilla MC draws this at the bottom of the slot.
        drawDurabilityBar(g, item, tx, ty, tw, th);

        // Stack count — bottom-right of the texture, with shadow.
        int amount = item.getAmount();
        if (amount > 1) {
            String s = String.valueOf(amount);
            g.setFont(countFont);
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(s);
            int textX = tx + tw - textW - SCALE;
            int textY = ty + th - SCALE;
            g.setColor(COUNT_SHADOW);
            g.drawString(s, textX + SCALE, textY + SCALE);
            g.setColor(COUNT_COLOR);
            g.drawString(s, textX, textY);
        }
    }

    /**
     * Draw vanilla-style durability bar inside the item slot. Geometry +
     * colour formula match Minecraft's GUI rendering (and the
     * InteractiveChat-DiscordSRV-Addon) renderer: 13×2 black background at
     * y=13 of the slot, foreground 1 px high coloured by HSB hue
     * {@code 125° × percentage}.
     */
    private static void drawDurabilityBar(Graphics2D g, ItemStack item,
                                          int tx, int ty, int tw, int th) {
        try {
            if (!(item.getItemMeta() instanceof Damageable)) return;
            Damageable d = (Damageable) item.getItemMeta();
            int max = item.getType().getMaxDurability();
            if (max <= 0) return;
            int dmg = d.getDamage();
            if (dmg <= 0) return;

            double percentage = Math.max(0.0, Math.min(1.0, (max - dmg) / (double) max));

            // Bar geometry in MC pixels: x=2..15 (width 13), y=13..14 background,
            // y=13 foreground (1 px tall, top of background).
            int barW   = 13 * SCALE;
            int barH   = 2  * SCALE;
            int barX   = tx + 2 * SCALE;
            int barY   = ty + 13 * SCALE;
            int fillW  = (int) Math.round(barW * percentage);

            // Background rectangle (black).
            g.setColor(DURABILITY_BG);
            g.fillRect(barX, barY, barW, barH);

            // Foreground colour: hue goes from 0 (red) at 0% through to ~125° at 100%.
            float hue = (float) (125.0 * percentage / 360.0);
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            g.setColor(new Color(rgb));
            g.fillRect(barX, barY, Math.max(0, fillW), SCALE);
        } catch (Throwable ignored) {
        }
    }

    private static void drawScaled(Graphics2D g, BufferedImage src,
                                   int x, int y, int w, int h) {
        Object prev = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object prevAlpha = g.getComposite();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(src, x, y, w, h, null);
        } finally {
            if (prev != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, prev);
            if (prevAlpha != null) g.setComposite((java.awt.Composite) prevAlpha);
        }
    }

    private void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private static Font loadFont() {
        try (InputStream in = InventoryImageRenderer.class
                .getResourceAsStream("/fonts/Monocraft.ttf")) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                return f.deriveFont(8f);
            }
        } catch (IOException | java.awt.FontFormatException ignored) {
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 8);
    }

    /** Small helper for callers that hold an item name plus a metadata snapshot. */
    @SuppressWarnings("unused")
    public static boolean hasDisplayName(ItemStack item) {
        if (item == null) return false;
        ItemMeta m = item.getItemMeta();
        return m != null && m.hasDisplayName();
    }

    // ---------- shared icon resolution ----------

    /**
     * Resolve the best icon for an item: an iso cube for blocks (so dirt /
     * stone / planks etc. read as 3D in chat), or the flat 16×16 item
     * texture otherwise. Returns {@code null} when nothing usable is
     * available so the caller can leave the slot empty rather than draw a
     * giant magenta missing-texture square.
     */
    private BufferedImage resolveItemIcon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        // 1) Blocks: try the iso cube first.
        if (item.getType().isBlock()) {
            BufferedImage iso = blockIcons.iso(item.getType());
            if (iso != null) return iso;
        }
        // 2) Items: flat texture.
        BufferedImage flat = itemTextures.get(item.getType());
        if (flat != null && flat != itemTextures.missing()) return flat;
        return null;
    }

    /**
     * Render an item icon at the requested square pixel size, suitable for
     * use as the {@code setImage} attachment on an embed. Background is
     * transparent and the icon is centred. Falls back to a flat 16×16 item
     * texture when an iso cube isn't possible. Damageable items get a
     * vanilla-style durability bar drawn at the bottom of the icon.
     */
    public BufferedImage renderItemIcon(ItemStack item, int sizePx) {
        BufferedImage out = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            configureGraphics(g);
            BufferedImage tex = resolveItemIcon(item);
            if (tex == null) return out; // empty / transparent

            // Reserve ~8% margin so the icon isn't flush against the embed edge.
            int margin = sizePx / 12;
            int side = sizePx - margin * 2;
            int srcMax = Math.max(tex.getWidth(), tex.getHeight());
            int dw = (int) Math.round((double) tex.getWidth()  / srcMax * side);
            int dh = (int) Math.round((double) tex.getHeight() / srcMax * side);
            int dx = (sizePx - dw) / 2;
            int dy = (sizePx - dh) / 2;
            drawScaled(g, tex, dx, dy, dw, dh);

            // Durability bar overlaid in the lower portion of the icon.
            drawIconDurabilityBar(g, item, dx, dy, dw, dh);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Larger durability bar tuned for the big [item] icon. Same proportions
     *  as the slot version but scaled to the icon size. */
    private static void drawIconDurabilityBar(Graphics2D g, ItemStack item,
                                              int tx, int ty, int tw, int th) {
        try {
            if (item == null || !(item.getItemMeta() instanceof Damageable)) return;
            Damageable d = (Damageable) item.getItemMeta();
            int max = item.getType().getMaxDurability();
            if (max <= 0 || !d.hasDamage()) return;

            double percentage = Math.max(0.0, Math.min(1.0,
                    (max - d.getDamage()) / (double) max));

            // Geometry mirrors the slot bar: 13/16 of icon width, anchored
            // 13/16 of icon height from the top so it sits in the lower band.
            int barW = (int) Math.round(tw * (13.0 / 16.0));
            int barH = Math.max(2, (int) Math.round(th * (2.0 / 16.0)));
            int barX = tx + (int) Math.round(tw * (2.0 / 16.0));
            int barY = ty + (int) Math.round(th * (13.0 / 16.0));
            int fillW = (int) Math.round(barW * percentage);

            g.setColor(DURABILITY_BG);
            g.fillRect(barX, barY, barW, barH);
            float hue = (float) (125.0 * percentage / 360.0);
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            g.setColor(new Color(rgb));
            g.fillRect(barX, barY, Math.max(0, fillW), Math.max(1, barH / 2));
        } catch (Throwable ignored) {
        }
    }
}
