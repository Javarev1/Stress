package me.revqz.stress.test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class TestRegistry {

    private final Map<String, Supplier<Test>> registry = new HashMap<>();

    public void register(String name, Supplier<Test> factory) {
        registry.put(name.toLowerCase(), factory);
    }

    // fresh instance
    public Test create(String name) {
        Supplier<Test> factory = registry.get(name.toLowerCase());
        return factory != null ? factory.get() : null;
    }

    // known names
    public Set<String> names() {
        return registry.keySet();
    }
}
