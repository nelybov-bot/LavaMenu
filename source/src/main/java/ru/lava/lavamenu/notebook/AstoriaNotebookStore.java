package ru.lava.lavamenu.notebook;

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
import java.util.List;
import java.util.Locale;

/**
 * Локальная «Тетрадка Астории» — {@code config/lavamenu-notebook.json}.
 * Редакторы правят у себя; зрителям снимок приходит через /msg ({@link NotebookShare}).
 */
public final class AstoriaNotebookStore {
    private static final AstoriaNotebookStore INSTANCE = new AstoriaNotebookStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<NotebookEntry> entries = new ArrayList<>();
    private String sharedFrom = "";
    private Runnable changeListener = () -> {};

    public static AstoriaNotebookStore get() {
        return INSTANCE;
    }

    private AstoriaNotebookStore() {}

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener != null ? listener : () -> {};
    }

    private void notifyChanged() {
        changeListener.run();
    }

    public synchronized String sharedFrom() {
        return sharedFrom;
    }

    /** У зрителя вкладка после успешного «Показать» (флаг sharedFrom). */
    public synchronized boolean hasViewerContent() {
        return !sharedFrom.isBlank();
    }

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu-notebook.json");
    }

    public synchronized List<NotebookEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            if (root.has("sharedFrom")) sharedFrom = root.get("sharedFrom").getAsString();
            entries.clear();
            if (root.has("entries")) {
                root.getAsJsonArray("entries").forEach(el -> {
                    JsonObject o = el.getAsJsonObject();
                    NotebookEntry e = new NotebookEntry(
                            o.has("nick") ? o.get("nick").getAsString() : "",
                            o.has("reason") ? o.get("reason").getAsString() : "",
                            o.has("addedAt") ? o.get("addedAt").getAsLong() : 0L,
                            o.has("addedBy") ? o.get("addedBy").getAsString() : ""
                    );
                    if (!e.nick.isBlank()) entries.add(e);
                });
            }
            // Старые снимки без sharedFrom — иначе вкладка пропадёт
            if (sharedFrom.isBlank() && !entries.isEmpty()) {
                for (NotebookEntry e : entries) {
                    if (e.addedBy != null && !e.addedBy.isBlank()) {
                        sharedFrom = e.addedBy;
                        break;
                    }
                }
                if (sharedFrom.isBlank()) sharedFrom = "?";
            }
        } catch (Throwable ignored) {
        }
    }

    public synchronized void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException ignored) {}
        JsonObject root = new JsonObject();
        if (sharedFrom != null && !sharedFrom.isBlank()) {
            root.addProperty("sharedFrom", sharedFrom);
        }
        JsonArray arr = new JsonArray();
        for (NotebookEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("nick", e.nick);
            o.addProperty("reason", e.reason);
            o.addProperty("addedAt", e.addedAt);
            o.addProperty("addedBy", e.addedBy);
            arr.add(o);
        }
        root.add("entries", arr);
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (IOException ignored) {}
    }

    public synchronized boolean add(String nick, String reason) {
        if (nick == null || nick.isBlank()) return false;
        String n = nick.trim();
        String r = reason == null ? "" : reason.trim();
        sharedFrom = "";
        for (NotebookEntry e : entries) {
            if (e.nick.equalsIgnoreCase(n)) {
                e.reason = r;
                e.addedAt = System.currentTimeMillis();
                e.addedBy = NotebookAccess.actorName();
                save();
                notifyChanged();
                return true;
            }
        }
        entries.add(0, new NotebookEntry(n, r, System.currentTimeMillis(), NotebookAccess.actorName()));
        save();
        notifyChanged();
        return true;
    }

    public synchronized void setReason(String nick, String reason) {
        NotebookEntry e = find(nick);
        if (e == null) return;
        e.reason = reason == null ? "" : reason.trim();
        sharedFrom = "";
        save();
        notifyChanged();
    }

    public synchronized void remove(String nick) {
        if (nick == null) return;
        String key = nick.toLowerCase(Locale.ROOT);
        entries.removeIf(e -> e.nick.toLowerCase(Locale.ROOT).equals(key));
        sharedFrom = "";
        save();
        notifyChanged();
    }

    public synchronized NotebookEntry find(String nick) {
        if (nick == null || nick.isBlank()) return null;
        String key = nick.toLowerCase(Locale.ROOT);
        for (NotebookEntry e : entries) {
            if (e.nick.toLowerCase(Locale.ROOT).equals(key)) return e;
        }
        return null;
    }

    /** Зритель получил снимок через /msg — заменить локальный просмотр. */
    public synchronized void replaceFromShare(List<NotebookEntry> incoming, String from) {
        entries.clear();
        if (incoming != null) {
            for (NotebookEntry e : incoming) {
                if (e != null && !e.nick.isBlank()) entries.add(e);
            }
        }
        sharedFrom = from == null ? "" : from.trim();
        save();
        notifyChanged();
    }
}
