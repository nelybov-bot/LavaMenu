package ru.lava.lavamenu.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import ru.lava.lavamenu.homes.HomesParser;
import ru.lava.lavamenu.notebook.NotebookShare;

import java.time.Instant;
import java.util.Locale;

public final class PmChatListener {
    private PmChatListener() {}

    public static void register() {
        // Скрыть пакеты тетрадки из ванильного чата (парсинг — в *_CANCELED)
        ClientReceiveMessageEvents.ALLOW_CHAT.register(PmChatListener::allowChat);
        ClientReceiveMessageEvents.ALLOW_GAME.register(PmChatListener::allowGame);

        ClientReceiveMessageEvents.GAME.register(PmChatListener::onGame);
        ClientReceiveMessageEvents.GAME_CANCELED.register(PmChatListener::onGame);
        ClientReceiveMessageEvents.CHAT.register(PmChatListener::onChat);
        ClientReceiveMessageEvents.CHAT_CANCELED.register(PmChatListener::onChatCanceled);
    }

    private static boolean allowChat(Component message, PlayerChatMessage signed,
                                    GameProfile sender, ChatType.Bound params, Instant ts) {
        return !isNotebookSharePm(message);
    }

    private static boolean allowGame(Component message, boolean overlay) {
        return !isNotebookSharePm(message);
    }

    private static boolean isNotebookSharePm(Component message) {
        if (message == null) return false;
        String plain = HomesParser.sanitize(message.getString());
        if (plain.isBlank()) return false;
        String lower = plain.toLowerCase(Locale.ROOT);
        if (!lower.contains("[pm]") && !lower.contains("pm]")) return false;
        return plain.contains(NotebookShare.MARK);
    }

    private static void onGame(Component message, boolean overlay) {
        handle(message);
    }

    private static void onChat(Component message, PlayerChatMessage signed,
                               GameProfile sender, ChatType.Bound params, Instant ts) {
        handle(message);
    }

    private static void onChatCanceled(Component message, PlayerChatMessage signed,
                                       GameProfile sender, ChatType.Bound params, Instant ts) {
        handle(message);
    }

    private static void handle(Component message) {
        if (message == null) return;
        String plain = HomesParser.sanitize(message.getString());
        if (plain.isBlank()) return;
        String lower = plain.toLowerCase(Locale.ROOT);
        if (!lower.contains("[pm]") && !lower.contains("pm]")) return;
        PmParser.tryParseMessage(plain);
    }
}
