package me.revqz.stress.tests;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class TpsTest implements Test {

    private int intervalSeconds;
    private BukkitTask task;

    @Override
    public void setup() {
        intervalSeconds = Stress.get().getConfig().getInt("tests.tps.report-interval", 5);
    }

    @Override
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), this::printReport, 20L, intervalSeconds * 20L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        printReport();
    }

    private void printReport() {
        double mspt1 = Stress.get().getTickProfiler().getAverageMspt(20);
        double mspt5 = Stress.get().getTickProfiler().getAverageMspt(100);
        double mspt15 = Stress.get().getTickProfiler().getAverageMspt(300);
        double tps1 = Stress.get().getTickProfiler().msptToTps(mspt1);
        double tps5 = Stress.get().getTickProfiler().msptToTps(mspt5);
        double tps15 = Stress.get().getTickProfiler().msptToTps(mspt15);

        Component msg = MessageUtils.prefix()
                .append(Component.text("TPS ", MessageUtils.TEXT))
                .append(tpsComponent(tps1)).append(Component.text(", ", MessageUtils.TEXT))
                .append(tpsComponent(tps5)).append(Component.text(", ", MessageUtils.TEXT))
                .append(tpsComponent(tps15))
                .append(Component.text("  MSPT ", MessageUtils.TEXT))
                .append(msptComponent(mspt1)).append(Component.text(", ", MessageUtils.TEXT))
                .append(msptComponent(mspt5)).append(Component.text(", ", MessageUtils.TEXT))
                .append(msptComponent(mspt15));

        Bukkit.broadcast(msg);
    }

    private Component tpsComponent(double tps) {
        TextColor color = tps >= 19.5 ? NamedTextColor.GREEN : tps >= 16 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text(String.format("%.1f", tps), color);
    }

    private Component msptComponent(double mspt) {
        TextColor color = mspt <= 50 ? NamedTextColor.GREEN : mspt <= 100 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text(String.format("%.1fms", mspt), color);
    }

    @Override
    public String getName() {
        return "tps";
    }
}
