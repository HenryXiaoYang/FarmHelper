package com.jelly.farmhelperv3.event;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import com.jelly.farmhelperv3.event.Events.Event;
import org.jetbrains.annotations.NotNull;

public class BlockChangeEvent extends Event {
    public BlockPos pos;
    public BlockState old;
    public BlockState update;
    public BlockGetter world;

    public BlockChangeEvent(@NotNull BlockPos pos, @NotNull BlockState old, @NotNull BlockState update, @NotNull BlockGetter world) {
        this.pos = pos;
        this.old = old;
        this.update = update;
        this.world = world;
    }
}
