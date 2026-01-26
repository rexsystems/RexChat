package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class LogUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final RexChat plugin;
    private final File logFile;

    public LogUtils(RexChat plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs/rexchat.log");
        initLogFile();
    }

    private void initLogFile() {
        try {
            File logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            
            if (logFile.length() > 5 * 1024 * 1024) {
                File oldLog = new File(plugin.getDataFolder(), "logs/rexchat.old.log");
                if (oldLog.exists()) {
                    oldLog.delete();
                }
                logFile.renameTo(oldLog);
                logFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to initialize log file: " + e.getMessage());
        }
    }

    public void log(Level level, String message) {
        plugin.getLogger().log(level, message);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String timestamp = DATE_FORMAT.format(new Date());
            writer.println(String.format("[%s] [%s] %s", timestamp, level.getName(), message));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
        }
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warning(String message) {
        log(Level.WARNING, message);
    }

    public void severe(String message) {
        log(Level.SEVERE, message);
    }

    public void debug(String message) {
        try {
            if (plugin.getConfigManager() != null && 
                plugin.getConfigManager().getConfig() != null && 
                plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                log(Level.INFO, "[DEBUG] " + message);
            }
        } catch (Exception e) {
        }
    }
} 