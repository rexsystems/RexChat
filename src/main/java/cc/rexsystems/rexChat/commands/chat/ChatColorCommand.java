package cc.rexsystems.rexChat.commands.chat;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.service.ChatColorManager;
import cc.rexsystems.rexChat.service.ChatColorManager.ChatColorPreset;
import cc.rexsystems.rexChat.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command handler for /chatcolor - allows players to set their chat color.
 */
public class ChatColorCommand implements CommandExecutor, TabCompleter {
    private final RexChat plugin;

    public ChatColorCommand(RexChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        ChatColorManager manager = plugin.getChatColorManager();
        if (manager == null || !manager.isEnabled()) {
            sendMessage(player, "&cChat colors are not enabled.");
            return true;
        }

        String prefix = plugin.getConfigManager().getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            // Show available colors
            showColorList(player, prefix);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set" -> {
                if (args.length < 2) {
                    sendMessage(player, prefix + "&eUsage: /" + label + " set <color>");
                    return true;
                }
                String colorName = args[1].toLowerCase();
                ChatColorPreset preset = manager.getPreset(colorName);

                if (preset == null) {
                    sendMessage(player, prefix + "&cColor '&f" + colorName + "&c' not found.");
                    return true;
                }

                if (!player.hasPermission(preset.permission())) {
                    sendMessage(player, prefix + "&cYou don't have permission for this color.");
                    return true;
                }

                manager.setPlayerColor(player, colorName);
                sendMessage(player, prefix + "&aChat color set to: " + preset.format() + preset.displayName());
            }
            case "off", "reset", "clear" -> {
                manager.setPlayerColor(player, null);
                sendMessage(player, prefix + "&aChat color removed.");
            }
            case "list" -> showColorList(player, prefix);
            default -> {
                // Try to set the color directly
                ChatColorPreset preset = manager.getPreset(subCommand);
                if (preset != null) {
                    if (!player.hasPermission(preset.permission())) {
                        sendMessage(player, prefix + "&cYou don't have permission for this color.");
                        return true;
                    }
                    manager.setPlayerColor(player, subCommand);
                    sendMessage(player, prefix + "&aChat color set to: " + preset.format() + preset.displayName());
                } else {
                    sendMessage(player, prefix + "&cUnknown subcommand. Use /" + label + " for help.");
                }
            }
        }

        return true;
    }

    private void showColorList(Player player, String prefix) {
        ChatColorManager manager = plugin.getChatColorManager();
        Map<String, ChatColorPreset> available = manager.getAvailablePresets(player);
        String currentColor = manager.getPlayerColor(player);

        sendMessage(player, prefix + "&6Available Chat Colors:");

        if (available.isEmpty()) {
            sendMessage(player, "  &7No colors available.");
            return;
        }

        for (ChatColorPreset preset : available.values()) {
            String status = preset.id().equals(currentColor) ? " &a(selected)" : "";
            sendMessage(player, "  " + preset.format() + preset.displayName() + status);
        }

        sendMessage(player, "");
        sendMessage(player, "  &7Use &e/chatcolor set <color> &7to select.");
        sendMessage(player, "  &7Use &e/chatcolor off &7to remove.");
    }

    private void sendMessage(Player player, String message) {
        MessageUtils.sendMessage(player, message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        ChatColorManager manager = plugin.getChatColorManager();
        if (manager == null || !manager.isEnabled()) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("set");
            completions.add("off");
            completions.add("list");
            // Also add available color names for quick selection
            completions.addAll(manager.getAvailablePresets(player).keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(manager.getAvailablePresets(player).keySet());
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}
