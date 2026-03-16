package me.revqz.stress.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.revqz.stress.Stress;
import me.revqz.stress.utils.Stopwatch;
import me.revqz.stress.utils.MessageUtils;

public class StopwatchCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // validation of argument
        if (args.length != 1) {
            player.sendMessage(MessageUtils.error("Usage: /stopwatch <seconds>"));
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
            if (seconds <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.error("Positive integer only."));
            return true;
        }

        // Delegate to util
        Stopwatch.start(player, seconds, Stress.get()::stopAll);
        player.sendMessage(MessageUtils.success("Stopwatch: " + Stopwatch.format(seconds * 10)));
        return true;
    }
}
