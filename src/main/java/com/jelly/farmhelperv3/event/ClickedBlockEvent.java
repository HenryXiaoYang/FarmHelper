package com.jelly.farmhelperv3.event;

import lombok.Getter;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.jelly.farmhelperv3.event.Events.Event;

@Getter
public class ClickedBlockEvent extends Event {
    private final BlockPos pos;
    private final Direction facing;
    private final Block block;

    public ClickedBlockEvent(BlockPos pos, Direction facing, Block block) {
        this.pos = pos;
        this.facing = facing;
        this.block = block;
    }
}
