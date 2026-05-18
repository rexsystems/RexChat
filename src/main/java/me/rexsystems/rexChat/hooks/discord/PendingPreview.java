package me.rexsystems.rexChat.hooks.discord;

import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 *
 * <p>Each preview can carry zero or more {@link Attachment} files (e.g. an
 * item icon for the embed thumbnail PLUS a tooltip image for the embed body).
 */
public final class PendingPreview {

    public enum Kind { ITEM, INVENTORY, ENDERCHEST }

    /** A single PNG attachment — the file name must match an
     *  {@code attachment://} URL referenced from the embed. */
    public static final class Attachment {
        public final byte[] bytes;
        public final String name;

        public Attachment(byte[] bytes, String name) {
            this.bytes = bytes;
            this.name = name;
        }
    }

    private final Kind kind;
    /** Sender display name used in the embed author / image header. */
    private final String playerName;
    /** Stable url for the player's head, shown next to the embed author. */
    private final String avatarUrl;
    /** Optional rich embed (item card). May be {@code null} for image-only previews. */
    private final MessageEmbed embed;
    /** All files attached to this preview (icon, tooltip, …). May be empty. */
    private final List<Attachment> attachments;
    /** Wall-clock timestamp this preview was created (used for expiry). */
    private final long createdAtMs;

    private PendingPreview(Kind kind,
                           String playerName,
                           String avatarUrl,
                           MessageEmbed embed,
                           List<Attachment> attachments) {
        this.kind = kind;
        this.playerName = playerName;
        this.avatarUrl = avatarUrl;
        this.embed = embed;
        this.attachments = attachments == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
        this.createdAtMs = System.currentTimeMillis();
    }

    /** Item preview: rich embed only, no PNG attachments. */
    public static PendingPreview item(String playerName, String avatarUrl, MessageEmbed embed) {
        return new PendingPreview(Kind.ITEM, playerName, avatarUrl, embed, null);
    }

    /**
     * Item preview backed by one or more rendered PNGs (icon, tooltip card, …).
     * The embed is expected to reference each file by name via
     * {@code setThumbnail("attachment://...")} or
     * {@code setImage("attachment://...")}.
     */
    public static PendingPreview item(String playerName,
                                      String avatarUrl,
                                      MessageEmbed embed,
                                      List<Attachment> attachments) {
        return new PendingPreview(Kind.ITEM, playerName, avatarUrl, embed, attachments);
    }

    /** Inventory preview: rich embed + a single PNG attachment. */
    public static PendingPreview inventory(String playerName,
                                           String avatarUrl,
                                           MessageEmbed embed,
                                           byte[] png,
                                           String fileName) {
        return new PendingPreview(Kind.INVENTORY, playerName, avatarUrl, embed,
                Collections.singletonList(new Attachment(png, fileName)));
    }

    /** Ender chest preview: rich embed + a single PNG attachment. */
    public static PendingPreview enderChest(String playerName,
                                            String avatarUrl,
                                            MessageEmbed embed,
                                            byte[] png,
                                            String fileName) {
        return new PendingPreview(Kind.ENDERCHEST, playerName, avatarUrl, embed,
                Collections.singletonList(new Attachment(png, fileName)));
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

    /** All PNG attachments registered with this preview. Order matters — the
     *  first one is the "primary" file (matches the embed's image), the
     *  others are referenced from {@code setThumbnail} etc. */
    public List<Attachment> getAttachments() {
        return attachments;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean hasAttachment() {
        return !attachments.isEmpty();
    }
}
