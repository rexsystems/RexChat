package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Builds a 2D front-facing image of the player's body, optionally
 * overlaid with the equipped armour set.
 *
 * <p>The skin texture is downloaded from {@code mc-heads.net/skin/{uuid}}
 * (raw 64×64 PNG) and cached on disk. Each visible body part (head, body,
 * arms, legs) is extracted from the canonical skin layout and stacked into
 * a 16×32 base image. When armour is provided, the matching face from
 * {@code models/armor/<material>_layer_{1,2}.png} is composited on top of
 * the corresponding body part.
 *
 * <p>This is intentionally a 2D front-facing render (not the full 3D pose
 * a real Minecraft player inventory would show); it's a much smaller chunk
 * of code and reads fine inside the inventory image's character preview
 * area.
 */
public final class PlayerBodyRenderer {

    /** Width of the rendered body in MC-pixels (16 wide: arm + body + arm). */
    public static final int BASE_W = 16;
    /** Height of the rendered body in MC-pixels (32 tall: head 8 + body 12 + legs 12). */
    public static final int BASE_H = 32;

    private static final int TIMEOUT_MS = 5_000;
    private static final String SKIN_URL = "https://mc-heads.net/skin/%s";

    private final ItemTextureCache itemTextures;
    private final File cacheDir;
    private final ConcurrentMap<UUID, BufferedImage> skinCache = new ConcurrentHashMap<>();
    private Consumer<String> debug = msg -> {};

    public PlayerBodyRenderer(File pluginDataFolder, ItemTextureCache itemTextures) {
        this.itemTextures = itemTextures;
        this.cacheDir = new File(pluginDataFolder, "textures/skins");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    /** Wire up a debug-log sink so this renderer can report what it tried. */
    public void setDebug(Consumer<String> sink) {
        this.debug = sink == null ? msg -> {} : sink;
    }

    /**
     * Render the player's body at the requested approximate height (in
     * pixels), with all currently equipped armour pieces composited on top.
     * Returns {@code null} when the skin can't be downloaded.
     */
    public BufferedImage render(Player player, int outputHeight) {
        if (player == null) return null;
        BufferedImage skin = loadSkin(player.getUniqueId());
        if (skin == null) {
            debug.accept("body: skin download FAILED for " + player.getName()
                    + " (" + player.getUniqueId() + "); preview will be skipped");
            return null;
        }
        debug.accept("body: skin loaded for " + player.getName()
                + " (" + skin.getWidth() + "x" + skin.getHeight() + ")");

        BufferedImage base = renderBaseBody(skin);

        // Overlay armor (helmet → chestplate → leggings → boots; later layers
        // would otherwise hide earlier ones, but our crops don't overlap).
        PlayerInventory inv = player.getInventory();
        overlayArmorPiece(base, inv.getHelmet(),     ArmorSlot.HELMET);
        overlayArmorPiece(base, inv.getChestplate(), ArmorSlot.CHESTPLATE);
        overlayArmorPiece(base, inv.getLeggings(),   ArmorSlot.LEGGINGS);
        overlayArmorPiece(base, inv.getBoots(),      ArmorSlot.BOOTS);

        // Scale to requested output height (nearest-neighbour for crisp pixels).
        return scaleByHeight(base, outputHeight);
    }

    // ---------- skin acquisition + caching ----------

    private BufferedImage loadSkin(UUID uuid) {
        BufferedImage cached = skinCache.get(uuid);
        if (cached != null) return cached;

        File diskFile = new File(cacheDir, uuid + ".png");
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                BufferedImage img = ImageIO.read(diskFile);
                if (img != null) {
                    skinCache.put(uuid, img);
                    return img;
                }
            } catch (IOException ignored) {
            }
        }

        BufferedImage img = download(String.format(SKIN_URL, uuid));
        if (img == null) return null;
        try {
            ImageIO.write(img, "png", diskFile);
        } catch (IOException ignored) {
        }
        skinCache.put(uuid, img);
        return img;
    }

    private static BufferedImage download(String url) {
        try {
            URL u = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "RexChat/1.0 (+https://rexsystems.me)");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) {
                conn.disconnect();
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                conn.disconnect();
                return img;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- body composition ----------

    /**
     * Build the 16×32 front-facing body from the canonical skin layout. Skin
     * texture sub-regions used:
     * <ul>
     *   <li>head front: (8, 8)..(15, 15)</li>
     *   <li>body front: (20, 20)..(27, 31)</li>
     *   <li>right arm front: (44, 20)..(47, 31) (slim assumed)</li>
     *   <li>left arm front: (36, 52)..(39, 63) (modern skins)</li>
     *   <li>right leg front: (4, 20)..(7, 31)</li>
     *   <li>left leg front: (20, 52)..(23, 63) (modern skins)</li>
     * </ul>
     * Plus the matching overlay layers (hat / jacket / sleeves) drawn on top.
     */
    private static BufferedImage renderBaseBody(BufferedImage skin) {
        BufferedImage body = new BufferedImage(BASE_W, BASE_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = body.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // Head 8×8 at top centre (4..11, 0..7).
            blit(g, skin, 8, 8, 8, 8,   4, 0);
            // Right arm 4×12 (left side of canvas) at (0..3, 8..19).
            blit(g, skin, 44, 20, 4, 12, 0, 8);
            // Body 8×12 at (4..11, 8..19).
            blit(g, skin, 20, 20, 8, 12, 4, 8);
            // Left arm 4×12 (right side of canvas) at (12..15, 8..19).
            // Modern skins have it at (36, 52); legacy mirrors right arm.
            if (skin.getHeight() >= 64) {
                blit(g, skin, 36, 52, 4, 12, 12, 8);
            } else {
                blitMirroredX(g, skin, 44, 20, 4, 12, 12, 8);
            }
            // Right leg 4×12 at (4..7, 20..31).
            blit(g, skin, 4, 20, 4, 12, 4, 20);
            // Left leg 4×12 at (8..11, 20..31).
            if (skin.getHeight() >= 64) {
                blit(g, skin, 20, 52, 4, 12, 8, 20);
            } else {
                blitMirroredX(g, skin, 4, 20, 4, 12, 8, 20);
            }

            // Skin overlay layers (hat + jacket + sleeves + pants), modern only.
            if (skin.getHeight() >= 64) {
                blit(g, skin, 40, 8,  8, 8,   4, 0);   // hat
                blit(g, skin, 20, 36, 8, 12,  4, 8);   // jacket
                blit(g, skin, 44, 36, 4, 12,  0, 8);   // right sleeve
                blit(g, skin, 52, 52, 4, 12, 12, 8);   // left sleeve
                blit(g, skin, 4,  36, 4, 12,  4, 20);  // right pants
                blit(g, skin, 4,  52, 4, 12,  8, 20);  // left pants
            }
        } finally {
            g.dispose();
        }
        return body;
    }

    private static void blit(Graphics2D g, BufferedImage src,
                             int sx, int sy, int sw, int sh,
                             int dx, int dy) {
        if (sx + sw > src.getWidth() || sy + sh > src.getHeight()) return;
        BufferedImage sub = src.getSubimage(sx, sy, sw, sh);
        g.drawImage(sub, dx, dy, null);
    }

    private static void blitMirroredX(Graphics2D g, BufferedImage src,
                                      int sx, int sy, int sw, int sh,
                                      int dx, int dy) {
        if (sx + sw > src.getWidth() || sy + sh > src.getHeight()) return;
        BufferedImage sub = src.getSubimage(sx, sy, sw, sh);
        g.drawImage(sub, dx + sw, dy, dx, dy + sh, 0, 0, sw, sh, null);
    }

    // ---------- armour overlay ----------

    /**
     * Identifies which body region each armour piece covers in the 16×32
     * base image. Coordinates intentionally use the SAME pixel layout as
     * vanilla armour textures so we can crop straight from them.
     */
    private enum ArmorSlot {
        HELMET    (1, 8,  8,  8, 8,   4, 0,  8, 8),                         // head
        CHESTPLATE(1, 20, 20, 8, 12,  4, 8,  8, 12),                        // body
        LEGGINGS  (2, 4,  20, 4, 12,  4, 20, 4, 12),                        // right leg
        BOOTS     (1, 4,  28, 4, 4,   4, 28, 4, 4);                         // boots over legs

        final int layer;
        final int sx, sy, sw, sh;
        final int dx, dy, dw, dh;

        ArmorSlot(int layer, int sx, int sy, int sw, int sh,
                  int dx, int dy, int dw, int dh) {
            this.layer = layer;
            this.sx = sx; this.sy = sy; this.sw = sw; this.sh = sh;
            this.dx = dx; this.dy = dy; this.dw = dw; this.dh = dh;
        }
    }

    private void overlayArmorPiece(BufferedImage body, ItemStack item, ArmorSlot slot) {
        if (item == null || item.getType() == Material.AIR) return;
        List<String> paths = armorTexturePaths(item.getType(), slot.layer);
        if (paths.isEmpty()) {
            debug.accept("armor: " + item.getType() + " has no resolver — skipped");
            return;
        }

        BufferedImage armor = null;
        String hitPath = null;
        for (String p : paths) {
            BufferedImage img = itemTextures.getRaw(p);
            if (img != null) {
                armor = img;
                hitPath = p;
                break;
            }
        }
        if (armor == null) {
            debug.accept("armor: " + item.getType() + " texture not found at any of " + paths);
            return;
        }
        debug.accept("armor: " + item.getType() + " using " + hitPath
                + " (" + armor.getWidth() + "x" + armor.getHeight() + ")");

        Graphics2D g = body.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setComposite(AlphaComposite.SrcOver);

            BufferedImage sub = safeSub(armor, slot.sx, slot.sy, slot.sw, slot.sh);
            if (sub != null) {
                g.drawImage(sub, slot.dx, slot.dy, slot.dw, slot.dh, null);
            }

            // For chestplate, also overlay arms.
            if (slot == ArmorSlot.CHESTPLATE) {
                BufferedImage rArm = safeSub(armor, 44, 20, 4, 12);
                if (rArm != null) g.drawImage(rArm, 0, 8, 4, 12, null);
                BufferedImage lArm = armor.getHeight() >= 64
                        ? safeSub(armor, 36, 52, 4, 12)
                        : safeSub(armor, 44, 20, 4, 12);
                if (lArm != null) g.drawImage(lArm, 12, 8, 4, 12, null);
            }
            // Leggings cover both legs.
            if (slot == ArmorSlot.LEGGINGS) {
                BufferedImage lLeg = armor.getHeight() >= 64
                        ? safeSub(armor, 20, 52, 4, 12)
                        : safeSub(armor, 4, 20, 4, 12);
                if (lLeg != null) g.drawImage(lLeg, 8, 20, 4, 12, null);
            }
            // Boots: both legs from the bottom 4 px (already cropped via slot).
            if (slot == ArmorSlot.BOOTS) {
                BufferedImage lBoot = armor.getHeight() >= 64
                        ? safeSub(armor, 20, 60, 4, 4)
                        : safeSub(armor, 4, 28, 4, 4);
                if (lBoot != null) g.drawImage(lBoot, 8, 28, 4, 4, null);
            }
        } finally {
            g.dispose();
        }
    }

    private static BufferedImage safeSub(BufferedImage img, int x, int y, int w, int h) {
        try {
            if (x < 0 || y < 0 || x + w > img.getWidth() || y + h > img.getHeight()) return null;
            return img.getSubimage(x, y, w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Map an armour {@link Material} to candidate texture paths under
     * {@code assets/minecraft/textures/}. Returns the modern (1.21.2+)
     * path first, then the legacy (≤1.21.1) path so the renderer works
     * across MC versions without needing per-version config.
     *
     * <p>Note that the <em>file</em> names in the new {@code humanoid/} layout
     * don't always match the Bukkit material name — e.g. {@code GOLDEN_HELMET}
     * lives at {@code humanoid/gold.png} and {@code TURTLE_HELMET} at
     * {@code humanoid/turtle_scute.png}. We canonicalise here.
     */
    private static List<String> armorTexturePaths(Material mat, int requiredLayer) {
        String name = mat.name().toLowerCase(Locale.ROOT);
        int idx = name.lastIndexOf('_');
        if (idx < 0) return java.util.Collections.emptyList();
        String matName = name.substring(0, idx);
        String slot = name.substring(idx + 1);

        int layer = "leggings".equals(slot) ? 2 : 1;
        if (layer != requiredLayer) return java.util.Collections.emptyList();

        // Canonical file basename in the new humanoid/ layout.
        String newName;
        switch (matName) {
            case "golden":     newName = "gold"; break;
            case "turtle":     newName = "turtle_scute"; break;
            case "leather":
            case "chainmail":
            case "iron":
            case "diamond":
            case "netherite":  newName = matName; break;
            default:           return java.util.Collections.emptyList();
        }

        // turtle_scute only ships as humanoid/ (helmet only). Don't try leggings.
        if ("turtle_scute".equals(newName) && layer == 2) {
            return java.util.Collections.emptyList();
        }

        if (layer == 2) {
            return Arrays.asList(
                    "entity/equipment/humanoid_leggings/" + newName,   // 1.21.2+
                    "models/armor/" + matName + "_layer_2");           // legacy
        } else {
            return Arrays.asList(
                    "entity/equipment/humanoid/" + newName,            // 1.21.2+
                    "models/armor/" + matName + "_layer_1");           // legacy
        }
    }

    // ---------- scaling ----------

    private static BufferedImage scaleByHeight(BufferedImage src, int targetHeight) {
        double scale = (double) targetHeight / src.getHeight();
        int targetWidth = (int) Math.round(src.getWidth() * scale);
        BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
