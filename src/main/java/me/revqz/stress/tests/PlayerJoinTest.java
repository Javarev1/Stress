package me.revqz.stress.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.revqz.stress.Stress;
import me.revqz.stress.test.Test;

/**
 * PlayerJoinTest
 *
 * Stresses the server's player-join / player-leave pipeline by repeatedly:
 *   1. Firing the NMS "ClientboundPlayerInfoUpdatePacket" (ADD_PLAYER) for a
 *      batch of fake UUIDs so every real online player receives a playerlist
 *      add entry.
 *   2. A few ticks later, firing the matching REMOVE packet.
 *
 * This exercises:
 *   - PlayerList serialisation & tracking
 *   - Netty pipeline write-through for each real player
 *   - Event-queue pressure (no real login sequence → pure packet pressure)
 *
 * The test is version-adaptive: it tries the 1.19.3+ packet API first,
 * then falls back to the legacy 1.8–1.19.2 TabList packet approach.
 */
public class PlayerJoinTest implements Test {

    private int batchPerTick;
    private int fakePool;
    private static final long REMOVE_DELAY_TICKS = 5L;

    private final AtomicLong totalCycles = new AtomicLong(0);
    private long startMs;
    private BukkitTask addTask;
    private BukkitTask statsTask;

    private final List<UUID> fakeUUIDs = new ArrayList<>();

    @Override
    public void setup() {
        batchPerTick = Stress.get().getConfig().getInt("tests.player-join.batch-per-tick", 20);
        fakePool = Stress.get().getConfig().getInt("tests.player-join.fake-pool", 200);
        for (int i = 0; i < fakePool; i++) {
            fakeUUIDs.add(UUID.randomUUID());
        }
    }

    @Override
    public void start() {
        startMs = System.currentTimeMillis();
        totalCycles.set(0);

        // Main tick task – fires ADD then schedules a REMOVE a few ticks later.
        addTask = Bukkit.getScheduler().runTaskTimer(Stress.get(), () -> {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            if (online.isEmpty()) return;

            int offset = (int) (totalCycles.get() % (fakePool - batchPerTick));
            List<UUID> batch = fakeUUIDs.subList(offset, offset + batchPerTick);

            // Send ADD to every real player.
            for (Player player : online) {
                sendAddPackets(player, batch);
            }

            totalCycles.incrementAndGet();

            // Schedule the matching REMOVE.
            Bukkit.getScheduler().runTaskLater(Stress.get(), () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendRemovePackets(player, batch);
                }
            }, REMOVE_DELAY_TICKS);

        }, 0L, 1L);

        // Async stats logger every 5 s.
        statsTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Stress.get(), () -> {
            long elapsed = Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
            long total   = totalCycles.get();
            Stress.get().getLogger().info(String.format(
                    "[PlayerJoin] cycles=%d  fake-adds=%d  rate=%.1f cycles/s  elapsed=%ds",
                    total, total * batchPerTick, (double) total / elapsed, elapsed));
        }, 100L, 100L);
    }

    @Override
    public void stop() {
        if (addTask  != null) addTask.cancel();
        if (statsTask != null) statsTask.cancel();

        // Clean up any lingering fake entries for online players.
        Bukkit.getScheduler().runTask(Stress.get(), () -> {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            if (!online.isEmpty()) {
                for (Player player : online) {
                    sendRemovePackets(player, fakeUUIDs);
                }
            }
        });

        long elapsed = Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
        Stress.get().getLogger().info(String.format(
                "[PlayerJoin] finished — total cycles=%d  total fake-adds=%d  avg=%.1f cycles/s",
                totalCycles.get(),
                totalCycles.get() * batchPerTick,
                (double) totalCycles.get() / elapsed));
    }

    @Override
    public String getName() {
        return "player-join";
    }

    // -------------------------------------------------------------------------
    //  Packet helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to send a "player added to tab-list" packet to {@code recipient}
     * for each UUID in {@code uuids}. Tries modern (1.19.3+) API first, then
     * falls back to legacy approaches.
     */
    private void sendAddPackets(Player recipient, List<UUID> uuids) {
        try {
            if (!trySendModernAdd(recipient, uuids)) {
                trySendLegacyAdd(recipient, uuids);
            }
        } catch (Exception ignored) {}
    }

    private void sendRemovePackets(Player recipient, List<UUID> uuids) {
        try {
            if (!trySendModernRemove(recipient, uuids)) {
                trySendLegacyRemove(recipient, uuids);
            }
        } catch (Exception ignored) {}
    }

    // --- Modern (1.19.3+): ClientboundPlayerInfoUpdatePacket / ClientboundPlayerInfoRemovePacket ---

    private boolean trySendModernAdd(Player recipient, List<UUID> uuids) {
        try {
            // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, List<PlayerUpdate>)
            Class<?> updatePktClass = nms("network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> actionClass    = nms("network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entryClass     = nms("network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");

            // Action.ADD_PLAYER
            Object addAction = null;
            for (Object constant : actionClass.getEnumConstants()) {
                if (constant.toString().equals("ADD_PLAYER")) { addAction = constant; break; }
            }
            if (addAction == null) return false;

            java.util.EnumSet<?> actions = enumSetOf(actionClass, addAction);

            // Build minimal Entry list.
            List<Object> entries = new ArrayList<>();
            Constructor<?> entryCtor = findEntryCtor(entryClass);
            if (entryCtor == null) return false;

            for (UUID uuid : uuids) {
                entries.add(buildEntry(entryCtor, entryClass, uuid));
            }

            // Construct packet.
            Constructor<?> pktCtor = updatePktClass.getDeclaredConstructor(
                    java.util.EnumSet.class, java.util.List.class);
            pktCtor.setAccessible(true);
            Object pkt = pktCtor.newInstance(actions, entries);

            sendPacket(recipient, pkt);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean trySendModernRemove(Player recipient, List<UUID> uuids) {
        try {
            Class<?> removePktClass = nms("network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Constructor<?> ctor = removePktClass.getDeclaredConstructor(java.util.List.class);
            ctor.setAccessible(true);
            Object pkt = ctor.newInstance(new ArrayList<>(uuids));
            sendPacket(recipient, pkt);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Legacy (≤ 1.19.2): PacketPlayOutPlayerInfo ---

    private boolean trySendLegacyAdd(Player recipient, List<UUID> uuids) {
        try {
            Class<?> pktClass = nmsLegacy("PacketPlayOutPlayerInfo");
            // Enum: EnumPlayerInfoAction
            Class<?> actionEnum = null;
            for (Class<?> inner : pktClass.getDeclaredClasses()) {
                if (inner.isEnum() && inner.getSimpleName().contains("Action")) {
                    actionEnum = inner; break;
                }
            }
            if (actionEnum == null) return false;

            Object addAction = null;
            for (Object c : actionEnum.getEnumConstants()) {
                if (c.toString().contains("ADD")) { addAction = c; break; }
            }
            if (addAction == null) return false;

            Constructor<?> ctor = pktClass.getDeclaredConstructor(actionEnum);
            ctor.setAccessible(true);
            Object pkt = ctor.newInstance(addAction);

            // Populate the players list inside the packet with fake profile stubs.
            Field bField = findField(pktClass, "b"); // list of PlayerInfoData
            if (bField == null) return false;
            bField.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Object> dataList = (List<Object>) bField.get(pkt);

            Class<?> dataClass = null;
            for (Class<?> inner : pktClass.getDeclaredClasses()) {
                if (!inner.isEnum()) { dataClass = inner; break; }
            }
            if (dataClass == null) return false;

            for (UUID uuid : uuids) {
                Object data = buildLegacyPlayerInfoData(dataClass, pkt, uuid);
                if (data != null) dataList.add(data);
            }

            sendPacket(recipient, pkt);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean trySendLegacyRemove(Player recipient, List<UUID> uuids) {
        try {
            Class<?> pktClass = nmsLegacy("PacketPlayOutPlayerInfo");
            Class<?> actionEnum = null;
            for (Class<?> inner : pktClass.getDeclaredClasses()) {
                if (inner.isEnum() && inner.getSimpleName().contains("Action")) {
                    actionEnum = inner; break;
                }
            }
            if (actionEnum == null) return false;

            Object removeAction = null;
            for (Object c : actionEnum.getEnumConstants()) {
                if (c.toString().contains("REMOVE")) { removeAction = c; break; }
            }
            if (removeAction == null) return false;

            Constructor<?> ctor = pktClass.getDeclaredConstructor(actionEnum);
            ctor.setAccessible(true);
            Object pkt = ctor.newInstance(removeAction);

            Field bField = findField(pktClass, "b");
            if (bField == null) return false;
            bField.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Object> dataList = (List<Object>) bField.get(pkt);

            Class<?> dataClass = null;
            for (Class<?> inner : pktClass.getDeclaredClasses()) {
                if (!inner.isEnum()) { dataClass = inner; break; }
            }
            if (dataClass == null) return false;

            for (UUID uuid : uuids) {
                Object data = buildLegacyPlayerInfoData(dataClass, pkt, uuid);
                if (data != null) dataList.add(data);
            }

            sendPacket(recipient, pkt);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    //  Low-level helpers
    // -------------------------------------------------------------------------

    /** Send an NMS packet to a Bukkit player. */
    private void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);

        // Try the 1.20+ connection field first.
        Object conn = safeGetField(handle, "c");
        if (conn == null) conn = safeGetField(handle, "playerConnection");
        if (conn == null) conn = safeGetField(handle, "b");
        if (conn == null) return;

        // conn.send(packet) or conn.sendPacket(packet)
        try {
            Method send = conn.getClass().getMethod("send",
                    nms("network.protocol.Packet"));
            send.invoke(conn, packet);
        } catch (Exception ex) {
            try {
                Method sendPacket = conn.getClass().getMethod("sendPacket",
                        nmsLegacy("Packet"));
                sendPacket.invoke(conn, packet);
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private java.util.EnumSet<?> enumSetOf(Class<?> enumClass, Object value) throws Exception {
        return java.util.EnumSet.of((Enum) value);
    }

    private Constructor<?> findEntryCtor(Class<?> entryClass) {
        for (Constructor<?> c : entryClass.getDeclaredConstructors()) {
            c.setAccessible(true);
            if (c.getParameterCount() >= 1) return c;
        }
        return null;
    }

    /** Build a minimal modern Entry with just a UUID and a GameProfile stub. */
    private Object buildEntry(Constructor<?> entryCtor, Class<?> entryClass, UUID uuid) throws Exception {
        // Entry typically holds: UUID, GameProfile, latency, gameMode, displayName, listed, etc.
        // We pass nulls / defaults for everything except UUID.
        Object[] args = new Object[entryCtor.getParameterCount()];
        Class<?>[] params = entryCtor.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (params[i] == UUID.class) {
                args[i] = uuid;
            } else if (params[i] == boolean.class) {
                args[i] = true;
            } else if (params[i] == int.class) {
                args[i] = 0;
            } else {
                args[i] = null; // GameProfile, Component, ClientInformation, etc.
            }
        }
        return entryCtor.newInstance(args);
    }

    /** Build a legacy PlayerInfoData stub with just enough for REMOVE to work. */
    private Object buildLegacyPlayerInfoData(Class<?> dataClass, Object pkt, UUID uuid) {
        try {
            // Try to find a ctor accepting (PacketPlayOutPlayerInfo, GameProfile, int, ...)
            for (Constructor<?> c : dataClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Object[] args = new Object[c.getParameterCount()];
                Class<?>[] params = c.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (params[i].getSimpleName().equals("GameProfile")) {
                        // Build a minimal GameProfile.
                        args[i] = buildGameProfile(uuid);
                    } else if (params[i] == UUID.class) {
                        args[i] = uuid;
                    } else if (params[i] == int.class) {
                        args[i] = 0;
                    } else if (params[i] == boolean.class) {
                        args[i] = false;
                    } else if (params[i].getName().contains("PacketPlayOutPlayerInfo")) {
                        args[i] = pkt;
                    } else {
                        args[i] = null;
                    }
                }
                return c.newInstance(args);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object buildGameProfile(UUID uuid) throws Exception {
        // com.mojang.authlib.GameProfile(UUID, String)
        Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
        return gpClass.getConstructor(UUID.class, String.class)
                .newInstance(uuid, "FakePlayer-" + uuid.toString().substring(0, 6));
    }

    private Field findField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private Object safeGetField(Object obj, String name) {
        try {
            // Search class hierarchy.
            Class<?> c = obj.getClass();
            while (c != null) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        return f.get(obj);
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // --- NMS class resolution ---

    private static Class<?> nms(String path) throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft." + path);
        } catch (ClassNotFoundException ignored) {}
        // Legacy obfuscated path
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        String simple  = path.substring(path.lastIndexOf('.') + 1);
        return Class.forName("net.minecraft.server." + version + "." + simple);
    }

    private static Class<?> nmsLegacy(String simpleName) throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft." + simpleName);
        } catch (ClassNotFoundException ignored) {}
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("net.minecraft.server." + version + "." + simpleName);
    }
}
