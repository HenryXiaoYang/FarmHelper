package com.jelly.farmhelperv3.event;

import com.jelly.farmhelperv3.event.Events.Event;

public class MillisecondEvent extends Event {
    public long timestamp;

    public MillisecondEvent() {
        timestamp = System.currentTimeMillis();
    }
}
