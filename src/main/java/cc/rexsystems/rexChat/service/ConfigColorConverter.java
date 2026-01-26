package cc.rexsystems.rexChat.service;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts legacy color codes in config.yml to MiniMessage format.
 * Creates backup before modifying.
 */
public class ConfigColorConverter {
    private final RexChat plugin;

    // Legacy color code mappings to MiniMessage
    private static final Map<Character, String> LEGACY_TO_MINI = new HashMap<>();
    static {
        LEGACY_TO_MINI.put('0', "<black>");
        LEGACY_TO_MINI.put('1', "<dark_blue>");
        LEGACY_TO_MINI.put('2', "<dark_green>");
        LEGACY_TO_MINI.put('3', "<dark_aqua>");
        LEGACY_TO_MINI.put('4', "<dark_red>");
        LEGACY_TO_MINI.put('5', "<dark_purple>");
        LEGACY_TO_MINI.put('6', "<gold>");
        LEGACY_TO_MINI.put('7', "<gray>");
        LEGACY_TO_MINI.put('8', "<dark_gray>");
        LEGACY_TO_MINI.put('9', "<blue>");
        LEGACY_TO_MINI.put('a', "<green>");
        LEGACY_TO_MINI.put('b', "<aqua>");
        LEGACY_TO_MINI.put('c', "<red>");
        LEGACY_TO_MINI.put('d', "<light_purple>");
        LEGACY_TO_MINI.put('e', "<yellow>");
        LEGACY_TO_MINI.put('f', "<white>");
        LEGACY_TO_MINI.put('k', "<obfuscated>");
        LEGACY_TO_MINI.put('l', "<bold>");
        LEGACY_TO_MINI.put('m', "<strikethrough>");
        LEGACY_TO_MINI.put('n', "<underlined>");
        LEGACY_TO_MINI.put('o', "<italic>");
        LEGACY_TO_MINI.put('r', "<reset>");
    }

    // Patterns for legacy codes
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_CODE = Pattern.compile("§([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMPERSAND_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern SECTION_HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");

    public ConfigColorConverter(RexChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if config contains any legacy color codes that need conversion.
     */
    public boolean needsConversion() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists())
            return false;

        try {
            String content = new String(Files.readAllBytes(configFile.toPath()));
            return AMPERSAND_CODE.matcher(content).find() ||
                    SECTION_CODE.matcher(content).find() ||
                    AMPERSAND_HEX.matcher(content).find() ||
                    SECTION_HEX.matcher(content).find();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not check config for legacy colors: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create backup of config.yml before conversion.
     */
    public File createBackup() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists())
            return null;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(plugin.getDataFolder(), "config_backup_" + timestamp + ".yml");

        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created config backup: " + backupFile.getName());
            return backupFile;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create config backup: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert all legacy color codes in config.yml to MiniMessage format.
     * Creates backup first.
     */
    public boolean convertConfig() {
        if (!needsConversion()) {
            plugin.getLogger().info("Config already uses MiniMessage format - no conversion needed.");
            return true;
        }

        // Create backup FIRST
        File backup = createBackup();
        if (backup == null) {
            plugin.getLogger().severe("Failed to backup config - aborting conversion for safety!");
            return false;
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");

        try {
            // Read entire file
            String content = new String(Files.readAllBytes(configFile.toPath()));

            // Convert all legacy codes
            String converted = convertColors(content);

            // Write back
            Files.write(configFile.toPath(), converted.getBytes());

            // Reload config
            plugin.reloadConfig();

            plugin.getLogger().info("Successfully converted config.yml to MiniMessage format!");
            plugin.getLogger().info("Backup saved as: " + backup.getName());
            return true;

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to convert config: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convert a string from legacy color codes to MiniMessage format.
     */
    public String convertColors(String text) {
        if (text == null || text.isEmpty())
            return text;

        String result = text;

        // 1. Convert §x§R§R§G§G§B§B to <#RRGGBB>
        result = convertSectionHex(result);

        // 2. Convert &#RRGGBB to <#RRGGBB>
        result = AMPERSAND_HEX.matcher(result).replaceAll("<#$1>");

        // 3. Convert &c style codes
        result = convertLegacyCodes(result, AMPERSAND_CODE);

        // 4. Convert §c style codes
        result = convertLegacyCodes(result, SECTION_CODE);

        return result;
    }

    private String convertSectionHex(String text) {
        // Pattern: §x§R§R§G§G§B§B
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 13 < text.length() && text.charAt(i) == '§' && text.charAt(i + 1) == 'x') {
                // Check if this is a valid hex sequence
                StringBuilder hex = new StringBuilder();
                boolean valid = true;
                for (int j = 0; j < 6 && valid; j++) {
                    int pos = i + 2 + (j * 2);
                    if (pos + 1 < text.length() && text.charAt(pos) == '§') {
                        char c = Character.toLowerCase(text.charAt(pos + 1));
                        if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                            hex.append(c);
                        } else {
                            valid = false;
                        }
                    } else {
                        valid = false;
                    }
                }
                if (valid && hex.length() == 6) {
                    result.append("<#").append(hex).append(">");
                    i += 14; // Skip the entire §x§R§R§G§G§B§B
                    continue;
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString();
    }

    private String convertLegacyCodes(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = LEGACY_TO_MINI.getOrDefault(code, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
