package ru.lava.lavamenu.homes;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HomesData {
    private static final HomesData INSTANCE = new HomesData();

    private int count = 0;
    private int max = 20;
    private final Map<String, List<String>> byDimension = new LinkedHashMap<>();
    private Runnable changeListener = () -> {};

    public static HomesData get() {
        return INSTANCE;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener != null ? listener : () -> {};
    }

    private void notifyChanged() {
        changeListener.run();
    }

    public void clear() {
        count = 0;
        max = 20;
        byDimension.clear();
        notifyChanged();
    }

    public void setCount(int count, int max) {
        this.count = count;
        this.max = max;
        notifyChanged();
    }

    public int count() {
        return count;
    }

    public int max() {
        return max;
    }

    public boolean isEmpty() {
        return byDimension.isEmpty() || allNames().isEmpty();
    }

    public Map<String, List<String>> dimensions() {
        return Collections.unmodifiableMap(byDimension);
    }

    public void putDimension(String dim, List<String> names) {
        byDimension.put(dim, new ArrayList<>(names));
        notifyChanged();
    }

    public List<String> allNames() {
        List<String> all = new ArrayList<>();
        for (List<String> names : byDimension.values()) all.addAll(names);
        return all;
    }

    public boolean hasNameExact(String name) {
        if (name == null || name.isBlank()) return false;
        for (String n : allNames()) {
            if (n != null && n.equals(name)) return true;
        }
        return false;
    }

    public boolean hasNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) return false;
        for (String n : allNames()) {
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean isFull() {
        return count >= max;
    }
}
