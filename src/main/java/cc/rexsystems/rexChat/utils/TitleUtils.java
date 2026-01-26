package cc.rexsystems.rexChat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class TitleUtils {

    /**
     * Sends a title in a version-aware way:
     * - Modern Player#sendTitle(String,String,int,int,int)
     * - Legacy Player#sendTitle(String,String)
     * - 1.8 NMS PacketPlayOutTitle fallback
     * Returns true if a title was dispatched successfully.
     */
    public static boolean sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return false;

        String tTitle = ColorUtils.translateLegacyColors(title == null ? "" : title);
        String tSubtitle = ColorUtils.translateLegacyColors(subtitle == null ? "" : subtitle);

        // Try modern timed signature
        try {
            Method modern = Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            modern.invoke(player, tTitle, tSubtitle, fadeIn, stay, fadeOut);
            return true;
        } catch (Throwable ignored) { }

        // Try legacy two-argument signature
        try {
            Method legacy = Player.class.getMethod("sendTitle", String.class, String.class);
            legacy.invoke(player, tTitle, tSubtitle);
            return true;
        } catch (Throwable ignored) { }

        // Fallback for 1.8 via NMS packets
        try {
            return sendNmsTitle18(player, tTitle, tSubtitle, fadeIn, stay, fadeOut);
        } catch (Throwable ignored) { }

        return false;
    }

    private static boolean sendNmsTitle18(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) throws Exception {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

        Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
        Object craftPlayer = craftPlayerClass.cast(player);
        Method getHandle = craftPlayerClass.getMethod("getHandle");
        Object entityPlayer = getHandle.invoke(craftPlayer);
        Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);

        Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
        Class<?> enumTitleClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle$EnumTitleAction");
        Class<?> ichatClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
        Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");

        Method aMethod = chatSerializerClass.getMethod("a", String.class);
        Object titleComp = null;
        Object subtitleComp = null;
        if (title != null && !title.isEmpty()) {
            String json = "{\"text\":\"" + jsonEscape(title) + "\"}";
            titleComp = aMethod.invoke(null, json);
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            String json = "{\"text\":\"" + jsonEscape(subtitle) + "\"}";
            subtitleComp = aMethod.invoke(null, json);
        }

        Object timesEnum = null, titleEnum = null, subtitleEnum = null;
        for (Object constant : enumTitleClass.getEnumConstants()) {
            String name = constant.toString();
            if ("TIMES".equals(name)) timesEnum = constant;
            else if ("TITLE".equals(name)) titleEnum = constant;
            else if ("SUBTITLE".equals(name)) subtitleEnum = constant;
        }

        // TIMES packet (some 1.8 builds require sending this first)
        try {
            Constructor<?> timesCtor = packetClass.getConstructor(enumTitleClass, ichatClass, int.class, int.class, int.class);
            Object timesPacket = timesCtor.newInstance(timesEnum, null, fadeIn, stay, fadeOut);
            sendPacket(connection, timesPacket, version);
        } catch (NoSuchMethodException e) {
            try {
                Constructor<?> altTimes = packetClass.getConstructor(int.class, int.class, int.class);
                Object timesPacket = altTimes.newInstance(fadeIn, stay, fadeOut);
                sendPacket(connection, timesPacket, version);
            } catch (NoSuchMethodException ignored) { }
        }

        if (titleComp != null && titleEnum != null) {
            Constructor<?> titleCtor = packetClass.getConstructor(enumTitleClass, ichatClass);
            Object titlePacket = titleCtor.newInstance(titleEnum, titleComp);
            sendPacket(connection, titlePacket, version);
        }
        if (subtitleComp != null && subtitleEnum != null) {
            Constructor<?> subCtor = packetClass.getConstructor(enumTitleClass, ichatClass);
            Object subPacket = subCtor.newInstance(subtitleEnum, subtitleComp);
            sendPacket(connection, subPacket, version);
        }

        return true;
    }

    private static void sendPacket(Object connection, Object packet, String version) throws Exception {
        Class<?> packetInterface = Class.forName("net.minecraft.server." + version + ".Packet");
        Method sendPacket = connection.getClass().getMethod("sendPacket", packetInterface);
        sendPacket.invoke(connection, packet);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}