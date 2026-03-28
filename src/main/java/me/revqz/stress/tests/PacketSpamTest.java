package me.revqz.stress.tests;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class PacketSpamTest implements Test {

    private static final Random RNG = new Random();

    private int packetsPerTick;
    private BukkitTask task;
    private final AtomicLong totalSent = new AtomicLong(0);
    private long startMs;
    private ProtocolManager protocol;

    @Override
    public void setup() {
        packetsPerTick = Stress.get().getConfig().getInt("tests.packet-spam.packets-per-tick", 100);
        Plugin pl = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (pl != null && pl.isEnabled()) {
            protocol = ProtocolLibrary.getProtocolManager();
        }
    }

    @Override
    public void start() {
        startMs = System.currentTimeMillis();
        totalSent.set(0);

        if (protocol == null) {
            Stress.get().getLogger().warning("[PacketSpam] ProtocolLib not found — test skipped.");
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty())
                return;

            Player target = players.stream()
                    .skip(RNG.nextInt(players.size()))
                    .findFirst()
                    .orElse(null);
            if (target == null)
                return;

            for (int i = 0; i < packetsPerTick; i++) {
                PacketContainer pkt = (i % 2 == 0)
                        ? buildBlockChangePacket()
                        : buildWindowItemsPacket();
                try {
                    protocol.sendServerPacket(target, pkt);
                    totalSent.incrementAndGet();
                } catch (Exception ignored) {
                }
            }
        }, 0L, 1L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(Stress.get(), () -> {
            long elapsed = (System.currentTimeMillis() - startMs) / 1000;
            if (elapsed == 0)
                return;
            long rate = totalSent.get() / elapsed;
            Stress.get().getLogger().info(
                    "[PacketSpam] sent=" + totalSent.get()
                            + "  rate=" + rate + " pkt/s"
                            + "  elapsed=" + elapsed + "s");
        }, 100L, 100L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        long elapsed = Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
        Stress.get().getLogger().info(
                "[PacketSpam] finished — total=" + totalSent.get()
                        + "  avg=" + (totalSent.get() / elapsed) + " pkt/s");
    }

    @Override
    public String getName() {
        return "packet-spam";
    }

    private PacketContainer buildBlockChangePacket() {
        PacketContainer pkt = protocol.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
        return pkt;
    }

    private PacketContainer buildWindowItemsPacket() {
        PacketContainer pkt = protocol.createPacket(PacketType.Play.Server.WINDOW_ITEMS);
        return pkt;
    }
}
