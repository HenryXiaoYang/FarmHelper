package com.jelly.farmhelperv3.hud;

import com.jelly.farmhelperv3.config.Setting;

import com.jelly.farmhelperv3.config.ConfigColor;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.impl.BPSTracker;
import com.jelly.farmhelperv3.feature.impl.ProfitCalculator;
import com.jelly.farmhelperv3.handler.GameStateHandler;
import com.jelly.farmhelperv3.util.LogUtils;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProfitCalculatorHUD extends TextHud {
    private final float iconWidth = 12 * scale;
    private final float iconHeight = 12 * scale;
    protected transient ArrayList<Pair<String, String>> lines = new ArrayList<>();
    public ProfitCalculatorHUD() {
        super(true, 1f, 1f, 1, true, true, 4, 5, 5, new ConfigColor(0, 0, 0, 150), false, 2, new ConfigColor(0, 0, 0, 127));
    }

    @Override
    protected void getLines(List<String> output, boolean example) {
        addLines();
        lines.forEach(line -> output.add(line.getLeft()));
    }

    private static final java.util.Map<String, int[]> iconSizes = new java.util.HashMap<>();
    @Override protected int contentInset() { return 16; }
    @Override protected int lineHeight() { return 15; }
    @Override protected void drawLine(net.minecraft.client.gui.GuiGraphicsExtractor graphics, String text, int index, int left, int top) {
        String path = lines.get(index).getRight();
        if (path != null) {
            int[] size = iconSizes.computeIfAbsent(path, resource -> {
                try (var stream = getClass().getResourceAsStream(resource)) {
                    if (stream == null) throw new java.io.IOException("Missing HUD icon: " + resource);
                    var image = javax.imageio.ImageIO.read(stream);
                    return new int[]{image.getWidth(), image.getHeight()};
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            });
            graphics.pose().pushMatrix();
            graphics.pose().translate(left - 16, top);
            graphics.pose().scale(12f / size[0], 12f / size[1]);
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("farmhelperv3", path.substring("/assets/farmhelperv3/".length())), 0, 0, 0f, 0f, size[0], size[1], size[0], size[1]);
            graphics.pose().popMatrix();
        }
        super.drawLine(graphics, text, index, left, top);
    }

    @Override
    protected boolean shouldShow() {
        if (!super.shouldShow()) {
            return false;
        }
        return !FarmHelperConfig.streamerMode && GameStateHandler.getInstance().inGarden();
    }

    public void addLines() {
        if (ProfitCalculator.getInstance().getUpdateClock().isScheduled() && !ProfitCalculator.getInstance().getUpdateClock().passed()) {
            return;
        }
        ProfitCalculator.getInstance().getUpdateClock().schedule(100);
        lines.clear();

        lines.add(Pair.of(ProfitCalculator.getInstance().getRealProfitString(), "/assets/farmhelperv3/textures/gui/profit.png"));
        lines.add(Pair.of(ProfitCalculator.getInstance().getProfitPerHourString(), "/assets/farmhelperv3/textures/gui/profithr.png"));
        lines.add(Pair.of(BPSTracker.getInstance().getBPS(), "/assets/farmhelperv3/textures/gui/bps.png"));
        lines.add(Pair.of(LogUtils.getRuntimeFormat(), "/assets/farmhelperv3/textures/gui/runtime.png"));
        List<ProfitCalculator.BazaarItem> linesCopy = new ArrayList<>(ProfitCalculator.getInstance().cropsToCount);
        linesCopy.addAll(ProfitCalculator.getInstance().rngDropToCount);
        linesCopy.stream().filter(crop -> crop.currentAmount > 0).sorted(
                Comparator.comparing(
                        (ProfitCalculator.BazaarItem item) -> -item.currentAmount
                )
        ).forEachOrdered(
                item -> lines.add(
                        Pair.of(
                                String.format("%,.2f", item.currentAmount / item.amountToEnchanted),
                                item.imageURL
                        )
                )
        );
    }
}
