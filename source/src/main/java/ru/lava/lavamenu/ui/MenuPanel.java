package ru.lava.lavamenu.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Общая отрисовка панелей LavaMenu. */
public final class MenuPanel {
    private MenuPanel() {}

    public static void layout(int screenW, int screenH, int targetH, int[] box) {
        layout(screenW, screenH, targetH, box, UiTheme.PANEL_WIDTH);
    }

    public static void layout(int screenW, int screenH, int targetH, int[] box, int preferredW) {
        int w = Math.min(preferredW, Math.max(300, screenW - 40));
        int h = Math.min(targetH, Math.max(240, screenH - 40));
        box[0] = (screenW - w) / 2;
        box[1] = (screenH - h) / 2;
        box[2] = w;
        box[3] = h;
    }

    public static void drawBackdrop(GuiGraphicsExtractor gfx, int screenW, int screenH) {
        gfx.fill(0, 0, screenW, screenH, UiTheme.BACKDROP);
    }

    public static void drawFrame(GuiGraphicsExtractor gfx, Font font, Component title,
                                 int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, UiTheme.PANEL_BG);
        gfx.fill(x, y, x + w, y + 2, UiTheme.ACCENT);
        gfx.fill(x, y + 2, x + w, y + 2 + UiTheme.HEADER_H, UiTheme.HEADER_BG);
        gfx.text(font, title, x + UiTheme.PAD, y + 6, UiTheme.TEXT_PRIMARY, false);
    }

    public static void drawSection(GuiGraphicsExtractor gfx, Font font, Component label, int x, int y) {
        gfx.text(font, label, x, y, UiTheme.TEXT_MUTED, false);
    }

    public static void drawDivider(GuiGraphicsExtractor gfx, int x, int y, int w) {
        gfx.fill(x, y, x + w, y + 1, UiTheme.DIVIDER);
    }

    /** Непрозрачная полоса — скрывает «пролезание» списка под форму. */
    public static void drawScrollCap(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
        if (h > 0) gfx.fill(x, y, x + w, y + h, UiTheme.PANEL_BG);
    }

    public static void drawRowHover(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, UiTheme.ROW_HOVER);
    }

    public static void drawStatusDot(GuiGraphicsExtractor gfx, int x, int y, int rowH, boolean online) {
        int size = 6;
        int dy = y + (rowH - size) / 2;
        gfx.fill(x, dy, x + size, dy + size, online ? UiTheme.ONLINE : UiTheme.OFFLINE);
    }

    /** Точка статуса на линии текста (не по центру высокой строки). */
    public static void drawStatusDotAt(GuiGraphicsExtractor gfx, int x, int textY, boolean online) {
        int size = 6;
        int dy = textY + 1;
        gfx.fill(x, dy, x + size, dy + size, online ? UiTheme.ONLINE : UiTheme.OFFLINE);
    }

    public static void drawAvatar(GuiGraphicsExtractor gfx, Font font, String initials, int x, int y, int size) {
        gfx.fill(x, y, x + size, y + size, UiTheme.TAB_ACTIVE_BG);
        gfx.centeredText(font, Component.literal(initials), x + size / 2, y + (size - 8) / 2, UiTheme.ACCENT);
    }

    public static void drawDialog(GuiGraphicsExtractor gfx, Font font, Component title, Component message,
                                  int screenW, int screenH, int boxW, int boxH) {
        gfx.fill(0, 0, screenW, screenH, 0xCC000000);
        int bx = (screenW - boxW) / 2;
        int by = (screenH - boxH) / 2;
        gfx.fill(bx, by, bx + boxW, by + boxH, UiTheme.PANEL_BG);
        gfx.fill(bx, by, bx + boxW, by + 2, UiTheme.ACCENT);
        gfx.text(font, title, bx + 12, by + 10, UiTheme.TEXT_PRIMARY, false);
        if (message != null) {
            int my = by + 28;
            for (String part : message.getString().split("\\n", -1)) {
                gfx.text(font, Component.literal(part), bx + 12, my, UiTheme.TEXT_PRIMARY, false);
                my += 10;
            }
        }
    }

    /** Виджет целиком внутри прокручиваемой зоны. */
    public static boolean rowInside(int y, int h, int top, int bottom) {
        return y >= top && y + h <= bottom;
    }

    public static boolean rowVisible(int y, int h, int top, int bottom) {
        return y + h > top && y < bottom;
    }

    public static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void withScissor(GuiGraphicsExtractor gfx, int x, int y, int w, int h, Runnable draw) {
        if (w <= 0 || h <= 0) return;
        // GuiGraphicsExtractor.enableScissor — это (x1, y1, x2, y2), не (x, y, w, h).
        gfx.enableScissor(x, y, x + w, y + h);
        try {
            draw.run();
        } finally {
            gfx.disableScissor();
        }
    }
}
