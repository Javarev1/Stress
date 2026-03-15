package me.revqz.stress.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.revqz.stress.Stress;

public final class Stopwatch {

    private static BukkitTask active;

    // start countdown
    public static void start(Player player, int seconds, Runnable onFinish) {
        cancel();

        final int[] tenths = { seconds * 10 };

        active = new BukkitRunnable() {
            @Override
            public void run() {
                if (tenths[0] <= 0) {
                    player.sendActionBar(Component.text("Done!", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f); // 1 decimal plac
                    active = null;
                    cancel();
                    if (onFinish != null)
                        onFinish.run();
                    return;
                }
                Component msg = Component.text("sᴛᴏᴘᴡᴀᴛᴄʜ ", net.kyori.adventure.text.format.TextColor.color(0x4A90E2))
                        .append(Component.text("| ", NamedTextColor.WHITE))
                        .append(Component.text(format(tenths[0]), NamedTextColor.GRAY));
                player.sendActionBar(msg);
                tenths[0]--;
            }
        }.runTaskTimer(Stress.get(), 0L, 2L);
    }

    public static void cancel() {
        if (active != null) {
            active.cancel();
            active = null;
        }
    }

    // formatting
    public static String format(int tenths) {
        if (tenths >= 36000) {
            int h = tenths / 36000; // hours
            int m = (tenths % 36000) / 600; // minutes
            int s = (tenths % 600) / 10; // seconds
            if (m == 0 && s == 0)
                return h + "h";
            if (s == 0)
                return h + "h " + m + "m";
            return h + "h " + m + "m " + s + "s";
        } else if (tenths >= 600) {
            int m = tenths / 600;
            int s = (tenths % 600) / 10;
            if (s == 0)
                return m + "m";
            return m + "m " + s + "s";
        } else {
            return String.format("%.1fs", tenths / 10.0);
        }
    }

    private Stopwatch() {
    }
}
