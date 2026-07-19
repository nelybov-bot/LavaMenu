package ru.lava.lavamenu.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** SHA-256 для проверки скачанных JAR. */
public final class Sha256 {
    private Sha256() {}

    public static String ofFile(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Нормализация: убрать префикс {@code sha256:}, пробелы, lower-case. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("sha256:")) s = s.substring(7).trim();
        // файл .sha256 часто: "<hex>  filename"
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp).trim();
        int tab = s.indexOf('\t');
        if (tab > 0) s = s.substring(0, tab).trim();
        return s.replaceAll("[^0-9a-f]", "");
    }

    public static boolean matches(String expected, String actual) {
        String a = normalize(expected);
        String b = normalize(actual);
        return a.length() == 64 && a.equals(b);
    }
}
