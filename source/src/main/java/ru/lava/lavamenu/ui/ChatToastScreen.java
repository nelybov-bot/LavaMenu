package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.lava.lavamenu.chat.ChatStore;
import ru.lava.lavamenu.chat.ChatToastService;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.UiFeedback;

/**
 * Режим ответа в тосте: без затемнения, {@code isPauseScreen=false}.
 * Можно сразу открыть полный чат (с переносом черновика).
 */
public final class ChatToastScreen extends Screen {
    public static final int PANEL_H = 58;

    private final String nick;
    private String preview;
    private EditBox input;
    private int panelX;
    private int panelY;

    public ChatToastScreen(String nick, String preview) {
        super(Component.translatable("lavamenu.chats.toast_title"));
        this.nick = nick == null ? "" : nick;
        this.preview = preview == null ? "" : preview;
    }

    public String nick() {
        return nick;
    }

    public void updatePreview(String text) {
        if (text != null && !text.isBlank()) this.preview = text.trim();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelX = ChatToastService.toastX(width);
        panelY = height - PANEL_H - ChatToastService.MARGIN - ChatToastService.BOTTOM_PAD;

        int pad = 4;
        int iconW = UiTheme.ICON_BTN;
        int gap = 3;
        int fieldX = panelX + pad;
        int fieldY = panelY + PANEL_H - pad - UiTheme.FIELD_H;
        int fieldW = ChatToastService.TOAST_W - pad * 2 - iconW * 2 - gap * 2;

        input = new EditBox(font, fieldX, fieldY, fieldW, UiTheme.FIELD_H,
                Component.translatable("lavamenu.chats.input"));
        input.setMaxLength(256);
        input.setHint(Component.translatable("lavamenu.chats.input_hint"));
        addRenderableWidget(input);
        setInitialFocus(input);

        int openX = fieldX + fieldW + gap;
        var openBtn = LavaWidgets.icon(openX, fieldY, GuiIcons.USERS, LavaWidgets.BtnStyle.SECONDARY, this::openFullChat);
        openBtn.setTooltip(Tooltip.create(Component.translatable("lavamenu.chats.toast_open")));
        addRenderableWidget(openBtn);

        addRenderableWidget(LavaWidgets.icon(openX + iconW + gap, fieldY,
                GuiIcons.SEND, LavaWidgets.BtnStyle.PRIMARY, this::send));
    }

    private void openFullChat() {
        String draft = input == null ? "" : input.getValue();
        ChatStore.get().getOrCreate(nick);
        ChatStore.get().save();
        Minecraft.getInstance().setScreen(new ChatConversationScreen(null, nick, draft));
    }

    private void send() {
        String text = input == null ? "" : input.getValue().trim();
        if (text.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.chats.err_empty"));
            return;
        }
        ChatStore.get().addMessage(nick, true, text, "", false);
        CommandHelper.sendFromUi("msg " + nick + " " + text);
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        // без затемнения
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        ChatToastService.drawPanel(gfx, font, nick, preview, panelX, panelY, true);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (mx < panelX || mx >= panelX + ChatToastService.TOAST_W
                || my < panelY || my >= panelY + PANEL_H) {
            onClose();
            return true;
        }
        // клик по шапке (ник / превью) — открыть полный чат
        int fieldTop = panelY + PANEL_H - 6 - UiTheme.FIELD_H;
        if (my < fieldTop) {
            openFullChat();
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
