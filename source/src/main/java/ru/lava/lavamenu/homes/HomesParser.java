package ru.lava.lavamenu.homes;

import org.slf4j.Logger;
import ru.lava.lavamenu.LavaMenuClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формат LavaWin /homes (со скрина):
 * <pre>
 * [LAVA] > Список сохраненных точек (11 / 20):
 * • Верхний мир: актив башня дом … мишин
 * мишин2 пещерка починкапредметов
 * • Край: фармилкаэндеров
 * </pre>
 */
public final class HomesParser {
    private static final Logger LOGGER = LavaMenuClient.LOGGER;
    private static final Pattern COUNT = Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");
    private static final int IDLE_TIMEOUT_TICKS = 80;
    /** LAVA / [LAVA] и разделитель > » › */
    private static final Pattern LAVA_PREFIX = Pattern.compile("(?i)^\\[?\\s*LAVA\\s*\\]?\\s*[>»›]\\s*");
    private static final Pattern LEADING_BULLET = Pattern.compile("^[•▪●◦·*\\-–—]\\s*");

    private static boolean parsing = false;
    private static String currentDimension = null;
    private static int expectedCount = -1;
    private static int idleTicks = 0;
    /** После /homes — логируем входящие строки (для отладки). */
    private static int captureTicks = 0;

    private HomesParser() {}

    public static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").replaceAll("\\u00A7.", "");
    }

    /** Нормализация пробелов/двоеточий перед разбором. */
    public static String sanitize(String s) {
        if (s == null) return "";
        String t = stripFormatting(s);
        t = t.replace('\u00A0', ' ').replace('\u202F', ' ');
        t = t.replace('：', ':');
        t = t.replace("\r", "");
        return t.trim();
    }

    public static String foldRu(String s) {
        if (s == null) return "";
        return s.replace('ё', 'е').replace('Ё', 'Е').toLowerCase();
    }

    public static boolean isParsing() {
        return parsing;
    }

    /** Включить захват чата на N тиков (после кнопки «Обновить»). */
    public static void armCapture(int ticks) {
        captureTicks = Math.max(captureTicks, ticks);
    }

    public static boolean isCapturing() {
        return captureTicks > 0 || parsing;
    }

    public static void tick() {
        if (captureTicks > 0) captureTicks--;
        if (!parsing) return;
        idleTicks++;
        if (idleTicks >= IDLE_TIMEOUT_TICKS) {
            LOGGER.warn(
                    "Homes parse session timed out (expected={}, got={}, lastDim={})",
                    expectedCount,
                    HomesData.get().allNames().size(),
                    currentDimension);
            endSession(false);
        }
    }

    /** Заголовок списка: фраза + (N / M). Префикс LAVA не обязателен. */
    public static boolean isHomesListHeader(String plain) {
        String cleaned = sanitize(plain);
        if (cleaned.isEmpty()) return false;
        String f = foldRu(cleaned);
        if (!(f.contains("сохраненных точек") || f.contains("список сохраненных"))) {
            return false;
        }
        return COUNT.matcher(cleaned).find();
    }

    public static void tryParseMessage(String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split("\\n")) {
            tryParseLine(part);
        }
    }

    public static boolean tryParseLine(String rawLine) {
        if (rawLine == null) return false;
        String plain = sanitize(rawLine);
        if (plain.isEmpty()) return false;

        if (captureTicks > 0 || parsing) {
            logCapture(plain);
        }

        String line = normalizeLine(plain);

        if (!parsing) {
            if (!isHomesListHeader(plain)) {
                return false;
            }
            beginSession(line);
            LOGGER.info("Homes parse started (expected={})", expectedCount);
            return true;
        }

        if (isHomesListHeader(plain)) {
            beginSession(line);
            return true;
        }

        int colon = line.indexOf(':');
        if (colon > 0) {
            String dim = line.substring(0, colon).trim();
            String rest = line.substring(colon + 1).trim();
            if (isDimensionName(dim)) {
                idleTicks = 0;
                if (rest.isEmpty()) {
                    currentDimension = dim;
                    HomesData.get().putDimension(dim, new ArrayList<>());
                } else {
                    HomesData.get().putDimension(dim, splitNames(rest));
                    currentDimension = dim;
                }
                updateCountIfNeeded();
                if (reachedExpectedCount()) {
                    endSession(true);
                }
                return true;
            }
        }

        if (currentDimension != null && looksLikeHomeNamesLine(line)) {
            idleTicks = 0;
            List<String> names = new ArrayList<>(
                    HomesData.get().dimensions().getOrDefault(currentDimension, List.of()));
            names.addAll(splitNames(line));
            HomesData.get().putDimension(currentDimension, names);
            updateCountIfNeeded();
            if (reachedExpectedCount()) {
                endSession(true);
            }
            return true;
        }

        LOGGER.warn("Unrecognized homes line while parsing: {}", plain);
        return false;
    }

    public static boolean hasLavaPrefix(String plain) {
        return plain != null && LAVA_PREFIX.matcher(sanitize(plain)).find();
    }

    private static void logCapture(String plain) {
        String f = foldRu(plain);
        if (f.contains("точ") || f.contains("lava") || f.contains("мир") || f.contains("край")
                || f.contains("ад") || plain.indexOf(':') > 0 || hasLavaPrefix(plain)) {
            LOGGER.info("homes-chat: {}", plain);
        }
    }

    private static void beginSession(String headerLine) {
        parsing = true;
        idleTicks = 0;
        currentDimension = null;
        expectedCount = -1;
        HomesData.get().clear();
        Matcher m = COUNT.matcher(headerLine);
        if (!m.find()) {
            m = COUNT.matcher(sanitize(headerLine));
        }
        if (m.find()) {
            expectedCount = Integer.parseInt(m.group(1));
            HomesData.get().setCount(expectedCount, Integer.parseInt(m.group(2)));
        }
        if (expectedCount == 0) {
            endSession(true);
        }
    }

    private static boolean looksLikeHomeNamesLine(String line) {
        if (line.isEmpty() || isHomesListHeader(line)) return false;
        if (line.indexOf(':') >= 0) return false;
        if (hasLavaPrefix(line)) return false;
        char c0 = line.charAt(0);
        if (c0 == '<' || c0 == '(') return false;
        if (c0 == '[' && !foldRu(line).startsWith("[lava")) return false;
        return true;
    }

    private static boolean reachedExpectedCount() {
        return expectedCount >= 0 && HomesData.get().allNames().size() >= expectedCount;
    }

    private static void endSession(boolean clean) {
        if (!parsing) return;
        parsing = false;
        int got = HomesData.get().allNames().size();
        LOGGER.info("Homes parse ended (clean={}, expected={}, got={})", clean, expectedCount, got);
        currentDimension = null;
        expectedCount = -1;
        idleTicks = 0;
        if (clean) {
            updateCountIfNeeded();
        }
        HomeRenameSession.onHomesListParsed();
    }

    private static String normalizeLine(String plain) {
        String line = sanitize(plain);
        Matcher m = LAVA_PREFIX.matcher(line);
        if (m.find()) {
            line = line.substring(m.end()).trim();
        }
        Matcher bullet = LEADING_BULLET.matcher(line);
        if (bullet.find()) {
            line = line.substring(bullet.end()).trim();
        }
        return line;
    }

    private static boolean isDimensionName(String dim) {
        if (dim.isEmpty()) return false;
        String f = foldRu(dim);
        return f.contains("мир")
                || f.equals("край")
                || f.equals("ад")
                || f.equals("overworld")
                || f.equals("nether")
                || f.equals("the end");
    }

    private static List<String> splitNames(String text) {
        List<String> out = new ArrayList<>();
        for (String p : text.split("\\s+")) {
            if (!p.isBlank()) out.add(p.trim());
        }
        return out;
    }

    private static void updateCountIfNeeded() {
        if (HomesData.get().count() == 0) {
            HomesData.get().setCount(HomesData.get().allNames().size(), HomesData.get().max());
        }
    }
}
