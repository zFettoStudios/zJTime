package com.zfettostudios.zjtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Мутабельный класс для удобной, высокопроизводительной и безопасной работы со временем,
 * интервалами, конвертациями и асинхронными задержками.
 *
 * @since 0.1
 * @version 1.1.1
 * @author RandomShel
 */
public class Time implements Comparable<Time> {
    /**
     * Константа нулевой длительности времени.
     */
    public static final Time ZERO = new Time(0, TimeUnit.NANOSECONDS);

    /**
     * Реестр функций конвертеров для преобразования {@link Time} во внешние типы данных.
     */
    public static final Map<Class<?>, Function<Long, ?>> CONVERTERS = new ConcurrentHashMap<>();

    /**
     * Реестр экстракторов для создания {@link Time} из внешних типов данных.
     */
    public static final Map<Class<?>, SafeExtractor<?>> EXTRACTORS = new ConcurrentHashMap<>();

    /**
     * Реестр поддерживаемых единиц измерения времени для парсинга текста.
     *
     * @since 1.1
     */
    public static final List<TimeUnit> TIME_UNITS = new CopyOnWriteArrayList<>();

    static {
        register(new SafeExtractor<>(Duration.class) {
            @Override
            protected Time extract(Duration duration) {
                if (duration.isZero() || duration.isNegative()) return ZERO;
                return new Time(duration.toNanos(), TimeUnit.NANOSECONDS);
            }
        });

        CONVERTERS.put(Duration.class, Duration::ofNanos);
        CONVERTERS.put(Long.class, nanoseconds -> nanoseconds);
    }

    /**
     * Внутреннее числовое значение длительности.
     */
    private long value;

    /**
     * Единица измерения внутреннего хранения значения.
     */
    private final TimeUnit storageUnit;

    /**
     * Создает экземпляр {@link Time} с указанным значением и единицей хранения.
     *
     * @param value значение времени.
     * @param storageUnit единица хранения.
     */
    private Time(long value, TimeUnit storageUnit) {
        this.value = value;
        this.storageUnit = storageUnit;
    }

    /**
     * Создает новый экземпляр {@link Time} с базовой единицей хранения в наносекундах.
     *
     * @param value числовое значение (поддерживает {@code long}, {@code double}, {@code int} и др.).
     * @param timeUnit единица измерения передаваемого значения.
     * @return новый объект {@link Time}.
     */
    public static Time of(Number value, TimeUnit timeUnit) {
        return of(value, timeUnit, TimeUnit.NANOSECONDS);
    }

    /**
     * Создает новый экземпляр {@link Time} с явным указанием единицы внутреннего хранения.
     *
     * @param value числовое значение.
     * @param timeUnit единица измерения передаваемого значения.
     * @param storageUnit желаемая единица внутреннего хранения.
     * @return новый объект {@link Time}.
     */
    public static Time of(Number value, TimeUnit timeUnit, TimeUnit storageUnit) {
        if (value == null || value.doubleValue() <= 0.0) return ZERO;
        return new Time(storageUnit.convertLong(value, timeUnit), storageUnit);
    }

    /**
     * Создает объект {@link Time} из стороннего зарегистрированного типа данных.
     *
     * @param inputClass класс входного объекта.
     * @param inputObject экземпляр входного объекта.
     * @param <T> тип входного объекта.
     * @return новый объект {@link Time}.
     * @throws IllegalArgumentException если тип {@code inputClass} не был зарегистрирован в реестре экстракторов.
     */
    public static <T> Time of(Class<T> inputClass, T inputObject) {
        if (inputObject == null) return ZERO;

        SafeExtractor<?> extractor = EXTRACTORS.get(inputClass);
        if (extractor == null) throw new IllegalArgumentException("Unregistered type for creating Time: " + inputClass.getName());

        return extractor.process(inputObject);
    }

    /**
     * Парсит текстовое представление времени с суффиксом в новый объект {@link Time}.
     *
     * @param input строка с описанием времени (например, "10s", "2.5m").
     * @return новый объект {@link Time}.
     */
    public static Time parse(String input) {
        if (input == null || input.isBlank()) return ZERO;

        int textCharIndex = getFirstTextIndex(input);

        if (textCharIndex <= 0) return of(parseDoubleDirectly(input, 0, input.length()), TimeUnit.SECONDS);

        double value = parseDoubleDirectly(input, 0, textCharIndex);
        int inputLength = input.length();
        int suffixLength = inputLength - textCharIndex;

        for (TimeUnit timeUnit : TIME_UNITS) {
            String unitSuffix = timeUnit.getSuffix();

            if (
                unitSuffix.length() == suffixLength &&
                input.regionMatches(true, textCharIndex, unitSuffix, 0, suffixLength)
            ) return of(value, timeUnit);
        }

        return of(value, TimeUnit.SECONDS);
    }

    /**
     * Возвращает индекс первого найденого текстового символа.
     *
     * @return строка вида "10s" или "500ms".
     * @since 1.1
     */
    private static int getFirstTextIndex(String input) {
        if (input == null || input.isEmpty()) return -1;

        int length = input.length();
        for (int i = 0; i < length; i++) {
            char charAt = input.charAt(i);
            if ((charAt < '0' || charAt > '9') && charAt != '.' && charAt != ',') return i;
        }

        return -1;
    }

    /**
     * Парсит {@code double} напрямую из диапазона символов строки без вызова {@code substring} и {@code trim}.
     *
     * @param input строка-источник.
     * @param start начальный индекс включительно.
     * @param end конечный индекс исключительно.
     * @return распарсенное значение типа {@code double}.
     * @since 1.1
     */
    private static double parseDoubleDirectly(String input, int start, int end) {
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

    /**
     * Регистрирует кастомный экстрактор для создания {@link Time} из неродных типов объектов.
     *
     * @param extractor экстрактор типа {@link SafeExtractor}.
     * @param <T> тип целевого класса.
     */
    public static <T> void register(SafeExtractor<T> extractor) {
        EXTRACTORS.put(extractor.targetClass, extractor);
    }

    /**
     * Регистрирует функцию конвертации внутреннего времени в наносекундах во внешний тип объекта.
     *
     * @param targetClass класс целевого объекта.
     * @param converterFunction функция преобразования (принимает {@code long} наносекунд).
     * @param <T> тип целевого объекта.
     */
    public static <T> void register(Class<T> targetClass, Function<Long, T> converterFunction) {
        CONVERTERS.put(targetClass, converterFunction);
    }

    /**
     * Возвращает общее количество наносекунд длительности.
     *
     * @return общее количество наносекунд.
     */
    public long toNanoseconds() {
        return storageUnit.getNanoseconds() * value;
    }

    /**
     * Возвращает значение времени, переведенное в запрашиваемую единицу измерения (дробное).
     *
     * @param timeUnit целевая единица измерения времени.
     * @return значение времени в виде {@code double}.
     */
    public double to(TimeUnit timeUnit) {
        return timeUnit.convert(value, storageUnit);
    }

    /**
     * Возвращает значение времени, переведенное в запрашиваемую единицу измерения (целочисленное).
     *
     * @param timeUnit целевая единица измерения времени.
     * @return значение времени в виде {@code long}.
     * @since 1.1
     */
    public long toLong(TimeUnit timeUnit) {
        return timeUnit.convertLong(value, storageUnit);
    }

    /**
     * Преобразует объект {@link Time} в зарегистрированный внешний класс (например, {@link Duration}).
     *
     * @param targetClass целевой класс для конвертации.
     * @param <T> тип целевого объекта.
     * @return объект целевого типа.
     * @throws IllegalArgumentException если для указанного класса не зарегистрирован конвертер.
     */
    public <T> T to(Class<T> targetClass) {
        Function<Long, ?> converter = CONVERTERS.get(targetClass);
        if (converter == null) throw new IllegalArgumentException("Unregistered conversion type: " + targetClass.getName());

        return targetClass.cast(converter.apply(toNanoseconds()));
    }

    /**
     * Изменяет текущее значение длительности объекта.
     *
     * @param value новое значение.
     * @param timeUnit единица измерения передаваемого значения.
     * @return текущий экземпляр {@code this} с обновленным значением.
     * @since 1.1
     */
    public Time set(long value, TimeUnit timeUnit) {
        checkNotConstantZero();
        this.value = Math.max(0, storageUnit.convertLong(value, timeUnit));
        return this;
    }

    /**
     * Прибавляет длительность переданного объекта к текущему объекту без создания новых объектов.
     *
     * @param other добавляемый объект времени.
     * @return текущий экземпляр {@code this} с обновленной длительностью.
     */
    public Time plus(Time other) {
        return combine(other, true);
    }

    /**
     * Вычитает длительность переданного объекта из текущего объекта без создания новых объектов.
     *
     * @param other вычитаемый объект времени.
     * @return текущий экземпляр {@code this} с обновленной длительностью.
     */
    public Time minus(Time other) {
        return combine(other, false);
    }

    /**
     * Умножает текущую длительность объекта на указанный коэффициент.
     *
     * @param factor коэффициент умножения (например, 0.5 для уменьшения вдвое или 2.0 для удвоения).
     * @return текущий экземпляр {@code this} с обновленной длительностью.
     */
    public Time modifyByFactor(double factor) {
        checkNotConstantZero();
        if (factor <= 0) {
            this.value = 0;
            return this;
        }

        this.value = Math.max(0, Math.round(this.value * factor));
        return this;
    }

    /**
     * Внутренний метод мутации состояния при сложении или вычитании.
     */
    private Time combine(Time other, boolean isAddition) {
        checkNotConstantZero();
        if (other == null || other.value == 0) return this;

        long otherInOurUnits = storageUnit.convertLong(other.value, other.storageUnit);
        if (isAddition) this.value += otherInOurUnits;
        else this.value = Math.max(0, this.value - otherInOurUnits);

        return this;
    }

    /**
     * Проверяет, что текущий объект не является константой {@link #ZERO}.
     *
     * @throws UnsupportedOperationException если попытка изменить константу {@link #ZERO}.
     */
    private void checkNotConstantZero() {
        if (this == ZERO) throw new UnsupportedOperationException("You must not modify the global constant Time.ZERO!");
    }

    /**
     * Вычисляет абсолютную разницу (модуль) между двумя объектами времени.
     *
     * @param first первый объект времени.
     * @param second второй объект времени.
     * @return новый экземпляр {@link Time}, равный абсолютной разнице.
     */
    public static Time differenceBetween(Time first, Time second) {
        if (first == null || second == null) return ZERO;
        return of(Math.abs(first.toNanoseconds() - second.toNanoseconds()), TimeUnit.NANOSECONDS);
    }

    /**
     * Проверяет, превышает ли текущая длительность указанную.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущая длительность строго больше {@code other}.
     */
    public boolean isAfter(Time other) {
        return other != null && toNanoseconds() > other.toNanoseconds();
    }

    /**
     * Проверяет, меньше ли текущая длительность, чем указанная.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущая длительность строго меньше {@code other}.
     */
    public boolean isBefore(Time other) {
        return other != null && toNanoseconds() < other.toNanoseconds();
    }

    /**
     * Проверяет, больше ли текущая длительность или равна указанной.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущая длительность больше или равна {@code other}.
     */
    public boolean isAtLeast(Time other) {
        return other != null && toNanoseconds() >= other.toNanoseconds();
    }

    /**
     * Проверяет, меньше ли текущая длительность или равна указанной.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущая длительность меньше или равна {@code other}.
     */
    public boolean isAtMost(Time other) {
        return other != null && toNanoseconds() <= other.toNanoseconds();
    }

    /**
     * Проверяет, истек ли временной интервал от заданной временной метки старта до текущего момента.
     *
     * @param startTime метка времени отсчета (в наносекундах {@link System#nanoTime()}).
     * @return {@code true}, если с момента {@code startTime} прошло больше времени, чем текущая длительность.
     * @since 1.1.1
     */
    public boolean isExpiredFrom(Time startTime) {
        return startTime == null || System.nanoTime() >= (startTime.toNanoseconds() + toNanoseconds());
    }

    /**
     * Вычисляет оставшееся время до завершения интервала от момента старта.
     *
     * @param startTime метка времени отсчета.
     * @param duration общая длительность таймера.
     * @return новый объект {@link Time} с оставшимся временем или с нулевой длительностью, если время истекло.
     */
    public static Time getRemainingTime(Time startTime, Time duration) {
        if (startTime == null || duration == null) return ZERO;
        long remaining = (startTime.toNanoseconds() + duration.toNanoseconds()) - System.nanoTime();

        return remaining <= 0L ? ZERO : new Time(remaining, TimeUnit.NANOSECONDS);
    }

    /**
     * Выполняет асинхронную задачу с возвращаемым результатом с задержкой, равной текущей длительности.
     *
     * @param taskSupplier поставщик (supplier) значения для асинхронного выполнения.
     * @param <T> тип возвращаемого результата.
     * @return {@link CompletableFuture}, который завершится по истечении задержки.
     */
    public <T> CompletableFuture<T> delayAsync(Supplier<T> taskSupplier) {
        return CompletableFuture.supplyAsync(
            taskSupplier,
            CompletableFuture.delayedExecutor(toNanoseconds(), java.util.concurrent.TimeUnit.NANOSECONDS)
        );
    }

    /**
     * Выполняет асинхронную задачу без возвращаемого значения с задержкой, равной текущей длительности.
     *
     * @param runnableTask задача для асинхронного выполнения.
     * @return {@link CompletableFuture}, представляющий состояние выполнения задержки.
     */
    public CompletableFuture<Void> delayAsync(Runnable runnableTask) {
        return delayAsync(() -> {
            runnableTask.run();
            return null;
        });
    }

    /**
     * Запускает периодическое выполнение задачи с фиксированным интервалом.
     *
     * @param executorService сервис планирования потоков.
     * @param periodicTask периодически выполняемая задача.
     * @return {@link ScheduledFuture}, позволяющий управлять расписанием выполнения задачи.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(ScheduledExecutorService executorService, Runnable periodicTask) {
        long intervalNanos = toNanoseconds();
        return executorService.scheduleAtFixedRate(periodicTask, intervalNanos, intervalNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * Сравнивает текущий объект времени с другим по длительности.
     *
     * @param other объект для сравнения.
     * @return отрицательное число, ноль или положительное число при сравнении.
     */
    @Override
    public int compareTo(Time other) {
        return other == null ? 1 : Long.compare(toNanoseconds(), other.toNanoseconds());
    }

    /**
     * Проверяет равенство двух объектов времени по их полной длительности в наносекундах.
     *
     * @param object проверяемый объект.
     * @return {@code true}, если объекты эквивалентны по времени.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        return this.toNanoseconds() == ((Time) object).toNanoseconds();
    }

    /**
     * Возвращает хэш-код на основе общего количества наносекунд.
     *
     * @return хэш-код объекта.
     */
    @Override
    public int hashCode() {
        return Long.hashCode(toNanoseconds());
    }

    /**
     * Возвращает строковое представление длительности и суффикса единицы хранения.
     *
     * @return строка вида "10 s" или "500 ms".
     */
    @Override
    public String toString() {
        return value + " " + storageUnit.getSuffix();
    }

    /**
     * Абстрактный базовый класс для безопасного извлечения времени из сторонних объектов.
     *
     * @param <T> поддерживаемый тип входных данных.
     */
    public abstract static class SafeExtractor<T> {

        /**
         * Целевой класс, с которым работает экстрактор.
         */
        private final Class<T> targetClass;

        /**
         * Конструктор экстрактора.
         *
         * @param targetClass класс целевого объекта.
         */
        protected SafeExtractor(Class<T> targetClass) {
            this.targetClass = targetClass;
        }

        /**
         * Безопасно приводит нетипизированный объект к целевому типу и запускает процесс извлечения.
         *
         * @param untypedInput исходный объект.
         * @return полученный {@link Time} или нулевой {@link Time}, если входной объект равен {@code null}.
         */
        public Time process(Object untypedInput) {
            if (untypedInput == null) return ZERO;
            return extract(targetClass.cast(untypedInput));
        }

        /**
         * Метод извлечения длительности из гарантированно типизированного не-null объекта.
         *
         * @param typedInput типизированный экземпляр объекта.
         * @return объект {@link Time}.
         */
        protected abstract Time extract(T typedInput);
    }
}