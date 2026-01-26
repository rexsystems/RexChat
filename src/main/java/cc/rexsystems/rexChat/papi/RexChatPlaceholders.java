package cc.rexsystems.rexChat.papi;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.service.ChatColorManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for RexChat.
 * Provides placeholders for chat status and player chat colors.
 */
public class RexChatPlaceholders extends PlaceholderExpansion {

    private final RexChat plugin;

    public RexChatPlaceholders(RexChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rexchat";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Handle muted status
        if (params.equalsIgnoreCase("muted")) {
            return String.valueOf(plugin.getChatManager().isChatMuted());
        }

        // Handle player-specific placeholders (require online player)
        if (player == null || !player.isOnline()) {
            return null;
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return null;
        }

        ChatColorManager colorManager = plugin.getChatColorManager();

        // Handle chat color placeholders
        if (params.equalsIgnoreCase("chatcolor")) {
            String colorName = colorManager.getPlayerColor(onlinePlayer);
            if (colorName == null) {
                return "None";
            }

            ChatColorManager.ChatColorPreset preset = colorManager.getPreset(colorName);
            if (preset != null && preset.displayName() != null) {
                return preset.displayName();
            }

            // Fallback: capitalize first letter
            return capitalizeFirst(colorName);
        }

        if (params.equalsIgnoreCase("chatcolor_raw")) {
            String colorName = colorManager.getPlayerColor(onlinePlayer);
            return colorName != null ? colorName : "none";
        }

        if (params.equalsIgnoreCase("chatcolor_format")) {
            String colorName = colorManager.getPlayerColor(onlinePlayer);
            if (colorName == null) {
                return "";
            }

            ChatColorManager.ChatColorPreset preset = colorManager.getPreset(colorName);
            return preset != null ? preset.format() : "";
        }

        return null;
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
