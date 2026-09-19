package com.jelly.farmhelperv3.util;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.GameType;



import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TablistUtils {

    public static final Ordering<PlayerInfo> playerOrdering = Ordering.from(new PlayerComparator());

    private static volatile List<String> formattedTablist = List.of();

    public static List<String> getFormattedTabList() { return formattedTablist; }

    private static final CopyOnWriteArrayList<String> cachedTablist = new CopyOnWriteArrayList<>();

    public static List<String> getTabList() {
        return cachedTablist;
    }

    public static void setCachedTablist(List<String> tablist) {
        cachedTablist.clear();
        formattedTablist = List.copyOf(tablist);
        cachedTablist.addAll(tablist.stream().map(net.minecraft.ChatFormatting::stripFormatting).toList());
    }


    static class PlayerComparator implements Comparator<PlayerInfo> {
        private PlayerComparator() {
        }

        public int compare(PlayerInfo o1, PlayerInfo o2) {
            PlayerTeam team1 = o1.getTeam();
            PlayerTeam team2 = o2.getTeam();
            return ComparisonChain.start().compare(o2.getTabListOrder(), o1.getTabListOrder()).compareTrueFirst(
                            o1.getGameMode() != GameType.SPECTATOR,
                            o2.getGameMode() != GameType.SPECTATOR
                    )
                    .compare(
                            team1 != null ? team1.getName() : "",
                            team2 != null ? team2.getName() : ""
                    )
                    .compare(o1.getProfile().name(), o2.getProfile().name(), String.CASE_INSENSITIVE_ORDER).result();
        }
    }
}
