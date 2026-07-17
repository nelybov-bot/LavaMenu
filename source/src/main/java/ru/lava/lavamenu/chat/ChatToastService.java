package ru.lava.lavamenu.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.ui.ChatToastScreen;
import ru.lava.lavamenu.ui.UiTheme;
import ru.lava.lavamenu.util.PlayerFaces;

/**
 * HUD-тост входящих ЛС (без затемнения / без блокировки движения).
 * Клик → {@link ChatToastScreen} с полем ответа.
 */
public final class ChatToastService {
    public static final int TOAST_W = 220;
    public static final int TOAST_H = 52;
    public static final int MARGIN = 8;
    private static final long VISIBLE_MS = 10_000L;

    private static String nick = "";
    private static String text = "";
    private static long untilMs = 0L;
    private static boolean leftWasDown = false;

    private ChatToastService() {}

    public static void registerHud() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(LavaMenuClient.MOD_ID, "chat_toast"),
                (gfx, delta) -> renderHud(gfx));
    }

    public static void onIncoming(String fromNick, String message) {
        if (!LavaMenuConfig.get().chatsNotify) return;
        if (fromNick == null || fromNick.isBlank() || message == null || message.isBlank()) return;

        Minecraft mc = Minecraft.getInstance();
        String viewing = ChatStore.get().viewingNick();
        if (viewing != null && viewing.equalsIgnoreCase(fromNick)) return;

        if (mc.screen instanceof ChatToastScreen toast && toast.nick().equalsIgnoreCase(fromNick)) {
            toast.updatePreview(message);
            return;
        }

        String key = fromNick.trim();
        nick = key;
        text = message.trim();
        untilMs = System.currentTimeMillis() + VISIBLE_MS;
        LavaMenuConfig.get().chatsNotifySound.play();
    }

    public static boolean isVisible() {
        return untilMs > System.currentTimeMillis() && !nick.isBlank();
    }

    public static String nick() { return nick; }
    public static String text() { return text; }

    public static void dismiss() {
        untilMs = 0L;
        nick = "";
        text = "";
    }

    public static void tick() {
        if (untilMs > 0L && System.currentTimeMillis() >= untilMs) {
            dismiss();
        }
        handleClick();
    }

    public static int toastX(int screenW) {
        return screenW - TOAST_W - MARGIN;
    }

    public static int toastY(int screenH) {
        return screenH - TOAST_H - MARGIN - 22; // над хотбаром
    }

    public static boolean hit(double mouseX, double mouseY, int screenW, int screenH) {
        int x = toastX(screenW);
        int y = toastY(screenH);
        return mouseX >= x && mouseX < x + TOAST_W && mouseY >= y && mouseY < y + TOAST_H;
    }

    public static void openReply() {
        if (!isVisible() && nick.isBlank()) return;
        String n = nick;
        String preview = text;
        dismiss();
        Minecraft.getInstance().setScreen(new ChatToastScreen(n, preview));
    }

    private static void handleClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            leftWasDown = mc.mouseHandler.isLeftPressed();
            return;
        }
        boolean down = mc.mouseHandler.isLeftPressed();
        boolean edge = down && !leftWasDown;
        leftWasDown = down;
        if (!edge || !isVisible()) return;

        var win = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(win);
        double my = mc.mouseHandler.getScaledYPos(win);
        if (hit(mx, my, win.getGuiScaledWidth(), win.getGuiScaledHeight())) {
            openReply();
        }
    }

    private static void renderHud(GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChatToastScreen) return;
        if (!isVisible()) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        drawPanel(gfx, mc.font, nick, text, toastX(sw), toastY(sh), false);
    }

    /** Общая отрисовка панели (HUD и экран ответа). */
    public static void drawPanel(GuiGraphicsExtractor gfx, Font font, String from, String preview,
                                 int x, int y, boolean replyMode) {
        int h = replyMode ? ChatToastScreen.PANEL_H : TOAST_H;
        gfx.fill(x, y, x + TOAST_W, y + h, UiTheme.PANEL_BG);
        gfx.fill(x, y, x + 2, y + h, UiTheme.ACCENT);

        int face = 20;
        PlayerFaces.draw(gfx, font, from, x + 6, y + 6, face);
        String title = from == null ? "" : from;
        gfx.text(font, net.minecraft.network.chat.Component.literal(title),
                x + 30, y + 6, UiTheme.TEXT_PRIMARY, false);

        String body = preview == null ? "" : preview;
        int maxW = TOAST_W - 36;
        if (font.width(body) > maxW) {
            body = font.plainSubstrByWidth(body, maxW - font.width("…")) + "…";
        }
        gfx.text(font, net.minecraft.network.chat.Component.literal(body),
                x + 30, y + 18, UiTheme.TEXT_MUTED, false);

        if (!replyMode) {
            gfx.text(font, net.minecraft.network.chat.Component.translatable("lavamenu.chats.toast_hint"),
                    x + 30, y + 34, UiTheme.TEXT_DIM, false);
        }
    }
}
