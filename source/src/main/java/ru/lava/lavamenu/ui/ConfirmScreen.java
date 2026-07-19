package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ConfirmScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;
    private final int boxW;
    private final int boxH;

    public ConfirmScreen(Screen parent, Component title, Component message, Runnable onConfirm) {
        this(parent, title, message, 300, 80, onConfirm);
    }

    public ConfirmScreen(Screen parent, Component title, Component message,
                         int boxW, int boxH, Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
        this.boxW = boxW;
        this.boxH = boxH;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        int btnY = cy + boxH / 2 - 28;
        addRenderableWidget(LavaWidgets.styled(cx - 105, btnY, 100, 22,
                Component.translatable("lavamenu.confirm.yes"), LavaWidgets.BtnStyle.PRIMARY, () -> {
                    onConfirm.run();
                    Minecraft.getInstance().gui.setScreen(parent);
                }));
        addRenderableWidget(LavaWidgets.styled(cx + 5, btnY, 100, 22,
                Component.translatable("lavamenu.confirm.no"), LavaWidgets.BtnStyle.SECONDARY,
                () -> Minecraft.getInstance().gui.setScreen(parent)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawDialog(gfx, font, title, message, width, height, boxW, boxH);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }
}
