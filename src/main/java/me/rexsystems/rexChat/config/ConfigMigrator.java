package me.rexsystems.rexChat.config;

import me.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Applies incremental config.yml migrations based on {@code config-version}.
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 2;

    private final RexChat plugin;

    public ConfigMigrator(RexChat plugin) {
        this.plugin = plugin;
    }

    /**
     * @return number of version steps applied (0 if already up to date)
     */
    public int migrate(FileConfiguration config) {
        int version = config.getInt("config-version", 0);
        if (version > CURRENT_VERSION) {
            plugin.getLogUtils().warning(
                    "config-version " + version + " is newer than this plugin supports (" + CURRENT_VERSION + ").");
            return 0;
        }
        if (version >= CURRENT_VERSION) {
            return 0;
        }

        int fromVersion = version;
        while (version < CURRENT_VERSION) {
            switch (version + 1) {
                case 1 -> migrateToV1(config);
                case 2 -> migrateToV2(config);
                default -> {
                }
            }
            version++;
        }

        config.set("config-version", CURRENT_VERSION);
        backupConfigSafely();
        plugin.saveConfig();
        plugin.getLogUtils().info("Config migrated from v" + fromVersion + " to v" + CURRENT_VERSION + ".");
        return CURRENT_VERSION - fromVersion;
    }

    /** Legacy layouts without config-version. */
    private void migrateToV1(FileConfiguration config) {
        if (config.contains("features.chat-previews") && !config.contains("chat-previews")) {
            if (config.isBoolean("features.chat-previews.enabled")) {
                config.set("chat-previews.enabled", config.getBoolean("features.chat-previews.enabled"));
            }
            if (config.isList("features.chat-previews.tokens.item")) {
                config.set("chat-previews.tokens.item", config.getList("features.chat-previews.tokens.item"));
            }
            if (config.isList("features.chat-previews.tokens.inventory")) {
                config.set("chat-previews.tokens.inventory",
                        config.getList("features.chat-previews.tokens.inventory"));
            }
            config.set("features.chat-previews", null);
            if (config.isConfigurationSection("features")) {
                ConfigurationSection features = config.getConfigurationSection("features");
                if (features != null && features.getKeys(false).isEmpty()) {
                    config.set("features", null);
                }
            }
        }
    }

    /** Configurable name click on chat messages. */
    private void migrateToV2(FileConfiguration config) {
        if (!config.isConfigurationSection("chat-format.player.click")) {
            config.set("chat-format.player.click.type", "suggest_command");
            config.set("chat-format.player.click.value", "/msg {player} ");
        }
    }

    private void backupConfigSafely() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                return;
            }
            File file = new File(dataFolder, "config.yml");
            if (!file.exists()) {
                return;
            }
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Path backup = new File(dataFolder, "config.yml.bak." + ts).toPath();
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogUtils().debug("Config backup created: " + backup.getFileName());
        } catch (Throwable ignored) {
        }
    }
}
