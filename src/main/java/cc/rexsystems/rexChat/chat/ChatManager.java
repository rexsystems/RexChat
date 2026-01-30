package cc.rexsystems.rexChat.chat;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.utils.MessageUtils;
import cc.rexsystems.rexChat.utils.MessageFormatter;
import cc.rexsystems.rexChat.utils.ColorUtils;
import cc.rexsystems.rexChat.utils.PapiUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import net.kyori.adventure.text.Component;

public class ChatManager implements Listener {
    private final RexChat plugin;
    private boolean chatMuted;
    private final MessageFormatter formatter;
    private final java.util.Map<java.util.UUID, Long> lastCommandAt = new java.util.concurrent.ConcurrentHashMap<>();
    private static final boolean DEBUG_CHAT = false; // disable debugging
    private final boolean hasPaperAsyncEvent;
    private final java.util.Map<java.util.UUID, ChatStamp> lastChat = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatManager(RexChat plugin) {
        this.plugin = plugin;
        this.chatMuted = plugin.getDataManager().getData().getBoolean("chat.muted", false);
        this.formatter = new MessageFormatter(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        boolean present;
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            present = true;
        } catch (Throwable t) {
            present = false;
        }
        this.hasPaperAsyncEvent = present;
        if (hasPaperAsyncEvent) {
            try {
                Bukkit.getPluginManager().registerEvents(new PaperChatListener(plugin, this), plugin);
            } catch (Throwable ignored) {
                // If registration fails, legacy fallback (AsyncPlayerChatEvent) will handle
                // formatting
            }
        }
        dbg("ChatManager initialized. chatMuted=" + chatMuted);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSyncChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        dbg("PlayerChatEvent: player=" + player.getName() + ", msg='" + event.getMessage() + "' thread="
                + Thread.currentThread().getName());

        // Skip mute check if Paper listener handles it
        if (hasPaperAsyncEvent) {
            // Paper listener handles mute check, don't duplicate
            return;
        }

        if (chatMuted && !player.hasPermission("rexchat.bypass")) {
            String mutedMessage = plugin.getConfigManager().getConfig()
                    .getString("chat-management.mute.muted-message", "%rc_prefix%&#ff0000The chat is currently muted.");
            sendMessage(player, mutedMessage);
            event.setCancelled(true);
            dbg("Chat muted -> cancel PlayerChatEvent");
            return;
        }

        boolean formatEnabled = plugin.getConfigManager().getConfig().getBoolean("chat-format.enabled", true);
        if (!formatEnabled)
            return;

        if (cc.rexsystems.rexChat.utils.MessageUtils.isLegacy()) {
            String original = event.getMessage();
            if (!shouldProcessChat(player, original)) {
                event.setCancelled(true);
                return;
            }
            String msg = original;
            if (!player.hasPermission("rexchat.chatcolor")) {
                msg = cc.rexsystems.rexChat.utils.ColorUtils.stripColors(msg);
            }
            // Play mention effects (sound/notify) based on the original message
            java.util.Set<Player> targets = cc.rexsystems.rexChat.utils.MentionUtils.findMentionedPlayers(original,
                    plugin.getConfigManager().getConfig());
            cc.rexsystems.rexChat.utils.MentionUtils.playMentionEffects(plugin, player, targets);
            event.setCancelled(true);
            formatter.sendFormattedChat(player, msg);
            dbg("Formatted legacy sync chat for player=" + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        dbg("AsyncPlayerChatEvent: player=" + player.getName() + ", msg='" + event.getMessage() + "' thread="
                + Thread.currentThread().getName());

        // Skip mute check if Paper listener handles it
        if (hasPaperAsyncEvent) {
            // Paper listener handles mute check, don't duplicate
            return;
        }

        if (chatMuted && !player.hasPermission("rexchat.bypass")) {
            String mutedMessage = plugin.getConfigManager().getConfig()
                    .getString("chat-management.mute.muted-message", "%rc_prefix%&#ff0000The chat is currently muted.");
            sendMessage(player, mutedMessage);
            event.setCancelled(true);
            dbg("Chat muted -> cancel AsyncPlayerChatEvent");
            return;
        }
        boolean formatEnabled = plugin.getConfigManager().getConfig().getBoolean("chat-format.enabled", true);
        if (!formatEnabled)
            return;

        if (!hasPaperAsyncEvent) {
            String original = event.getMessage();
            if (!shouldProcessChat(player, original)) {
                event.setCancelled(true);
                return;
            }
            String msg = original;
            if (!player.hasPermission("rexchat.chatcolor")) {
                msg = cc.rexsystems.rexChat.utils.ColorUtils.stripColors(msg);
            }
            // Play mention effects (sound/notify) based on the original message
            java.util.Set<Player> targets = cc.rexsystems.rexChat.utils.MentionUtils.findMentionedPlayers(original,
                    plugin.getConfigManager().getConfig());
            cc.rexsystems.rexChat.utils.MentionUtils.playMentionEffects(plugin, player, targets);
            // Cancel vanilla formatting and broadcast our formatted string
            event.setCancelled(true);
            formatter.sendFormattedChat(player, msg);
            dbg("Formatted fallback chat for player=" + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        lastCommandAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        dbg("PlayerCommandPreprocessEvent: " + event.getPlayer().getName() + " -> " + event.getMessage());
    }

    private void dbg(String msg) {
        if (!DEBUG_CHAT)
            return;
        plugin.getLogger().info("[RexChat-Debug] " + msg);
    }

    private static class ChatStamp {
        final String msg;
        final long at;

        ChatStamp(String msg, long at) {
            this.msg = msg;
            this.at = at;
        }
    }

    private boolean shouldProcessChat(Player player, String msg) {
        long now = System.currentTimeMillis();
        ChatStamp prev = lastChat.get(player.getUniqueId());
        if (prev != null) {
            if (msg.equals(prev.msg) && (now - prev.at) < 800) {
                return false;
            }
        }
        lastChat.put(player.getUniqueId(), new ChatStamp(msg, now));
        return true;
    }

    public void clearChat(String executor) {
        int lines = plugin.getConfigManager().getConfig().getInt("chat-management.clear.lines", 100);

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < lines; i++) {
                player.sendMessage(" ");
            }
        }

        String clearMessage = plugin.getConfigManager().getConfig()
                .getString("chat-management.clear.clear-message",
                        "%rc_prefix%&#00ff00The chat has been cleared by {player}");
        broadcastMessage(clearMessage.replace("{player}", executor));
        
        // Fire event
        Bukkit.getPluginManager().callEvent(new cc.rexsystems.rexChat.api.events.ChatClearEvent(executor));
    }

    public void toggleMute(String executor) {
        chatMuted = !chatMuted;

        plugin.getDataManager().getData().set("chat.muted", chatMuted);
        plugin.getDataManager().saveData();

        String message = chatMuted
                ? plugin.getConfigManager().getConfig().getString("chat-management.mute.mute-announcement",
                        "%rc_prefix%&#ff0000The chat has been muted by {player}")
                : plugin.getConfigManager().getConfig().getString("chat-management.mute.unmute-announcement",
                        "%rc_prefix%&#00ff00The chat has been unmuted by {player}");

        broadcastMessage(message.replace("{player}", executor));
        
        // Fire event
        Bukkit.getPluginManager().callEvent(new cc.rexsystems.rexChat.api.events.ChatMuteEvent(chatMuted, executor));
    }

    private void sendMessage(Player player, String message) {
        String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
        message = message.replace("%rc_prefix%", prefix);
        MessageUtils.sendMessage(player, message);
    }

    private void broadcastMessage(String message) {
        String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
        message = message.replace("%rc_prefix%", prefix);
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtils.sendMessage(player, message);
        }
        MessageUtils.sendMessage(Bukkit.getConsoleSender(), message);
    }

    public boolean isChatMuted() {
        return chatMuted;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Custom join message handling
        String joinMsg = plugin.getConfigManager().getConfig().getString("join-leave.join-message", null);
        if (joinMsg != null) {
            if (joinMsg.trim().isEmpty()) {
                // Legacy: setJoinMessage, Modern: joinMessage(Component)
                if (MessageUtils.isLegacy()) {
                    event.setJoinMessage(null);
                } else {
                    event.joinMessage(null);
                }
            } else {
                String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                joinMsg = joinMsg.replace("%rc_prefix%", prefix)
                        .replace("{player}", event.getPlayer().getName())
                        .replace("{display_name}", event.getPlayer().getDisplayName());
                joinMsg = PapiUtils.apply(event.getPlayer(), joinMsg);
                if (MessageUtils.isLegacy()) {
                    event.setJoinMessage(ColorUtils.translateLegacyColors(joinMsg));
                } else {
                    event.joinMessage(ColorUtils.parseComponent(joinMsg));
                }
            }
        }

        boolean updatesEnabled = plugin.getConfigManager().getConfig().getBoolean("update-checker.enabled", true);
        boolean notifyOps = plugin.getConfigManager().getConfig().getBoolean("update-checker.notify-ops-on-join", true);
        if (!updatesEnabled || !notifyOps)
            return;

        Player player = event.getPlayer();
        String perm = plugin.getConfigManager().getConfig().getString("update-checker.permission", "rexchat.admin");
        boolean canSee = player.isOp() || player.hasPermission(perm);
        if (!canSee)
            return;

        if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
            String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
            String msg = plugin.getConfigManager().getConfig().getString("update-checker.message",
                    "%rc_prefix%&fA new version of &cRexChat &fis available: &c{latest}&7 (current: &f{current}&7). &fDownload: &chttps://www.spigotmc.org/resources/rexchat.122562/");
            msg = msg.replace("%rc_prefix%", prefix)
                    .replace("{latest}", String.valueOf(plugin.getUpdateChecker().getLatestVersion()))
                    .replace("{current}", plugin.getDescription().getVersion());
            MessageUtils.sendMessage(player, msg);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up inventory snapshot on quit
        plugin.getInventorySnapshotService().removeSnapshot(event.getPlayer());

        String leaveMsg = plugin.getConfigManager().getConfig().getString("join-leave.leave-message", null);
        if (leaveMsg != null) {
            if (leaveMsg.trim().isEmpty()) {
                if (MessageUtils.isLegacy()) {
                    event.setQuitMessage(null);
                } else {
                    event.quitMessage(null);
                }
            } else {
                String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                leaveMsg = leaveMsg.replace("%rc_prefix%", prefix)
                        .replace("{player}", event.getPlayer().getName())
                        .replace("{display_name}", event.getPlayer().getDisplayName());
                leaveMsg = PapiUtils.apply(event.getPlayer(), leaveMsg);
                if (MessageUtils.isLegacy()) {
                    event.setQuitMessage(ColorUtils.translateLegacyColors(leaveMsg));
                } else {
                    event.quitMessage(ColorUtils.parseComponent(leaveMsg));
                }
            }
        }
    }
}