package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Utility class for scheduling tasks on the Bukkit scheduler
 */
public class SchedulerUtils {

    /**
     * Run a task asynchronously
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(plugin, task);
    }

    /**
     * Run a task for a specific player on the main thread
     * This ensures thread-safety when interacting with player objects
     */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null || task == null) {
            return;
        }
        
        BukkitScheduler scheduler = Bukkit.getScheduler();
        // Run on main thread to ensure thread-safety
        scheduler.runTask(plugin, task);
    }

    /**
     * Run a task on the main thread
     */
    public static void runSync(Plugin plugin, Runnable task) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTask(plugin, task);
    }

    /**
     * Run a task later on the main thread
     */
    public static void runLater(Plugin plugin, Runnable task, long delayTicks) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskLater(plugin, task, delayTicks);
    }
}
