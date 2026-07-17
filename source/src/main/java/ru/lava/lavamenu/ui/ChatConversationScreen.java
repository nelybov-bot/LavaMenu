package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.lava.lavamenu.chat.ChatMessage;
import ru.lava.lavamenu.chat.ChatStore;
import ru.lava.lavamenu.chat.ChatThread;
import ru.lava.lavamenu.util.ChatTimeFormat;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.OnlinePlayers;
import ru.lava.lavamenu.util.PlayerFaces;
import ru.lava.lavamenu.util.UiFeedback;

import java.util.ArrayList;
import java.util.List;

/** Экран переписки с одним игроком. Новые сообщения внизу. */
public final class ChatConversationScreen extends Screen {
    private static final int MSG_LINE_H = 11;
    private static final int MSG_GAP = 3;

    private final Screen parent;
    private final String nick;
    private final String draft;
    private final int[] box = new int[4];
    private EditBox input;
    /** 0 = верх (старые); max = низ (новые). */
    private int scrollPx = 0;
    private boolean stickToBottom = true;
    /** Кэш высоты контента для скролла (обновляется при отрисовке). */
    private int lastContentH = 0;

    public ChatConversationScreen(Screen parent, String nick) {
        this(parent, nick, "");
    }

    public ChatConversationScreen(Screen parent, String nick, String draft) {
        super(Component.literal(nick));
        this.parent = parent;
        this.nick = nick;
        this.draft = draft == null ? "" : draft;
    }

    public void onChatsChanged() {
        // Сообщения читаются каждый кадр из ChatStore — не rebuild (сбросит поле ввода).
        if (stickToBottom) scrollPx = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        ChatStore.get().setViewing(nick);
        MenuPanel.layout(width, height, 260, box);
        int px = box[0] + UiTheme.PAD;
        int w = box[2] - UiTheme.PAD * 2;
        int bottom = box[1] + box[3] - UiTheme.PAD;
        int barY = box[1] + 20;

        // узкая «←», чтобы голова сразу рядом, без дыры
        addRenderableWidget(LavaWidgets.textAction(px, barY, 14, UiTheme.ROW_H,
                Component.literal("←"), this::closeToParent));

        int sendW = UiTheme.ICON_BTN;
        int fieldW = w - sendW - 4;
        input = new EditBox(font, px, bottom - UiTheme.FIELD_H, fieldW, UiTheme.FIELD_H,
                Component.translatable("lavamenu.chats.input"));
        input.setMaxLength(256);
        input.setHint(Component.translatable("lavamenu.chats.input_hint"));
        if (!draft.isEmpty()) {
            input.setValue(draft);
            input.setCursorPosition(draft.length());
            input.setHighlightPos(draft.length());
        }
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(LavaWidgets.icon(px + fieldW + 4, bottom - UiTheme.FIELD_H,
                GuiIcons.SEND, LavaWidgets.BtnStyle.PRIMARY, this::send));
    }

    private void closeToParent() {
        ChatStore.get().setViewing(null);
        Minecraft.getInstance().setScreen(parent);
    }

    private void send() {
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.chats.err_empty"));
            return;
        }
        ChatStore.get().addMessage(nick, true, text, "", false);
        CommandHelper.sendFromUi("msg " + nick + " " + text);
        input.setValue("");
        stickToBottom = true;
        scrollPx = Integer.MAX_VALUE;
        rebuildWidgets();
    }

    private int listTop() { return box[1] + 42; }
    private int listBottom() { return box[1] + box[3] - UiTheme.PAD - UiTheme.FIELD_H - 6; }

    private int maxScroll(int contentH, int viewH) {
        return Math.max(0, contentH - viewH);
    }

    private int textWidth() {
        return Math.max(40, box[2] - UiTheme.PAD * 2);
    }

    /** Перенос по ширине панели (без обрезания «…»). */
    static List<String> wrapLine(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        if (font.width(text) <= maxW) {
            out.add(text);
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            String ch = text.substring(i, i + n);
            if (ch.equals("\n")) {
                out.add(line.toString());
                line.setLength(0);
                i += n;
                continue;
            }
            if (font.width(line.toString() + ch) > maxW && !line.isEmpty()) {
                // перенос по последнему пробелу, если есть
                int sp = line.lastIndexOf(" ");
                if (sp > 0 && sp >= line.length() / 3) {
                    out.add(line.substring(0, sp));
                    String rest = line.substring(sp + 1);
                    line.setLength(0);
                    line.append(rest);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                }
            }
            line.append(ch);
            i += n;
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private List<WrappedMsg> buildWrapped(List<ChatMessage> msgs, int maxW) {
        List<WrappedMsg> list = new ArrayList<>(msgs.size());
        for (ChatMessage m : msgs) {
            String who = m.outgoing
                    ? Component.translatable("lavamenu.chats.you").getString()
                    : nick;
            String stamp = ChatTimeFormat.messageStamp(m.timeMs, m.clock);
            String full = stamp + "  " + who + ": " + m.text;
            List<String> lines = wrapLine(font, full, maxW);
            list.add(new WrappedMsg(lines, m.outgoing ? UiTheme.TEXT_MUTED : UiTheme.TEXT_PRIMARY));
        }
        return list;
    }

    private static int contentHeight(List<WrappedMsg> wrapped) {
        int h = 0;
        for (int i = 0; i < wrapped.size(); i++) {
            h += wrapped.get(i).lines.size() * MSG_LINE_H;
            if (i + 1 < wrapped.size()) h += MSG_GAP;
        }
        return h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawBackdrop(gfx, width, height);
        MenuPanel.drawFrame(gfx, font, Component.literal(nick), box[0], box[1], box[2], box[3]);

        boolean online = OnlinePlayers.isOnline(nick);
        int px = box[0] + UiTheme.PAD;
        int barY = box[1] + 20;
        int face = 16;
        // сразу после «←» (кнопка 14px)
        int faceX = px + 16;
        PlayerFaces.draw(gfx, font, nick, faceX, barY, face);

        int statusX = box[0] + box[2] - UiTheme.PAD - 54;
        MenuPanel.drawStatusDot(gfx, statusX, barY, UiTheme.ROW_H, online);
        gfx.text(font, Component.translatable(online ? "lavamenu.chats.online" : "lavamenu.chats.offline"),
                statusX + 12, barY + 4, online ? UiTheme.ONLINE : UiTheme.OFFLINE, false);

        ChatThread thread = ChatStore.get().find(nick);
        List<ChatMessage> msgs = thread == null ? List.of() : new ArrayList<>(thread.messages);
        int top = listTop(), bottom = listBottom();
        int viewH = Math.max(1, bottom - top);
        int maxW = textWidth();
        List<WrappedMsg> wrapped = buildWrapped(msgs, maxW);
        int contentH = contentHeight(wrapped);
        lastContentH = contentH;
        int max = maxScroll(contentH, viewH);
        if (stickToBottom) scrollPx = max;
        scrollPx = Math.max(0, Math.min(max, scrollPx));

        int y = top + Math.max(0, viewH - contentH) - scrollPx;
        MenuPanel.withScissor(gfx, px, top, maxW, viewH, () -> {
            int drawY = y;
            for (int mi = 0; mi < wrapped.size(); mi++) {
                WrappedMsg wm = wrapped.get(mi);
                for (String line : wm.lines) {
                    if (MenuPanel.rowVisible(drawY, MSG_LINE_H, top, bottom)) {
                        gfx.text(font, Component.literal(line), px, drawY + 1, wm.color, false);
                    }
                    drawY += MSG_LINE_H;
                }
                if (mi + 1 < wrapped.size()) drawY += MSG_GAP;
            }
        });

        if (msgs.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.chats.empty_thread"),
                    px, top + 4, UiTheme.TEXT_DIM, false);
        }

        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (MenuPanel.inRect(mouseX, mouseY, box[0] + UiTheme.PAD, listTop(),
                box[2] - UiTheme.PAD * 2, listBottom() - listTop())) {
            int viewH = Math.max(1, listBottom() - listTop());
            int max = maxScroll(lastContentH, viewH);
            scrollPx = Math.max(0, Math.min(max, scrollPx - (int) (scrollY * MSG_LINE_H * 2)));
            stickToBottom = scrollPx >= max - 2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
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
        ChatStore.get().setViewing(null);
        super.onClose();
    }

    private record WrappedMsg(List<String> lines, int color) {}
}
