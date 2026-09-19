package com.jelly.farmhelperv3.util;

import net.minecraft.client.Minecraft;
import java.util.concurrent.*;

public final class Tasks {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "FarmHelper timer");
        thread.setDaemon(true);
        return thread;
    });
    public static ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
        return TIMER.schedule(() -> Minecraft.getInstance().execute(action), delay, unit);
    }
    public static ScheduledFuture<?> background(Runnable action, long delay, TimeUnit unit) {
        return TIMER.schedule(() -> Thread.ofVirtual().start(action), delay, unit);
    }
    public static void shutdown() { TIMER.shutdownNow(); }
}
