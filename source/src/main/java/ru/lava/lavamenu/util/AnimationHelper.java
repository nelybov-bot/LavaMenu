package ru.lava.lavamenu.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

/**
 * Переключение /sit ↔ /lay: короткий sneak-пакет на сервер (без Shift на клиенте).
 * Не трогаем KeyMapping.shift — иначе Inventory Profiles и др. моды реагируют на «переодевание».
 */
public final class AnimationHelper {
    public enum Type {
        SIT("sit"),
        LAY("lay");

        private final String command;

        Type(String command) {
            this.command = command;
        }

        public String command() {
            return command;
        }
    }

    private static final int SNEAK_HOLD_TICKS = 2;
    private static final int SNEAK_RELEASE_TICKS = 1;
    private static final int BEFORE_CMD_TICKS = 2;

    private static Type tracked = null;

    private AnimationHelper() {}

    public static void register() {}

    public static void closeAndPlay(Type type) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.execute(() -> play(type));
    }

    public static void play(Type type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (tracked != null && tracked != type) {
            pulseSneakToServer(mc, () -> send(type));
        } else {
            send(type);
        }
    }

    private static void send(Type type) {
        if (CommandHelper.sendFromUi(type.command())) {
            tracked = type;
            UiFeedback.actionBar(Component.translatable("lavamenu.anim.sent", type.command()));
        }
    }

    /** Только серверный пакет sneak — клиентский Shift не нажимаем. */
    private static void pulseSneakToServer(Minecraft mc, Runnable after) {
        if (mc.player == null) {
            after.run();
            return;
        }
        Input base = mc.player.input.keyPresses;
        holdSneakPacket(mc, base, SNEAK_HOLD_TICKS, 0, () ->
                releaseSneakPacket(mc, base, SNEAK_RELEASE_TICKS, 0, () ->
                        runAfterTicks(mc, BEFORE_CMD_TICKS, () -> {
                            tracked = null;
                            after.run();
                        })
                )
        );
    }

    private static void holdSneakPacket(Minecraft mc, Input base, int ticks, int tick, Runnable next) {
        if (mc.player == null) return;
        sendSneakPacket(mc, base, true);
        if (tick + 1 >= ticks) {
            next.run();
        } else {
            runAfterTicks(mc, 1, () -> holdSneakPacket(mc, base, ticks, tick + 1, next));
        }
    }

    private static void releaseSneakPacket(Minecraft mc, Input base, int ticks, int tick, Runnable next) {
        if (mc.player == null) return;
        sendSneakPacket(mc, base, false);
        if (tick + 1 >= ticks) {
            next.run();
        } else {
            runAfterTicks(mc, 1, () -> releaseSneakPacket(mc, base, ticks, tick + 1, next));
        }
    }

    private static void sendSneakPacket(Minecraft mc, Input base, boolean sneak) {
        mc.player.connection.send(new ServerboundPlayerInputPacket(inputWithShift(base, sneak)));
    }

    private static Input inputWithShift(Input base, boolean shift) {
        return new Input(
                base.forward(), base.backward(), base.left(), base.right(), base.jump(), shift, base.sprint()
        );
    }

    private static void runAfterTicks(Minecraft mc, int ticks, Runnable action) {
        if (ticks <= 0) {
            mc.execute(action);
            return;
        }
        int[] left = {ticks};
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (mc.player == null) return;
                left[0]--;
                if (left[0] <= 0) {
                    action.run();
                } else {
                    mc.execute(this);
                }
            }
        };
        mc.execute(task);
    }
}
