package cc.rexsystems.rexChat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.utils.SchedulerUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
// Use fully-qualified names for bungee HoverEvent/ClickEvent to avoid conflicts with kyori

public class MessageFormatter {
    private final RexChat plugin;

    public MessageFormatter(RexChat plugin) {
        this.plugin = plugin;
    }

    public void sendFormattedChat(Player sender, String message) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        boolean enabled = cfg.getBoolean("chat-format.enabled", true);

        if (!enabled) {
            // Fallback to vanilla handling
            return;
        }
        if (MessageUtils.isLegacy()) {
            // Legacy path: convert our MiniMessage token wrappers to Bungee components
            String rendered = buildRenderedString(sender, message);

            java.util.List<BaseComponent> out = new java.util.ArrayList<>();
            String s = rendered;
            // FIXED: use a global, case-insensitive, dot-all pattern to catch every wrapper
            java.util.regex.Pattern pat = java.util.regex.Pattern
                    .compile("(?is)<hover:show_text:'([^']*)'><click:run_command:'([^']*)'>(.*?)</click></hover>");
            java.util.regex.Matcher m = pat.matcher(s);
            int pos = 0;
            boolean hadToken = false;
            while (m.find()) {
                hadToken = true;
                String pre = s.substring(pos, m.start());
                if (!pre.isEmpty()) {
                    String preLegacy = ColorUtils.translateLegacyColors(pre);
                    BaseComponent[] preComp = TextComponent.fromLegacyText(preLegacy);
                    for (BaseComponent c : preComp)
                        out.add(c);
                }

                String hover = m.group(1).replace("''", "'");
                String cmd = m.group(2).replace("''", "'");
                String label = m.group(3).replace("''", "'");

                String labelLegacy = ColorUtils.translateLegacyColors(label);
                BaseComponent[] labelComps = TextComponent.fromLegacyText(labelLegacy);
                BaseComponent[] hoverComps = TextComponent.fromLegacyText(ColorUtils.translateLegacyColors(hover));
                for (BaseComponent lc : labelComps) {
                    lc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, hoverComps));
                    lc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, cmd));
                    out.add(lc);
                }

                pos = m.end();
            }
            String tail = s.substring(pos);
            if (!tail.isEmpty()) {
                String tailLegacy = ColorUtils.translateLegacyColors(tail);
                BaseComponent[] tailComp = TextComponent.fromLegacyText(tailLegacy);
                for (BaseComponent c : tailComp)
                    out.add(c);
            }

            BaseComponent[] base;
            if (!hadToken) {
                // No tokens found: apply global hover/click to entire message
                String legacy = ColorUtils.translateLegacyColors(rendered);
                base = TextComponent.fromLegacyText(legacy);

                if (isHoverEnabled(sender, cfg)) {
                    java.util.List<String> lines = getHoverLines(sender, cfg);
                    if (!lines.isEmpty()) {
                        String prefix = cfg.getString("messages.prefix", "");
                        String chatPrefix = PrefixUtils.getChatPrefix(sender, cfg);
                        String hoverJoined = String.join("\n", lines)
                                .replace("%rc_prefix%", prefix)
                                .replace("{prefix}", chatPrefix)
                                .replace("{player}", sender.getName())
                                .replace("{name}", sender.getName())
                                .replace("{display_name}", sender.getDisplayName())
                                .replace("{message}", message)
                                .replace("{world}", sender.getWorld().getName())
                                .replace("{health}", String.valueOf((int) Math.round(sender.getHealth())))
                                .replace("{max_health}", String.valueOf((int) Math.round(sender.getMaxHealth())))
                                .replace("{x}", String.valueOf(sender.getLocation().getBlockX()))
                                .replace("{y}", String.valueOf(sender.getLocation().getBlockY()))
                                .replace("{z}", String.valueOf(sender.getLocation().getBlockZ()))
                                .replace("{ping}", String.valueOf(getPing(sender)));
                        hoverJoined = PapiUtils.apply(sender, hoverJoined);
                        String hoverLegacy = ColorUtils.translateLegacyColors(hoverJoined);
                        BaseComponent[] hoverComp = TextComponent.fromLegacyText(hoverLegacy);

                        for (BaseComponent c : base) {
                            c.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, hoverComp));
                            c.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                    "/msg " + sender.getName() + " "));
                        }
                    } else {
                        for (BaseComponent c : base) {
                            c.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                    "/msg " + sender.getName() + " "));
                        }
                    }
                } else {
                    for (BaseComponent c : base) {
                        c.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                "/msg " + sender.getName() + " "));
                    }
                }
            } else {
                base = out.toArray(new BaseComponent[0]);
            }

            // Send to all online players
            Bukkit.getOnlinePlayers()
                    .forEach(p -> SchedulerUtils.runForPlayer(plugin, p, () -> p.spigot().sendMessage(base)));
            // Console: strip any remaining wrappers and print plain text
            String consolePlain = rendered
                    .replaceAll("(?is)<hover:show_text:'[^']*'><click:run_command:'[^']*'>(.*?)</click></hover>", "$1");
            MessageUtils.sendMessage(Bukkit.getConsoleSender(), consolePlain);
            return;
        }

        Component component = buildFormattedComponent(sender, message);
        Component finalComponent = component;
        Bukkit.getOnlinePlayers()
                .forEach(p -> SchedulerUtils.runForPlayer(plugin, p, () -> p.sendMessage(finalComponent)));
        // For console, also provide a plain-text fallback that strips token wrappers
        try {
            String consolePlain = buildRenderedString(sender, message)
                    .replaceAll("(?is)<hover:show_text:'[^']*'><click:run_command:'[^']*'>(.*?)</click></hover>", "$1");
            MessageUtils.sendMessage(Bukkit.getConsoleSender(), consolePlain);
        } catch (Throwable ignored) {
            Bukkit.getConsoleSender().sendMessage(finalComponent);
        }
    }

    public String buildRenderedString(Player sender, String message) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String prefix = cfg.getString("messages.prefix", "");
        String format = getFormatForPlayer(sender, cfg);
        String chatPrefix = PrefixUtils.getChatPrefix(sender, cfg);
        format = format.replace("%rc_prefix%", prefix);

        // Parse PAPI placeholders in the message if player has permission
        // This is done BEFORE color stripping so that if a placeholder returns color
        // codes,
        // they are subject to the color permission check below.
        if (sender.hasPermission("rexchat.chat.placeholders")) {
            message = cc.rexsystems.rexChat.utils.PapiUtils.apply(sender, message);
        }

        // Check if player has permission to use colors in chat
        if (!sender.hasPermission("rexchat.chatcolor")) {
            // No permission: strip ALL color codes completely (legacy, hex with &, hex without &)
            message = ColorUtils.stripColors(message);
        } else {
            // Has permission: apply preset color if player has one selected
            cc.rexsystems.rexChat.service.ChatColorManager colorManager = plugin.getChatColorManager();
            if (colorManager != null && colorManager.isEnabled()) {
                message = colorManager.applyPlayerColor(sender, message);
            }
        }

        // Apply emojis (configurable) and mention highlight after stripping user colors
        message = EmojiUtils.apply(sender, message, cfg);
        message = MentionUtils.applyHighlight(sender, message, cfg);
        // NOTE: [item]/[inventory] tokens are now processed via ItemTokenProcessor
        // AFTER Component creation
        // This is required because MiniMessage can't handle legacy colors inside hover
        // tags

        String world = sender.getWorld().getName();
        int ping = getPing(sender);
        double health = Math.max(0, sender.getHealth());
        double maxHealth = Math.max(0, sender.getMaxHealth());
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();

        String rendered = format
                .replace("{player}", sender.getName())
                .replace("{name}", sender.getName())
                .replace("{display_name}", sender.getDisplayName())
                .replace("{message}", message)
                .replace("{world}", world)
                .replace("{health}", String.valueOf((int) Math.round(health)))
                .replace("{max_health}", String.valueOf((int) Math.round(maxHealth)))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{ping}", String.valueOf(ping))
                .replace("{prefix}", chatPrefix)
                .replace("%luckperms_prefix%", chatPrefix);

        rendered = PapiUtils.apply(sender, rendered);
        return rendered;
    }

    public Component buildFormattedComponent(Player sender, String message) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String rendered = buildRenderedString(sender, message);
        Component component = ColorUtils.parseComponent(rendered);

        // If message contains preview token wrappers, do NOT apply global hover/click
        boolean hasPreviewWrappers = false;
        try {
            hasPreviewWrappers = java.util.regex.Pattern
                    .compile("(?is)<hover:show_text:'[^']*'><click:run_command:'[^']*'>.*?</click></hover>")
                    .matcher(rendered)
                    .find();
        } catch (Throwable ignored) {
        }

        String prefix = cfg.getString("messages.prefix", "");
        String world = sender.getWorld().getName();
        int ping = getPing(sender);
        double health = Math.max(0, sender.getHealth());
        double maxHealth = Math.max(0, sender.getMaxHealth());
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();
        String chatPrefix = PrefixUtils.getChatPrefix(sender, cfg);

        if (!hasPreviewWrappers && isHoverEnabled(sender, cfg)) {
            java.util.List<String> lines = getHoverLines(sender, cfg);
            if (!lines.isEmpty()) {
                String hoverJoined = String.join("\n", lines)
                        .replace("%rc_prefix%", prefix)
                        .replace("{prefix}", chatPrefix)
                        .replace("{player}", sender.getName())
                        .replace("{name}", sender.getName())
                        .replace("{display_name}", sender.getDisplayName())
                        .replace("{message}", message)
                        .replace("{world}", world)
                        .replace("{health}", String.valueOf((int) Math.round(health)))
                        .replace("{max_health}", String.valueOf((int) Math.round(maxHealth)))
                        .replace("{x}", String.valueOf(x))
                        .replace("{y}", String.valueOf(y))
                        .replace("{z}", String.valueOf(z))
                        .replace("{ping}", String.valueOf(ping));

                hoverJoined = PapiUtils.apply(sender, hoverJoined);
                Component hover = ColorUtils.parseComponent(hoverJoined);
                component = component.hoverEvent(HoverEvent.showText(hover));
            }
        }

        // Process [item] and [inventory] tokens with native hover events
        // This is done AFTER Component creation to avoid MiniMessage parsing issues
        ItemTokenProcessor tokenProcessor = new ItemTokenProcessor(plugin);
        component = tokenProcessor.processTokens(component, sender);

        // Check again if we have [item] tokens (now replaced with components that have
        // hover events)
        boolean hasItemTokens = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component).toLowerCase().contains("[item]") ||
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(component).toLowerCase().contains("[inventory]");

        // Apply a global click only when there are no token wrappers
        if (!hasPreviewWrappers && !hasItemTokens) {
            component = component.clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        }
        return component;
    }

    // Decorate only the player's display name (hover + click), to keep message
    // Component intact for signed chat
    public Component decorateDisplayName(Player sender, Component displayName) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        Component decorated = displayName;

        if (isHoverEnabled(sender, cfg)) {
            java.util.List<String> lines = getHoverLines(sender, cfg);
            if (!lines.isEmpty()) {
                String prefix = cfg.getString("messages.prefix", "");
                String chatPrefix = PrefixUtils.getChatPrefix(sender, cfg);
                String hoverJoined = String.join("\n", lines)
                        .replace("%rc_prefix%", prefix)
                        .replace("{prefix}", chatPrefix)
                        .replace("{player}", sender.getName())
                        .replace("{display_name}", sender.getDisplayName())
                        .replace("{world}", sender.getWorld().getName())
                        .replace("{health}", String.valueOf((int) Math.round(sender.getHealth())))
                        .replace("{max_health}", String.valueOf((int) Math.round(sender.getMaxHealth())))
                        .replace("{x}", String.valueOf(sender.getLocation().getBlockX()))
                        .replace("{y}", String.valueOf(sender.getLocation().getBlockY()))
                        .replace("{z}", String.valueOf(sender.getLocation().getBlockZ()))
                        .replace("{ping}", String.valueOf(getPing(sender)));
                hoverJoined = PapiUtils.apply(sender, hoverJoined);
                Component hover = ColorUtils.parseComponent(hoverJoined);
                decorated = decorated.hoverEvent(HoverEvent.showText(hover));
            }
        }

        decorated = decorated.clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        return decorated;
    }

    // --- Group-based formatting helpers ---
    private String getFormatForPlayer(Player sender, FileConfiguration cfg) {
        String defaultFormat = cfg.getString("chat-format.format", "%rc_prefix%&7{player}: &f{message}");
        String group = resolveGroupForPlayer(sender, cfg);
        if (group != null) {
            String path = "chat-format.groups." + group + ".format";
            if (cfg.isString(path)) {
                String fmt = cfg.getString(path);
                if (fmt != null && !fmt.isEmpty()) {
                    plugin.getLogUtils().debug("Using format for group '" + group + "' for player " + sender.getName());
                    return fmt;
                }
            } else {
                plugin.getLogUtils().debug("Group '" + group + "' found but no format at path: " + path);
            }
        } else {
            plugin.getLogUtils()
                    .debug("No matching group found for player " + sender.getName() + ", using default format");
        }

        // Also allow first matching group by permission if defined
        // IMPORTANT: Get fresh config section to avoid cache issues after reload
        if (cfg.isConfigurationSection("chat-format.groups")) {
            org.bukkit.configuration.ConfigurationSection groupsSection = cfg
                    .getConfigurationSection("chat-format.groups");
            if (groupsSection != null) {
                for (String key : groupsSection.getKeys(false)) {
                    String perm = cfg.getString("chat-format.groups." + key + ".permission", null);
                    if (perm != null && !perm.isEmpty() && sender.hasPermission(perm)) {
                        String fmt = cfg.getString("chat-format.groups." + key + ".format");
                        if (fmt != null && !fmt.isEmpty()) {
                            plugin.getLogUtils().debug("Using format for group '" + key
                                    + "' (matched by permission) for player " + sender.getName());
                            return fmt;
                        }
                    }
                }
            }
        }

        return defaultFormat;
    }

    private boolean isHoverEnabled(Player sender, FileConfiguration cfg) {
        String group = resolveGroupForPlayer(sender, cfg);
        if (group != null) {
            String path = "chat-format.groups." + group + ".hover.enabled";
            if (cfg.isSet(path))
                return cfg.getBoolean(path, true);
        }
        return cfg.getBoolean("chat-format.player.hover.enabled", true);
    }

    private java.util.List<String> getHoverLines(Player sender, FileConfiguration cfg) {
        String group = resolveGroupForPlayer(sender, cfg);
        if (group != null) {
            String path = "chat-format.groups." + group + ".hover.lines";
            if (cfg.isList(path))
                return cfg.getStringList(path);
        }
        return cfg.getStringList("chat-format.player.hover.lines");
    }

    private String resolveGroupForPlayer(Player sender, FileConfiguration cfg) {
        // Resolve primary group without PAPI: prefer LuckPerms, then Vault, then PAPI
        // fallback
        String primary = null;

        // Debug: log available groups in config (only once per config load to avoid
        // spam)
        // Removed excessive logging - will log only if group matching fails

        // LuckPerms v5 via reflection
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            // Try player adapter path to avoid missing user cache
            Object adapter = luckPerms.getClass().getMethod("getPlayerAdapter", Class.class)
                    .invoke(luckPerms, org.bukkit.entity.Player.class);
            Object meta = adapter.getClass().getMethod("getMetaData", org.bukkit.entity.Player.class)
                    .invoke(adapter, sender);
            Object pg = meta.getClass().getMethod("getPrimaryGroup").invoke(meta);
            if (pg instanceof String) {
                primary = (String) pg;
            }
            // Fallback to user manager if adapter didn't yield
            if (primary == null) {
                Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
                Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                        .invoke(userManager, sender.getUniqueId());
                if (user != null) {
                    Object pg2 = user.getClass().getMethod("getPrimaryGroup").invoke(user);
                    if (pg2 instanceof String)
                        primary = (String) pg2;
                }
            }
        } catch (Throwable ignored) {
        }

        // Vault Permission provider via reflection
        if (primary == null) {
            try {
                Class<?> permClass = Class.forName("net.milkbowl.vault.permission.Permission");
                Object registration = org.bukkit.Bukkit.getServicesManager().getRegistration(permClass);
                if (registration != null) {
                    Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                    try {
                        Object res = provider.getClass().getMethod("getPrimaryGroup", org.bukkit.entity.Player.class)
                                .invoke(provider, sender);
                        if (res instanceof String)
                            primary = (String) res;
                    } catch (NoSuchMethodException nsme) {
                        // Try (String world, String player) signature
                        Object res = provider.getClass().getMethod("getPrimaryGroup", String.class, String.class)
                                .invoke(provider, sender.getWorld().getName(), sender.getName());
                        if (res instanceof String)
                            primary = (String) res;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // PAPI fallback if available
        if (primary == null) {
            try {
                String lp = PapiUtils.apply(sender, "%luckperms_primary_group%");
                if (lp != null && !"%luckperms_primary_group%".equals(lp))
                    primary = lp;
            } catch (Throwable ignored) {
            }
            if (primary == null) {
                try {
                    String vault = PapiUtils.apply(sender, "%vault_primary_group%");
                    if (vault != null && !"%vault_primary_group%".equals(vault))
                        primary = vault;
                } catch (Throwable ignored) {
                }
            }
        }

        // If primary group is present and matches a key (case-insensitive), use it
        // IMPORTANT: Get fresh config section to avoid cache issues after reload
        if (primary != null && cfg.isConfigurationSection("chat-format.groups")) {
            String normalized = primary.toLowerCase();
            org.bukkit.configuration.ConfigurationSection groupsSection = cfg
                    .getConfigurationSection("chat-format.groups");
            if (groupsSection != null) {
                plugin.getLogUtils().debug("Player " + sender.getName() + " has primary group: " + primary
                        + " (normalized: " + normalized + ")");
                for (String key : groupsSection.getKeys(false)) {
                    if (key != null && key.equalsIgnoreCase(normalized)) {
                        plugin.getLogUtils().debug("Matched group '" + key + "' for player " + sender.getName());
                        return key;
                    }
                }
                plugin.getLogUtils().debug("Primary group '" + primary + "' not found in config groups. Available: "
                        + String.join(", ", groupsSection.getKeys(false)));
            }
        } else if (primary == null) {
            plugin.getLogUtils().debug("Could not resolve primary group for player " + sender.getName());
        }

        // Otherwise, only match via explicit permission from config
        // IMPORTANT: Get fresh config section to avoid cache issues after reload
        if (cfg.isConfigurationSection("chat-format.groups")) {
            org.bukkit.configuration.ConfigurationSection groupsSection = cfg
                    .getConfigurationSection("chat-format.groups");
            if (groupsSection != null) {
                for (String key : groupsSection.getKeys(false)) {
                    String explicitPerm = cfg.getString("chat-format.groups." + key + ".permission", null);
                    if (explicitPerm != null && !explicitPerm.isEmpty() && sender.hasPermission(explicitPerm)) {
                        plugin.getLogUtils().debug("Matched group '" + key + "' for player " + sender.getName()
                                + " via permission: " + explicitPerm);
                        return key;
                    }
                }
            }
        }

        // No match; use default format
        return null;
    }

    private int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Escape color codes so they display literally instead of formatting.
     * Changes &c to &&c (double ampersand escapes the formatting)
     */
    private String escapeColorCodes(String message) {
        if (message == null)
            return "";
        // Escape valid color codes: &[0-9a-fk-or] and &#RRGGBB
        message = message.replaceAll("&([0-9a-fk-orA-FK-OR])", "&&$1");
        message = message.replaceAll("&#([A-Fa-f0-9]{6})", "&&$1");
        return message;
    }

    /**
     * Preserve lone ampersands that aren't part of color codes.
     * This ensures "Tom & Jerry" stays as-is.
     */
    private String preserveLoneAmpersands(String message) {
        if (message == null || message.isEmpty())
            return "";

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '&') {
                // Check if this is a lone & (end of string, followed by space, or followed by
                // non-color char)
                if (i + 1 >= message.length()) {
                    // & at end of string - preserve it literally
                    result.append("\\&");
                    continue;
                }

                char next = message.charAt(i + 1);

                // Check if it's a valid color code
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) != -1) {
                    // It's a valid color code, keep as-is
                    result.append(c);
                } else if (next == '#' && i + 7 < message.length()) {
                    // Possible hex code &#RRGGBB
                    String potentialHex = message.substring(i + 2, Math.min(i + 8, message.length()));
                    if (potentialHex.matches("[A-Fa-f0-9]{6}")) {
                        result.append(c);
                    } else {
                        // Not a valid hex, escape it to preserve
                        result.append("\\&");
                        continue;
                    }
                } else if (next == '&') {
                    // Already escaped &&, keep both
                    result.append("&&");
                    i++; // Skip next &
                    continue;
                } else {
                    // Lone & not followed by valid color code - escape to preserve
                    result.append("\\&");
                    continue;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
