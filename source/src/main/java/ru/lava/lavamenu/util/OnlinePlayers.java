package ru.lava.lavamenu.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Locale;

/** Список игроков онлайн (тот же источник, что Tab / меню P). */
public final class OnlinePlayers {
    private OnlinePlayers() {}

    public static PlayerInfo find(String nick) {
        if (nick == null || nick.isBlank()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        String key = nick.toLowerCase(Locale.ROOT);
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            String name = info.getProfile().name();
            if (name != null && name.toLowerCase(Locale.ROOT).equals(key)) return info;
        }
        return null;
    }

    public static boolean isOnline(String nick) {
        return find(nick) != null;
    }

    public static java.util.Set<String> onlineNicksLower() {
        java.util.Set<String> out = new java.util.HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return out;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            String name = info.getProfile().name();
            if (name != null && !name.isBlank()) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
