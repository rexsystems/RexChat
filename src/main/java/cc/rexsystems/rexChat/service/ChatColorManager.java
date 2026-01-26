package cc.rexsystems.rexChat.service;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player chat color selections and preset colors.
 */
public class ChatColorManager {
    private final RexChat plugin;
    private final Map<String, ChatColorPreset> presets = new LinkedHashMap<>();

    public ChatColorManager(RexChat plugin) {
        this.plugin = plugin;
        loadPresets();
    }

    /**
     * Load color presets from config. Does NOT add defaults if section doesn't
     * exist.
     */
    public void loadPresets() {
        presets.clear();

        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("chatcolor.colors");

        if (section == null) {
            // No colors defined - that's fine, user chose to remove them
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection colorSection = section.getConfigurationSection(key);
            if (colorSection == null)
                continue;

            String format = colorSection.getString("format", "&f");
            String permission = colorSection.getString("permission", "rexchat.chatcolor." + key);
            String displayName = colorSection.getString("display-name", key);

            presets.put(key.toLowerCase(), new ChatColorPreset(key, format, permission, displayName));
        }

        plugin.getLogUtils().debug("Loaded " + presets.size() + " chat color presets");
    }

    /**
     * Get all available presets.
     */
    public Map<String, ChatColorPreset> getPresets() {
        return presets;
    }

    /**
     * Get a preset by name.
     */
    public ChatColorPreset getPreset(String name) {
        return presets.get(name.toLowerCase());
    }

    /**
     * Get presets available to a player (based on permissions).
     */
    public Map<String, ChatColorPreset> getAvailablePresets(Player player) {
        Map<String, ChatColorPreset> available = new LinkedHashMap<>();
        for (Map.Entry<String, ChatColorPreset> entry : presets.entrySet()) {
            if (player.hasPermission(entry.getValue().permission())) {
                available.put(entry.getKey(), entry.getValue());
            }
        }
        return available;
    }

    /**
     * Get the player's selected color from data.yml.
     */
    public String getPlayerColor(Player player) {
        return plugin.getDataManager().getData()
                .getString("chatcolor." + player.getUniqueId().toString(), null);
    }

    /**
     * Set the player's selected color.
     */
    public void setPlayerColor(Player player, String colorName) {
        if (colorName == null) {
            plugin.getDataManager().getData().set("chatcolor." + player.getUniqueId().toString(), null);
        } else {
            plugin.getDataManager().getData().set("chatcolor." + player.getUniqueId().toString(),
                    colorName.toLowerCase());
        }
        plugin.getDataManager().saveData();
    }

    /**
     * Apply the player's preset color to their message.
     * Returns the message with color applied, or original if no preset selected.
     */
    public String applyPlayerColor(Player player, String message) {
        String colorName = getPlayerColor(player);
        if (colorName == null)
            return message;

        ChatColorPreset preset = getPreset(colorName);
        if (preset == null)
            return message;

        // Check if player still has permission for this color
        if (!player.hasPermission(preset.permission())) {
            // Remove their selection since they lost permission
            setPlayerColor(player, null);
            return message;
        }

        // Apply the color format to the message
        return preset.format() + message;
    }

    /**
     * Check if chatcolor system is enabled.
     */
    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("chatcolor.enabled", true);
    }

    /**
     * Represents a chat color preset.
     */
    public record ChatColorPreset(String id, String format, String permission, String displayName) {
    }
}
