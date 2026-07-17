package ru.lava.lavamenu.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Иконки из атласа {@code textures/gui/icons.png} (256×256, ячейки 16×16).
 */
public enum GuiIcons {
    STAR(0),
    STAR_FILLED(1),
    EDIT(2),
    TRASH(3),
    PLUS(4),
    REFRESH(5),
    SEND(6),
    USERS(7),
    MAP_PIN(8),
    SETTINGS(9),
    TERMINAL(10),
    GAVEL(11),
    SHOP(12),
    ARMCHAIR(13),
    BED(14),
    SHIELD(15),
    SWORD(16),
    GRIP(17),
    CHECK(18),
    WORLD(19),
    NETHER(20),
    END(21),
    GRID(22);

    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("lavamenu", "textures/gui/icons.png");
    private static final int ATLAS_SIZE = 256;
    private static final int CELL = 16;

    private final int u;
    private final int v;

    GuiIcons(int index) {
        this.u = (index % 16) * CELL;
        this.v = (index / 16) * CELL;
    }

    public void draw(GuiGraphicsExtractor gfx, int x, int y, int color) {
        draw(gfx, x, y, UiTheme.ICON_PX, color);
    }

    /** x,y — экран; u,v — пиксели в атласе; size — размер на экране */
    public void draw(GuiGraphicsExtractor gfx, int x, int y, int size, int color) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, size, size, CELL, CELL, ATLAS_SIZE, ATLAS_SIZE);
    }

    public void drawInBox(GuiGraphicsExtractor gfx, int bx, int by, int box, int size, int color) {
        draw(gfx, bx + (box - size) / 2, by + (box - size) / 2, size, color);
    }

    public void drawInBox(GuiGraphicsExtractor gfx, int bx, int by, int box, int color) {
        drawInBox(gfx, bx, by, box, UiTheme.ICON_PX, color);
    }

    public static GuiIcons forDimension(String dim) {
        String d = dim == null ? "" : dim.replace('ё', 'е').replace('Ё', 'Е').toLowerCase();
        if (d.contains("край") || d.contains("the end") || d.equals("end")) return END;
        // «ад» как отдельное слово / Нижний мир — не путать с «край»
        if (d.equals("ад") || d.contains("нижний") || d.contains("nether")) return NETHER;
        return WORLD;
    }

    public static int colorForDimension(String dim) {
        return switch (forDimension(dim)) {
            case END -> UiTheme.END_PURPLE;
            case NETHER -> UiTheme.NETHER_ORANGE;
            default -> UiTheme.WORLD_GREEN;
        };
    }

    public static String initials(String label) {
        if (label == null || label.isBlank()) return "?";
        String[] parts = label.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return label.length() >= 2
                ? label.substring(0, 2).toUpperCase()
                : label.toUpperCase();
    }
}
