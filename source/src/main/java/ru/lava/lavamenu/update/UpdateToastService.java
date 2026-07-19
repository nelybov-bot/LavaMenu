package ru.lava.lavamenu.update;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.chat.ChatToastService;
import ru.lava.lavamenu.input.KeyBindings;
import ru.lava.lavamenu.ui.LavaMenuScreen;
import ru.lava.lavamenu.ui.RadialCenterIcon;
import ru.lava.lavamenu.ui.UiTheme;

/**
 * HUD-тост «доступно обновление» (картинка из центра колеса G).
 * Показывается при обнаружении новой версии и повторно раз в час, пока не обновят.
 */
public final class UpdateToastService {
    public static final int TOAST_W = 168;
    public static final int TOAST_H = 44;
    private static final long VISIBLE_MS = 20_000L;

    private static long untilMs = 0L;
    private static String version = "";
    private static boolean leftWasDown = false;

    private UpdateToastService() {}

    public static void registerHud() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(LavaMenuClient.MOD_ID, "update_toast"),
                (gfx, delta) -> renderHud(gfx));
    }

    /** Вызвать, когда найдена новая версия (в т.ч. при часовой проверке). */
    public static void show(String remoteVersion) {
        version = remoteVersion == null ? "" : remoteVersion.trim();
        untilMs = System.currentTimeMillis() + VISIBLE_MS;
    }

    public static boolean isVisible() {
        return untilMs > System.currentTimeMillis();
    }

    public static void dismiss() {
        untilMs = 0L;
    }

    public static void tick() {
        if (untilMs > 0L && System.currentTimeMillis() >= untilMs) {
            dismiss();
        }
        handleClick();
        // Приоритет у тоста ЛС: если оба видны — Y открывает ответ, не настройки
        if (ChatToastService.isVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && isVisible() && KeyBindings.OPEN_REPLY != null) {
            while (KeyBindings.OPEN_REPLY.consumeClick()) {
                openSettings();
            }
        }
    }

    public static int toastX(int screenW) {
        return screenW - TOAST_W - ChatToastService.MARGIN;
    }

    public static int toastY(int screenH) {
        int y = screenH - TOAST_H - ChatToastService.MARGIN - ChatToastService.BOTTOM_PAD;
        if (ChatToastService.isVisible()) {
            y -= ChatToastService.TOAST_H + 4;
        }
        return y;
    }

    public static void openSettings() {
        dismiss();
        Minecraft.getInstance().gui.setScreen(new LavaMenuScreen(LavaMenuScreen.Tab.SETTINGS));
    }

    private static void handleClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            leftWasDown = mc.mouseHandler.isLeftPressed();
            return;
        }
        if (mc.mouseHandler.isMouseGrabbed()) {
            leftWasDown = false;
            return;
        }
        boolean down = mc.mouseHandler.isLeftPressed();
        boolean edge = down && !leftWasDown;
        leftWasDown = down;
        if (!edge || !isVisible()) return;

        var win = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(win);
        double my = mc.mouseHandler.getScaledYPos(win);
        int x = toastX(win.getGuiScaledWidth());
        int y = toastY(win.getGuiScaledHeight());
        if (mx >= x && mx < x + TOAST_W && my >= y && my < y + TOAST_H) {
            openSettings();
        }
    }

    private static void renderHud(GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        if (!isVisible()) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        drawPanel(gfx, mc.font, toastX(sw), toastY(sh));
    }

    private static void drawPanel(GuiGraphicsExtractor gfx, Font font, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        gfx.fill(x, y, x + TOAST_W, y + TOAST_H, UiTheme.PANEL_BG);
        gfx.fill(x, y, x + 2, y + TOAST_H, UiTheme.WORLD_GREEN);

        int img = 28;
        RadialCenterIcon.drawAt(gfx, mc, x + 5, y + (TOAST_H - img) / 2, img);

        int tx = x + 5 + img + 5;
        gfx.text(font, Component.translatable("lavamenu.update.toast_title"),
                tx, y + 5, UiTheme.TEXT_PRIMARY, false);
        gfx.text(font, Component.translatable("lavamenu.update.toast_body"),
                tx, y + 16, UiTheme.WORLD_GREEN, false);

        String keyName = KeyBindings.OPEN_REPLY == null
                ? "Y"
                : KeyBindings.OPEN_REPLY.getTranslatedKeyMessage().getString();
        String hint = Component.translatable("lavamenu.update.toast_hint", keyName).getString();
        if (!version.isBlank()) {
            hint = Component.translatable("lavamenu.update.toast_hint_ver", keyName, version).getString();
        }
        gfx.text(font, Component.literal(hint), tx, y + 28, UiTheme.TEXT_DIM, false);
    }
}
