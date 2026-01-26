package cc.rexsystems.rexChat.config;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidator {
    private final RexChat plugin;
    private final List<String> errors;

    public ConfigValidator(RexChat plugin) {
        this.plugin = plugin;
        this.errors = new ArrayList<>();
    }

    public boolean validateConfig() {
        errors.clear();
        FileConfiguration config = plugin.getConfigManager().getConfig();

        checkSection(config, "messages");
        checkSection(config, "chat-management");
        checkSection(config, "commands");
        checkSection(config, "chat-format");
        checkSection(config, "update-checker");
        checkSection(config, "join-leave");
        // Optional but recommended: chat previews
        if (config.contains("chat-previews")) {
            if (!config.isBoolean("chat-previews.enabled")) {
                errors.add("Missing or invalid chat-previews.enabled");
            }
            if (config.contains("chat-previews.tokens.item") && !config.isList("chat-previews.tokens.item")) {
                errors.add("chat-previews.tokens.item must be a list");
            }
            if (config.contains("chat-previews.tokens.inventory") && !config.isList("chat-previews.tokens.inventory")) {
                errors.add("chat-previews.tokens.inventory must be a list");
            }
            // Messages optional
        }

        checkString(config, "messages.prefix", "Message prefix");
        checkString(config, "messages.no-permission", "No permission message");
        checkString(config, "messages.reload-success", "Reload success message");
        // Mention chat notifications must be configurable
        checkString(config, "messages.mention.target", "Mention target message");
        checkString(config, "messages.mention.sender", "Mention sender message");

        checkString(config, "chat-management.mute.muted-message", "Chat muted message");
        checkString(config, "chat-management.mute.mute-announcement", "Mute announcement message");
        checkString(config, "chat-management.mute.unmute-announcement", "Unmute announcement message");
        checkString(config, "chat-management.clear.clear-message", "Clear chat message");
        checkInteger(config, "chat-management.clear.lines", "Clear chat lines");

        // Chat format validation
        if (config.contains("chat-format")) {
            if (!config.isBoolean("chat-format.enabled")) {
                errors.add("Missing or invalid Chat format 'enabled' flag at chat-format.enabled");
            }
            checkString(config, "chat-format.format", "Chat format pattern");
            if (config.isConfigurationSection("chat-format.player.hover")) {
                if (!config.isBoolean("chat-format.player.hover.enabled")) {
                    errors.add("Missing or invalid hover 'enabled' at chat-format.player.hover.enabled");
                }
                if (!config.isList("chat-format.player.hover.lines")) {
                    errors.add("Hover 'lines' must be a list at chat-format.player.hover.lines");
                }
            }
        }

        // Chat reporting toggle (optional)
        if (config.contains("chat-reporting")) {
            if (config.isConfigurationSection("chat-reporting")) {
                if (config.contains("chat-reporting.disable") && !config.isBoolean("chat-reporting.disable")) {
                    errors.add("Invalid chat-reporting.disable (must be boolean)");
                }
            } else {
                errors.add("Invalid chat-reporting section");
            }
        }

        // Update checker validation
        if (config.contains("update-checker")) {
            if (!config.isBoolean("update-checker.enabled")) {
                errors.add("Missing or invalid update-checker.enabled");
            }
            if (!config.isBoolean("update-checker.notify-ops-on-join")) {
                errors.add("Missing or invalid update-checker.notify-ops-on-join");
            }
            checkString(config, "update-checker.permission", "Update checker permission");
            checkString(config, "update-checker.message", "Update checker message");
        }

        // Join/Leave validation
        if (config.contains("join-leave")) {
            if (!config.isSet("join-leave.join-message")) {
                errors.add("Missing join-leave.join-message (can be blank to disable)");
            }
            if (!config.isSet("join-leave.leave-message")) {
                errors.add("Missing join-leave.leave-message (can be blank to disable)");
            }
        }

        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection != null) {
            for (String key : commandsSection.getKeys(false)) {
                validateCommand(commandsSection.getConfigurationSection(key), key);
            }
        }

        // Chat Emoji validation (optional)
        if (config.contains("chat-emoji")) {
            if (!config.isBoolean("chat-emoji.enabled")) {
                errors.add("Missing or invalid chat-emoji.enabled");
            }
        }

        // Mention validation (optional)
        if (config.contains("mention")) {
            if (!config.isBoolean("mention.enabled")) {
                errors.add("Missing or invalid mention.enabled");
            }
            if (!config.isString("mention.color")) {
                errors.add("Missing or invalid mention.color");
            }
            if (config.contains("mention.by-name") && !config.isBoolean("mention.by-name")) {
                errors.add("Missing or invalid mention.by-name");
            }
            if (config.isConfigurationSection("mention.sound")) {
                if (!config.isBoolean("mention.sound.enabled")) {
                    errors.add("Missing or invalid mention.sound.enabled");
                }
                if (!config.isString("mention.sound.name")) {
                    errors.add("Missing or invalid mention.sound.name");
                }
            }
            if (config.isConfigurationSection("mention.notify")) {
                if (!config.isBoolean("mention.notify.sender")) {
                    errors.add("Missing or invalid mention.notify.sender");
                }
                if (!config.isBoolean("mention.notify.target")) {
                    errors.add("Missing or invalid mention.notify.target");
                }
            }
            if (config.isConfigurationSection("mention.title")) {
                if (!config.isBoolean("mention.title.enabled")) {
                    errors.add("Missing or invalid mention.title.enabled");
                }
                if (!config.isString("mention.title.title")) {
                    errors.add("Missing or invalid mention.title.title");
                }
                if (!config.isString("mention.title.subtitle")) {
                    errors.add("Missing or invalid mention.title.subtitle");
                }
                if (!config.isInt("mention.title.fade-in") || !config.isInt("mention.title.stay") || !config.isInt("mention.title.fade-out")) {
                    errors.add("Missing or invalid mention.title timings (fade-in/stay/fade-out)");
                }
            }
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().severe("Configuration validation failed with the following errors:");
            for (String error : errors) {
                plugin.getLogger().severe("- " + error);
            }
            return false;
        }

        return true;
    }

    private void validateCommand(ConfigurationSection cmdSection, String commandName) {
        if (cmdSection == null) {
            errors.add("Invalid command configuration for: " + commandName);
            return;
        }

        if (!cmdSection.contains("command")) {
            errors.add("Missing 'command' field for command: " + commandName);
        }

        if (!cmdSection.contains("message") && !cmdSection.contains("message-list")) {
            errors.add("Command '" + commandName + "' must have either 'message' or 'message-list' defined");
        }

        String message = cmdSection.getString("message");
        if (message != null && !isValidMessageFormat(message)) {
            errors.add("Invalid message format for command: " + commandName);
        }

        List<String> messageList = cmdSection.getStringList("message-list");
        for (String line : messageList) {
            if (!isValidMessageFormat(line)) {
                errors.add("Invalid message format in message-list for command: " + commandName);
                break;
            }
        }
    }

    private boolean isValidMessageFormat(String message) {
        return message != null && !message.trim().isEmpty();
    }

    private void checkSection(FileConfiguration config, String path) {
        if (!config.contains(path)) {
            errors.add("Missing required section: " + path);
        }
    }

    private void checkString(FileConfiguration config, String path, String description) {
        if (!config.contains(path) || !config.isString(path)) {
            errors.add("Missing or invalid " + description + " at: " + path);
        }
    }

    private void checkInteger(FileConfiguration config, String path, String description) {
        if (!config.contains(path) || !config.isInt(path)) {
            errors.add("Missing or invalid " + description + " at: " + path);
        }
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}