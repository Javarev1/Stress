package me.revqz.stress.tests;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.projectiles.ProjectileSource;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class EntityTest implements Test, Listener {

    private static final Random RNG = new Random();

    private int amount;
    private int durationSeconds;
    private int range;
    private EntityType entityType;
    private final Set<Entity> spawned = new HashSet<>();
    private double msptBefore;

    @Override
    public void setup() {
        amount = Stress.get().getConfig().getInt("tests.entity.amount", 100);
        durationSeconds = Stress.get().getConfig().getInt("tests.entity.duration", 10);
        range = Stress.get().getConfig().getInt("tests.entity.range", 0);
        String typeName = Stress.get().getConfig().getString("tests.entity.type", "zombie").toUpperCase();
        try {
            entityType = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            entityType = EntityType.ZOMBIE;
        }
        Bukkit.getPluginManager().registerEvents(this, Stress.get());
    }

    @Override
    public void start() {
        World world = Bukkit.getWorlds().get(0);
        Location center = world.getSpawnLocation();
        msptBefore = Stress.get().getTickProfiler().getAverageMspt(20);

        for (int i = 0; i < amount; i++) {
            Location loc = range > 0 ? randomNearby(center, range) : center;
            spawned.add(world.spawnEntity(loc, entityType));
        }

        Bukkit.broadcast(MessageUtils.info(
                "Spawned " + amount + " " + entityType.name().toLowerCase() + " — measuring for " + durationSeconds + "s..."));

        Bukkit.getScheduler().runTaskLater(Stress.get(), this::finish, durationSeconds * 20L);
    }

    private void finish() {
        double msptAfter = Stress.get().getTickProfiler().getAverageMspt(20);
        double tpsBefore = Stress.get().getTickProfiler().msptToTps(msptBefore);
        double tpsAfter = Stress.get().getTickProfiler().msptToTps(msptAfter);

        Bukkit.broadcast(MessageUtils.info(String.format(
                "[entity] %d %s — TPS: %.1f → %.1f  MSPT: %.1fms → %.1fms",
                amount, entityType.name().toLowerCase(), tpsBefore, tpsAfter, msptBefore, msptAfter)));

        spawned.forEach(Entity::remove);
        spawned.clear();
        HandlerList.unregisterAll(this);
    }

    @Override
    public void stop() {
        spawned.forEach(Entity::remove);
        spawned.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (spawned.contains(e.getEntity()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        Entity damager = resolve(e.getDamager());
        if (spawned.contains(damager))
            e.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (spawned.contains(e.getEntity()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (spawned.contains(e.getEntity())) {
            e.setDroppedExp(0);
            e.getDrops().clear();
        }
    }

    private Entity resolve(Entity entity) {
        if (entity instanceof Projectile p && p.getShooter() instanceof Entity shooter)
            return shooter;
        return entity;
    }

    private Location randomNearby(Location center, int radius) {
        double ox = RNG.nextInt(radius * 2) - radius;
        double oz = RNG.nextInt(radius * 2) - radius;
        Location loc = center.clone().add(ox, 0, oz);
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
        return loc;
    }

    @Override
    public String getName() {
        return "entity";
    }
}
