package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class LavaWidgets {
    private LavaWidgets() {}

    public enum BtnStyle { SECONDARY, PRIMARY, DANGER }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }

    private static int textY(int h) {
        return (h - 8) / 2;
    }

    private abstract static class LavaButtonBase extends AbstractButton {
        LavaButtonBase(int x, int y, int w, int h, Component text) {
            super(x, y, w, h, text);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    public static AbstractButton styled(int x, int y, int w, int h, Component text, BtnStyle style, Runnable onPress) {
        return new LavaButtonBase(x, y, w, h, text) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                int bg = switch (style) {
                    case PRIMARY -> isHovered() ? 0xFF7BB4E0 : UiTheme.ACCENT;
                    case DANGER -> isHovered() ? 0xFFD47A7A : UiTheme.DANGER_BG;
                    default -> isHovered() ? UiTheme.ROW_HOVER : UiTheme.BTN_SECONDARY_BG;
                };
                int fg = switch (style) {
                    case PRIMARY -> UiTheme.TEXT_DARK;
                    case DANGER -> UiTheme.DANGER_TEXT;
                    default -> UiTheme.TEXT_PRIMARY;
                };
                int x = getX(), y = getY();
                if (style == BtnStyle.SECONDARY) {
                    gfx.fill(x, y, x + width, y + height, UiTheme.BTN_SECONDARY_BORDER);
                    gfx.fill(x + 1, y + 1, x + width - 1, y + height - 1, bg);
                } else {
                    gfx.fill(x, y, x + width, y + height, bg);
                }
                gfx.centeredText(font(), getMessage(), x + width / 2, y + textY(height), fg);
            }
        };
    }

    public static AbstractButton iconBtn(int x, int y, GuiIcons icon, BtnStyle style, Runnable onPress) {
        return icon(x, y, UiTheme.ICON_BTN, icon, style, onPress);
    }

    public static AbstractButton icon(int x, int y, GuiIcons icon, Runnable onPress) {
        return icon(x, y, UiTheme.ICON_BTN, icon, BtnStyle.SECONDARY, onPress);
    }

    public static AbstractButton icon(int x, int y, GuiIcons icon, BtnStyle style, Runnable onPress) {
        return icon(x, y, UiTheme.ICON_BTN, icon, style, onPress);
    }

    public static AbstractButton icon(int x, int y, int size, GuiIcons icon, BtnStyle style, Runnable onPress) {
        return new LavaButtonBase(x, y, size, size, Component.empty()) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                int bg = switch (style) {
                    case PRIMARY -> UiTheme.ACCENT;
                    case DANGER -> UiTheme.DANGER_BG;
                    default -> isHovered() ? UiTheme.ROW_HOVER : 0x00000000;
                };
                if (bg != 0) gfx.fill(getX(), getY(), getX() + width, getY() + height, bg);
                icon.drawInBox(gfx, getX(), getY(), width, UiTheme.ICON_PX, UiTheme.TEXT_PRIMARY);
            }
        };
    }

    public static AbstractButton tab(int x, int y, int w, int h, GuiIcons icon, Component text, boolean active, Runnable onPress) {
        AbstractButton btn = new LavaButtonBase(x, y, w, h, text) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                if (active) {
                    gfx.fill(getX(), getY(), getX() + width, getY() + height, UiTheme.TAB_ACTIVE_BG);
                    gfx.fill(getX(), getY(), getX() + width, getY() + 1, UiTheme.ACCENT_SOFT);
                } else if (isHovered()) {
                    gfx.fill(getX(), getY(), getX() + width, getY() + height, UiTheme.ROW_HOVER);
                }

                String label = getMessage().getString();
                int pad = 2;
                int iconSlot = 11;
                int iconPx = 10;
                int fg = active ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_MUTED;

                // Компактно: текст всегда; иконка только если влезает целиком с подписью
                int fullNeed = iconSlot + 2 + font().width(label);
                boolean withIcon = fullNeed <= width - pad;

                if (withIcon) {
                    icon.drawInBox(gfx, getX() + pad, getY(), iconSlot, iconPx, fg);
                    int tx = getX() + pad + iconSlot + 1;
                    int maxTw = Math.max(0, getX() + width - pad - tx);
                    String shown = label;
                    if (font().width(shown) > maxTw && maxTw > 8) {
                        shown = font().plainSubstrByWidth(shown, maxTw - font().width("…")) + "…";
                    }
                    if (!shown.isEmpty()) {
                        gfx.text(font(), Component.literal(shown), tx, getY() + textY(height), fg, false);
                    }
                } else {
                    // Без иконки — только текст (обрезать при нехватке места)
                    int maxTw = Math.max(0, width - pad * 2);
                    String shown = label;
                    if (font().width(shown) > maxTw && maxTw > 8) {
                        shown = font().plainSubstrByWidth(shown, maxTw - font().width("…")) + "…";
                    }
                    int tw = font().width(shown);
                    int tx = getX() + Math.max(pad, (width - tw) / 2);
                    if (!shown.isEmpty()) {
                        gfx.text(font(), Component.literal(shown), tx, getY() + textY(height), fg, false);
                    }
                }

                if (active) {
                    gfx.fill(getX(), getY() + height - 2, getX() + width, getY() + height, UiTheme.ACCENT);
                }
            }
        };
        btn.setTooltip(Tooltip.create(text));
        return btn;
    }

    /** Строка: иконка слева + текст (компактная, без лишней высоты). */
    public static AbstractButton cmdRow(int x, int y, int w, int h, GuiIcons icon, Component text, Runnable onPress) {
        return new LavaButtonBase(x, y, w, h, text) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                int bg = isHovered() ? UiTheme.ROW_HOVER : UiTheme.BTN_SECONDARY_BG;
                int x = getX(), y = getY();
                gfx.fill(x, y, x + width, y + height, UiTheme.BTN_SECONDARY_BORDER);
                gfx.fill(x + 1, y + 1, x + width - 1, y + height - 1, bg);
                icon.drawInBox(gfx, x + 2, y, UiTheme.ICON_SLOT, UiTheme.ICON_PX, UiTheme.TEXT_PRIMARY);
                String label = getMessage().getString();
                int textX = x + UiTheme.ICON_SLOT + 4;
                int maxTw = Math.max(0, x + width - textX - 4);
                if (font().width(label) > maxTw && maxTw > 8) {
                    label = font().plainSubstrByWidth(label, Math.max(0, maxTw - font().width("…"))) + "…";
                }
                if (maxTw > 0 && !label.isEmpty()) {
                    gfx.text(font(), Component.literal(label), textX, y + textY(height),
                            UiTheme.TEXT_PRIMARY, false);
                }
            }
        };
    }

    public static ToggleSwitch toggle(int x, int y, boolean on, Consumer<Boolean> onChange) {
        return new ToggleSwitch(x, y, on, onChange);
    }

    /** Кликабельная подпись без фона кнопки. */
    public static AbstractButton textAction(int x, int y, int w, int h, Component text, Runnable onPress) {
        return new LavaButtonBase(x, y, w, h, text) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                int fg = isHovered() ? UiTheme.ACCENT : UiTheme.TEXT_PRIMARY;
                gfx.text(font(), getMessage(), getX(), getY() + textY(height), fg, false);
            }
        };
    }

    /** Невидимая зона клика — текст/hover рисует overlay. */
    public static AbstractButton hitArea(int x, int y, int w, int h, Runnable onPress) {
        return new LavaButtonBase(x, y, w, h, Component.empty()) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                // пусто
            }
        };
    }

    public static AbstractButton radialPill(int x, int y, GuiIcons icon, Component text, boolean highlighted, Runnable onPress) {
        int h = UiTheme.ROW_H;
        int tw = Math.min(100, UiTheme.ICON_SLOT + 6 + text.getString().length() * 5);
        int w = Math.max(72, tw);
        return radialPill(x, y, w, h, icon, text, highlighted, onPress);
    }

    public static AbstractButton radialPill(int x, int y, int w, int h, GuiIcons icon, Component text,
                                            boolean highlighted, Runnable onPress) {
        return new LavaButtonBase(x, y, w, h, text) {
            @Override
            public void onPress(InputWithModifiers input) { onPress.run(); }

            @Override
            protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                boolean hot = highlighted || isHovered();
                int bg = hot ? UiTheme.ACCENT : UiTheme.BTN_SECONDARY_BG;
                int fg = hot ? UiTheme.TEXT_DARK : UiTheme.TEXT_PRIMARY;
                int x = getX(), y = getY();
                if (!hot) {
                    gfx.fill(x, y, x + width, y + height, UiTheme.BTN_SECONDARY_BORDER);
                    gfx.fill(x + 1, y + 1, x + width - 1, y + height - 1, bg);
                } else {
                    gfx.fill(x, y, x + width, y + height, bg);
                }
                icon.drawInBox(gfx, x + 1, y, UiTheme.ICON_SLOT, UiTheme.ICON_PX, fg);
                gfx.text(font(), getMessage(), x + UiTheme.ICON_SLOT + 2, y + textY(height), fg, false);
            }
        };
    }

    /**
     * Заголовок измерения в списке точек (иконка мира + подпись). Не кликабелен.
     */
    public static AbstractWidget dimHeader(int x, int y, int w, int h, String dimension) {
        GuiIcons icon = GuiIcons.forDimension(dimension);
        int color = GuiIcons.colorForDimension(dimension);
        Component label = Component.literal(dimension == null ? "" : dimension);
        return new AbstractWidget(x, y, w, h, label) {
            @Override
            public void updateWidgetNarration(NarrationElementOutput output) {
                defaultButtonNarrationText(output);
            }

            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
                gfx.fill(getX(), getY(), getX() + width, getY() + height, UiTheme.PANEL_BG);
                icon.drawInBox(gfx, getX(), getY(), h, Math.min(12, h - 2), color);
                gfx.text(font(), getMessage(), getX() + h + 2, getY() + textY(height), color, false);
            }

            @Override
            public void onClick(MouseButtonEvent event, boolean doubleClick) {
                // только подпись
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
                return false;
            }
        };
    }

    public static final class ToggleSwitch extends AbstractWidget {
        private boolean on;
        private final Consumer<Boolean> onChange;

        public ToggleSwitch(int x, int y, boolean on, Consumer<Boolean> onChange) {
            super(x, y, UiTheme.TOGGLE_W, UiTheme.TOGGLE_H, Component.empty());
            this.on = on;
            this.onChange = onChange;
        }

        public boolean isOn() {
            return on;
        }

        public void setOn(boolean value) {
            this.on = value;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
            int track = on ? UiTheme.ACCENT : UiTheme.BTN_SECONDARY_BG;
            int x = getX(), y = getY();
            gfx.fill(x, y + 2, x + width, y + height - 2, UiTheme.BTN_SECONDARY_BORDER);
            gfx.fill(x + 1, y + 3, x + width - 1, y + height - 3, track);
            int knobX = on ? x + width - 10 : x + 2;
            gfx.fill(knobX, y + 1, knobX + 8, y + height - 1, UiTheme.TEXT_PRIMARY);
            if (on) {
                gfx.fill(knobX + 1, y + 2, knobX + 7, y + 3, UiTheme.ACCENT_SOFT);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            on = !on;
            onChange.accept(on);
        }
    }
}
