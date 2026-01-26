package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
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

        if (itemTokens.isEmpty()) {
            itemTokens = java.util.Arrays.asList("[item]", "[i]", "{item}", "{i}");
        }
        if (invTokens.isEmpty()) {
            invTokens = java.util.Arrays.asList("[inventory]", "[inv]", "{inventory}", "{inv}");
        }

        boolean hasItem = false;
        boolean hasInv = false;

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

        if (!hasItem && !hasInv) {
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

        return component;
    }
}
