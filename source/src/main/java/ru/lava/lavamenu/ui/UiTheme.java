package ru.lava.lavamenu.ui;

/** Компактная дизайн-система LavaMenu — плотный HUD-стиль. */
public final class UiTheme {
    /** Основной фон панели (чуть теплее графита). */
    public static final int PANEL_BG = 0xF01A1C1F;
    public static final int HEADER_BG = 0xFF23262B;
    /** Акцент — мягкий лёд/сталь (тот же холодный тон, чище). */
    public static final int ACCENT = 0xFF6BA8D8;
    public static final int ACCENT_SOFT = 0x336BA8D8;
    public static final int ROW_HOVER = 0xFF262A30;
    public static final int BTN_SECONDARY_BG = 0xFF262A30;
    public static final int BTN_SECONDARY_BORDER = 0xFF3E4450;
    public static final int TEXT_PRIMARY = 0xFFF0F2F5;
    public static final int TEXT_DARK = 0xFF0E1A26;
    public static final int TEXT_MUTED = 0xFFA0A6B0;
    public static final int TEXT_DIM = 0xFF7A8190;
    public static final int DANGER_BG = 0xFFC86A6A;
    public static final int DANGER_TEXT = 0xFF2B0E0E;
    public static final int DIVIDER = 0xFF343A44;
    public static final int TAB_ACTIVE_BG = 0xFF2A3644;
    public static final int FRAME_BORDER = 0xFF2E343E;
    public static final int WORLD_GREEN = 0xFF8FBF7A;
    public static final int NETHER_ORANGE = 0xFFE08A5A;
    public static final int END_PURPLE = 0xFFC9A0FF;
    public static final int ONLINE = 0xFF6DBF5E;
    public static final int OFFLINE = 0xFF666666;
    public static final int BACKDROP = 0xA0080A0C;

    public static final int PAD = 7;
    /** Базовая ширина; с вкладкой «Тетрадь» чуть шире ({@link #PANEL_WIDTH_NOTEBOOK}). */
    public static final int PANEL_WIDTH = 320;
    public static final int PANEL_WIDTH_NOTEBOOK = 356;
    public static final int PANEL_HEIGHT = 268;

    public static final int HEADER_H = 16;
    public static final int TAB_Y = 17;
    public static final int TAB_H = 16;
    public static final int CONTENT_Y = 36;

    public static final int ROW_H = 16;
    public static final int ROW_GAP = 3;
    public static final int FIELD_H = 16;
    public static final int ICON_BTN = 16;
    public static final int ICON_PX = 12;
    public static final int ICON_SLOT = 14;

    public static final int TOGGLE_W = 28;
    public static final int TOGGLE_H = 12;

    private UiTheme() {}
}
