package me.revqz.stress.tests.tps;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import me.revqz.stress.Stress;

public class ServerTickEndEventTickProfiler implements Listener {

    private double lastMspt;

    // Start listening
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, Stress.get());
    }

    // Stop listening
    public void stop() {
        HandlerList.unregisterAll(this);
    }

    // Record exact duration
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        lastMspt = event.getTickDuration();
    }

    // Latest MSPT
    public double getMspt() {
        return lastMspt;
    }
}
