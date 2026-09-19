package com.jelly.farmhelperv3.event;

import com.jelly.farmhelperv3.event.Events.Event;

import java.util.List;

public class UpdateTablistFooterEvent extends Event {
    public final List<String> footer;

    public UpdateTablistFooterEvent(List<String> footer) {
        this.footer = footer;
    }
}
