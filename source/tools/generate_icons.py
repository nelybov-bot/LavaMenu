#!/usr/bin/env python3
"""Генератор атласа иконок LavaMenu v2 (vanilla-MC bevel)."""
from PIL import Image, ImageDraw
import math
from pathlib import Path

CELL = 16
COLS = 16
ATLAS = 256
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/lavamenu/textures/gui/icons.png"

WHITE = (238, 238, 238)
GOLD = (255, 214, 102)
GREEN = (143, 191, 122)
ORANGE = (200, 90, 40)
PURPLE = (179, 102, 255)
DARK = (24, 24, 24, 255)

ICON_NAMES = [
    "STAR", "STAR_FILLED", "EDIT", "TRASH", "PLUS", "REFRESH", "SEND", "USERS",
    "MAP_PIN", "SETTINGS", "TERMINAL", "GAVEL", "SHOP", "ARMCHAIR", "BED",
    "SHIELD", "SWORD", "GRIP", "CHECK", "WORLD", "NETHER", "END", "GRID",
]


def darken(c, f=0.55):
    return tuple(max(0, int(v * f)) for v in c)


def lighten(c, f=0.4):
    return tuple(min(255, int(v + (255 - v) * f)) for v in c)


def new_cell():
    return Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))


def bevel(img, base):
    """Vanilla-MC style 1px highlight (top-left) / shadow (bottom-right) rim."""
    shade = darken(base) + (255,)
    hi = lighten(base) + (255,)
    src = img.load()
    out = img.copy()
    op = out.load()

    def opaque(x, y):
        return 0 <= x < CELL and 0 <= y < CELL and src[x, y][3] > 0

    for y in range(CELL):
        for x in range(CELL):
            if not opaque(x, y):
                continue
            if src[x, y][:3] != base:
                continue
            edge_br = (not opaque(x + 1, y)) or (not opaque(x, y + 1))
            edge_tl = (not opaque(x - 1, y)) or (not opaque(x, y - 1))
            if edge_br and not edge_tl:
                op[x, y] = shade
            elif edge_tl and not edge_br:
                op[x, y] = hi
    return out


def from_bitmap(rows, color_map):
    img = new_cell()
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "." or ch not in color_map:
                continue
            c = color_map[ch]
            px[x, y] = c if len(c) == 4 else c + (255,)
    return img


def star_points(cx, cy, r_out, r_in, rot=-90):
    pts = []
    for i in range(10):
        ang = math.radians(rot + i * 36)
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    return pts


def icon_star():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.polygon(star_points(8, 8.5, 6.2, 2.6), fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_star_filled():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.polygon(star_points(8, 8.5, 6.2, 2.6), fill=GOLD + (255,))
    return bevel(img, GOLD)


EDIT_ROWS = [
    "................",
    ".............WWW",
    "............WWWD",
    "...........WWWD.",
    "..........WWWD..",
    ".........WWWD...",
    "........WWWD....",
    ".......WWWD.....",
    "......WWWD......",
    ".....WWWD.......",
    "....WWWD........",
    "...WWWD.........",
    "..WWD...........",
    ".DDD............",
    "................",
    "................",
]


def icon_edit():
    img = from_bitmap(EDIT_ROWS, {"W": WHITE, "D": DARK[:3]})
    return bevel(img, WHITE)


def icon_trash():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([6, 2, 9, 3], fill=WHITE + (255,))
    d.rectangle([4, 4, 11, 5], fill=WHITE + (255,))
    d.rectangle([5, 6, 10, 13], fill=WHITE + (255,))
    img2 = bevel(img, WHITE)
    d2 = ImageDraw.Draw(img2)
    for x in (6, 8):
        d2.line([(x, 7), (x, 12)], fill=DARK)
    return img2


def icon_plus():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([7, 3, 9, 13], fill=WHITE + (255,))
    d.rectangle([3, 7, 13, 9], fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_refresh():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.arc([2, 2, 14, 14], start=25, end=290, fill=WHITE + (255,), width=3)
    d.polygon([(11, 1), (15, 3), (10, 6)], fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_send():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.polygon([(2, 3), (14, 8), (2, 13), (6, 8)], fill=WHITE + (255,))
    return bevel(img, WHITE)


USERS_ROWS = [
    "................",
    "....WWW.........",
    "...WWWWW........",
    "...WWWWW....WWW.",
    "...WWWWW...WWWWW",
    "....WWW....WWWWW",
    "..WWWWWWW...WWW.",
    ".WWWWWWWWW......",
    ".WWWWWWWWW.WWWWW",
    ".WWWWWWWWWWWWWWW",
    "...........WWWWW",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def icon_users():
    img = from_bitmap(USERS_ROWS, {"W": WHITE})
    return bevel(img, WHITE)


def icon_map_pin():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.pieslice([3, 1, 13, 11], start=180, end=360, fill=WHITE + (255,))
    d.rectangle([3, 5, 13, 8], fill=WHITE + (255,))
    d.polygon([(3, 8), (13, 8), (8, 15)], fill=WHITE + (255,))
    img2 = bevel(img, WHITE)
    d2 = ImageDraw.Draw(img2)
    d2.ellipse([6, 3, 10, 7], fill=DARK)
    return img2


SETTINGS_ROWS = [
    "......WW........",
    "......WW........",
    "..WW..WW..WW....",
    "..WWW.WW.WWW....",
    "...WWWWWWWW.....",
    "....WWWWWW......",
    "WW..WWWDDWW..WW.",
    "WWW.WWDDDDW.WWW.",
    "WWW.WWDDDDW.WWW.",
    "WW..WWWDDWW..WW.",
    "....WWWWWW......",
    "...WWWWWWWW.....",
    "..WWW.WW.WWW....",
    "..WW..WW..WW....",
    "......WW........",
    "......WW........",
]


def icon_settings():
    img = from_bitmap(SETTINGS_ROWS, {"W": WHITE, "D": DARK[:3]})
    return bevel(img, WHITE)


def icon_terminal():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([2, 3, 13, 11], fill=WHITE + (255,))
    d.rectangle([6, 12, 9, 13], fill=WHITE + (255,))
    img2 = bevel(img, WHITE)
    d2 = ImageDraw.Draw(img2)
    d2.rectangle([3, 4, 12, 10], fill=DARK)
    d2.line([(4, 6), (6, 7.5), (4, 9)], fill=WHITE + (255,), width=1)
    d2.line([(7, 9), (10, 9)], fill=WHITE + (255,), width=1)
    return img2


GAVEL_ROWS = [
    "................",
    "..........WWWWW.",
    ".........WWWWWWW",
    "........WWWWWWW.",
    ".......WWWWWWW..",
    "......WW.WWW....",
    ".....WWW........",
    "....WWW.........",
    "...WWW..........",
    "..WWW...........",
    ".WWD............",
    "WWD.............",
    "................",
    "................",
    "................",
    "................",
]


def icon_gavel():
    img = from_bitmap(GAVEL_ROWS, {"W": WHITE, "D": DARK[:3]})
    return bevel(img, WHITE)


def icon_shop():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.polygon([(2, 6), (8, 2), (14, 6)], fill=WHITE + (255,))
    d.rectangle([3, 6, 13, 14], fill=WHITE + (255,))
    img2 = bevel(img, WHITE)
    d2 = ImageDraw.Draw(img2)
    d2.rectangle([7, 9, 9, 14], fill=DARK)
    return img2


def icon_armchair():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([3, 5, 5, 13], fill=WHITE + (255,))
    d.rectangle([11, 5, 13, 13], fill=WHITE + (255,))
    d.rectangle([3, 8, 13, 11], fill=WHITE + (255,))
    d.rectangle([4, 11, 12, 13], fill=WHITE + (255,))
    d.rectangle([2, 13, 14, 14], fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_bed():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([2, 4, 4, 13], fill=WHITE + (255,))
    d.rectangle([2, 8, 6, 10], fill=WHITE + (255,))
    d.rectangle([2, 11, 14, 13], fill=WHITE + (255,))
    d.rectangle([13, 9, 14, 13], fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_shield():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 2), (13, 4), (13, 8), (8, 14), (3, 8), (3, 4)], fill=WHITE + (255,))
    img2 = bevel(img, WHITE)
    d2 = ImageDraw.Draw(img2)
    d2.polygon([(8, 4.5), (11, 5.8), (11, 8), (8, 12), (5, 8), (5, 5.8)], fill=DARK)
    return img2


SWORD_ROWS = [
    "..............WW",
    ".............WWW",
    "............WWW.",
    "...........WWW..",
    "..........WWW...",
    ".........WWW....",
    "........WWW.....",
    ".......WWW......",
    "......WWWDD.....",
    ".....WWDDDD.....",
    "....WDDWWD......",
    "...WDD.WWD......",
    "..DD....WWD.....",
    "........WWD.....",
    "........WWD.....",
    "................",
]


def icon_sword():
    img = from_bitmap(SWORD_ROWS, {"W": WHITE, "D": DARK[:3]})
    return bevel(img, WHITE)


def icon_grip():
    img = new_cell()
    d = ImageDraw.Draw(img)
    for y in (4, 7, 10):
        d.rectangle([4, y, 11, y + 1], fill=WHITE + (255,))
    return bevel(img, WHITE)


def icon_check():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.line([(3, 8), (6, 12)], fill=WHITE + (255,), width=3)
    d.line([(6, 12), (13, 3)], fill=WHITE + (255,), width=3)
    return bevel(img, WHITE)


def globe(color):
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 13, 13], fill=color + (255,))
    img2 = bevel(img, color)
    d2 = ImageDraw.Draw(img2)
    dark = darken(color, 0.5)
    d2.ellipse([6, 2, 9, 13], outline=dark, width=1)
    d2.line([(2, 7), (13, 7)], fill=dark, width=1)
    return img2


def icon_grid():
    img = new_cell()
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 7, 7], fill=WHITE + (255,))
    d.rectangle([9, 2, 14, 7], fill=WHITE + (255,))
    d.rectangle([2, 9, 7, 14], fill=WHITE + (255,))
    d.rectangle([9, 9, 14, 14], fill=WHITE + (255,))
    return bevel(img, WHITE)


DRAW_FUNCS = [
    icon_star, icon_star_filled, icon_edit, icon_trash, icon_plus, icon_refresh,
    icon_send, icon_users, icon_map_pin, icon_settings, icon_terminal, icon_gavel,
    icon_shop, icon_armchair, icon_bed, icon_shield, icon_sword, icon_grip,
    icon_check, lambda: globe(GREEN), lambda: globe(ORANGE), lambda: globe(PURPLE),
    icon_grid,
]


def build_atlas(path):
    atlas = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    for index, fn in enumerate(DRAW_FUNCS):
        cell = fn()
        col = index % COLS
        row = index // COLS
        atlas.paste(cell, (col * CELL, row * CELL), cell)
    path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(path)
    print(f"saved {path} ({len(DRAW_FUNCS)} icons)")


if __name__ == "__main__":
    build_atlas(OUT)
