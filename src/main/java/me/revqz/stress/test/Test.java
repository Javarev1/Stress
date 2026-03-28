package me.revqz.stress.test;

public interface Test {
    default void setup() {}

    void start();

    void stop();

    default void cleanup() {}

    String getName();
}
