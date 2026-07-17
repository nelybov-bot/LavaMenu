package ru.lava.lavamenu.util;

import net.minecraft.client.Minecraft;
import ru.lava.lavamenu.config.LavaMenuConfig;

public final class CommandHelper {
    private static long lastSentMs = 0;
    private static String lastCommand = "";

    private CommandHelper() {}

    /** Отправляет серверную команду (/sit, /homes, /home name …). */
    public static boolean send(String commandWithoutSlash) {
        return send(commandWithoutSlash, false);
    }

    /** UI-кнопки: без общего cooldown, чтобы клики не «пропадали». */
    public static boolean sendFromUi(String commandWithoutSlash) {
        return send(commandWithoutSlash, true);
    }

    private static boolean send(String commandWithoutSlash, boolean fromUi) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            UiFeedback.actionBar("§cНет игрока — команда не отправлена");
            return false;
        }

        String cmd = commandWithoutSlash.startsWith("/")
                ? commandWithoutSlash.substring(1)
                : commandWithoutSlash;

        long now = System.currentTimeMillis();
        if (!fromUi) {
            long cd = LavaMenuConfig.get().cooldownMs;
            if (cmd.equals(lastCommand) && now - lastSentMs < cd) {
                return false;
            }
        }

        // sendCommand = то же, что ввод /cmd в чате (ServerboundChatCommandPacket)
        mc.player.connection.sendCommand(cmd);
        lastSentMs = now;
        lastCommand = cmd;
        return true;
    }

    /** Закрыть экран и выполнить команду на следующем тике (нужно для /sit, /lay, /ah). */
    public static void closeAndSend(String commandWithoutSlash) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.execute(() -> {
            if (sendFromUi(commandWithoutSlash)) {
                UiFeedback.actionBar("§7→ /" + stripSlash(commandWithoutSlash));
            }
        });
    }

    public static void sendDelayed(String commandWithoutSlash, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int[] left = {ticks};
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (mc.player == null) return;
                left[0]--;
                if (left[0] <= 0) {
                    sendFromUi(commandWithoutSlash);
                } else {
                    mc.execute(this);
                }
            }
        };
        mc.execute(task);
    }

    private static String stripSlash(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
