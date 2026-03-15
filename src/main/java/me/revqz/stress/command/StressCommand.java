package me.revqz.stress.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class StressCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text(
                "Usage: /stress <test_name>", NamedTextColor.RED));
            return true;
        }

        String name = args[0].toLowerCase();
        Test test = Stress.get().getRegistry().create(name);

        if (test == null) {
            player.sendMessage(Component.text(
                "Unknown test: " + name, NamedTextColor.RED));
            player.sendMessage(Component.text(
                "Available: " + Stress.get().getRegistry().names(), NamedTextColor.GRAY));
            return true;
        }

        Stress.get().register(test);
        player.sendMessage(Component.text(
            "Started: " + name, NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String name : Stress.get().getRegistry().names()) {
                if (name.startsWith(prefix)) matches.add(name);
            }
            return matches;
        }
        return List.of();
    }
}
