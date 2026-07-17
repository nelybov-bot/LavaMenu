package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import ru.lava.lavamenu.LavaMenuClient;
import ru.lava.lavamenu.chat.ChatMessage;
import ru.lava.lavamenu.chat.ChatNotifySound;
import ru.lava.lavamenu.chat.ChatStore;
import ru.lava.lavamenu.chat.ChatThread;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.config.RadialAction;
import ru.lava.lavamenu.homes.HomeRenameSession;
import ru.lava.lavamenu.homes.HomesData;
import ru.lava.lavamenu.homes.HomesParser;
import ru.lava.lavamenu.notebook.AstoriaNotebookStore;
import ru.lava.lavamenu.notebook.NotebookAccess;
import ru.lava.lavamenu.notebook.NotebookEntry;
import ru.lava.lavamenu.notebook.NotebookShare;
import ru.lava.lavamenu.util.AnimationHelper;
import ru.lava.lavamenu.util.ChatTimeFormat;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.FriendsListHelper;
import ru.lava.lavamenu.util.OnlinePlayers;
import ru.lava.lavamenu.util.PlayerFaces;
import ru.lava.lavamenu.util.PvpStatus;
import ru.lava.lavamenu.util.UiFeedback;
import ru.lava.lavamenu.update.ModUpdateService;

import java.util.List;
import java.util.Map;

public final class LavaMenuScreen extends Screen {
    public enum Tab { HOMES, COMMANDS, FRIENDS, CHATS, NOTEBOOK, SETTINGS }

    private static final int DIM_HEADER_H = 16;
    private static final int CHAT_ROW_H = 36;
    private static final int NOTEBOOK_ROW_H = 28;

    private final int[] box = new int[4];
    private Tab tab;
    private int homesScrollPx = 0;
    private int friendsScroll = 0;
    private int chatsScroll = 0;
    private int notebookScroll = 0;
    private int radialScroll = 0;
    private int friendsOnlineTick = 0;
    private int chatsOnlineTick = 0;
    private int pvpSyncTick = 0;

    private EditBox homeNameField;
    private EditBox friendLabelField;
    private EditBox friendNickField;
    private EditBox chatNickField;
    private EditBox notebookNickField;
    private EditBox notebookReasonField;
    private EditBox notebookShowField;
    private String friendLabelDraft = "";
    private String friendNickDraft = "";
    private String chatNickDraft = "";
    private String notebookNickDraft = "";
    private String notebookReasonDraft = "";
    private String notebookShowDraft = "";
    private boolean homeOverwrite = false;
    private AbstractWidget overwriteBtn;

    private LavaWidgets.ToggleSwitch pvpToggle;
    private LavaWidgets.ToggleSwitch radialModeToggle;

    public LavaMenuScreen() { this(Tab.HOMES); }

    public LavaMenuScreen(Tab tab) {
        super(Component.translatable("lavamenu.title"));
        this.tab = tab;
    }

    public void onHomesDataChanged() {
        if (tab == Tab.HOMES) rebuildWidgets();
    }

    public void onFriendsChanged() {
        if (tab == Tab.FRIENDS) rebuildWidgets();
    }

    public void onChatsChanged() {
        if (tab == Tab.CHATS) rebuildWidgets();
    }

    public void onNotebookChanged() {
        // всегда rebuild: могла появиться/пропасть вкладка «Тетрадь»
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.FRIENDS && !LavaMenuConfig.get().friends.isEmpty()) {
            if (++friendsOnlineTick >= 40) {
                friendsOnlineTick = 0;
                if (!isTypingFriendFields()) {
                    rebuildWidgets();
                }
            }
        } else {
            friendsOnlineTick = 0;
        }
        if (tab == Tab.CHATS && !ChatStore.get().threadsNewestFirst().isEmpty()) {
            if (++chatsOnlineTick >= 40) {
                chatsOnlineTick = 0;
                if (chatNickField == null || !chatNickField.isFocused()) {
                    rebuildWidgets();
                }
            }
        } else {
            chatsOnlineTick = 0;
        }
        if (tab == Tab.COMMANDS) {
            if (++pvpSyncTick >= 20) {
                pvpSyncTick = 0;
                if (PvpStatus.syncFromTab() && pvpToggle != null
                        && pvpToggle.isOn() != LavaMenuConfig.get().pvpEnabled) {
                    pvpToggle.setOn(LavaMenuConfig.get().pvpEnabled);
                }
            }
        } else {
            pvpSyncTick = 0;
        }
    }

    private boolean isTypingFriendFields() {
        return (friendLabelField != null && friendLabelField.isFocused())
                || (friendNickField != null && friendNickField.isFocused());
    }

    private void rememberFriendDrafts() {
        if (friendLabelField != null) friendLabelDraft = friendLabelField.getValue();
        if (friendNickField != null) friendNickDraft = friendNickField.getValue();
    }

    private void selectTab(Tab t) {
        if (tab == t) return;
        if (tab == Tab.SETTINGS) {
            ModUpdateService.get().clearUiListener();
        }
        tab = t;
        homesScrollPx = friendsScroll = chatsScroll = notebookScroll = radialScroll = 0;
        rebuildWidgets();
    }

    private int panelH() {
        return UiTheme.PANEL_HEIGHT;
    }

    private int innerX() { return box[0] + UiTheme.PAD; }
    private int innerY() { return box[1] + UiTheme.CONTENT_Y; }
    private int innerW() { return box[2] - UiTheme.PAD * 2; }
    private int innerBottom() { return box[1] + box[3] - UiTheme.PAD; }

    private static int step() { return UiTheme.ROW_H + UiTheme.ROW_GAP; }

    // HOMES: refresh | name | overwrite+save | [list]
    private int homesNameY() { return innerY() + step(); }
    private int homesActionsY() { return homesNameY() + step(); }
    private int homesFormBottom() { return homesActionsY() + UiTheme.ROW_H; }
    private int homesListTop() { return homesFormBottom() + 12; }
    private int homesListBottom() { return innerBottom(); }

    // FRIENDS
    private int friendsFieldY() { return innerY() + 10; }
    private int friendsFormBottom() { return friendsFieldY() + UiTheme.FIELD_H; }
    private int friendsListTop() { return friendsFormBottom() + 12; }
    private int friendsListBottom() { return innerBottom(); }

    // COMMANDS
    private int cmdServerY() { return innerY(); }
    private int cmdAnimY() { return cmdServerY() + step() * 2 + 10; }
    private int cmdTerritoryY() { return cmdAnimY() + step() + 10; }
    private int cmdPvpY() { return cmdTerritoryY() + step(); }

    // CHATS
    private int chatsFieldY() { return innerY() + 8; }
    private int chatsFormBottom() { return chatsFieldY() + UiTheme.FIELD_H; }
    private int chatsListTop() { return chatsFormBottom() + 14; }
    private int chatsListBottom() { return innerBottom(); }
    private static int chatStep() { return CHAT_ROW_H + UiTheme.ROW_GAP; }

    // NOTEBOOK: заголовок сверху, форма/подзаголовок, потом список
    private int notebookTitleY() { return innerY() + 2; }
    private int notebookAddY() { return notebookTitleY() + 14; }
    private int notebookShowY() { return notebookAddY() + step(); }
    private int notebookFormBottom() {
        if (NotebookAccess.canEdit()) return notebookShowY() + UiTheme.FIELD_H;
        // зритель: заголовок + строка «от …»
        return notebookTitleY() + 24;
    }
    private int notebookListTop() { return notebookFormBottom() + 10; }
    private int notebookListBottom() { return innerBottom(); }
    private static int notebookStep() { return NOTEBOOK_ROW_H + UiTheme.ROW_GAP; }

    // SETTINGS: hold mode + hint + notify + sound, then update, then slots
    private int settingsModeY() { return innerY() + 8; }
    private int settingsHintY() { return settingsModeY() + 14; }
    private int settingsNotifyY() { return settingsHintY() + 14; }
    private int settingsSoundY() { return settingsNotifyY() + 16; }
    private int settingsUpdateY() { return settingsSoundY() + 18; }
    private int settingsUpdateBtnY() { return settingsUpdateY() + 11; }
    private int settingsSlotsTop() { return settingsUpdateBtnY() + UiTheme.ROW_H + 8; }
    private int settingsSlotsListTop() { return settingsSlotsTop() + 12; }
    private int settingsSlotsBottom() { return innerBottom(); }

    @Override
    protected void repositionElements() {
        MenuPanel.layout(width, height, panelH(), box);
        super.repositionElements();
    }

    @Override
    public void onClose() {
        ModUpdateService.get().clearUiListener();
        super.onClose();
    }

    @Override
    protected void init() {
        if (tab == Tab.NOTEBOOK && !NotebookAccess.canView()) {
            tab = Tab.HOMES;
        }
        MenuPanel.layout(width, height, panelH(), box);
        int tabY = box[1] + UiTheme.TAB_Y;
        int gap = 2;
        boolean showNotebook = NotebookAccess.canView();
        int tabCount = showNotebook ? 6 : 5;
        int tabW = (innerW() - gap * (tabCount - 1)) / tabCount;
        int tx = innerX();
        int i = 0;

        addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i++, tabY, tabW, UiTheme.TAB_H, GuiIcons.MAP_PIN,
                Component.translatable("lavamenu.tab.homes"), tab == Tab.HOMES, () -> selectTab(Tab.HOMES)));
        addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i++, tabY, tabW, UiTheme.TAB_H, GuiIcons.TERMINAL,
                Component.translatable("lavamenu.tab.commands"), tab == Tab.COMMANDS, () -> selectTab(Tab.COMMANDS)));
        addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i++, tabY, tabW, UiTheme.TAB_H, GuiIcons.USERS,
                Component.translatable("lavamenu.tab.friends"), tab == Tab.FRIENDS, () -> selectTab(Tab.FRIENDS)));
        addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i++, tabY, tabW, UiTheme.TAB_H, GuiIcons.SEND,
                Component.translatable("lavamenu.tab.chats"), tab == Tab.CHATS, () -> selectTab(Tab.CHATS)));
        if (showNotebook) {
            addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i++, tabY, tabW, UiTheme.TAB_H, GuiIcons.EDIT,
                    Component.translatable("lavamenu.tab.notebook"), tab == Tab.NOTEBOOK, () -> selectTab(Tab.NOTEBOOK)));
        }
        addRenderableWidget(LavaWidgets.tab(tx + (tabW + gap) * i, tabY, tabW, UiTheme.TAB_H, GuiIcons.SETTINGS,
                Component.translatable("lavamenu.tab.settings"), tab == Tab.SETTINGS, () -> selectTab(Tab.SETTINGS)));

        switch (tab) {
            case HOMES -> initHomes();
            case COMMANDS -> initCommands();
            case FRIENDS -> initFriends();
            case CHATS -> initChats();
            case NOTEBOOK -> initNotebook();
            case SETTINGS -> initSettings();
        }
    }

    // ==================== HOMES ====================

    private void initHomes() {
        int px = innerX(), w = innerW();
        addRenderableWidget(LavaWidgets.cmdRow(px, innerY(), w, UiTheme.ROW_H, GuiIcons.REFRESH,
                Component.translatable("lavamenu.homes.refresh"), this::refreshHomes));

        homeNameField = new EditBox(font, px, homesNameY(), w, UiTheme.FIELD_H,
                Component.translatable("lavamenu.homes.name"));
        homeNameField.setHint(Component.translatable("lavamenu.homes.name"));
        homeNameField.setMaxLength(32);
        addRenderableWidget(homeNameField);

        int ay = homesActionsY();
        int owW = (int) (w * 0.58);
        overwriteBtn = LavaWidgets.styled(px, ay, owW, UiTheme.ROW_H, overwriteLabel(),
                LavaWidgets.BtnStyle.SECONDARY, () -> {
                    homeOverwrite = !homeOverwrite;
                    overwriteBtn.setMessage(overwriteLabel());
                });
        addRenderableWidget(overwriteBtn);
        int saveX = px + owW + UiTheme.ROW_GAP;
        addRenderableWidget(LavaWidgets.textAction(saveX, ay, w - owW - UiTheme.ROW_GAP, UiTheme.ROW_H,
                Component.translatable("lavamenu.homes.set"), this::setHome));

        initHomesList(px, w);
    }

    private void initHomesList(int px, int w) {
        var data = HomesData.get();
        if (data.isEmpty()) return;
        int top = homesListTop(), bottom = homesListBottom();
        int y = top - homesScrollPx;
        for (Map.Entry<String, List<String>> e : data.dimensions().entrySet()) {
            if (y > bottom) break;
            if (MenuPanel.rowVisible(y, DIM_HEADER_H, top, bottom)) {
                addRenderableWidget(LavaWidgets.dimHeader(px, y, w, DIM_HEADER_H, e.getKey()));
            }
            y += DIM_HEADER_H;
            for (String name : e.getValue()) {
                if (MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) addHomeRow(px, w, y, name);
                y += step();
            }
        }
    }

    private void addHomeRow(int px, int w, int y, String name) {
        boolean fav = LavaMenuConfig.get().homes.isFavorite(name);
        int ax = px + w - 50;
        addRenderableWidget(LavaWidgets.icon(px, y, fav ? GuiIcons.STAR_FILLED : GuiIcons.STAR, () -> {
            LavaMenuConfig.get().homes.toggleFavorite(name);
            LavaMenuConfig.get().save();
            rebuildWidgets();
        }));
        String label = name.length() > 24 ? name.substring(0, 22) + "…" : name;
        addRenderableWidget(LavaWidgets.styled(px + 18, y, ax - px - 20, UiTheme.ROW_H, Component.literal(label),
                LavaWidgets.BtnStyle.SECONDARY, () -> teleportHome(name)));
        addRenderableWidget(LavaWidgets.icon(ax, y, GuiIcons.EDIT, () ->
                Minecraft.getInstance().setScreen(new RenameEntryScreen(this,
                        Component.translatable("lavamenu.homes.rename"),
                        Component.translatable("lavamenu.homes.rename_hint", name),
                        name, neu -> Minecraft.getInstance().execute(() -> promptRenameHome(name, neu))))));
        addRenderableWidget(LavaWidgets.icon(ax + 17, y, GuiIcons.TRASH, LavaWidgets.BtnStyle.DANGER, () ->
                Minecraft.getInstance().setScreen(new ConfirmScreen(this,
                        Component.translatable("lavamenu.confirm.title"),
                        Component.translatable("lavamenu.homes.delete_confirm", name),
                        () -> CommandHelper.sendFromUi("delhome " + name)))));
    }

    /**
     * Переименование без атомарной команды сервера: /sethome пишет текущую позицию.
     * Удаление старого имени — только после появления нового в ответе /homes.
     */
    private void promptRenameHome(String oldName, String neu) {
        String name = neu == null ? "" : neu.trim();
        if (name.isEmpty() || name.equals(oldName)) return;
        Minecraft.getInstance().setScreen(new ConfirmScreen(this,
                Component.translatable("lavamenu.confirm.title"),
                Component.translatable("lavamenu.homes.rename_confirm", oldName, name),
                340, 128,
                () -> HomeRenameSession.begin(oldName, name)));
    }

    // ==================== COMMANDS ====================

    private void initCommands() {
        int px = innerX(), w = innerW(), half = (w - UiTheme.ROW_GAP) / 2;
        addRenderableWidget(LavaWidgets.cmdRow(px, cmdServerY(), w, UiTheme.ROW_H, GuiIcons.GAVEL,
                Component.translatable("lavamenu.cmd.ah"), () -> CommandHelper.closeAndSend("ah")));
        addRenderableWidget(LavaWidgets.cmdRow(px, cmdServerY() + step(), w, UiTheme.ROW_H, GuiIcons.SHOP,
                Component.translatable("lavamenu.cmd.shop"), () -> CommandHelper.closeAndSend("warp shop")));
        addRenderableWidget(LavaWidgets.cmdRow(px, cmdAnimY(), half, UiTheme.ROW_H, GuiIcons.ARMCHAIR,
                Component.translatable("lavamenu.anim.sit"),
                () -> AnimationHelper.closeAndPlay(AnimationHelper.Type.SIT)));
        addRenderableWidget(LavaWidgets.cmdRow(px + half + UiTheme.ROW_GAP, cmdAnimY(), half, UiTheme.ROW_H, GuiIcons.BED,
                Component.translatable("lavamenu.anim.lay"),
                () -> AnimationHelper.closeAndPlay(AnimationHelper.Type.LAY)));

        PvpStatus.syncFromTab();
        pvpToggle = LavaWidgets.toggle(px + w - UiTheme.TOGGLE_W, cmdPvpY() + 2,
                LavaMenuConfig.get().pvpEnabled, on -> {
                    LavaMenuConfig.get().pvpEnabled = on;
                    LavaMenuConfig.get().save();
                    CommandHelper.sendFromUi(on ? "pvp on" : "pvp off");
                });
        addRenderableWidget(pvpToggle);
    }

    // ==================== FRIENDS ====================

    private void initFriends() {
        rememberFriendDrafts();
        int px = innerX(), w = innerW();
        int fy = friendsFieldY();
        int addW = UiTheme.ICON_BTN;
        int gap = 4;
        int addX = px + w - addW;
        int fieldW = (addX - gap - px - gap) / 2;

        friendLabelField = new EditBox(font, px, fy, fieldW, UiTheme.FIELD_H, Component.translatable("lavamenu.friends.label"));
        friendLabelField.setHint(Component.translatable("lavamenu.friends.label_hint"));
        friendLabelField.setMaxLength(32);
        friendLabelField.setValue(friendLabelDraft);
        addRenderableWidget(friendLabelField);
        friendNickField = new EditBox(font, px + fieldW + gap, fy, fieldW, UiTheme.FIELD_H, Component.translatable("lavamenu.friends.nick"));
        friendNickField.setHint(Component.translatable("lavamenu.friends.nick_hint"));
        friendNickField.setMaxLength(16);
        friendNickField.setValue(friendNickDraft);
        addRenderableWidget(friendNickField);
        addRenderableWidget(LavaWidgets.iconBtn(addX, fy, GuiIcons.PLUS, LavaWidgets.BtnStyle.PRIMARY, this::addFriend));

        List<FriendsListHelper.Row> friends = FriendsListHelper.sortedRows(LavaMenuConfig.get().friends);
        int top = friendsListTop(), bottom = friendsListBottom();
        int y = top - friendsScroll * step();
        for (FriendsListHelper.Row row : friends) {
            if (!MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) { y += step(); continue; }
            LavaMenuConfig.FriendEntry fe = row.entry();
            int idx = row.index();
            // ТП · Написать · ✎ · ✕
            int bx = px + w - 68;
            LavaWidgets.BtnStyle tpaStyle = row.online() ? LavaWidgets.BtnStyle.PRIMARY : LavaWidgets.BtnStyle.SECONDARY;
            addRenderableWidget(LavaWidgets.icon(bx, y, GuiIcons.MAP_PIN, tpaStyle,
                    () -> CommandHelper.closeAndSend("tpa " + fe.nick)));
            addRenderableWidget(LavaWidgets.icon(bx + 17, y, GuiIcons.SEND, () -> {
                ChatStore.get().getOrCreate(fe.nick);
                ChatStore.get().save();
                Minecraft.getInstance().setScreen(new ChatConversationScreen(this, fe.nick));
            }));
            addRenderableWidget(LavaWidgets.icon(bx + 34, y, GuiIcons.EDIT, () ->
                    Minecraft.getInstance().setScreen(new RenameEntryScreen(this,
                            Component.translatable("lavamenu.friends.rename"),
                            Component.translatable("lavamenu.friends.rename_hint", fe.label),
                            fe.label, neu -> { fe.label = neu; LavaMenuConfig.get().save(); }))));
            addRenderableWidget(LavaWidgets.icon(bx + 51, y, GuiIcons.TRASH, LavaWidgets.BtnStyle.DANGER, () ->
                    Minecraft.getInstance().setScreen(new ConfirmScreen(this,
                            Component.translatable("lavamenu.confirm.title"),
                            Component.translatable("lavamenu.friends.delete_confirm", fe.label),
                            () -> {
                                LavaMenuConfig.get().friends.remove(idx);
                                LavaMenuConfig.get().save();
                                rebuildWidgets();
                            }))));
            y += step();
        }
    }

    // ==================== CHATS ====================

    private void initChats() {
        if (chatNickField != null) chatNickDraft = chatNickField.getValue();
        int px = innerX(), w = innerW();
        int fy = chatsFieldY();
        int openW = UiTheme.ICON_BTN;
        int gap = 4;
        int fieldW = w - openW - gap;

        chatNickField = new EditBox(font, px, fy, fieldW, UiTheme.FIELD_H,
                Component.translatable("lavamenu.chats.nick"));
        chatNickField.setHint(Component.translatable("lavamenu.chats.nick_hint"));
        chatNickField.setMaxLength(16);
        chatNickField.setValue(chatNickDraft);
        addRenderableWidget(chatNickField);
        addRenderableWidget(LavaWidgets.iconBtn(px + fieldW + gap, fy, GuiIcons.SEND,
                LavaWidgets.BtnStyle.PRIMARY, this::openChatFromField));

        List<ChatThread> threads = ChatStore.get().threadsNewestFirst();
        int top = chatsListTop(), bottom = chatsListBottom();
        int y = top - chatsScroll * chatStep();
        for (ChatThread thread : threads) {
            if (!MenuPanel.rowInside(y, CHAT_ROW_H, top, bottom)) {
                y += chatStep();
                continue;
            }
            String nick = thread.nick;
            int trashX = px + w - UiTheme.ICON_BTN;
            // Клик по строке (кроме корзины) → диалог. Текст рисует overlay.
            addRenderableWidget(LavaWidgets.hitArea(px, y, trashX - px - 2, CHAT_ROW_H,
                    () -> Minecraft.getInstance().setScreen(new ChatConversationScreen(this, nick))));
            addRenderableWidget(LavaWidgets.icon(trashX, y + (CHAT_ROW_H - UiTheme.ICON_BTN) / 2,
                    GuiIcons.TRASH, LavaWidgets.BtnStyle.DANGER, () ->
                            Minecraft.getInstance().setScreen(new ConfirmScreen(this,
                                    Component.translatable("lavamenu.confirm.title"),
                                    Component.translatable("lavamenu.chats.delete_confirm", nick),
                                    () -> {
                                        ChatStore.get().deleteThread(nick);
                                        rebuildWidgets();
                                    }))));
            y += chatStep();
        }
    }

    private String ellipsize(String text, int maxW) {
        if (text == null || text.isEmpty() || maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        String ell = "…";
        int ellW = font.width(ell);
        if (maxW <= ellW) return ell;
        // официальный splitter Minecraft — совпадает с отрисовкой
        return font.plainSubstrByWidth(text, maxW - ellW) + ell;
    }

    private void openChatFromField() {
        String nick = chatNickField == null ? "" : chatNickField.getValue().trim();
        if (nick.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.chats.err_nick"));
            return;
        }
        ChatStore.get().getOrCreate(nick);
        ChatStore.get().save();
        Minecraft.getInstance().setScreen(new ChatConversationScreen(this, nick));
    }

    // ==================== NOTEBOOK ====================

    private void initNotebook() {
        boolean edit = NotebookAccess.canEdit();
        int px = innerX(), w = innerW();

        if (edit) {
            if (notebookNickField != null) notebookNickDraft = notebookNickField.getValue();
            if (notebookReasonField != null) notebookReasonDraft = notebookReasonField.getValue();
            if (notebookShowField != null) notebookShowDraft = notebookShowField.getValue();

            int gap = 4;
            int addW = UiTheme.ICON_BTN;
            int nickW = (w - addW - gap * 2) / 2;

            notebookNickField = new EditBox(font, px, notebookAddY(), nickW, UiTheme.FIELD_H,
                    Component.translatable("lavamenu.notebook.nick"));
            notebookNickField.setHint(Component.translatable("lavamenu.notebook.nick_hint"));
            notebookNickField.setMaxLength(16);
            notebookNickField.setValue(notebookNickDraft);
            addRenderableWidget(notebookNickField);

            notebookReasonField = new EditBox(font, px + nickW + gap, notebookAddY(), nickW, UiTheme.FIELD_H,
                    Component.translatable("lavamenu.notebook.reason"));
            notebookReasonField.setHint(Component.translatable("lavamenu.notebook.reason_hint"));
            notebookReasonField.setMaxLength(64);
            notebookReasonField.setValue(notebookReasonDraft);
            addRenderableWidget(notebookReasonField);

            addRenderableWidget(LavaWidgets.iconBtn(px + w - addW, notebookAddY(), GuiIcons.PLUS,
                    LavaWidgets.BtnStyle.PRIMARY, this::addNotebookEntry));

            int showBtnW = 72;
            notebookShowField = new EditBox(font, px, notebookShowY(), w - showBtnW - gap, UiTheme.FIELD_H,
                    Component.translatable("lavamenu.notebook.show_nick"));
            notebookShowField.setHint(Component.translatable("lavamenu.notebook.show_nick_hint"));
            notebookShowField.setMaxLength(16);
            notebookShowField.setValue(notebookShowDraft);
            addRenderableWidget(notebookShowField);
            addRenderableWidget(LavaWidgets.styled(px + w - showBtnW, notebookShowY(), showBtnW, UiTheme.ROW_H,
                    Component.translatable("lavamenu.notebook.show"),
                    LavaWidgets.BtnStyle.SECONDARY, this::shareNotebook));
        }

        List<NotebookEntry> list = AstoriaNotebookStore.get().entries();
        int top = notebookListTop(), bottom = notebookListBottom();
        int y = top - notebookScroll * notebookStep();
        for (NotebookEntry entry : list) {
            if (!MenuPanel.rowInside(y, NOTEBOOK_ROW_H, top, bottom)) {
                y += notebookStep();
                continue;
            }
            if (!edit) {
                y += notebookStep();
                continue;
            }
            String nick = entry.nick;
            int bx = px + w - 34;
            addRenderableWidget(LavaWidgets.icon(bx, y + (NOTEBOOK_ROW_H - UiTheme.ICON_BTN) / 2, GuiIcons.EDIT, () ->
                    Minecraft.getInstance().setScreen(new RenameEntryScreen(this,
                            Component.translatable("lavamenu.notebook.edit_reason"),
                            Component.translatable("lavamenu.notebook.reason_hint"),
                            entry.reason, neu -> {
                                AstoriaNotebookStore.get().setReason(nick, neu);
                                rebuildWidgets();
                            }))));
            addRenderableWidget(LavaWidgets.icon(bx + 17, y + (NOTEBOOK_ROW_H - UiTheme.ICON_BTN) / 2,
                    GuiIcons.TRASH, LavaWidgets.BtnStyle.DANGER, () ->
                            Minecraft.getInstance().setScreen(new ConfirmScreen(this,
                                    Component.translatable("lavamenu.confirm.title"),
                                    Component.translatable("lavamenu.notebook.delete_confirm", nick),
                                    () -> {
                                        AstoriaNotebookStore.get().remove(nick);
                                        rebuildWidgets();
                                    }))));
            y += notebookStep();
        }
    }

    private void addNotebookEntry() {
        String nick = notebookNickField == null ? "" : notebookNickField.getValue().trim();
        String reason = notebookReasonField == null ? "" : notebookReasonField.getValue().trim();
        if (nick.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.err_nick"));
            return;
        }
        AstoriaNotebookStore.get().add(nick, reason);
        notebookNickDraft = "";
        notebookReasonDraft = "";
        if (notebookNickField != null) notebookNickField.setValue("");
        if (notebookReasonField != null) notebookReasonField.setValue("");
        UiFeedback.actionBar(Component.translatable("lavamenu.notebook.added", nick));
        rebuildWidgets();
    }

    private void shareNotebook() {
        String nick = notebookShowField == null ? "" : notebookShowField.getValue().trim();
        if (nick.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.err_show_nick"));
            return;
        }
        if (AstoriaNotebookStore.get().entries().isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.notebook.err_empty"));
            return;
        }
        List<String> lines = NotebookShare.encode(
                AstoriaNotebookStore.get().entries(),
                NotebookAccess.actorName());
        NotebookShare.sendTo(nick, lines);
        // успех — после последнего /msg внутри NotebookShare
    }

    // ==================== SETTINGS ====================

    private void initSettings() {
        int px = innerX(), w = innerW();
        ModUpdateService upd = ModUpdateService.get();
        upd.setUiListener(this::rebuildWidgets);

        radialModeToggle = LavaWidgets.toggle(px + w - UiTheme.TOGGLE_W, settingsModeY() + 2,
                LavaMenuConfig.get().radial.mode() == LavaMenuConfig.RadialMode.HOLD, on -> {
                    LavaMenuConfig.get().radial.setMode(on ? LavaMenuConfig.RadialMode.HOLD : LavaMenuConfig.RadialMode.TOGGLE);
                    LavaMenuConfig.get().save();
                });
        addRenderableWidget(radialModeToggle);

        addRenderableWidget(LavaWidgets.toggle(px + w - UiTheme.TOGGLE_W, settingsNotifyY() + 1,
                LavaMenuConfig.get().chatsNotify, on -> {
                    LavaMenuConfig.get().chatsNotify = on;
                    LavaMenuConfig.get().save();
                }));

        ChatNotifySound sound = LavaMenuConfig.get().chatsNotifySound;
        if (sound == null) sound = ChatNotifySound.CHIME;
        final ChatNotifySound soundBtn = sound;
        addRenderableWidget(LavaWidgets.styled(px + w - 88, settingsSoundY(), 88, UiTheme.ROW_H,
                soundBtn.label(), LavaWidgets.BtnStyle.SECONDARY, () -> {
                    ChatNotifySound cur = LavaMenuConfig.get().chatsNotifySound;
                    if (cur == null) cur = ChatNotifySound.CHIME;
                    ChatNotifySound next = cur.next();
                    LavaMenuConfig.get().chatsNotifySound = next;
                    LavaMenuConfig.get().save();
                    next.play();
                    rebuildWidgets();
                }));

        int half = (w - UiTheme.ROW_GAP) / 2;
        int by = settingsUpdateBtnY();
        boolean canInstall = upd.updateAvailable() || upd.needsRestart();
        addRenderableWidget(LavaWidgets.styled(px, by, half, UiTheme.ROW_H,
                Component.translatable("lavamenu.update.check"),
                LavaWidgets.BtnStyle.SECONDARY,
                () -> ModUpdateService.get().checkAsync(true)));
        addRenderableWidget(LavaWidgets.styled(px + half + UiTheme.ROW_GAP, by, half, UiTheme.ROW_H,
                Component.translatable("lavamenu.update.install"),
                canInstall && !upd.needsRestart() ? LavaWidgets.BtnStyle.PRIMARY : LavaWidgets.BtnStyle.SECONDARY,
                () -> ModUpdateService.get().installAsync()));

        var radial = LavaMenuConfig.get().radial;
        radial.ensureDefaults();
        radial.setEnabled(true);

        int top = settingsSlotsListTop(), bottom = settingsSlotsBottom();
        int y = top - radialScroll * step();
        for (int i = 0; i < 8; i++) {
            if (!MenuPanel.rowInside(y, UiTheme.ROW_H, top, bottom)) { y += step(); continue; }
            int idx = i;
            RadialAction action = radial.slots.get(idx);
            boolean vis = radial.isSlotVisible(idx);
            addRenderableWidget(LavaWidgets.icon(px, y, vis ? GuiIcons.CHECK : GuiIcons.STAR, () -> {
                radial.toggleSlotVisible(idx);
                LavaMenuConfig.get().save();
                rebuildWidgets();
            }));
            addRenderableWidget(LavaWidgets.cmdRow(px + 18, y, w - 18, UiTheme.ROW_H,
                    RadialMenuScreen.iconFor(action),
                    Component.literal((idx + 1) + ". ").append(action.label()),
                    () -> cycleRadialSlot(idx)));
            y += step();
        }
    }

    // ==================== helpers ====================

    private Component overwriteLabel() {
        return homeOverwrite
                ? Component.translatable("lavamenu.homes.overwrite_on")
                : Component.translatable("lavamenu.homes.overwrite");
    }

    private void refreshHomes() {
        HomesParser.armCapture(120);
        if (CommandHelper.sendFromUi("homes")) {
            UiFeedback.actionBar(Component.translatable("lavamenu.homes.refresh_sent"));
        }
    }

    private void setHome() {
        String name = homeNameField.getValue().trim();
        if (name.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.homes.err_empty"));
            return;
        }
        if (!homeOverwrite && HomesData.get().isFull()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.homes.err_full", HomesData.get().max()));
            return;
        }
        if (CommandHelper.sendFromUi("sethome " + name + (homeOverwrite ? "!" : ""))) {
            UiFeedback.actionBar(Component.translatable("lavamenu.homes.set_sent", name));
        }
    }

    private void teleportHome(String name) {
        CommandHelper.closeAndSend("home " + name);
        LavaMenuConfig.get().homes.lastUsed = name;
        LavaMenuConfig.get().save();
    }

    private void addFriend() {
        String label = friendLabelField.getValue().trim();
        String nick = friendNickField.getValue().trim();
        if (label.isEmpty() || nick.isEmpty()) {
            UiFeedback.actionBar(Component.translatable("lavamenu.friends.err_empty"));
            return;
        }
        var fe = new LavaMenuConfig.FriendEntry();
        fe.label = label;
        fe.nick = nick;
        LavaMenuConfig.get().friends.add(fe);
        LavaMenuConfig.get().save();
        friendLabelDraft = "";
        friendNickDraft = "";
        friendLabelField.setValue("");
        friendNickField.setValue("");
        rebuildWidgets();
    }

    private void cycleRadialSlot(int idx) {
        var slots = LavaMenuConfig.get().radial.slots;
        RadialAction cur = slots.get(idx);
        RadialAction[] all = RadialAction.selectable();
        int pos = 0;
        for (int i = 0; i < all.length; i++) if (all[i] == cur) { pos = i; break; }
        slots.set(idx, all[(pos + 1) % all.length]);
        LavaMenuConfig.get().save();
        rebuildWidgets();
    }

    private int homesContentHeight() {
        int h = 0;
        for (Map.Entry<String, List<String>> e : HomesData.get().dimensions().entrySet()) {
            h += DIM_HEADER_H;
            h += e.getValue().size() * step();
        }
        return h;
    }

    private int maxHomesScrollPx() {
        return Math.max(0, homesContentHeight() - (homesListBottom() - homesListTop()));
    }

    // ==================== render ====================

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawBackdrop(gfx, width, height);
        MenuPanel.drawFrame(gfx, font, title, box[0], box[1], box[2], box[3]);

        switch (tab) {
            case HOMES -> drawHomesOverlay(gfx, mouseX, mouseY);
            case COMMANDS -> drawCommandsOverlay(gfx);
            case FRIENDS -> drawFriendsOverlay(gfx, mouseX, mouseY);
            case CHATS -> drawChatsOverlay(gfx, mouseX, mouseY);
            case NOTEBOOK -> drawNotebookOverlay(gfx, mouseX, mouseY);
            case SETTINGS -> drawSettingsOverlay(gfx);
        }

        super.extractRenderState(gfx, mouseX, mouseY, delta);

        if (tab == Tab.SETTINGS) drawSettingsSlotDim(gfx);

        Component credits = Component.translatable("lavamenu.credits", modVersion());
        int tw = font.width(credits);
        // Справа внизу под панелью, очень тускло
        gfx.text(font, credits,
                box[0] + box[2] - tw - UiTheme.PAD,
                box[1] + box[3] + 6,
                0xFF454545, false);
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(LavaMenuClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.1.0");
    }

    private void drawSettingsSlotDim(GuiGraphicsExtractor gfx) {
        var radial = LavaMenuConfig.get().radial;
        int x = innerX(), w = innerW();
        int top = settingsSlotsListTop(), bottom = settingsSlotsBottom();
        int y = top - radialScroll * step();
        for (int i = 0; i < 8; i++) {
            if (MenuPanel.rowVisible(y, UiTheme.ROW_H, top, bottom) && !radial.isSlotVisible(i)) {
                gfx.fill(x, y, x + w, y + UiTheme.ROW_H, 0x88000000);
            }
            y += step();
        }
    }

    private void drawHomesOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x = innerX(), w = innerW();
        var data = HomesData.get();

        MenuPanel.drawScrollCap(gfx, x, innerY(), w, homesFormBottom() - innerY());
        MenuPanel.drawDivider(gfx, x, homesListTop() - 3, w);
        gfx.text(font, Component.translatable("lavamenu.homes.list_title", data.count(), data.max()),
                x, homesListTop() - 10, UiTheme.TEXT_PRIMARY, false);

        if (data.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.homes.empty_hint"), x, homesListTop() + 2, UiTheme.TEXT_DIM, false);
            return;
        }

        int top = homesListTop(), bottom = homesListBottom();
        MenuPanel.withScissor(gfx, x, top, w, bottom - top, () -> {
            int y = top - homesScrollPx;
            for (Map.Entry<String, List<String>> e : data.dimensions().entrySet()) {
                if (y > bottom) return;
                y += DIM_HEADER_H;
                for (String ignored : e.getValue()) {
                    if (MenuPanel.rowVisible(y, UiTheme.ROW_H, top, bottom)
                            && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + UiTheme.ROW_H) {
                        MenuPanel.drawRowHover(gfx, x, y, w, UiTheme.ROW_H);
                    }
                    y += step();
                }
            }
        });
    }

    private void drawCommandsOverlay(GuiGraphicsExtractor gfx) {
        int x = innerX(), w = innerW();
        MenuPanel.drawSection(gfx, font, Component.translatable("lavamenu.cmd.section_server"), x, cmdServerY() - 9);
        MenuPanel.drawSection(gfx, font, Component.translatable("lavamenu.cmd.section_anim"), x, cmdAnimY() - 9);
        MenuPanel.drawSection(gfx, font, Component.translatable("lavamenu.cmd.section_territory"), x, cmdTerritoryY() - 9);

        GuiIcons.SHIELD.drawInBox(gfx, x, cmdTerritoryY(), UiTheme.ICON_BTN, UiTheme.TEXT_PRIMARY);
        gfx.text(font, Component.translatable("lavamenu.cmd.area"), x + 18, cmdTerritoryY() + 4, UiTheme.TEXT_PRIMARY, false);
        gfx.text(font, Component.translatable("lavamenu.cmd.area_wip"), x + w - 76, cmdTerritoryY() + 4, UiTheme.TEXT_MUTED, false);

        GuiIcons.SWORD.drawInBox(gfx, x, cmdPvpY(), UiTheme.ICON_BTN, UiTheme.TEXT_PRIMARY);
        gfx.text(font, Component.translatable("lavamenu.cmd.pvp"), x + 18, cmdPvpY() + 4, UiTheme.TEXT_PRIMARY, false);
        Boolean tabPvp = PvpStatus.readFromTab();
        Component status = tabPvp == null
                ? Component.translatable("lavamenu.cmd.pvp_unknown")
                : Component.translatable(tabPvp ? "lavamenu.cmd.pvp_on" : "lavamenu.cmd.pvp_off");
        int statusColor = tabPvp == null ? UiTheme.TEXT_DIM : (tabPvp ? UiTheme.ONLINE : UiTheme.OFFLINE);
        gfx.text(font, status, x + 48, cmdPvpY() + 4, statusColor, false);
    }

    private void drawFriendsOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x = innerX(), w = innerW();
        var configFriends = LavaMenuConfig.get().friends;
        var friends = FriendsListHelper.sortedRows(configFriends);
        int online = FriendsListHelper.countOnline(configFriends);

        MenuPanel.drawScrollCap(gfx, x, innerY(), w, friendsFormBottom() - innerY());
        MenuPanel.drawDivider(gfx, x, friendsListTop() - 3, w);
        gfx.text(font, Component.translatable("lavamenu.friends.list_title_online", online, configFriends.size()),
                x, friendsListTop() - 10, UiTheme.TEXT_PRIMARY, false);

        if (friends.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.friends.empty_hint"), x, friendsListTop() + 2, UiTheme.TEXT_DIM, false);
            return;
        }

        int top = friendsListTop(), bottom = friendsListBottom();
        int face = 12;
        MenuPanel.withScissor(gfx, x, top, w, bottom - top, () -> {
            int y = top - friendsScroll * step();
            for (FriendsListHelper.Row row : friends) {
                if (y > bottom) return;
                if (!MenuPanel.rowVisible(y, UiTheme.ROW_H, top, bottom)) { y += step(); continue; }
                if (mouseX >= x && mouseX < x + w - 68 && mouseY >= y && mouseY < y + UiTheme.ROW_H) {
                    MenuPanel.drawRowHover(gfx, x, y, w - 68, UiTheme.ROW_H);
                }
                LavaMenuConfig.FriendEntry fe = row.entry();
                PlayerFaces.draw(gfx, font, fe.nick, x + 1, y + (UiTheme.ROW_H - face) / 2, face);
                int textColor = row.online() ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_MUTED;
                gfx.text(font, Component.literal(fe.label + " · " + fe.nick),
                        x + 1 + face + 4, y + 4, textColor, false);
                y += step();
            }
        });
    }

    private void drawChatsOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x = innerX(), w = innerW();
        List<ChatThread> threads = ChatStore.get().threadsNewestFirst();
        MenuPanel.drawScrollCap(gfx, x, innerY(), w, chatsFormBottom() - innerY());
        MenuPanel.drawDivider(gfx, x, chatsListTop() - 3, w);
        int unreadTotal = ChatStore.get().totalUnread();
        Component title = unreadTotal > 0
                ? Component.translatable("lavamenu.chats.list_title_unread", threads.size(), unreadTotal)
                : Component.translatable("lavamenu.chats.list_title", threads.size());
        gfx.text(font, title, x, chatsListTop() - 10, UiTheme.TEXT_PRIMARY, false);

        if (threads.isEmpty()) {
            gfx.text(font, Component.translatable("lavamenu.chats.empty_hint"),
                    x, chatsListTop() + 2, UiTheme.TEXT_DIM, false);
            return;
        }

        int top = chatsListTop(), bottom = chatsListBottom();
        int trashCol = x + w - UiTheme.ICON_BTN;
        // колонка времени слева от корзины
        int metaRight = trashCol - 4;
        MenuPanel.withScissor(gfx, x, top, w, bottom - top, () -> {
            int y = top - chatsScroll * chatStep();
            for (ChatThread thread : threads) {
                if (y > bottom) return;
                if (!MenuPanel.rowVisible(y, CHAT_ROW_H, top, bottom)) {
                    y += chatStep();
                    continue;
                }
                boolean online = OnlinePlayers.isOnline(thread.nick);
                int hitW = trashCol - x - 2;
                if (mouseX >= x && mouseX < trashCol - 2 && mouseY >= y && mouseY < y + CHAT_ROW_H) {
                    MenuPanel.drawRowHover(gfx, x, y, hitW, CHAT_ROW_H);
                }

                int nickY = y + 4;
                int previewY = y + 17;
                int face = PlayerFaces.SIZE;
                int faceY = y + (CHAT_ROW_H - face) / 2;
                PlayerFaces.draw(gfx, font, thread.nick, x + 1, faceY, face);

                ChatMessage last = thread.lastMessage();
                String time = last == null ? "" : ChatTimeFormat.listTime(last.timeMs, last.clock);
                int timeW = time.isEmpty() ? 0 : font.width(time);
                int timeX = metaRight - timeW;
                if (!time.isEmpty()) {
                    gfx.text(font, Component.literal(time), timeX, nickY, UiTheme.TEXT_DIM, false);
                }

                int nickX = x + 1 + face + 4;
                int nickMax = Math.max(12, timeX - 8 - nickX);
                String nickLabel = thread.nick;
                if (thread.unread > 0) {
                    String badge = thread.unread > 99 ? "99+" : String.valueOf(thread.unread);
                    nickLabel = thread.nick + " · " + badge;
                }
                nickLabel = ellipsize(nickLabel, nickMax);
                int nickColor = thread.unread > 0
                        ? UiTheme.ACCENT
                        : (online ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_MUTED);
                gfx.text(font, Component.literal(nickLabel), nickX, nickY, nickColor, false);

                String preview = "";
                if (last != null) {
                    if (last.outgoing) {
                        preview = Component.translatable("lavamenu.chats.you").getString() + ": " + last.text;
                    } else {
                        preview = last.text;
                    }
                }
                int previewMax = Math.max(12, metaRight - nickX);
                preview = ellipsize(preview, previewMax);
                if (!preview.isEmpty()) {
                    gfx.text(font, Component.literal(preview), nickX, previewY, UiTheme.TEXT_DIM, false);
                }
                y += chatStep();
            }
        });
    }

    private void drawNotebookOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x = innerX(), w = innerW();
        MenuPanel.drawScrollCap(gfx, x, innerY(), w, Math.max(0, notebookListTop() - 4 - innerY()));

        // Заголовок — всегда сверху, яркий, не пересекается с полями
        gfx.text(font, Component.translatable("lavamenu.notebook.title"),
                x, notebookTitleY(), UiTheme.ACCENT, false);

        if (!NotebookAccess.canEdit()) {
            String from = AstoriaNotebookStore.get().sharedFrom();
            if (from != null && !from.isBlank() && !from.equals("?")) {
                gfx.text(font, Component.translatable("lavamenu.notebook.from", from),
                        x, notebookTitleY() + 12, UiTheme.TEXT_MUTED, false);
            } else {
                gfx.text(font, Component.translatable("lavamenu.notebook.readonly_hint"),
                        x, notebookTitleY() + 12, UiTheme.TEXT_MUTED, false);
            }
        }

        MenuPanel.drawDivider(gfx, x, notebookListTop() - 5, w);

        List<NotebookEntry> list = AstoriaNotebookStore.get().entries();
        if (list.isEmpty()) {
            String emptyKey;
            if (NotebookAccess.canEdit()) {
                emptyKey = "lavamenu.notebook.empty";
            } else if (AstoriaNotebookStore.get().sharedFrom().isBlank()) {
                emptyKey = "lavamenu.notebook.empty_view";
            } else {
                emptyKey = "lavamenu.notebook.empty_shown";
            }
            gfx.text(font, Component.translatable(emptyKey),
                    x, notebookListTop() + 4, UiTheme.TEXT_MUTED, false);
            return;
        }

        int top = notebookListTop(), bottom = notebookListBottom();
        int actionsW = NotebookAccess.canEdit() ? 34 : 0;
        MenuPanel.withScissor(gfx, x, top, w, bottom - top, () -> {
            int y = top - notebookScroll * notebookStep();
            for (NotebookEntry entry : list) {
                if (y > bottom) return;
                if (!MenuPanel.rowVisible(y, NOTEBOOK_ROW_H, top, bottom)) {
                    y += notebookStep();
                    continue;
                }
                if (mouseX >= x && mouseX < x + w - actionsW && mouseY >= y && mouseY < y + NOTEBOOK_ROW_H) {
                    MenuPanel.drawRowHover(gfx, x, y, w - actionsW, NOTEBOOK_ROW_H);
                }
                PlayerFaces.draw(gfx, font, entry.nick, x + 1, y + (NOTEBOOK_ROW_H - 12) / 2, 12);
                String reason = entry.reason.isBlank()
                        ? Component.translatable("lavamenu.notebook.no_reason").getString()
                        : entry.reason;
                gfx.text(font, Component.literal(entry.nick), x + 18, y + 2, UiTheme.TEXT_PRIMARY, false);
                gfx.text(font, Component.literal(ellipsize(reason, w - actionsW - 22)),
                        x + 18, y + 14, UiTheme.TEXT_MUTED, false);
                y += notebookStep();
            }
        });
    }

    private void drawSettingsOverlay(GuiGraphicsExtractor gfx) {
        int x = innerX(), py = innerY(), w = innerW();
        MenuPanel.drawScrollCap(gfx, x, innerY(), w, settingsSlotsTop() - innerY());
        MenuPanel.drawSection(gfx, font, Component.translatable("lavamenu.settings.section_keys"), x, py - 9);
        gfx.text(font, Component.translatable("lavamenu.radial.mode_label"), x, settingsModeY() + 2, UiTheme.TEXT_PRIMARY, false);
        gfx.text(font, Component.translatable("lavamenu.radial.controls_hint"), x, settingsHintY(), UiTheme.TEXT_DIM, false);
        gfx.text(font, Component.translatable("lavamenu.chats.notify"), x, settingsNotifyY() + 2, UiTheme.TEXT_PRIMARY, false);
        gfx.text(font, Component.translatable("lavamenu.chats.notify_sound"), x, settingsSoundY() + 2, UiTheme.TEXT_PRIMARY, false);
        gfx.text(font, ModUpdateService.get().statusLabel(), x, settingsUpdateY(),
                ModUpdateService.get().updateAvailable() || ModUpdateService.get().needsRestart()
                        ? UiTheme.WORLD_GREEN : UiTheme.TEXT_DIM, false);
        MenuPanel.drawSection(gfx, font, Component.translatable("lavamenu.radial.slots_title"), x, settingsSlotsTop() - 9);
        gfx.text(font, Component.translatable("lavamenu.radial.slots_hint"), x, settingsSlotsTop(), UiTheme.TEXT_DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.HOMES && !HomesData.get().isEmpty()
                && MenuPanel.inRect(mouseX, mouseY, innerX(), homesListTop(), innerW(), homesListBottom() - homesListTop())) {
            homesScrollPx = Math.max(0, Math.min(maxHomesScrollPx(), homesScrollPx - (int) (scrollY * step())));
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.FRIENDS && !LavaMenuConfig.get().friends.isEmpty()
                && MenuPanel.inRect(mouseX, mouseY, innerX(), friendsListTop(), innerW(), friendsListBottom() - friendsListTop())) {
            int vis = Math.max(1, (friendsListBottom() - friendsListTop()) / step());
            int max = Math.max(0, LavaMenuConfig.get().friends.size() - vis + 1);
            friendsScroll = Math.max(0, Math.min(max, friendsScroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.CHATS && !ChatStore.get().threadsNewestFirst().isEmpty()
                && MenuPanel.inRect(mouseX, mouseY, innerX(), chatsListTop(), innerW(),
                chatsListBottom() - chatsListTop())) {
            int vis = Math.max(1, (chatsListBottom() - chatsListTop()) / chatStep());
            int max = Math.max(0, ChatStore.get().threadsNewestFirst().size() - vis + 1);
            chatsScroll = Math.max(0, Math.min(max, chatsScroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.NOTEBOOK && !AstoriaNotebookStore.get().entries().isEmpty()
                && MenuPanel.inRect(mouseX, mouseY, innerX(), notebookListTop(), innerW(),
                notebookListBottom() - notebookListTop())) {
            int vis = Math.max(1, (notebookListBottom() - notebookListTop()) / notebookStep());
            int max = Math.max(0, AstoriaNotebookStore.get().size() - vis + 1);
            notebookScroll = Math.max(0, Math.min(max, notebookScroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.SETTINGS
                && MenuPanel.inRect(mouseX, mouseY, innerX(), settingsSlotsListTop(), innerW(),
                settingsSlotsBottom() - settingsSlotsListTop())) {
            int vis = Math.max(1, (settingsSlotsBottom() - settingsSlotsListTop()) / step());
            int max = Math.max(0, 8 - vis + 1);
            radialScroll = Math.max(0, Math.min(max, radialScroll - (int) scrollY));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

}
