package cc.rexsystems.rexChat.commands.admin;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.commands.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RexChatCommand extends BaseCommand {
    private final List<String> subCommands = List.of("reload", "inv", "item", "help");

    public RexChatCommand(RexChat plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String[] args) {
        if (args.length < 1) {
            // Show version info and help message
            String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
            String version = plugin.getDescription().getVersion();
            String name = plugin.getDescription().getName();
            String authors = String.join(", ", plugin.getDescription().getAuthors());

            sendMessage(sender, prefix + "&6" + name + " &7v" + version);
            sendMessage(sender, prefix + "&7Made by: &f" + authors);
            sendMessage(sender, prefix + "&7Website: &f" + plugin.getDescription().getWebsite());
            sendMessage(sender, "");
            sendMessage(sender, prefix + "&7Use &e/" + label + " help &7for help and available commands.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasPermission(sender, "admin"))
                return true;

            plugin.getConfigManager().loadConfigs();

            // Reload chat color presets
            if (plugin.getChatColorManager() != null) {
                plugin.getChatColorManager().loadPresets();
            }

            // NOTE: Config color conversion DISABLED
            plugin.getCommandManager().loadCommands();

            String reloadMsg = plugin.getConfigManager().getConfig()
                    .getString("messages.reload-success", "&aConfiguration reloaded successfully!");
            sendMessage(sender, reloadMsg);
            return true;
        }

        // /rexchat viewinv <id> - view inventory by unique ID (replaces old /rexchat
        // inv)
        if (args[0].equalsIgnoreCase("viewinv")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sendMessage(sender, "%rc_prefix%&eThis command can only be used in-game.");
                return true;
            }
            if (args.length < 2) {
                sendMessage(sender, "&cUsage: /rexchat viewinv <id>");
                return true;
            }

            org.bukkit.entity.Player viewer = (org.bukkit.entity.Player) sender;
            String invId = args[1];

            // Get inventory from snapshot manager
            cc.rexsystems.rexChat.service.InventorySnapshotService.InventorySnapshot snapshot = plugin
                    .getInventorySnapshotService().getSnapshotById(invId);
            if (snapshot == null) {
                String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                sendMessage(sender, prefix + "&cInventory preview has expired or does not exist.");
                return true;
            }

            // Open GUI with stored inventory
            String playerName = plugin.getInventorySnapshotService().getPlayerNameById(invId);
            if (playerName == null)
                playerName = "Unknown";

            // Parse title with colors (supports both MiniMessage and legacy)
            String titleRaw = plugin.getConfigManager().getConfig().getString(
                    "messages.preview.inventory.title", "&6Inventory: &f{player}");
            titleRaw = titleRaw.replace("{player}", playerName);
            net.kyori.adventure.text.Component titleComp = cc.rexsystems.rexChat.utils.ColorUtils
                    .parseComponent(titleRaw);
            String title = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(titleComp);

            org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(
                    new cc.rexsystems.rexChat.listener.PreviewGuiListener.PreviewGuiHolder(), 54, title);

            // Copy storage
            org.bukkit.inventory.ItemStack[] storage = snapshot.getStorage();
            for (int i = 0; i < Math.min(storage.length, 36); i++) {
                if (storage[i] != null)
                    gui.setItem(i, storage[i].clone());
            }

            // Fill separator
            org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta fm = filler.getItemMeta();
            if (fm != null) {
                fm.setDisplayName(" ");
                filler.setItemMeta(fm);
            }
            for (int i = 36; i < 45; i++)
                gui.setItem(i, filler);

            // Armor
            org.bukkit.inventory.ItemStack[] armor = snapshot.getArmor();
            if (armor[0] != null)
                gui.setItem(45, armor[0].clone());
            if (armor[1] != null)
                gui.setItem(46, armor[1].clone());
            if (armor[2] != null)
                gui.setItem(47, armor[2].clone());
            if (armor[3] != null)
                gui.setItem(48, armor[3].clone());
            if (snapshot.getOffhand() != null)
                gui.setItem(49, snapshot.getOffhand().clone());

            viewer.openInventory(gui);
            return true;
        }

        // OLD /rexchat item REMOVED - using /rexchat viewitem <id> instead

        // NEW: /rexchat viewitem <id> - view item by unique ID
        if (args[0].equalsIgnoreCase("viewitem")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sendMessage(sender, "%rc_prefix%&eThis command can only be used in-game.");
                return true;
            }
            if (args.length < 2) {
                sendMessage(sender, "&cUsage: /rexchat viewitem <id>");
                return true;
            }

            org.bukkit.entity.Player viewer = (org.bukkit.entity.Player) sender;
            String itemId = args[1];

            // Get item from snapshot manager by ID
            org.bukkit.inventory.ItemStack item = plugin.getItemSnapshotManager().getItem(itemId);
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");
                sendMessage(sender, prefix + "&cItem preview has expired or does not exist.");
                return true;
            }

            // Get player name for title
            String playerName = plugin.getItemSnapshotManager().getPlayerName(itemId);
            if (playerName == null)
                playerName = "Unknown";

            // Parse title with colors (supports both MiniMessage and legacy)
            String titleRaw = plugin.getConfigManager().getConfig().getString(
                    "messages.preview.item.title", "&6Item: &f{player}");
            titleRaw = titleRaw.replace("{player}", playerName);
            // Convert to Component then to legacy string for inventory title
            net.kyori.adventure.text.Component titleComp = cc.rexsystems.rexChat.utils.ColorUtils
                    .parseComponent(titleRaw);
            String title = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(titleComp);

            // Create DROPPER inventory
            org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(
                    new cc.rexsystems.rexChat.listener.PreviewGuiListener.PreviewGuiHolder(),
                    org.bukkit.event.inventory.InventoryType.DROPPER,
                    title);

            // Put item in center (slot 4)
            gui.setItem(4, item);

            // Add filler around the item
            org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta fm = filler.getItemMeta();
            if (fm != null) {
                fm.setDisplayName(" ");
                filler.setItemMeta(fm);
            }
            for (int i = 0; i < 9; i++) {
                if (i != 4) {
                    gui.setItem(i, filler);
                }
            }

            viewer.openInventory(gui);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            showHelp(sender, label);
            return true;
        }

        String unknownCmd = plugin.getConfigManager().getConfig()
                .getString("messages.command-not-found", "&cCommand not found.");
        sendMessage(sender, unknownCmd);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && ("inv".equalsIgnoreCase(args[0]) || "item".equalsIgnoreCase(args[0]))) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private void showHelp(CommandSender sender, String label) {
        String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");

        sendMessage(sender, prefix + "&6=== &fRexChat Help &6===");
        sendMessage(sender, "");

        // Admin commands
        if (hasPermissionForHelp(sender, "rexchat.admin")) {
            sendMessage(sender, prefix + "&6Admin Commands:");
            sendMessage(sender, prefix + "  &e/" + label + " reload &7- Reload plugin configuration");
            sendMessage(sender, prefix + "  &e/" + label + " inv [player] &7- Preview player inventory");
            sendMessage(sender, prefix + "  &e/" + label + " item [player] &7- Preview player's held item");
            sendMessage(sender, "");
        }

        // Preview commands
        if (hasPermissionForHelp(sender, "rexchat.preview.inv")
                || hasPermissionForHelp(sender, "rexchat.preview.item")) {
            sendMessage(sender, prefix + "&6Preview Commands:");
            if (hasPermissionForHelp(sender, "rexchat.preview.inv")) {
                sendMessage(sender, prefix + "  &e/" + label + " inv [player] &7- Preview player inventory");
            }
            if (hasPermissionForHelp(sender, "rexchat.preview.item")) {
                sendMessage(sender, prefix + "  &e/" + label + " item [player] &7- Preview player's held item");
            }
            sendMessage(sender, "");
        }

        // General commands
        sendMessage(sender, prefix + "&6General Commands:");
        if (hasPermissionForHelp(sender, "rexchat.clear")) {
            sendMessage(sender, prefix + "  &e/clearchat &7- Clear the chat");
        }
        if (hasPermissionForHelp(sender, "rexchat.mute")) {
            sendMessage(sender, prefix + "  &e/mutechat &7- Toggle chat mute");
        }

        sendMessage(sender, "");
        sendMessage(sender, prefix + "&7Use &e/" + label + " help &7to see this help again.");
    }

    private boolean hasPermissionForHelp(CommandSender sender, String permission) {
        // Same logic as BaseCommand.hasPermission but without sending error message
        return sender.isOp()
                || sender.hasPermission(permission)
                || sender.hasPermission("rexchat.*")
                || sender.hasPermission("*");
    }

    private org.bukkit.entity.Player findPlayer(String name) {
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayerExact(name);
        if (p != null)
            return p;
        String lc = name.toLowerCase();
        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.getName().toLowerCase().equals(lc))
                return op;
        }
        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.getName().toLowerCase().startsWith(lc))
                return op;
        }
        return null;
    }
}