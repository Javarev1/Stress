package me.revqz.stress.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class ChunkLoadTest implements Test {

    private static final int REGION_SIZE = 32;

    private int regionX;
    private int regionZ;
    private long startMs;

    @Override
    public void setup() {
        regionX = ChunkGenTest.lastRegionX != Integer.MIN_VALUE
                ? ChunkGenTest.lastRegionX
                : Stress.get().getConfig().getInt("tests.chunk-load.region-x", 0);
        regionZ = ChunkGenTest.lastRegionZ != Integer.MIN_VALUE
                ? ChunkGenTest.lastRegionZ
                : Stress.get().getConfig().getInt("tests.chunk-load.region-z", 0);
    }

    @Override
    public void start() {
        World world = ChunkGenTest.lastWorld != null
                ? ChunkGenTest.lastWorld
                : Bukkit.getWorlds().get(0);

        int startX = regionX * REGION_SIZE;
        int startZ = regionZ * REGION_SIZE;

        for (int x = 0; x < REGION_SIZE; x++) {
            for (int z = 0; z < REGION_SIZE; z++) {
                if (!world.isChunkGenerated(startX + x, startZ + z)) {
                    Bukkit.broadcast(MessageUtils.error(
                            "Region (" + regionX + ", " + regionZ + ") has ungenerated chunks. Run chunk-gen first."));
                    return;
                }
            }
        }

        Bukkit.broadcast(MessageUtils.info(
                "Loading region (" + regionX + ", " + regionZ + ") [" + (REGION_SIZE * REGION_SIZE) + " chunks]..."));

        startMs = System.currentTimeMillis();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();

        for (int x = 0; x < REGION_SIZE; x++) {
            final int cx = startX + x;
            for (int z = 0; z < REGION_SIZE; z++) {
                final int cz = startZ + z;
                if (world.isChunkLoaded(cx, cz))
                    world.unloadChunkRequest(cx, cz);
                futures.add(world.getChunkAtAsync(cx, cz, false));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRunAsync(() -> Bukkit.getScheduler().runTask(Stress.get(), () -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    Bukkit.broadcast(MessageUtils.info(String.format(
                            "[chunk-load] Region (%d, %d) loaded %d chunks in %dms (%.1f chunks/s)",
                            regionX, regionZ, REGION_SIZE * REGION_SIZE,
                            elapsed, (REGION_SIZE * REGION_SIZE * 1000.0) / elapsed)));
                }));
    }

    @Override
    public void stop() {
    }

    @Override
    public String getName() {
        return "chunk-load";
    }
}
