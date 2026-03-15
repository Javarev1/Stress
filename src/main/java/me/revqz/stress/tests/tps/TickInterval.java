package me.revqz.stress.tests.tps;

public class TickInterval {

    private final long start;
    private long end;

    public TickInterval() {
        this.start = System.nanoTime();
    }

    // record end time
    public void end() {
        this.end = System.nanoTime();
    }

    // nanoseconds elapsed
    public long durationNs() {
        return end - start;
    }

    // milliseconds elapsed
    public double durationMs() {
        return durationNs() / 1_000_000.0;
    }
}
