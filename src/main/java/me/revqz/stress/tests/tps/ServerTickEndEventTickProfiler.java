package me.revqz.stress.tests.tps;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import me.revqz.stress.Stress;

public class ServerTickEndEventTickProfiler implements Listener {

    private double lastMspt;

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, Stress.get());
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    // record exact duration
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        lastMspt = event.getTickDuration();
    }

    // latest mspt
    public double getMspt() {
        return lastMspt;
    }
}
