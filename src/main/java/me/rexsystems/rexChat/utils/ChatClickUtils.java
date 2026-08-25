package me.rexsystems.rexChat.utils;

import net.kyori.adventure.text.event.ClickEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class ChatClickUtils {

    public enum ClickType {
        NONE,
        OPEN_URL,
        RUN_COMMAND,
        SUGGEST_COMMAND,
        COPY_TO_CLIPBOARD;

        public static ClickType from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "open_url", "url" -> OPEN_URL;
                case "run_command", "command" -> RUN_COMMAND;
                case "suggest_command", "suggest" -> SUGGEST_COMMAND;
                case "copy_to_clipboard", "copy" -> COPY_TO_CLIPBOARD;
                case "none", "off", "disabled" -> NONE;
                default -> NONE;
            };
        }
    }

    public record ClickSettings(ClickType type, String value) {
        public static final ClickSettings DEFAULT_NAME_CLICK =
                new ClickSettings(ClickType.SUGGEST_COMMAND, "/msg {player} ");
    }

    private ChatClickUtils() {
    }

    public static ClickSettings readNameClick(FileConfiguration cfg, String basePath) {
        if (!cfg.isSet(basePath + ".type")) {
            return ClickSettings.DEFAULT_NAME_CLICK;
        }
        ClickType type = ClickType.from(cfg.getString(basePath + ".type", "none"));
        String value = cfg.getString(basePath + ".value", "");
        if (type != ClickType.NONE && (value == null || value.isBlank())) {
            return ClickSettings.DEFAULT_NAME_CLICK;
        }
        return new ClickSettings(type, value == null ? "" : value);
    }

    public static String resolveNamePlaceholders(String template, Player sender, FileConfiguration cfg) {
        if (template == null) {
            return "";
        }
        String prefix = cfg.getString("messages.prefix", "");
        String chatPrefix = PrefixUtils.getChatPrefix(sender, cfg);
        var loc = sender.getLocation();
        String resolved = template
                .replace("%rc_prefix%", prefix)
                .replace("{prefix}", chatPrefix)
                .replace("{player}", sender.getName())
                .replace("{name}", sender.getName())
                .replace("{display_name}", sender.getDisplayName())
                .replace("{world}", loc.getWorld().getName())
                .replace("{health}", String.valueOf((int) Math.round(sender.getHealth())))
                .replace("{max_health}", String.valueOf((int) Math.round(sender.getMaxHealth())))
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()))
                .replace("{ping}", String.valueOf(getPing(sender)));
        return PapiUtils.apply(sender, resolved);
    }

    public static ClickEvent toAdventureClick(ClickSettings settings, Player sender, FileConfiguration cfg) {
        if (settings == null || settings.type() == ClickType.NONE) {
            return null;
        }
        String resolved = resolveNamePlaceholders(settings.value(), sender, cfg);
        return switch (settings.type()) {
            case OPEN_URL -> ClickEvent.openUrl(resolved);
            case RUN_COMMAND -> ClickEvent.runCommand(stripLeadingSlash(resolved));
            case SUGGEST_COMMAND -> ClickEvent.suggestCommand(ensureLeadingSlash(resolved));
            case COPY_TO_CLIPBOARD -> ClickEvent.copyToClipboard(resolved);
            default -> null;
        };
    }

    public static void applyBungeeClick(BaseComponent component, ClickSettings settings, Player sender,
            FileConfiguration cfg) {
        if (component == null || settings == null || settings.type() == ClickType.NONE) {
            return;
        }
        String resolved = resolveNamePlaceholders(settings.value(), sender, cfg);
        net.md_5.bungee.api.chat.ClickEvent.Action action = switch (settings.type()) {
            case OPEN_URL -> net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL;
            case RUN_COMMAND -> net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND -> net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND;
            case COPY_TO_CLIPBOARD -> net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> null;
        };
        if (action == null) {
            return;
        }
        String payload = switch (settings.type()) {
            case RUN_COMMAND -> stripLeadingSlash(resolved);
            case SUGGEST_COMMAND -> ensureLeadingSlash(resolved);
            default -> resolved;
        };
        component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(action, payload));
    }

    private static String stripLeadingSlash(String value) {
        if (value != null && value.startsWith("/")) {
            return value.substring(1);
        }
        return value == null ? "" : value;
    }

    private static String ensureLeadingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "/";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
