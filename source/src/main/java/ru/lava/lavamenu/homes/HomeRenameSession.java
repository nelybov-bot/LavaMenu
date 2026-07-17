package ru.lava.lavamenu.homes;

import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.UiFeedback;

/**
 * Безопасное «переименование» без телепорта и без фиксированных задержек между
 * шагами: /sethome new! с текущей позиции → /homes → /delhome old только если
 * новое имя появилось в ответе сервера.
 */
public final class HomeRenameSession {
    private static final Logger LOGGER = LavaMenuClient.LOGGER;
    /** Макс. ожидание появления нового имени в /homes (~5 с). */
    private static final int TIMEOUT_TICKS = 100;
    /** Повторный /homes, пока ждём. */
    private static final int RESEND_EVERY = 40;

    private static String oldName;
    private static String newName;
    private static int ticksLeft;
    private static boolean active;

    private HomeRenameSession() {}

    public static boolean isActive() {
        return active;
    }

    public static void begin(String old, String neu) {
        if (old == null || neu == null || old.isBlank() || neu.isBlank()) return;
        if (old.equals(neu)) return;

        cancelQuiet();
        oldName = old;
        newName = neu;
        ticksLeft = TIMEOUT_TICKS;
        active = true;

        CommandHelper.sendFromUi("sethome " + newName + "!");
        CommandHelper.sendDelayed("homes", 15);
        UiFeedback.actionBar(Component.translatable("lavamenu.homes.rename_started", oldName, newName));
    }

    public static void tick() {
        if (!active) return;
        ticksLeft--;
        if (ticksLeft > 0 && ticksLeft % RESEND_EVERY == 0) {
            CommandHelper.sendFromUi("homes");
        }
        if (ticksLeft <= 0) {
            LOGGER.warn(
                    "Home rename timed out waiting for new name in /homes (old={}, new={})",
                    oldName, newName);
            UiFeedback.actionBar(Component.translatable("lavamenu.homes.rename_failed", oldName, newName));
            cancelQuiet();
        }
    }

    /** Вызывается после штатного/таймаутного конца сессии парсера /homes. */
    public static void onHomesListParsed() {
        if (!active) return;
        if (HomesData.get().hasNameExact(newName)) {
            String del = oldName;
            String neu = newName;
            cancelQuiet();
            if (!del.equals(neu)) {
                CommandHelper.sendFromUi("delhome " + del);
                UiFeedback.actionBar(Component.translatable("lavamenu.homes.rename_done", del, neu));
            }
        }
    }

    private static void cancelQuiet() {
        active = false;
        oldName = null;
        newName = null;
        ticksLeft = 0;
    }
}
