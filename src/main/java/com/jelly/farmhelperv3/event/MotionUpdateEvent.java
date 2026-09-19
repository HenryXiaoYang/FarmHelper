package com.jelly.farmhelperv3.event;

import com.jelly.farmhelperv3.event.Events.Cancelable;
import com.jelly.farmhelperv3.event.Events.Event;

@Cancelable
public class MotionUpdateEvent extends Event {
    public float yaw;
    public float pitch;

    protected MotionUpdateEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Cancelable
    public static class Pre extends MotionUpdateEvent {
        public Pre(final float yaw, final float pitch) {
            super(yaw, pitch);
        }
    }

    @Cancelable
    public static class Post extends MotionUpdateEvent {
        public Post(final float yaw, final float pitch) {
            super(yaw, pitch);
        }
    }
}
