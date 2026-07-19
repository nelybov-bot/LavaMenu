package ru.lava.lavamenu.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Отложенные задачи по игровым тикам клиента (не по кадрам {@code mc.execute}).
 */
public final class ClientTickQueue {
    private static final List<Entry> QUEUE = new ArrayList<>();

    private ClientTickQueue() {}

    public static synchronized void schedule(int ticks, Runnable task) {
        if (task == null) return;
        int t = Math.max(0, ticks);
        if (t == 0) {
            task.run();
            return;
        }
        QUEUE.add(new Entry(t, task));
    }

    public static synchronized void tick() {
        if (QUEUE.isEmpty()) return;
        Iterator<Entry> it = QUEUE.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Entry e = it.next();
            e.ticksLeft--;
            if (e.ticksLeft <= 0) {
                it.remove();
                due.add(e.task);
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception ex) {
                ru.lava.lavamenu.LavaMenuClient.LOGGER.warn("ClientTickQueue task failed", ex);
            }
        }
    }

    private static final class Entry {
        int ticksLeft;
        final Runnable task;

        Entry(int ticksLeft, Runnable task) {
            this.ticksLeft = ticksLeft;
            this.task = task;
        }
    }
}
