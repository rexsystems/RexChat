package me.rexsystems.rexChat.hooks;

import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;

import me.rexsystems.rexChat.utils.VaultEconomyUtils;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds JDA {@link MessageEmbed} objects for chat-preview tokens.
 * Lives under {@code hooks/} so its DiscordSRV-relocated JDA imports are
 * isolated from the rest of the plugin.
 */
final class DiscordEmbedFactory {

    private DiscordEmbedFactory() {}

    // ---------- Item ----------

    static MessageEmbed itemEmbed(Player sender, ItemStack item, FileConfiguration cfg) {
        if (item == null || item.getType() == Material.AIR) return null;

        String title = cfg.getString("chat-discord.embeds.item.title", "{player}'s item")
                .replace("{player}", sender.getName());

        String displayName = itemDisplayName(item);
        int amount = item.getAmount();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(parseColor(cfg.getString("chat-discord.embeds.item.color", "#5865F2")))
                .setAuthor(sender.getName(), null, headUrl(sender))
                .setTitle(title)
                .setDescription("**" + escape(displayName) + "**" + (amount > 1 ? " × " + amount : ""));

        // Material technical info
        eb.addField("Material", "`" + item.getType().name().toLowerCase() + "`", true);
        eb.addField("Amount", String.valueOf(amount), true);

        // Durability for damageable items
        try {
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
                org.bukkit.inventory.meta.Damageable d =
                        (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
                if (d.hasDamage() || item.getType().getMaxDurability() > 0) {
                    int max = item.getType().getMaxDurability();
                    int remaining = max - d.getDamage();
                    if (max > 0) {
                        eb.addField("Durability", remaining + " / " + max, true);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Enchantments
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty() && item.hasItemMeta()
                && item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
            enchants = ((org.bukkit.inventory.meta.EnchantmentStorageMeta) item.getItemMeta())
                    .getStoredEnchants();
        }
        if (!enchants.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("• ").append(prettyEnchantName(e.getKey())).append(' ').append(e.getValue());
            }
            eb.addField("Enchantments", sb.toString(), false);
        }

        // Lore
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                List<String> lore = meta.getLore();
                StringBuilder sb = new StringBuilder();
                int lines = Math.min(lore.size(), 10);
                for (int i = 0; i < lines; i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(escape(stripColors(lore.get(i))));
                }
                if (lore.size() > 10) sb.append("\n… (+").append(lore.size() - 10).append(" more)");
                eb.addField("Lore", sb.toString(), false);
            }
        }

        // Item icon
        String urlTpl = cfg.getString("chat-discord.embeds.item.image-url-template",
                "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/master/assets/minecraft/textures/item/{material}.png");
        String url = urlTpl.replace("{material}", item.getType().name().toLowerCase());
        eb.setThumbnail(url);

        return eb.build();
    }

    // ---------- Inventory ----------

    static MessageEmbed inventoryEmbed(Player sender, FileConfiguration cfg) {
        PlayerInventory inv = sender.getInventory();

        String title = cfg.getString("chat-discord.embeds.inventory.title", "{player}'s inventory")
                .replace("{player}", sender.getName());

        // Aggregate counts so 64+64+32 = "Cobblestone × 160"
        Map<String, Integer> counts = new LinkedHashMap<>();
        ItemStack[] storage = inv.getStorageContents();
        for (ItemStack s : storage) addCount(counts, s);

        String hotbar = formatRange(storage, 0, 9);
        String mainPart = formatRange(storage, 9, 36);
        String armor = formatArmor(inv);
        String offhand = formatItem(inv.getItemInOffHand());

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(parseColor(cfg.getString("chat-discord.embeds.inventory.color", "#57F287")))
                .setAuthor(sender.getName(), null, headUrl(sender))
                .setTitle(title);

        if (!hotbar.isEmpty())  eb.addField("Hotbar",  truncate(hotbar,  1024), false);
        if (!mainPart.isEmpty()) eb.addField("Main",    truncate(mainPart, 1024), false);
        if (!armor.isEmpty())   eb.addField("Armor",   truncate(armor,   1024), true);
        if (!offhand.isEmpty()) eb.addField("Offhand", truncate(offhand, 1024), true);

        if (eb.getFields().isEmpty()) {
            eb.setDescription("*(empty inventory)*");
        }

        return eb.build();
    }

    // ---------- Ender chest ----------

    static MessageEmbed enderChestEmbed(Player sender, FileConfiguration cfg) {
        String title = cfg.getString("chat-discord.embeds.enderchest.title", "{player}'s ender chest")
                .replace("{player}", sender.getName());

        ItemStack[] contents = sender.getEnderChest().getContents();
        String list = formatRange(contents, 0, contents.length);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(parseColor(cfg.getString("chat-discord.embeds.enderchest.color", "#9B59B6")))
                .setAuthor(sender.getName(), null, headUrl(sender))
                .setTitle(title);

        if (list.isEmpty()) {
            eb.setDescription("*(empty)*");
        } else {
            eb.setDescription(truncate(list, 4000));
        }
        return eb.build();
    }

    // ---------- Balance (text only, no embed needed in current design) ----------

    static String balanceText(Player sender) {
        if (!VaultEconomyUtils.isAvailable()) return "(balance unavailable)";
        Double bal = VaultEconomyUtils.getBalance(sender);
        if (bal == null) return "?";
        return VaultEconomyUtils.format(bal);
    }

    // ---------- helpers ----------

    static String itemDisplayName(ItemStack item) {
        try {
            if (item.hasItemMeta() && item.getItemMeta() != null
                    && item.getItemMeta().hasDisplayName()) {
                return stripColors(item.getItemMeta().getDisplayName());
            }
        } catch (Throwable ignored) {}
        return prettyMaterialName(item.getType());
    }

    private static String prettyMaterialName(Material mat) {
        String[] parts = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String prettyEnchantName(Enchantment ench) {
        try {
            String key = ench.getKey().getKey();
            String[] parts = key.split("_");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            return sb.toString();
        } catch (Throwable t) {
            return ench.toString();
        }
    }

    private static void addCount(Map<String, Integer> counts, ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return;
        String name = itemDisplayName(s);
        counts.merge(name, s.getAmount(), Integer::sum);
    }

    private static String formatRange(ItemStack[] arr, int from, int to) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = from; i < Math.min(to, arr.length); i++) addCount(counts, arr[i]);
        if (counts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(escape(e.getKey()));
            if (e.getValue() > 1) sb.append(" × ").append(e.getValue());
        }
        return sb.toString();
    }

    private static String formatArmor(PlayerInventory inv) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        addCount(counts, inv.getHelmet());
        addCount(counts, inv.getChestplate());
        addCount(counts, inv.getLeggings());
        addCount(counts, inv.getBoots());
        if (counts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(escape(e.getKey()));
        }
        return sb.toString();
    }

    private static String formatItem(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return "";
        String name = itemDisplayName(s);
        return "• " + escape(name) + (s.getAmount() > 1 ? " × " + s.getAmount() : "");
    }

    private static String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    /** Escape Discord markdown so item names with * or _ render literally. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~")
                .replace("|", "\\|")
                .replace(">", "\\>");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private static Color parseColor(String hex) {
        try {
            if (hex == null) return new Color(0x5865F2);
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(Integer.parseInt(h, 16));
        } catch (Throwable t) {
            return new Color(0x5865F2);
        }
    }

    private static String headUrl(Player p) {
        try {
            return "https://mc-heads.net/avatar/" + p.getUniqueId() + "/64";
        } catch (Throwable t) {
            return null;
        }
    }
}
