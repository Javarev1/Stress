package me.revqz.stress;

// Bukkit
import org.bukkit.plugin.java.JavaPlugin;

// Internal
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

    // Register test
    public void register(Test test) {
        if (!isTestEnabled(test))
            return;
        tests.add(test);
        test.start();
    }

    public void stopAll() {
        tests.forEach(Test::stop);
        tests.clear();
    }

    private boolean isTestEnabled(Test test) {
        return getConfig().getBoolean("enabled", true)
                && getConfig().getBoolean("tests." + test.getName(), true);
    }

    public static Stress get() {
        return instance;
    }

    // Registry accessor
    public TestRegistry getRegistry() {
        return registry;
    }
}
