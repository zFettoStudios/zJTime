package com.zfettostudios.zjtime;

import lombok.Getter;

public enum TimeUnit {
    NANOSECONDS(1L),
    MICROSECONDS(1_000L),
    MILLISECONDS(1_000_000L),
    SECONDS(1_000_000_000L),
    MINUTES(60_000_000_000L),
    HOURS(3_600_000_000_000L),
    DAYS(86_400_000_000_000L),

    MINECRAFT_TICKS(50_000_000L);

    @Getter
    private final long nanos;
    private final double invNanos;

    TimeUnit(long nanos) {
        this.nanos = nanos;
        this.invNanos = 1.0 / nanos;
    }

    public long toNanos(long duration) {
        return duration * this.nanos;
    }

    public double toNanos(double duration) {
        return duration * this.nanos;
    }

    public double fromNanos(long nanos) {
        return nanos * this.invNanos;
    }
}
