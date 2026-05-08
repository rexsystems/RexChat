package me.rexsystems.rexChat.utils;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Applies configurable chat emoji replacements.
 * Supports aliases like ":)" and shortcode ":smile:" mapping to the same replacement.
 */
public class EmojiUtils {

    public static String apply(Player sender, String message, FileConfiguration cfg) {
        if (message == null) return null;
        try {
            boolean enabled = cfg.getBoolean("chat-emoji.enabled", true);
            if (!enabled) return message;

            java.util.List<java.util.Map<?, ?>> emojis = cfg.getMapList("chat-emoji.emojis");
            if (emojis == null || emojis.isEmpty()) return message;

            String result = message;
            for (java.util.Map<?, ?> item : emojis) {
                if (item == null) continue;
                Object aliasesObj = item.get("aliases");
                Object replacementObj = item.get("replacement");
                if (!(aliasesObj instanceof List) || !(replacementObj instanceof String)) continue;
                @SuppressWarnings("unchecked")
                java.util.List<Object> aliases = (java.util.List<Object>) aliasesObj;
                String replacement = (String) replacementObj;
                if (replacement == null) replacement = "";

                for (Object aliasObj : aliases) {
                    if (!(aliasObj instanceof String)) continue;
                    String alias = (String) aliasObj;
                    if (alias == null || alias.isEmpty()) continue;
                    // Match only when token is surrounded by space/start/end (not part of longer sequence)
                    String escaped = java.util.regex.Pattern.quote(alias);
                    String regex = "(?<=^|\\s)" + escaped + "(?=\\s|$)";
                    result = result.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(replacement));
                }
            }

            return result;
        } catch (Throwable ignored) {
            return message;
        }
    }
}
