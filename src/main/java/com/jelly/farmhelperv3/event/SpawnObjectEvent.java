package com.jelly.farmhelperv3.event;

import net.minecraft.world.phys.Vec3;
import com.jelly.farmhelperv3.event.Events.Event;

public class SpawnObjectEvent extends Event {
    public int entityId;
    public double x;
    public double y;
    public double z;
    public Vec3 pos;
    public double speedX;
    public double speedY;
    public double speedZ;
    public float yaw;
    public float pitch;
    public net.minecraft.world.entity.EntityType<?> type;

    public SpawnObjectEvent(int entityId, double x, double y, double z, double speedX, double speedY, double speedZ, float yaw, float pitch, net.minecraft.world.entity.EntityType<?> type) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pos = new Vec3(x, y, z);
        this.speedX = speedX;
        this.speedY = speedY;
        this.speedZ = speedZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.type = type;
    }
}
