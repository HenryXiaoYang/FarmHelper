package com.jelly.farmhelperv3.event;

import lombok.Getter;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.Cancelable;
import com.jelly.farmhelperv3.event.Events.Event;

@Cancelable
@Getter
public class SpawnParticleEvent extends Event {

    ParticleOptions particleTypes;
    boolean isLongDistance;
    double x;
    double y;
    double z;

    double xOffset;
    double yOffset;
    double zOffset;
    int[] params;

    public SpawnParticleEvent(
            ParticleOptions particleTypes,
            boolean isLongDistance,
            double x, double y, double z,
            double xOffset, double yOffset, double zOffset,
            int[] params
    ) {
        this.particleTypes = particleTypes;
        this.isLongDistance = isLongDistance;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.params = params;
    }

    public Vec3 getPos() {
        return new Vec3(x, y, z);
    }
}
