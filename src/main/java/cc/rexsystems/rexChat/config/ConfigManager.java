package cc.rexsystems.rexChat.config;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final RexChat plugin;
    private FileConfiguration config;
    private final ConfigValidator validator;

    public ConfigManager(RexChat plugin) {
        this.plugin = plugin;
        this.validator = new ConfigValidator(plugin);
    }

    public boolean loadConfigs() {
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.config = plugin.getConfig();

            // Debug: log available groups after reload
            if (this.config.isConfigurationSection("chat-format.groups")) {
                java.util.Set<String> groups = this.config.getConfigurationSection("chat-format.groups").getKeys(false);
                plugin.getLogUtils().info("Loaded chat-format groups: " + String.join(", ", groups));
            }

            // Merge any missing keys from bundled defaults while preserving user values
            new ConfigAutoUpdater(plugin).ensureDefaults();

            // Migrate old features.chat-previews to chat-previews
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
                // Remove old features.chat-previews section
                config.set("features.chat-previews", null);
                // Remove features section if empty
                if (config.isConfigurationSection("features")
                        && config.getConfigurationSection("features").getKeys(false).isEmpty()) {
                    config.set("features", null);
                }
                plugin.saveConfig();
                plugin.getLogUtils().info("Migrated features.chat-previews to chat-previews");
            }

            // Ensure mention messages exist for chat notifications
            ensureDefault(config, "messages.mention.target", "%rc_prefix%&eYou were mentioned by &6{sender}");
            ensureDefault(config, "messages.mention.sender", "%rc_prefix%&aYou mentioned &6{targets}");
            // Prevent self-mention triggers by default
            ensureDefault(config, "mention.prevent-self", true);
            // Default to disabling chat reporting on 1.19+ unless explicitly turned off
            ensureDefault(config, "chat-reporting.disable", true);

            // Chat previews: tokens and messages
            ensureDefault(config, "chat-previews.enabled", true);
            if (!config.contains("chat-previews.tokens.item")) {
                java.util.List<String> itemTokens = new java.util.ArrayList<>();
                itemTokens.add("[item]");
                itemTokens.add("[i]");
                config.set("chat-previews.tokens.item", itemTokens);
            }
            if (!config.contains("chat-previews.tokens.inventory")) {
                java.util.List<String> invTokens = new java.util.ArrayList<>();
                invTokens.add("[inventory]");
                invTokens.add("[inv]");
                config.set("chat-previews.tokens.inventory", invTokens);
            }
            ensureDefault(config, "messages.preview.inventory.title", "§6Inventory: §f{player}");
            ensureDefault(config, "messages.preview.item.title", "§6Item: §f{player}");
            ensureDefault(config, "messages.preview.item.hover", "%rc_prefix%&7Click to view {player}'s item");
            ensureDefault(config, "messages.preview.inventory.hover",
                    "%rc_prefix%&7Click to view {player}'s inventory");
            ensureDefault(config, "messages.preview.target-not-found", "%rc_prefix%&cPlayer not found.");
            ensureDefault(config, "messages.preview.item.none", "%rc_prefix%&eYou are not holding any item.");
            ensureDefault(config, "messages.preview.inventory.open",
                    "%rc_prefix%&7Opening inventory preview for &6{player}");
            ensureDefault(config, "messages.preview.item.open", "%rc_prefix%&7Opening item preview for &6{player}");
            plugin.saveConfig();

            boolean isValid = validator.validateConfig();

            if (isValid) {
                plugin.getLogUtils().info("Configuration loaded and validated successfully");
            } else {
                plugin.getLogUtils().severe("Configuration validation failed. Check above for errors.");
                plugin.getLogUtils().warning("Plugin will continue to run with default values where possible.");
            }

            return isValid;
        } catch (Exception e) {
            plugin.getLogUtils().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public ConfigValidator getValidator() {
        return validator;
    }

    private void ensureDefault(FileConfiguration cfg, String path, Object defVal) {
        if (!cfg.contains(path)) {
            cfg.set(path, defVal);
            if (plugin.getLogUtils() != null) {
                plugin.getLogUtils().debug("Added missing config key: " + path);
            }
        }
    }
}