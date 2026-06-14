package me.rexsystems.rexChat.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * A custom chat token registered through {@link RexChatAPI#registerCustomToken(CustomChatToken)}.
 *
 * @since 1.6.6
 */
public interface CustomChatToken {

    /** Stable id (e.g. {@code "discord"}). */
    String getId();

    /** Literal strings players can type (e.g. {@code [discord]}). Case-insensitive matching. */
    Collection<String> getAliases();

    /**
     * Permission required to use this token in chat, or {@code null} / empty for none.
     */
    default String getUsePermission() {
        return null;
    }

    /**
     * Build the clickable chat component that replaces the matched token.
     *
     * @param sender       player who sent the message
     * @param matchedToken the exact alias that was matched
     */
    Component buildReplacement(Player sender, String matchedToken);

    /** When true, chat-color presets won't recolor this token's text. Default: true. */
    default boolean excludeFromChatColor() {
        return true;
    }
}
