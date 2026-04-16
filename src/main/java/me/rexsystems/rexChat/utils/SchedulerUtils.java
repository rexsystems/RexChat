package me.rexsystems.rexChat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for scheduling tasks compatible with Folia
 */
public class SchedulerUtils {

    /**
     * Run a task asynchronously
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /**
     * Run a task for a specific player using the entity scheduler
     */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null || task == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    /**
     * Run a task on the global region thread
     */
    public static void runSync(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    /**
     * Run a task later on the global region thread
     */
    public static void runLater(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }
}
