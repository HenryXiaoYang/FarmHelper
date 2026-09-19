package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.config.FarmHelperConfig;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public class CropUtils {

    public static final AABB[] CARROT_POTATO_BOX = {
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.1875D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.375D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.4375D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5625D, 1.0D)
    };

    public static final AABB[] WHEAT_BOX = {
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.375D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.625D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
    };

    public static final AABB[] NETHER_WART_BOX = {
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.6875D, 1.0D),
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D)
    };

    public static net.minecraft.world.phys.shapes.VoxelShape selectionShape(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock) {
            int age = state.getValue(CropBlock.AGE);
            double height = FarmHelperConfig.increasedCrops ? (block instanceof PotatoBlock || block instanceof CarrotBlock ? CARROT_POTATO_BOX[age].maxY : WHEAT_BOX[age].maxY) : 0.25;
            return net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1, height, 1);
        }
        if (block instanceof NetherWartBlock) return net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1,
                FarmHelperConfig.increasedNetherWarts ? NETHER_WART_BOX[state.getValue(NetherWartBlock.AGE)].maxY : 0.25, 1);
        if (block instanceof MushroomBlock) {
            double min = FarmHelperConfig.increasedMushrooms ? 0 : 0.3;
            return net.minecraft.world.phys.shapes.Shapes.box(min, 0, min, 1 - min, 0.5, 1 - min);
        }
        if (block instanceof CocoaBlock) {
            int age = state.getValue(CocoaBlock.AGE), j = 4 + age * 2, k = 5 + age * 2;
            double low = FarmHelperConfig.increasedCocoaBeans ? 0 : (8 - j / 2.0) / 16;
            double high = FarmHelperConfig.increasedCocoaBeans ? 1 : (8 + j / 2.0) / 16;
            double bottom = (12 - k) / 16.0;
            return switch (state.getValue(CocoaBlock.FACING)) {
                case SOUTH -> net.minecraft.world.phys.shapes.Shapes.box(low, bottom, (15 - j) / 16.0, high, 0.75, 0.9375);
                case NORTH -> net.minecraft.world.phys.shapes.Shapes.box(low, bottom, 0.0625, high, 0.75, (1 + j) / 16.0);
                case WEST -> net.minecraft.world.phys.shapes.Shapes.box(0.0625, bottom, low, (1 + j) / 16.0, 0.75, high);
                case EAST -> net.minecraft.world.phys.shapes.Shapes.box((15 - j) / 16.0, bottom, low, 0.9375, 0.75, high);
                default -> throw new IllegalArgumentException("Vertical cocoa facing");
            };
        }
        return null;
    }

    public static boolean isCrop(Block block) {
        return block instanceof CropBlock ||
                block instanceof PotatoBlock ||
                block instanceof CarrotBlock ||
                block instanceof NetherWartBlock ||
                block instanceof CocoaBlock ||
                block instanceof CactusBlock ||
                block instanceof SugarCaneBlock ||
                block instanceof PumpkinBlock ||
                block == Blocks.MELON ||
                block instanceof StemBlock ||
                block instanceof MushroomBlock;
    }

    public static boolean isCropReady(Block block, BlockPos blockPos) {
        if (block instanceof CropBlock) {
            return Minecraft.getInstance().level.getBlockState(blockPos).getValue(CropBlock.AGE) == 7;
        } else if (block instanceof PotatoBlock) {
            return Minecraft.getInstance().level.getBlockState(blockPos).getValue(PotatoBlock.AGE) == 7;
        } else if (block instanceof CarrotBlock) {
            return Minecraft.getInstance().level.getBlockState(blockPos).getValue(CarrotBlock.AGE) == 7;
        } else if (block instanceof NetherWartBlock) {
            return Minecraft.getInstance().level.getBlockState(blockPos).getValue(NetherWartBlock.AGE) == 3;
        } else if (block instanceof CocoaBlock) {
            return Minecraft.getInstance().level.getBlockState(blockPos).getValue(CocoaBlock.AGE) == 2;
        } else if (block instanceof CactusBlock) {
            return true;
        } else if (block instanceof SugarCaneBlock) {
            return true;
        } else if (block instanceof PumpkinBlock) {
            return true;
        } else if (block == Blocks.MELON) {
            return true;
        } else return block instanceof MushroomBlock;
    }
}
