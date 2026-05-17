package me.rexsystems.rexChat.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.hooks.image.InventoryImageRenderer;
import me.rexsystems.rexChat.hooks.image.ItemTextureCache;
import me.rexsystems.rexChat.utils.VaultEconomyUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge between RexChat chat-preview tokens and DiscordSRV.
 *
 * <p>Subscribes to {@link GameChatMessagePreProcessEvent} which DiscordSRV
 * fires for every chat message it relays. We modify the relayed text in place
 * (replacing tokens with friendly labels) and push rich embeds + rendered PNG
 * attachments for inventory/ender-chest previews to the same Discord channel.
 *
 * <p>This avoids double-sends: DiscordSRV's MONITOR listener still picks up
 * Bukkit's chat events even when RexChat cancels them, so RexChat MUST NOT
 * manually relay via {@code processChatMessage}; that would result in two
 * messages.
 *
 * <p>Public methods deliberately use only Bukkit / standard Java types so the
 * class can be referenced (as a nullable field) from elsewhere even when
 * DiscordSRV is not on the classpath. All DiscordSRV / JDA imports stay inside
 * this package so a missing plugin never causes class-loading errors at
 * runtime.
 */
public final class DiscordSRVHook {

    private final RexChat plugin;
    private final ItemTextureCache textureCache;
    private final InventoryImageRenderer renderer;
    private boolean subscribed;

    public DiscordSRVHook(RexChat plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String texBase = cfg.getString("chat-discord.images.texture-base-url",
                ItemTextureCache.DEFAULT_BASE_URL);
        this.textureCache = new ItemTextureCache(plugin.getDataFolder(), texBase);
        this.renderer = new InventoryImageRenderer(textureCache);

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

            // 3) [item] preview: rich embed with thumbnail + metadata
            if (cfg.getBoolean("chat-discord.previews.item", true)
                    && containsAnyToken(original, getTokens(cfg, "item",
                            "[item]", "[i]", "{item}", "{i}"))) {
                ItemStack hand = sender.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() != org.bukkit.Material.AIR) {
                    MessageEmbed embed = DiscordEmbedFactory.itemEmbed(sender, hand, cfg);
                    if (embed != null) channel.sendMessage("").embed(embed).queue(null, t -> {});
                }
            }

            // 4) [inv] preview: rendered PNG of player inventory
            if (cfg.getBoolean("chat-discord.previews.inventory", true)
                    && containsAnyToken(original, getTokens(cfg, "inventory",
                            "[inventory]", "[inv]", "{inventory}", "{inv}"))) {
                sendInventoryImage(channel, sender, cfg);
            }

            // 5) [ec] preview: rendered PNG of ender chest
            if (cfg.getBoolean("chat-discord.previews.enderchest", true)
                    && containsAnyToken(original, getTokens(cfg, "enderchest",
                            "[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}"))) {
                sendEnderChestImage(channel, sender, cfg);
            }
        } catch (Throwable t) {
            // Never let this throw across the DSRV API boundary
            plugin.getLogUtils().warning("DiscordSRV pre-process handler failed: " + t.getMessage());
        }
    }

    // ---------- image-attachment helpers ----------

    private void sendInventoryImage(TextChannel channel, Player sender, FileConfiguration cfg) {
        try {
            String title = cfg.getString("chat-discord.embeds.inventory.title", "{player}'s inventory")
                    .replace("{player}", sender.getName());
            BufferedImage img = renderer.renderPlayerInventory(sender, title);
            byte[] png = toPng(img);
            if (png == null) return;

            String fileName = "inventory-" + sender.getName() + ".png";
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(parseColor(cfg.getString("chat-discord.embeds.inventory.color", "#57F287")))
                    .setAuthor(sender.getName(), null, headUrl(sender))
                    .setTitle(title)
                    .setImage("attachment://" + fileName);

            channel.sendMessage("")
                    .embed(eb.build())
                    .addFile(new ByteArrayInputStream(png), fileName)
                    .queue(null, t -> {});
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to render inventory image: " + t.getMessage());
        }
    }

    private void sendEnderChestImage(TextChannel channel, Player sender, FileConfiguration cfg) {
        try {
            String title = cfg.getString("chat-discord.embeds.enderchest.title", "{player}'s ender chest")
                    .replace("{player}", sender.getName());
            ItemStack[] contents = sender.getEnderChest().getContents();
            BufferedImage img = renderer.renderEnderChest(contents, title);
            byte[] png = toPng(img);
            if (png == null) return;

            String fileName = "enderchest-" + sender.getName() + ".png";
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(parseColor(cfg.getString("chat-discord.embeds.enderchest.color", "#9B59B6")))
                    .setAuthor(sender.getName(), null, headUrl(sender))
                    .setTitle(title)
                    .setImage("attachment://" + fileName);

            channel.sendMessage("")
                    .embed(eb.build())
                    .addFile(new ByteArrayInputStream(png), fileName)
                    .queue(null, t -> {});
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to render ender chest image: " + t.getMessage());
        }
    }

    private static byte[] toPng(BufferedImage img) {
        if (img == null) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.awt.Color parseColor(String hex) {
        try {
            if (hex == null) return new java.awt.Color(0x5865F2);
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return new java.awt.Color(Integer.parseInt(h, 16));
        } catch (Throwable t) {
            return new java.awt.Color(0x5865F2);
        }
    }

    private static String headUrl(Player p) {
        try {
            return "https://mc-heads.net/avatar/" + p.getUniqueId() + "/64";
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- internals ----------

    private TextChannel resolveChannel(String gameChannel, FileConfiguration cfg) {
        try {
            DiscordSRV dsrv = DiscordSRV.getPlugin();
            if (dsrv == null) return null;

            if (gameChannel != null && !gameChannel.isEmpty()) {
                TextChannel c = dsrv.getDestinationTextChannelForGameChannelName(gameChannel);
                if (c != null) return c;
            }

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
