package ru.lava.lavamenu.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class UiFeedback {
    private UiFeedback() {}

    public static void actionBar(String message) {
        actionBar(Component.literal(message));
    }

    public static void actionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(message);
        }
    }
}
