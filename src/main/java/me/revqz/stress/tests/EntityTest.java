package me.revqz.stress.tests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class EntityTest implements Test {

    // entities per tick of running
    private static final int BATCH = 10;

    private BukkitTask task;
    private Location spawnLoc;

    @Override
    public void start() {
        World world = Bukkit.getWorlds().get(0);
        spawnLoc = world.getSpawnLocation();

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            for (int i = 0; i < BATCH; i++) {
                world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            }
        }, 0L, 1L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        // Remove spawned entities
        if (spawnLoc != null) {
            spawnLoc.getWorld().getNearbyEntities(spawnLoc, 64, 64, 64)
                    .forEach(e -> {
                        if (e.getType() == EntityType.ZOMBIE)
                            e.remove();
                    });
        }
    }

    @Override
    public String getName() {
        return "entity";
    }
}
