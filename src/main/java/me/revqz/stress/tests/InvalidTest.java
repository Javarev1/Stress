package me.revqz.stress.tests;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Method;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class InvalidTest implements Test {

    private static final int INJECT_COUNT = 50;
    private static final Random RNG = new Random();

    private BukkitTask task;

    @Override
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            // random online player (track)
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty())
                return;
            Player target = players.stream()
                    .skip(RNG.nextInt(players.size()))
                    .findFirst()
                    .orElse(null);

            if (target != null) {
                Object channel = getChannel(target);
                if (channel != null) {
                    try {
                        Method isActive = channel.getClass().getMethod("isActive");
                        if (!(Boolean) isActive.invoke(channel))
                            return;

                        Method pipelineMethod = channel.getClass().getMethod("pipeline");
                        Object pipeline = pipelineMethod.invoke(channel);
                        Method fireChannelRead = pipeline.getClass().getMethod("fireChannelRead", Object.class);

                        Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
                        Method wrappedBuffer = unpooledClass.getMethod("wrappedBuffer", byte[].class);

                        for (int i = 0; i < INJECT_COUNT; i++) {
                            byte[] garbage = new byte[RNG.nextInt(10, 100)];
                            RNG.nextBytes(garbage);
                            Object badBuf = wrappedBuffer.invoke(null, garbage);
                            fireChannelRead.invoke(pipeline, badBuf);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }, 0L, 1L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
    }

    @Override
    public String getName() {
        return "invalid";
    }

    // reflection to find the Netty channel
    private Object getChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = getField(handle, "c"); // connection
            if (connection == null)
                connection = getField(handle, "playerConnection");
            if (connection == null)
                return null;

            Object networkManager = getField(connection, "c"); // networkManager
            if (networkManager == null)
                networkManager = getField(connection, "networkManager");
            if (networkManager == null)
                return null;

            Object channel = getField(networkManager, "m"); // channel
            if (channel == null)
                channel = getField(networkManager, "channel");

            return isChannel(channel) ? channel : null;
        } catch (Exception ignored) {
            return fallbackChannelFind(player);
        }
    }

    private Object getField(Object obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    // generic search for Channel field
    private Object fallbackChannelFind(Object current) {
        try {
            Object handle = current.getClass().getMethod("getHandle").invoke(current);
            for (Field f1 : handle.getClass().getFields()) {
                Object pConn = get(f1, handle);
                if (pConn == null)
                    continue;
                for (Field f2 : pConn.getClass().getFields()) {
                    Object nManager = get(f2, pConn);
                    if (nManager == null)
                        continue;
                    for (Field f3 : nManager.getClass().getFields()) {
                        Object ch = get(f3, nManager);
                        if (isChannel(ch))
                            return ch;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isChannel(Object obj) {
        if (obj == null)
            return false;
        try {
            return Class.forName("io.netty.channel.Channel").isInstance(obj);
        } catch (Exception e) {
            return false;
        }
    }

    private Object get(Field f, Object obj) {
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
