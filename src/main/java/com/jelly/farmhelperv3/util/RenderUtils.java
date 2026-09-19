package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.event.Events.RenderGameOverlayEvent;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.jelly.farmhelperv3.event.Events.RenderWorldLastEvent;
import com.jelly.farmhelperv3.event.Events;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.*;
import java.awt.Color;
import java.util.ArrayList;

public final class RenderUtils {
    private static final RenderStateDataKey<List<Gizmo>> FRAME = RenderStateDataKey.create();
    private static final RenderType LINES = overlay("lines", RenderPipelines.LINES_SNIPPET);
    private static final RenderType BOXES = overlay("boxes", RenderPipelines.DEBUG_FILLED_SNIPPET);
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(1536);
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(ALLOCATOR);
    private static List<Gizmo> extracting;
    private static Vec3 tracerOrigin, cameraPosition;

    private static RenderType overlay(String name, RenderPipeline.Snippet snippet) {
        var pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("farmhelperv3", "overlay/" + name))
                .withDepthStencilState(Optional.empty()).withCull(false).build());
        var setup = RenderSetup.builder(pipeline);
        if (name.equals("boxes")) setup.sortOnUpload();
        return RenderType.create("farmhelperv3_" + name, setup.createRenderSetup());
    }
    public static void extractWorld(LevelExtractionContext context) {
        extracting = new ArrayList<>();
        var camera = context.camera();
        cameraPosition = camera.position();
        tracerOrigin = cameraPosition.add(new Vec3(camera.forwardVector()).scale(0.25));
        try {
            Events.BUS.post(new RenderWorldLastEvent(context.deltaTracker().getGameTimeDeltaPartialTick(false)));
            ((FabricRenderState)context.levelState()).setData(FRAME, List.copyOf(extracting));
        } finally { extracting = null; tracerOrigin = null; cameraPosition = null; }
    }
    public static void renderWorld(LevelRenderContext context) {
        var frame = ((FabricRenderState)context.levelState()).getDataOrDefault(FRAME, List.of());
        if (frame.isEmpty()) return;
        var geometry = new DrawableGizmoPrimitives();
        for (Gizmo gizmo : frame) if (!(gizmo instanceof TextGizmo)) gizmo.emit(geometry, 1);
        var camera = context.levelState().cameraRenderState;
        var pose = context.poseStack();
        geometry.render(pose, type -> BUFFERS.getBuffer(type == RenderTypes.debugFilledBox() ? BOXES : LINES),
                camera, RenderSystem.getModelViewMatrix());
        BUFFERS.endBatch();
        var font = Minecraft.getInstance().font;
        for (Gizmo gizmo : frame) if (gizmo instanceof TextGizmo text) {
            pose.pushPose();
            Vec3 relative = text.pos().subtract(camera.pos);
            pose.translate(relative.x, relative.y, relative.z);
            pose.mulPose(camera.orientation);
            float scale = text.style().scale() / 16;
            pose.scale(scale, -scale, scale);
            font.drawInBatch(text.text(), -font.width(text.text()) / 2f, 0, text.style().color(), false, pose.last().pose(), BUFFERS,
                    Font.DisplayMode.SEE_THROUGH, 0x55000000, 15728880);
            pose.popPose();
        }
        BUFFERS.endBatch();
    }
    public static void close() { ALLOCATOR.close(); }
    private static void add(Gizmo gizmo) {
        Objects.requireNonNull(extracting, "World geometry must be submitted during extraction").add(gizmo);
    }
    /** All world rendering helpers take absolute world coordinates. */
    public static void drawBlockBox(BlockPos pos, Color color) { drawBox(new AABB(pos), color); }
    public static void drawBox(AABB box, Color color) {
        add(new CuboidGizmo(box, GizmoStyle.strokeAndFill(color.getRGB(), 2, color.getRGB()), false));
    }
    public static void drawMultiLineText(ArrayList<String> lines, RenderGameOverlayEvent event, Color color, float scale) {
        var graphics = event.graphics;
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2f, 50);
        graphics.pose().scale(scale, scale);
        int y = 0;
        for (String line : lines) {
            graphics.centeredText(Minecraft.getInstance().font, line, 0, y, color.getRGB());
            y += Minecraft.getInstance().font.lineHeight * 2;
        }
        graphics.pose().popMatrix();
    }
    public static void drawCenterTopText(String text, RenderGameOverlayEvent event, Color color) { drawCenterTopText(text, event, color, 3); }
    public static void drawCenterTopText(String text, RenderGameOverlayEvent event, Color color, float scale) {
        drawMultiLineText(new ArrayList<>(List.of(text)), event, color, scale);
    }
    public static void drawText(String text, double x, double y, double z, float scale) {
        Vec3 position = new Vec3(x, y, z);
        float worldScale = scale * (float)(0.45 * Math.max(position.distanceTo(Objects.requireNonNull(cameraPosition)) / 150, 0.1));
        add(new TextGizmo(position, text, TextGizmo.Style.whiteAndCentered().withScale(16 * worldScale)));
    }
    public static void drawTracer(Vec3 from, Vec3 to, Color color) {
        add(new LineGizmo(from, to, color.getRGB(), 1.5f));
    }
    public static void drawTracer(Vec3 to, Color color) {
        drawTracer(Objects.requireNonNull(tracerOrigin), to, color);
    }
}
