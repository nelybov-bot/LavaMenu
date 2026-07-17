package ru.lava.lavamenu.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import ru.lava.lavamenu.LavaMenuClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Кэш голов: онлайн → из PlayerInfo (как Tab/P), на диск PNG;
 * офлайн → последняя сохранённая; иначе — нет текстуры (инициалы снаружи).
 */
public final class FaceCache {
    private static final Logger LOGGER = LavaMenuClient.LOGGER;
    private static final int MAX_FACES = 120;
    private static final FaceCache INSTANCE = new FaceCache();

    /** nickLower → зарегистрированная динамическая текстура (голова). */
    private final Map<String, Identifier> loaded = new HashMap<>();
    /** nickLower → путь скина из последней сессии (пока текстура ещё в TextureManager). */
    private final Map<String, Identifier> sessionBody = new HashMap<>();
    private boolean dirLoaded;
    private int tick;

    public static FaceCache get() {
        return INSTANCE;
    }

    private FaceCache() {}

    private Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu/faces");
    }

    public void ensureLoaded() {
        if (dirLoaded) return;
        dirLoaded = true;
        Path d = dir();
        if (!Files.isDirectory(d)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d, "*.png")) {
            for (Path p : stream) {
                String file = p.getFileName().toString();
                String key = file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT);
                registerFile(key, p);
            }
        } catch (IOException e) {
            LOGGER.warn("FaceCache load failed: {}", e.toString());
        }
    }

    /** Периодически снимаем головы онлайн-игроков в кэш. */
    public void tick() {
        ensureLoaded();
        if (++tick < 40) return;
        tick = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        int n = 0;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info == null) continue;
            if (capture(info) && ++n >= 8) break; // не более 8 за раз
        }
        trimIfNeeded();
    }

    /** Запомнить голову, если ещё не в файле / обновить из живого скина. */
    public boolean capture(PlayerInfo info) {
        if (info == null || info.getProfile() == null) return false;
        String name = info.getProfile().name();
        if (name == null || name.isBlank()) return false;
        String key = name.toLowerCase(Locale.ROOT);

        PlayerSkin skin = info.getSkin();
        if (skin == null || skin.body() == null) return false;
        Identifier body = skin.body().texturePath();
        sessionBody.put(key, body);

        if (loaded.containsKey(key)) return false;

        Minecraft mc = Minecraft.getInstance();
        AbstractTexture tex = mc.getTextureManager().getTexture(body);
        if (!(tex instanceof DynamicTexture dyn)) return false;
        NativeImage src = dyn.getPixels();
        if (src == null || src.getWidth() < 64 || src.getHeight() < 32) return false;

        try {
            NativeImage face = extractHead(src);
            Path d = dir();
            Files.createDirectories(d);
            Path out = d.resolve(key + ".png");
            face.writeToFile(out);
            face.close();
            registerFile(key, out);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("FaceCache capture {}: {}", key, t.toString());
            return false;
        }
    }

    public void rememberIfOnline(String nick) {
        PlayerInfo info = OnlinePlayers.find(nick);
        if (info != null) capture(info);
    }

    /** Текстура для отрисовки головы (файл или тело скина в сессии). */
    public Identifier textureFor(String nick) {
        if (nick == null || nick.isBlank()) return null;
        ensureLoaded();
        String key = nick.toLowerCase(Locale.ROOT);
        Identifier face = loaded.get(key);
        if (face != null) return face;
        return sessionBody.get(key);
    }

    public boolean isFaceTexture(Identifier id) {
        return id != null && "lavamenu".equals(id.getNamespace()) && id.getPath().startsWith("faces/");
    }

    private void registerFile(String key, Path png) {
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            registerImage(key, img);
        } catch (IOException ignored) {
        }
    }

    private void registerImage(String key, NativeImage img) {
        Minecraft mc = Minecraft.getInstance();
        Identifier id = Identifier.fromNamespaceAndPath(LavaMenuClient.MOD_ID, "faces/" + key);
        DynamicTexture tex = new DynamicTexture(() -> "lavamenu face " + key, img);
        mc.getTextureManager().register(id, tex);
        loaded.put(key, id);
    }

    /** Передняя голова + слой шляпы. */
    static NativeImage extractHead(NativeImage skin) {
        int tw = skin.getWidth();
        int unit = Math.max(1, tw / 8);
        NativeImage raw = new NativeImage(unit, unit, false);
        // head base (8,8) in 64-space → copy FROM skin INTO raw
        raw.copyRect(skin, unit, unit, 0, 0, unit, unit, false, false);
        NativeImage hat = new NativeImage(unit, unit, false);
        hat.copyRect(skin, unit * 5, unit, 0, 0, unit, unit, false, false);
        for (int y = 0; y < unit; y++) {
            for (int x = 0; x < unit; x++) {
                int hp = hat.getPixel(x, y);
                int a = (hp >>> 24) & 0xFF;
                if (a > 16) raw.setPixel(x, y, hp);
            }
        }
        hat.close();
        if (unit == 8) return raw;
        NativeImage out = new NativeImage(8, 8, false);
        raw.resizeSubRectTo(0, 0, unit, unit, out);
        raw.close();
        return out;
    }

    private void trimIfNeeded() {
        if (loaded.size() <= MAX_FACES) return;
        // удаляем самые старые файлы по mtime
        Path d = dir();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d, "*.png")) {
            Path oldest = null;
            long oldestT = Long.MAX_VALUE;
            int count = 0;
            for (Path p : stream) {
                count++;
                long t = Files.getLastModifiedTime(p).toMillis();
                if (t < oldestT) {
                    oldestT = t;
                    oldest = p;
                }
            }
            if (count > MAX_FACES && oldest != null) {
                String file = oldest.getFileName().toString();
                String key = file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT);
                Files.deleteIfExists(oldest);
                loaded.remove(key);
            }
        } catch (IOException ignored) {
        }
    }
}
