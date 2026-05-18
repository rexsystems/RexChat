package me.rexsystems.rexChat.hooks.image;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Downloads Minecraft item / block / GUI textures from a public CDN once and
 * caches them on disk under {@code <data folder>/textures/} so subsequent
 * renders are fast and offline-capable.
 *
 * <p>{@link #get(Material)} tries the {@code item/} path first then
 * {@code block/}. If both fail, a checkerboard {@link #MISSING} placeholder
 * is returned and cached so we don't hammer the CDN for materials that have
 * no rendered texture (custom items, structure blocks, air, etc.).
 *
 * <p>{@link #getRaw(String)} is the lower-level API used by other renderers
 * (e.g. {@link BlockIconRenderer}) to grab individual face textures by
 * explicit path (e.g. {@code "block/oak_log_top"}).
 *
 * <h2>Version-aware base URL</h2>
 * The base URL may contain a literal {@code {version}} placeholder; it is
 * resolved at construction time to either {@link Bukkit#getMinecraftVersion()}
 * or, when Bukkit isn't available (e.g. test harness), the
 * {@link #FALLBACK_VERSION}. So configuring
 * {@code https://assets.mcasset.cloud/{version}/assets/minecraft/textures/}
 * automatically targets the running server's MC version and "just works"
 * across server upgrades.
 */
public final class ItemTextureCache {

    /** Last-known-good MC version on mcasset.cloud, used when version detection fails. */
    public static final String FALLBACK_VERSION = "26.1.2";

    /**
     * Default CDN. Configurable via
     * {@code chat-discord.images.texture-base-url}. Mirrors what
     * InteractiveChat-DiscordSRV-Addon ships in its bundled resource pack:
     * the official MC textures pulled from mcasset.cloud's Cloudflare edge,
     * pinned to the running server's MC version via {@code {version}}.
     */
    public static final String DEFAULT_BASE_URL =
            "https://assets.mcasset.cloud/{version}/assets/minecraft/textures/";

    private static final BufferedImage MISSING = createMissingTexture();
    private static final int TIMEOUT_MS = 5_000;

    private final File cacheDir;
    private final String baseUrl;
    private final ConcurrentMap<String, BufferedImage> memCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JsonObject> jsonCache = new ConcurrentHashMap<>();
    private static final JsonObject MISSING_JSON = new JsonObject();
    private Consumer<String> debug = msg -> {};

    public ItemTextureCache(File pluginDataFolder, String baseUrl) {
        this.cacheDir = new File(pluginDataFolder, "textures");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        String resolved = baseUrl == null || baseUrl.isEmpty() ? DEFAULT_BASE_URL : baseUrl;
        this.baseUrl = resolveVersion(resolved);
    }

    /** Wire up a debug-log sink so this cache can report HTTP results. */
    public void setDebug(Consumer<String> sink) {
        this.debug = sink == null ? msg -> {} : sink;
    }

    /** Replace any {@code {version}} placeholder in the base URL with the running MC version. */
    private static String resolveVersion(String url) {
        if (url == null || !url.contains("{version}")) return url;
        String version = FALLBACK_VERSION;
        try {
            String mc = Bukkit.getMinecraftVersion();
            if (mc != null && !mc.isEmpty()) version = mc;
        } catch (Throwable ignored) {
            // Bukkit not available (tests / standalone tools); keep fallback.
        }
        return url.replace("{version}", version);
    }

    /** Visible for diagnostics — what URL we actually hit at runtime. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get a 16x16 texture for the given material, downloading + caching as
     * needed. Returns the {@link #MISSING} placeholder if the material has no
     * known texture under either {@code item/} or {@code block/}.
     */
    public BufferedImage get(Material material) {
        if (material == null || material == Material.AIR) return null;
        String key = material.name().toLowerCase(Locale.ROOT);

        BufferedImage cached = memCache.get(key);
        if (cached != null) return cached;

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

    /**
     * Lower-level fetch by explicit relative path (e.g.
     * {@code "block/oak_log_top"} → downloads
     * {@code <baseUrl>/block/oak_log_top.png}). Returns {@code null} if the
     * texture doesn't exist on the CDN.
     */
    public BufferedImage getRaw(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        String key = "raw:" + relativePath;
        BufferedImage cached = memCache.get(key);
        if (cached != null) return cached == MISSING ? null : cached;

        File diskFile = new File(cacheDir, "raw_" + relativePath.replace('/', '_') + ".png");
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

        String b = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        BufferedImage img = tryFetch(b + relativePath + ".png");
        if (img == null) {
            memCache.put(key, MISSING);
            return null;
        }
        try {
            ImageIO.write(img, "png", diskFile);
        } catch (IOException ignored) {
        }
        memCache.put(key, img);
        return img;
    }

    /**
     * Fetch + parse a JSON resource pack file (e.g.
     * {@code "models/block/dirt.json"}). Returns {@code null} if the file
     * is missing on the CDN. Both successful results and 404s are cached
     * (in memory + on disk) so repeat lookups are free.
     */
    public JsonObject getJson(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        String key = "json:" + relativePath;
        JsonObject cached = jsonCache.get(key);
        if (cached != null) return cached == MISSING_JSON ? null : cached;

        File diskFile = new File(cacheDir, "json_" + relativePath.replace('/', '_'));
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                String text = new String(Files.readAllBytes(Path.of(diskFile.toURI())),
                        StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                jsonCache.put(key, obj);
                return obj;
            } catch (Throwable ignored) {
            }
        }

        // Texture base URL ends in `assets/minecraft/textures/` — but model
        // JSONs live at `assets/minecraft/models/...`, NOT under textures/.
        // Strip the trailing `textures/` (if present) so the path resolves
        // against `assets/minecraft/`.
        String b = baseUrl;
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/textures")) b = b.substring(0, b.length() - "/textures".length());
        String url = b + "/" + relativePath;
        String text = tryFetchText(url);
        if (text == null) {
            jsonCache.put(key, MISSING_JSON);
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            try {
                Files.write(Path.of(diskFile.toURI()), text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
            jsonCache.put(key, obj);
            return obj;
        } catch (Throwable t) {
            debug.accept("json: parse error " + t.getMessage() + " - " + url);
            jsonCache.put(key, MISSING_JSON);
            return null;
        }
    }

    private String tryFetchText(String url) {
        try {
            URL u = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "RexChat/1.0 (+https://rexsystems.me)");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) {
                debug.accept("json: HTTP " + code + " - " + url);
                conn.disconnect();
                return null;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                conn.disconnect();
                debug.accept("json: OK - " + url);
                return sb.toString();
            }
        } catch (Throwable t) {
            debug.accept("json: ERR " + t.getClass().getSimpleName() + " " + url);
            return null;
        }
    }

    private BufferedImage downloadTexture(String key) {
        // Try item path, then block path
        String[] paths = { "item/", "block/" };
        for (String p : paths) {
            BufferedImage img = tryFetch(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + p + key + ".png");
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
                debug.accept("texture: HTTP " + code + " - " + url);
                conn.disconnect();
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                conn.disconnect();
                if (img != null) {
                    debug.accept("texture: OK " + img.getWidth() + "x" + img.getHeight()
                            + " - " + url);
                }
                return img;
            }
        } catch (Throwable t) {
            debug.accept("texture: ERR " + t.getClass().getSimpleName() + " " + url);
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
