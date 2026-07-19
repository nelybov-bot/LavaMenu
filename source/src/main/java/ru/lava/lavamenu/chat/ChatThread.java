package ru.lava.lavamenu.chat;

import java.util.ArrayList;
import java.util.List;

/** Переписка с одним ником. История не обрезается — удаление только вручную (✕). */
public final class ChatThread {
    public String nick;
    public int unread;
    public final List<ChatMessage> messages = new ArrayList<>();

    public ChatThread(String nick) {
        this.nick = nick == null ? "" : nick;
    }

    public long lastActivityMs() {
        if (messages.isEmpty()) return 0L;
        return messages.get(messages.size() - 1).timeMs;
    }

    public ChatMessage lastMessage() {
        if (messages.isEmpty()) return null;
        return messages.get(messages.size() - 1);
    }

    public void add(ChatMessage msg) {
        messages.add(msg);
    }
}
