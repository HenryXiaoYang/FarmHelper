package com.jelly.farmhelperv3.util;

import com.jelly.farmhelperv3.event.*;
import com.jelly.farmhelperv3.event.Events.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import java.util.*;

public final class ScoreboardUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    public static List<String> cachedScoreboardLines = List.of(), cachedCleanScoreboardLines = List.of();
    public static List<String> getScoreboardLines(boolean clean) { return clean ? cachedCleanScoreboardLines : cachedScoreboardLines; }
    public static String getScoreboardTitle() {
        if (mc.level == null) return "";
        Objective objective = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective == null ? "" : objective.getDisplayName().getString();
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.level == null) return;
        Scoreboard board = mc.level.getScoreboard();
        Objective objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>();
        if (objective != null) board.listPlayerScores(objective).stream().filter(score -> !score.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15).forEach(score -> {
                    Component label = score.display() == null ? Component.literal(score.owner()) : score.display();
                    String text = PlayerTeam.formatNameForTeam(board.getPlayersTeam(score.owner()), label).getString();
                    if (text.contains("☀")) lines.add("Day");
                    if (text.contains("☽")) lines.add("Night");
                    lines.add(text);
                });
        List<String> clean = lines.stream().map(ScoreboardUtils::cleanSB).toList();
        if (!clean.equals(cachedCleanScoreboardLines)) {
            for (int i = 0; i < clean.size(); i++) if (i >= cachedCleanScoreboardLines.size() || !clean.get(i).equals(cachedCleanScoreboardLines.get(i))) Events.BUS.post(new UpdateScoreboardLineEvent(clean.get(i)));
            cachedScoreboardLines = List.copyOf(lines); cachedCleanScoreboardLines = clean;
            Events.BUS.post(new UpdateScoreboardListEvent(lines, clean, System.currentTimeMillis()));
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { cachedScoreboardLines = List.of(); cachedCleanScoreboardLines = List.of(); }
    private static String cleanSB(String text) {
        StringBuilder cleaned = new StringBuilder();
        ChatFormatting.stripFormatting(text).chars().filter(c -> c >= 32 && c < 127 || c == 'ൠ').forEach(c -> cleaned.append((char)c));
        return cleaned.toString();
    }
}
