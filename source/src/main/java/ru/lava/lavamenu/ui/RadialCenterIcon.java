package ru.lava.lavamenu.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import ru.lava.lavamenu.LavaMenuClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Центральная иконка быстрого меню: из мода или config/lavamenu/radial_center.png */
public final class RadialCenterIcon {
    private static final Identifier DEFAULT_RESOURCE = Identifier.fromNamespaceAndPath("lavamenu", "textures/gui/radial_center.png");
    private static final Identifier BAKED = Identifier.fromNamespaceAndPath("lavamenu", "dynamic/radial_center_baked");
    private static final Identifier CUSTOM = Identifier.fromNamespaceAndPath("lavamenu", "dynamic/radial_center");

    private static Identifier current = DEFAULT_RESOURCE;
    private static long customModified = -1;
    private static boolean bakedDefault;
    private static int texW = 64;
    private static int texH = 64;

    private RadialCenterIcon() {}

    private static void setTexSize(NativeImage img) {
        texW = Math.max(1, img.getWidth());
        texH = Math.max(1, img.getHeight());
    }

    private static void ensureBakedDefault(Minecraft mc) {
        if (bakedDefault) return;
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(DEFAULT_RESOURCE);
            if (res.isEmpty()) return;
            try (InputStream in = res.get().open()) {
                NativeImage img = NativeImage.read(in);
                setTexSize(img);
                DynamicTexture tex = new DynamicTexture(() -> "lavamenu radial center baked", img);
                mc.getTextureManager().register(BAKED, tex);
                bakedDefault = true;
                if (current == DEFAULT_RESOURCE) {
                    current = BAKED;
                }
            }
        } catch (IOException e) {
            LavaMenuClient.LOGGER.debug("RadialCenterIcon bake default failed: {}", e.toString());
        }
    }

    public static void refresh(Minecraft mc) {
        ensureBakedDefault(mc);

        Path custom = FabricLoader.getInstance().getConfigDir().resolve("lavamenu/radial_center.png");
        if (!Files.exists(custom)) {
            current = bakedDefault ? BAKED : DEFAULT_RESOURCE;
            customModified = -1;
            return;
        }
        try {
            long mod = Files.getLastModifiedTime(custom).toMillis();
            if (mod == customModified && mc.getTextureManager().getTexture(CUSTOM) != null) {
                current = CUSTOM;
                return;
            }
            customModified = mod;
            try (InputStream in = Files.newInputStream(custom)) {
                NativeImage img = NativeImage.read(in);
                setTexSize(img);
                DynamicTexture tex = new DynamicTexture(() -> "lavamenu radial center custom", img);
                mc.getTextureManager().register(CUSTOM, tex);
                current = CUSTOM;
            }
        } catch (IOException e) {
            LavaMenuClient.LOGGER.warn("RadialCenterIcon custom load failed: {}", e.toString());
            current = bakedDefault ? BAKED : DEFAULT_RESOURCE;
        }
    }

    /** Рисует иконку в квадрате [x,y] размера size (для тостов и т.п.). */
    public static void drawAt(GuiGraphicsExtractor gfx, Minecraft mc, int x, int y, int size) {
        refresh(mc);
        gfx.blit(RenderPipelines.GUI_TEXTURED, current,
                x, y, 0, 0,
                size, size, texW, texH, texW, texH);
    }

    public static void draw(GuiGraphicsExtractor gfx, Minecraft mc, int cx, int cy, int size) {
        refresh(mc);
        int half = size / 2;
        int pad = 2;
        gfx.fill(cx - half - pad, cy - half - pad, cx + half + pad, cy + half + pad, 0xE0181818);
        drawAt(gfx, mc, cx - half, cy - half, size);
    }
}
