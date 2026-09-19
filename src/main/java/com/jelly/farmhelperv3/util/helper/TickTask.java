package com.jelly.farmhelperv3.util.helper;

import com.jelly.farmhelperv3.util.Tasks;
import lombok.Getter;
import lombok.Setter;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import java.util.concurrent.TimeUnit;

@Getter
public class TickTask {
    private static TickTask instance;
    @Setter
    private Runnable task;
    @Setter
    private Runnable callback;

    public static TickTask getInstance() {
        if (instance == null) {
            instance = new TickTask();
        }
        return instance;
    }

    public void schedule(int delay, Runnable task) {
        Tasks.schedule(() -> this.task = task, delay, TimeUnit.MILLISECONDS);
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (task != null) {
            task.run();
            if (callback != null) {
                task = callback;
                callback = null;
            } else {
                task = null;
            }
        }
    }
}
