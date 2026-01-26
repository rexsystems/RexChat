package cc.rexsystems.rexChat.commands;

import cc.rexsystems.rexChat.RexChat;
import cc.rexsystems.rexChat.commands.admin.RexChatCommand;
import cc.rexsystems.rexChat.commands.chat.ClearChatCommand;
import cc.rexsystems.rexChat.commands.chat.MuteChatCommand;
import cc.rexsystems.rexChat.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

public class ChatCommandManager {
    private final RexChat plugin;
    private final Map<String, CommandConfig> commands;
    private CommandMap commandMap;
    private final Set<String> registeredCommands;

    public ChatCommandManager(RexChat plugin) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
        this.registeredCommands = new HashSet<>();
        setupCommandMap();
    }

    private void setupCommandMap() {
        try {
            Field commandMapField = SimplePluginManager.class.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getPluginManager());
        } catch (Exception e) {
            plugin.getLogUtils().severe("Failed to access command map: " + e.getMessage());
        }
    }

    /**
     * Ensure the global knownCommands map points to our PluginCommand and has the
     * right executor/tab completer.
     * This avoids issues where another plugin or late registration overwrote the
     * executor mapping.
     */
    private void verifyAndFixExecutor(String name, CommandExecutorFactory factory) {
        try {
            PluginCommand cmd = plugin.getCommand(name);
            if (cmd == null) {
                plugin.getLogUtils().warning("verifyAndFixExecutor: plugin.getCommand returned null for " + name);
                return;
            }

            // Prepare expected executor from our command class if missing
            if (cmd.getExecutor() == null) {
                org.bukkit.command.CommandExecutor exec = factory.create();
                cmd.setExecutor(exec);
                if (exec instanceof org.bukkit.command.TabCompleter) {
                    cmd.setTabCompleter((org.bukkit.command.TabCompleter) exec);
                }
            }

            // Check commandMap knownCommands entries for name and aliases
            if (commandMap instanceof SimpleCommandMap) {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

                // Build list: primary name + aliases
                java.util.List<String> entriesToCheck = new java.util.ArrayList<>();
                entriesToCheck.add(name.toLowerCase());
                // Also add with plugin prefix
                entriesToCheck.add(plugin.getName().toLowerCase() + ":" + name.toLowerCase());

                // aliases from plugin.yml
                org.bukkit.plugin.PluginDescriptionFile desc = plugin.getDescription();
                Object cmdData = desc.getCommands().get(name);
                if (cmdData instanceof java.util.Map) {
                    Object aliasesObj = ((java.util.Map<?, ?>) cmdData).get("aliases");
                    if (aliasesObj instanceof java.util.List) {
                        for (Object a : (java.util.List<?>) aliasesObj) {
                            if (a != null) {
                                entriesToCheck.add(a.toString().toLowerCase());
                                entriesToCheck.add(plugin.getName().toLowerCase() + ":" + a.toString().toLowerCase());
                            }
                        }
                    }
                }

                boolean foundInMap = false;
                for (String key : entriesToCheck) {
                    Command mapped = knownCommands.get(key);
                    if (mapped != null) {
                        foundInMap = true;

                        if (mapped instanceof PluginCommand) {
                            PluginCommand pc = (PluginCommand) mapped;
                            if (pc.getPlugin() == plugin) {
                                // CRITICAL FIX: Always ensure the executor matches, not just when null
                                // This fixes the case where the command object exists but points to wrong
                                // executor
                                if (pc.getExecutor() == null || pc.getExecutor() != cmd.getExecutor()) {
                                    pc.setExecutor(cmd.getExecutor());
                                    pc.setTabCompleter(cmd.getTabCompleter());
                                }
                            } else {
                                // Another plugin owns this name/alias
                                plugin.getLogUtils().warning(
                                        "Command alias conflict: '" + key + "' owned by " + pc.getPlugin().getName());
                            }
                        } else if (mapped != cmd) {
                            // The mapped command is not our PluginCommand - this is the bug!
                            plugin.getLogUtils()
                                    .warning("Command '" + key + "' in knownCommands is not our PluginCommand! Type: "
                                            + mapped.getClass().getName());
                            // Replace it with our command
                            knownCommands.put(key, cmd);
                        }
                    }
                }

                if (!foundInMap) {
                    plugin.getLogUtils()
                            .warning("Command '" + name + "' not found in knownCommands! Registering it now...");
                    // Register the command in knownCommands
                    knownCommands.put(name.toLowerCase(), cmd);
                    knownCommands.put(plugin.getName().toLowerCase() + ":" + name.toLowerCase(), cmd);
                }
            }
        } catch (Exception e) {
            plugin.getLogUtils().warning("verifyAndFixExecutor failed for " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface CommandExecutorFactory {
        org.bukkit.command.CommandExecutor create();
    }

    private PluginCommand createPluginCommand(String name, List<String> aliases) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class,
                    org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, plugin);
            command.setAliases(aliases);
            return command;
        } catch (Exception e) {
            plugin.getLogUtils().severe("Failed to create command " + name + ": " + e.getMessage());
            return null;
        }
    }

    private void unregisterDynamicCommands() {
        if (commandMap == null || registeredCommands.isEmpty())
            return;

        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            Iterator<String> it = registeredCommands.iterator();
            while (it.hasNext()) {
                String cmdName = it.next();

                // Remove from knownCommands
                knownCommands.remove(cmdName.toLowerCase());
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + cmdName.toLowerCase());

                // Handle aliases too if possible
                PluginCommand pc = plugin.getCommand(cmdName);
                if (pc != null && pc.getAliases() != null) {
                    for (String alias : pc.getAliases()) {
                        knownCommands.remove(alias.toLowerCase());
                        knownCommands.remove(plugin.getName().toLowerCase() + ":" + alias.toLowerCase());
                    }
                    // Unregister from the plugin itself to allow re-creation if needed
                    try {
                        // This is tricky for PluginCommand, but essential for full cleanup
                        pc.unregister(commandMap);
                    } catch (Exception ignored) {
                    }
                }

                it.remove();
            }
        } catch (Exception e) {
            plugin.getLogUtils().warning("Failed to unregister dynamic commands: " + e.getMessage());
        }
    }

    private void ensureStaticCommandsRegistered() {
        // List of static commands that should be registered from plugin.yml
        // Only core plugin commands that have their own executors
        Set<String> staticCommands = Set.of("rexchat", "clearchat", "mutechat");

        org.bukkit.plugin.PluginDescriptionFile desc = plugin.getDescription();

        for (String cmdName : staticCommands) {
            // Check if command exists in plugin.yml
            if (!desc.getCommands().containsKey(cmdName)) {
                continue;
            }

            // Check if command is already registered
            PluginCommand existing = plugin.getCommand(cmdName);
            if (existing != null) {
                continue;
            }

            // Try to find in commandMap
            try {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

                boolean found = false;
                for (Command cmd : knownCommands.values()) {
                    if (cmd instanceof PluginCommand) {
                        PluginCommand pc = (PluginCommand) cmd;
                        if (pc.getPlugin() == plugin && pc.getName().equalsIgnoreCase(cmdName)) {
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    continue;
                }
            } catch (Exception e) {
            }

            // Command not found, create and register it
            PluginCommand newCmd = getOrCreateCommand(cmdName);
            if (newCmd == null) {
                plugin.getLogUtils().warning("Failed to register static command: " + cmdName);
            }
        }
    }

    private void verifyStaticCommand(String name, String expectedExecutorClass) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            if (cmd.getExecutor() != null) {
                String actualClass = cmd.getExecutor().getClass().getName();
                if (actualClass.contains(expectedExecutorClass)) {

                    // CRITICAL: Verify command is actually in commandMap and has correct executor
                    try {
                        Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                        knownCommandsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

                        // Check main command name
                        Command cmdInMap = knownCommands.get(name.toLowerCase());
                        if (cmdInMap instanceof PluginCommand) {
                            PluginCommand pc = (PluginCommand) cmdInMap;
                            if (pc.getExecutor() != null
                                    && pc.getExecutor().getClass().getName().contains(expectedExecutorClass)) {
                            } else {
                                plugin.getLogUtils().severe("✗ " + name + " command in commandMap has WRONG executor: "
                                        + (pc.getExecutor() != null ? pc.getExecutor().getClass().getName() : "NULL"));
                                // FIX: Re-set the executor in commandMap
                                pc.setExecutor(cmd.getExecutor());
                                pc.setTabCompleter(cmd.getTabCompleter());
                            }

                            // CRITICAL: Also check aliases
                            if (cmd.getAliases() != null && !cmd.getAliases().isEmpty()) {
                                for (String alias : cmd.getAliases()) {
                                    Command aliasCmd = knownCommands.get(alias.toLowerCase());
                                    if (aliasCmd instanceof PluginCommand) {
                                        PluginCommand aliasPc = (PluginCommand) aliasCmd;
                                        if (aliasPc.getExecutor() == null || !aliasPc.getExecutor().getClass().getName()
                                                .contains(expectedExecutorClass)) {
                                            plugin.getLogUtils()
                                                    .severe("✗ " + name + " alias '" + alias
                                                            + "' in commandMap has WRONG executor: "
                                                            + (aliasPc.getExecutor() != null
                                                                    ? aliasPc.getExecutor().getClass().getName()
                                                                    : "NULL"));
                                            aliasPc.setExecutor(cmd.getExecutor());
                                            aliasPc.setTabCompleter(cmd.getTabCompleter());
                                        }
                                    }
                                }
                            }
                        } else {
                            plugin.getLogUtils().severe("✗ " + name + " command NOT FOUND in commandMap!");
                        }
                    } catch (Exception e) {
                        plugin.getLogUtils().severe("Failed to verify " + name + " in commandMap: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    plugin.getLogUtils().severe("✗ " + name + " command WRONG executor: expected "
                            + expectedExecutorClass + ", got " + actualClass);
                    // FIX: Re-set the executor if it was overwritten
                    if (name.equals("mutechat")) {
                        cmd.setExecutor(new MuteChatCommand(plugin));
                    } else if (name.equals("clearchat")) {
                        cmd.setExecutor(new ClearChatCommand(plugin));
                    } else if (name.equals("rexchat")) {
                        cmd.setExecutor(new RexChatCommand(plugin));
                    }
                }
            } else {
                plugin.getLogUtils().severe("✗ " + name + " command has NULL executor!");
                // FIX: Re-set the executor if it's null
                if (name.equals("mutechat")) {
                    cmd.setExecutor(new MuteChatCommand(plugin));
                } else if (name.equals("clearchat")) {
                    cmd.setExecutor(new ClearChatCommand(plugin));
                } else if (name.equals("rexchat")) {
                    cmd.setExecutor(new RexChatCommand(plugin));
                }
            }
        } else {
            plugin.getLogUtils().severe("✗ " + name + " command not found!");
        }
    }

    private PluginCommand getOrCreateCommand(String name) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            return cmd;
        }

        // Try to get from commandMap if plugin.getCommand() returns null
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Try exact name
            Command found = knownCommands.get(name.toLowerCase());
            if (found instanceof PluginCommand && ((PluginCommand) found).getPlugin() == plugin) {
                return (PluginCommand) found;
            }

            // Try aliases
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                if (entry.getValue() instanceof PluginCommand) {
                    PluginCommand pc = (PluginCommand) entry.getValue();
                    if (pc.getPlugin() == plugin && pc.getName().equalsIgnoreCase(name)) {
                        return pc;
                    }
                }
            }

            // If command doesn't exist, create it from plugin.yml
            List<String> aliases = Collections.emptyList();
            // Get aliases from plugin.yml description
            org.bukkit.plugin.PluginDescriptionFile desc = plugin.getDescription();
            if (desc.getCommands().containsKey(name)) {
                Object cmdData = desc.getCommands().get(name);
                if (cmdData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cmdMap = (Map<String, Object>) cmdData;
                    Object aliasesObj = cmdMap.get("aliases");
                    if (aliasesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> aliasesList = (List<String>) aliasesObj;
                        aliases = aliasesList;
                    }
                }
            } else {
                plugin.getLogUtils().warning("Command " + name + " not found in plugin.yml!");
            }
            PluginCommand newCmd = createPluginCommand(name, aliases);
            if (newCmd != null) {
                commandMap.register(plugin.getName().toLowerCase(), newCmd);
                return newCmd;
            } else {
                plugin.getLogUtils().warning("Failed to create command: " + name);
            }
        } catch (Exception e) {
            plugin.getLogUtils().severe("Failed to get/create command from commandMap: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public void loadCommands() {
        unregisterDynamicCommands();
        commands.clear();

        // Rely on Bukkit/Paper to register commands from plugin.yml.
        // Only set executors/tab completers via plugin.getCommand(...).

        ConfigurationSection chatSection = plugin.getConfigManager().getConfig()
                .getConfigurationSection("chat-management");
        if (chatSection != null) {
            // Use plugin.getCommand() directly - Bukkit should have already registered it
            // from plugin.yml
            PluginCommand mutechatCmd = plugin.getCommand("mutechat");
            if (mutechatCmd != null) {
                if (chatSection.getBoolean("mute.enabled", true)) {
                    MuteChatCommand executor = new MuteChatCommand(plugin);

                    mutechatCmd.setExecutor(executor);
                    mutechatCmd.setTabCompleter(executor);

                    // CRITICAL: Also register in commandMap directly
                    if (commandMap != null) {
                        commandMap.register(plugin.getName().toLowerCase(), mutechatCmd);
                    }

                    verifyAndFixExecutor("mutechat", () -> new MuteChatCommand(plugin));
                } else {
                    if (plugin.getChatManager().isChatMuted()) {
                        plugin.getLogUtils().info("Unmuting chat because mutechat command is being disabled");
                        plugin.getChatManager().toggleMute("SYSTEM");
                    }
                    mutechatCmd.setExecutor(null);
                    mutechatCmd.setTabCompleter(null);
                    mutechatCmd.setPermissionMessage(null);
                    mutechatCmd.setUsage(null);
                    mutechatCmd.setPermission("rexchat.disabled.mutechat");
                }
            } else {
                plugin.getLogUtils().warning("Failed to get mutechat command from plugin.yml!");
            }

            PluginCommand clearchatCmd = plugin.getCommand("clearchat");
            if (clearchatCmd != null) {
                if (chatSection.getBoolean("clear.enabled", true)) {
                    ClearChatCommand executor = new ClearChatCommand(plugin);

                    clearchatCmd.setExecutor(executor);
                    clearchatCmd.setTabCompleter(executor);

                    // CRITICAL: Also register in commandMap directly
                    if (commandMap != null) {
                        commandMap.register(plugin.getName().toLowerCase(), clearchatCmd);
                    }

                    verifyAndFixExecutor("clearchat", () -> new ClearChatCommand(plugin));
                } else {
                    clearchatCmd.setExecutor(null);
                    clearchatCmd.setTabCompleter(null);
                    clearchatCmd.setPermissionMessage(null);
                    clearchatCmd.setUsage(null);
                    clearchatCmd.setPermission("rexchat.disabled.clearchat");
                }
            } else {
                plugin.getLogUtils().warning("Failed to get clearchat command from plugin.yml!");
            }
        }

        PluginCommand rexchatCmd = plugin.getCommand("rexchat");
        if (rexchatCmd != null) {
            RexChatCommand executor = new RexChatCommand(plugin);
            rexchatCmd.setExecutor(executor);
            rexchatCmd.setTabCompleter(executor);
            verifyAndFixExecutor("rexchat", () -> new RexChatCommand(plugin));
        } else {
            plugin.getLogUtils().warning("Failed to get rexchat command from plugin.yml!");
        }

        // Register /chatcolor command
        PluginCommand chatcolorCmd = plugin.getCommand("chatcolor");
        if (chatcolorCmd != null) {
            cc.rexsystems.rexChat.commands.chat.ChatColorCommand executor = new cc.rexsystems.rexChat.commands.chat.ChatColorCommand(
                    plugin);
            chatcolorCmd.setExecutor(executor);
            chatcolorCmd.setTabCompleter(executor);
        }

        ConfigurationSection commandsSection = plugin.getConfigManager().getConfig()
                .getConfigurationSection("commands");
        if (commandsSection != null) {
            for (String key : commandsSection.getKeys(false)) {
                ConfigurationSection cmdSection = commandsSection.getConfigurationSection(key);
                if (cmdSection != null && cmdSection.getBoolean("enabled", true)) {
                    CommandConfig cmdConfig = new CommandConfig(key, cmdSection);
                    commands.put(key, cmdConfig);
                } else {
                }
            }
        }

        // List of static commands that should NOT be overwritten by dynamic commands
        // Only core plugin commands that have their own executors
        Set<String> staticCommands = Set.of("rexchat", "clearchat", "mutechat");

        // Now register dynamic commands from config - BUT ONLY IF THEY'RE NOT STATIC
        // COMMANDS

        for (CommandConfig cmdConfig : commands.values()) {
            String commandName = cmdConfig.getCommand().toLowerCase();

            // Don't overwrite static commands with dynamic ones
            if (staticCommands.contains(commandName)) {
                continue;
            }

            PluginCommand existingCommand = plugin.getCommand(commandName);

            if (existingCommand != null) {
                // Double-check: don't overwrite static commands
                if (staticCommands.contains(commandName)) {
                    continue;
                }

                // Check if executor is already set to a static command executor
                if (existingCommand.getExecutor() != null) {
                    String executorClass = existingCommand.getExecutor().getClass().getName();
                    if (executorClass.contains("MuteChatCommand") ||
                            executorClass.contains("ClearChatCommand") ||
                            executorClass.contains("RexChatCommand")) {
                        continue;
                    }
                }

                existingCommand.setExecutor(new DynamicCommand(plugin, cmdConfig));
                registeredCommands.add(commandName);
            } else {
                // If the command is not in plugin.yml and not a protected static name, we can
                // create it dynamically
                if (!staticCommands.contains(commandName)) {
                    PluginCommand newCommand = createPluginCommand(commandName, cmdConfig.getAliases());
                    if (newCommand != null) {
                        newCommand.setExecutor(new DynamicCommand(plugin, cmdConfig));
                        commandMap.register(plugin.getName().toLowerCase(), newCommand);
                        registeredCommands.add(commandName);
                    }
                }
            }
        }

        // CRITICAL FIX: Schedule a task to re-verify commands after server fully loads
        // This ensures our executors aren't overwritten by late-loading plugins
        SchedulerUtils.runLater(plugin, () -> {
            // Re-verify and fix mutechat
            PluginCommand mutechatCmd = plugin.getCommand("mutechat");
            if (mutechatCmd != null && chatSection != null && chatSection.getBoolean("mute.enabled", true)) {
                if (mutechatCmd.getExecutor() == null || !(mutechatCmd.getExecutor() instanceof MuteChatCommand)) {
                    plugin.getLogUtils().warning("mutechat executor was overwritten! Fixing...");
                    MuteChatCommand executor = new MuteChatCommand(plugin);
                    mutechatCmd.setExecutor(executor);
                    mutechatCmd.setTabCompleter(executor);
                    verifyAndFixExecutor("mutechat", () -> new MuteChatCommand(plugin));
                }
            }

            // Re-verify and fix clearchat
            PluginCommand clearchatCmd = plugin.getCommand("clearchat");
            if (clearchatCmd != null && chatSection != null && chatSection.getBoolean("clear.enabled", true)) {
                if (clearchatCmd.getExecutor() == null || !(clearchatCmd.getExecutor() instanceof ClearChatCommand)) {
                    plugin.getLogUtils().warning("clearchat executor was overwritten! Fixing...");
                    ClearChatCommand executor = new ClearChatCommand(plugin);
                    clearchatCmd.setExecutor(executor);
                    clearchatCmd.setTabCompleter(executor);
                    verifyAndFixExecutor("clearchat", () -> new ClearChatCommand(plugin));
                }
            }
        }, 20L); // Wait 1 second after server starts
    }

    public Map<String, CommandConfig> getCommands() {
        return commands;
    }
}
