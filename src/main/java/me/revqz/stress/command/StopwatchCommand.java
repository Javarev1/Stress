package me.revqz.stress.command;

// Bukkit
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// Adventure
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

// Internal
import me.revqz.stress.Stress;
import me.revqz.stress.utils.Stopwatch;

public class StopwatchCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Validate arg
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /stopwatch <seconds>", NamedTextColor.RED));
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
            if (seconds <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Positive integer only.", NamedTextColor.RED));
            return true;
        }

        // Delegate to util
        Stopwatch.start(player, seconds, Stress.get()::stopAll);
        player.sendMessage(Component.text("Stopwatch: " + Stopwatch.format(seconds * 10), NamedTextColor.GREEN));
        return true;
    }
}
