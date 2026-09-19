package com.jelly.farmhelperv3.event;

import net.minecraft.client.gui.screens.Screen;
import com.jelly.farmhelperv3.event.Events.Event;

public class DrawScreenAfterEvent extends Event {
    public Screen guiScreen;

    public DrawScreenAfterEvent(Screen guiScreen) {
        this.guiScreen = guiScreen;
    }
}
