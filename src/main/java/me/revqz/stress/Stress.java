package me.revqz.stress;

// Bukkit
import org.bukkit.plugin.java.JavaPlugin;

// Internal
import me.revqz.stress.command.BenchmarkCommand;
import me.revqz.stress.command.StopwatchCommand;
import me.revqz.stress.command.StressCommand;
import me.revqz.stress.test.Test;
import me.revqz.stress.test.TestRegistry;

import java.util.ArrayList;
import java.util.List;

public final class Stress extends JavaPlugin {

    // Singleton
    private static Stress instance;

    // Active tests
    private final List<Test> tests = new ArrayList<>();

    // Test registry
    private final TestRegistry registry = new TestRegistry();

    // Enable
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getCommand("stopwatch").setExecutor(new StopwatchCommand());
        getCommand("benchmark").setExecutor(new BenchmarkCommand());
        StressCommand stressCmd = new StressCommand();
        getCommand("stress").setExecutor(stressCmd);
        getCommand("stress").setTabCompleter(stressCmd);
        getLogger().info("Stress enabled.");
    }

    // Disable
    @Override
    public void onDisable() {
        stopAll();
        getLogger().info("Stress disabled.");
    }

    // Register test
    public void register(Test test) {
        if (!isTestEnabled(test)) return;
        tests.add(test);
        test.start();
    }

    // Stop all
    public void stopAll() {
        tests.forEach(Test::stop);
        tests.clear();
    }

    // Config check
    private boolean isTestEnabled(Test test) {
        return getConfig().getBoolean("enabled", true)
            && getConfig().getBoolean("tests." + test.getName(), true);
    }

    // Accessor
    public static Stress get() { return instance; }

    // Registry accessor
    public TestRegistry getRegistry() { return registry; }
}
