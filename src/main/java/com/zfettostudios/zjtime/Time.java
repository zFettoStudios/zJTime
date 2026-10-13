package com.zfettostudios.zjtime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Высокопроизводительный, мутабельный класс для работы со временем, интервалами,
 * конвертациями и асинхронными задержками с нулевым выделением памяти (Zero-Allocation).
 * <p>
 * Позволяет выполнять операции напрямую с примитивами, парсить строки без создания подстрок
 * и динамически интегрироваться с любыми сторонними типами данных через систему экстракторов.
 *
 * @since 0.1
 * @version 1.2
 * @author RandomShel
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Time implements Comparable<Time> {
    /**
     * Глобальная константа, представляющая нулевую длительность.
     */
    public static final Time ZERO = new Time(0, TimeUnit.NANOSECONDS);

    /**
     * Реестр конвертеров для преобразования наносекунд во внешние типы данных.
     */
    public static final Map<Class<?>, Function<Long, ?>> CONVERTERS = new ConcurrentHashMap<>();

    /**
     * Реестр экстракторов для извлечения времени из сторонних объектов.
     */
    public static final Map<Class<?>, SafeExtractor<?>> EXTRACTORS = new ConcurrentHashMap<>();

    /**
     * Набор поддерживаемых единиц измернеия для динамического парсинга текста.
     */
    public static final Set<TimeUnit> TIME_UNITS = new CopyOnWriteArraySet<>();

    static {
        register(new SafeExtractor<>(Duration.class) {
            @Override
            protected Time extract(Duration duration) {
                if (duration == null || duration.isZero() || duration.isNegative()) return ZERO;
                return new Time(duration.toNanos(), TimeUnit.NANOSECONDS);
            }
        });

        register(new SafeExtractor<>(Number.class) {
            @Override
            protected Time extract(Number number) {
                return of(number, TimeUnit.NANOSECONDS);
            }
        });

        register(new SafeExtractor<>(CharSequence.class) {
            @Override
            protected Time extract(CharSequence charSequence) {
                return parse(charSequence);
            }
        });

        CONVERTERS.put(Duration.class, Duration::ofNanos);
        CONVERTERS.put(Long.class, nanoseconds -> nanoseconds);
    }

    /** Числовое значение длительности во внутренней единице измерения. */
    long value;

    /** Базовая единица измерения, в которой хранится значение {@link #value}. */
    final TimeUnit storageUnit;

    /**
     * Создает объект {@link Time} с указанным числовым значением и единицей измерения.
     * Внутренним хранилищем автоматически назначаются наносекунды.
     *
     * @param value числовое значение времени.
     * @param timeUnit единица измерения переданного значения.
     * @return новый экземпляр {@link Time} (или {@link #ZERO}, если значение null или <= 0).
     */
    public static Time of(Number value, TimeUnit timeUnit) {
        return of(value, timeUnit, TimeUnit.NANOSECONDS);
    }

    /**
     * Создает объект {@link Time} с явным указанием единицы измерения и целевого хранилища.
     *
     * @param value числовое значение времени.
     * @param timeUnit единица измерения переданного значения.
     * @param storageUnit желаемая единица внутреннего хранения.
     * @return новый экземпляр {@link Time}.
     */
    public static Time of(Number value, TimeUnit timeUnit, TimeUnit storageUnit) {
        if (value == null || value.doubleValue() <= 0.0) return ZERO;
        return new Time(storageUnit.convert(value, timeUnit, Long.class), storageUnit);
    }

    /**
     * Универсальный фабричный метод. Создает объект {@link Time} из объекта **любого** зарегистрированного типа
     * (будь то число, строка, {@link Duration} или ваш кастомный класс).
     *
     * @param inputClass класс входного объекта.
     * @param inputObject сам объект, содержащий информацию о времени.
     * @param <T> тип входных данных.
     * @return готовый объект {@link Time}.
     * @throws IllegalArgumentException если класс не зарегистрирован в системе экстракторов.
     */
    public static <T> Time of(Class<T> inputClass, T inputObject) {
        if (inputObject == null) return ZERO;
        if (inputObject instanceof Time time) return time;

        SafeExtractor<?> extractor = EXTRACTORS.get(inputClass);
        if (extractor == null) {
            extractor = EXTRACTORS.entrySet().parallelStream()
                .filter(entry -> entry.getKey().isAssignableFrom(inputClass))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }

        if (extractor == null) throw new IllegalArgumentException("Тип не зарегистрирован для создания Time: " + inputClass.getName());

        return extractor.process(inputObject);
    }

    /**
     * Регистрирует новый кастомный экстрактор для конвертации сторонних объектов в {@link Time}.
     *
     * @param extractor экземпляр вашего экстрактора.
     * @param <T> обрабатываемый класс.
     * @return переданный экстрактор для удобства цепочек вызовов.
     */
    public static <T> SafeExtractor<T> register(SafeExtractor<T> extractor) {
        EXTRACTORS.put(extractor.targetClass, extractor);
        return extractor;
    }

    /**
     * Регистрирует функцию конвертации внутреннего времени (в наносекундах) во внешний класс.
     *
     * @param targetClass целевой класс (например, ваш кастомный класс таймера).
     * @param converterFunction лямбда, принимающая `long` наносекунд и возвращающая ваш объект.
     * @param <T> целевой тип.
     * @return зарегистрированная функция.
     */
    public static <T> Function<Long, T> register(Class<T> targetClass, Function<Long, T> converterFunction) {
        CONVERTERS.put(targetClass, converterFunction);
        return converterFunction;
    }

    /**
     * Добавляет кастомную единицу измерения в глобальный пул парсинга текстов.
     *
     * @param timeUnit  единица измерения.
     * @return добавленная единица.
     */
    public static TimeUnit addTimeUnit(TimeUnit timeUnit) {
        if (timeUnit != null) TIME_UNITS.add(timeUnit);
        return timeUnit;
    }

    /**
     * Удаляет единицу измерения из пула парсинга.
     */
    public static TimeUnit removeTimeUnit(TimeUnit timeUnit) {
        if (timeUnit != null) TIME_UNITS.remove(timeUnit);
        return timeUnit;
    }

    /**
     * Абстрактный инструмент безопасности для создания экстракторов из сторонних классов без опасных кастов.
     *
     * @param <T> целевой тип данных.
     */
    public abstract static class SafeExtractor<T> {
        private final Class<T> targetClass;

        protected SafeExtractor(Class<T> targetClass) {
            this.targetClass = targetClass;
        }

        public Time process(Object untypedInput) {
            return extract(targetClass.cast(untypedInput));
        }

        protected abstract Time extract(T typedInput);
    }

    /**
     * Возвращает общее количество наносекунд текущего времени (чистый примитив `long`).
     */
    public long toNanoseconds() {
        return storageUnit.getNanoseconds() * value;
    }

    /**
     * Конвертирует текущее время в заданную единицу измерения и числовой тип.
     *
     * @param timeUnit в какую единицу перевести (например, {@link TimeUnit#SECONDS}).
     * @param target в какой класс завернуть результат (например, {@link Double#getClass()}, {@link Long#getClass()}).
     * @param <T> числовой тип.
     * @return число в нужном формате.
     */
    public <T extends Number> T to(TimeUnit timeUnit, Class<T> target) {
        Utils.OptionalException.of()
            .objects(timeUnit, target)
            .arguments("timeUnit", "target")
            .isNull();

        return timeUnit.convert(value, storageUnit, target);
    }

    /**
     * Парсит строковое представление времени (например: "10s", "2.5m", "500ms") в объект {@link Time}.
     *
     * @param input текст для парсинга.
     * @return новый экземпляр {@link Time}.
     */
    public static Time parse(CharSequence input) {
        if (input == null || input.isEmpty()) return ZERO;

        int textCharIndex = getFirstTextIndex(input);
        if (textCharIndex <= 0) return of(parseDoubleDirectly(input, 0, input.length()), TimeUnit.SECONDS);

        double value = parseDoubleDirectly(input, 0, textCharIndex);
        int inputLength = input.length();
        int suffixLength = inputLength - textCharIndex;

        for (TimeUnit timeUnit : TIME_UNITS) {
            CharSequence unitSuffix = timeUnit.getSuffix();
            if (unitSuffix.length() == suffixLength && regionMatchesIgnoreCase(input, textCharIndex, unitSuffix, 0, suffixLength))
                return of(value, timeUnit);
        }

        return of(value, TimeUnit.SECONDS);
    }

    private static int getFirstTextIndex(CharSequence input) {
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char charAt = input.charAt(i);
            if ((charAt < '0' || charAt > '9') && charAt != '.' && charAt != ',') return i;
        }

        return -1;
    }

    private static double parseDoubleDirectly(CharSequence input, int start, int end) {
        while (start < end && Character.isWhitespace(input.charAt(start))) start++;
        while (end > start && Character.isWhitespace(input.charAt(end - 1))) end--;

        if (start >= end) return 0.0;

        long integerPart = 0;
        long fractionalPart = 0;
        int fractionalDivisor = 1;
        boolean isFraction = false;

        for (int i = start; i < end; i++) {
            char currentChar = input.charAt(i);
            if (currentChar >= '0' && currentChar <= '9') {
                if (!isFraction) integerPart = integerPart * 10 + (currentChar - '0');
                else {
                    fractionalPart = fractionalPart * 10 + (currentChar - '0');
                    fractionalDivisor *= 10;
                }
            }
            else if (currentChar == '.' || currentChar == ',') isFraction = true;
        }

        if (!isFraction) return (double) integerPart;
        return integerPart + ((double) fractionalPart / fractionalDivisor);
    }

    private static boolean regionMatchesIgnoreCase(CharSequence charSequence, int offset, CharSequence charSequenceSecond, int offsetSecond, int length) {
        if (offset < 0 || offsetSecond < 0 || offset + length > charSequence.length() || offsetSecond + length > charSequenceSecond.length()) return false;

        for (int i = 0; i < length; i++) {
            char charFirst = charSequence.charAt(offset + i);
            char charSecond = charSequenceSecond.charAt(offsetSecond + i);

            if (charFirst == charSecond) continue;

            char upper = Character.toUpperCase(charFirst);
            char upperSecond = Character.toUpperCase(charSecond);

            if (upper != upperSecond && Character.toLowerCase(upper) != Character.toLowerCase(upperSecond)) return false;
        }

        return true;
    }

    /**
     * Создает объект {@link Time}, равный текущей системной наносекундной отметке ( {@link System#nanoTime()} ).
     * Идеально для использования в качестве таймеров и бенчмарков.
     */
    public static Time now() {
        return of(System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    /**
     * Мутирует текущий объект, устанавливая в него новое значение.
     */
    public Time set(Number value, TimeUnit timeUnit) {
        checkNotConstantZero();
        this.value = Math.max(0, storageUnit.convert(value, timeUnit, Long.class));
        return this;
    }

    /**
     * Прибавляет к текущему времени другой объект времени. Мутирует текущий экземпляр.
     */
    public Time plus(Object other) {
        return combine(resolveDuration(other), true);
    }

    /**
     * Вычитает из текущего времени другой объект времени. Мутирует текущий экземпляр.
     */
    public Time minus(Object other) {
        return combine(resolveDuration(other), false);
    }

    /**
     * Умножает текущую длительность на указанный коэффициент.
     */
    public Time modifyByFactor(double factor) {
        checkNotConstantZero();
        if (factor <= 0.0) {
            value = 0L;
            return this;
        }
        value = Math.max(0L, Math.round(value * factor));

        return this;
    }

    private Time combine(Time other, boolean isAddition) {
        checkNotConstantZero();
        if (other == null || other.value == 0) return this;

        long otherInOurUnits = storageUnit.convert(other.value, other.storageUnit, Long.class);
        if (isAddition) value += otherInOurUnits;
        else value = Math.max(0L, value - otherInOurUnits);
        return this;
    }

    private void checkNotConstantZero() {
        if (this == ZERO) throw new UnsupportedOperationException("Нельзя изменять глобальную константу Time.ZERO!");
    }

    /**
     * Универсальный метод вычисления абсолютной разницы (модуля) между двумя временными объектами.
     * Может вызываться как статически: {@code Time.between(t1, t2)}, так и от экземпляра: {@code t1.between(t2)}.
     *
     * @param args принимает один объект (разница с этим инстансом) или два объекта (разница между ними).
     * @return новый объект {@link Time} с абсолютной разницей.
     */
    public static Time between(Object... args) {
        if (args == null || args.length == 0) return ZERO;
        if (args.length == 1) {
            Time second = resolveDuration(args[0]);
            if (second == null) return ZERO;
            return of(Math.abs(ZERO.toNanoseconds() - second.toNanoseconds()), TimeUnit.NANOSECONDS);
        }
        Time first = resolveDuration(args[0]);
        Time second = resolveDuration(args[1]);
        if (first == null || second == null) return ZERO;

        long diffNanos = Math.abs(first.toNanoseconds() - second.toNanoseconds());
        return of(diffNanos, TimeUnit.NANOSECONDS);
    }

    public Time between(Object other) {
        return between(this, other);
    }

    /**
     * Универсальный динамический метод сравнения времени.
     * Поддерживает сравнение текущего объекта с переданным по правилам:
     * '>' (isAfter), '<' (isBefore), '>=' (isAtLeast), '<=' (isAtMost).
     *
     * @param other объект для сравнения.
     * @return результат сравнения по стандарту {@link Long#compare}.
     */
    public int compareWith(Object other) {
        Time resolved = resolveDuration(other);
        if (resolved == null) return 1;
        return Long.compare(toNanoseconds(), resolved.toNanoseconds());
    }

    public boolean isAfter(Object other) { return compareWith(other) > 0; }
    public boolean isBefore(Object other) { return compareWith(other) < 0; }
    public boolean isAtLeast(Object other) { return compareWith(other) >= 0; }
    public boolean isAtMost(Object other) { return compareWith(other) <= 0; }

    /**
     * Универсальный супер-метод проверки истечения времени (таймаута).
     * Может принимать разное количество аргументов благодаря динамическому {@code Object... args}:
     * <ul>
     *     <li>{@code isTimeUp(startTime)} — проверяет, истек ли текущий объект-интервал с момента {@code startTime}.</li>
     *     <li>{@code isTimeUp(startTime, duration)} — проверяет, истек ли кастомный интервал {@code duration} с момента {@code startTime}.</li>
     *     <li>{@code isTimeUp(startTimeNanos)} — принимает сырой {@code long} наносекунд.</li>
     * </ul>
     *
     * @param args аргументы проверки (старт и опционально длительность).
     * @return {@code true}, если время вышло.
     */
    public boolean isTimeUp(Object... args) {
        if (args == null || args.length == 0 || args[0] == null) return true;

        long startNanos;
        if (args[0] instanceof Number num && args.length == 1) startNanos = num.longValue();
        else startNanos = toNanosecondsSafe(args[0]);

        long durationNanos = (args.length > 1 && args[1] != null)
            ? Objects.requireNonNull(resolveDuration(args[1])).toNanoseconds()
            : this.toNanoseconds();

        if (durationNanos <= 0) return true;

        if (Long.MAX_VALUE - durationNanos < startNanos) return false;

        return System.nanoTime() >= (startNanos + durationNanos);
    }

    /**
     * Статическая перегрузка для проверки тайм-аута без создания инстанса {@link Time}.
     */
    public static boolean checkTimeUp(Object startTime, Object duration) {
        Time resolvedDuration = resolveDuration(duration);
        if (resolvedDuration == null) return true;
        return resolvedDuration.isTimeUp(startTime);
    }

    /**
     * Универсальный метод расчета оставшегося времени.
     * Принимает старт и опциональную длительность. Возвращает объект {@link Time} с остатком или {@link #ZERO}.
     */
    public static Time remaining(Object... args) {
        if (args == null || args.length == 0 || args[0] == null) return ZERO;

        Time startTime = resolveDuration(args[0]);
        Time duration = (args.length > 1 && args[1] != null) ? resolveDuration(args[1]) : ZERO;

        if (startTime == null || duration == null) return ZERO;

        long startNanos = startTime.toNanoseconds();
        long durationNanos = duration.toNanoseconds();
        long currentNanos = System.nanoTime();

        if (Long.MAX_VALUE - durationNanos < startNanos) return duration;

        long remainingNanos = (startNanos + durationNanos) - currentNanos;
        return remainingNanos <= 0 ? ZERO : of(remainingNanos, TimeUnit.NANOSECONDS);
    }

    public Time remaining(Object startTime) {
        return remaining(startTime, this);
    }

    private static long toNanosecondsSafe(Object object) {
        return switch (object) {
            case null -> 0L;
            case Time time -> time.toNanoseconds();
            case Number number -> number.longValue();
            case Duration duration -> duration.toNanos();
            case CharSequence seq -> parse(seq.toString()).toNanoseconds();
            default -> {
                Time resolved = resolveDuration(object);
                yield resolved != null ? resolved.toNanoseconds() : 0L;
            }
        };
    }

    private static Time resolveDuration(Object object) {
        return switch (object) {
            case null -> null;
            case Time time -> time;
            case Number number -> of(number, TimeUnit.NANOSECONDS);
            case Duration duration -> of(Duration.class, duration);
            case CharSequence seq -> parse(seq.toString());
            default -> {
                SafeExtractor<?> extractor = EXTRACTORS.get(object.getClass());
                if (extractor == null) {
                    for (Map.Entry<Class<?>, SafeExtractor<?>> entry : EXTRACTORS.entrySet()) {
                        if (entry.getKey().isAssignableFrom(object.getClass())) {
                            extractor = entry.getValue();
                            break;
                        }
                    }
                }
                yield extractor != null ? extractor.process(object) : null;
            }
        };
    }

    /**
     * Асинхронно выполняет задачу с возвращаемым результатом после задержки, равной этому времени.
     */
    public <T> CompletableFuture<T> delayAsync(Supplier<T> taskSupplier) {
        return CompletableFuture.supplyAsync(
            taskSupplier,
            CompletableFuture.delayedExecutor(toNanoseconds(), java.util.concurrent.TimeUnit.NANOSECONDS)
        );
    }

    /**
     * Асинхронно выполняет задачу (Runnable) после задержки.
     */
    public CompletableFuture<Void> delayAsync(Runnable runnableTask) {
        return delayAsync(() -> {
            runnableTask.run();
            return null;
        });
    }

    /**
     * Планирует периодическое выполнение задачи с фиксированным интервалом через переданный планировщик.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(ScheduledExecutorService executorService, Runnable periodicTask) {
        long intervalNanos = toNanoseconds();
        return executorService.scheduleAtFixedRate(periodicTask, intervalNanos, intervalNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Time other) {
        return other == null ? 1 : Long.compare(toNanoseconds(), other.toNanoseconds());
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Time time) return value == time.value && storageUnit == time.storageUnit;

        return false;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(toNanoseconds());
    }

    @Override
    public String toString() {
        return value + " " + storageUnit.getName();
    }
}