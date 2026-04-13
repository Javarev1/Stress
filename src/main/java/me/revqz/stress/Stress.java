package me.revqz.stress;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.revqz.stress.command.BenchmarkCommand;
import me.revqz.stress.command.StopwatchCommand;
import me.revqz.stress.command.StressCommand;
import me.revqz.stress.data.MongoManager;
import me.revqz.stress.test.Test;
import me.revqz.stress.test.TestRegistry;
import me.revqz.stress.tests.ChunkGenTest;
import me.revqz.stress.tests.ChunkLoadTest;
import me.revqz.stress.tests.CommandTest;
import me.revqz.stress.tests.EntityTest;
import me.revqz.stress.tests.InvalidTest;
import me.revqz.stress.tests.NBTDataTest;
import me.revqz.stress.tests.PacketSpamTest;
import me.revqz.stress.tests.PlayerJoinTest;
import me.revqz.stress.tests.TestArgument;
import me.revqz.stress.tests.TicksTest;
import me.revqz.stress.tests.TpsTest;
import me.revqz.stress.tests.tps.TickProfiler;

import java.util.ArrayList;
import java.util.List;

public final class Stress extends JavaPlugin {

    private static Stress instance;

    private final List<Test> tests = new ArrayList<>();
    private final TestRegistry registry = new TestRegistry();
    private final TickProfiler tickProfiler = new TickProfiler();
    private final MongoManager mongoManager = new MongoManager();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        tickProfiler.start();
        mongoManager.connect();

        registry.register("chunk-gen", ChunkGenTest::new);
        registry.register("chunk-load", ChunkLoadTest::new);
        registry.register("entity", EntityTest::new);
        registry.register("command", CommandTest::new);
        registry.register("invalid", InvalidTest::new);
        registry.register("nbt-data", NBTDataTest::new);
        registry.register("packet-spam", PacketSpamTest::new);
        registry.register("argument", TestArgument::new);
        registry.register("ticks", TicksTest::new);
        registry.register("tps", TpsTest::new);
        registry.register("player-join", PlayerJoinTest::new);

        getCommand("stopwatch").setExecutor(new StopwatchCommand());
        getCommand("benchmark").setExecutor(new BenchmarkCommand());
        StressCommand stressCmd = new StressCommand();
        getCommand("stress").setExecutor(stressCmd);
        getCommand("stress").setTabCompleter(stressCmd);
        getLogger().info("Stress enabled.");
    }

    @Override
    public void onDisable() {
        stopAll();
        tickProfiler.stop();
        mongoManager.disconnect();
        getLogger().info("Stress disabled.");
    }

    public void register(Test test, int timeoutSeconds) {
        if (!isTestEnabled(test))
            return;

        test.setup();
        tests.add(test);
        test.start();

        if (timeoutSeconds > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (tests.contains(test)) {
                    stopTest(test);
                    getServer().broadcast(me.revqz.stress.utils.MessageUtils.info("Stopped test (timeout): " + test.getName()));
                }
            }, timeoutSeconds * 20L);
        }
    }

    public void stopTest(Test test) {
        if (tests.remove(test)) {
            test.stop();
            test.cleanup();
        }
    }

    public void stopAll() {
        for (Test test : new ArrayList<>(tests)) {
            stopTest(test);
        }
    }

    private boolean isTestEnabled(Test test) {
        return getConfig().getBoolean("enabled", true);
    }

    public static Stress get() {
        return instance;
    }

    public TestRegistry getRegistry() {
        return registry;
    }

    public TickProfiler getTickProfiler() {
        return tickProfiler;
    }

    public MongoManager getMongoManager() {
        return mongoManager;
    }
}
