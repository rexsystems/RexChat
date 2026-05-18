package me.rexsystems.rexChat.hooks.discord;

import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;

/**
 * One queued preview that should be attached to the next outbound DiscordSRV
 * chat message containing the matching {@code <RXC=N>} marker.
 *
 * <p>Mirrors {@code DiscordDisplayData} from
 * InteractiveChat-DiscordSRV-Addon's {@code OutboundToDiscordEvents}: when a
 * chat message is rewritten in {@code GameChatMessagePreProcessEvent}, each
 * {@code [item]} / {@code [inv]} / {@code [ec]} occurrence yields one of these
 * along with a numeric id; the id is appended to the message as
 * {@code <RXC=N>} and is then matched by
 * {@link DiscordJDAListener} once the message arrives back from JDA so we can
 * edit the message and attach the rendered preview in place.
 */
public final class PendingPreview {

    public enum Kind { ITEM, INVENTORY, ENDERCHEST }

    private final Kind kind;
    /** Sender display name used in the embed author / image header. */
    private final String playerName;
    /** Stable url for the player's head, shown next to the embed author. */
    private final String avatarUrl;
    /** Optional rich embed (item card). May be {@code null} for image-only previews. */
    private final MessageEmbed embed;
    /** PNG bytes of the rendered inventory / ender chest image. May be {@code null}. */
    private final byte[] imageBytes;
    /** File name for the attached PNG (must match the embed's {@code attachment://} url). */
    private final String fileName;
    /** Wall-clock timestamp this preview was created (used for expiry). */
    private final long createdAtMs;

    private PendingPreview(Kind kind,
                           String playerName,
                           String avatarUrl,
                           MessageEmbed embed,
                           byte[] imageBytes,
                           String fileName) {
        this.kind = kind;
        this.playerName = playerName;
        this.avatarUrl = avatarUrl;
        this.embed = embed;
        this.imageBytes = imageBytes;
        this.fileName = fileName;
        this.createdAtMs = System.currentTimeMillis();
    }

    /** Item preview: rich embed with thumbnail, no PNG attachment. */
    public static PendingPreview item(String playerName, String avatarUrl, MessageEmbed embed) {
        return new PendingPreview(Kind.ITEM, playerName, avatarUrl, embed, null, null);
    }

    /** Inventory preview: rich embed referencing an attached PNG. */
    public static PendingPreview inventory(String playerName,
                                           String avatarUrl,
                                           MessageEmbed embed,
                                           byte[] png,
                                           String fileName) {
        return new PendingPreview(Kind.INVENTORY, playerName, avatarUrl, embed, png, fileName);
    }

    /** Ender chest preview: rich embed referencing an attached PNG. */
    public static PendingPreview enderChest(String playerName,
                                            String avatarUrl,
                                            MessageEmbed embed,
                                            byte[] png,
                                            String fileName) {
        return new PendingPreview(Kind.ENDERCHEST, playerName, avatarUrl, embed, png, fileName);
    }

    public Kind getKind() {
        return kind;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public MessageEmbed getEmbed() {
        return embed;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean hasAttachment() {
        return imageBytes != null && fileName != null;
    }
}
