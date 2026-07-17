package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.config.RadialAction;
import ru.lava.lavamenu.input.KeyBindings;
import ru.lava.lavamenu.radial.RadialExecutor;
import ru.lava.lavamenu.util.PvpStatus;

import java.util.ArrayList;
import java.util.List;

public final class RadialMenuScreen extends Screen {
    private static final int CENTER_SIZE = 56;
    private static final int RADIUS = 92;
    private static final int VERTICAL_OFFSET = 36;
    private static final int PILL_W = 84;

    private final List<RadialAction> shown = new ArrayList<>();
    private RadialAction hovered = null;
    private final boolean holdMode;
    private final List<AbstractWidget> pillButtons = new ArrayList<>();

    public RadialMenuScreen() {
        super(Component.translatable("lavamenu.radial.title"));
        this.holdMode = LavaMenuConfig.get().radial.mode() == LavaMenuConfig.RadialMode.HOLD;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public void executeHoveredAndClose() {
        if (hovered != null) runAction(hovered);
        else onClose();
    }

    private void runAction(RadialAction action) {
        if (action == null || !action.isExecutable()) { onClose(); return; }
        if (RadialExecutor.execute(action)) onClose();
    }

    @Override
    protected void init() { rebuildButtons(); }

    @Override
    protected void repositionElements() { rebuildButtons(); }

    private void rebuildButtons() {
        clearWidgets();
        pillButtons.clear();
        shown.clear();
        PvpStatus.syncFromTab();
        shown.addAll(LavaMenuConfig.get().radial.visibleActions());
        if (shown.isEmpty()) return;

        int cx = width / 2;
        int cy = centerY();
        int n = shown.size();
        int pillH = UiTheme.ROW_H;

        for (int i = 0; i < n; i++) {
            RadialAction action = shown.get(i);
            double ang = (Math.PI * 2 * i / n) - Math.PI / 2;
            int bx = cx + (int) (Math.cos(ang) * RADIUS) - PILL_W / 2;
            int by = cy + (int) (Math.sin(ang) * RADIUS) - pillH / 2;
            AbstractWidget pill = LavaWidgets.radialPill(bx, by, PILL_W, pillH,
                    iconFor(action), action.label(), false, () -> runAction(action));
            pillButtons.add(pill);
            addRenderableWidget(pill);
        }
    }

    static GuiIcons iconFor(RadialAction a) {
        return switch (a) {
            case OPEN_AH -> GuiIcons.GAVEL;
            case OPEN_SHOP -> GuiIcons.SHOP;
            case SIT -> GuiIcons.ARMCHAIR;
            case LAY -> GuiIcons.BED;
            case REFRESH_HOMES -> GuiIcons.REFRESH;
            case LAST_HOME, OPEN_HOMES -> GuiIcons.MAP_PIN;
            case FAVORITE_1 -> GuiIcons.STAR_FILLED;
            case FRIEND_QUICK -> GuiIcons.USERS;
            case OPEN_CHATS -> GuiIcons.SEND;
            case TOGGLE_PVP -> PvpStatus.isEnabled() ? GuiIcons.SHIELD : GuiIcons.SWORD;
            case OPEN_MENU -> GuiIcons.GRID;
            default -> GuiIcons.TERMINAL;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        gfx.fill(0, 0, width, height, 0x99000000);
    }

    private int centerY() { return height / 2 - VERTICAL_OFFSET; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        hovered = findHoveredAction(mouseX, mouseY);

        super.extractRenderState(gfx, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = centerY();
        RadialCenterIcon.draw(gfx, Minecraft.getInstance(), cx, cy, CENTER_SIZE);

        int hintY = cy + CENTER_SIZE / 2 + 8;
        if (holdMode) {
            gfx.centeredText(font, Component.translatable("lavamenu.radial.release"), cx, hintY, UiTheme.TEXT_DIM);
        }
    }

    private RadialAction findHoveredAction(int mouseX, int mouseY) {
        for (int i = 0; i < pillButtons.size() && i < shown.size(); i++) {
            if (pillButtons.get(i).isMouseOver(mouseX, mouseY)) return shown.get(i);
        }
        return null;
    }

    public void refreshHoverFromMouse(int mouseX, int mouseY) {
        hovered = findHoveredAction(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
}
