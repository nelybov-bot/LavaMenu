package ru.lava.lavamenu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Универсальный экран переименования (точки, друзья, …). */
public final class RenameEntryScreen extends Screen {
    private final Screen parent;
    private final Component hint;
    private final String initial;
    private final Consumer<String> onApply;
    private EditBox field;

    public RenameEntryScreen(Screen parent, Component title, Component hint, String initial, Consumer<String> onApply) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.initial = initial;
        this.onApply = onApply;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        field = new EditBox(font, cx - 110, cy - 4, 220, 20, Component.empty());
        field.setMaxLength(48);
        field.setValue(initial == null ? "" : initial);
        field.setHint(hint);
        addRenderableWidget(field);
        addRenderableWidget(LavaWidgets.styled(cx - 110, cy + 22, 106, 22,
                Component.translatable("lavamenu.common.apply"), LavaWidgets.BtnStyle.PRIMARY, this::apply));
        addRenderableWidget(LavaWidgets.styled(cx + 4, cy + 22, 106, 22,
                Component.translatable("lavamenu.common.cancel"), LavaWidgets.BtnStyle.SECONDARY,
                () -> Minecraft.getInstance().gui.setScreen(parent)));
    }

    private void apply() {
        String v = field.getValue().trim();
        if (!v.isEmpty()) onApply.accept(v);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        MenuPanel.drawDialog(gfx, font, title, hint, width, height, 260, 110);
        super.extractRenderState(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            apply();
            return true;
        }
        return super.keyPressed(event);
    }
}
