package ru.lava.lavamenu.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Локальное хранилище ЛС. Файл: {@code config/lavamenu-chats.json}.
 */
public final class ChatStore {
    private static final ChatStore INSTANCE = new ChatStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<ChatThread> threads = new ArrayList<>();
    private String viewingNick = null;
    private Runnable changeListener = () -> {};

    public static ChatStore get() {
        return INSTANCE;
    }

    private ChatStore() {}

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener != null ? listener : () -> {};
    }

    private void notifyChanged() {
        changeListener.run();
    }

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu-chats.json");
    }

    public void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null || !root.has("threads")) return;
            threads.clear();
            root.getAsJsonArray("threads").forEach(el -> {
                JsonObject o = el.getAsJsonObject();
                ChatThread t = new ChatThread(o.has("nick") ? o.get("nick").getAsString() : "");
                if (o.has("unread")) t.unread = o.get("unread").getAsInt();
                if (o.has("messages")) {
                    o.getAsJsonArray("messages").forEach(mEl -> {
                        JsonObject m = mEl.getAsJsonObject();
                        long time = m.has("t") ? m.get("t").getAsLong() : System.currentTimeMillis();
                        boolean out = m.has("out") && m.get("out").getAsBoolean();
                        String text = m.has("text") ? m.get("text").getAsString() : "";
                        String clock = m.has("clock") ? m.get("clock").getAsString() : "";
                        t.messages.add(new ChatMessage(time, out, text, clock));
                    });
                }
                if (!t.nick.isBlank()) threads.add(t);
            });
        } catch (Throwable ignored) {
        }
    }

    public void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException ignored) {}
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (ChatThread t : threads) {
            JsonObject o = new JsonObject();
            o.addProperty("nick", t.nick);
            o.addProperty("unread", t.unread);
            JsonArray msgs = new JsonArray();
            for (ChatMessage m : t.messages) {
                JsonObject mo = new JsonObject();
                mo.addProperty("t", m.timeMs);
                mo.addProperty("out", m.outgoing);
                mo.addProperty("text", m.text);
                mo.addProperty("clock", m.clock);
                msgs.add(mo);
            }
            o.add("messages", msgs);
            arr.add(o);
        }
        root.add("threads", arr);
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (IOException ignored) {}
    }

    public synchronized List<ChatThread> threadsNewestFirst() {
        List<ChatThread> copy = new ArrayList<>(threads);
        copy.sort(Comparator.comparingLong(ChatThread::lastActivityMs).reversed());
        return copy;
    }

    public synchronized ChatThread find(String nick) {
        if (nick == null || nick.isBlank()) return null;
        String key = nick.toLowerCase(Locale.ROOT);
        for (ChatThread t : threads) {
            if (t.nick.toLowerCase(Locale.ROOT).equals(key)) return t;
        }
        return null;
    }

    public synchronized ChatThread getOrCreate(String nick) {
        ChatThread existing = find(nick);
        if (existing != null) {
            // сохраняем регистр с сервера, если пришёл новый
            if (!existing.nick.equals(nick) && nick != null && !nick.isBlank()) {
                existing.nick = nick;
            }
            return existing;
        }
        ChatThread t = new ChatThread(nick);
        threads.add(0, t);
        return t;
    }

    public void setViewing(String nick) {
        viewingNick = nick;
        if (nick != null) markRead(nick);
    }

    public String viewingNick() {
        return viewingNick;
    }

    public synchronized void markRead(String nick) {
        ChatThread t = find(nick);
        if (t == null) return;
        if (t.unread != 0) {
            t.unread = 0;
            save();
            notifyChanged();
        }
    }

    public synchronized void deleteThread(String nick) {
        ChatThread t = find(nick);
        if (t == null) return;
        threads.remove(t);
        if (viewingNick != null && viewingNick.equalsIgnoreCase(nick)) viewingNick = null;
        save();
        notifyChanged();
    }

    /**
     * Добавить сообщение из парсера или UI.
     * @param fromServer true — пришло из чата; false — оптимистичная отправка из UI
     */
    public synchronized void addMessage(String nick, boolean outgoing, String text, String clock, boolean fromServer) {
        if (nick == null || nick.isBlank() || text == null || text.isBlank()) return;
        ChatThread t = getOrCreate(nick.trim());

        // антидубль: эхо /msg после оптимистичной отправки
        if (fromServer && !t.messages.isEmpty()) {
            ChatMessage last = t.lastMessage();
            if (last != null && last.outgoing == outgoing && last.text.equals(text)
                    && Math.abs(System.currentTimeMillis() - last.timeMs) < 8000) {
                return;
            }
        }

        t.add(new ChatMessage(System.currentTimeMillis(), outgoing, text, clock == null ? "" : clock));

        boolean viewing = viewingNick != null && viewingNick.equalsIgnoreCase(t.nick);
        if (!outgoing && !viewing) {
            t.unread = Math.min(99, t.unread + 1);
        }
        if (viewing && !outgoing) {
            t.unread = 0;
        }

        // поднять тред
        threads.remove(t);
        threads.add(0, t);

        save();
        notifyChanged();

        if (fromServer && !outgoing) {
            ChatToastService.onIncoming(t.nick, text);
        }
    }

    public synchronized int totalUnread() {
        int n = 0;
        for (ChatThread t : threads) n += t.unread;
        return n;
    }
}
