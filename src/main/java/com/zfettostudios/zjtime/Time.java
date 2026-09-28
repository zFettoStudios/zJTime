package com.zfettostudios.zjtime;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Иммутабельный класс для удобной, высокопроизводительной и безопасной работы со временем,
 * интервалами, конвертациями и асинхронными задержками.
 *
 * @since 0.1
 * @version 1.0
 * @author RandomShel
 */
public class Time implements Comparable<Time> {
    /**
     * Константа нулевой длительности времени.
     */
    public static final Time ZERO = new Time(0, StorageUnit.NANOSECONDS);

    /**
     * Реестр функций-конвертеров для преобразования {@link Time} во внешние типы данных.
     */
    private static final Map<Class<?>, Function<Long, ?>> CONVERTERS = new ConcurrentHashMap<>();

    /**
     * Реестр экстракторов для создания {@link Time} из внешних типов данных.
     */
    private static final Map<Class<?>, SafeExtractor<?>> EXTRACTORS = new ConcurrentHashMap<>();

    static {
        registerExtractor(new SafeExtractor<>(Duration.class) {
            @Override
            protected Time extract(Duration duration) {
                if (duration.isZero() || duration.isNegative()) return ZERO;
                return new Time(duration.toNanos(), StorageUnit.NANOSECONDS);
            }
        });

        CONVERTERS.put(Duration.class, Duration::ofNanos);
        CONVERTERS.put(Long.class, nanoseconds -> nanoseconds);
    }

    /**
     * Внутреннее числовое значение длительности.
     */
    private final long value;

    /**
     * Единица измерения внутреннего хранения значения.
     */
    private final StorageUnit storageUnit;

    /**
     * Приватный конструктор.
     *
     * @param value       значение времени.
     * @param storageUnit единица хранения.
     */
    private Time(long value, StorageUnit storageUnit) {
        this.value = value;
        this.storageUnit = storageUnit;
    }

    /**
     * Создает экземпляр {@link Time} с базовой единицей хранения в наносекундах.
     *
     * @param value    числовое значение (поддерживает {@code long}, {@code double}, {@code int} и др.).
     * @param timeUnit единица измерения передаваемого значения.
     * @return объект {@link Time} или {@link #ZERO}, если значение {@code null} или {@code <= 0}.
     */
    public static Time of(Number value, TimeUnit timeUnit) {
        return of(value, timeUnit, StorageUnit.NANOSECONDS);
    }

    /**
     * Создает экземпляр {@link Time} с явным указанием единицы внутреннего хранения.
     *
     * @param value       числовое значение.
     * @param timeUnit    единица измерения передаваемого значения.
     * @param storageUnit желаемая единица внутреннего хранения.
     * @return объект {@link Time} или {@link #ZERO}, если значение {@code null} или {@code <= 0}.
     */
    public static Time of(Number value, TimeUnit timeUnit, StorageUnit storageUnit) {
        if (value == null || value.doubleValue() <= 0.0) return ZERO;
        return new Time(storageUnit.convertToStorage(value, timeUnit), storageUnit);
    }

    /**
     * Создает объект {@link Time} из стороннего зарегистрированного типа данных.
     *
     * @param inputClass  класс входного объекта.
     * @param inputObject экземпляр входного объекта.
     * @param <T>         тип входного объекта.
     * @return объект {@link Time} или {@link #ZERO}, если входной объект равен {@code null}.
     * @throws IllegalArgumentException если тип {@code inputClass} не был зарегистрирован в реестре экстракторов.
     */
    public static <T> Time of(Class<T> inputClass, T inputObject) {
        if (inputObject == null) return ZERO;

        SafeExtractor<?> extractor = EXTRACTORS.get(inputClass);
        if (extractor == null) throw new IllegalArgumentException("Unregistered type for creating Time: " + inputClass.getName());

        return extractor.process(inputObject);
    }

    /**
     * Парсит текстовое представление времени с суффиксом в объект {@link Time}.
     * <p>
     * Поддерживаемые суффиксы:
     * <ul>
     *     <li>{@code ns} — наносекунды</li>
     *     <li>{@code us} — микросекунды</li>
     *     <li>{@code ms} — миллисекунды</li>
     *     <li>{@code s} — секунды</li>
     *     <li>{@code m} — минуты</li>
     *     <li>{@code h} — часы</li>
     *     <li>{@code d} — дни</li>
     *     <li>{@code mine_ticks} — игровые тики Minecraft</li>
     * </ul>
     * Если суффикс отсутствует, значение интерпретируется в секундах.
     *
     * @param time строка с описанием времени (например, "10s", "2.5m", "20mine_ticks").
     * @return объект {@link Time} или {@link #ZERO}, если строка пустая или равна {@code null}.
     */
    public static Time parse(String time) {
        if (time == null || time.isBlank()) return ZERO;

        String formatted = time.trim().toLowerCase();
        if (formatted.endsWith("mine_ticks"))
            return of(Double.parseDouble(formatted.replaceAll("[^0-9.]", "")), TimeUnit.MINECRAFT_TICKS);
        else if (formatted.endsWith("ms"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 2)), TimeUnit.MILLISECONDS);
        else if (formatted.endsWith("ns"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 2)), TimeUnit.NANOSECONDS);
        else if (formatted.endsWith("us"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 2)), TimeUnit.MICROSECONDS);
        else if (formatted.endsWith("s"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 1)), TimeUnit.SECONDS);
        else if (formatted.endsWith("m"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 1)), TimeUnit.MINUTES);
        else if (formatted.endsWith("h"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 1)), TimeUnit.HOURS);
        else if (formatted.endsWith("d"))
            return of(Double.parseDouble(formatted.substring(0, formatted.length() - 1)), TimeUnit.DAYS);

        return of(Double.parseDouble(formatted), TimeUnit.SECONDS);
    }

    /**
     * Регистрирует кастомный экстрактор для создания {@link Time} из неродных типов объектов.
     *
     * @param extractor экпрес-экстрактор типа {@link SafeExtractor}.
     * @param <T>       тип целевого класса.
     */
    public static <T> void registerExtractor(SafeExtractor<T> extractor) {
        EXTRACTORS.put(extractor.targetClass, extractor);
    }

    /**
     * Регистрирует функцию конвертации внутреннего времени в наносекундах во внешний тип объекта.
     *
     * @param targetClass      класс целевого объекта.
     * @param converterFunction функция преобразования (принимает {@code long} наносекунд).
     * @param <T>              тип целевого объекта.
     */
    public static <T> void registerConverter(Class<T> targetClass, Function<Long, T> converterFunction) {
        CONVERTERS.put(targetClass, converterFunction);
    }

    /**
     * Возвращает значение времени, переведенное в запрашиваемую единицу измерения.
     *
     * @param timeUnit единица измерения времени.
     * @return дробное значение времени в указанной единице.
     */
    public double to(TimeUnit timeUnit) {
        return timeUnit.convertFromStorage(value, storageUnit);
    }

    /**
     * Преобразует объект {@link Time} в зарегистрированный внешний класс (например, {@link Duration}).
     *
     * @param targetClass целевой класс для конвертации.
     * @param <T>         тип целевого объекта.
     * @return объект целевого типа.
     * @throws IllegalArgumentException если для указанного класса не зарегистрирован конвертер.
     */
    public <T> T to(Class<T> targetClass) {
        Function<Long, ?> converter = CONVERTERS.get(targetClass);
        if (converter == null) throw new IllegalArgumentException("Unregistered conversion type: " + targetClass.getName());

        return targetClass.cast(converter.apply(toNanoseconds()));
    }

    /**
     * Возвращает полное значение времени в наносекундах.
     *
     * @return количество наносекунд в формате {@code long}.
     */
    public long toNanoseconds() {
        if (storageUnit == StorageUnit.NANOSECONDS) return value;
        return storageUnit.convertToStorage(value, TimeUnit.NANOSECONDS);
    }

    /**
     * Складывает текущий объект времени с другим.
     *
     * @param other добавляемый объект времени.
     * @return новый экземпляр {@link Time} с суммарной длительностью.
     */
    public Time plus(Time other) {
        return combine(other, true);
    }

    /**
     * Вычитает указанный объект времени из текущего.
     *
     * @param other вычитаемый объект времени.
     * @return новый экземпляр {@link Time} или {@link #ZERO}, если результат {@code <= 0}.
     */
    public Time minus(Time other) {
        return combine(other, false);
    }

    /**
     * Внутренний метод комбинации времени (сложение / вычитание).
     */
    private Time combine(Time other, boolean isAddition) {
        if (other == null || other.value == 0) return this;

        long delta = isAddition ? other.toNanoseconds() : -other.toNanoseconds();
        long resultNanos = toNanoseconds() + delta;
        return resultNanos <= 0 ? ZERO : of(resultNanos, TimeUnit.NANOSECONDS, storageUnit);
    }

    /**
     * Изменяет длительность времени, умножая её на указанный коэффициент.
     *
     * @param factor коэффициент умножения (например, 0.5 для уменьшения вдвое или 2.0 для удвоения).
     * @return новый экземпляр {@link Time} или {@link #ZERO}, если {@code factor <= 0}.
     */
    public Time modifyByFactor(double factor) {
        if (factor <= 0) return ZERO;
        return of(value * factor, TimeUnit.NANOSECONDS, storageUnit);
    }

    /**
     * Вычисляет абсолютную разницу (модуль) между двумя объектами времени.
     *
     * @param first  первый объект времени.
     * @param second второй объект времени.
     * @return новый экземпляр {@link Time}, равный разнице.
     */
    public static Time differenceBetween(Time first, Time second) {
        if (first == null || second == null) return ZERO;
        return of(Math.abs(first.toNanoseconds() - second.toNanoseconds()), TimeUnit.NANOSECONDS);
    }

    /**
     * Проверяет, превышает ли текущая длительность указанную.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущее время строго больше {@code other}.
     */
    public boolean isAfter(Time other) {
        return other != null && toNanoseconds() > other.toNanoseconds();
    }

    /**
     * Проверяет, меньше ли текущая длительность, чем указанная.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущее время строго меньше {@code other}.
     */
    public boolean isBefore(Time other) {
        return other != null && toNanoseconds() < other.toNanoseconds();
    }

    /**
     * Проверяет, больше ли текущая длительность или равна указанной.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущее время больше или равно {@code other}.
     */
    public boolean isAtLeast(Time other) {
        return other != null && toNanoseconds() >= other.toNanoseconds();
    }

    /**
     * Проверяет, меньше ли текущая длительность или равна указанной.
     *
     * @param other объект для сравнения.
     * @return {@code true}, если текущее время меньше или равно {@code other}.
     */
    public boolean isAtMost(Time other) {
        return other != null && toNanoseconds() <= other.toNanoseconds();
    }

    /**
     * Проверяет, истек ли временной интервал от заданной временной метки старта до текущего момента времени.
     *
     * @param startTime метка времени отсчета (в наносекундах {@link System#nanoTime()}).
     * @return {@code true}, если с момента {@code startTime} прошло больше времени, чем текущий интервал.
     */
    public boolean isExpiredFromTimestamp(Time startTime) {
        return startTime == null || System.nanoTime() >= (startTime.toNanoseconds() + toNanoseconds());
    }

    /**
     * Вычисляет время, оставшееся до завершения таймера от момента старта.
     *
     * @param startTime метка времени отсчета.
     * @param duration  общая длительность таймера.
     * @return оставшееся время в виде {@link Time} или {@link #ZERO}, если время уже истекло.
     */
    public static Time getRemainingTime(Time startTime, Time duration) {
        if (startTime == null || duration == null) return ZERO;
        long remaining = (startTime.toNanoseconds() + duration.toNanoseconds()) - System.nanoTime();
        return remaining <= 0L ? ZERO : new Time(remaining, StorageUnit.NANOSECONDS);
    }

    /**
     * Выполняет асинхронную задачу с возвращаемым результатом с задержкой, равной текущей длительности.
     *
     * @param taskSupplier поставщик (supplier) значения для асинхронного выполнения.
     * @param <T>          тип возвращаемого результата.
     * @return {@link CompletableFuture}, который завершится по истечении времени.
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
     * Запускает периодическое выполнение задачи с фиксированным интервалом через указанный исполнитель задач.
     *
     * @param executorService сервис планирования потоков.
     * @param periodicTask    периодически выполняемая задача.
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
     * Возвращает строковое представление длительности и единицы хранения.
     *
     * @return строка вида "10 seconds" или "500 milliseconds".
     */
    @Override
    public String toString() {
        return value + " " + storageUnit.name().toLowerCase();
    }

    /**
     * Абстрактный базовый класс для безопасного извлечения времени из нетипизированных или сторонних объектов.
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
         * @return полученный {@link Time} или {@link #ZERO}, если входной объект равен {@code null}.
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