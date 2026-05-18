package me.rexsystems.rexChat.hooks.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Downloads and caches Minecraft GUI sprite-sheet textures (e.g.
 * {@code gui/container/inventory.png}) from mcasset.cloud.
 *
 * <p>These are the chrome textures used by {@link InventoryImageRenderer}
 * to draw inventory / chest backgrounds that look identical to vanilla
 * Minecraft, in the same way InteractiveChat-DiscordSRV-Addon does it via
 * its packaged resource pack.
 *
 * <p>Files are cached on disk under {@code <plugin folder>/textures/gui/}
 * and in memory after first read.
 */
public final class GuiTextureCache {

    private static final int TIMEOUT_MS = 5_000;

    private final File cacheDir;
    private final String baseUrl;
    private final ConcurrentMap<String, BufferedImage> memCache = new ConcurrentHashMap<>();

    public GuiTextureCache(File pluginDataFolder, String baseUrl) {
        this.cacheDir = new File(pluginDataFolder, "textures/gui");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        this.baseUrl = baseUrl == null || baseUrl.isEmpty()
                ? ItemTextureCache.DEFAULT_BASE_URL : baseUrl;
    }

    /**
     * Get the GUI sprite-sheet for the given path (e.g.
     * {@code container/inventory}). Returns {@code null} if it can't be
     * downloaded — the renderer is expected to fall back to a flat colour.
     */
    public BufferedImage get(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;

        BufferedImage cached = memCache.get(relativePath);
        if (cached != null) return cached;

        File diskFile = new File(cacheDir, relativePath.replace('/', '_') + ".png");
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                BufferedImage img = ImageIO.read(diskFile);
                if (img != null) {
                    memCache.put(relativePath, img);
                    return img;
                }
            } catch (IOException ignored) {
            }
        }

        BufferedImage img = download(relativePath);
        if (img == null) return null;

        try {
            File parent = diskFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            ImageIO.write(img, "png", diskFile);
        } catch (IOException ignored) {
        }
        memCache.put(relativePath, img);
        return img;
    }

    private BufferedImage download(String relativePath) {
        String b = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String url = b + "gui/" + relativePath + ".png";
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
}
