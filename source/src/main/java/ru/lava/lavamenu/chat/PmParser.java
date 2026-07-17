package ru.lava.lavamenu.chat;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.homes.HomesParser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формат LavaWin:
 * {@code [PM] [16:04:39] [вы >> jolan]: текст}
 * {@code [PM] [16:04:46] [jolan >> вам]: текст}
 */
public final class PmParser {
    private static final Logger LOGGER = LavaMenuClient.LOGGER;

    /** стрелка: >>, », ›, ->, → и варианты с пробелами */
    private static final Pattern ARROW_SPLIT = Pattern.compile(
            "\\s*(?:>>|»|››|›|>{1,2}|->|→|⇒)\\s*");

    private static final Pattern TIME_BRACKET = Pattern.compile(
            "\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)]");

    private PmParser() {}

    public static boolean tryParseLine(String raw) {
        String plain = normalizePm(HomesParser.sanitize(raw));
        if (plain.isEmpty()) return false;
        int pmAt = indexOfPm(plain);
        if (pmAt < 0) return false;
        if (pmAt > 0) plain = plain.substring(pmAt).trim();

        if (parseStructural(plain)) return true;

        LOGGER.warn("PM line not matched: {}", plain);
        return false;
    }

    /**
     * Разбор без одного большого regex: [PM] → [время] → [left arrow right]: текст
     */
    private static boolean parseStructural(String plain) {
        Matcher timeM = TIME_BRACKET.matcher(plain);
        if (!timeM.find()) return false;
        String clock = timeM.group(1);
        String afterTime = plain.substring(timeM.end()).trim();

        String left;
        String right;
        String text;

        if (afterTime.startsWith("[")) {
            int close = afterTime.indexOf(']');
            if (close < 0) return false;
            String pair = afterTime.substring(1, close).trim();
            text = afterTime.substring(close + 1).trim();
            if (text.startsWith(":")) text = text.substring(1).trim();
            String[] parts = ARROW_SPLIT.split(pair, 2);
            if (parts.length < 2) return false;
            left = parts[0].trim();
            right = parts[1].trim();
        } else {
            // запасной формат без скобок вокруг пары: nick >> вам: текст
            Matcher loose = Pattern.compile(
                    "(?i)^(.+?)\\s*(?:>>|»|››|›|>{1,2}|->|→|⇒)\\s*(\\S+)\\s*:\\s*(.*)$"
            ).matcher(afterTime);
            if (!loose.find()) return false;
            left = loose.group(1).trim();
            right = loose.group(2).trim();
            text = loose.group(3).trim();
        }

        if (left.isEmpty() || right.isEmpty() || text.isEmpty()) return false;

        if (isYou(left)) {
            ChatStore.get().addMessage(right, true, text, clock, true);
            return true;
        }
        if (isIncomingTarget(right) && !isYou(left)) {
            ChatStore.get().addMessage(left, false, text, clock, true);
            return true;
        }
        return false;
    }

    private static boolean isYou(String s) {
        if (s == null) return false;
        String t = s.toLowerCase(Locale.ROOT).trim();
        return t.equals("вы") || t.equals("you") || t.equals("me");
    }

    private static boolean isIncomingTarget(String target) {
        if (target == null || target.isBlank()) return false;
        String t = target.toLowerCase(Locale.ROOT).trim();
        if (t.equals("вам") || t.equals("вы") || t.equals("тебе") || t.equals("you")) return true;
        String self = localNick();
        return self != null && !self.isBlank() && self.equalsIgnoreCase(target.trim());
    }

    private static String localNick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getGameProfile().name();
    }

    private static int indexOfPm(String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        int a = lower.indexOf("[pm]");
        if (a >= 0) return a;
        // редкий вариант без скобок
        return lower.indexOf("pm]");
    }

    private static String normalizePm(String s) {
        if (s == null) return "";
        return s
                .replace('［', '[')
                .replace('］', ']')
                .replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\uFEFF', ' ')
                .replace("≫", ">>")
                .replace("››", ">>")
                .trim();
    }

    public static void tryParseMessage(String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split("\\n")) {
            tryParseLine(part);
        }
    }
}
