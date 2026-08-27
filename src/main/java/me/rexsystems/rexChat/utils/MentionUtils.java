package me.rexsystems.rexChat.utils;

import me.rexsystems.rexChat.RexChat;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utilities for @mention parsing, highlighting, and playing configurable sounds.
 */
public class MentionUtils {

    // Cache compiled patterns per player name to avoid recompilation on every message
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> NAME_PATTERN_CACHE 
        = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> AT_PATTERN_CACHE 
        = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.util.regex.Pattern getNamePattern(String playerName) {
        return NAME_PATTERN_CACHE.computeIfAbsent(playerName, name -> {
            String regex = "(?i)(?<!\\w)" + java.util.regex.Pattern.quote(name) + "(?!\\w)";
            return java.util.regex.Pattern.compile(regex);
        });
    }

    private static java.util.regex.Pattern getAtPattern(String playerName) {
        return AT_PATTERN_CACHE.computeIfAbsent(playerName, name -> {
            String regex = "(?i)@" + java.util.regex.Pattern.quote(name) + "(?!\\w)";
            return java.util.regex.Pattern.compile(regex);
        });
    }

    /**
     * Clear cached patterns (call on player join/quit to keep cache fresh).
     */
    public static void invalidateCache() {
        NAME_PATTERN_CACHE.clear();
        AT_PATTERN_CACHE.clear();
    }

    public static boolean isEnabled(FileConfiguration cfg) {
        return cfg.getBoolean("mention.enabled", true);
    }

    public static Set<Player> findMentionedPlayers(String rawMessage, FileConfiguration cfg) {
        Set<Player> targets = new HashSet<>();
        if (!isEnabled(cfg)) return targets;
        if (rawMessage == null || rawMessage.isEmpty()) return targets;

        // Names inside MiniMessage / chat tags like <head:Player> must not count as mentions
        String searchable = maskAngleBracketTags(rawMessage);

        boolean byName = cfg.getBoolean("mention.by-name", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String pname = p.getName();
            boolean matched = false;
            try {
                java.util.regex.Pattern atPat = getAtPattern(pname);
                matched = atPat.matcher(searchable).find();
            } catch (Throwable ignored) { }
            if (!matched && byName) {
                try {
                    java.util.regex.Pattern pat = getNamePattern(pname);
                    matched = pat.matcher(searchable).find();
                } catch (Throwable ignored) { }
            }
            if (matched) {
                targets.add(p);
            }
        }
        return targets;
    }

    /**
     * Replaces content of {@code <...>} regions with spaces (same length) so
     * player-name patterns do not match inside MiniMessage / chat tags.
     */
    static String maskAngleBracketTags(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inTag = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inTag && c == '<') {
                inTag = true;
                out.append(' ');
            } else if (inTag && c == '>') {
                inTag = false;
                out.append(' ');
            } else if (inTag) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static void playMentionEffects(RexChat plugin, Player sender, Set<Player> targets) {
        if (targets == null || targets.isEmpty()) return;
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        if (!isEnabled(cfg)) return;

        // Prevent self-mention triggers if enabled (default: true)
        boolean preventSelf = cfg.getBoolean("mention.prevent-self", true);
        java.util.Set<Player> effectiveTargets = new java.util.HashSet<>(targets);
        if (preventSelf && sender != null) {
            effectiveTargets.remove(sender);
        }

        // Sound
        boolean soundEnabled = cfg.getBoolean("mention.sound.enabled", true);
        String soundName = cfg.getString("mention.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float volume = (float) cfg.getDouble("mention.sound.volume", 0.8D);
        float pitch = (float) cfg.getDouble("mention.sound.pitch", 1.2D);

        Sound resolved = null;
        if (soundEnabled) {
            // Try configured sound, then sensible fallbacks per version
            String[] candidates = me.rexsystems.rexChat.utils.MessageUtils.isLegacy()
                    ? new String[]{soundName, "LEVEL_UP", "NOTE_PLING"}
                    : new String[]{soundName, "ENTITY_EXPERIENCE_ORB_PICKUP"};
            for (String cand : candidates) {
                try {
                    resolved = Sound.valueOf(cand);
                    if (resolved != null) break;
                } catch (Throwable ignored) { }
            }
        }

        // Capture effectively-final references for lambda usage
        final boolean soundEnabledLocal = soundEnabled;
        final Sound soundResolved = resolved;
        final float vol = volume;
        final float pit = pitch;
        final FileConfiguration cfgLocal = cfg;
        final Player senderLocal = sender;

        for (Player target : effectiveTargets) {
            SchedulerUtils.runForPlayer(plugin, target, () -> {
                if (soundEnabledLocal && soundResolved != null) {
                    try {
                        target.playSound(target.getLocation(), soundResolved, vol, pit);
                    } catch (Throwable ignored) { }
                }
                boolean notifyTarget = cfgLocal.getBoolean("mention.notify.target", true);
                if (notifyTarget) {
                    String msg = plugin.getConfigManager().getConfig().getString("messages.mention.target", null);
                    if (msg != null && !msg.trim().isEmpty()) {
                        String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                        String built = msg.replace("%rc_prefix%", prefix)
                                          .replace("{sender}", senderLocal.getName())
                                          .replace("{display_name}", senderLocal.getDisplayName());
                        MessageUtils.sendMessage(target, built);
                    }

                    // Optional title notification (version-aware, includes 1.8 fallback)
                    boolean titleEnabled = cfgLocal.getBoolean("mention.title.enabled", true);
                    if (titleEnabled) {
                        String title = cfgLocal.getString("mention.title.title", "&6Mention!");
                        String subtitle = cfgLocal.getString("mention.title.subtitle", "&eYou were mentioned by &6{sender}");
                        int fadeIn = cfgLocal.getInt("mention.title.fade-in", 5);
                        int stay = cfgLocal.getInt("mention.title.stay", 40);
                        int fadeOut = cfgLocal.getInt("mention.title.fade-out", 10);
                        title = title.replace("{sender}", senderLocal.getName()).replace("{display_name}", senderLocal.getDisplayName());
                        subtitle = subtitle.replace("{sender}", senderLocal.getName()).replace("{display_name}", senderLocal.getDisplayName());

                        TitleUtils.sendTitle(target, title, subtitle, fadeIn, stay, fadeOut);
                    }
                }
            });
        }

        boolean notifySender = cfg.getBoolean("mention.notify.sender", false);
        if (notifySender && sender != null) {
            StringBuilder names = new StringBuilder();
            for (Player t : effectiveTargets) {
                if (names.length() > 0) names.append(", ");
                names.append(t.getName());
            }
            String msg = plugin.getConfigManager().getConfig().getString("messages.mention.sender", null);
            if (msg != null && !msg.trim().isEmpty()) {
                String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                String built = msg.replace("%rc_prefix%", prefix)
                                  .replace("{targets}", names.toString());
                SchedulerUtils.runForPlayer(plugin, sender, () -> MessageUtils.sendMessage(sender, built));
            }
        }
    }

    public static String applyHighlight(Player sender, String message, FileConfiguration cfg) {
        return applyHighlight(sender, message, cfg, "&r");
    }

    /**
     * Apply mention highlighting with a custom color to restore after the highlight.
     * This prevents chat colors from being reset to white after a mention.
     *
     * @param sender       the player who sent the message
     * @param message      the chat message
     * @param cfg          plugin configuration
     * @param restoreColor the color code to restore after the mention highlight (e.g. "&a" or "&r")
     * @return the message with mention highlights applied
     */
    public static String applyHighlight(Player sender, String message, FileConfiguration cfg, String restoreColor) {
        if (!isEnabled(cfg)) return message;
        if (message == null || message.isEmpty()) return message;

        String color = cfg.getString("mention.color", "&6");
        boolean byName = cfg.getBoolean("mention.by-name", true);
        String result = message;
        // Sort players by name length descending to prevent partial matches
        // (e.g. "Rex" matching inside "@RexStar" before "RexStar" is processed)
        java.util.List<Player> sorted = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
        sorted.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

        // Use placeholders during processing to prevent already-highlighted mentions
        // from being matched again by shorter name patterns
        java.util.List<String[]> placeholders = new java.util.ArrayList<>();
        int placeholderIndex = 0;

        for (Player p : sorted) {
            String name = p.getName();
            try {
                // Match against tag-masked text so <head:Name> is ignored
                String searchable = maskAngleBracketTags(result);

                java.util.regex.Pattern atPat = getAtPattern(name);
                java.util.regex.Matcher atMatcher = atPat.matcher(searchable);
                boolean hadAtMention = atMatcher.find();
                if (hadAtMention) {
                    String placeholder = "\u0000MENTION" + (placeholderIndex++) + "\u0000";
                    String highlighted = color + "@" + name + restoreColor;
                    placeholders.add(new String[]{placeholder, highlighted});
                    result = replaceMatchesOutsideTags(result, atPat, placeholder);
                }
                // Highlight plain Name if enabled and not part of a larger word
                // Skip if @Name was already highlighted to avoid double-processing
                if (byName && !hadAtMention) {
                    java.util.regex.Pattern pat = getNamePattern(name);
                    if (pat.matcher(searchable).find()) {
                        String placeholder = "\u0000MENTION" + (placeholderIndex++) + "\u0000";
                        // Keep the typed name (no forced @) when matching by bare name
                        String highlighted = color + name + restoreColor;
                        placeholders.add(new String[]{placeholder, highlighted});
                        result = replaceMatchesOutsideTags(result, pat, placeholder);
                    }
                }
            } catch (Throwable ignored) { }
        }

        // Replace all placeholders with their final highlighted values
        for (String[] entry : placeholders) {
            result = result.replace(entry[0], entry[1]);
        }

        return result;
    }

    /**
     * Replaces pattern matches in {@code input} only where the match is not
     * inside an angle-bracket tag. Masking is length-preserving so indices align.
     */
    private static String replaceMatchesOutsideTags(String input, java.util.regex.Pattern pattern, String replacement) {
        String masked = maskAngleBracketTags(input);
        java.util.regex.Matcher matcher = pattern.matcher(masked);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder out = new StringBuilder();
        int last = 0;
        matcher.reset();
        while (matcher.find()) {
            out.append(input, last, matcher.start());
            out.append(replacement);
            last = matcher.end();
        }
        out.append(input, last, input.length());
        return out.toString();
    }

}