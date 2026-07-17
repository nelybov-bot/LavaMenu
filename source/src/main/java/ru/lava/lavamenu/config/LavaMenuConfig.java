package ru.lava.lavamenu.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import ru.lava.lavamenu.chat.ChatNotifySound;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LavaMenuConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LavaMenuConfig INSTANCE = new LavaMenuConfig();

    public static LavaMenuConfig get() {
        return INSTANCE;
    }

    public long cooldownMs = 400;
    public final Keys keys = new Keys();
    public final Radial radial = new Radial();
    public final Homes homes = new Homes();
    public final List<FriendEntry> friends = new ArrayList<>();
    public boolean pvpEnabled = false;
    /** Всплывающие уведомления о входящих ЛС. */
    public boolean chatsNotify = true;
    public ChatNotifySound chatsNotifySound = ChatNotifySound.CHIME;

    private Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu.json");
    }

    public void load() {
        Path p = configPath();
        if (!Files.exists(p)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;

            if (root.has("cooldownMs")) cooldownMs = root.get("cooldownMs").getAsLong();

            if (root.has("keys")) {
                JsonObject o = root.getAsJsonObject("keys");
                if (o.has("main")) keys.mainKey = o.get("main").getAsInt();
                if (o.has("radial")) keys.radialKey = o.get("radial").getAsInt();
            }
            if (root.has("radial")) {
                JsonObject o = root.getAsJsonObject("radial");
                if (o.has("enabled")) radial.enabled = o.get("enabled").getAsBoolean();
                if (o.has("mode")) radial.mode = RadialMode.fromString(o.get("mode").getAsString());
                if (o.has("slots")) {
                    radial.slots.clear();
                    o.getAsJsonArray("slots").forEach(e -> radial.slots.add(RadialAction.fromId(e.getAsString())));
                }
                if (o.has("slotVisible")) {
                    radial.slotVisible.clear();
                    o.getAsJsonArray("slotVisible").forEach(e -> radial.slotVisible.add(e.getAsBoolean()));
                }
                radial.ensureDefaults();
            }
            radial.setEnabled(true);
            if (root.has("homes")) {
                JsonObject o = root.getAsJsonObject("homes");
                if (o.has("lastUsed")) homes.lastUsed = o.get("lastUsed").getAsString();
                if (o.has("favorites")) {
                    homes.favorites.clear();
                    o.getAsJsonArray("favorites").forEach(e -> homes.favorites.add(e.getAsString()));
                }
            }
            if (root.has("pvpEnabled")) pvpEnabled = root.get("pvpEnabled").getAsBoolean();
            if (root.has("chatsNotify")) chatsNotify = root.get("chatsNotify").getAsBoolean();
            if (root.has("chatsNotifySound")) {
                chatsNotifySound = ChatNotifySound.fromId(root.get("chatsNotifySound").getAsString());
            }
            if (root.has("friends")) {
                friends.clear();
                root.getAsJsonArray("friends").forEach(e -> {
                    JsonObject o = e.getAsJsonObject();
                    FriendEntry fe = new FriendEntry();
                    if (o.has("label")) fe.label = o.get("label").getAsString();
                    if (o.has("nick")) fe.nick = o.get("nick").getAsString();
                    friends.add(fe);
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public void save() {
        Path p = configPath();
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException ignored) {}

        JsonObject root = new JsonObject();
        root.addProperty("cooldownMs", cooldownMs);

        JsonObject keysObj = new JsonObject();
        keysObj.addProperty("main", keys.mainKey);
        keysObj.addProperty("radial", keys.radialKey);
        root.add("keys", keysObj);

        JsonObject radialObj = new JsonObject();
        radialObj.addProperty("enabled", radial.enabled);
        radialObj.addProperty("mode", radial.mode.id);
        JsonArray slots = new JsonArray();
        for (RadialAction a : radial.slots) slots.add(a.id);
        radialObj.add("slots", slots);
        JsonArray vis = new JsonArray();
        for (boolean v : radial.slotVisible) vis.add(v);
        radialObj.add("slotVisible", vis);
        root.add("radial", radialObj);

        JsonObject homesObj = new JsonObject();
        if (homes.lastUsed != null) homesObj.addProperty("lastUsed", homes.lastUsed);
        JsonArray fav = new JsonArray();
        for (String s : homes.favorites) fav.add(s);
        homesObj.add("favorites", fav);
        root.add("homes", homesObj);

        root.addProperty("pvpEnabled", pvpEnabled);
        root.addProperty("chatsNotify", chatsNotify);
        root.addProperty("chatsNotifySound", chatsNotifySound == null ? ChatNotifySound.CHIME.id : chatsNotifySound.id);
        JsonArray friendsArr = new JsonArray();
        for (FriendEntry fe : friends) {
            JsonObject o = new JsonObject();
            o.addProperty("label", fe.label);
            o.addProperty("nick", fe.nick);
            friendsArr.add(o);
        }
        root.add("friends", friendsArr);

        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (IOException ignored) {}
    }

    public static final class Keys {
        public int mainKey = GLFW.GLFW_KEY_R;
        public int radialKey = GLFW.GLFW_KEY_G;
    }

    public static final class Radial {
        private boolean enabled = true;
        private RadialMode mode = RadialMode.HOLD;
        public final List<RadialAction> slots = new ArrayList<>(Arrays.asList(RadialAction.defaults()));
        /** Какие из 8 слотов показывать в быстром меню. */
        public final List<Boolean> slotVisible = new ArrayList<>(Arrays.asList(
                true, true, true, false, false, true, false, true
        ));

        public void ensureDefaults() {
            while (slots.size() < 8) slots.add(RadialAction.OPEN_AH);
            while (slots.size() > 8) slots.remove(slots.size() - 1);
            while (slotVisible.size() < 8) slotVisible.add(false);
            while (slotVisible.size() > 8) slotVisible.remove(slotVisible.size() - 1);
        }

        public boolean isSlotVisible(int index) {
            ensureDefaults();
            return index >= 0 && index < slotVisible.size() && slotVisible.get(index);
        }

        public void toggleSlotVisible(int index) {
            ensureDefaults();
            if (index >= 0 && index < slotVisible.size()) {
                slotVisible.set(index, !slotVisible.get(index));
            }
        }

        public List<RadialAction> visibleActions() {
            ensureDefaults();
            List<RadialAction> out = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                if (isSlotVisible(i)) {
                    RadialAction a = slots.get(i);
                    if (a.isExecutable()) out.add(a);
                }
            }
            return out;
        }

        public void resetDefaults() {
            slots.clear();
            for (RadialAction a : RadialAction.defaults()) slots.add(a);
            slotVisible.clear();
            slotVisible.addAll(Arrays.asList(true, true, true, false, false, true, false, true));
            ensureDefaults();
        }

        public boolean enabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public RadialMode mode() { return mode; }
        public void setMode(RadialMode m) { mode = (m == null ? RadialMode.HOLD : m); }
    }

    public static final class Homes {
        public String lastUsed = "";
        public final List<String> favorites = new ArrayList<>();

        public void toggleFavorite(String name) {
            if (favorites.contains(name)) favorites.remove(name);
            else if (favorites.size() < 5) favorites.add(name);
        }

        public boolean isFavorite(String name) {
            return favorites.contains(name);
        }
    }

    public static final class FriendEntry {
        public String label = "";
        public String nick = "";
    }

    public enum RadialMode {
        HOLD("hold"),
        TOGGLE("toggle");

        public final String id;

        RadialMode(String id) {
            this.id = id;
        }

        public static RadialMode fromString(String s) {
            if (s == null) return HOLD;
            for (RadialMode m : values()) {
                if (m.id.equalsIgnoreCase(s)) return m;
            }
            return HOLD;
        }
    }
}
