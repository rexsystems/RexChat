package me.rexsystems.rexChat.config;

import me.rexsystems.rexChat.RexChat;
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

            new ConfigMigrator(plugin).migrate(this.config);

            // Merge any missing keys from bundled defaults while preserving user values
            new ConfigAutoUpdater(plugin).ensureDefaults();

            if (plugin.getCustomTokenRegistry() != null) {
                plugin.getCustomTokenRegistry().reloadFromConfig();
            }

            // Ensure mention messages exist for chat notifications
            ensureDefault(config, "messages.mention.target", "%rc_prefix%&eYou were mentioned by &6{sender}");
            ensureDefault(config, "messages.mention.sender", "%rc_prefix%&aYou mentioned &6{targets}");
            // Prevent self-mention triggers by default
            ensureDefault(config, "mention.prevent-self", true);
            // Default to disabling chat reporting on 1.19+ unless explicitly turned off
            ensureDefault(config, "chat-reporting.disable", true);
            ensureDefault(config, "chat-format.player.click.type", "suggest_command");
            ensureDefault(config, "chat-format.player.click.value", "/msg {player} ");

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
            ensureDefault(config, "messages.preview.coords.label-template", "&7[&b{x}, {y}, {z}&7]");
            ensureDefault(config, "messages.preview.coords.hover",
                    "&7Click to copy coordinates\n&7Staff: &f/rexchat tpcoords {id}");
            ensureDefault(config, "messages.preview.coords.copy-format", "{x} {y} {z}");
            ensureDefault(config, "messages.preview.coords.teleport-success",
                    "%rc_prefix%&aTeleported to &6{player}&a's coordinates &7({x}, {y}, {z})");

            // DiscordSRV container contents preview ([item] only)
            ensureDefault(config, "chat-discord.previews.item-container-contents", true);
            ensureDefault(config, "chat-discord.previews.item-shulker-contents", true);
            ensureDefault(config, "chat-discord.embeds.item.container.color", "#9B59B6");
            ensureDefault(config, "chat-discord.embeds.item.shulker.color", "#9B59B6");
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