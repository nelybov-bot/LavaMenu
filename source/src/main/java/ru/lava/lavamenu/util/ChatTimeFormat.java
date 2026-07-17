package ru.lava.lavamenu.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ChatTimeFormat {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private ChatTimeFormat() {}

    /** Для списка диалогов: только «16:04». */
    public static String listTime(long timeMs, String serverClock) {
        if (serverClock != null && !serverClock.isBlank()) {
            return shortenClock(serverClock);
        }
        return CLOCK.format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()));
    }

    /** Для списка: «16:04  16.07» или серверный clock + дата. */
    public static String listStamp(long timeMs, String serverClock) {
        LocalDate day = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDate();
        String date = DAY.format(day);
        return listTime(timeMs, serverClock) + "  " + date;
    }

    /** Внутри диалога: «16:04:39  16.07». */
    public static String messageStamp(long timeMs, String serverClock) {
        LocalDate day = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDate();
        String date = DAY.format(day);
        String time = (serverClock != null && !serverClock.isBlank())
                ? serverClock
                : CLOCK.format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault())) + ":00";
        return time + "  " + date;
    }

    private static String shortenClock(String clock) {
        // 16:04:39 → 16:04
        if (clock.length() >= 5 && clock.charAt(2) == ':') {
            return clock.substring(0, 5);
        }
        return clock;
    }
}
