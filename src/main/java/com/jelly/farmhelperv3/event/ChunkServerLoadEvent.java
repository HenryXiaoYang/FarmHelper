package com.jelly.farmhelperv3.event;

import lombok.Getter;
import net.minecraft.world.level.chunk.LevelChunk;
import com.jelly.farmhelperv3.event.Events.Event;

@Getter
public class ChunkServerLoadEvent extends Event {
    public int x;
    public int z;
    public LevelChunk chunk;

    public ChunkServerLoadEvent(int x, int z, LevelChunk c) {
        this.x = x;
        this.z = z;
        this.chunk = c;
    }
}
