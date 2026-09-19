package com.jelly.farmhelperv3.event;

import com.jelly.farmhelperv3.event.Events.Event;

import java.util.List;

public class UpdateScoreboardListEvent extends Event {
    public final List<String> scoreboardLines;
    public final List<String> cleanScoreboardLines;
    public final long timestamp;

    public UpdateScoreboardListEvent(List<String> scoreboardLines, List<String> cleanScoreboardLines, long timestamp) {
        this.scoreboardLines = scoreboardLines;
        this.cleanScoreboardLines = cleanScoreboardLines;
        this.timestamp = timestamp;
    }
}
