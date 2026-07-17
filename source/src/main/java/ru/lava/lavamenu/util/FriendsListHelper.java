package ru.lava.lavamenu.util;

import ru.lava.lavamenu.config.LavaMenuConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FriendsListHelper {
    private FriendsListHelper() {}

    public record Row(int index, LavaMenuConfig.FriendEntry entry, boolean online) {}

    public static List<Row> sortedRows(List<LavaMenuConfig.FriendEntry> friends) {
        Set<String> online = OnlinePlayers.onlineNicksLower();
        List<Row> rows = new ArrayList<>(friends.size());
        for (int i = 0; i < friends.size(); i++) {
            LavaMenuConfig.FriendEntry fe = friends.get(i);
            boolean on = online.contains(fe.nick.toLowerCase(Locale.ROOT));
            rows.add(new Row(i, fe, on));
        }
        rows.sort(Comparator.comparing((Row r) -> !r.online()).thenComparingInt(Row::index));
        return rows;
    }

    public static int countOnline(List<LavaMenuConfig.FriendEntry> friends) {
        Set<String> online = OnlinePlayers.onlineNicksLower();
        int n = 0;
        for (LavaMenuConfig.FriendEntry fe : friends) {
            if (online.contains(fe.nick.toLowerCase(Locale.ROOT))) n++;
        }
        return n;
    }
}
