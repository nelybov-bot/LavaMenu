package ru.lava.lavamenu.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.lava.lavamenu.LavaMenuClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class KeyBindings {
    public static KeyMapping OPEN_MAIN;
    public static KeyMapping OPEN_RADIAL;
    /** Открыть тост ЛС / тост обновления (когда мышь занята игрой). */
    public static KeyMapping OPEN_REPLY;
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(LavaMenuClient.MOD_ID, "category"));

    private static Field keyField;
    private static Method keyMethod;
    private static boolean keyAccessResolved;

    private KeyBindings() {}

    public static void register() {
        OPEN_MAIN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lavamenu.open_main",
                GLFW.GLFW_KEY_R,
                CATEGORY
        ));
        OPEN_RADIAL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lavamenu.open_radial",
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));
        OPEN_REPLY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lavamenu.toast_reply",
                GLFW.GLFW_KEY_Y,
                CATEGORY
        ));
    }

    public static void applyFromConfig() {
        // Горячие клавиши теперь меняются только через настройки Minecraft -> Управление.
    }

    public static InputConstants.Key keyOf(int glfwKey) {
        if (glfwKey == GLFW.GLFW_KEY_UNKNOWN) {
            return InputConstants.UNKNOWN;
        }
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
    }

    public static String radialKeyName() {
        return OPEN_RADIAL == null ? "G" : OPEN_RADIAL.getTranslatedKeyMessage().getString();
    }

    /**
     * Физическое нажатие текущей клавиши radial (из Minecraft Controls).
     * Нужно для hold-режима: KeyMapping.isDown() сбрасывается при открытом GUI.
     */
    public static boolean isRadialPhysicalDown() {
        return isPhysicalDown(OPEN_RADIAL);
    }

    public static boolean isPhysicalDown(KeyMapping mapping) {
        if (mapping == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        InputConstants.Key bound = boundKey(mapping);
        if (bound == null || bound == InputConstants.UNKNOWN) return false;
        long handle = mc.getWindow().handle();
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        int code = bound.getValue();
        if (code == GLFW.GLFW_KEY_UNKNOWN) return false;
        return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
    }

    private static InputConstants.Key boundKey(KeyMapping mapping) {
        resolveKeyAccess();
        try {
            if (keyMethod != null) {
                Object result = keyMethod.invoke(mapping);
                if (result instanceof InputConstants.Key key) return key;
            }
            if (keyField != null) {
                Object result = keyField.get(mapping);
                if (result instanceof InputConstants.Key key) return key;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
    }

    private static void resolveKeyAccess() {
        if (keyAccessResolved) return;
        keyAccessResolved = true;
        for (String name : new String[]{"getKey", "key"}) {
            try {
                Method m = KeyMapping.class.getMethod(name);
                if (InputConstants.Key.class.isAssignableFrom(m.getReturnType())) {
                    keyMethod = m;
                    return;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (String name : new String[]{"key", "boundKey"}) {
            try {
                Field f = KeyMapping.class.getDeclaredField(name);
                f.setAccessible(true);
                keyField = f;
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
    }
}
