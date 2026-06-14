package me.rexsystems.rexChat.utils;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.api.CustomChatToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

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
        String plainLower = plain.toLowerCase(Locale.ROOT);

        List<String> itemTokens = PreviewTokenLists.itemTokens(cfg);
        List<String> invTokens = PreviewTokenLists.inventoryTokens(cfg);
        List<String> ecTokens = PreviewTokenLists.enderChestTokens(cfg);
        List<String> balTokens = PreviewTokenLists.balanceTokens(cfg);
        List<String> coordsTokens = PreviewTokenLists.coordsTokens(cfg);

        boolean hasItem = PreviewTokenLists.containsAnyToken(plainLower, itemTokens);
        boolean hasInv = PreviewTokenLists.containsAnyToken(plainLower, invTokens);
        boolean hasEc = PreviewTokenLists.containsAnyToken(plainLower, ecTokens);
        boolean hasBal = PreviewTokenLists.containsAnyToken(plainLower, balTokens);
        boolean hasCoords = PreviewTokenLists.containsAnyToken(plainLower, coordsTokens);

        boolean hasCustom = false;
        for (CustomChatToken token : plugin.getCustomTokenRegistry().getAll()) {
            for (String alias : token.getAliases()) {
                if (plainLower.contains(alias)) {
                    hasCustom = true;
                    break;
                }
            }
            if (hasCustom) {
                break;
            }
        }

        if (!hasItem && !hasInv && !hasEc && !hasBal && !hasCoords && !hasCustom) {
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
                    .hoverEvent(HoverEvent.showText(
                            ColorUtils.parseComponent(cfg.getString("messages.preview.inventory.hover",
                                    "&7Click to view {player}'s inventory")
                                    .replace("{player}", player.getName()))))
                    .clickEvent(ClickEvent.runCommand("/rexchat viewinv " + invId));

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
                    .hoverEvent(HoverEvent.showText(
                            ColorUtils.parseComponent(cfg.getString("messages.preview.enderchest.hover",
                                    "&7Click to view {player}'s ender chest")
                                    .replace("{player}", player.getName()))))
                    .clickEvent(ClickEvent.runCommand("/rexchat viewec " + ecId));

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
                String unavailableLabel = cfg.getString("messages.preview.balance.unavailable-label",
                        "&7[&cBalance unavailable&7]");
                balDisplay = ColorUtils.parseComponent(unavailableLabel);
            } else {
                Double balance = VaultEconomyUtils.getBalance(player);
                if (balance == null)
                    balance = 0.0;

                String formatted = VaultEconomyUtils.format(balance);
                String currency = VaultEconomyUtils.currencyNamePlural();
                if (currency == null)
                    currency = "";

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
                        .hoverEvent(HoverEvent.showText(ColorUtils.parseComponent(hover)));
            }

            for (String token : balTokens) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(balDisplay)
                        .build());
            }
        }

        if (hasCoords && player.hasPermission("rexchat.preview.coords")) {
            String coordsId = plugin.getCoordsSnapshotManager().store(player);
            var loc = player.getLocation();
            String world = loc.getWorld().getName();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            String labelTemplate = cfg.getString("messages.preview.coords.label-template",
                    "&7[&b{x}, {y}, {z}&7]");
            String label = labelTemplate
                    .replace("{world}", world)
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{player}", player.getName());

            String hoverTemplate = cfg.getString("messages.preview.coords.hover",
                    "&7Click to copy coordinates\n&7Staff: &f/rexchat tpcoords " + coordsId);
            String hover = hoverTemplate
                    .replace("{world}", world)
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z))
                    .replace("{player}", player.getName())
                    .replace("{id}", coordsId);

            String copyTemplate = cfg.getString("messages.preview.coords.copy-format", "{x} {y} {z}");
            String copyValue = copyTemplate
                    .replace("{world}", world)
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z));

            Component coordsDisplay = ColorUtils.parseComponent(label)
                    .hoverEvent(HoverEvent.showText(ColorUtils.parseComponent(hover)))
                    .clickEvent(ClickEvent.copyToClipboard(copyValue));

            for (String token : coordsTokens) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(token)
                        .replacement(coordsDisplay)
                        .build());
            }
        }

        for (CustomChatToken customToken : plugin.getCustomTokenRegistry().getAll()) {
            String usePermission = customToken.getUsePermission();
            if (usePermission != null && !usePermission.isBlank() && !player.hasPermission(usePermission)) {
                continue;
            }

            for (String alias : customToken.getAliases()) {
                if (!plainLower.contains(alias)) {
                    continue;
                }
                Component replacement = customToken.buildReplacement(player, alias);
                component = component.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(alias)
                        .replacement(replacement)
                        .build());
            }
        }

        return component;
    }
}
