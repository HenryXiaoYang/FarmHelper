package com.jelly.farmhelperv3.event;

import lombok.Getter;
import com.jelly.farmhelperv3.event.Events.Event;

@Getter
public class UpdateScoreboardLineEvent extends Event {
    private final String line;

    public UpdateScoreboardLineEvent(String line) {
        this.line = line;
    }

}
