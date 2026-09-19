package com.jelly.farmhelperv3.util.helper;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class Target {
    private Vec3 vec;
    @Getter
    private Entity entity;
    @Getter
    private BlockPos blockPos;
    @Accessors(fluent = true)
    @Setter
    @Getter
    private float additionalY;

    public Target(Vec3 vec) {
        this.vec = vec;
    }

    public Target(Entity entity) {
        this.entity = entity;
    }

    public Target(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public Optional<Vec3> getTarget() {
        if (vec != null) {
            return Optional.of(vec.add(0, this.additionalY, 0));
        } else if (entity != null) {
            return Optional.of(entity.position().add(new Vec3(0, Minecraft.getInstance().player.getEyeHeight() + this.additionalY, 0)));
        } else if (blockPos != null) {
            return Optional.of(new Vec3(blockPos.getX(), blockPos.getY() + this.additionalY, blockPos.getZ()));
        } else {
            return Optional.empty();
        }
    }
}
