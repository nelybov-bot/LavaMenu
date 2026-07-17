package ru.lava.lavamenu.update;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.util.UiFeedback;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Проверка и установка обновлений с GitHub Releases ({@code nelybov-bot/LavaMenu}).
 * После замены JAR нужен ручной перезапуск игры.
 */
public final class ModUpdateService {
    public static final String GITHUB_OWNER = "nelybov-bot";
    public static final String GITHUB_REPO = "LavaMenu";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    private static final Pattern TAG_VERSION = Pattern.compile("(\\d+(?:\\.\\d+){0,3})");

    private static final ModUpdateService INSTANCE = new ModUpdateService();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile long lastCheckMs = 0L;
    private volatile Status status = Status.UNKNOWN;
    private volatile String remoteVersion = "";
    private volatile String downloadUrl = "";
    private volatile String statusDetail = "";
    private volatile Runnable uiListener = () -> {};

    public enum Status {
        UNKNOWN,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        INSTALLED_RESTART,
        ERROR
    }

    public static ModUpdateService get() {
        return INSTANCE;
    }

    private ModUpdateService() {}

    public void setUiListener(Runnable listener) {
        this.uiListener = listener != null ? listener : () -> {};
    }

    public void clearUiListener() {
        this.uiListener = () -> {};
    }

    private void notifyUi() {
        Minecraft mc = Minecraft.getInstance();
        Runnable r = uiListener;
        if (r != null) mc.execute(r);
    }

    public Status status() { return status; }
    public String remoteVersion() { return remoteVersion; }
    public String statusDetail() { return statusDetail; }
    public boolean busy() { return busy.get(); }
    public boolean updateAvailable() { return status == Status.UPDATE_AVAILABLE; }
    public boolean needsRestart() { return status == Status.INSTALLED_RESTART; }

    public Component statusLabel() {
        return switch (status) {
            case CHECKING -> Component.translatable("lavamenu.update.checking");
            case UP_TO_DATE -> Component.translatable("lavamenu.update.up_to_date", currentVersion());
            case UPDATE_AVAILABLE -> Component.translatable("lavamenu.update.available", remoteVersion);
            case DOWNLOADING -> Component.translatable("lavamenu.update.downloading", remoteVersion);
            case INSTALLED_RESTART -> Component.translatable("lavamenu.update.restart");
            case ERROR -> Component.translatable("lavamenu.update.error",
                    statusDetail == null || statusDetail.isBlank() ? "…" : statusDetail);
            default -> Component.translatable("lavamenu.update.idle", currentVersion());
        };
    }

    public static String currentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(LavaMenuClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /** На старте: удалить старые JAR, помеченные после обновления. */
    public void cleanupPendingDeletes() {
        Path marker = pendingDeleteFile();
        if (!Files.isRegularFile(marker)) return;
        try {
            List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                try {
                    Files.deleteIfExists(Path.of(line.trim()));
                } catch (Exception e) {
                    LavaMenuClient.LOGGER.warn("Could not delete old mod jar: {}", line);
                }
            }
            Files.deleteIfExists(marker);
        } catch (Exception e) {
            LavaMenuClient.LOGGER.warn("Pending delete cleanup failed", e);
        }
    }

    /** Тик клиента: раз в час фоновая проверка. */
    public void tickHourly() {
        if (Minecraft.getInstance().player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_INTERVAL_MS) return;
        if (status == Status.INSTALLED_RESTART) return;
        checkAsync(false);
    }

    public void checkAsync(boolean fromButton) {
        if (!busy.compareAndSet(false, true)) {
            if (fromButton) {
                UiFeedback.actionBar(Component.translatable("lavamenu.update.busy"));
            }
            return;
        }
        status = Status.CHECKING;
        statusDetail = "";
        notifyUi();
        if (fromButton) {
            UiFeedback.actionBar(Component.translatable("lavamenu.update.checking"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                ReleaseInfo info = fetchLatest();
                remoteVersion = info.version;
                downloadUrl = info.jarUrl;
                int cmp = compareVersions(info.version, currentVersion());
                if (cmp > 0 && info.jarUrl != null && !info.jarUrl.isBlank()) {
                    status = Status.UPDATE_AVAILABLE;
                    final String ver = info.version;
                    Minecraft.getInstance().execute(() -> {
                        UpdateToastService.show(ver);
                        if (fromButton) {
                            UiFeedback.actionBar(Component.translatable("lavamenu.update.available", ver));
                        }
                    });
                } else {
                    status = Status.UP_TO_DATE;
                    if (fromButton) {
                        Minecraft.getInstance().execute(() ->
                                UiFeedback.actionBar(Component.translatable("lavamenu.update.up_to_date", currentVersion())));
                    }
                }
            } catch (Exception e) {
                status = Status.ERROR;
                statusDetail = shortError(e);
                LavaMenuClient.LOGGER.warn("Update check failed: {}", statusDetail);
                if (fromButton) {
                    Minecraft.getInstance().execute(() ->
                            UiFeedback.actionBar(Component.translatable("lavamenu.update.error", statusDetail)));
                }
            } finally {
                // и при ошибке — иначе фоновый тик долбит API каждый кадр
                lastCheckMs = System.currentTimeMillis();
                busy.set(false);
                notifyUi();
            }
        });
    }

    public void installAsync() {
        if (status == Status.INSTALLED_RESTART) {
            UiFeedback.actionBar(Component.translatable("lavamenu.update.restart"));
            return;
        }
        if (status != Status.UPDATE_AVAILABLE || downloadUrl == null || downloadUrl.isBlank()) {
            checkAsync(true);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            UiFeedback.actionBar(Component.translatable("lavamenu.update.busy"));
            return;
        }
        status = Status.DOWNLOADING;
        notifyUi();
        UiFeedback.actionBar(Component.translatable("lavamenu.update.downloading", remoteVersion));

        final String version = remoteVersion;
        final String url = downloadUrl;
        CompletableFuture.runAsync(() -> {
            try {
                installJar(url, version);
                status = Status.INSTALLED_RESTART;
                Minecraft.getInstance().execute(() ->
                        UiFeedback.actionBar(Component.translatable("lavamenu.update.restart")));
            } catch (Exception e) {
                status = Status.ERROR;
                statusDetail = shortError(e);
                LavaMenuClient.LOGGER.warn("Update install failed: {}", statusDetail);
                Minecraft.getInstance().execute(() ->
                        UiFeedback.actionBar(Component.translatable("lavamenu.update.error", statusDetail)));
            } finally {
                busy.set(false);
                notifyUi();
            }
        });
    }

    private void installJar(String url, String version) throws Exception {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Files.createDirectories(modsDir);
        String safeVer = version.replaceAll("[^0-9A-Za-z._-]", "_");
        Path target = modsDir.resolve("lavamenu-" + safeVer + ".jar");
        Path part = modsDir.resolve("lavamenu-" + safeVer + ".jar.part");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "LavaMenu-Updater")
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        try (InputStream in = resp.body()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(part) < 1024) {
            Files.deleteIfExists(part);
            throw new IllegalStateException("файл слишком маленький");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);

        List<Path> toDelete = new ArrayList<>();
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.startsWith("lavamenu") && n.endsWith(".jar") && !p.equals(target);
                    })
                    .forEach(toDelete::add);
        }
        // также текущий origin, если имя другое
        ownJarPath().ifPresent(own -> {
            if (!own.equals(target) && !toDelete.contains(own)) toDelete.add(own);
        });

        List<String> pending = new ArrayList<>();
        for (Path old : toDelete) {
            try {
                Files.deleteIfExists(old);
            } catch (Exception e) {
                pending.add(old.toAbsolutePath().toString());
            }
        }
        if (!pending.isEmpty()) {
            Files.write(pendingDeleteFile(), pending, StandardCharsets.UTF_8);
        }
    }

    private static java.util.Optional<Path> ownJarPath() {
        return FabricLoader.getInstance().getModContainer(LavaMenuClient.MOD_ID)
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst());
    }

    private static Path pendingDeleteFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu-pending-delete.txt");
    }

    private ReleaseInfo fetchLatest() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_LATEST))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "LavaMenu-Updater")
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 404) {
            throw new IllegalStateException("релизов нет");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        String body = resp.body();
        String tag = jsonString(body, "tag_name");
        if (tag == null || tag.isBlank()) throw new IllegalStateException("нет tag_name");
        String version = normalizeVersion(tag);
        String jarUrl = findJarAssetUrl(body);
        if (jarUrl == null) throw new IllegalStateException("в релизе нет .jar");
        return new ReleaseInfo(version, jarUrl);
    }

    private static String findJarAssetUrl(String releaseJson) {
        // ищем browser_download_url у asset с .jar (предпочтительно lavamenu)
        Pattern asset = Pattern.compile(
                "\"name\"\\s*:\\s*\"([^\"]+\\.jar)\"[\\s\\S]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE);
        Matcher m = asset.matcher(releaseJson);
        String fallback = null;
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String url = m.group(2);
            if (name.contains("lavamenu")) return url;
            if (fallback == null) fallback = url;
        }
        // иногда порядок полей другой
        Pattern urlFirst = Pattern.compile(
                "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"",
                Pattern.CASE_INSENSITIVE);
        Matcher m2 = urlFirst.matcher(releaseJson);
        if (m2.find()) return m2.group(1);
        return fallback;
    }

    private static String jsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String normalizeVersion(String tag) {
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        Matcher m = TAG_VERSION.matcher(t);
        return m.find() ? m.group(1) : t;
    }

    /** @return >0 если a новее b */
    static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        String n = normalizeVersion(v);
        String[] parts = n.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String shortError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) m = e.getClass().getSimpleName();
        if (m.length() > 40) m = m.substring(0, 37) + "…";
        return m;
    }

    private record ReleaseInfo(String version, String jarUrl) {}
}
