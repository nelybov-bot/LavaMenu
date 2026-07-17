package ru.lava.lavamenu.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Пресеты звука уведомления ЛС. */
public enum ChatNotifySound {
    CHIME("chime"),
    PLING("pling"),
    ORB("orb"),
    POP("pop"),
    OFF("off");

    public final String id;

    ChatNotifySound(String id) {
        this.id = id;
    }

    public static ChatNotifySound fromId(String s) {
        if (s == null || s.isBlank()) return CHIME;
        for (ChatNotifySound v : values()) {
            if (v.id.equalsIgnoreCase(s.trim())) return v;
        }
        return CHIME;
    }

    public ChatNotifySound next() {
        ChatNotifySound[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public Component label() {
        return Component.translatable("lavamenu.chats.notify_sound." + id);
    }

    public void play() {
        if (this == OFF) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        SoundEvent ev = switch (this) {
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
            case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case POP -> SoundEvents.UI_BUTTON_CLICK.value();
            case OFF -> null;
        };
        if (ev == null) return;
        float vol = this == ORB ? 0.25f : 0.35f;
        float pitch = this == CHIME ? 1.15f : (this == PLING ? 1.35f : 1.0f);
        player.playSound(ev, vol, pitch);
    }
}
