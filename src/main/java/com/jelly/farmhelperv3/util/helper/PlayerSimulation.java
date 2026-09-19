package com.jelly.farmhelperv3.util.helper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** No-input flying prediction. Collision shapes come from the current world. */
public final class PlayerSimulation {
    private PlayerSimulation() {}

    public static Vec3 trimMovement(Vec3 movement) {
        boolean stopped = movement.horizontalDistanceSqr() < 9.0E-6;
        return new Vec3(stopped ? 0 : movement.x, Math.abs(movement.y) < 0.003 ? 0 : movement.y, stopped ? 0 : movement.z);
    }

    public static Vec3 predictFlyingStop(LocalPlayer player) {
        Vec3 position = player.position();
        if (!player.getAbilities().flying || player.isPassenger()) return position;
        Vec3 motion = player.getDeltaMovement();
        AABB box = player.getBoundingBox();
        boolean grounded = player.onGround();
        for (int tick = 0; tick < 60; tick++) {
            motion = trimMovement(motion);
            if (motion.lengthSqr() == 0) break;
            float friction = 0.91F * (grounded ? player.level().getBlockState(BlockPos.containing(position.x, box.minY - 0.5000001, position.z)).getBlock().getFriction() : 1.0F);
            Vec3 step = player.noPhysics ? motion : Entity.collideBoundingBox(player, motion, box, player.level(), player.level().getEntityCollisions(player, box.expandTowards(motion)));
            position = position.add(step);
            box = box.move(step);
            grounded = motion.y < 0 && motion.y != step.y;
            // LocalPlayer cancels ordinary flight on landing. Ground travel is
            // outside this predictor; let the pathfinder reassess next tick.
            if (grounded && !player.isSpectator()) break;
            motion = new Vec3(motion.x != step.x ? 0 : motion.x * friction,
                    motion.y * 0.6, motion.z != step.z ? 0 : motion.z * friction);
        }
        return position;
    }
}
