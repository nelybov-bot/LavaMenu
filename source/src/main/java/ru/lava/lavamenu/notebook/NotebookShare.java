package ru.lava.lavamenu.notebook;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.ui.LavaMenuScreen;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.UiFeedback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Передача тетрадки через /msg.
 * Строки: {@code [LMNB]S|from}, {@code [LMNB]E|nick|reason}, {@code [LMNB]D|count}.
 */
public final class NotebookShare {
    public static final String MARK = "[LMNB]";
    private static final int TICKS_BETWEEN = 3;
    private static final long PENDING_TTL_MS = 180_000L;
    /** Запас под {@code msg <nick> } и лимит команды ~256. */
    private static final int MAX_PAYLOAD = 200;

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
                out.add(clip(MARK + "E|" + sanitize(e.nick) + "|" + sanitize(e.reason)));
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
        CommandHelper.sendFromUi("msg " + target + " " + payloads.get(index));
        if (index + 1 >= payloads.size()) {
            sending.set(false);
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.shown", target));
            return;
        }
        int[] left = {TICKS_BETWEEN};
        Runnable wait = new Runnable() {
            @Override
            public void run() {
                if (mc.player == null) {
                    sending.set(false);
                    return;
                }
                left[0]--;
                if (left[0] <= 0) sendNext(target, payloads, index + 1);
                else mc.execute(this);
            }
        };
        mc.execute(wait);
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
            if (pendingStartedMs > 0L) {
                boolean sameSender = pendingSender.isBlank()
                        || senderNick == null
                        || senderNick.isBlank()
                        || pendingSender.equalsIgnoreCase(senderNick.trim());
                if (!sameSender) return true; // чужой шаре не сбрасывает текущий
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
                pending.add(new NotebookEntry(nick, reason, System.currentTimeMillis(), pendingFrom));
            }
            return true;
        }
        if (body.startsWith("D|")) {
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
