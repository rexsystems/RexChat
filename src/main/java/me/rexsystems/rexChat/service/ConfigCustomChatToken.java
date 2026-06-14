package me.rexsystems.rexChat.service;

import me.rexsystems.rexChat.api.CustomChatToken;
import me.rexsystems.rexChat.utils.ColorUtils;
import me.rexsystems.rexChat.utils.PapiUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Config-defined custom chat token loaded from {@code chat-previews.custom-tokens.*}.
 */
public final class ConfigCustomChatToken implements CustomChatToken {

    private final String id;
    private final List<String> aliases;
    private final String labelTemplate;
    private final String hoverTemplate;
    private final ClickAction clickAction;
    private final String clickValue;
    private final String usePermission;

    public ConfigCustomChatToken(String id, ConfigurationSection section) {
        this.id = id;
        this.aliases = normalizeAliases(section.getStringList("tokens"));
        this.labelTemplate = section.getString("label", "&7[" + id + "&7]");
        this.hoverTemplate = section.getString("hover", "&7Click");
        this.clickAction = ClickAction.from(section.getString("click-type", "none"));
        this.clickValue = section.getString("click-value", "");
        String perm = section.getString("permission", "");
        this.usePermission = perm == null || perm.isBlank() ? null : perm;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getAliases() {
        return aliases;
    }

    @Override
    public String getUsePermission() {
        return usePermission;
    }

    @Override
    public Component buildReplacement(Player sender, String matchedToken) {
        String label = applyPlaceholders(labelTemplate, sender);
        String hover = applyPlaceholders(hoverTemplate, sender);
        Component component = ColorUtils.parseComponent(label)
                .hoverEvent(HoverEvent.showText(ColorUtils.parseComponent(hover)));

        if (clickAction != ClickAction.NONE && clickValue != null && !clickValue.isBlank()) {
            String resolvedClick = applyPlaceholders(clickValue, sender);
            ClickEvent clickEvent = switch (clickAction) {
                case OPEN_URL -> ClickEvent.openUrl(resolvedClick);
                case RUN_COMMAND -> ClickEvent.runCommand(resolvedClick.startsWith("/")
                        ? resolvedClick.substring(1) : resolvedClick);
                case SUGGEST_COMMAND -> ClickEvent.suggestCommand(resolvedClick.startsWith("/")
                        ? resolvedClick : "/" + resolvedClick);
                case COPY_TO_CLIPBOARD -> ClickEvent.copyToClipboard(resolvedClick);
                default -> null;
            };
            if (clickEvent != null) {
                component = component.clickEvent(clickEvent);
            }
        }
        return component;
    }

    private String applyPlaceholders(String input, Player sender) {
        if (input == null) {
            return "";
        }
        var loc = sender.getLocation();
        String resolved = input
                .replace("{player}", sender.getName())
                .replace("{display_name}", sender.getDisplayName())
                .replace("{world}", loc.getWorld().getName())
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));
        return PapiUtils.apply(sender, resolved);
    }

    private static List<String> normalizeAliases(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                out.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private enum ClickAction {
        NONE,
        OPEN_URL,
        RUN_COMMAND,
        SUGGEST_COMMAND,
        COPY_TO_CLIPBOARD;

        static ClickAction from(String raw) {
            if (raw == null) {
                return NONE;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "open_url", "url" -> OPEN_URL;
                case "run_command", "command" -> RUN_COMMAND;
                case "suggest_command", "suggest" -> SUGGEST_COMMAND;
                case "copy_to_clipboard", "copy" -> COPY_TO_CLIPBOARD;
                default -> NONE;
            };
        }
    }
}
