package me.rexsystems.rexChat;

import me.rexsystems.rexChat.chat.ChatManager;
import me.rexsystems.rexChat.commands.ChatCommandManager;
import me.rexsystems.rexChat.config.ConfigManager;
import me.rexsystems.rexChat.data.DataManager;
import me.rexsystems.rexChat.utils.LogUtils;

import me.rexsystems.rexChat.service.PreviewGuiService;
import me.rexsystems.rexChat.listener.PreviewGuiListener;
import me.rexsystems.rexChat.utils.UpdateChecker;
import me.rexsystems.rexChat.papi.RexChatPlaceholders;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class RexChat extends JavaPlugin {
    private static RexChat instance;
    private ConfigManager configManager;
    private ChatCommandManager commandManager;
    private ChatManager chatManager;
    private DataManager dataManager;
    private LogUtils logUtils;
    private static final int BSTATS_PLUGIN_ID = 24762;
    private UpdateChecker updateChecker;

    private PreviewGuiService previewGuiService;
    private me.rexsystems.rexChat.service.InventorySnapshotService inventorySnapshotService;
    private me.rexsystems.rexChat.service.PreviewAccessManager previewAccessManager;
    private me.rexsystems.rexChat.service.ItemSnapshotManager itemSnapshotManager;
    private me.rexsystems.rexChat.service.ChatColorManager chatColorManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            saveDefaultConfig();

            this.logUtils = new LogUtils(this);
            this.configManager = new ConfigManager(this);
            this.dataManager = new DataManager(this);
            this.chatManager = new ChatManager(this);
            this.commandManager = new ChatCommandManager(this);

            this.previewGuiService = new PreviewGuiService(this);
            this.inventorySnapshotService = new me.rexsystems.rexChat.service.InventorySnapshotService(this);
            // Initialize preview access manager with 30-second token expiry
            this.previewAccessManager = new me.rexsystems.rexChat.service.PreviewAccessManager(30);
            // Initialize item snapshot manager for unique item IDs
            this.itemSnapshotManager = new me.rexsystems.rexChat.service.ItemSnapshotManager();
            this.updateChecker = new UpdateChecker(this);

            logUtils.info("Initializing RexChat plugin...");

            new Metrics(this, BSTATS_PLUGIN_ID);
            logUtils.info("bStats metrics initialized");

            boolean configValid = configManager.loadConfigs();
            if (!configValid) {
                logUtils.warning("Plugin will continue to run with potential configuration issues.");
            }

            // Initialize ChatColorManager AFTER config is loaded
            this.chatColorManager = new me.rexsystems.rexChat.service.ChatColorManager(this);

            // NOTE: Config color conversion DISABLED - users manage their own config format
            // Legacy codes (&6) are supported, no need to convert to MiniMessage

            commandManager.loadCommands();

            // Register read-only preview GUI listener
            getServer().getPluginManager().registerEvents(new PreviewGuiListener(), this);
            // Register preview access listener to grant tokens when commands are clicked
            getServer().getPluginManager()
                    .registerEvents(new me.rexsystems.rexChat.listener.PreviewAccessListener(this), this);
            // Register static command listener as fallback
            getServer().getPluginManager()
                    .registerEvents(new me.rexsystems.rexChat.listener.StaticCommandListener(this), this);
            
            // Register PlaceholderAPI expansion if available
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new RexChatPlaceholders(this).register();
                logUtils.info("PlaceholderAPI expansion registered!");
            }
            
            updateChecker.checkForUpdatesAsync();

            // Schedule periodic cleanup of expired snapshots/tokens (every 5 minutes)
            getServer().getAsyncScheduler().runAtFixedRate(this, scheduledTask -> {
                if (previewAccessManager != null) previewAccessManager.cleanupExpiredTokens();
                if (itemSnapshotManager != null) itemSnapshotManager.cleanupExpired();
                if (inventorySnapshotService != null) inventorySnapshotService.cleanupExpired();
            }, 300, 300, TimeUnit.SECONDS);

            logUtils.info("RexChat has been enabled successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable RexChat: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (logUtils != null) {
            logUtils.info("RexChat has been disabled!");
        } else {
            getLogger().info("RexChat has been disabled!");
        }
    }

    public static RexChat getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChatCommandManager getCommandManager() {
        return commandManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public LogUtils getLogUtils() {
        return logUtils;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public PreviewGuiService getPreviewGuiService() {
        return previewGuiService;
    }

    public me.rexsystems.rexChat.service.InventorySnapshotService getInventorySnapshotService() {
        return inventorySnapshotService;
    }

    public me.rexsystems.rexChat.service.PreviewAccessManager getPreviewAccessManager() {
        return previewAccessManager;
    }

    public me.rexsystems.rexChat.service.ItemSnapshotManager getItemSnapshotManager() {
        return itemSnapshotManager;
    }

    public me.rexsystems.rexChat.service.ChatColorManager getChatColorManager() {
        return chatColorManager;
    }
}
