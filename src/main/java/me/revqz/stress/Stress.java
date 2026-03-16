package me.revqz.stress;

import org.bukkit.plugin.java.JavaPlugin;

import me.revqz.stress.command.BenchmarkCommand;
import me.revqz.stress.command.StopwatchCommand;
import me.revqz.stress.command.StressCommand;
import me.revqz.stress.test.Test;
import me.revqz.stress.test.TestRegistry;
import me.revqz.stress.tests.ChunkGenTest;
import me.revqz.stress.tests.ChunkLoadTest;
import me.revqz.stress.tests.CommandTest;
import me.revqz.stress.tests.EntityTest;
import me.revqz.stress.tests.InvalidTest;
import me.revqz.stress.tests.NBTDataTest;
import me.revqz.stress.tests.PacketSpamTest;
import me.revqz.stress.tests.TestArgument;
import me.revqz.stress.tests.TicksTest;
import me.revqz.stress.tests.TpsTest;

import java.util.ArrayList;
import java.util.List;

public final class Stress extends JavaPlugin {

    private static Stress instance;

    // active tests
    private final List<Test> tests = new ArrayList<>();

    // test registry
    private final TestRegistry registry = new TestRegistry();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
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
        getLogger().info("Stress disabled.");
    }

    // register test
    public void register(Test test, int timeoutSeconds) {
        if (!isTestEnabled(test))
            return;
            
        test.setup();
        tests.add(test);
        test.start();
        
        if (timeoutSeconds > 0) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(this, () -> {
                if (tests.contains(test)) {
                    stopTest(test);
                    getServer().broadcast(me.revqz.stress.utils.MessageUtils.info("Test stopped (Timeout reached): " + test.getName()));
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
        return getConfig().getBoolean("enabled", true)
                && getConfig().getBoolean("tests." + test.getName(), true);
    }

    public static Stress get() {
        return instance;
    }

    // registry accessor
    public TestRegistry getRegistry() {
        return registry;
    }
}
