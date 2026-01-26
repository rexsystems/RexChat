package cc.rexsystems.rexChat.config;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Merges missing defaults from the bundled config.yml into the user's config
 * without overwriting existing values. Creates a timestamped backup before
 * saving.
 */
public class ConfigAutoUpdater {
    private final RexChat plugin;

    public ConfigAutoUpdater(RexChat plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults() {
        try {
            FileConfiguration userCfg = plugin.getConfig();
            InputStream is = plugin.getResource("config.yml");
            if (is == null) {
                plugin.getLogger().warning("Default config.yml resource not found; skipping auto-update.");
                return;
            }

            YamlConfiguration defaults = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

            int missing = mergeMissingKeysPreservingOrder(userCfg, defaults);
            if (missing <= 0) {
                plugin.getLogUtils().debug("Config auto-update: no missing keys.");
                return;
            }

            // Backup original config before saving
            backupConfigSafely();

            // Persist merged config without reordering existing keys
            plugin.saveConfig();
            plugin.getLogUtils()
                    .info("Config auto-update: added " + missing + " missing key(s) safely (order preserved).");
        } catch (Throwable t) {
            plugin.getLogger().warning("Config auto-update failed: " + t.getMessage());
        }
    }

    private int mergeMissingKeysPreservingOrder(FileConfiguration userCfg, YamlConfiguration defaults) {
        int count = 0;

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key))
                continue; // leaf values only
            if (userCfg.isSet(key))
                continue; // do not override

            // SKIP ALL commands.* keys if user already has commands section
            // This allows users to delete default commands (rules, store, etc.) without
            // them coming back
            if (key.startsWith("commands.") && userCfg.contains("commands")) {
                continue;
            }

            Object val = defaults.get(key);
            userCfg.set(key, val);
            count++;
        }
        return count;
    }

    private void backupConfigSafely() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists())
                return;
            File file = new File(dataFolder, "config.yml");
            if (!file.exists())
                return;
            String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            Path backup = new File(dataFolder, "config.yml.bak." + ts).toPath();
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogUtils().debug("Config backup created: " + backup.getFileName());
        } catch (Throwable ignored) {
        }
    }
}