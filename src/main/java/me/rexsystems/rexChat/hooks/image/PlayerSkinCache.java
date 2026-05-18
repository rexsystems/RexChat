package me.rexsystems.rexChat.hooks.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Downloads and caches a 2D rendering of the player's skin from
 * <a href="https://mc-heads.net">mc-heads.net</a> for use as the character
 * preview drawn into the inventory image.
 *
 * <p>This is intentionally a 2D body image — replicating ICDA's full 3D
 * model rendering would require a non-trivial chunk of skin / armour /
 * pose / projection code. mc-heads.net already exposes a server-rendered
 * body sprite that matches the look closely enough at our 4× pixel-art
 * scale.
 */
public final class PlayerSkinCache {

    private static final int TIMEOUT_MS = 5_000;
    /** mc-heads.net body endpoint: 2D side-on body image. */
    private static final String BODY_URL = "https://mc-heads.net/body/%s/%d";

    private final File cacheDir;
    private final ConcurrentMap<String, BufferedImage> memCache = new ConcurrentHashMap<>();

    public PlayerSkinCache(File pluginDataFolder) {
        this.cacheDir = new File(pluginDataFolder, "textures/skins");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    /**
     * Get a body image for the given player UUID at approximately the
     * requested height (in pixels). Returns {@code null} if the download
     * fails — the renderer is expected to skip the character preview in that
     * case.
     */
    public BufferedImage getBody(UUID uuid, int approxHeight) {
        if (uuid == null) return null;
        int h = Math.max(8, Math.min(512, approxHeight));
        String key = uuid + "-" + h;
        BufferedImage cached = memCache.get(key);
        if (cached != null) return cached;

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

        BufferedImage img = download(uuid, h);
        if (img == null) return null;
        try {
            ImageIO.write(img, "png", diskFile);
        } catch (IOException ignored) {
        }
        memCache.put(key, img);
        return img;
    }

    private BufferedImage download(UUID uuid, int height) {
        String url = String.format(BODY_URL, uuid, height);
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
