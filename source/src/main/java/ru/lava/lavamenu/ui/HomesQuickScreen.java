package ru.lava.lavamenu.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.homes.HomesData;
import ru.lava.lavamenu.homes.HomesParser;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.UiFeedback;

import java.util.List;
import java.util.Map;

public final class HomesQuickScreen extends Screen {
    private static final int DIM_HEADER_H = 16;

    private final int[] box = new int[4];
    private int scroll = 0;

    public HomesQuickScreen() {
        super(Component.translatable("lavamenu.homes.quick_title"));
    }

    public void onHomesDataChanged() { rebuildWidgets(); }

    private static int step() { return UiTheme.ROW_H + UiTheme.ROW_GAP; }

    @Override
    protected void init() {
        MenuPanel.layout(width, height, 230, box);
        int px = box[0] + UiTheme.PAD;
        int py = box[1] + 32;
        int w = box[2] - UiTheme.PAD * 2;
        addRenderableWidget(LavaWidgets.cmdRow(px, py, w, UiTheme.ROW_H, GuiIcons.REFRESH,
                Component.translatable("lavamenu.homes.refresh"), () -> {
                    HomesParser.armCapture(120);
                    if (CommandHelper.sendFromUi("homes")) {
                        UiFeedback.actionBar(Component.translatable("lavamenu.homes.refresh_sent"));
                    }
                }));
        initList(px, w);
    }

    private int listTop() { return box[1] + 54; }
    private int listBottom() { return box[1] + box[3] - UiTheme.PAD; }

    private void initList(int px, int w) {
        var data = HomesData.get();
        if (data.isEmpty()) return;
        int top = listTop(), bottom = listBottom();
        int y = top - scroll * step();
        for (Map.Entry<String, List<String>> e : data.dimensions().entrySet()) {
            if (y > bottom) break;
            if (MenuPanel.rowVisible(y, DIM_HEADER_H, top, bottom)) {
                addRenderableWidget(LavaWidgets.dimHeader(px, y, w, DIM_HEADER_H, e.getKey()));
            }
            y += DIM_HEADER_H;
            for (String name : e.getValue()) {
                if (MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) {
                    addRenderableWidget(LavaWidgets.styled(px, y, w, UiTheme.ROW_H, Component.literal(name),
                            LavaWidgets.BtnStyle.SECONDARY, () -> teleport(name)));
                }
                y += step();
            }
        }
    }

    private void teleport(String name) {
        CommandHelper.closeAndSend("home " + name);
        LavaMenuConfig.get().homes.lastUsed = name;
        LavaMenuConfig.get().save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawBackdrop(gfx, width, height);
        MenuPanel.drawFrame(gfx, font, title, box[0], box[1], box[2], box[3]);
        int px = box[0] + UiTheme.PAD;
        var data = HomesData.get();
        MenuPanel.drawDivider(gfx, px, listTop() - 3, box[2] - UiTheme.PAD * 2);
        gfx.text(font, Component.translatable("lavamenu.homes.list_title", data.count(), data.max()),
                px, listTop() - 10, UiTheme.TEXT_PRIMARY, false);
        if (data.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.homes.empty_hint"),
                    px, listTop() + 2, UiTheme.TEXT_DIM, false);
        }
        gfx.centeredText(font, Component.translatable("lavamenu.homes.quick_hint"),
                box[0] + box[2] / 2, box[1] + box[3] - 6, UiTheme.TEXT_DIM);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!HomesData.get().isEmpty()
                && MenuPanel.inRect(mouseX, mouseY, box[0] + UiTheme.PAD, listTop(),
                box[2] - UiTheme.PAD * 2, listBottom() - listTop())) {
            int content = 0;
            for (Map.Entry<String, List<String>> e : HomesData.get().dimensions().entrySet()) {
                content += DIM_HEADER_H + e.getValue().size() * step();
            }
            int vis = Math.max(1, listBottom() - listTop());
            int maxScroll = Math.max(0, (content - vis + step() - 1) / step());
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
