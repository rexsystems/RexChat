package me.rexsystems.rexChat.utils;

import me.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for resolving configured preview token strings.
 */
public final class PreviewTokenLists {

    private PreviewTokenLists() {
    }

    public static List<String> itemTokens(FileConfiguration cfg) {
        return tokens(cfg, "chat-previews.tokens.item",
                "[item]", "[i]", "{item}", "{i}");
    }

    public static List<String> inventoryTokens(FileConfiguration cfg) {
        return tokens(cfg, "chat-previews.tokens.inventory",
                "[inventory]", "[inv]", "{inventory}", "{inv}");
    }

    public static List<String> enderChestTokens(FileConfiguration cfg) {
        return tokens(cfg, "chat-previews.tokens.enderchest",
                "[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}");
    }

    public static List<String> balanceTokens(FileConfiguration cfg) {
        return tokens(cfg, "chat-previews.tokens.balance",
                "[balance]", "[bal]", "[money]", "{balance}", "{bal}", "{money}");
    }

    public static List<String> coordsTokens(FileConfiguration cfg) {
        return tokens(cfg, "chat-previews.tokens.coords",
                "[coords]", "[here]", "{coords}", "{here}");
    }

    public static List<String> allProtectedTokens(RexChat plugin) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        List<String> all = new ArrayList<>();
        all.addAll(itemTokens(cfg));
        all.addAll(inventoryTokens(cfg));
        all.addAll(enderChestTokens(cfg));
        all.addAll(balanceTokens(cfg));
        all.addAll(coordsTokens(cfg));
        all.addAll(plugin.getCustomTokenRegistry().getAllAliases());
        return all;
    }

    public static List<String> tokens(FileConfiguration cfg, String path, String... defaults) {
        List<String> configured = cfg.getStringList(path);
        if (configured != null && !configured.isEmpty()) {
            List<String> out = new ArrayList<>(configured.size());
            for (String token : configured) {
                if (token != null && !token.isBlank()) {
                    out.add(token.toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }
        List<String> out = new ArrayList<>(defaults.length);
        for (String token : defaults) {
            out.add(token.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public static boolean containsAnyToken(String message, List<String> tokens) {
        if (message == null || message.isEmpty() || tokens == null || tokens.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static String replaceFirstToken(String message, List<String> tokens, String replacement) {
        if (message == null || tokens == null || tokens.isEmpty()) {
            return message;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        int foundPos = -1;
        String matched = null;
        for (String token : tokens) {
            int pos = lower.indexOf(token);
            if (pos != -1 && (foundPos == -1 || pos < foundPos)) {
                foundPos = pos;
                matched = token;
            }
        }
        if (foundPos == -1 || matched == null) {
            return message;
        }
        return message.substring(0, foundPos) + replacement + message.substring(foundPos + matched.length());
    }
}
