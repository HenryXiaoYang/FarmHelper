package com.jelly.farmhelperv3.pathfinder;

import com.jelly.farmhelperv3.util.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Six-neighbor flying search, advanced on the client thread so world reads stay safe. */
public final class FlyNodeProcessor {
    private record Node(BlockPos pos, int cost, int estimate, Node parent) {}
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::estimate));
    private final Map<BlockPos, Integer> costs = new HashMap<>();
    private ClientLevel level;
    private BlockPos goal, origin;
    private int height, width;
    private double maxDistance;
    private long deadline;
    private CompletableFuture<List<Vec3>> result;

    public CompletableFuture<List<Vec3>> start(Vec3 target, double range) {
        cancel();
        var player = Minecraft.getInstance().player;
        level = Minecraft.getInstance().level;
        result = new CompletableFuture<>();
        width = (int) Math.ceil(player.getBbWidth());
        height = (int) Math.ceil(player.getBbHeight());
        origin = BlockPos.containing(player.getBoundingBox().minX, player.getY() + 0.5, player.getBoundingBox().minZ);
        goal = BlockPos.containing(target.x - player.getBbWidth() / 2, target.y - player.getBbHeight() / 2, target.z - player.getBbWidth() / 2);
        maxDistance = range;
        deadline = System.nanoTime() + 10_000_000_000L;
        costs.put(origin, 0);
        open.add(new Node(origin, 0, origin.distManhattan(goal), null));
        return result;
    }
    public void cancel() {
        if (result != null) result.cancel(false);
        open.clear(); costs.clear();
    }
    public void tick() {
        if (result == null || result.isDone()) return;
        if (Minecraft.getInstance().level != level || System.nanoTime() >= deadline) { result.complete(null); return; }
        long sliceEnd = System.nanoTime() + 2_000_000;
        // ponytail: cap search at 65,536 discovered nodes; use hierarchical routing for larger searches.
        while (!open.isEmpty() && costs.size() <= 65_536 && System.nanoTime() < sliceEnd) {
            Node current = open.poll();
            if (current.cost != costs.getOrDefault(current.pos, Integer.MAX_VALUE)) continue;
            if (current.pos.equals(goal)) {
                List<Vec3> route = new ArrayList<>();
                for (Node node = current; node != null; node = node.parent) route.add(Vec3.atLowerCornerOf(node.pos));
                Collections.reverse(route);
                result.complete(route); return;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.pos.relative(direction);
                int cost = current.cost + 1;
                if (next.distSqr(origin) > maxDistance * maxDistance || cost >= costs.getOrDefault(next, Integer.MAX_VALUE) || !free(next)) continue;
                costs.put(next, cost);
                open.add(new Node(next, cost, cost + next.distManhattan(goal), current));
            }
        }
        if (open.isEmpty() || costs.size() > 65_536) result.complete(null);
    }
    private boolean free(BlockPos pos) {
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < width; z++) {
            BlockPos test = pos.offset(x, y, z);
            if (!level.hasChunkAt(test) || !BlockUtils.isFree(test.getX(), test.getY(), test.getZ(), level)) return false;
        }
        return true;
    }
}
