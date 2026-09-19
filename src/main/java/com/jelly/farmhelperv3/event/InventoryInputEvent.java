package com.jelly.farmhelperv3.event;

import lombok.Getter;
import com.jelly.farmhelperv3.event.Events.Event;

@Getter
public class InventoryInputEvent extends Event {
    private final int keyCode;
    private final char typedChar;

    public InventoryInputEvent(int keyCode, char typedChar) {
        this.keyCode = keyCode;
        this.typedChar = typedChar;
    }
}
