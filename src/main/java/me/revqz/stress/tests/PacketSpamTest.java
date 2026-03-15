package me.revqz.stress.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

public class PacketSpamTest implements Test {

    // 2 000 packets ish per second at 20 TPS
    private static final int PACKETS_PER_TICK = 100;

    private static final Random RNG = new Random();
    private BukkitTask task;
    private final AtomicLong totalSent = new AtomicLong(0);
    private long startMs;

    @Override
    public void start() {
        startMs = System.currentTimeMillis();
        totalSent.set(0);

        task = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty())
                return;

            Player target = players.stream()
                    .skip(RNG.nextInt(players.size()))
                    .findFirst()
                    .orElse(null);
            if (target == null)
                return;

            Object channel = getChannel(target);
            if (channel == null)
                return;

            try {
                Method isActive = channel.getClass().getMethod("isActive");
                if (!(Boolean) isActive.invoke(channel))
                    return;

                Method pipelineMethod = channel.getClass().getMethod("pipeline");
                Object pipeline = pipelineMethod.invoke(channel);
                Method fireRead = pipeline.getClass()
                        .getMethod("fireChannelRead", Object.class);

                for (int i = 0; i < PACKETS_PER_TICK; i++) {
                    Object pkt = (i % 2 == 0)
                            ? buildBlockPlacePacket()
                            : buildWindowClickPacket();

                    if (pkt != null) {
                        fireRead.invoke(pipeline, pkt);
                        totalSent.incrementAndGet();
                    }
                }
            } catch (Exception ignored) {
            }

        }, 0L, 1L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(Stress.get(), () -> {
            long elapsed = (System.currentTimeMillis() - startMs) / 1000;
            if (elapsed == 0)
                return;
            long rate = totalSent.get() / elapsed;
            Stress.get().getLogger().info(
                    "[PacketSpam] sent=" + totalSent.get()
                            + "  rate=" + rate + " pkt/s"
                            + "  elapsed=" + elapsed + "s");
        }, 100L, 100L);
    }

    @Override
    public void stop() {
        if (task != null)
            task.cancel();
        long elapsed = Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
        Stress.get().getLogger().info(
                "[PacketSpam] finished — total=" + totalSent.get()
                        + "  avg=" + (totalSent.get() / elapsed) + " pkt/s");
    }

    @Override
    public String getName() {
        return "packet-spam";
    }

    private Object buildBlockPlacePacket() {
        try {
            Class<?> blockPosClass = nms("core.BlockPos");
            Class<?> dirClass = nms("core.Direction");
            Class<?> vec3Class = nms("world.phys.Vec3");
            Class<?> hitResultClass = nms("world.phys.BlockHitResult");

            Object blockPos = blockPosClass
                    .getConstructor(int.class, int.class, int.class)
                    .newInstance(0, 64, 0);

            Object dirUp = dirClass.getEnumConstants()[1];

            Object hitVec = vec3Class
                    .getConstructor(double.class, double.class, double.class)
                    .newInstance(0.5, 65.0, 0.5);

            Object blockHit = hitResultClass
                    .getConstructor(vec3Class, dirClass, blockPosClass, boolean.class)
                    .newInstance(hitVec, dirUp, blockPos, false);

            Class<?> handClass = nms("world.InteractionHand");
            Object mainHand = handClass.getEnumConstants()[0];

            Class<?> pktClass = nms("network.protocol.game.ServerboundUseItemOnPacket");
            try {
                Constructor<?> ctor = pktClass.getConstructor(handClass, hitResultClass, int.class);
                return ctor.newInstance(mainHand, blockHit, 0);
            } catch (NoSuchMethodException e) {
                Constructor<?> ctor = pktClass.getConstructor(handClass, hitResultClass);
                return ctor.newInstance(mainHand, blockHit);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Object buildWindowClickPacket() {
        try {
            Class<?> pktClass = nms("network.protocol.game.ServerboundContainerClickPacket");
            Class<?> clickClass = nms("world.inventory.ClickType");
            Class<?> itemClass = nms("world.item.ItemStack");
            Object clickType = clickClass.getEnumConstants()[0];

            Object emptyItem;
            try {
                emptyItem = itemClass.getField("EMPTY").get(null);
            } catch (NoSuchFieldException ex) {
                emptyItem = itemClass.getMethod("empty").invoke(null);
            }

            Class<?> mapClass = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap");
            Object changedSlots = mapClass.getConstructor().newInstance();

            try {
                Constructor<?> ctor = pktClass.getConstructor(
                        int.class, int.class, int.class, int.class,
                        clickClass, itemClass,
                        Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap"));
                return ctor.newInstance(1, 0, 0, 0, clickType, emptyItem, changedSlots);
            } catch (NoSuchMethodException ex) {
                Constructor<?> ctor = pktClass.getConstructor(
                        int.class, int.class, int.class,
                        clickClass, itemClass,
                        Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap"));
                return ctor.newInstance(1, 0, 0, clickType, emptyItem, changedSlots);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Object getChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = getField(handle, "c");
            if (connection == null)
                connection = getField(handle, "playerConnection");
            if (connection == null)
                return null;

            Object networkManager = getField(connection, "c");
            if (networkManager == null)
                networkManager = getField(connection, "networkManager");
            if (networkManager == null)
                return null;

            Object channel = getField(networkManager, "m");
            if (channel == null)
                channel = getField(networkManager, "channel");

            return isNettyChannel(channel) ? channel : fallbackChannel(player);
        } catch (Exception ignored) {
            return fallbackChannel(player);
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

    private Object fallbackChannel(Object current) {
        try {
            Object handle = current.getClass().getMethod("getHandle").invoke(current);
            for (Field f1 : handle.getClass().getFields()) {
                Object pConn = safeGet(f1, handle);
                if (pConn == null)
                    continue;
                for (Field f2 : pConn.getClass().getFields()) {
                    Object nMgr = safeGet(f2, pConn);
                    if (nMgr == null)
                        continue;
                    for (Field f3 : nMgr.getClass().getFields()) {
                        Object ch = safeGet(f3, nMgr);
                        if (isNettyChannel(ch))
                            return ch;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isNettyChannel(Object obj) {
        if (obj == null)
            return false;
        try {
            return Class.forName("io.netty.channel.Channel").isInstance(obj);
        } catch (Exception e) {
            return false;
        }
    }

    private Object safeGet(Field f, Object obj) {
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static Class<?> nms(String path) throws ClassNotFoundException {
        // modern versions
        try {
            return Class.forName("net.minecraft." + path);
        } catch (ClassNotFoundException ignored) {
        }

        // legacy versions
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("net.minecraft.server." + version + "." + path.substring(path.lastIndexOf('.') + 1));
    }
}
