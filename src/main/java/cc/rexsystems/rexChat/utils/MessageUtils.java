package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class MessageUtils {
    private static final boolean IS_LEGACY = isLegacyVersion();
    private static final int[] MC_VERSION = parseMinecraftVersion();
    
    private static boolean isLegacyVersion() {
        try {
            CommandSender.class.getMethod("sendMessage", Class.forName("net.kyori.adventure.text.Component"));
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    public static void sendMessage(CommandSender sender, String message) {
        if (IS_LEGACY) {
            message = ColorUtils.translateLegacyColors(message);
            sender.sendMessage(message);
        } else {
            sender.sendMessage(ColorUtils.parseComponent(message));
        }
    }

    public static boolean isLegacy() {
        return IS_LEGACY;
    }

    private static int[] parseMinecraftVersion() {
        try {
            String v = Bukkit.getMinecraftVersion();
            String[] parts = v.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new int[]{major, minor};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    public static boolean isMinecraftVersionAtLeast(int major, int minor) {
        int[] v = MC_VERSION;
        if (v[0] > major) return true;
        if (v[0] < major) return false;
        return v[1] >= minor;
    }
}