package cc.rexsystems.rexChat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Pattern for hex codes without & prefix (e.g., #A96EEE)
    // Negative lookbehind ensures it's not part of &#RRGGBB or :#RRGGBB
    private static final Pattern HEX_PATTERN_NO_AMPERSAND = Pattern.compile("(?<![:&<])#([A-Fa-f0-9]{6})");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?");
    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("§([0-9a-fk-or])");

    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile(
            "(?is)(<hover:show_text:'((?:[^']|'')*)'><click:run_command:'((?:[^']|'')*)'>((?:(?!</click></hover>).)*)</click></hover>)");

    public static Component parseComponent(String text) {
        // Protect MiniMessage tags (hover/click) BEFORE any processing
        java.util.List<String> protectedTags = new java.util.ArrayList<>();
        StringBuffer protectedText = new StringBuffer();
        Matcher tagMatcher = MINIMESSAGE_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        // Extract and protect MiniMessage tags BEFORE any color conversion
        while (tagMatcher.find()) {
            protectedText.append(text, lastEnd, tagMatcher.start());
            String fullTag = tagMatcher.group(1);
            String placeholder = "__REXCHAT_PROTECTED_TAG_" + protectedTags.size() + "__";
            protectedTags.add(fullTag);
            protectedText.append(placeholder);
            lastEnd = tagMatcher.end();
        }
        protectedText.append(text.substring(lastEnd));
        text = protectedText.toString();

        // Protect lone ampersands (& not followed by valid color code) BEFORE color
        // conversion
        // This prevents ChatColor.translateAlternateColorCodes from eating them
        text = protectLoneAmpersands(text);

        // Normalize uppercase color codes to lowercase BEFORE translation
        // ChatColor.translateAlternateColorCodes only recognizes lowercase codes
        // This fixes issues where &C or &L don't work but &c and &l do
        text = normalizeColorCodes(text);

        // Now safe to convert & codes to § codes
        text = ChatColor.translateAlternateColorCodes('&', text);

        // First, handle hex colors followed by legacy formatting (like &#FF4500&lF)
        text = convertHexWithLegacyFormatting(text);

        // First, convert legacy RGB format (§x§R§R§G§G§B§B) to hex format
        // This must be done BEFORE individual § replacements to preserve the pattern
        text = convertLegacyRGB(text);

        // Convert any remaining § codes that weren't converted from & codes
        // This handles cases where prefixes or other data contain § codes
        text = text.replace("§0", "<reset><black>")
                .replace("§1", "<reset><dark_blue>")
                .replace("§2", "<reset><dark_green>")
                .replace("§3", "<reset><dark_aqua>")
                .replace("§4", "<reset><dark_red>")
                .replace("§5", "<reset><dark_purple>")
                .replace("§6", "<reset><gold>")
                .replace("§7", "<reset><gray>")
                .replace("§8", "<reset><dark_gray>")
                .replace("§9", "<reset><blue>")
                .replace("§a", "<reset><green>")
                .replace("§b", "<reset><aqua>")
                .replace("§c", "<reset><red>")
                .replace("§d", "<reset><light_purple>")
                .replace("§e", "<reset><yellow>")
                .replace("§f", "<reset><white>")
                .replace("§k", "<obfuscated>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");

        // Remove leftover & that aren't valid color codes (e.g. &ʟᴀᴠᴀɴᴅ)
        // This happens when config has & followed by non-color characters
        text = text.replaceAll("&(?![0-9a-fk-orA-FK-OR#])", "");

        // Clean up broken hex patterns like "&B&l&o&o&m" -> "Bloom"
        // This happens when hex colors (&#FFFFFF) get corrupted
        // Pattern: & followed by a single uppercase letter, repeated
        text = text.replaceAll("&([A-Z])(?=&[A-Z]|$)", "$1");

        // Restore protected MiniMessage tags
        for (int i = 0; i < protectedTags.size(); i++) {
            text = text.replace("__REXCHAT_PROTECTED_TAG_" + i + "__", protectedTags.get(i));
        }

        // Convert #RRGGBB (without &) to MiniMessage format first
        Matcher noAmpMatcher = HEX_PATTERN_NO_AMPERSAND.matcher(text);
        StringBuffer noAmpBuffer = new StringBuffer();
        while (noAmpMatcher.find()) {
            noAmpMatcher.appendReplacement(noAmpBuffer, "<reset><#$1>");
        }
        noAmpMatcher.appendTail(noAmpBuffer);
        text = noAmpBuffer.toString();

        // Then convert &#RRGGBB (with &) to MiniMessage format
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            // Apply reset before hex colors so formatting like bold is cleared when a color
            // is set
            matcher.appendReplacement(buffer, "<reset><#$1>");
        }
        matcher.appendTail(buffer);

        String miniMessage = buffer.toString();

        // Restore protected lone ampersands (they were replaced with __LONE_AMP__
        // earlier)
        miniMessage = restoreLoneAmpersands(miniMessage);

        // Final cleanup: remove any remaining & that should have been processed but
        // weren't
        // But keep restored lone ampersands (__LONE_AMP__ -> &)
        // At this point, only & from invalid patterns remain - leave them as-is for
        // visibility

        // Use non-strict MiniMessage to handle unknown tags gracefully
        // This prevents errors when config contains custom placeholders like <LAVAND>
        Component component;
        try {
            component = MiniMessage.builder()
                    .strict(false)
                    .build()
                    .deserialize(miniMessage);
        } catch (Throwable t) {
            // If MiniMessage fails completely, fall back to legacy serializer
            try {
                component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(text);
            } catch (Throwable t2) {
                component = Component.text(text);
            }
        }

        String originalText = ChatColor.stripColor(text);
        matcher = URL_PATTERN.matcher(originalText);
        while (matcher.find()) {
            String url = matcher.group();
            String fullUrl = url.startsWith("http") ? url : "https://" + url;
            component = component.clickEvent(ClickEvent.openUrl(fullUrl));
        }

        return component;
    }

    private static final Pattern LEGACY_RGB_PATTERN = Pattern.compile("§x(?:§[0-9a-fA-F]){6}");

    private static String convertLegacyRGB(String text) {
        // Convert §x§R§R§G§G§B§B format to &#RRGGBB format
        Matcher matcher = LEGACY_RGB_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String match = matcher.group();
            // Extract hex from §x§R§R§G§G§B§B format
            String hex = match.substring(2).replace("§", "");
            matcher.appendReplacement(buffer, "&#" + hex);
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static final Pattern HEX_WITH_LEGACY_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})&([0-9a-fk-orA-FK-OR])");

    private static String convertHexWithLegacyFormatting(String text) {
        // Handle patterns like &#FF4500&lF where hex color is followed by legacy
        // formatting
        Matcher matcher = HEX_WITH_LEGACY_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String legacyCode = matcher.group(2);

            // Convert the legacy code to MiniMessage format
            String miniMessageFormat = convertLegacyCodeToMiniMessage(legacyCode);
            matcher.appendReplacement(buffer, "<#" + hex + ">" + miniMessageFormat);
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static String convertLegacyCodeToMiniMessage(String code) {
        switch (code.toLowerCase()) {
            case "k":
                return "<obfuscated>";
            case "l":
                return "<bold>";
            case "m":
                return "<strikethrough>";
            case "n":
                return "<underlined>";
            case "o":
                return "<italic>";
            case "r":
                return "<reset>";
            // Color codes - these should have reset before them
            case "0":
                return "<reset><black>";
            case "1":
                return "<reset><dark_blue>";
            case "2":
                return "<reset><dark_green>";
            case "3":
                return "<reset><dark_aqua>";
            case "4":
                return "<reset><dark_red>";
            case "5":
                return "<reset><dark_purple>";
            case "6":
                return "<reset><gold>";
            case "7":
                return "<reset><gray>";
            case "8":
                return "<reset><dark_gray>";
            case "9":
                return "<reset><blue>";
            case "a":
                return "<reset><green>";
            case "b":
                return "<reset><aqua>";
            case "c":
                return "<reset><red>";
            case "d":
                return "<reset><light_purple>";
            case "e":
                return "<reset><yellow>";
            case "f":
                return "<reset><white>";
            default:
                return "";
        }
    }

    public static String translateLegacyColors(String text) {
        if (text == null)
            return "";

        // Normalize uppercase color codes to lowercase BEFORE translation
        // ChatColor.translateAlternateColorCodes only recognizes lowercase codes
        text = normalizeColorCodes(text);

        // Use ChatColor.translateAlternateColorCodes first to handle standard & codes
        text = ChatColor.translateAlternateColorCodes('&', text);

        // Then handle hex codes: &#RRGGBB -> §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * Strip all color codes and formatting from a message
     * 
     * @param message The message to strip colors from
     * @return The message without any color codes
     */
    public static String stripColors(String message) {
        if (message == null)
            return "";

        // First, normalize uppercase color codes to lowercase for consistent matching
        message = normalizeColorCodes(message);

        // Remove hex colors with & (&#RRGGBB)
        message = HEX_PATTERN.matcher(message).replaceAll("");

        // Remove hex colors without & (#RRGGBB) - use a simpler pattern that matches
        // any # followed by 6 hex digits, regardless of what follows (space, text, etc.)
        // This ensures #a96eee TEXT or #a96eee[space]TEXT are both stripped
        message = message.replaceAll("(?<![:&])#([A-Fa-f0-9]{6})", "");

        // Remove & color codes (case insensitive now after normalization)
        message = COLOR_PATTERN.matcher(message).replaceAll("");

        // Remove § color codes
        message = LEGACY_COLOR_PATTERN.matcher(message).replaceAll("");

        // Remove legacy RGB format (§x§R§R§G§G§B§B)
        message = message.replaceAll("§x(§[0-9a-fA-F]){6}", "");

        // Remove any remaining § codes that might have been missed
        message = message.replaceAll("§[0-9a-fk-orA-FK-OR]", "");

        return message;
    }

    /**
     * Protect lone ampersands that aren't part of valid color codes.
     * Replaces lone & with a placeholder that won't be processed by color
     * conversion.
     * This is called BEFORE ChatColor.translateAlternateColorCodes.
     */
    private static String protectLoneAmpersands(String text) {
        if (text == null || text.isEmpty())
            return text;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                // Check what follows the &
                if (i + 1 >= text.length()) {
                    // & at end of string - replace with literal ampersand entity
                    result.append("__LONE_AMP__");
                    continue;
                }

                char next = text.charAt(i + 1);

                // Valid color codes: 0-9, a-f, k-o, r (case insensitive)
                boolean isValidColorCode = "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) != -1;

                // Check for hex code &#RRGGBB
                boolean isHexCode = (next == '#' && i + 7 < text.length() &&
                        text.substring(i + 2, i + 8).matches("[A-Fa-f0-9]{6}"));

                if (isValidColorCode || isHexCode) {
                    // Valid color code, keep the &
                    result.append(c);
                } else {
                    // Lone & - protect it
                    result.append("__LONE_AMP__");
                    continue;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Restore protected lone ampersands after color processing.
     */
    public static String restoreLoneAmpersands(String text) {
        if (text == null)
            return text;
        return text.replace("__LONE_AMP__", "&");
    }

    /**
     * Normalize uppercase color codes to lowercase.
     * ChatColor.translateAlternateColorCodes only recognizes lowercase codes (a-f, k-o, r),
     * so &C, &L, etc. need to be converted to &c, &l before processing.
     * 
     * This handles patterns like:
     * - &C -> &c
     * - &L -> &l
     * - &[A-FK-OR] -> &[a-fk-or]
     * 
     * Hex codes (&#RRGGBB) are left unchanged.
     */
    private static String normalizeColorCodes(String text) {
        if (text == null || text.isEmpty())
            return text;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                // Check if there's a next character
                if (i + 1 >= text.length()) {
                    result.append(c);
                    continue;
                }

                char next = text.charAt(i + 1);

                // Check if it's a hex code (&#RRGGBB)
                if (next == '#' && i + 7 < text.length()) {
                    String potentialHex = text.substring(i + 2, i + 8);
                    if (potentialHex.matches("[A-Fa-f0-9]{6}")) {
                        // It's a hex code, keep as-is
                        result.append(c);
                        continue;
                    }
                }

                // Check if it's an uppercase color code that needs normalization
                // Valid codes: 0-9, a-f, k-o, r (case insensitive)
                if ("ABCDEFKLMNOR".indexOf(next) != -1) {
                    // Convert uppercase to lowercase
                    result.append(c);
                    result.append(Character.toLowerCase(next));
                    i++; // Skip the next character since we've processed it
                    continue;
                }

                // Not an uppercase color code, keep as-is
                result.append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
