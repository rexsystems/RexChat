package me.rexsystems.rexChat.hooks.discord;

import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.hooks.DiscordEmbedFactory;
import me.rexsystems.rexChat.hooks.image.InventoryImageRenderer;
import me.rexsystems.rexChat.utils.VaultEconomyUtils;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DiscordSRV-side handler for outbound chat messages.
 *
 * <p>Subscribes to {@link GameChatMessagePreProcessEvent} and rewrites the
 * relayed text the same way InteractiveChat-DiscordSRV-Addon's
 * {@code OutboundToDiscordEvents} does:
 *
 * <ol>
 *   <li>Replace each {@code [item]} / {@code [inv]} / {@code [ec]} occurrence
 *       with a friendly text label (e.g. {@code [Diamond Sword × 32]}).</li>
 *   <li>Render the corresponding rich embed / PNG attachment up-front and
 *       register it in {@link PendingPreviewRegistry}, getting back an id.</li>
 *   <li>Append a {@code <RXC=ID>} marker to the message so DiscordSRV-relayed
 *       text carries the id through to JDA.</li>
 * </ol>
 *
 * <p>Once DiscordSRV delivers the message,
 * {@link DiscordJDAListener#onMessageReceived} matches the markers and edits
 * the message in place to attach embeds + files — just like ICDA does.
 *
 * <p>The {@code [bal]} token is rewritten inline only (no embed/attachment).
 */
public final class OutboundChatListener {

    private final RexChat plugin;
    private final InventoryImageRenderer renderer;
    private boolean subscribed;

    public OutboundChatListener(RexChat plugin, InventoryImageRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    public void register() {
        if (subscribed) return;
        try {
            github.scarsz.discordsrv.DiscordSRV.api.subscribe(this);
            subscribed = true;
        } catch (Throwable t) {
            plugin.getLogUtils().warning("Failed to subscribe to DiscordSRV API: " + t.getMessage());
        }
    }

    public void unregister() {
        if (!subscribed) return;
        try {
            github.scarsz.discordsrv.DiscordSRV.api.unsubscribe(this);
        } catch (Throwable ignored) {
        }
        subscribed = false;
    }

    @Subscribe(priority = ListenerPriority.NORMAL)
    public void onChatPreProcess(GameChatMessagePreProcessEvent event) {
        try {
            FileConfiguration cfg = plugin.getConfigManager().getConfig();
            if (!cfg.getBoolean("chat-discord.enabled", true)) return;

            Player sender = event.getPlayer();
            if (sender == null) return;

            String original = event.getMessage();
            if (original == null || original.isEmpty()) return;

            StringBuilder markerSuffix = new StringBuilder();
            String rewritten = original;

            // ----- [item] -----
            List<String> itemTokens = tokens(cfg, "item",
                    "[item]", "[i]", "{item}", "{i}");
            if (containsAnyToken(rewritten, itemTokens)) {
                ItemStack hand = sender.getInventory().getItemInMainHand();
                String label = itemLabel(hand);
                rewritten = replaceTokens(rewritten, itemTokens, label);

                if (cfg.getBoolean("chat-discord.previews.item", true)
                        && hand != null && hand.getType() != Material.AIR) {
                    // Render a 256×256 icon (iso cube for blocks, flat texture
                    // for items) and attach it so the embed shows it LARGE
                    // via setImage, instead of as the small corner thumbnail.
                    int iconPx = cfg.getInt("chat-discord.embeds.item.icon-pixels", 256);
                    BufferedImage icon = renderer.renderItemIcon(hand, iconPx);
                    byte[] iconPng = toPng(icon);
                    String fileName = null;
                    if (iconPng != null) {
                        fileName = "item-" + safeName(sender.getName()) + ".png";
                    }
                    MessageEmbed embed = DiscordEmbedFactory.itemEmbed(sender, hand, cfg, fileName);
                    if (embed != null) {
                        int id = PendingPreviewRegistry.register(
                                fileName != null
                                    ? PendingPreview.itemWithIcon(sender.getName(),
                                            headUrl(sender), embed, iconPng, fileName)
                                    : PendingPreview.item(sender.getName(),
                                            headUrl(sender), embed));
                        markerSuffix.append(PendingPreviewRegistry.marker(id));
                    }
                }
            }

            // ----- [inv] -----
            List<String> invTokens = tokens(cfg, "inventory",
                    "[inventory]", "[inv]", "{inventory}", "{inv}");
            if (containsAnyToken(rewritten, invTokens)) {
                rewritten = replaceTokens(rewritten, invTokens,
                        cfg.getString("chat-discord.labels.inventory", "[Inventory]"));

                if (cfg.getBoolean("chat-discord.previews.inventory", true)) {
                    String title = cfg.getString("chat-discord.embeds.inventory.title",
                            "{player}'s inventory").replace("{player}", sender.getName());
                    BufferedImage img = renderer.renderPlayerInventory(sender, title);
                    byte[] png = toPng(img);
                    if (png != null) {
                        String fileName = "inventory-" + safeName(sender.getName()) + ".png";
                        MessageEmbed embed = imageEmbed(
                                sender,
                                cfg.getString("chat-discord.embeds.inventory.color", "#57F287"),
                                title,
                                fileName);
                        int id = PendingPreviewRegistry.register(
                                PendingPreview.inventory(sender.getName(), headUrl(sender),
                                        embed, png, fileName));
                        markerSuffix.append(PendingPreviewRegistry.marker(id));
                    }
                }
            }

            // ----- [ec] -----
            List<String> ecTokens = tokens(cfg, "enderchest",
                    "[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}");
            if (containsAnyToken(rewritten, ecTokens)) {
                rewritten = replaceTokens(rewritten, ecTokens,
                        cfg.getString("chat-discord.labels.enderchest", "[Ender Chest]"));

                if (cfg.getBoolean("chat-discord.previews.enderchest", true)) {
                    String title = cfg.getString("chat-discord.embeds.enderchest.title",
                            "{player}'s ender chest").replace("{player}", sender.getName());
                    ItemStack[] contents = sender.getEnderChest().getContents();
                    BufferedImage img = renderer.renderEnderChest(contents, title);
                    byte[] png = toPng(img);
                    if (png != null) {
                        String fileName = "enderchest-" + safeName(sender.getName()) + ".png";
                        MessageEmbed embed = imageEmbed(
                                sender,
                                cfg.getString("chat-discord.embeds.enderchest.color", "#9B59B6"),
                                title,
                                fileName);
                        int id = PendingPreviewRegistry.register(
                                PendingPreview.enderChest(sender.getName(), headUrl(sender),
                                        embed, png, fileName));
                        markerSuffix.append(PendingPreviewRegistry.marker(id));
                    }
                }
            }

            // ----- [bal] -----
            List<String> balTokens = tokens(cfg, "balance",
                    "[balance]", "[bal]", "[money]", "{balance}", "{bal}", "{money}");
            if (containsAnyToken(rewritten, balTokens)) {
                String balanceText = VaultEconomyUtils.isAvailable()
                        ? safeFormat(VaultEconomyUtils.getBalance(sender))
                        : cfg.getString("chat-discord.labels.balance-unavailable",
                                "(balance unavailable)");
                rewritten = replaceTokens(rewritten, balTokens, balanceText);
            }

            if (markerSuffix.length() > 0) {
                rewritten = rewritten + " " + markerSuffix;
            }

            if (!rewritten.equals(original)) {
                event.setMessage(rewritten);
            }
        } catch (Throwable t) {
            plugin.getLogUtils().warning("DiscordSRV pre-process handler failed: " + t.getMessage());
        }
    }

    // ---------- helpers ----------

    /** Build a [Display Name × Amount] label for the given held item. */
    private static String itemLabel(ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) {
            return "[empty hand]";
        }
        String name = DiscordEmbedFactory.itemDisplayName(hand);
        return "[" + name + (hand.getAmount() > 1 ? " \u00d7 " + hand.getAmount() : "") + "]";
    }

    /** Build a minimal embed that just shows the rendered PNG referenced by {@code fileName}. */
    private static MessageEmbed imageEmbed(Player sender, String hexColor, String title, String fileName) {
        return new EmbedBuilder()
                .setColor(parseColor(hexColor))
                .setAuthor(sender.getName(), null, headUrl(sender))
                .setTitle(title)
                .setImage("attachment://" + fileName)
                .build();
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

    private static String safeName(String name) {
        if (name == null) return "player";
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static String safeFormat(Double bal) {
        if (bal == null) return "?";
        return VaultEconomyUtils.format(bal);
    }

    private static List<String> tokens(FileConfiguration cfg, String key, String... defaults) {
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
