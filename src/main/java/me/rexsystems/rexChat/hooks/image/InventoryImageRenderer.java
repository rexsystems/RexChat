package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Renders an in-game-style PNG of a player inventory or ender chest.
 * <p>
 * Drawing happens directly at the final output scale (4× by default) so item
 * textures are nearest-neighbour upscaled to crisp pixels and the count text is
 * rendered with the Monocraft TTF at the matching point size — no intermediate
 * downscaled canvas, no blurry text.
 */
public final class InventoryImageRenderer {

    /** Output scale: each Minecraft pixel becomes {@code SCALE} screen pixels. */
    public static final int SCALE = 4;

    // Logical (Minecraft) sizes — scaled by SCALE when drawn
    private static final int SLOT_SIZE   = 18 * SCALE;
    private static final int TEX_SIZE    = 16 * SCALE;
    private static final int FRAME_PAD   = 8  * SCALE;
    private static final int TITLE_BAR   = 14 * SCALE;
    private static final int HOTBAR_GAP  = 4  * SCALE;

    // Palette (vanilla GUI)
    private static final Color FRAME_COLOR  = new Color(0xC6, 0xC6, 0xC6);
    private static final Color SLOT_FILL    = new Color(0x8B, 0x8B, 0x8B);
    private static final Color SLOT_DARK    = new Color(0x37, 0x37, 0x37);
    private static final Color SLOT_LIGHT   = new Color(0xFF, 0xFF, 0xFF);
    private static final Color FRAME_DARK   = new Color(0x55, 0x55, 0x55);
    private static final Color FRAME_LIGHT  = new Color(0xFF, 0xFF, 0xFF);
    private static final Color TITLE_COLOR  = new Color(0x40, 0x40, 0x40);
    private static final Color COUNT_COLOR  = new Color(0xFF, 0xFF, 0xFF);
    private static final Color COUNT_SHADOW = new Color(0x3F, 0x3F, 0x3F);

    private final ItemTextureCache textures;
    private final Font titleFont; // for title bar (slightly larger)
    private final Font countFont; // for stack counts (smaller, like in-game)

    public InventoryImageRenderer(ItemTextureCache textures) {
        this.textures = textures;
        Font base = loadFont();
        // Minecraft default font is 8 px tall; render at 8*SCALE pt for pixel-perfect look
        this.countFont = base.deriveFont((float) (8 * SCALE));
        this.titleFont = base.deriveFont((float) (8 * SCALE));
    }

    /** Render a player's full inventory: 9x4 grid (3 main rows + hotbar with separator). */
    public BufferedImage renderPlayerInventory(Player player, String title) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents(); // 36: 0-8 hotbar, 9-35 main

        int cols = 9;
        int mainRows = 3;
        int hotbarRows = 1;

        int contentW = cols * SLOT_SIZE;
        int contentH = mainRows * SLOT_SIZE + HOTBAR_GAP + hotbarRows * SLOT_SIZE;

        int w = contentW + FRAME_PAD * 2;
        int h = TITLE_BAR + contentH + FRAME_PAD * 2;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            configureGraphics(g);
            drawFrame(g, w, h);
            drawTitle(g, title, FRAME_PAD, FRAME_PAD + TITLE_BAR - (4 * SCALE));

            int gridX = FRAME_PAD;
            int gridY = FRAME_PAD + TITLE_BAR;

            // Main rows (slots 9..35)
            for (int row = 0; row < mainRows; row++) {
                for (int col = 0; col < cols; col++) {
                    int slot = 9 + row * cols + col;
                    int x = gridX + col * SLOT_SIZE;
                    int y = gridY + row * SLOT_SIZE;
                    drawSlot(g, x, y);
                    drawItem(g, x + SCALE, y + SCALE, storage[slot]);
                }
            }
            // Hotbar (slots 0..8) below the gap
            int hotbarY = gridY + mainRows * SLOT_SIZE + HOTBAR_GAP;
            for (int col = 0; col < cols; col++) {
                int x = gridX + col * SLOT_SIZE;
                drawSlot(g, x, hotbarY);
                drawItem(g, x + SCALE, hotbarY + SCALE, storage[col]);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Render the ender chest (or any flat array of items) as a 9 x N grid. */
    public BufferedImage renderEnderChest(ItemStack[] contents, String title) {
        int cols = 9;
        int rows = (int) Math.ceil(Math.max(1, contents.length) / 9.0);

        int contentW = cols * SLOT_SIZE;
        int contentH = rows * SLOT_SIZE;

        int w = contentW + FRAME_PAD * 2;
        int h = TITLE_BAR + contentH + FRAME_PAD * 2;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            configureGraphics(g);
            drawFrame(g, w, h);
            drawTitle(g, title, FRAME_PAD, FRAME_PAD + TITLE_BAR - (4 * SCALE));

            int gridX = FRAME_PAD;
            int gridY = FRAME_PAD + TITLE_BAR;

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int idx = row * cols + col;
                    int x = gridX + col * SLOT_SIZE;
                    int y = gridY + row * SLOT_SIZE;
                    drawSlot(g, x, y);
                    if (idx < contents.length) {
                        drawItem(g, x + SCALE, y + SCALE, contents[idx]);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    // ---------- drawing primitives ----------

    private void configureGraphics(Graphics2D g) {
        // Pixel-art: nearest-neighbour for textures, no AA on shapes/text
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private void drawFrame(Graphics2D g, int w, int h) {
        g.setColor(FRAME_COLOR);
        g.fillRect(0, 0, w, h);

        // Outer bevel
        g.setColor(FRAME_LIGHT);
        g.fillRect(0, 0, w, SCALE);
        g.fillRect(0, 0, SCALE, h);
        g.setColor(FRAME_DARK);
        g.fillRect(0, h - SCALE, w, SCALE);
        g.fillRect(w - SCALE, 0, SCALE, h);
    }

    private void drawTitle(Graphics2D g, String text, int x, int y) {
        if (text == null || text.isEmpty()) return;
        g.setFont(titleFont);
        g.setColor(TITLE_COLOR);
        g.drawString(text, x, y);
    }

    private void drawSlot(Graphics2D g, int x, int y) {
        g.setColor(SLOT_FILL);
        g.fillRect(x, y, SLOT_SIZE, SLOT_SIZE);

        // Inset shadow on top + left
        g.setColor(SLOT_DARK);
        g.fillRect(x, y, SLOT_SIZE - SCALE, SCALE);
        g.fillRect(x, y, SCALE, SLOT_SIZE - SCALE);
        // Highlight on bottom + right
        g.setColor(SLOT_LIGHT);
        g.fillRect(x + SCALE, y + SLOT_SIZE - SCALE, SLOT_SIZE - SCALE, SCALE);
        g.fillRect(x + SLOT_SIZE - SCALE, y + SCALE, SCALE, SLOT_SIZE - SCALE);
    }

    private void drawItem(Graphics2D g, int x, int y, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        BufferedImage tex = textures.get(item.getType());
        if (tex != null) {
            g.drawImage(tex, x, y, TEX_SIZE, TEX_SIZE, null);
        }

        int amount = item.getAmount();
        if (amount > 1) {
            String s = String.valueOf(amount);
            g.setFont(countFont);
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(s);
            // Anchor bottom-right of texture, with 1 logical px (SCALE) inset
            int textX = x + TEX_SIZE - textW - SCALE;
            int textY = y + TEX_SIZE - SCALE;
            g.setColor(COUNT_SHADOW);
            g.drawString(s, textX + SCALE, textY + SCALE);
            g.setColor(COUNT_COLOR);
            g.drawString(s, textX, textY);
        }
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
}
