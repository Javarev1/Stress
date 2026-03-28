package me.revqz.stress.tests;

import java.util.List;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class TicksTest implements Test {

    private int count;

    @Override
    public void setup() {
        count = Stress.get().getConfig().getInt("tests.ticks.count", 100);
    }

    @Override
    public void start() {
        List<Double> ticks = Stress.get().getTickProfiler().getLastTicks(count);

        if (ticks.isEmpty()) {
            Bukkit.broadcast(MessageUtils.info("No tick data yet — wait a moment and try again."));
            return;
        }

        Bukkit.broadcast(MessageUtils.prefix()
                .append(Component.text(
                        "Last " + ticks.size() + " ticks (green ≤50ms, yellow ≤100ms, red >100ms)",
                        MessageUtils.TEXT)));

        int batchSize = 100;
        for (int start = 0; start < ticks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, ticks.size());
            Component line = Component.empty();
            for (int i = start; i < end; i++) {
                line = line.append(Component.text("█", msptColor(ticks.get(i))));
            }
            Bukkit.broadcast(line);
        }
    }

    @Override
    public void stop() {
    }

    private TextColor msptColor(double mspt) {
        if (mspt <= 50) return NamedTextColor.GREEN;
        if (mspt <= 100) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    @Override
    public String getName() {
        return "ticks";
    }
}
