package cc.rexsystems.rexChat.chat;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.utils.MessageFormatter;
import cc.rexsystems.rexChat.utils.ColorUtils;
import cc.rexsystems.rexChat.utils.MessageUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Paper-specific chat listener using AsyncChatEvent. Registered only when the
 * class exists.
 */
public class PaperChatListener implements Listener {
    private final RexChat plugin;
    private final ChatManager chatManager;
    private final MessageFormatter formatter;

    public PaperChatListener(RexChat plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
        this.formatter = new MessageFormatter(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChatPaper(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (chatManager.isChatMuted() && !player.hasPermission("rexchat.bypass")) {
            String mutedMessage = plugin.getConfigManager().getConfig()
                    .getString("chat-management.mute.muted-message", "%rc_prefix%&#ff0000The chat is currently muted.");
            String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
            mutedMessage = mutedMessage.replace("%rc_prefix%", prefix);
            MessageUtils.sendMessage(player, mutedMessage);
            event.setCancelled(true);
            return;
        }

        boolean formatEnabled = plugin.getConfigManager().getConfig().getBoolean("chat-format.enabled", true);
        if (!formatEnabled)
            return;

        // Compute plain message and trigger mention effects (sound/notify)
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        java.util.Set<Player> targets = cc.rexsystems.rexChat.utils.MentionUtils.findMentionedPlayers(raw,
                plugin.getConfigManager().getConfig());
        cc.rexsystems.rexChat.utils.MentionUtils.playMentionEffects(plugin, player, targets);

        // If 1.19+ and chat-reporting.disable is true, convert to system messages
        boolean disableReporting = plugin.getConfigManager().getConfig().getBoolean("chat-reporting.disable", true);
        boolean is119Plus = MessageUtils.isMinecraftVersionAtLeast(1, 19);
        if (disableReporting && is119Plus) {
            String msg = raw;
            if (!player.hasPermission("rexchat.chatcolor")) {
                msg = ColorUtils.stripColors(msg);
            }
            event.setCancelled(true);
            formatter.sendFormattedChat(player, msg);
            return;
        }

        // Default: Use Paper's renderer to honor chat-format.format
        event.renderer((source, displayName, message, viewer) -> {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            return formatter.buildFormattedComponent(source, plain);
        });
    }
}