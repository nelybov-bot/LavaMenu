package ru.lava.lavamenu.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.input.KeyBindings;
import ru.lava.lavamenu.ui.ChatToastScreen;
import ru.lava.lavamenu.ui.UiTheme;
import ru.lava.lavamenu.util.PlayerFaces;

/**
 * HUD-тост входящих ЛС (без затемнения / без блокировки движения).
 * Открыть: ЛКМ по тосту или клавиша {@link KeyBindings#OPEN_REPLY} (по умолчанию Y).
 */
public final class ChatToastService {
    /** Компактный тост — меньше перекрывает ванильный чат справа внизу. */
    public static final int TOAST_W = 168;
    public static final int TOAST_H = 36;
    public static final int MARGIN = 6;
    /** Отступ от низа экрана (хотбар + чат). */
    public static final int BOTTOM_PAD = 48;
    private static final long VISIBLE_MS = 12_000L;

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

        if (mc.gui.screen() instanceof ChatToastScreen toast && toast.nick().equalsIgnoreCase(fromNick)) {
            toast.updatePreview(message);
            return;
        }

        nick = fromNick.trim();
        text = message.trim();
        untilMs = System.currentTimeMillis() + VISIBLE_MS;
        LavaMenuConfig.get().chatsNotifySound.play();
    }

    public static boolean isVisible() {
        return untilMs > System.currentTimeMillis() && !nick.isBlank();
    }

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
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && isVisible() && KeyBindings.OPEN_REPLY != null) {
            while (KeyBindings.OPEN_REPLY.consumeClick()) {
                openReply();
            }
        }
    }

    public static int toastX(int screenW) {
        return screenW - TOAST_W - MARGIN;
    }

    public static int toastY(int screenH) {
        return screenH - TOAST_H - MARGIN - BOTTOM_PAD;
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
        Minecraft.getInstance().gui.setScreen(new ChatToastScreen(n, preview));
    }

    private static void handleClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            leftWasDown = mc.mouseHandler.isLeftPressed();
            return;
        }
        if (!mc.mouseHandler.isMouseGrabbed()) {
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
        } else {
            leftWasDown = false;
        }
    }

    private static void renderHud(GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ChatToastScreen) return;
        if (!isVisible()) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        drawPanel(gfx, mc.font, nick, text, toastX(sw), toastY(sh), false);
    }

    public static void drawPanel(GuiGraphicsExtractor gfx, Font font, String from, String preview,
                                 int x, int y, boolean replyMode) {
        int h = replyMode ? ChatToastScreen.PANEL_H : TOAST_H;
        gfx.fill(x, y, x + TOAST_W, y + h, UiTheme.PANEL_BG);
        gfx.fill(x, y, x + 2, y + h, UiTheme.ACCENT);

        int face = 12;
        PlayerFaces.draw(gfx, font, from, x + 4, y + 4, face);
        int textX = x + 4 + face + 3;
        int maxW = TOAST_W - (textX - x) - 4;

        String title = from == null ? "" : from;
        if (font.width(title) > maxW) {
            title = font.plainSubstrByWidth(title, Math.max(0, maxW - font.width("…"))) + "…";
        }
        gfx.text(font, Component.literal(title), textX, y + 3, UiTheme.TEXT_PRIMARY, false);

        String body = preview == null ? "" : preview;
        if (!replyMode) {
            String keyName = KeyBindings.OPEN_REPLY == null
                    ? "Y"
                    : KeyBindings.OPEN_REPLY.getTranslatedKeyMessage().getString();
            // превью + короткий хинт в одну линию по высоте
            if (font.width(body) > maxW) {
                body = font.plainSubstrByWidth(body, Math.max(0, maxW - font.width("…"))) + "…";
            }
            gfx.text(font, Component.literal(body), textX, y + 14, UiTheme.TEXT_MUTED, false);
            gfx.text(font, Component.translatable("lavamenu.chats.toast_hint", keyName),
                    textX, y + 24, UiTheme.TEXT_DIM, false);
        } else {
            if (font.width(body) > maxW) {
                body = font.plainSubstrByWidth(body, Math.max(0, maxW - font.width("…"))) + "…";
            }
            gfx.text(font, Component.literal(body), textX, y + 14, UiTheme.TEXT_MUTED, false);
        }
    }
}
