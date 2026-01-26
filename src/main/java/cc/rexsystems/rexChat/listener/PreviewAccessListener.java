package cc.rexsystems.rexChat.listener;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Grants preview access tokens when players click on [item] or [inventory] in
 * chat.
 */
public class PreviewAccessListener implements Listener {

    private final RexChat plugin;

    public PreviewAccessListener(RexChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase();

        // Grant access when /rexchat item <player> is used
        if (cmd.startsWith("/rexchat item ")) {
            String[] parts = event.getMessage().split(" ");
            if (parts.length >= 3) {
                Player viewer = event.getPlayer();
                String targetName = parts[2];
                plugin.getPreviewAccessManager().grantAccess(viewer, targetName, "item");
            }
        }

        // Grant access when /rexchat inv <player> is used
        else if (cmd.startsWith("/rexchat inv ")) {
            String[] parts = event.getMessage().split(" ");
            if (parts.length >= 3) {
                Player viewer = event.getPlayer();
                String targetName = parts[2];
                plugin.getPreviewAccessManager().grantAccess(viewer, targetName, "inv");
            }
        }
    }
}
