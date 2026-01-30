package cc.rexsystems.rexChat.api;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * RexChat Developer API
 * 
 * Provides access to RexChat features for other plugins.
 * 
 * @since 1.6.0
 */
public class RexChatAPI {
    
    private final RexChat plugin;
    
    private RexChatAPI(RexChat plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Get the RexChat API instance
     * 
     * @param plugin Your plugin instance
     * @return RexChatAPI instance or null if RexChat is not loaded
     */
    public static RexChatAPI getInstance(Plugin plugin) {
        Plugin rexChat = plugin.getServer().getPluginManager().getPlugin("RexChat");
        if (rexChat instanceof RexChat) {
            return new RexChatAPI((RexChat) rexChat);
        }
        return null;
    }
    
    /**
     * Check if chat is currently muted
     * 
     * @return true if chat is muted
     */
    public boolean isChatMuted() {
        return plugin.getChatManager().isChatMuted();
    }
    
    /**
     * Mute or unmute the chat
     * 
     * @param muted true to mute, false to unmute
     * @param executor Name of the executor (for announcements)
     */
    public void setChatMuted(boolean muted, String executor) {
        if (muted != isChatMuted()) {
            plugin.getChatManager().toggleMute(executor);
        }
    }
    
    /**
     * Clear the chat for all players
     * 
     * @param executor Name of the executor (for announcements)
     */
    public void clearChat(String executor) {
        plugin.getChatManager().clearChat(executor);
    }
    
    /**
     * Get a player's selected chat color
     * 
     * @param player The player
     * @return Color ID or null if no color is set
     */
    public String getPlayerChatColor(Player player) {
        if (plugin.getChatColorManager() == null) {
            return null;
        }
        return plugin.getChatColorManager().getPlayerColor(player);
    }
    
    /**
     * Set a player's chat color
     * 
     * @param player The player
     * @param colorId Color ID from config (e.g., "red", "rainbow") or null to remove
     * @return true if successful, false if color doesn't exist or player lacks permission
     */
    public boolean setPlayerChatColor(Player player, String colorId) {
        if (plugin.getChatColorManager() == null) {
            return false;
        }
        
        if (colorId == null) {
            plugin.getChatColorManager().setPlayerColor(player, null);
            return true;
        }
        
        var preset = plugin.getChatColorManager().getPreset(colorId);
        if (preset == null) {
            return false;
        }
        
        if (!player.hasPermission(preset.permission())) {
            return false;
        }
        
        plugin.getChatColorManager().setPlayerColor(player, colorId);
        return true;
    }
    
    /**
     * Send a formatted message to a player using RexChat's color system
     * 
     * @param player The player
     * @param message Message with color codes (&, &#RRGGBB, or MiniMessage)
     */
    public void sendFormattedMessage(Player player, String message) {
        cc.rexsystems.rexChat.utils.MessageUtils.sendMessage(player, message);
    }
    
    /**
     * Format a message using RexChat's color system
     * 
     * @param message Message with color codes
     * @return Formatted component
     */
    public net.kyori.adventure.text.Component formatMessage(String message) {
        return cc.rexsystems.rexChat.utils.ColorUtils.parseComponent(message);
    }
    
    /**
     * Get the RexChat plugin instance
     * 
     * @return RexChat plugin instance
     */
    public RexChat getPlugin() {
        return plugin;
    }
}
