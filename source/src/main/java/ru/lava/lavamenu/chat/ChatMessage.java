package ru.lava.lavamenu.chat;

/** Одно ЛС в истории. */
public final class ChatMessage {
    public final long timeMs;
    public final boolean outgoing;
    public final String text;
    /** Время с сервера, напр. 16:04:39 (может быть пустым). */
    public final String clock;

    public ChatMessage(long timeMs, boolean outgoing, String text, String clock) {
        this.timeMs = timeMs;
        this.outgoing = outgoing;
        this.text = text == null ? "" : text;
        this.clock = clock == null ? "" : clock;
    }
}
