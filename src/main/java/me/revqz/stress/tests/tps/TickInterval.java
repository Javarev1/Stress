package me.revqz.stress.tests.tps;

public class TickInterval {

    private final long start;
    private long end;

    // Init start time
    public TickInterval() {
        this.start = System.nanoTime();
    }

    // Record end time
    public void end() {
        this.end = System.nanoTime();
    }

    // Nanoseconds elapsed
    public long durationNs() {
        return end - start;
    }

    // Milliseconds elapsed
    public double durationMs() {
        return durationNs() / 1_000_000.0;
    }
}
