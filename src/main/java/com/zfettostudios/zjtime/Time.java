package com.zfettostudios.zjtime;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class Time implements Comparable<Time> {
    public static final Time ZERO = new Time(0L);
    private static final Map<Class<?>, Function<Long, ?>> CONVERTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, SafeExtractor<?>> EXTRACTORS = new ConcurrentHashMap<>();

    private final long nanoseconds;

    static {
        register(new SafeExtractor<>(Duration.class) {
            @Override
            protected Time extract(Duration duration) {
                if (duration == null || duration.isZero() || duration.isNegative()) return ZERO;
                return new Time(duration.toNanos());
            }
        });

        CONVERTERS.put(Duration.class, Duration::ofNanos);
        CONVERTERS.put(Long.class, nanos -> nanos);
    }

    private Time(long nanoseconds) {
        this.nanoseconds = Math.max(0L, nanoseconds);
    }

    public static Time of(long value, TimeUnit unit) {
        Objects.requireNonNull(unit, "TimeUnit не может быть null");
        if (value <= 0L) return ZERO;
        return new Time(unit.toNanos(value));
    }

    public static <T> Time of(Class<T> clazz, T input) {
        Objects.requireNonNull(clazz, "Class не может быть null");
        if (input == null) return ZERO;

        SafeExtractor<?> extractor = EXTRACTORS.get(clazz);
        if (extractor == null) throw new IllegalArgumentException("Незарегистрированный тип для создания Time: " + clazz.getName());

        return extractor.process(input);
    }

    public long to(TimeUnit unit) {
        Objects.requireNonNull(unit, "TimeUnit не может быть null");
        return unit.convert(nanoseconds, TimeUnit.NANOSECONDS);
    }

    public <T> T to(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Class не может быть null");
        Function<Long, ?> converter = CONVERTERS.get(clazz);

        if (converter == null) throw new IllegalArgumentException("Незарегистрированный тип для конвертации: " + clazz.getName());

        return clazz.cast(converter.apply(nanoseconds));
    }

    public boolean isAfter(Time other) {
        return other != null && this.nanoseconds > other.nanoseconds;
    }

    public boolean isBefore(Time other) {
        return other != null && this.nanoseconds < other.nanoseconds;
    }

    public boolean isAtLeast(Time other) {
        return other != null && this.nanoseconds >= other.nanoseconds;
    }

    public boolean isAtMost(Time other) {
        return other != null && this.nanoseconds <= other.nanoseconds;
    }

    public boolean isExpiredFrom(Time startTime) {
        if (startTime == null) return true;
        return System.nanoTime() >= (startTime.nanoseconds + this.nanoseconds);
    }

    public static Time getRemainingTime(Time startTime, Time duration) {
        if (startTime == null || duration == null) return ZERO;

        long remainingNanos = (startTime.nanoseconds + duration.nanoseconds) - System.nanoTime();
        return remainingNanos <= 0L ? ZERO : new Time(remainingNanos);
    }

    private static <T> void register(SafeExtractor<T> extractor) {
        EXTRACTORS.put(extractor.clazz, extractor);
    }

    private static <T> void registerConverter(Class<T> clazz, Function<Long, T> converter) {
        CONVERTERS.put(clazz, converter);
    }

    @Override
    public int compareTo(Time other) {
        return Long.compare(this.nanoseconds, other.nanoseconds);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Time time = (Time) object;
        return nanoseconds == time.nanoseconds;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(nanoseconds);
    }

    @Override
    public String toString() {
        return nanoseconds + " ns";
    }

    public abstract static class SafeExtractor<T> {
        private final Class<T> clazz;

        protected SafeExtractor(Class<T> clazz) {
            this.clazz = Objects.requireNonNull(clazz, "Class не может быть null");
        }

        public Time process(Object input) {
            return extract(clazz.cast(input));
        }

        protected abstract Time extract(T input);
    }
}