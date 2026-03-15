package me.revqz.stress.tests;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.tests.tps.BukkitSchedulerTickProfiler;
import me.revqz.stress.tests.tps.ServerTickEndEventTickProfiler;
import me.revqz.stress.tests.tps.TickInterval;

public class TpsTest implements Test {

    // volume radius of test
    private static final int RADIUS = 15;
    private static final Random RNG = new Random();

    private BukkitTask task;
    private Location center;
    private BukkitSchedulerTickProfiler schedProf;
    private ServerTickEndEventTickProfiler eventProf;

    @Override
    public void start() {
        schedProf = new BukkitSchedulerTickProfiler();
        eventProf = new ServerTickEndEventTickProfiler();
        schedProf.start();
        eventProf.start();

        World world = Bukkit.getWorlds().get(0);
        center = world.getHighestBlockAt(world.getSpawnLocation()).getLocation().add(0, 10, 0);

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            TickInterval interval = new TickInterval();
            Material mat = RNG.nextBoolean() ? Material.REDSTONE_BLOCK : Material.AIR;

            // Massive synchronous block updates
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int y = -RADIUS; y <= RADIUS; y++) {
                    for (int z = -RADIUS; z <= RADIUS; z++) {
                        center.clone().add(x, y, z).getBlock().setType(mat, true);
                    }
                }
            }
            interval.end();

            Bukkit.broadcast(Component.text(String.format(
                    "[TpsTest] Block Update Time: %.2fms | Sched Delay: %.2fms | Paper Event: %.2fms",
                    interval.durationMs(), schedProf.getMspt(), eventProf.getMspt()), NamedTextColor.RED));
        }, 0L, 10L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        if (schedProf != null)
            schedProf.stop();
        if (eventProf != null)
            eventProf.stop();
        // Cleanup volume
        if (center != null) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int y = -RADIUS; y <= RADIUS; y++) {
                    for (int z = -RADIUS; z <= RADIUS; z++) {
                        center.clone().add(x, y, z).getBlock().setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "tps";
    }
}
