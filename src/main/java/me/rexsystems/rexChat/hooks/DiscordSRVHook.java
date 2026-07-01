package me.rexsystems.rexChat.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.hooks.discord.DiscordJDAListener;
import me.rexsystems.rexChat.hooks.discord.OutboundChatListener;
import me.rexsystems.rexChat.hooks.discord.PendingPreviewRegistry;
import me.rexsystems.rexChat.hooks.image.GuiTextureCache;
import me.rexsystems.rexChat.hooks.image.InventoryImageRenderer;
import me.rexsystems.rexChat.hooks.image.ItemTextureCache;
import me.rexsystems.rexChat.hooks.image.PlayerBodyRenderer;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.TimeUnit;

/**
 * Bridge between RexChat and DiscordSRV.
 *
 * <p>The integration is structured the same way
 * InteractiveChat-DiscordSRV-Addon does it:
 *
 * <ol>
 *   <li>An {@link OutboundChatListener} is subscribed to DSRV's
 *       {@code GameChatMessagePreProcessEvent}; it rewrites tokens, queues
 *       embeds/PNGs and appends {@code <RXC=N>} markers to the relayed
 *       text.</li>
 *   <li>Once DSRV's JDA is ready, a {@link DiscordJDAListener} is registered
 *       on it; that listener catches the relayed message coming back from
 *       Discord and edits it in place to attach the rendered previews.</li>
 *   <li>{@link PendingPreviewRegistry} is the static handover between the two
 *       — a snapshot of the rendered preview keyed by an int id.</li>
 * </ol>
 *
 * <p>This class only references DSRV / JDA types from inside its package
 * (and {@code hooks.discord}), so the rest of RexChat can hold a nullable
 * reference to it without risking class-loading errors when DiscordSRV is
 * absent.
 */
public final class DiscordSRVHook {

    private final RexChat plugin;
    private final ItemTextureCache textureCache;
    private final GuiTextureCache guiCache;
    private final PlayerBodyRenderer bodyRenderer;
    private final InventoryImageRenderer renderer;
    private final OutboundChatListener outboundListener;
    private final DiscordJDAListener jdaListener;
    private final ReadyHandler readyHandler;
    private boolean jdaListenerRegistered;
    private ScheduledTask cleanupTask;

    public DiscordSRVHook(RexChat plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfigManager().getConfig();

        this.textureCache = new ItemTextureCache(plugin.getDataFolder(),
                cfg.getString("chat-discord.images.texture-base-url", ItemTextureCache.DEFAULT_BASE_URL));
        // GuiTextureCache + BlockIconRenderer use whatever base URL the item cache
        // resolved (so {version} placeholders are honoured exactly once).
        String resolved = textureCache.getBaseUrl();
        this.guiCache = new GuiTextureCache(plugin.getDataFolder(), resolved);
        this.bodyRenderer = new PlayerBodyRenderer(plugin.getDataFolder(), textureCache);
        this.renderer = new InventoryImageRenderer(textureCache, guiCache, bodyRenderer);

        plugin.getLogUtils().info("RexChat texture base URL: " + resolved);

        // Wire debug logging through every renderer when `chat-discord.debug`
        // is enabled. Operators can flip this on temporarily to see exactly
        // which textures hit / miss and which blocks fall back.
        boolean debugEnabled = cfg.getBoolean("chat-discord.debug", false);
        if (debugEnabled) {
            java.util.function.Consumer<String> sink = msg ->
                    plugin.getLogUtils().info("[chat-discord] " + msg);
            textureCache.setDebug(sink);
            renderer.getBlockIcons().setDebug(sink);
            bodyRenderer.setDebug(sink);
            plugin.getLogUtils().info("Discord preview debug logging enabled.");
        }

        this.outboundListener = new OutboundChatListener(plugin, renderer);
        this.jdaListener = new DiscordJDAListener(plugin);
        this.readyHandler = new ReadyHandler();

        // Subscribe to DSRV — both for chat events and the ready hook.
        outboundListener.register();
        try {
            DiscordSRV.api.subscribe(readyHandler);
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to subscribe to DiscordSRV ready event: "
                    + t.getMessage());
        }

        // If JDA is already up by the time we initialize (e.g. /reload), wire it now.
        if (DiscordSRV.isReady) {
            registerJdaListener();
        }

        // Periodic cleanup of orphaned previews (e.g. message was filtered/cancelled).
        try {
            this.cleanupTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    task -> PendingPreviewRegistry.cleanupExpired(),
                    30, 30, TimeUnit.SECONDS);
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to schedule preview cleanup: " + t.getMessage());
        }
    }

    /** Tear everything down. Safe to call multiple times. */
    public void shutdown() {
        try {
            DiscordSRV.api.unsubscribe(readyHandler);
        } catch (Throwable ignored) {
        }
        outboundListener.unregister();
        if (jdaListenerRegistered) {
            jdaListener.unregister();
            jdaListenerRegistered = false;
        }
        PendingPreviewRegistry.clear();
        if (cleanupTask != null) {
            try {
                cleanupTask.cancel();
            } catch (Throwable ignored) {
            }
            cleanupTask = null;
        }
    }

    private synchronized void registerJdaListener() {
        if (jdaListenerRegistered) return;
        jdaListener.register();
        jdaListenerRegistered = true;
        plugin.getLogUtils().info("RexChat DiscordSRV JDA listener attached.");
    }

    /**
     * DSRV doesn't expose JDA until the bot has finished logging in. We listen
     * for {@link DiscordReadyEvent} so we can attach our JDA listener as soon
     * as it's safe — exactly like ICDA's {@code DiscordReadyEvents}.
     */
    private final class ReadyHandler {
        @Subscribe(priority = ListenerPriority.HIGHEST)
        public void onDiscordReady(DiscordReadyEvent event) {
            registerJdaListener();
        }
    }
}
