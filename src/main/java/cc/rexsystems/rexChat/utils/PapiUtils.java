package cc.rexsystems.rexChat.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class PapiUtils {
    private static volatile boolean checked = false;
    private static volatile boolean available = false;
    private static Method papiMethod;

    private static void ensureChecked() {
        if (checked) return;
        checked = true;
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiMethod = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public static boolean isAvailable() {
        ensureChecked();
        return available && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public static String apply(Player player, String text) {
        if (text == null) return null;
        ensureChecked();
        if (!isAvailable()) return text;
        try {
            return (String) papiMethod.invoke(null, player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}


