package ru.lava.lavamenu.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.FriendsListHelper;

import java.util.List;

public final class FriendsQuickScreen extends Screen {
    private final int[] box = new int[4];
    private int scroll = 0;

    public FriendsQuickScreen() {
        super(Component.translatable("lavamenu.friends.quick_title"));
    }

    public void onFriendsChanged() {
        rebuildWidgets();
    }

    private static int step() { return UiTheme.ROW_H + UiTheme.ROW_GAP; }

    @Override
    protected void init() {
        MenuPanel.layout(width, height, 210, box);
        initList();
    }

    @Override
    public void tick() {
        super.tick();
        // Не пересобираем список постоянно — точки статуса рисуются в overlay
        // и обновляются при следующем открытии/скролле.
    }

    private int listTop() { return box[1] + 36; }
    private int listBottom() { return box[1] + box[3] - UiTheme.PAD; }

    private void initList() {
        List<FriendsListHelper.Row> friends = FriendsListHelper.sortedRows(LavaMenuConfig.get().friends);
        if (friends.isEmpty()) return;
        int px = box[0] + UiTheme.PAD;
        int w = box[2] - UiTheme.PAD * 2;
        int top = listTop(), bottom = listBottom();
        int y = top - scroll * step();
        for (FriendsListHelper.Row row : friends) {
            if (MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) {
                String nick = row.entry().nick;
                String line = row.entry().label + " · " + nick;
                addRenderableWidget(LavaWidgets.styled(px + 10, y, w - 10, UiTheme.ROW_H,
                        Component.literal(line), LavaWidgets.BtnStyle.SECONDARY, () -> tpa(nick)));
            }
            y += step();
        }
    }

    private void tpa(String nick) {
        CommandHelper.closeAndSend("tpa " + nick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawBackdrop(gfx, width, height);
        MenuPanel.drawFrame(gfx, font, title, box[0], box[1], box[2], box[3]);

        var configFriends = LavaMenuConfig.get().friends;
        var friends = FriendsListHelper.sortedRows(configFriends);
        if (friends.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.friends.empty_hint"),
                    box[0] + UiTheme.PAD, listTop() + 4, UiTheme.TEXT_DIM, false);
        } else {
            int top = listTop(), bottom = listBottom();
            int px = box[0] + UiTheme.PAD;
            int w = box[2] - UiTheme.PAD * 2;
            MenuPanel.withScissor(gfx, px, top, w, bottom - top, () -> {
                int y = top - scroll * step();
                for (FriendsListHelper.Row row : friends) {
                    if (y > bottom) return;
                    if (!MenuPanel.rowVisible(y, UiTheme.ROW_H, top, bottom)) { y += step(); continue; }
                    MenuPanel.drawStatusDot(gfx, px + 1, y, UiTheme.ROW_H, row.online());
                    y += step();
                }
            });
        }

        gfx.centeredText(font, Component.translatable("lavamenu.friends.quick_hint"),
                box[0] + box[2] / 2, box[1] + box[3] - 6, UiTheme.TEXT_DIM);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int n = LavaMenuConfig.get().friends.size();
        if (n > 0 && MenuPanel.inRect(mouseX, mouseY, box[0] + UiTheme.PAD, listTop(),
                box[2] - UiTheme.PAD * 2, listBottom() - listTop())) {
            int vis = Math.max(1, (listBottom() - listTop()) / step());
            int maxScroll = Math.max(0, n - vis + 1);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
