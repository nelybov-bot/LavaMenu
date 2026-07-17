package ru.lava.lavamenu.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import ru.lava.lavamenu.ui.GuiIcons;
import ru.lava.lavamenu.ui.MenuPanel;
import ru.lava.lavamenu.ui.UiTheme;

/**
 * Голова игрока: онлайн (Tab/P) → кэш файла → инициалы.
 */
public final class PlayerFaces {
    /** Размер в списке чатов. */
    public static final int SIZE = 20;
    /** Компактный размер (друзья и т.п.). */
    public static final int SIZE_SM = 12;

    private PlayerFaces() {}

    public static void draw(GuiGraphicsExtractor gfx, Font font, String nick, int x, int y, int size) {
        boolean online = OnlinePlayers.isOnline(nick);
        FaceCache.get().rememberIfOnline(nick);

        PlayerInfo info = OnlinePlayers.find(nick);
        if (info != null) {
            PlayerSkin skin = info.getSkin();
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(gfx, skin, x, y, size);
                drawOnlineRing(gfx, x, y, size, true);
                return;
            }
        }

        Identifier cached = FaceCache.get().textureFor(nick);
        if (cached != null) {
            if (FaceCache.get().isFaceTexture(cached)) {
                // наш PNG — уже только голова 8×8, на весь квадрат
                drawFaceTexture(gfx, cached, x, y, size);
            } else {
                // полный скин из сессии
                PlayerFaceExtractor.extractRenderState(gfx, cached, x, y, size, true, false, -1);
            }
            drawOnlineRing(gfx, x, y, size, online);
            return;
        }

        MenuPanel.drawAvatar(gfx, font, GuiIcons.initials(nick), x, y, size);
        // рамка, чтобы квадрат инициалов не сливался с панелью
        gfx.fill(x, y, x + size, y + 1, UiTheme.DIVIDER);
        gfx.fill(x, y + size - 1, x + size, y + size, UiTheme.DIVIDER);
        gfx.fill(x, y, x + 1, y + size, UiTheme.DIVIDER);
        gfx.fill(x + size - 1, y, x + size, y + size, UiTheme.DIVIDER);
        drawOnlineRing(gfx, x, y, size, online);
    }

    private static void drawFaceTexture(GuiGraphicsExtractor gfx, Identifier id, int x, int y, int size) {
        // тот же pipeline, что у иконок: растянуть 8×8 на size
        gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id,
                x, y, 0, 0, size, size, 8, 8, 8, 8);
    }

    /** Маленькая точка статуса в углу головы. */
    private static void drawOnlineRing(GuiGraphicsExtractor gfx, int x, int y, int size, boolean online) {
        int ds = 4;
        int dx = x + size - ds;
        int dy = y + size - ds;
        gfx.fill(dx - 1, dy - 1, dx + ds + 1, dy + ds + 1, UiTheme.PANEL_BG);
        gfx.fill(dx, dy, dx + ds, dy + ds, online ? UiTheme.ONLINE : UiTheme.OFFLINE);
    }
}
