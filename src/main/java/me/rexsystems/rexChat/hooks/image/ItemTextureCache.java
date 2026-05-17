package me.rexsystems.rexChat.hooks.image;

import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Downloads Minecraft item textures from a public CDN once and caches them on
 * disk under {@code <data folder>/textures/} so subsequent renders are fast and
 * offline-capable.
 *
 * <p>Tries the {@code item/} path first, then {@code block/}. If both fail, a
 * {@code MISSING} placeholder image is returned and cached so we don't hammer
 * the CDN for materials that don't have a texture (custom items, structure
 * blocks, air, etc.).
 */
public final class ItemTextureCache {

    /**
     * Default CDN. Uses {@code mcasset.cloud}'s {@code latest} alias which is
     * Cloudflare-cached and auto-points at the most recent extracted version.
     * Configurable via {@code chat-discord.images.texture-base-url}.
     */
    public static final String DEFAULT_BASE_URL =
            "https://mcasset.cloud/latest/assets/minecraft/textures/";

    /** Old default that pointed at the (empty) {@code master} branch — kept so we
     *  can silently migrate user configs that still reference it. */
    private static final String LEGACY_BROKEN_BASE_URL =
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/master/assets/minecraft/textures/";

    private static final BufferedImage MISSING = createMissingTexture();
    private static final int TIMEOUT_MS = 5_000;

    private final File cacheDir;
    private final String baseUrl;
    private final ConcurrentMap<String, BufferedImage> memCache = new ConcurrentHashMap<>();

    public ItemTextureCache(File pluginDataFolder, String baseUrl) {
        this.cacheDir = new File(pluginDataFolder, "textures");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        // Migrate the broken master-branch default that earlier builds shipped
        if (baseUrl == null || baseUrl.isEmpty() || baseUrl.equals(LEGACY_BROKEN_BASE_URL)) {
            this.baseUrl = DEFAULT_BASE_URL;
        } else {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * Get a 16x16 texture for the given material, downloading + caching as needed.
     * Returns the {@link #MISSING} placeholder if the material has no known texture.
     */
    public BufferedImage get(Material material) {
        if (material == null || material == Material.AIR) return null;
        String key = material.name().toLowerCase(Locale.ROOT);

        BufferedImage cached = memCache.get(key);
        if (cached != null) return cached == MISSING ? MISSING : cached;

        // Disk cache
        File diskFile = new File(cacheDir, key + ".png");
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                BufferedImage img = ImageIO.read(diskFile);
                if (img != null) {
                    memCache.put(key, img);
                    return img;
                }
            } catch (IOException ignored) {
            }
        }

        // Download
        BufferedImage img = downloadTexture(key);
        if (img == null) {
            memCache.put(key, MISSING);
            return MISSING;
        }
        try {
            ImageIO.write(img, "png", diskFile);
        } catch (IOException ignored) {
        }
        memCache.put(key, img);
        return img;
    }

    private BufferedImage downloadTexture(String key) {
        // Try item path, then block path
        String[] paths = { "item/", "block/" };
        for (String p : paths) {
            BufferedImage img = tryFetch(baseUrl + p + key + ".png");
            if (img != null) return img;
        }
        return null;
    }

    private BufferedImage tryFetch(String url) {
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

    private static BufferedImage createMissingTexture() {
        // The classic black/magenta missing-texture pattern (8x8 each, in a 16x16)
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 0, 0));
        g.fillRect(0, 0, 8, 8);
        g.fillRect(8, 8, 8, 8);
        g.setColor(new Color(255, 0, 220));
        g.fillRect(8, 0, 8, 8);
        g.fillRect(0, 8, 8, 8);
        g.dispose();
        return img;
    }

    public BufferedImage missing() {
        return MISSING;
    }
}
