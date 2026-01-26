package cc.rexsystems.rexChat.service;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary access tokens for preview commands.
 * Prevents abuse of /rexchat item and /rexchat inv commands.
 */
public class PreviewAccessManager {

    private static class AccessToken {
        final String targetName;
        final String type; // "item" or "inv"
        final long expiresAt;

        AccessToken(String targetName, String type, long expiresAt) {
            this.targetName = targetName;
            this.type = type;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    // Map: viewer UUID -> target name -> token
    private final Map<UUID, Map<String, AccessToken>> tokens = new ConcurrentHashMap<>();
    private final long tokenDurationMs;

    public PreviewAccessManager(long tokenDurationSeconds) {
        this.tokenDurationMs = tokenDurationSeconds * 1000;
    }

    /**
     * Grant access for a viewer to see a target's preview.
     * Called when [item] or [inventory] is clicked in chat.
     */
    public void grantAccess(Player viewer, String targetName, String type) {
        UUID viewerId = viewer.getUniqueId();
        long expiresAt = System.currentTimeMillis() + tokenDurationMs;

        tokens.computeIfAbsent(viewerId, k -> new HashMap<>())
                .put(targetName.toLowerCase(), new AccessToken(targetName, type, expiresAt));
    }

    /**
     * Check if viewer has access to see target's preview.
     * Returns true if token exists and is valid, or if viewer is viewing self.
     */
    public boolean hasAccess(Player viewer, String targetName, String type) {
        // Always allow self-preview
        if (viewer.getName().equalsIgnoreCase(targetName)) {
            return true;
        }

        UUID viewerId = viewer.getUniqueId();
        Map<String, AccessToken> viewerTokens = tokens.get(viewerId);

        if (viewerTokens == null) {
            return false;
        }

        AccessToken token = viewerTokens.get(targetName.toLowerCase());
        if (token == null) {
            return false;
        }

        if (!token.isValid()) {
            viewerTokens.remove(targetName.toLowerCase());
            return false;
        }

        return token.type.equals(type);
    }

    /**
     * Remove expired tokens periodically to prevent memory leaks.
     */
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.values().forEach(viewerTokens -> viewerTokens.entrySet().removeIf(entry -> !entry.getValue().isValid()));
        tokens.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
