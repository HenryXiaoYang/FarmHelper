package com.jelly.farmhelperv3;

import com.jelly.farmhelperv3.event.Events;
import com.jelly.farmhelperv3.feature.impl.Freelook;
import com.jelly.farmhelperv3.util.RenderUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.awt.Color;
import java.nio.file.Path;
import java.util.*;

/** Actual frame rendering, with temporary local-world markers behind an opaque wall. */
public final class RenderChecks {
    private final Minecraft mc;
    private final Vec3 position;
    private final float yaw, pitch;
    private final CameraType cameraType;
    private final Map<BlockPos, BlockState> oldBlocks = new LinkedHashMap<>();
    private final AABB marker;
    private final RenderStateDataKey<List<Gizmo>> frameKey;
    private boolean active = true, capturing;
    private volatile boolean captured;
    private volatile Throwable failure;
    private int ticks, frames, phase;

    @SuppressWarnings("unchecked")
    public RenderChecks(Minecraft mc) throws Exception {
        this.mc = mc; position = mc.player.position(); yaw = mc.player.getYRot(); pitch = mc.player.getXRot(); cameraType = mc.options.getCameraType();
        marker = new AABB(position.add(-2.5, 1, 7), position.add(-1.5, 2, 8));
        var key = RenderUtils.class.getDeclaredField("FRAME"); key.setAccessible(true); frameKey = (RenderStateDataKey<List<Gizmo>>)key.get(null);
        for (String field : List.of("LINES", "BOXES")) {
            var layer = RenderUtils.class.getDeclaredField(field); layer.setAccessible(true);
            var type = (net.minecraft.client.renderer.rendertype.RenderType)layer.get(null);
            check(type.pipeline().getDepthStencilState() == null, "Overlay pipeline must not test or write world depth");
        }
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) {
            BlockPos pos = mc.player.blockPosition().offset(x, y, 4);
            oldBlocks.put(pos, mc.level.getBlockState(pos)); mc.level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        mc.player.setYRot(0); mc.player.setXRot(0); mc.options.setCameraType(CameraType.FIRST_PERSON);
        Events.BUS.register(this);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (!active) return;
            try {
                List<Gizmo> frame = ((FabricRenderState)context.levelState()).getDataOrDefault(frameKey, List.of());
                check(frame.stream().anyMatch(g -> g instanceof CuboidGizmo box && box.aabb().equals(marker)), "World box must not acquire a camera offset");
                Vec3 camera = context.levelState().cameraRenderState.pos;
                LineGizmo tracer = (LineGizmo)frame.stream().filter(g -> g instanceof LineGizmo line && line.end().equals(marker.getCenter())).findFirst().orElseThrow();
                check(Math.abs(tracer.start().distanceTo(camera) - 0.25) < 0.00001, "Tracer starts ahead of the current camera, not at the player eye");
                check(frame.stream().anyMatch(g -> g instanceof TextGizmo text && Math.abs(text.style().scale() - 16 * 0.45 * Math.max(text.pos().distanceTo(camera) / 150, 0.1)) < 0.0001), "Text scale preserves the legacy visual size");
                frames++;
            } catch (Throwable e) { failure = e; }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    @Events.SubscribeEvent public void extract(Events.RenderWorldLastEvent event) {
        if (!active) return;
        RenderUtils.drawBox(marker, new Color(0, 255, 220, 110));
        RenderUtils.drawTracer(marker.getCenter(), new Color(0, 255, 220));
        RenderUtils.drawText("ESP behind wall", marker.getCenter().x, marker.maxY + 0.35, marker.getCenter().z, 1);
        AABB other = marker.move(4, 0, 0);
        RenderUtils.drawBox(other, new Color(255, 75, 190, 90));
        RenderUtils.drawTracer(other.getCenter(), new Color(255, 75, 190));
        RenderUtils.drawTracer(marker.getCenter(), other.getCenter(), Color.YELLOW);
    }
    private void tick() {
        if (!active) return;
        if (failure != null) { cleanup(); throw new AssertionError("Rendering regression", failure); }
        if (++ticks > 400) { cleanup(); throw new AssertionError("Rendering checks timed out"); }
        if (frames < 20 || ticks < 20 * (phase + 1) || capturing && !captured) return;
        if (!capturing) {
            capturing = true;
            int shot = phase;
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                try (image) {
                    Path file = Path.of("..", "porting", "overlay-" + shot + ".png");
                    image.writeToFile(file);
                    var png = javax.imageio.ImageIO.read(file.toFile());
                    int cyan = 0, magenta = 0;
                    for (int y = png.getHeight() / 5; y < png.getHeight() * 7 / 10; y++) for (int x = png.getWidth() / 5; x < png.getWidth() * 4 / 5; x++) {
                        int color = png.getRGB(x, y), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                        if (r < 100 && g > 150 && b > 120) cyan++;
                        if (r > 140 && g < 120 && b > 100) magenta++;
                    }
                    check(cyan > 100 && magenta > 100, "Both overlay colors must reach the framebuffer behind the wall");
                    captured = true;
                }
                catch (Throwable e) { failure = e; }
            });
            return;
        }
        phase++; frames = 0; capturing = false; captured = false;
        if (phase == 1) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        else if (phase == 2) {
            mc.options.setCameraType(CameraType.FIRST_PERSON); Freelook.getInstance().start();
            Freelook.getInstance().setCameraYaw(15); Freelook.getInstance().setCameraPitch(0);
        } else {
            cleanup(); System.out.println("FH CHECKS: overlay frame coordinates, through-wall pipelines, first/third-person/Freelook GPU rendering passed"); mc.stop();
        }
    }
    private void cleanup() {
        active = false; Events.BUS.unregister(this);
        Freelook.getInstance().stop(); mc.options.setCameraType(cameraType);
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        oldBlocks.forEach((pos, state) -> mc.level.setBlock(pos, state, 3));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
