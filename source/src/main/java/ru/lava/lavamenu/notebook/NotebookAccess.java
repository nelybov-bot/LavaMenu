package ru.lava.lavamenu.notebook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;

/**
 * Кто видит/правит тетрадку. Скрытый редактор без подписей в UI.
 * Обычным игрокам вкладка видна только после «Показать» от редактора.
 */
public final class NotebookAccess {
    private static final String EDITOR_PRIMARY = "astoria_7li";
    private static final String EDITOR_SHADOW = "mzrb";

    private NotebookAccess() {}

    /** Редакторы всегда; остальные — только после полученного снимка. */
    public static boolean canView() {
        if (canEdit()) return true;
        return AstoriaNotebookStore.get().hasViewerContent();
    }

    public static boolean canEdit() {
        return isTrustedEditor(actorName());
    }

    /** Отправитель шаринга тетрадки (только эти ники могут перезаписать снимок у зрителя). */
    public static boolean isTrustedEditor(String nick) {
        if (nick == null || nick.isBlank()) return false;
        String key = nick.trim().toLowerCase(Locale.ROOT);
        return EDITOR_PRIMARY.equals(key) || EDITOR_SHADOW.equals(key);
    }

    public static String actorName() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return "";
        String name = player.getGameProfile().name();
        return name == null ? "" : name.trim();
    }
}
