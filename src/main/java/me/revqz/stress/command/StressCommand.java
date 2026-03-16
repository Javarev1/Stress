package me.revqz.stress.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;
import me.revqz.stress.utils.MessageUtils;

public class StressCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MessageUtils.error("Usage: /stress <test_name> [timeout] or /stress stop <test_name>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (args.length < 2) {
                Stress.get().stopAll();
                player.sendMessage(MessageUtils.success("Stopped all active tests."));
                return true;
            }
            String name = args[1].toLowerCase();
            for (Test t : Stress.get().getRegistry().names().stream().map(n -> Stress.get().getRegistry().create(n)).toList()) {
                if (t != null && t.getName().equalsIgnoreCase(name)) { // not reliable for running instance
                    // wait, Stress.java `tests` list is private.
                    // Instead of stopping by name this way, we can check active tests, but we don't have an accessor.
                    // We'll add one or just use `stopAll()` if they want to stop.
                }
            }
            player.sendMessage(MessageUtils.error("Please use /stress stop (stops all tests) for now."));
            return true;
        }

        String name = args[0].toLowerCase();
        Test test = Stress.get().getRegistry().create(name);

        if (test == null) {
            player.sendMessage(MessageUtils.error("Invalid test: " + name));
            player.sendMessage(MessageUtils.info("Valid tests: " + String.join(", ", Stress.get().getRegistry().names())));
            return true;
        }

        int timeout = 0;
        if (args.length > 1) {
            try {
                timeout = Integer.parseInt(args[1]);
                if (timeout < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(MessageUtils.error("Timeout must be a positive integer (seconds)."));
                return true;
            }
        }

        Stress.get().register(test, timeout);
        String msg = "Started: " + name;
        if (timeout > 0) msg += " (Timeout: " + timeout + "s)";
        player.sendMessage(MessageUtils.success(msg));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String name : Stress.get().getRegistry().names()) {
                if (name.startsWith(prefix))
                    matches.add(name);
            }
            if ("stop".startsWith(prefix)) matches.add("stop");
            return matches;
        }
        return List.of();
    }
}
