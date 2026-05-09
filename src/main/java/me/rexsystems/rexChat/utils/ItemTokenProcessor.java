package me.rexsystems.rexChat.utils;

import me.rexsystems.rexChat.RexChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemTokenProcessor {
    private final RexChat plugin;
    private final ItemDisplayBuilder displayBuilder;

    public ItemTokenProcessor(RexChat plugin) {
        this.plugin = plugin;
        this.displayBuilder = new ItemDisplayBuilder(plugin);
    }

    public Component processTokens(Component component, Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();

        if (!cfg.getBoolean("chat-previews.enabled", true)) {
            return component;
        }

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
        String plainLower = plain.toLowerCase();

        List<String> itemTokens = cfg.getStringList("chat-previews.tokens.item");
        List<String> invTokens = cfg.getStringList("chat-previews.tokens.inventory");
        List<String> ecTokens = cfg.getStringList("chat-previews.tokens.enderchest");
        List<String> balTokens = cfg.getStringList("chat-previews.tokens.balance");

        if (itemTokens.isEmpty()) {
            itemTokens = java.util.Arrays.asList("[item]", "[i]", "{item}", "{i}");
        }
        if (invTokens.isEmpty()) {
            invTokens = java.util.Arrays.asList("[inventory]", "[inv]", "{inventory}", "{inv}");
        }
        if (ecTokens.isEmpty()) {
            ecTokens = java.util.Arrays.asList("[enderchest]", "[ec]", "[echest]", "{enderchest}", "{ec}", "{echest}");
        }
        if (balTokens.isEmpty()) {
            balTokens = java.util.Arrays.asList("[balance]", "[bal]", "[money]", "{balance}", "{bal}", "{money}");
        }

        boolean hasItem = false;
        boolean hasInv = false;
        boolean hasEc = false;
        boolean hasBal = false;

        for (String token : itemTokens) {
            if (plainLower.contains(token.toLowerCase())) {
                hasItem = true;
                break;
            }
        }
        for (String token : invTokens) {
            if (plainLower.contains(token.toLowerCase())) {
                hasInv = true;
                break;
            }
        }
        for (String token : ecTokens) {
            if (plainLower.contains(token.toLowerCase())) {
                hasEc = true;
                break;
            }
        }
        for (String token : balTokens) {
            if (plainLower.contains(token.toLowerCase())) {
                hasBal = true;
                break;
            }
        }

        if (!hasItem && !hasInv && !hasEc && !hasBal) {
            return component;
        }

        if (hasItem) {
            ItemStack hand = player.getInventory().getItemInMainHand();

            if (hand == null || hand.getType() == org.bukkit.Material.AIR) {
                String emptyLabel = cfg.getString("messages.preview.item.empty-label",
                        "&7[&eHold an item to show it&7]");
                Component emptyComp = ColorUtils.parseComponent(emptyLabel);

                for (String token : itemTokens) {
                    component = component.replaceText(TextReplacementConfig.builder()
                            .matchLiteral(token)
                            .replacement(emptyComp)
                            .build());
                }
            } else {
                String itemId = plugin.getItemSnapshotManager().storeItem(hand, player.getName());
                Component itemDisplay = displayBuilder.createItemDisplay(hand, player, itemId);

                for (String token : itemTokens) {
                    component = component.replaceText(TextReplacementConfig.builder()
                            .matchLiteral(token)
                            .replacement(itemDisplay)
                            .build());
                }
            }
        }

        if (hasInv) {
            String invId = plugin.getInventorySnapshotService().storeSnapshotWithId(player);

            String labelTemplate = cfg.getString("messages.preview.inventory.label-template",
                    "&7[&fInventory&7]");
            Component invDisplay = ColorUtils.parseComponent(labelTemplate)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            ColorUtils.parseComponent(cfg.getString("messages.preview.inventory.hover",
                                    "&7Click to view {player}'s inventory")
                                    .replace("{player}", player.getName()))))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/rexchat viewinv " + invId));

            for (String token : invTokens) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(invDisplay)
                        .build());
            }
        }

        if (hasEc) {
            String ecId = plugin.getInventorySnapshotService().storeEnderChestWithId(player);

            String labelTemplate = cfg.getString("messages.preview.enderchest.label-template",
                    "&7[&5Ender Chest&7]");
            Component ecDisplay = ColorUtils.parseComponent(labelTemplate)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            ColorUtils.parseComponent(cfg.getString("messages.preview.enderchest.hover",
                                    "&7Click to view {player}'s ender chest")
                                    .replace("{player}", player.getName()))))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/rexchat viewec " + ecId));

            for (String token : ecTokens) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(ecDisplay)
                        .build());
            }
        }

        if (hasBal) {
            Component balDisplay;

            if (!VaultEconomyUtils.isAvailable()) {
                // Vault/Economy not present: show an unavailable label
                String unavailableLabel = cfg.getString("messages.preview.balance.unavailable-label",
                        "&7[&cBalance unavailable&7]");
                balDisplay = ColorUtils.parseComponent(unavailableLabel);
            } else {
                Double balance = VaultEconomyUtils.getBalance(player);
                if (balance == null) balance = 0.0;

                String formatted = VaultEconomyUtils.format(balance);
                String currency = VaultEconomyUtils.currencyNamePlural();
                if (currency == null) currency = "";

                String labelTemplate = cfg.getString("messages.preview.balance.label-template",
                        "&7[&a{balance}&7]");
                String label = labelTemplate
                        .replace("{balance}", formatted)
                        .replace("{amount}", String.format(java.util.Locale.US, "%.2f", balance))
                        .replace("{currency}", currency)
                        .replace("{player}", player.getName());

                String hoverTemplate = cfg.getString("messages.preview.balance.hover",
                        "&7Balance of &6{player}&7: &a{balance}");
                String hover = hoverTemplate
                        .replace("{balance}", formatted)
                        .replace("{amount}", String.format(java.util.Locale.US, "%.2f", balance))
                        .replace("{currency}", currency)
                        .replace("{player}", player.getName());

                balDisplay = ColorUtils.parseComponent(label)
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                ColorUtils.parseComponent(hover)));
            }

            for (String token : balTokens) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(balDisplay)
                        .build());
            }
        }

        return component;
    }
}
