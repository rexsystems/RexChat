package me.rexsystems.rexChat.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.utils.VaultEconomyUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge between RexChat chat-preview tokens and DiscordSRV.
 *
 * <p>Subscribes to {@link GameChatMessagePreProcessEvent} which DiscordSRV fires
 * for every chat message it relays. We modify the relayed text in place
 * (replacing tokens with friendly labels) and emit rich embeds for previews to
 * the same Discord channel.
 *
 * <p>This avoids double-sends: DiscordSRV's MONITOR listener still picks up
 * Bukkit's chat events even when RexChat cancels them, so RexChat MUST NOT
 * manually relay via {@code processChatMessage}; that would result in two
 * messages.
 *
 * <p>Public methods deliberately use only Bukkit / standard Java types so the
 * class can be referenced (as a nullable field) from elsewhere even when
 * DiscordSRV is not on the classpath. All DiscordSRV / JDA imports stay inside
 * this class so a missing plugin never causes class-loading errors at runtime.
 */
public final class DiscordSRVHook {

    private final RexChat plugin;
    private boolean subscribed;

    public DiscordSRVHook(RexChat plugin) {
        this.plugin = plugin;
        try {
            DiscordSRV.api.subscribe(this);
            this.subscribed = true;
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to subscribe to DiscordSRV API: " + t.getMessage());
        }
    }

    /** Unsubscribe from DiscordSRV. Safe to call multiple times. */
    public void shutdown() {
        if (!subscribed) return;
        try {
            DiscordSRV.api.unsubscribe(this);
        } catch (Throwable ignored) {
        }
        subscribed = false;
    }

    @Subscribe
    public void onChatPreProcess(GameChatMessagePreProcessEvent event) {
        try {
            FileConfiguration cfg = plugin.getConfigManager().getConfig();
            if (!cfg.getBoolean("chat-discord.enabled", true)) return;

            Player sender = event.getPlayer();
            if (sender == null) return;

            String original = event.getMessage();
            if (original == null || original.isEmpty()) return;

            // 1) Rewrite tokens in the relayed text
            String rewritten = rewriteTokensForDiscord(sender, original, cfg);
            if (!rewritten.equals(original)) {
                event.setMessage(rewritten);
            }

            // 2) Resolve the Discord channel for this game-channel
            TextChannel channel = resolveChannel(event.getChannel(), cfg);
            if (channel == null) return;

            // 3) Emit embeds for any preview tokens present in the original message
            if (cfg.getBoolean("chat-discord.previews.item", true)
                    && containsAnyToken(original, getTokens(cfg, "item",
                            "[item]", "[i]", "{item}", "{i}"))) {
                ItemStack hand = sender.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() != org.bukkit.Material.AIR) {
                    MessageEmbed embed = DiscordEmbedFactory.itemEmbed(sender, hand, cfg);
                    if (embed != null) channel.sendMessageEmbeds(embed).queue(null, t -> {});
                }
            }

            if (cfg.getBoolean("chat-discord.previews.inventory", true)
                    && containsAnyToken(original, getTokens(cfg, "inventory",
                            "[inventory]", "[inv]", "{inventory}", "{inv}"))) {
                MessageEmbed embed = DiscordEmbedFactory.inventoryEmbed(sender, cfg);
                if (embed != null) channel.sendMessageEmbeds(embed).queue(null, t -> {});
            }

            if (cfg.getBoolean("chat-discord.previews.enderchest", true)
                    && containsAnyToken(original, getTokens(cfg, "enderchest",
                            "[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}"))) {
                MessageEmbed embed = DiscordEmbedFactory.enderChestEmbed(sender, cfg);
                if (embed != null) channel.sendMessageEmbeds(embed).queue(null, t -> {});
            }
        } catch (Throwable t) {
            // Never let this throw across the DSRV API boundary
            plugin.getLogUtils().warning("DiscordSRV pre-process handler failed: " + t.getMessage());
        }
    }

    // ---------- internals ----------

    private TextChannel resolveChannel(String gameChannel, FileConfiguration cfg) {
        try {
            DiscordSRV dsrv = DiscordSRV.getPlugin();
            if (dsrv == null) return null;

            // Prefer the channel DSRV says this message is going to
            if (gameChannel != null && !gameChannel.isEmpty()) {
                TextChannel c = dsrv.getDestinationTextChannelForGameChannelName(gameChannel);
                if (c != null) return c;
            }

            // Config override
            String configured = cfg.getString("chat-discord.channel", "");
            if (configured != null && !configured.isEmpty()) {
                TextChannel c = dsrv.getDestinationTextChannelForGameChannelName(configured);
                if (c != null) return c;
            }

            return dsrv.getMainTextChannel();
        } catch (Throwable t) {
            return null;
        }
    }

    private String rewriteTokensForDiscord(Player sender, String raw, FileConfiguration cfg) {
        String result = raw;

        // [item] -> [Diamond Sword × 32] or [empty hand]
        ItemStack hand = sender.getInventory().getItemInMainHand();
        String itemLabel;
        if (hand == null || hand.getType() == org.bukkit.Material.AIR) {
            itemLabel = "[empty hand]";
        } else {
            String name = DiscordEmbedFactory.itemDisplayName(hand);
            itemLabel = "[" + name
                    + (hand.getAmount() > 1 ? " \u00d7 " + hand.getAmount() : "") + "]";
        }
        result = replaceTokens(result, getTokens(cfg, "item",
                "[item]", "[i]", "{item}", "{i}"), itemLabel);

        // [inv] -> [Inventory]
        result = replaceTokens(result, getTokens(cfg, "inventory",
                "[inventory]", "[inv]", "{inventory}", "{inv}"), "[Inventory]");

        // [ec] -> [Ender Chest]
        result = replaceTokens(result, getTokens(cfg, "enderchest",
                "[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}"),
                "[Ender Chest]");

        // [bal] -> $1,234.50 (inline)
        String balanceText = VaultEconomyUtils.isAvailable()
                ? safeFormat(VaultEconomyUtils.getBalance(sender))
                : "(balance unavailable)";
        result = replaceTokens(result, getTokens(cfg, "balance",
                "[balance]", "[bal]", "[money]", "{balance}", "{bal}", "{money}"),
                balanceText);

        return result;
    }

    private static String safeFormat(Double bal) {
        if (bal == null) return "?";
        return VaultEconomyUtils.format(bal);
    }

    private static List<String> getTokens(FileConfiguration cfg, String key, String... defaults) {
        List<String> configured = cfg.getStringList("chat-previews.tokens." + key);
        if (configured == null || configured.isEmpty()) {
            return java.util.Arrays.asList(defaults);
        }
        return configured;
    }

    private static boolean containsAnyToken(String message, List<String> tokens) {
        if (message == null || tokens == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            if (t != null && lower.contains(t.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String replaceTokens(String message, List<String> tokens, String replacement) {
        if (message == null || tokens == null) return message;
        for (String t : tokens) {
            if (t == null || t.isEmpty()) continue;
            Pattern p = Pattern.compile(Pattern.quote(t), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(message);
            message = m.replaceAll(Matcher.quoteReplacement(replacement));
        }
        return message;
    }
}
