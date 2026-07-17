package ru.lava.lavamenu.config;

import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.util.PvpStatus;

public enum RadialAction {
    OPEN_AH("open_ah", "lavamenu.radial.action.ah"),
    OPEN_SHOP("open_shop", "lavamenu.radial.action.shop"),
    SIT("sit", "lavamenu.radial.action.sit"),
    LAY("lay", "lavamenu.radial.action.lay"),
    REFRESH_HOMES("refresh_homes", "lavamenu.radial.action.refresh"),
    LAST_HOME("last_home", "lavamenu.radial.action.last"),
    OPEN_HOMES("open_homes", "lavamenu.radial.action.homes"),
    FAVORITE_1("favorite_1", "lavamenu.radial.action.fav"),
    FRIEND_QUICK("friend_quick", "lavamenu.radial.action.friend"),
    OPEN_CHATS("open_chats", "lavamenu.radial.action.chats"),
    TOGGLE_PVP("toggle_pvp", "lavamenu.radial.action.pvp"),
    OPEN_MENU("open_menu", "lavamenu.radial.action.menu"),
    NONE("none", "lavamenu.radial.action.none");

    public final String id;
    public final String labelKey;

    RadialAction(String id, String labelKey) {
        this.id = id;
        this.labelKey = labelKey;
    }

    public Component label() {
        if (this == TOGGLE_PVP) return PvpStatus.radialActionLabel();
        return Component.translatable(labelKey);
    }

    public boolean isExecutable() {
        return this != NONE;
    }

    public static RadialAction fromId(String s) {
        if (s == null) return OPEN_AH;
        if ("sell".equalsIgnoreCase(s)) return OPEN_AH;
        for (RadialAction a : values()) {
            if (a.id.equalsIgnoreCase(s) || a.name().equalsIgnoreCase(s)) return a;
        }
        return OPEN_AH;
    }

    public static RadialAction[] defaults() {
        return new RadialAction[]{
                OPEN_AH, SIT, LAY, REFRESH_HOMES, LAST_HOME, OPEN_HOMES, FAVORITE_1, OPEN_MENU
        };
    }

    /** Значения для переключения в настройках (без NONE). */
    public static RadialAction[] selectable() {
        RadialAction[] all = values();
        RadialAction[] out = new RadialAction[all.length - 1];
        int j = 0;
        for (RadialAction a : all) {
            if (a != NONE) out[j++] = a;
        }
        return out;
    }
}
