package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
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
            String regex = "(?i)@" + java.util.regex.Pattern.quote(name);
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

        String lower = rawMessage.toLowerCase(Locale.ROOT);
        boolean byName = cfg.getBoolean("mention.by-name", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String pname = p.getName();
            String atNeedle = "@" + pname.toLowerCase(Locale.ROOT);
            boolean matched = lower.contains(atNeedle);
            if (!matched && byName) {
                try {
                    java.util.regex.Pattern pat = getNamePattern(pname);
                    matched = pat.matcher(rawMessage).find();
                } catch (Throwable ignored) { }
            }
            if (matched) {
                targets.add(p);
            }
        }
        return targets;
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
            String[] candidates = cc.rexsystems.rexChat.utils.MessageUtils.isLegacy()
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
        if (!isEnabled(cfg)) return message;
        if (message == null || message.isEmpty()) return message;

        String color = cfg.getString("mention.color", "&6");
        boolean byName = cfg.getBoolean("mention.by-name", true);
        String result = message;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            try {
                // Highlight @Name
                java.util.regex.Pattern atPat = getAtPattern(name);
                String replacementAt = java.util.regex.Matcher.quoteReplacement(color + "@" + name + "&r");
                result = atPat.matcher(result).replaceAll(replacementAt);
                // Highlight plain Name if enabled and not part of a larger word
                if (byName) {
                    java.util.regex.Pattern pat = getNamePattern(name);
                    String replacementName = java.util.regex.Matcher.quoteReplacement(color + "@" + name + "&r");
                    result = pat.matcher(result).replaceAll(replacementName);
                }
            } catch (Throwable ignored) { }
        }
        return result;
    }
}