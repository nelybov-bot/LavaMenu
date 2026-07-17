package ru.lava.lavamenu.notebook;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.ui.LavaMenuScreen;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.UiFeedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Передача тетрадки через /msg.
 * Строки: {@code [LMNB]S|from}, {@code [LMNB]E|nick|reason}, {@code [LMNB]D|count}.
 * <p>
 * Важно: одно PM может прийти и как CHAT, и как GAME — повторный {@code S|} не должен
 * сбрасывать уже принятые {@code E|}.
 */
public final class NotebookShare {
    public static final String MARK = "[LMNB]";
    /** Пауза между /msg — сервер часто режет быстрый спам ЛС. */
    private static final int TICKS_BETWEEN = 8;
    private static final long PENDING_TTL_MS = 180_000L;
    /** Запас под {@code msg <nick> } и лимит команды ~256. */
    private static final int MAX_PAYLOAD = 180;

    private static final List<NotebookEntry> pending = new ArrayList<>();
    private static String pendingFrom = "";
    private static String pendingSender = "";
    private static long pendingStartedMs = 0L;
    private static final AtomicBoolean sending = new AtomicBoolean(false);

    private NotebookShare() {}

    public static boolean isSharePayload(String text) {
        return text != null && text.trim().startsWith(MARK);
    }

    public static boolean isSending() {
        return sending.get();
    }

    public static List<String> encode(List<NotebookEntry> entries, String fromNick) {
        List<String> out = new ArrayList<>();
        String from = fromNick == null || fromNick.isBlank() ? "?" : fromNick.trim();
        out.add(clip(MARK + "S|" + sanitize(from)));
        int n = 0;
        if (entries != null) {
            for (NotebookEntry e : entries) {
                if (e == null || e.nick == null || e.nick.isBlank()) continue;
                String nick = sanitize(e.nick);
                String reason = sanitize(e.reason);
                // ужимаем reason, если вместе с ником не влезает
                String line = MARK + "E|" + nick + "|" + reason;
                if (line.length() > MAX_PAYLOAD) {
                    int keep = Math.max(0, MAX_PAYLOAD - (MARK + "E|" + nick + "|").length());
                    reason = reason.length() <= keep ? reason : reason.substring(0, keep);
                    line = MARK + "E|" + nick + "|" + reason;
                }
                out.add(clip(line));
                n++;
            }
        }
        out.add(clip(MARK + "D|" + n));
        return out;
    }

    /**
     * Последовательная отправка.
     * @return false — не стартовала (занято / пусто)
     */
    public static boolean sendTo(String targetNick, List<String> payloads) {
        if (targetNick == null || targetNick.isBlank() || payloads == null || payloads.isEmpty()) {
            return false;
        }
        if (!sending.compareAndSet(false, true)) {
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.share_busy"));
            return false;
        }
        sendNext(targetNick.trim(), List.copyOf(payloads), 0);
        return true;
    }

    private static void sendNext(String target, List<String> payloads, int index) {
        if (index >= payloads.size()) {
            sending.set(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            sending.set(false);
            return;
        }
        String payload = payloads.get(index);
        // chat-команда: надёжнее для длинного текста на части серверов
        boolean ok = CommandHelper.sendChatCommand("msg " + target + " " + payload);
        if (!ok) {
            sending.set(false);
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.share_fail"));
            return;
        }
        if (index + 1 >= payloads.size()) {
            sending.set(false);
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.shown", target));
            return;
        }
        scheduleNext(target, payloads, index + 1, TICKS_BETWEEN);
    }

    private static void scheduleNext(String target, List<String> payloads, int nextIndex, int ticksLeft) {
        Minecraft mc = Minecraft.getInstance();
        if (ticksLeft <= 0) {
            sendNext(target, payloads, nextIndex);
            return;
        }
        mc.execute(() -> {
            if (mc.player == null) {
                sending.set(false);
                return;
            }
            scheduleNext(target, payloads, nextIndex, ticksLeft - 1);
        });
    }

    /** Тик клиента: применить частичный снимок, если D| так и не пришёл. */
    public static synchronized void tick() {
        expirePendingIfNeeded(true);
    }

    /**
     * @return true — пакет тетрадки (в Чаты не класть)
     */
    public static synchronized boolean tryConsume(String text, String senderNick) {
        if (!isSharePayload(text)) return false;
        if (NotebookAccess.canEdit()) return true;

        expirePendingIfNeeded(true);

        String body = text.trim().substring(MARK.length()).trim();
        if (body.startsWith("S|")) {
            boolean sameSender = pendingStartedMs > 0L && (
                    pendingSender.isBlank()
                            || senderNick == null
                            || senderNick.isBlank()
                            || pendingSender.equalsIgnoreCase(senderNick.trim()));
            // Повтор того же S| (CHAT+GAME) — не чистим уже принятые E|
            if (sameSender && pendingStartedMs > 0L) {
                return true;
            }
            // Чужой параллельный шаре — игнор
            if (pendingStartedMs > 0L && !sameSender) {
                return true;
            }
            pending.clear();
            pendingFrom = body.length() > 2 ? body.substring(2).trim() : (senderNick == null ? "" : senderNick);
            pendingSender = senderNick == null ? "" : senderNick.trim();
            pendingStartedMs = System.currentTimeMillis();
            return true;
        }
        if (!acceptFromSender(senderNick)) return true;
        if (body.startsWith("E|")) {
            if (pendingStartedMs == 0L) {
                pendingStartedMs = System.currentTimeMillis();
                pendingFrom = senderNick == null ? "" : senderNick;
                pendingSender = senderNick == null ? "" : senderNick.trim();
            }
            String rest = body.substring(2);
            int sep = rest.indexOf('|');
            String nick = sep < 0 ? rest.trim() : rest.substring(0, sep).trim();
            String reason = sep < 0 ? "" : rest.substring(sep + 1).trim();
            if (!nick.isBlank()) {
                upsertPending(nick, reason);
            }
            return true;
        }
        if (body.startsWith("D|")) {
            // Повторный D| после commit (CHAT+GAME) — не затирать снимок пустым
            if (pendingStartedMs == 0L && pending.isEmpty()) {
                return true;
            }
            String from = pendingFrom.isBlank() && senderNick != null ? senderNick : pendingFrom;
            int expected = -1;
            try {
                expected = Integer.parseInt(body.substring(2).trim());
            } catch (NumberFormatException ignored) {}
            List<NotebookEntry> copy = new ArrayList<>(pending);
            clearPending();
            commitShare(copy, from, expected);
            return true;
        }
        return true;
    }

    private static void upsertPending(String nick, String reason) {
        String key = nick.toLowerCase(Locale.ROOT);
        for (NotebookEntry e : pending) {
            if (e.nick.toLowerCase(Locale.ROOT).equals(key)) {
                e.reason = reason;
                return;
            }
        }
        pending.add(new NotebookEntry(nick, reason, System.currentTimeMillis(), pendingFrom));
    }

    private static boolean acceptFromSender(String senderNick) {
        if (pendingStartedMs == 0L) return true;
        if (pendingSender.isBlank() || senderNick == null || senderNick.isBlank()) return true;
        return pendingSender.equalsIgnoreCase(senderNick.trim());
    }

    /** Если D| потерян — сохранить то, что успело прийти. */
    private static void expirePendingIfNeeded(boolean applyPartial) {
        if (pendingStartedMs == 0L) return;
        long now = System.currentTimeMillis();
        if (now - pendingStartedMs <= PENDING_TTL_MS) return;
        if (applyPartial && !pending.isEmpty()) {
            String from = pendingFrom;
            List<NotebookEntry> copy = new ArrayList<>(pending);
            clearPending();
            commitShare(copy, from, -1);
        } else {
            clearPending();
        }
    }

    private static void clearPending() {
        pending.clear();
        pendingFrom = "";
        pendingSender = "";
        pendingStartedMs = 0L;
    }

    private static void commitShare(List<NotebookEntry> copy, String from, int expected) {
        AstoriaNotebookStore.get().replaceFromShare(copy, from);
        Minecraft mc = Minecraft.getInstance();
        final int got = copy.size();
        final int exp = expected;
        mc.execute(() -> {
            if (exp >= 0 && got != exp) {
                UiFeedback.actionBar(Component.translatable("lavamenu.notebook.received_partial", got, exp));
            } else if (exp < 0) {
                UiFeedback.actionBar(Component.translatable("lavamenu.notebook.received_partial", got, "?"));
            } else {
                UiFeedback.actionBar(Component.translatable("lavamenu.notebook.received"));
            }
            if (mc.screen instanceof LavaMenuScreen screen) {
                screen.onNotebookChanged();
            }
        });
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String clip(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_PAYLOAD) return s;
        return s.substring(0, MAX_PAYLOAD);
    }
}
