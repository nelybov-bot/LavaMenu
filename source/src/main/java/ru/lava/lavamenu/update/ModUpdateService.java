package ru.lava.lavamenu.update;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.util.UiFeedback;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Проверка и установка обновлений с GitHub Releases ({@code nelybov-bot/LavaMenu}).
 * HTTP через {@link HttpURLConnection} + прокси Minecraft (java.net.http часто даёт «нет соединения»).
 */
public final class ModUpdateService {
    public static final String GITHUB_OWNER = "nelybov-bot";
    public static final String GITHUB_REPO = "LavaMenu";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String WEB_LATEST =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    /** Версия из репо — когда API/редирект недоступны. */
    private static final String RAW_GRADLE =
            "https://raw.githubusercontent.com/" + GITHUB_OWNER + "/" + GITHUB_REPO
                    + "/main/source/gradle.properties";
    private static final String RAW_JAR =
            "https://raw.githubusercontent.com/" + GITHUB_OWNER + "/" + GITHUB_REPO
                    + "/main/release/lavamenu-%s.jar";
    private static final String JSDELIVR_JAR =
            "https://cdn.jsdelivr.net/gh/" + GITHUB_OWNER + "/" + GITHUB_REPO
                    + "@main/release/lavamenu-%s.jar";

    private static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    private static final int CONNECT_MS = 12_000;
    private static final int READ_MS = 45_000;
    private static final Pattern TAG_VERSION = Pattern.compile("(\\d+(?:\\.\\d+){0,3})");
    private static final Pattern TAG_IN_URL = Pattern.compile("/releases/tag/([^/?#\\s]+)");
    private static final Pattern MOD_VERSION = Pattern.compile("(?m)^\\s*mod\\.version\\s*=\\s*(\\S+)");

    private static final ModUpdateService INSTANCE = new ModUpdateService();

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

    private static String userAgent() {
        return "LavaMenu/" + currentVersion() + " (+https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + ")";
    }

    public static String currentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(LavaMenuClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

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
                LavaMenuClient.LOGGER.warn("Update check failed: {}", statusDetail, e);
                if (fromButton) {
                    Minecraft.getInstance().execute(() ->
                            UiFeedback.actionBar(Component.translatable("lavamenu.update.error", statusDetail)));
                }
            } finally {
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
                LavaMenuClient.LOGGER.warn("Update install failed: {}", statusDetail, e);
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

        Exception last = null;
        for (String tryUrl : downloadCandidates(url, version)) {
            try {
                downloadTo(tryUrl, part);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
                LavaMenuClient.LOGGER.warn("Download failed from {}: {}", tryUrl, e.toString());
                Files.deleteIfExists(part);
            }
        }
        if (last != null) throw last;

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

    private static List<String> downloadCandidates(String primary, String version) {
        List<String> out = new ArrayList<>();
        if (primary != null && !primary.isBlank()) out.add(primary);
        String raw = String.format(Locale.ROOT, RAW_JAR, version);
        String cdn = String.format(Locale.ROOT, JSDELIVR_JAR, version);
        if (!out.contains(raw)) out.add(raw);
        if (!out.contains(cdn)) out.add(cdn);
        return out;
    }

    private static void downloadTo(String url, Path part) throws Exception {
        HttpURLConnection conn = open(url, true);
        try {
            int code = conn.getResponseCode();
            if (code / 100 != 2) {
                throw new IllegalStateException("скачивание HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static Optional<Path> ownJarPath() {
        return FabricLoader.getInstance().getModContainer(LavaMenuClient.MOD_ID)
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst());
    }

    private static Path pendingDeleteFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavamenu-pending-delete.txt");
    }

    private ReleaseInfo fetchLatest() throws Exception {
        List<Exception> errors = new ArrayList<>();
        try {
            return fetchLatestApi();
        } catch (Exception e) {
            errors.add(e);
            LavaMenuClient.LOGGER.warn("Update API failed: {}", e.toString());
        }
        try {
            return fetchLatestWebRedirect();
        } catch (Exception e) {
            errors.add(e);
            LavaMenuClient.LOGGER.warn("Update web redirect failed: {}", e.toString());
        }
        try {
            return fetchLatestFromRawGradle();
        } catch (Exception e) {
            errors.add(e);
            LavaMenuClient.LOGGER.warn("Update raw gradle failed: {}", e.toString());
        }
        Exception last = errors.isEmpty()
                ? new IllegalStateException("нет источников")
                : errors.get(errors.size() - 1);
        for (int i = 0; i < errors.size() - 1; i++) {
            last.addSuppressed(errors.get(i));
        }
        throw last;
    }

    private ReleaseInfo fetchLatestApi() throws Exception {
        String body = httpGetString(API_LATEST, "application/vnd.github+json");
        String tag = jsonString(body, "tag_name");
        if (tag == null || tag.isBlank()) throw new IllegalStateException("нет tag_name");
        String version = normalizeVersion(tag);
        String jarUrl = findJarAssetUrl(body);
        if (jarUrl == null) {
            jarUrl = canonicalJarUrl(tag.startsWith("v") || tag.startsWith("V") ? tag : ("v" + version), version);
        }
        return new ReleaseInfo(version, jarUrl);
    }

    private ReleaseInfo fetchLatestWebRedirect() throws Exception {
        HttpURLConnection conn = open(WEB_LATEST, false);
        try {
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            if (location == null || location.isBlank()) {
                location = conn.getHeaderField("location");
            }
            if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                    && location != null && !location.isBlank()) {
                Matcher m = TAG_IN_URL.matcher(location);
                if (!m.find()) throw new IllegalStateException("нет тега в Location");
                String tag = m.group(1);
                String version = normalizeVersion(tag);
                String tagPath = tag.startsWith("v") || tag.startsWith("V") ? tag : ("v" + version);
                return new ReleaseInfo(version, canonicalJarUrl(tagPath, version));
            }
            throw new IllegalStateException("web HTTP " + code);
        } finally {
            conn.disconnect();
        }
    }

    private ReleaseInfo fetchLatestFromRawGradle() throws Exception {
        String props = httpGetString(RAW_GRADLE, "*/*");
        Matcher m = MOD_VERSION.matcher(props);
        if (!m.find()) throw new IllegalStateException("нет mod.version");
        String version = normalizeVersion(m.group(1));
        // предпочитаем raw jar из репо (часто доступен, когда releases режут)
        String jarUrl = String.format(Locale.ROOT, RAW_JAR, version);
        return new ReleaseInfo(version, jarUrl);
    }

    private static String canonicalJarUrl(String tagPath, String version) {
        return "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO
                + "/releases/download/" + tagPath + "/lavamenu-" + version + ".jar";
    }

    private static String httpGetString(String url, String accept) throws Exception {
        HttpURLConnection conn = open(url, true);
        try {
            conn.setRequestProperty("Accept", accept);
            int code = conn.getResponseCode();
            if (code == 404) throw new IllegalStateException("404");
            if (code == 403 || code == 429) throw new IllegalStateException("лимит GitHub " + code);
            if (code / 100 != 2) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = conn.getInputStream()) {
                return readUtf8(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String urlStr, boolean followRedirects) throws Exception {
        URL url = URI.create(urlStr).toURL();
        Proxy proxy = minecraftProxy();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setInstanceFollowRedirects(followRedirects);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent());
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (conn instanceof HttpsURLConnection https) {
            // дефолтный SSL контекст JVM Minecraft
            https.setSSLSocketFactory(HttpsURLConnection.getDefaultSSLSocketFactory());
        }
        return conn;
    }

    private static Proxy minecraftProxy() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                Proxy p = mc.getProxy();
                if (p != null && p.type() != Proxy.Type.DIRECT) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
        }
        return Proxy.NO_PROXY;
    }

    private static String readUtf8(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String findJarAssetUrl(String releaseJson) {
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
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        if (t instanceof SSLException || nameHas(t, "SSL", "PKIX", "Certificate")) {
            return "SSL/сеть (антивирус?)";
        }
        if (t instanceof UnknownHostException) {
            return "нет DNS/сети";
        }
        if (t instanceof ConnectException) {
            return "нет соединения";
        }
        if (t instanceof SocketTimeoutException || nameHas(t, "Timeout", "TimedOut")) {
            return "таймаут";
        }
        if (t instanceof java.net.SocketException) {
            return "сеть: " + safeMsg(t);
        }
        String m = safeMsg(t);
        if (m == null || m.isBlank()) m = safeMsg(e);
        if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
        if (m.toLowerCase(Locale.ROOT).startsWith("https://") || m.toLowerCase(Locale.ROOT).startsWith("http://")) {
            return "сеть/HTTPS";
        }
        if (m.length() > 42) m = m.substring(0, 39) + "…";
        return m;
    }

    private static String safeMsg(Throwable t) {
        return t.getMessage();
    }

    private static boolean nameHas(Throwable t, String... parts) {
        String n = t.getClass().getName();
        String m = t.getMessage() == null ? "" : t.getMessage();
        String hay = n + " " + m;
        for (String p : parts) {
            if (hay.contains(p)) return true;
        }
        return false;
    }

    private record ReleaseInfo(String version, String jarUrl) {}
}
