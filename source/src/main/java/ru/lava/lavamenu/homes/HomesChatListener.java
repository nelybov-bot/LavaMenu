package ru.lava.lavamenu.homes;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.slf4j.Logger;
import ru.lava.lavamenu.LavaMenuClient;

import java.time.Instant;

/**
 * Слушает и GAME, и CHAT — плагины иногда шлют системные строки как chat-пакеты.
 */
public final class HomesChatListener {
    private static final Logger LOGGER = LavaMenuClient.LOGGER;

    private HomesChatListener() {}

    public static void register() {
        ClientReceiveMessageEvents.GAME.register(HomesChatListener::onGameMessage);
        ClientReceiveMessageEvents.GAME_CANCELED.register(HomesChatListener::onGameMessage);
        ClientReceiveMessageEvents.CHAT.register(HomesChatListener::onChatMessage);
        ClientReceiveMessageEvents.CHAT_CANCELED.register(HomesChatListener::onChatMessageCanceled);
        LOGGER.info("HomesChatListener registered (GAME+CHAT)");
    }

    private static void onGameMessage(Component message, boolean overlay) {
        handle(message, overlay ? "game-overlay" : "game");
    }

    private static void onChatMessage(Component message, PlayerChatMessage signedMessage,
                                      GameProfile sender, ChatType.Bound params, Instant receptionTimestamp) {
        handle(message, "chat");
    }

    private static void onChatMessageCanceled(Component message, PlayerChatMessage signedMessage,
                                              GameProfile sender, ChatType.Bound params, Instant receptionTimestamp) {
        handle(message, "chat-canceled");
    }

    private static void handle(Component message, String channel) {
        String plain = toPlain(message);
        if (plain.isBlank()) return;

        if (HomesParser.isCapturing() && HomesParser.isHomesListHeader(plain)) {
            LOGGER.info("homes-header via {}: {}", channel, plain);
        }

        if (shouldParse(plain)) {
            HomesParser.tryParseMessage(plain);
        }
    }

    private static String toPlain(Component message) {
        if (message == null) return "";
        // getString() обходит siblings; sanitize убирает § / nbsp / fullwidth ':'
        return HomesParser.sanitize(message.getString());
    }

    private static boolean shouldParse(String plain) {
        if (plain.isBlank()) return false;
        if (HomesParser.isParsing()) return true;
        return HomesParser.isHomesListHeader(plain);
    }
}
