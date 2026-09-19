package com.jelly.farmhelperv3.command;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.impl.AutoWardrobe;
import com.jelly.farmhelperv3.feature.impl.PestFarmer;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.pathfinder.FlyPathFinderExecutor;
import com.jelly.farmhelperv3.util.LogUtils;
import com.jelly.farmhelperv3.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import com.mojang.brigadier.arguments.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

import java.util.Arrays;
import java.lang.Float;

public class FarmHelperMainCommand {
    public static void register() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            FarmHelperMainCommand commands = new FarmHelperMainCommand();
            for (String alias : new String[]{"fh", "farmhelper"}) {
                var root = literal(alias).executes(context -> { commands.mainCommand(); return 1; });
                root.then(literal("hud").executes(c -> {
                    com.jelly.farmhelperv3.util.Tasks.schedule(() -> {
                        var client = net.minecraft.client.Minecraft.getInstance();
                        client.setScreen(new com.jelly.farmhelperv3.config.HudEditorScreen(client.screen, com.jelly.farmhelperv3.FarmHelper.config));
                    }, 0, java.util.concurrent.TimeUnit.MILLISECONDS);
                    return 1;
                }));
                root.then(literal("stoppath").executes(c -> { commands.stoppath(); return 1; }));
                root.then(literal("sp").executes(c -> { commands.stoppath(); return 1; }));
                root.then(literal("update").executes(c -> { commands.update(); return 1; }));
                root.then(literal("up").executes(c -> { commands.update(); return 1; }));
                root.then(literal("msc").executes(c -> { commands.markSpawnChanged(); return 1; }));
                root.then(literal("markSpawnChanged").executes(c -> { commands.markSpawnChanged(); return 1; }));
                for (String name : new String[]{"pathfind", "pf"}) root.then(literal(name)
                    .then(argument("x", FloatArgumentType.floatArg())
                    .then(argument("y", FloatArgumentType.floatArg())
                    .then(argument("z", FloatArgumentType.floatArg())
                    .then(argument("follow", BoolArgumentType.bool())
                    .then(argument("smooth", BoolArgumentType.bool()).executes(c -> {
                        FlyPathFinderExecutor.getInstance().findPath(new Vec3(getFloat(c,"x"), getFloat(c,"y"), getFloat(c,"z")), getBool(c,"follow"), getBool(c,"smooth"));
                        return 1;
                    })))))));
                for (String name : new String[]{"pathfind", "pf"}) root.then(literal(name)
                    .then(argument("x", FloatArgumentType.floatArg())
                    .then(argument("y", FloatArgumentType.floatArg())
                    .then(argument("z", FloatArgumentType.floatArg())
                    .then(argument("threshold", FloatArgumentType.floatArg(0.01f))
                    .then(argument("follow", BoolArgumentType.bool())
                    .then(argument("smooth", BoolArgumentType.bool())
                    .then(argument("sprint", BoolArgumentType.bool()).executes(c -> {
                        var pathfinder = FlyPathFinderExecutor.getInstance();
                        pathfinder.setStoppingPositionThreshold(getFloat(c,"threshold"));
                        pathfinder.setSprinting(getBool(c,"sprint"));
                        pathfinder.findPath(new Vec3(getFloat(c,"x"), getFloat(c,"y"), getFloat(c,"z")), getBool(c,"follow"), getBool(c,"smooth"));
                        return 1;
                    })))))))));
                for (String name : new String[]{"pathfindmob", "pfm"}) root.then(literal(name)
                    .then(argument("name", StringArgumentType.string())
                    .then(argument("follow", BoolArgumentType.bool())
                    .then(argument("smooth", BoolArgumentType.bool())
                    .executes(c -> { commands.pathfindmob(getString(c,"name"), getBool(c,"follow"), getBool(c,"smooth")); return 1; })
                    .then(argument("yModifier", FloatArgumentType.floatArg()).executes(c -> {
                        commands.pathfindmob(getString(c,"name"), getBool(c,"follow"), getBool(c,"smooth"), getFloat(c,"yModifier")); return 1;
                    }))))));
                dispatcher.register(root);
            }
            dispatcher.register(literal("fhrewarp")
                .then(literal("add").executes(c -> { FarmHelperConfig.addRewarp(); return 1; }))
                .then(literal("remove").executes(c -> { FarmHelperConfig.removeRewarp(); return 1; }))
                .then(literal("removeall").executes(c -> { FarmHelperConfig.removeAllRewarps(); return 1; })));
        });
    }



    public void mainCommand() {
        FarmHelper.config.openGui();
    }

    public void pathfindmob(
            String mobName,
            boolean follow,
            boolean smooth) {
        Optional<Entity> entity = java.util.stream.StreamSupport.stream(Minecraft.getInstance().level.entitiesForRendering().spliterator(), false).filter(e -> e.getName().getString().toLowerCase().contains(mobName.toLowerCase())).findFirst();
        if (!entity.isPresent()) {
            LogUtils.sendError("[Pathfinder] Could not find entity with name: " + mobName);
            return;
        }
        FlyPathFinderExecutor.getInstance().findPath(entity.get(), follow, smooth);
    }

    public void pathfindmob(
            String mobName,
            boolean follow,
            boolean smooth,
            float yModifier) {
        Optional<Entity> entity = java.util.stream.StreamSupport.stream(Minecraft.getInstance().level.entitiesForRendering().spliterator(), false).filter(e -> e.getName().getString().toLowerCase().contains(mobName.toLowerCase())).findFirst();
        if (!entity.isPresent()) {
            LogUtils.sendError("[Pathfinder] Could not find entity with name: " + mobName);
            return;
        }
        FlyPathFinderExecutor.getInstance().findPath(entity.get(), follow, smooth, yModifier, false);
    }

    public void pathfind(int x, int y, int z,
                         boolean follow,
                         boolean smooth) {
        FlyPathFinderExecutor.getInstance().findPath(new Vec3(x, y, z), follow, smooth);
    }

    public void pathfind(String x, String y, String z, String threshold,
                         boolean follow,
                         boolean smooth,
                         boolean sprint) {
        try {
            FlyPathFinderExecutor.getInstance().setStoppingPositionThreshold(Float.valueOf(threshold));
            FlyPathFinderExecutor.getInstance().setSprinting(sprint);
            FlyPathFinderExecutor.getInstance().findPath(new Vec3(Float.valueOf(x), Float.valueOf(y), Float.valueOf(z)), follow, smooth);
        } catch (Exception e) {
            LogUtils.sendError("Could not. KYS");
            e.printStackTrace();
        }
    }

    public void stoppath() {
        FlyPathFinderExecutor.getInstance().stop();
    }

    public void update() {
        PlayerUtils.closeContainer();
        FarmHelperConfig.checkForUpdate();
    }

    public void markSpawnChanged() {
        PestFarmer.instance.wasSpawnChanged = true;
        System.out.println("Changed: " + PestFarmer.instance.wasSpawnChanged);
    }
}
