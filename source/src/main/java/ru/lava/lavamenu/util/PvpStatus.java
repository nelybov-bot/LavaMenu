package ru.lava.lavamenu.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.homes.HomesParser;

import java.lang.reflect.Field;

/**
 * Статус PvP с сервера LavaWin из текста Tab (header/footer).
 * Пример: «PvP режим отключен» / «PvP режим включен».
 */
public final class PvpStatus {
    private static Field headerField;
    private static Field footerField;
    private static boolean fieldsResolved;

    private PvpStatus() {}

    /** @return true=вкл, false=выкл, null=неизвестно */
    public static Boolean readFromTab() {
        String text = tabText();
        if (text.isBlank()) return null;
        String lower = text.toLowerCase();
        if (lower.contains("pvp") && (lower.contains("отключ") || lower.contains("выключ") || lower.contains("disabled") || lower.contains("off"))) {
            return false;
        }
        if (lower.contains("pvp") && (lower.contains("включ") || lower.contains("enabled") || lower.contains(" on"))) {
            return true;
        }
        return null;
    }

    /** Читает Tab и обновляет локальный флаг, если статус известен. */
    public static boolean syncFromTab() {
        Boolean status = readFromTab();
        if (status == null) return false;
        if (LavaMenuConfig.get().pvpEnabled != status) {
            LavaMenuConfig.get().pvpEnabled = status;
            LavaMenuConfig.get().save();
        }
        return true;
    }

    public static void toggleViaCommand() {
        syncFromTab();
        boolean next = !LavaMenuConfig.get().pvpEnabled;
        LavaMenuConfig.get().pvpEnabled = next;
        LavaMenuConfig.get().save();
        CommandHelper.closeAndSend(next ? "pvp on" : "pvp off");
    }

    /** Подпись для колеса: показывает действие, которое будет выполнено. */
    public static Component radialActionLabel() {
        syncFromTab();
        boolean on = LavaMenuConfig.get().pvpEnabled;
        return Component.translatable(on
                ? "lavamenu.radial.action.pvp_disable"
                : "lavamenu.radial.action.pvp_enable");
    }

    /** true = сейчас включён (кнопка предложит выключить). */
    public static boolean isEnabled() {
        syncFromTab();
        return LavaMenuConfig.get().pvpEnabled;
    }

    private static String tabText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return "";
        PlayerTabOverlay tab = mc.gui.getTabList();
        if (tab == null) return "";
        resolveFields();
        StringBuilder sb = new StringBuilder();
        append(sb, readComponent(tab, headerField));
        append(sb, readComponent(tab, footerField));
        return HomesParser.stripFormatting(sb.toString());
    }

    private static void append(StringBuilder sb, Component c) {
        if (c == null) return;
        if (!sb.isEmpty()) sb.append('\n');
        sb.append(c.getString());
    }

    private static Component readComponent(PlayerTabOverlay tab, Field field) {
        if (field == null) return null;
        try {
            return (Component) field.get(tab);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void resolveFields() {
        if (fieldsResolved) return;
        fieldsResolved = true;
        try {
            headerField = PlayerTabOverlay.class.getDeclaredField("header");
            headerField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            headerField = null;
        }
        try {
            footerField = PlayerTabOverlay.class.getDeclaredField("footer");
            footerField.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            footerField = null;
        }
    }
}
