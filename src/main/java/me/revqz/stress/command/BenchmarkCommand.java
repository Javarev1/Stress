package me.revqz.stress.command;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import me.revqz.stress.Stress;
import me.revqz.stress.utils.Stopwatch;

public class BenchmarkCommand implements CommandExecutor {

    private record Tick(long ts, double mspt, double cpuSys, double cpuProc) {
    }

    private static final OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory
            .getOperatingSystemMXBean();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(me.revqz.stress.utils.MessageUtils.error("try: /benchmark <seconds>"));
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
            if (seconds <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(me.revqz.stress.utils.MessageUtils.error("positive integer only"));
            return true;
        }

        final Deque<Tick> ticks = new ArrayDeque<>();
        final List<Integer> pings = new ArrayList<>();
        final BukkitTask[] ticker = new BukkitTask[1];
        final BukkitTask[] pinger = new BukkitTask[1];

        ticker[0] = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            long now = System.currentTimeMillis();
            double mspt = Bukkit.getServer().getAverageTickTime();
            double cpuSys = OS.getCpuLoad() * 100.0;
            double cpuProc = OS.getProcessCpuLoad() * 100.0;
            ticks.addLast(new Tick(now, mspt, cpuSys, cpuProc));
            long cutoff = now - 15 * 60_000L;
            while (!ticks.isEmpty() && ticks.peekFirst().ts() < cutoff)
                ticks.pollFirst();
        }, 0L, 1L);

        pinger[0] = Bukkit.getScheduler().runTaskTimer(
                Stress.get(), () -> pings.add(player.getPing()), 0L, 20L);

        Stopwatch.start(player, seconds, () -> {
            ticker[0].cancel();
            pinger[0].cancel();
            Stress.get().stopAll();
            printReport(player, seconds, ticks, pings);
        });

        player.sendMessage(me.revqz.stress.utils.MessageUtils.success(
                "Benchmark started for " + Stopwatch.format(seconds * 10)));
        return true;
    }
    // test results priniting format
    private void printReport(Player p, int seconds, Deque<Tick> ticks, List<Integer> pings) {
        long now = System.currentTimeMillis();

        double[] tpsApi = Bukkit.getTPS();
        double   tps5s  = tpsWindow(ticks, now,  5_000L);
        double   tps10s = tpsWindow(ticks, now, 10_000L);

        double[] t10s = tickStats(ticks, now, 10_000L);
        double[] t1m  = tickStats(ticks, now, 60_000L);
        double[] cpu  = cpuWindows(ticks, now);

        Runtime  rt    = Runtime.getRuntime();
        long     used  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long     max   = rt.maxMemory() / (1024 * 1024);
        double   pct   = (double) used / max * 100.0;
        double   usedGB = used / 1024.0;
        double   maxGB  = max  / 1024.0;
        Component bar   = memBar(pct, 20);

        String pingSummary = pings.isEmpty() ? "n/a" : String.format(
                "min=%.0f avg=%.0f max=%.0f ms",
                pings.stream().mapToInt(i -> i).min().orElse(0) * 1.0,
                pings.stream().mapToInt(i -> i).average().orElse(0),
                pings.stream().mapToInt(i -> i).max().orElse(0) * 1.0);

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("TPS from last 5s, 10s, 1m, 5m, 15m:", NamedTextColor.GOLD));
        p.sendMessage(Component.text(String.format(
                "  %.2f, %.2f, %.2f, %.2f, %.2f",
                cap(tps5s), cap(tps10s), tpsApi[0], tpsApi[1], tpsApi[2]), NamedTextColor.WHITE));

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Tick durations (min/med/95%ile/max ms) from last 10s, 1m:", NamedTextColor.GOLD));
        p.sendMessage(Component.text(String.format(
                "  %.1f/%.1f/%.1f/%.1f;  %.1f/%.1f/%.1f/%.1f",
                t10s[0], t10s[1], t10s[2], t10s[3],
                t1m[0],  t1m[1],  t1m[2],  t1m[3]), NamedTextColor.WHITE));

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("CPU usage from last 10s, 1m, 15m:", NamedTextColor.GOLD));
        p.sendMessage(Component.text(String.format("  %.1f%%, %.1f%%, %.1f%%  (system)",  cpu[0], cpu[2], cpu[4]), NamedTextColor.WHITE));
        p.sendMessage(Component.text(String.format("  %.1f%%, %.1f%%, %.1f%%  (process)", cpu[1], cpu[3], cpu[5]), NamedTextColor.WHITE));

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Memory usage:", NamedTextColor.GOLD));
        p.sendMessage(Component.text(String.format("  %.2f GB / %.2f GB  (%.1f%%)", usedGB, maxGB, pct), NamedTextColor.WHITE));
        p.sendMessage(Component.text("  ").append(bar));

        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Ping (" + seconds + "s sample): " + pingSummary, NamedTextColor.GOLD));
        p.sendMessage(Component.empty());
    }

    private double tpsWindow(Deque<Tick> ticks, long now, long windowMs) {
        long cutoff = now - windowMs;
        long count = ticks.stream().filter(t -> t.ts() >= cutoff).count();
        return Math.min(20.0, count / (windowMs / 1000.0));
    }

    private double[] tickStats(Deque<Tick> ticks, long now, long windowMs) {
        long cutoff = now - windowMs;
        List<Double> vals = ticks.stream()
                .filter(t -> t.ts() >= cutoff)
                .map(Tick::mspt)
                .sorted()
                .toList();
        if (vals.isEmpty())
            return new double[] { 0, 0, 0, 0 };
        int n = vals.size();
        return new double[] {
                vals.get(0),
                vals.get(n / 2),
                vals.get((int) Math.min(n - 1, Math.floor(n * 0.95))),
                vals.get(n - 1)
        };
    }

    private double[] cpuWindows(Deque<Tick> ticks, long now) {
        double[] r = new double[6];
        long[] windows = { 10_000L, 60_000L, 15 * 60_000L };
        for (int i = 0; i < windows.length; i++) {
            long cutoff = now - windows[i];
            List<Tick> sl = ticks.stream().filter(t -> t.ts() >= cutoff).toList();
            r[i * 2] = sl.stream().mapToDouble(Tick::cpuSys).average().orElse(0);
            r[i * 2 + 1] = sl.stream().mapToDouble(Tick::cpuProc).average().orElse(0);
        }
        return r;
    }

    private double cap(double v) {
        return Math.min(20.0, v);
    }

    private Component memBar(double pct, int width) {
        int filled = (int) Math.round(pct / 100.0 * width);
        int empty = Math.max(0, width - filled);
        Style on = Style.style(NamedTextColor.GREEN, TextDecoration.STRIKETHROUGH);
        Style off = Style.style(NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH);
        return Component.text("        ".repeat(Math.max(1, filled)), on)
                .append(Component.text("        ".repeat(Math.max(1, empty)), off));
    }
}
