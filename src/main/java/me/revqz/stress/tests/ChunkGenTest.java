package me.revqz.stress.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class ChunkGenTest implements Test {

    private static final int REGION_SIZE = 32;
    private static final Random RNG = new Random();
    private static final int MAX_REGION = 58593;

    private int regionX;
    private int regionZ;
    private double msptBefore;

    public static int lastRegionX = Integer.MIN_VALUE;
    public static int lastRegionZ = Integer.MIN_VALUE;
    public static World lastWorld;

    @Override
    public void setup() {
        regionX = Stress.get().getConfig().getInt("tests.chunk-gen.region-x", Integer.MIN_VALUE);
        regionZ = Stress.get().getConfig().getInt("tests.chunk-gen.region-z", Integer.MIN_VALUE);
        if (regionX == Integer.MIN_VALUE)
            regionX = RNG.nextInt(MAX_REGION * 2) - MAX_REGION;
        if (regionZ == Integer.MIN_VALUE)
            regionZ = RNG.nextInt(MAX_REGION * 2) - MAX_REGION;
    }

    @Override
    public void start() {
        World world = Bukkit.getWorlds().get(0);
        int startX = regionX * REGION_SIZE;
        int startZ = regionZ * REGION_SIZE;

        for (int x = 0; x < REGION_SIZE; x++) {
            for (int z = 0; z < REGION_SIZE; z++) {
                if (world.isChunkGenerated(startX + x, startZ + z)) {
                    Bukkit.broadcast(MessageUtils.error(
                            "Region (" + regionX + ", " + regionZ + ") already has generated chunks. Pick a different region."));
                    return;
                }
            }
        }

        msptBefore = Stress.get().getTickProfiler().getAverageMspt(20);
        Bukkit.broadcast(MessageUtils.info(
                "Generating region (" + regionX + ", " + regionZ + ") [" + (REGION_SIZE * REGION_SIZE) + " chunks]..."));

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        AtomicInteger scheduled = new AtomicInteger(0);

        for (int x = 0; x < REGION_SIZE; x++) {
            final int cx = startX + x;
            for (int z = 0; z < REGION_SIZE; z++) {
                final int cz = startZ + z;
                Bukkit.getScheduler().scheduleSyncDelayedTask(Stress.get(), () ->
                        futures.add(world.getChunkAtAsync(cx, cz, true)
                                .thenApply(c -> { scheduled.incrementAndGet(); return c; })),
                        x
                );
            }
        }

        Bukkit.getScheduler().runTaskLater(Stress.get(), () ->
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRunAsync(() -> Bukkit.getScheduler().runTask(Stress.get(), () -> {
                            double msptAfter = Stress.get().getTickProfiler().getAverageMspt(20);
                            double tpsBefore = Stress.get().getTickProfiler().msptToTps(msptBefore);
                            double tpsAfter = Stress.get().getTickProfiler().msptToTps(msptAfter);
                            Bukkit.broadcast(MessageUtils.info(String.format(
                                    "[chunk-gen] Region (%d, %d) done — TPS: %.1f → %.1f  MSPT: %.1fms → %.1fms",
                                    regionX, regionZ, tpsBefore, tpsAfter, msptBefore, msptAfter)));
                            lastRegionX = regionX;
                            lastRegionZ = regionZ;
                            lastWorld = world;
                        })),
                REGION_SIZE + 5L
        );
    }

    @Override
    public void stop() {
    }

    @Override
    public String getName() {
        return "chunk-gen";
    }
}
