package cc.rexsystems.rexChat.listener;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * Handles tab completion for static commands.
 */
public class StaticCommandListener implements Listener {
    private final RexChat plugin;

    public StaticCommandListener(RexChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/"))
            return;

        // Extract command name
        String[] parts = buffer.substring(1).split("\\s+");
        if (parts.length == 0)
            return;

        String commandName = parts[0].toLowerCase();

        // Only handle static commands
        if (!commandName.equals("clearchat") && !commandName.equals("cc") &&
                !commandName.equals("mutechat") && !commandName.equals("mc")) {
            return;
        }

        // Get the command and its tab completer
        org.bukkit.command.PluginCommand cmd = plugin.getCommand(commandName);
        if (cmd == null) {
            // Try aliases
            if (commandName.equals("cc")) {
                cmd = plugin.getCommand("clearchat");
            } else if (commandName.equals("mc")) {
                cmd = plugin.getCommand("mutechat");
            }
        }

        if (cmd == null || cmd.getTabCompleter() == null)
            return;

        // Extract args for tab completion
        String[] args = parts.length > 1 ? java.util.Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        // Call the tab completer
        try {
            java.util.List<String> completions = cmd.getTabCompleter().onTabComplete(
                    event.getSender(),
                    cmd,
                    commandName,
                    args);

            if (completions != null && !completions.isEmpty()) {
                // Filter completions based on the last argument if it exists
                if (args.length > 0 && !buffer.endsWith(" ")) {
                    String lastArg = args[args.length - 1].toLowerCase();
                    completions = completions.stream()
                            .filter(c -> c.toLowerCase().startsWith(lastArg))
                            .collect(java.util.stream.Collectors.toList());
                }
                event.setCompletions(completions);
            }
        } catch (Exception e) {
            plugin.getLogUtils().warning(
                    "[StaticCommandListener] Error in tab completion for " + commandName + ": " + e.getMessage());
        }
    }
}
