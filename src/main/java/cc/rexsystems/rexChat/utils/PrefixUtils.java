package cc.rexsystems.rexChat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Resolves a player's chat prefix without relying on PlaceholderAPI.
 * Attempts LuckPerms v5 API via reflection, then Vault Chat provider via reflection,
 * and finally falls back to a configured group-specific prefix or empty string.
 */
public class PrefixUtils {

    public static String getChatPrefix(Player player, FileConfiguration cfg) {
        // Try LuckPerms v5
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                    .invoke(userManager, player.getUniqueId());
            if (user != null) {
                Object cached = user.getClass().getMethod("getCachedData").invoke(user);
                Object meta = cached.getClass().getMethod("getMetaData").invoke(cached);
                Object prefixObj = meta.getClass().getMethod("getPrefix").invoke(meta);
                if (prefixObj instanceof String) {
                    String prefix = (String) prefixObj;
                    if (prefix != null) return prefix;
                }
            }
        } catch (Throwable ignored) { }

        // Try Vault Chat provider via Bukkit ServicesManager
        try {
            Class<?> chatClass = Class.forName("net.milkbowl.vault.chat.Chat");
            Object registration = Bukkit.getServicesManager().getRegistration(chatClass);
            if (registration != null) {
                Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                Object result = provider.getClass().getMethod("getPlayerPrefix", Player.class).invoke(provider, player);
                if (result instanceof String) {
                    String prefix = (String) result;
                    if (prefix != null) return prefix;
                }
            }
        } catch (Throwable ignored) { }

        // Plain fallback: empty string (no prefix). Users can include their own prefix via config formats.
        return "";
    }
}