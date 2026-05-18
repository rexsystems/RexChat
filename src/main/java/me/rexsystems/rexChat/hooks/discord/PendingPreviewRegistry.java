package me.rexsystems.rexChat.hooks.discord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Static keyed registry of {@link PendingPreview} instances awaiting delivery.
 *
 * <p>Mirrors {@code OutboundToDiscordEvents.DATA} in
 * InteractiveChat-DiscordSRV-Addon — but with our own marker namespace so the
 * two plugins can coexist on the same server.
 *
 * <p>Each preview is registered with a fresh integer id; that id is encoded
 * into the chat message as {@code <RXC=ID>} before DiscordSRV relays it. When
 * the message comes back via JDA, {@link DiscordJDAListener} matches the
 * {@link #MARKER_PATTERN}, removes the marker(s), pops the matching previews
 * and attaches them.
 */
public final class PendingPreviewRegistry {

    /** Marker prefix; ICDA uses {@code <ICD=}, we use {@code <RXC=} to avoid collisions. */
    public static final String MARKER_PREFIX = "<RXC=";
    public static final String MARKER_SUFFIX = ">";
    /** Matches a single marker (e.g. {@code <RXC=42>}) and captures the id. */
    public static final Pattern MARKER_PATTERN = Pattern.compile("<RXC=(\\d+)>");

    /** Previews older than this without delivery are evicted. */
    public static final long DEFAULT_EXPIRY_MS = 60_000L;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final ConcurrentMap<Integer, PendingPreview> DATA = new ConcurrentHashMap<>();

    private PendingPreviewRegistry() {}

    /** Register a preview and return the id to embed in the chat message. */
    public static int register(PendingPreview preview) {
        int id = NEXT_ID.incrementAndGet();
        DATA.put(id, preview);
        return id;
    }

    /** Build the marker string for the given id. */
    public static String marker(int id) {
        return MARKER_PREFIX + id + MARKER_SUFFIX;
    }

    /** Pop a preview by id (returns {@code null} if absent / already consumed). */
    public static PendingPreview poll(int id) {
        return DATA.remove(id);
    }

    /** Inspect (without removing) — used for diagnostics only. */
    public static PendingPreview peek(int id) {
        return DATA.get(id);
    }

    /**
     * Remove + return all previews currently registered with the given ids,
     * preserving insertion order of {@code ids}.
     */
    public static List<PendingPreview> drain(List<Integer> ids) {
        List<PendingPreview> out = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            PendingPreview p = DATA.remove(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Drop expired entries. Should be called periodically. */
    public static int cleanupExpired() {
        long cutoff = System.currentTimeMillis() - DEFAULT_EXPIRY_MS;
        int removed = 0;
        Iterator<Map.Entry<Integer, PendingPreview>> it = DATA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, PendingPreview> e = it.next();
            if (e.getValue().getCreatedAtMs() < cutoff) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Visible for diagnostics. */
    public static int size() {
        return DATA.size();
    }

    /** Forget every entry. Used on plugin shutdown. */
    public static void clear() {
        DATA.clear();
    }
}
