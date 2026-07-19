package ru.lava.lavamenu.radial;

import net.minecraft.client.Minecraft;
import ru.lava.lavamenu.config.LavaMenuConfig;
import ru.lava.lavamenu.config.RadialAction;
import ru.lava.lavamenu.ui.FavoriteHomesQuickScreen;
import ru.lava.lavamenu.ui.FriendsQuickScreen;
import ru.lava.lavamenu.ui.HomesQuickScreen;
import ru.lava.lavamenu.ui.LavaMenuScreen;
import ru.lava.lavamenu.util.AnimationHelper;
import ru.lava.lavamenu.util.CommandHelper;
import ru.lava.lavamenu.util.PvpStatus;

public final class RadialExecutor {
    private RadialExecutor() {}

    /**
     * @return true — закрыть radial; false — экран уже переключён (меню точек / главное меню)
     */
    public static boolean execute(RadialAction action) {
        if (action == null || !action.isExecutable()) return true;
        Minecraft mc = Minecraft.getInstance();
        switch (action) {
            case OPEN_AH -> CommandHelper.closeAndSend("ah");
            case OPEN_SHOP -> CommandHelper.closeAndSend("warp shop");
            case SIT -> AnimationHelper.closeAndPlay(AnimationHelper.Type.SIT);
            case LAY -> AnimationHelper.closeAndPlay(AnimationHelper.Type.LAY);
            case REFRESH_HOMES -> CommandHelper.closeAndSend("homes");
            case LAST_HOME -> {
                String last = LavaMenuConfig.get().homes.lastUsed;
                if (last != null && !last.isBlank()) {
                    LavaMenuConfig.get().homes.lastUsed = last;
                    LavaMenuConfig.get().save();
                    CommandHelper.closeAndSend("home " + last);
                }
            }
            case OPEN_HOMES -> {
                mc.gui.setScreen(new HomesQuickScreen());
                return false;
            }
            case FRIEND_QUICK -> {
                mc.gui.setScreen(new FriendsQuickScreen());
                return false;
            }
            case OPEN_CHATS -> {
                mc.gui.setScreen(new LavaMenuScreen(LavaMenuScreen.Tab.CHATS));
                return false;
            }
            case OPEN_MENU -> {
                mc.gui.setScreen(new LavaMenuScreen());
                return false;
            }
            case FAVORITE_1 -> {
                var favs = LavaMenuConfig.get().homes.favorites;
                if (favs.size() == 1) {
                    String name = favs.get(0);
                    LavaMenuConfig.get().homes.lastUsed = name;
                    LavaMenuConfig.get().save();
                    CommandHelper.closeAndSend("home " + name);
                } else if (favs.size() > 1) {
                    mc.gui.setScreen(new FavoriteHomesQuickScreen());
                    return false;
                }
            }
            case TOGGLE_PVP -> PvpStatus.toggleViaCommand();
        }
        return true;
    }
}
