package me.rexsystems.rexChat.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/**
 * Provides access to Vault's Economy provider via reflection, so RexChat
 * does not require Vault as a hard dependency.
 *
 * Returns null/false when Vault or an Economy provider is not available.
 */
public final class VaultEconomyUtils {

    private static volatile Boolean checked = null;
    private static volatile Object economyProvider = null;
    private static volatile Method getBalanceMethod = null;
    private static volatile Method formatMethod = null;
    private static volatile Method currencyNamePluralMethod = null;
    private static volatile Method currencyNameSingularMethod = null;

    private VaultEconomyUtils() {
    }

    /**
     * Lazy-initialize the economy provider via reflection.
     * Safe to call repeatedly; result is cached.
     */
    private static void ensureChecked() {
        if (checked != null) return;
        synchronized (VaultEconomyUtils.class) {
            if (checked != null) return;
            try {
                Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
                Object registration = Bukkit.getServicesManager().getRegistration(econClass);
                if (registration != null) {
                    Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                    if (provider != null) {
                        economyProvider = provider;
                        try {
                            getBalanceMethod = econClass.getMethod("getBalance", OfflinePlayer.class);
                        } catch (NoSuchMethodException ignored) {
                            // older vault used String player name
                            try {
                                getBalanceMethod = econClass.getMethod("getBalance", String.class);
                            } catch (NoSuchMethodException ignored2) {
                            }
                        }
                        try {
                            formatMethod = econClass.getMethod("format", double.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                        try {
                            currencyNamePluralMethod = econClass.getMethod("currencyNamePlural");
                        } catch (NoSuchMethodException ignored) {
                        }
                        try {
                            currencyNameSingularMethod = econClass.getMethod("currencyNameSingular");
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Vault not present or no provider registered
            }
            checked = Boolean.TRUE;
        }
    }

    /**
     * @return true if a Vault Economy provider is available.
     */
    public static boolean isAvailable() {
        ensureChecked();
        return economyProvider != null && getBalanceMethod != null;
    }

    /**
     * Get the balance for a player. Returns null if not available.
     */
    public static Double getBalance(OfflinePlayer player) {
        ensureChecked();
        if (economyProvider == null || getBalanceMethod == null || player == null) return null;
        try {
            Object arg;
            if (getBalanceMethod.getParameterTypes()[0] == String.class) {
                arg = player.getName();
            } else {
                arg = player;
            }
            Object result = getBalanceMethod.invoke(economyProvider, arg);
            if (result instanceof Number) return ((Number) result).doubleValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Format an amount using Vault's Economy.format(double) when available.
     * Falls back to a plain decimal if the provider doesn't support it.
     */
    public static String format(double amount) {
        ensureChecked();
        if (economyProvider != null && formatMethod != null) {
            try {
                Object res = formatMethod.invoke(economyProvider, amount);
                if (res instanceof String) return (String) res;
            } catch (Throwable ignored) {
            }
        }
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    /**
     * Get the configured plural currency name from Vault, or null if unavailable.
     */
    public static String currencyNamePlural() {
        ensureChecked();
        if (economyProvider == null || currencyNamePluralMethod == null) return null;
        try {
            Object res = currencyNamePluralMethod.invoke(economyProvider);
            if (res instanceof String) return (String) res;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Get the configured singular currency name from Vault, or null if unavailable.
     */
    public static String currencyNameSingular() {
        ensureChecked();
        if (economyProvider == null || currencyNameSingularMethod == null) return null;
        try {
            Object res = currencyNameSingularMethod.invoke(economyProvider);
            if (res instanceof String) return (String) res;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
