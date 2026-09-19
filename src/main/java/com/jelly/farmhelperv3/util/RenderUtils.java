package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.event.Events.RenderGameOverlayEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.*;
import java.awt.Color;
import java.util.ArrayList;

public final class RenderUtils {
    public static void drawBlockBox(BlockPos pos, Color color) {
        Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(color.getRGB(), 2, color.getRGB())).setAlwaysOnTop();
    }
    /** Existing callers pass a camera-relative box. */
    public static void drawBox(AABB box, Color color) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Gizmos.cuboid(box.move(camera), GizmoStyle.strokeAndFill(color.getRGB(), 2, color.getRGB())).setAlwaysOnTop();
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
        drawMultiLineText(new ArrayList<>(java.util.List.of(text)), event, color, scale);
    }
    public static void drawText(String text, double x, double y, double z, float scale) {
        Gizmos.billboardText(text, new Vec3(x, y, z), TextGizmo.Style.whiteAndCentered().withScale(scale)).setAlwaysOnTop();
    }
    public static void drawTracer(Vec3 from, Vec3 to, Color color) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Gizmos.line(from.add(camera), to, color.getRGB(), 1.5f).setAlwaysOnTop();
    }
    public static void drawTracer(Vec3 to, Color color) {
        Gizmos.line(Minecraft.getInstance().player.getEyePosition(), to, color.getRGB(), 1.5f).setAlwaysOnTop();
    }
}
