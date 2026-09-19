package com.jelly.farmhelperv3.event;

import com.jelly.farmhelperv3.event.Events.Event;

import java.util.List;

public class UpdateTablistEvent extends Event {
    public final List<String> tablist;
    public final long timestamp;

    public UpdateTablistEvent(List<String> tablist, long timestamp) {
        this.tablist = tablist;
        this.timestamp = timestamp;
    }
}
