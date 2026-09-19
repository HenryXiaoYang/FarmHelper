package com.jelly.farmhelperv3.util.helper;

import lombok.Getter;
import lombok.Setter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.List;

public class PlayerSimulation {

    public final Level worldObj;

    public double posX;
    public double posY;
    public double posZ;
    public double motionX;
    public double motionY;
    public double motionZ;
    @Getter
    @Setter
    private AABB entityBoundingBox;

    public boolean onGround;

    public float jumpMovementFactor;

    public float landMovementFactor;

    public float fallDistance;

    public boolean isCollided;

    public boolean horizontalCollision;

    public boolean verticalCollision;

    public float rotationYaw;

    public boolean noClip;

//    public Entity ridingEntity;

    public boolean isShiftKeyDown;

    public boolean isSprinting;

    public boolean isFlying;

    public boolean isOnLadder;

    public float stepHeight;

    public float moveStrafing;

    public float moveForward;

    public int depthStriderModifier;

    public PlayerSimulation(Level world) {
        this.worldObj = world;
    }

    public void copy(LivingEntity entity) {
        posX = entity.getX();
        posY = entity.getY();
        posZ = entity.getZ();
        motionX = entity.getDeltaMovement().x;
        motionY = entity.getDeltaMovement().y;
        motionZ = entity.getDeltaMovement().z;
        entityBoundingBox = entity.getBoundingBox();
        onGround = entity.onGround();
        jumpMovementFactor = entity.getSpeed() * 0.1f;
        landMovementFactor = entity.getSpeed();
        fallDistance = (float) entity.fallDistance;
        isCollided = entity.horizontalCollision || entity.verticalCollision;
        horizontalCollision = entity.horizontalCollision;
        verticalCollision = entity.verticalCollision;
        rotationYaw = entity.getYRot();
        noClip = entity.noPhysics;
        isShiftKeyDown = entity.isShiftKeyDown();
        isSprinting = entity.isSprinting();
        isFlying = false;
        stepHeight = entity.maxUpStep();
        moveStrafing = 0;
        moveForward = 0;
        depthStriderModifier = (int) (entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY) * 3);
    }

    public float getAIMoveSpeed() {
        return this.landMovementFactor;
    }

    public boolean isInLava() {
        return this.worldObj.getBlockStates(this.getEntityBoundingBox().inflate(-0.1f, -0.4f, -0.1f)).anyMatch(state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA));
    }

    public boolean isInWater() {
        return this.worldObj.getBlockStates(this.getEntityBoundingBox().inflate(-0.1f, -0.4f, -0.1f)).anyMatch(state -> state.getFluidState().is(net.minecraft.tags.FluidTags.WATER));
    }

    public boolean isOffsetPositionInLiquid(double x, double y, double z) {
        AABB axisalignedbb = this.getEntityBoundingBox().move(x, y, z);
        return this.worldObj.getBlockStates(axisalignedbb.inflate(-0.1f, -0.4f, -0.1f)).anyMatch(state -> state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) ||
                this.worldObj.getBlockStates(axisalignedbb.inflate(-0.1f, -0.4f, -0.1f)).anyMatch(state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA));
    }

    public void onLivingUpdate() {
        if (Math.abs(this.motionX) < 0.005) {
            this.motionX = 0.0;
        }
        if (Math.abs(this.motionY) < 0.005) {
            this.motionY = 0.0;
        }
        if (Math.abs(this.motionZ) < 0.005) {
            this.motionZ = 0.0;
        }
        this.moveStrafing *= 0.98f;
        this.moveForward *= 0.98f;
        moveEntityWithHeading_EntityPlayer(moveStrafing, moveForward);
    }

    public void moveEntityWithHeading_EntityPlayer(float strafe, float forward) {
//        if (this.isFlying && this.ridingEntity == null)
        if (this.isFlying) {
            double d3 = this.motionY;
            float f = this.jumpMovementFactor;
            this.jumpMovementFactor = 0.05F * (float) (this.isSprinting ? 2 : 1);
            this.moveEntityWithHeading(strafe, forward);
            this.motionY = d3 * 0.6D;
            this.jumpMovementFactor = f;
        } else {
            this.moveEntityWithHeading(strafe, forward);
        }
    }

    public void moveEntityWithHeading(float strafe, float forward) {
        if (!this.isInWater() || this.isFlying) {
            if (!this.isInLava() || this.isFlying) {
                float f4 = 0.91f;
                if (this.onGround) {
                    f4 = this.worldObj.getBlockState(BlockPos.containing(Mth.floor(this.posX), Mth.floor(this.getEntityBoundingBox().minY) - 1, Mth.floor(this.posZ))).getBlock().getFriction() * 0.91f;
                }
                float f = 0.16277136f / (f4 * f4 * f4);
                float f5 = this.onGround ? this.getAIMoveSpeed() * f : this.jumpMovementFactor;
                this.moveFlying(strafe, forward, f5);
                f4 = 0.91f;
                if (this.onGround) {
                    f4 = this.worldObj.getBlockState(BlockPos.containing(Mth.floor(this.posX), Mth.floor(this.getEntityBoundingBox().minY) - 1, Mth.floor(this.posZ))).getBlock().getFriction() * 0.91f;
                }

                this.moveEntity(this.motionX, this.motionY, this.motionZ);

                if (this.horizontalCollision && this.isOnLadder) {
                    this.motionY = 0.2D;
                }

                if (!this.worldObj.hasChunkAt(BlockPos.containing(posX, posY, posZ))) {
                    if (this.posY > 0.0D) {
                        this.motionY = -0.1D;
                    } else {
                        this.motionY = 0.0D;
                    }
                } else {
                    this.motionY -= 0.08D;
                }

                this.motionY *= 0.98F;
                this.motionX *= f4;
                this.motionZ *= f4;
            } else {
                double d1 = this.posY;
                this.moveFlying(strafe, forward, 0.02f);
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                this.motionX *= 0.5;
                this.motionY *= 0.5;
                this.motionZ *= 0.5;
                this.motionY -= 0.02;
                if (this.horizontalCollision && this.isOffsetPositionInLiquid(this.motionX, this.motionY + (double) 0.6f - this.posY + d1, this.motionZ)) {
                    this.motionY = 0.3f;
                }
            }
        } else {
            double d0 = this.posY;
            float f1 = 0.8f;
            float f2 = 0.02f;
            float f3 = this.depthStriderModifier;
            if (!this.onGround) {
                f3 *= 0.5f;
            }
            if (f3 > 0.0f) {
                f1 += (0.54600006f - f1) * f3 / 3.0f;
                f2 += (this.getAIMoveSpeed() - f2) * f3 / 3.0f;
            }
            this.moveFlying(strafe, forward, f2);
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= f1;
            this.motionY *= 0.8f;
            this.motionZ *= f1;
            this.motionY -= 0.02;
            if (this.horizontalCollision && this.isOffsetPositionInLiquid(this.motionX, this.motionY + (double) 0.6f - this.posY + d0, this.motionZ)) {
                this.motionY = 0.3f;
            }
        }
    }

    public void moveFlying(float strafe, float forward, float friction) {
        float f = strafe * strafe + forward * forward;
        if (f >= 1.0E-4f) {
            if ((f = Mth.sqrt(f)) < 1.0f) {
                f = 1.0f;
            }
            f = friction / f;
            float f1 = Mth.sin(this.rotationYaw * (float) Math.PI / 180.0f);
            float f2 = Mth.cos(this.rotationYaw * (float) Math.PI / 180.0f);
            this.motionX += ((strafe *= f) * f2 - (forward *= f) * f1);
            this.motionZ += (forward * f2 + strafe * f1);
        }
    }

    public void moveEntity(double x, double y, double z) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (this.noClip) {
            this.setEntityBoundingBox(this.getEntityBoundingBox().move(x, y, z));
            this.resetPositionToBB();
        } else {
            boolean flag;
            double d3 = x;
            double d4 = y;
            double d5 = z;
            flag = this.onGround && this.isShiftKeyDown;
            if (flag) {
                double d6 = 0.05;
                while (x != 0.0 && collisionBoxes(player, this.getEntityBoundingBox().move(x, -1.0, 0.0)).isEmpty()) {
                    x = x < d6 && x >= -d6 ? 0.0 : (x > 0.0 ? x - d6 : x + d6);
                    d3 = x;
                }
                while (z != 0.0 && collisionBoxes(player, this.getEntityBoundingBox().move(0.0, -1.0, z)).isEmpty()) {
                    z = z < d6 && z >= -d6 ? 0.0 : (z > 0.0 ? z - d6 : z + d6);
                    d5 = z;
                }
                while (x != 0.0 && z != 0.0 && collisionBoxes(player, this.getEntityBoundingBox().move(x, -1.0, z)).isEmpty()) {
                    x = x < d6 && x >= -d6 ? 0.0 : (x > 0.0 ? x - d6 : x + d6);
                    d3 = x;
                    z = z < d6 && z >= -d6 ? 0.0 : (z > 0.0 ? z - d6 : z + d6);
                    d5 = z;
                }
            }
            List<AABB> list1 = collisionBoxes(player, this.getEntityBoundingBox().expandTowards(x, y, z));
            AABB axisalignedbb = this.getEntityBoundingBox();
            for (AABB axisAlignedBB : list1) {
                y = net.minecraft.world.phys.shapes.Shapes.create(axisAlignedBB).collide(net.minecraft.core.Direction.Axis.Y, this.getEntityBoundingBox(), y);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().move(0.0, y, 0.0));
            boolean flag1 = this.onGround || d4 != y && d4 < 0.0;
            for (AABB axisalignedbb2 : list1) {
                x = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb2).collide(net.minecraft.core.Direction.Axis.X, this.getEntityBoundingBox(), x);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().move(x, 0.0, 0.0));
            for (AABB axisalignedbb13 : list1) {
                z = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb13).collide(net.minecraft.core.Direction.Axis.Z, this.getEntityBoundingBox(), z);
            }
            this.setEntityBoundingBox(this.getEntityBoundingBox().move(0.0, 0.0, z));
            if (this.stepHeight > 0.0f && flag1 && (d3 != x || d5 != z)) {
                double d = x;
                double d7 = y;
                double d8 = z;
                AABB axisalignedbb3 = this.getEntityBoundingBox();
                this.setEntityBoundingBox(axisalignedbb);
                y = this.stepHeight;
                List<AABB> list = collisionBoxes(player, this.getEntityBoundingBox().expandTowards(d3, y, d5));
                AABB axisalignedbb4 = this.getEntityBoundingBox();
                AABB axisalignedbb5 = axisalignedbb4.expandTowards(d3, 0.0, d5);
                double d9 = y;
                for (AABB axisalignedbb6 : list) {
                    d9 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb6).collide(net.minecraft.core.Direction.Axis.Y, axisalignedbb5, d9);
                }
                axisalignedbb4 = axisalignedbb4.move(0.0, d9, 0.0);
                double d15 = d3;
                for (AABB axisalignedbb7 : list) {
                    d15 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb7).collide(net.minecraft.core.Direction.Axis.X, axisalignedbb4, d15);
                }
                axisalignedbb4 = axisalignedbb4.move(d15, 0.0, 0.0);
                double d16 = d5;
                for (AABB axisalignedbb8 : list) {
                    d16 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb8).collide(net.minecraft.core.Direction.Axis.Z, axisalignedbb4, d16);
                }
                axisalignedbb4 = axisalignedbb4.move(0.0, 0.0, d16);
                AABB axisalignedbb14 = this.getEntityBoundingBox();
                double d17 = y;
                for (AABB axisalignedbb9 : list) {
                    d17 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb9).collide(net.minecraft.core.Direction.Axis.Y, axisalignedbb14, d17);
                }
                axisalignedbb14 = axisalignedbb14.move(0.0, d17, 0.0);
                double d18 = d3;
                for (AABB axisalignedbb10 : list) {
                    d18 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb10).collide(net.minecraft.core.Direction.Axis.X, axisalignedbb14, d18);
                }
                axisalignedbb14 = axisalignedbb14.move(d18, 0.0, 0.0);
                double d19 = d5;
                for (AABB axisalignedbb11 : list) {
                    d19 = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb11).collide(net.minecraft.core.Direction.Axis.Z, axisalignedbb14, d19);
                }
                axisalignedbb14 = axisalignedbb14.move(0.0, 0.0, d19);
                double d20 = d15 * d15 + d16 * d16;
                double d10 = d18 * d18 + d19 * d19;
                if (d20 > d10) {
                    x = d15;
                    z = d16;
                    y = -d9;
                    this.setEntityBoundingBox(axisalignedbb4);
                } else {
                    x = d18;
                    z = d19;
                    y = -d17;
                    this.setEntityBoundingBox(axisalignedbb14);
                }
                for (AABB axisalignedbb12 : list) {
                    y = net.minecraft.world.phys.shapes.Shapes.create(axisalignedbb12).collide(net.minecraft.core.Direction.Axis.Y, this.getEntityBoundingBox(), y);
                }
                this.setEntityBoundingBox(this.getEntityBoundingBox().move(0.0, y, 0.0));
                if (d * d + d8 * d8 >= x * x + z * z) {
                    x = d;
                    y = d7;
                    z = d8;
                    this.setEntityBoundingBox(axisalignedbb3);
                }
            }
            this.resetPositionToBB();
            this.horizontalCollision = d3 != x || d5 != z;
            this.verticalCollision = d4 != y;
            this.onGround = this.verticalCollision && d4 < 0.0;
            this.isCollided = this.horizontalCollision || this.verticalCollision;
            this.updateFallState(y, this.onGround);
            if (d3 != x) {
                this.motionX = 0.0;
            }
            if (d5 != z) {
                this.motionZ = 0.0;
            }
            if (d4 != y) {
                motionY = 0;
            }
        }
    }

    private List<AABB> collisionBoxes(LocalPlayer player, AABB box) {
        java.util.ArrayList<AABB> boxes = new java.util.ArrayList<>();
        worldObj.getCollisions(player, box).forEach(shape -> boxes.addAll(shape.toAabbs()));
        return boxes;
    }

    private void resetPositionToBB() {
        this.posX = (this.getEntityBoundingBox().minX + this.getEntityBoundingBox().maxX) / 2.0;
        this.posY = this.getEntityBoundingBox().minY;
        this.posZ = (this.getEntityBoundingBox().minZ + this.getEntityBoundingBox().maxZ) / 2.0;
    }


    protected void updateFallState(double y, boolean onGroundIn) {
        if (onGroundIn) {
            if (this.fallDistance > 0.0f) {
                this.fallDistance = 0.0f;
            }
        } else if (y < 0.0) {
            this.fallDistance = (float) ((double) this.fallDistance - y);
        }
    }
}