package ru.lava.lavamenu.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.util.CommandHelper;

import java.util.List;

public final class FavoriteHomesQuickScreen extends Screen {
    private final int[] box = new int[4];
    private int scroll = 0;

    public FavoriteHomesQuickScreen() {
        super(Component.translatable("lavamenu.homes.favorites_title"));
    }

    private static int step() { return UiTheme.ROW_H + UiTheme.ROW_GAP; }

    @Override
    protected void init() {
        MenuPanel.layout(width, height, 200, box);
        initList();
    }

    private int listTop() { return box[1] + 34; }
    private int listBottom() { return box[1] + box[3] - UiTheme.PAD; }

    private void initList() {
        List<String> favorites = LavaMenuConfig.get().homes.favorites;
        if (favorites.isEmpty()) return;
        int px = box[0] + UiTheme.PAD;
        int w = box[2] - UiTheme.PAD * 2;
        int top = listTop(), bottom = listBottom();
        int y = top - scroll * step();
        for (String name : favorites) {
            if (MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) {
                addRenderableWidget(LavaWidgets.cmdRow(px, y, w, UiTheme.ROW_H, GuiIcons.STAR_FILLED,
                        Component.literal(name), () -> teleport(name)));
            }
            y += step();
        }
    }

    private void teleport(String name) {
        LavaMenuConfig.get().homes.lastUsed = name;
        LavaMenuConfig.get().save();
        CommandHelper.closeAndSend("home " + name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawBackdrop(gfx, width, height);
        MenuPanel.drawFrame(gfx, font, title, box[0], box[1], box[2], box[3]);

        List<String> favorites = LavaMenuConfig.get().homes.favorites;
        if (favorites.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.homes.favorites_empty"),
                    box[0] + UiTheme.PAD, listTop() + 4, UiTheme.TEXT_DIM, false);
        }
        gfx.centeredText(font, Component.translatable("lavamenu.homes.quick_hint"),
                box[0] + box[2] / 2, box[1] + box[3] - 6, UiTheme.TEXT_DIM);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int n = LavaMenuConfig.get().homes.favorites.size();
        if (n > 0 && MenuPanel.inRect(mouseX, mouseY, box[0] + UiTheme.PAD, listTop(),
                box[2] - UiTheme.PAD * 2, listBottom() - listTop())) {
            int vis = Math.max(1, (listBottom() - listTop()) / step());
            int maxScroll = Math.max(0, n - vis);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
