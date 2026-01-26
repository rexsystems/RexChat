package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.utils.MessageUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replaces chat tokens like [item]/[i] and [inventory]/[inv] with
 * MiniMessage-wrapped segments that have hover + click actions.
 * Click opens a preview via /rexchat subcommands (Folia-safe).
 */
public final class PreviewTokenUtils {
    private PreviewTokenUtils() {
    }

    public static String apply(Player sender, String message, RexChat plugin) {
        if (message == null || message.isEmpty())
            return message;
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        boolean legacy = MessageUtils.isLegacy();

        boolean enabled = cfg.getBoolean("chat-previews.enabled",
                cfg.getBoolean("features.chat-previews.enabled", true));
        if (!enabled)
            return message;

        List<String> itemTokens = getTokensWithFallback(cfg,
                "chat-previews.tokens.item",
                "features.chat-previews.tokens.item",
                "[item]", "[i]", "{item}", "{i}");
        List<String> invTokens = getTokensWithFallback(cfg,
                "chat-previews.tokens.inventory",
                "features.chat-previews.tokens.inventory",
                "[inventory]", "[inv]", "{inventory}", "{inv}");

        String itemHover = cfg.getString("messages.preview.item.hover", "&7Click to view {player}'s item");
        String invHover = cfg.getString("messages.preview.inventory.hover", "&7Click to view {player}'s inventory");

        // Remove prefix token in hover (requested), and resolve simple placeholders
        itemHover = (itemHover == null ? "" : itemHover)
                .replace("%rc_prefix%", "")
                .replace("{player}", sender.getName())
                .replace("{display_name}", sender.getDisplayName());
        invHover = (invHover == null ? "" : invHover)
                .replace("%rc_prefix%", "")
                .replace("{player}", sender.getName())
                .replace("{display_name}", sender.getDisplayName());

        // Apply PAPI after basic replacements
        itemHover = PapiUtils.apply(sender, itemHover);
        invHover = PapiUtils.apply(sender, invHover);

        // Keep hover text simple/plain (no color codes in hover)
        String itemHoverMini = ColorUtils.stripColors(itemHover);
        String invHoverMini = ColorUtils.stripColors(invHover);

        // Prepare dynamic labels
        // Build labels with templates (colors configurable via config)
        boolean handEmpty = isHandEmpty(sender);
        String invLabelTemplate = cfg.getString("messages.preview.inventory.label-template", "&7[&fInventory&7]");
        String invLabel = invLabelTemplate; // simple static label for inventory

        boolean hasItemToken = containsAnyToken(message, itemTokens);
        if (handEmpty && hasItemToken) {
            // Always render Air as plain label (no hover/click) on all versions
            String rawItemLabel = "Air";
            String itemLabelTemplate = cfg.getString("messages.preview.item.label-template", "&7[&f{label}&7]");
            String itemLabel = itemLabelTemplate.replace("{label}", rawItemLabel);
            message = replaceTokens(message, itemTokens, content -> itemLabel);
        } else if (hasItemToken) {
            // Get item from hand
            org.bukkit.inventory.ItemStack hand = sender.getInventory().getItemInMainHand();

            // Store item with UNIQUE ID so multiple [item] tokens work
            String itemId = plugin.getItemSnapshotManager().storeItem(hand, sender.getName());

            // Get display name WITH EXACT COLORS from item
            String rawItemLabel = buildItemLabel(sender);

            // Convert § codes to MiniMessage format
            cc.rexsystems.rexChat.service.ConfigColorConverter converter = new cc.rexsystems.rexChat.service.ConfigColorConverter(
                    plugin);
            String rawItemLabelMini = converter.convertColors(rawItemLabel);

            String itemLabelTemplate = cfg.getString("messages.preview.item.label-template", "{label}");
            String itemLabelTemplateMini = converter.convertColors(itemLabelTemplate);
            String itemLabelMini = itemLabelTemplateMini.replace("{label}", rawItemLabelMini);

            if (legacy) {
                String itemLabel = itemLabelTemplate.replace("{label}", rawItemLabel);
                message = replaceTokens(message, itemTokens, content -> itemLabel);
            } else {
                // Use unique ID in command so correct item is shown
                message = replaceTokens(message, itemTokens,
                        buildItemWrapperWithId(sender.getName(), itemHoverMini, itemLabelMini, itemId));
            }
        }

        // Wrap inventory tokens only on modern clients; legacy gets plain label
        boolean hasInvToken = containsAnyToken(message, invTokens);
        if (hasInvToken) {
            // Store inventory with UNIQUE ID so each [inventory] works independently
            String invId = plugin.getInventorySnapshotService().storeSnapshotWithId(sender);

            if (legacy) {
                message = replaceTokens(message, invTokens, content -> invLabel);
            } else {
                // Use unique ID in command
                message = replaceTokens(message, invTokens,
                        buildInvWrapperWithId(sender.getName(), invHoverMini, invLabel, invId));
            }
        }

        return message;
    }

    private static List<String> getTokens(FileConfiguration cfg, String path, String... defaults) {
        List<String> out = new ArrayList<>();
        List<String> configured = cfg.getStringList(path);
        if (configured != null && !configured.isEmpty()) {
            for (String s : configured) {
                if (s == null)
                    continue;
                out.add(s.toLowerCase(Locale.ROOT));
            }
        } else {
            for (String d : defaults)
                out.add(d.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> getTokensWithFallback(FileConfiguration cfg, String primaryPath, String fallbackPath,
            String... defaults) {
        List<String> primary = cfg.getStringList(primaryPath);
        if (primary != null && !primary.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String s : primary)
                if (s != null)
                    out.add(s.toLowerCase(Locale.ROOT));
            return out;
        }
        List<String> fallback = cfg.getStringList(fallbackPath);
        if (fallback != null && !fallback.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String s : fallback)
                if (s != null)
                    out.add(s.toLowerCase(Locale.ROOT));
            return out;
        }
        List<String> out = new ArrayList<>();
        for (String d : defaults)
            out.add(d.toLowerCase(Locale.ROOT));
        return out;
    }

    private static String replaceTokens(String message, List<String> tokens, TokenWrapper wrapper) {
        String lower = message.toLowerCase(Locale.ROOT);
        int idx = 0;
        while (true) {
            int foundPos = -1;
            String matched = null;
            for (String t : tokens) {
                int p = lower.indexOf(t, idx);
                if (p != -1 && (foundPos == -1 || p < foundPos)) {
                    foundPos = p;
                    matched = t;
                }
            }
            if (foundPos == -1 || matched == null)
                break;
            int end = foundPos + matched.length();
            String original = message.substring(foundPos, end);
            String replacement = wrapper.wrap(original);
            message = message.substring(0, foundPos) + replacement + message.substring(end);
            lower = message.toLowerCase(Locale.ROOT);
            idx = foundPos + replacement.length();
        }
        return message;
    }

    private static boolean containsAnyToken(String message, List<String> tokens) {
        if (message == null || message.isEmpty() || tokens == null || tokens.isEmpty())
            return false;
        String lower = message.toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            if (lower.contains(t))
                return true;
        }
        return false;
    }

    private static TokenWrapper buildItemWrapper(String playerName, String hoverText, String label) {
        // Use run_command to open item preview - access token will be granted via chat
        // listener
        String cmd = "/rexchat item " + playerName;
        String hoverEscaped = escapeForMiniMessage(hoverText);
        // Label uses legacy color codes which MiniMessage will parse correctly
        return content -> "<hover:show_text:'" + hoverEscaped + "'><click:run_command:'" + cmd + "'>" + label
                + "</click></hover>";
    }

    /**
     * Build item wrapper with unique item ID so multiple items can be viewed.
     */
    private static TokenWrapper buildItemWrapperWithId(String playerName, String hoverText, String label,
            String itemId) {
        // Use unique ID in command so correct item is shown when clicked
        String cmd = "/rexchat viewitem " + itemId;
        String hoverEscaped = escapeForMiniMessage(hoverText);
        return content -> "<hover:show_text:'" + hoverEscaped + "'><click:run_command:'" + cmd + "'>" + label
                + "</click></hover>";
    }

    /**
     * Build inventory wrapper with unique ID.
     */
    private static TokenWrapper buildInvWrapperWithId(String playerName, String hoverText, String label, String invId) {
        String cmd = "/rexchat viewinv " + invId;
        String hoverEscaped = escapeForMiniMessage(hoverText);
        return content -> "<hover:show_text:'" + hoverEscaped + "'><click:run_command:'" + cmd + "'>" + label
                + "</click></hover>";
    }

    private static TokenWrapper buildInvWrapper(String playerName, String hoverText, String label) {
        String cmd = "/rexchat inv " + playerName;
        String hoverEscaped = escapeForMiniMessage(hoverText);
        // Label uses legacy color codes which MiniMessage will parse correctly
        return content -> "<hover:show_text:'" + hoverEscaped + "'><click:run_command:'" + cmd + "'>" + label
                + "</click></hover>";
    }

    /**
     * Convert a label with legacy color codes to MiniMessage format.
     * This is necessary because MiniMessage 4.20+ rejects legacy § codes inside
     * tags.
     */
    private static String convertToMiniMessageFormat(String label) {
        if (label == null)
            return "";

        // First: convert & codes to § codes
        label = org.bukkit.ChatColor.translateAlternateColorCodes('&', label);

        // Second: convert #RRGGBB format (without &) to MiniMessage hex tags
        label = label.replaceAll("#([A-Fa-f0-9]{6})", "<#$1>");

        // Third: convert legacy RGB format (§x§R§R§G§G§B§B) to hex format
        java.util.regex.Pattern legacyRGB = java.util.regex.Pattern.compile("§x(?:§[0-9a-fA-F]){6}");
        java.util.regex.Matcher rgbMatcher = legacyRGB.matcher(label);
        StringBuffer rgbBuffer = new StringBuffer();
        while (rgbMatcher.find()) {
            String match = rgbMatcher.group();
            String hex = match.substring(2).replace("§", "");
            rgbMatcher.appendReplacement(rgbBuffer, "<#" + hex + ">");
        }
        rgbMatcher.appendTail(rgbBuffer);
        label = rgbBuffer.toString();

        // Third: convert legacy § color codes to MiniMessage tags
        label = label
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§k", "<obfuscated>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");

        return label;
    }

    private static String escapeForMiniMessage(String s) {
        if (s == null)
            return "";
        // MiniMessage uses doubled single-quotes for escaping inside quoted params
        return s.replace("'", "''");
    }

    private static String buildItemLabel(Player sender) {
        try {
            org.bukkit.inventory.ItemStack hand = sender.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == org.bukkit.Material.AIR || hand.getAmount() <= 0) {
                return "Air";
            }

            // Use Paper API to get display name as Adventure Component
            net.kyori.adventure.text.Component displayComponent = hand.displayName();

            // Serialize to legacy § format (preserves colors without MiniMessage tags)
            String displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection()
                    .serialize(displayComponent);

            // Paper displayName() includes brackets like [Item Name] - strip them
            if (displayName.startsWith("[") || displayName.contains("[")) {
                // Remove first [ and last ] while preserving color codes around them
                displayName = displayName.replaceFirst("\\[", "");
                // Remove last ]
                int lastBracket = displayName.lastIndexOf(']');
                if (lastBracket >= 0) {
                    displayName = displayName.substring(0, lastBracket) +
                            displayName.substring(lastBracket + 1);
                }
            }

            // Strip any leftover < > characters that break MiniMessage
            displayName = displayName.replace("<", "").replace(">", "");

            int amount = hand.getAmount();
            return displayName + " x" + amount;
        } catch (Throwable t) {
            return "Item";
        }
    }

    /**
     * Build item label as PLAIN TEXT (no colors) for safe MiniMessage usage.
     */
    private static String buildItemLabelPlainText(Player sender) {
        try {
            org.bukkit.inventory.ItemStack hand = sender.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == org.bukkit.Material.AIR || hand.getAmount() <= 0) {
                return "Air";
            }

            // Use Paper API to get display name as Adventure Component
            net.kyori.adventure.text.Component displayComponent = hand.displayName();

            // Serialize to PLAIN TEXT (no colors - safe for MiniMessage)
            String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText()
                    .serialize(displayComponent);

            int amount = hand.getAmount();
            return displayName + " x" + amount;
        } catch (Throwable t) {
            return "Item";
        }
    }

    /**
     * Remove MiniMessage special characters to prevent parsing conflicts.
     * Simply removes < and > as they would break the hover/click wrappers.
     */
    private static String escapeMiniMessageChars(String text) {
        if (text == null)
            return "";
        // Simply remove < and > to avoid any MiniMessage conflicts
        return text
                .replace("<", "")
                .replace(">", "");
    }

    private static boolean isHandEmpty(Player sender) {
        try {
            org.bukkit.inventory.ItemStack hand = sender.getInventory().getItemInMainHand();
            return hand == null || hand.getType() == org.bukkit.Material.AIR || hand.getAmount() <= 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private static String capitalizeWords(String s) {
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty())
                continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1)
                out.append(p.substring(1));
            out.append(' ');
        }
        return out.toString().trim();
    }

    private interface TokenWrapper {
        String wrap(String content);
    }
}