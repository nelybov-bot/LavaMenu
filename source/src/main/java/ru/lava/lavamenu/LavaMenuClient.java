package ru.lava.lavamenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.lava.lavamenu.chat.ChatStore;
import ru.lava.lavamenu.chat.ChatToastService;
import ru.lava.lavamenu.chat.PmChatListener;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.homes.HomeRenameSession;
import ru.lava.lavamenu.homes.HomesChatListener;
import ru.lava.lavamenu.homes.HomesData;
import ru.lava.lavamenu.homes.HomesParser;
import ru.lava.lavamenu.input.KeyBindings;
import ru.lava.lavamenu.notebook.AstoriaNotebookStore;
import ru.lava.lavamenu.notebook.NotebookShare;
import ru.lava.lavamenu.update.ModUpdateService;
import ru.lava.lavamenu.update.UpdateToastService;
import ru.lava.lavamenu.util.AnimationHelper;
import ru.lava.lavamenu.util.FaceCache;
import ru.lava.lavamenu.util.PvpStatus;
import ru.lava.lavamenu.ui.ChatConversationScreen;
import ru.lava.lavamenu.ui.HomesQuickScreen;
import ru.lava.lavamenu.ui.LavaMenuScreen;
import ru.lava.lavamenu.ui.RadialMenuScreen;

public final class LavaMenuClient implements ClientModInitializer {
    public static final String MOD_ID = "lavamenu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean radialSession = false;
    private static boolean radialKeyWasDown = false;
    private static int pvpSyncTicks = 0;

    @Override
    public void onInitializeClient() {
        LavaMenuConfig.get().load();
        ChatStore.get().load();
        AstoriaNotebookStore.get().load();
        FaceCache.get().ensureLoaded();
        LavaMenuConfig.get().radial.ensureDefaults();
        KeyBindings.register();
        HomesChatListener.register();
        PmChatListener.register();
        ChatToastService.registerHud();
        UpdateToastService.registerHud();
        AnimationHelper.register();
        ModUpdateService.get().cleanupPendingDeletes();

        HomesData.get().setChangeListener(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof LavaMenuScreen screen) {
                mc.execute(screen::onHomesDataChanged);
            } else if (mc.gui.screen() instanceof HomesQuickScreen quick) {
                mc.execute(quick::onHomesDataChanged);
            }
        });

        ChatStore.get().setChangeListener(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.gui.screen() instanceof LavaMenuScreen screen) {
                    screen.onChatsChanged();
                } else if (mc.gui.screen() instanceof ChatConversationScreen conv) {
                    conv.onChatsChanged();
                }
            });
        });

        AstoriaNotebookStore.get().setChangeListener(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.gui.screen() instanceof LavaMenuScreen screen) {
                    screen.onNotebookChanged();
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HomesParser.tick();
            HomeRenameSession.tick();
            NotebookShare.tick();
            ChatToastService.tick();
            UpdateToastService.tick();
            FaceCache.get().tick();

            while (KeyBindings.OPEN_MAIN.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new LavaMenuScreen());
                }
            }

            Minecraft mc = Minecraft.getInstance();
            var mode = LavaMenuConfig.get().radial.mode();
            // Hold: физическая клавиша (isDown() мигает при открытом GUI).
            // Toggle: edge через isDown() достаточно.
            boolean down = mode == LavaMenuConfig.RadialMode.HOLD
                    ? KeyBindings.isRadialPhysicalDown()
                    : KeyBindings.OPEN_RADIAL != null && KeyBindings.OPEN_RADIAL.isDown();
            boolean edge = down && !radialKeyWasDown;

            if (mode == LavaMenuConfig.RadialMode.TOGGLE) {
                if (edge) {
                    if (mc.gui.screen() instanceof RadialMenuScreen) {
                        mc.gui.setScreen(null);
                    } else if (mc.gui.screen() == null) {
                        mc.gui.setScreen(new RadialMenuScreen());
                    }
                }
                radialSession = false;
            } else {
                if (down && !radialSession && mc.gui.screen() == null) {
                    mc.gui.setScreen(new RadialMenuScreen());
                    radialSession = true;
                } else if (radialSession) {
                    if (!down) {
                        if (mc.gui.screen() instanceof RadialMenuScreen radial) {
                            radial.executeHoveredAndClose();
                        } else {
                            mc.gui.setScreen(null);
                        }
                        radialSession = false;
                    }
                }
            }

            radialKeyWasDown = down;

            if (client.player != null && ++pvpSyncTicks >= 40) {
                pvpSyncTicks = 0;
                PvpStatus.syncFromTab();
            }

            ModUpdateService.get().tickHourly();
        });
    }
}
