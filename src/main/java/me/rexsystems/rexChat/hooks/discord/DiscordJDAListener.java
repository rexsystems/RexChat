package me.rexsystems.rexChat.hooks.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ChannelType;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.MessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.dependencies.jda.api.requests.restaction.MessageAction;

import me.rexsystems.rexChat.RexChat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * JDA listener that edits DiscordSRV-relayed chat messages to inline RexChat
 * preview embeds + image attachments.
 *
 * <p>Mirrors {@code OutboundToDiscordEvents.JDAEvents} from ICDA. We register
 * this on DiscordSRV's {@link github.scarsz.discordsrv.dependencies.jda.api.JDA}
 * once it is ready, and catch every chat message sent through DSRV (either as
 * the bot, or as a webhook). When the message body contains
 * {@code <RXC=N>} markers we drained from {@link PendingPreviewRegistry}, we
 * edit the message in-place to remove the markers and attach the previews.
 *
 * <p>Bot-authored messages can be edited directly via JDA. Webhook messages
 * need to go through DiscordSRV's {@code WebhookUtil}; that class is invoked
 * via reflection so a missing/renamed method on a slightly different DSRV
 * build degrades gracefully (we silently skip the edit instead of crashing).
 */
public final class DiscordJDAListener extends ListenerAdapter {

    private final RexChat plugin;

    public DiscordJDAListener(RexChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            if (event.getChannelType() != ChannelType.TEXT) return;

            // Only care about messages we (or DSRV's webhook) authored — never user chat.
            boolean fromSelfBot = event.getAuthor().equals(event.getJDA().getSelfUser());
            boolean fromWebhook = event.isWebhookMessage();
            if (!fromSelfBot && !fromWebhook) return;

            Message message = event.getMessage();
            String original = message.getContentRaw();
            if (original == null || !original.contains(PendingPreviewRegistry.MARKER_PREFIX)) return;

            // Collect ids referenced in the content (in document order).
            List<Integer> ids = new ArrayList<>();
            Matcher m = PendingPreviewRegistry.MARKER_PATTERN.matcher(original);
            while (m.find()) {
                try {
                    ids.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
            if (ids.isEmpty()) return;

            // Strip markers and trim trailing whitespace.
            String cleanText = m.reset().replaceAll("").replaceAll("\\s+$", "");
            if (cleanText.isEmpty()) cleanText = "\u200B"; // zero-width space; Discord forbids empty content

            List<PendingPreview> previews = PendingPreviewRegistry.drain(ids);
            if (previews.isEmpty()) {
                // Markers were stale — still strip them so users don't see the placeholder.
                editPlainText(event, message, cleanText, fromWebhook);
                return;
            }

            applyPreviews(event, message, cleanText, previews, fromWebhook);
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to attach RexChat preview to Discord message: "
                    + t.getMessage());
        }
    }

    // ---------- editing ----------

    private void editPlainText(MessageReceivedEvent event, Message message,
                               String cleanText, boolean fromWebhook) {
        if (fromWebhook) {
            tryWebhookEdit((TextChannel) event.getChannel(),
                    message.getId(), cleanText, Collections.emptyList(),
                    Collections.emptyMap());
        } else {
            try {
                message.editMessage(cleanText).queue(null, t -> {});
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyPreviews(MessageReceivedEvent event, Message message,
                               String cleanText, List<PendingPreview> previews,
                               boolean fromWebhook) {
        // Cap to Discord's 10 attachments / 10 embeds limit.
        List<MessageEmbed> embeds = new ArrayList<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (PendingPreview p : previews) {
            if (embeds.size() < 10 && p.getEmbed() != null) embeds.add(p.getEmbed());
            for (PendingPreview.Attachment att : p.getAttachments()) {
                if (files.size() >= 10) break;
                files.put(att.name, att.bytes);
            }
        }

        if (fromWebhook) {
            Map<String, InputStream> streams = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                streams.put(e.getKey(), new ByteArrayInputStream(e.getValue()));
            }
            boolean ok = tryWebhookEdit((TextChannel) event.getChannel(), message.getId(),
                    cleanText, embeds, streams);
            if (!ok) {
                // Last-ditch fallback: post a follow-up bot message in the same channel so
                // the user at least sees the previews even if we couldn't edit the webhook.
                sendFollowUpBot((TextChannel) event.getChannel(), embeds, files);
            }
            return;
        }

        // Bot-authored message — edit directly via JDA.
        try {
            MessageAction action = message.editMessage(cleanText);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                action = action.addFile(e.getValue(), e.getKey());
            }
            action.setEmbeds(embeds).queue(null, t -> {});
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Bot edit failed, falling back to follow-up: "
                    + t.getMessage());
            sendFollowUpBot((TextChannel) event.getChannel(), embeds, files);
        }
    }

    // ---------- webhook edit (reflective; method shape may vary across DSRV builds) ----------

    /**
     * Try {@code WebhookUtil.editMessage(TextChannel, String, String, Collection, Map, Collection)}
     * via reflection. Returns {@code false} if the call could not be performed; the caller is
     * expected to fall back to a follow-up bot message.
     */
    private boolean tryWebhookEdit(TextChannel channel, String messageId,
                                   String content,
                                   Collection<MessageEmbed> embeds,
                                   Map<String, InputStream> attachments) {
        try {
            Class<?> webhookUtil = Class.forName("github.scarsz.discordsrv.util.WebhookUtil");

            // Try the rich overload first (matches ICDA usage).
            for (Method m : webhookUtil.getDeclaredMethods()) {
                if (!m.getName().equals("editMessage")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 6
                        && TextChannel.class.isAssignableFrom(p[0])
                        && p[1] == String.class
                        && p[2] == String.class
                        && Collection.class.isAssignableFrom(p[3])
                        && Map.class.isAssignableFrom(p[4])
                        && Collection.class.isAssignableFrom(p[5])) {
                    m.setAccessible(true);
                    m.invoke(null, channel, messageId, content,
                            embeds, attachments, Collections.emptyList());
                    return true;
                }
            }

            // Simpler fallback overload: editMessage(channel, id, content, embeds).
            for (Method m : webhookUtil.getDeclaredMethods()) {
                if (!m.getName().equals("editMessage")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 4
                        && TextChannel.class.isAssignableFrom(p[0])
                        && p[1] == String.class
                        && p[2] == String.class
                        && Collection.class.isAssignableFrom(p[3])) {
                    m.setAccessible(true);
                    // Can't pass attachments through the simple overload — caller decides
                    // whether that's good enough.
                    m.invoke(null, channel, messageId, content, embeds);
                    return attachments == null || attachments.isEmpty();
                }
            }

            plugin.getLogUtils().warning(
                    "DiscordSRV WebhookUtil.editMessage(...) not found; webhook chat messages "
                            + "won't be edited with attachments. Falling back to follow-up.");
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogUtils().warning(
                    "DiscordSRV WebhookUtil class not found; cannot edit webhook chat messages.");
            return false;
        } catch (Throwable t) {
            plugin.getLogUtils().warning("WebhookUtil.editMessage failed: " + t.getMessage());
            return false;
        }
    }

    private void sendFollowUpBot(TextChannel channel,
                                 List<MessageEmbed> embeds,
                                 Map<String, byte[]> files) {
        try {
            MessageAction action = channel.sendMessage("\u200B");
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                action = action.addFile(e.getValue(), e.getKey());
            }
            action.setEmbeds(embeds).queue(null, t -> {});
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Follow-up post also failed: " + t.getMessage());
        }
    }

    // ---------- registration helpers ----------

    /** Add this listener to the running DiscordSRV JDA. Idempotent-ish (call once). */
    public void register() {
        try {
            DiscordSRV.getPlugin().getJda().addEventListener(this);
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to register JDA listener: " + t.getMessage());
        }
    }

    /** Remove the listener on shutdown, ignoring any errors. */
    public void unregister() {
        try {
            DiscordSRV.getPlugin().getJda().removeEventListener(this);
        } catch (Throwable ignored) {
        }
    }
}
