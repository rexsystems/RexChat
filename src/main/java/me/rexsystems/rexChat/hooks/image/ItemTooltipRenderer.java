package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders an in-game-style hover tooltip image for an {@link ItemStack}.
 *
 * <p>The output is a PNG that visually matches Minecraft's hover tooltip:
 * a near-black background with the signature 1-pixel purple border, the
 * item's display name on top (white by default, aqua when enchanted), then
 * one line per enchantment, lore line, and durability — all in vanilla
 * gray.
 *
 * <p>Used by the {@code [item]} preview to attach a "tooltip card" alongside
 * the item icon, so the Discord embed reads as a real Minecraft hover
 * instead of a generic text dump.
 */
public final class ItemTooltipRenderer {

    /** Output scale: each MC pixel becomes {@code SCALE} screen pixels. */
    public static final int SCALE = 4;

    // Vanilla tooltip colours.
    private static final Color BG          = new Color(0x10, 0x00, 0x10, 0xF0);
    private static final Color BORDER_TOP  = new Color(0x50, 0x00, 0xFF, 0xFF);
    private static final Color BORDER_BOT  = new Color(0x28, 0x00, 0x7F, 0xFF);

    // Text colours (mc chat colours).
    private static final Color WHITE       = new Color(0xFF, 0xFF, 0xFF);
    private static final Color GRAY        = new Color(0xAA, 0xAA, 0xAA);
    private static final Color AQUA        = new Color(0x55, 0xFF, 0xFF);
    private static final Color DARK_PURPLE = new Color(0xAA, 0x00, 0xAA);
    private static final Color YELLOW      = new Color(0xFF, 0xFF, 0x55);
    private static final Color SHADOW      = new Color(0x3F, 0x3F, 0x3F);

    // Layout constants in MC pixels (×SCALE for output).
    private static final int PADDING       = 4;
    private static final int LINE_HEIGHT   = 10;
    private static final int BORDER_INSET  = 3;
    private static final int FONT_SIZE_MC  = 8;

    private final Font font;

    public ItemTooltipRenderer() {
        Font base = loadFont();
        this.font = base.deriveFont((float) (FONT_SIZE_MC * SCALE));
    }

    /** Build the tooltip image for {@code item}. */
    public BufferedImage render(ItemStack item) {
        List<Line> lines = collectLines(item);
        if (lines.isEmpty()) lines.add(new Line(prettyName(Material.AIR.name()), WHITE));

        // Compute width based on widest line. We need a Graphics2D to measure
        // text — render lazily by allocating a tiny scratch image first.
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probe = scratch.createGraphics();
        probe.setFont(font);
        FontMetrics fm = probe.getFontMetrics();
        int maxW = 0;
        for (Line l : lines) {
            int w = fm.stringWidth(l.text);
            if (w > maxW) maxW = w;
        }
        probe.dispose();

        int width  = maxW + PADDING * 2 * SCALE;
        int height = lines.size() * LINE_HEIGHT * SCALE + PADDING * 2 * SCALE;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            configure(g);
            paintBackground(g, width, height);
            paintBorder(g, width, height);
            paintLines(g, lines, fm);
        } finally {
            g.dispose();
        }
        return img;
    }

    // ---------- line collection ----------

    private static final class Line {
        final String text;
        final Color color;
        Line(String text, Color color) { this.text = text; this.color = color; }
    }

    private static List<Line> collectLines(ItemStack item) {
        List<Line> out = new ArrayList<>();
        if (item == null) return out;

        // 1) Display name. Enchanted items get the aqua tint; otherwise white,
        //    or yellow when the item has a custom display name (vanilla).
        boolean enchanted = !item.getEnchantments().isEmpty();
        boolean customName = false;
        String name;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName() != null
                && !meta.getDisplayName().isEmpty()) {
            name = stripColors(meta.getDisplayName());
            customName = true;
        } else {
            name = prettyName(item.getType().name());
        }
        Color nameColor;
        if (enchanted)        nameColor = AQUA;
        else if (customName)  nameColor = YELLOW;
        else                  nameColor = WHITE;
        out.add(new Line(name, nameColor));

        // 2) Enchantments (book contents take precedence for written-book-like items).
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty() && meta instanceof EnchantmentStorageMeta) {
            enchants = ((EnchantmentStorageMeta) meta).getStoredEnchants();
        }
        if (!enchants.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                String s = prettyEnchant(e.getKey()) + " " + romanize(e.getValue());
                out.add(new Line(s, GRAY));
            }
        }

        // 3) Lore (custom italics rendering would need extra work — keep the dark
        //    purple colour to match vanilla "purple lore" feel).
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            int loreLines = 0;
            for (String l : meta.getLore()) {
                String stripped = stripColors(l);
                if (stripped.isEmpty()) continue;
                out.add(new Line(stripped, DARK_PURPLE));
                if (++loreLines >= 8) {
                    out.add(new Line("…", GRAY));
                    break;
                }
            }
        }

        // 4) Durability for damageable items.
        try {
            if (meta instanceof Damageable) {
                Damageable d = (Damageable) meta;
                int max = item.getType().getMaxDurability();
                if (max > 0 && d.hasDamage()) {
                    int remaining = max - d.getDamage();
                    out.add(new Line("Durability: " + remaining + " / " + max, GRAY));
                }
            }
        } catch (Throwable ignored) {
        }

        return out;
    }

    private static String prettyName(String enumName) {
        if (enumName == null) return "";
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String prettyEnchant(Enchantment e) {
        try {
            return prettyName(e.getKey().getKey());
        } catch (Throwable t) {
            return e.toString();
        }
    }

    /** Convert 1..10 to roman numerals; otherwise just the number. */
    private static String romanize(int level) {
        switch (level) {
            case 1:  return "I";
            case 2:  return "II";
            case 3:  return "III";
            case 4:  return "IV";
            case 5:  return "V";
            case 6:  return "VI";
            case 7:  return "VII";
            case 8:  return "VIII";
            case 9:  return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }

    private static String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    // ---------- painting ----------

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private static void paintBackground(Graphics2D g, int w, int h) {
        // Background fills the whole tooltip including the 1-px border area;
        // the border colours overpaint the very outer edge afterwards. This
        // gets rid of the stripe of bg-then-empty-then-border the previous
        // double-inset approach produced.
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
    }

    /** Single-thickness purple border drawn flush with the tooltip edge — much
     *  closer to the vanilla MC tooltip look (a 1-pixel ring right at the edge,
     *  not a thick bar pulled inward). */
    private static void paintBorder(Graphics2D g, int w, int h) {
        int s = SCALE; // 1 mc pixel
        g.setColor(BORDER_TOP);
        g.fillRect(0,        0,        w,    s);            // top edge
        g.fillRect(0,        0,        s,    h);            // left edge
        g.setColor(BORDER_BOT);
        g.fillRect(0,        h - s,    w,    s);            // bottom edge
        g.fillRect(w - s,    0,        s,    h);            // right edge
    }

    private void paintLines(Graphics2D g, List<Line> lines, FontMetrics fm) {
        g.setFont(font);
        int pad = PADDING * SCALE;
        // Baseline of first line. MC uses ascent + 1 mc pixel padding.
        int baseline = pad + fm.getAscent();
        for (Line l : lines) {
            // Drop shadow (1mc-pixel offset, dark grey).
            g.setColor(SHADOW);
            g.drawString(l.text, pad + SCALE, baseline + SCALE);
            g.setColor(l.color);
            g.drawString(l.text, pad, baseline);
            baseline += LINE_HEIGHT * SCALE;
        }
    }

    private static Color lerp(Color a, Color b, float t) {
        int r = (int) (a.getRed()   * (1 - t) + b.getRed()   * t);
        int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(r, g, bl, 0xFF);
    }

    private static Font loadFont() {
        try (InputStream in = ItemTooltipRenderer.class
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
