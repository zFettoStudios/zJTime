package com.zfettostudios.zjtime;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Перечисление поддерживаемых единиц измерения времени по умолчанию.
 * <p>
 * Используется для внешнего представления, конвертации и создания объектов {@link Time}.
 *
 * @since 0.1
 * @version 1.1.2
 * @author RandomShel
 */
@Getter
public class TimeUnit {
    /**
     * Наносекунды (1 / 1 000 000 000 секунды).
     */
    public static final TimeUnit NANOSECONDS = new TimeUnit(1L, "ns", "NANOSECONDS");

    /**
     * Микросекунды (1 / 1 000 000 секунды).
     */
    public static final TimeUnit MICROSECONDS = new TimeUnit(1_000L, "us", "MICROSECONDS");

    /**
     * Миллисекунды (1 / 1 000 секунды).
     */
    public static final TimeUnit MILLISECONDS = new TimeUnit(1_000_000L, "ms", "MILLISECONDS");

    /**
     * Секунды.
     */
    public static final TimeUnit SECONDS = new TimeUnit(1_000_000_000L, "s", "SECONDS");

    /**
     * Минуты.
     */
    public static final TimeUnit MINUTES = new TimeUnit(60_000_000_000L, "m", "MINUTES");

    /**
     * Часы.
     */
    public static final TimeUnit HOURS = new TimeUnit(3_600_000_000_000L, "h", "HOURS");

    /**
     * Дни.
     */
    public static final TimeUnit DAYS = new TimeUnit(86_400_000_000_000L, "d", "DAYS");

    /**
     * Количество наносекунд, содержащихся в одной единице времени.
     */
    private final long nanoseconds;

    /**
     * Обратное значение наносекунд для быстрой конвертации без операций деления.
     */
    private final double inverseNanoseconds;

    /**
     * Название единицы измерения времени.
     *
     * @since 1.1.2
     */
    private final String name;

    /**
     * Суффикс для парсинга {@link String} в объект {@link Time}.
     * <p>
     * Используется в методо {@link Time#parse}.
     *
     * @since 1.1
     */
    private final String suffix;

    /**
     * Конструктор единицы измерения времени.
     *
     * @param nanoseconds эквивалент единицы времени в наносекундах.
     * @param suffix суффикс для парсинга {@link String} в объект {@link Time}.
     * @param name название единицы измерения времени.
     */
    public TimeUnit(long nanoseconds, String suffix, String name) {
        this.nanoseconds = nanoseconds;
        this.inverseNanoseconds = 1.0 / nanoseconds;
        this.suffix = suffix;
        this.name = name;

        Time.TIME_UNITS.add(this);
    }

    /**
     * Переводит общее количество наносекунд в значение текущей единицы измерения.
     *
     * @param nanoseconds количество наносекунд (любой числовой тип).
     * @return переведенное значение в виде дробного числа.
     */
    public double convertNanoseconds(Number nanoseconds) {
        return nanoseconds.doubleValue() * this.inverseNanoseconds;
    }

    /**
     * Конвертирует передаваемое значение из указанной единицы измерения {@link TimeUnit} в текущую.
     *
     * <p>Пример использования:
     * <pre>{@code
     *     // Перевод 5 секунд в миллисекунды:
     *     double millis = TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS); // вернет 5000.0
     * }</pre>
     *
     * @param value числовое значение для конвертации
     * @param targetUnit единица измерения передаваемого значения
     * @return сконвертированное значение в текущей единице измерения
     * @throws IllegalArgumentException если {@code value} или {@code targetUnit} равно {@code null}
     * @since 1.1
     */
    public double convert(Number value, TimeUnit targetUnit) {
        if (value == null || targetUnit == null) throw new IllegalArgumentException("The value and targetUnit cannot be null");

        if (this == targetUnit) return value.doubleValue();

        return (value.doubleValue() * targetUnit.getNanoseconds()) * this.inverseNanoseconds;
    }

    /**
     * Конвертирует передаваемое значение из указанной единицы измерения {@link TimeUnit} в текущую.
     *
     * <p>Пример использования:
     * <pre>{@code
     *     // Перевод 5 секунд в миллисекунды:
     *     long millis = TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS); // вернет 5000
     * }</pre>
     *
     * @param value числовое значение для конвертации
     * @param targetUnit единица измерения передаваемого значения
     * @return сконвертированное значение в текущей единице измерения
     * @throws IllegalArgumentException если {@code value} или {@code targetUnit} равно {@code null}
     * @since 1.1
     */
    public long convertLong(Number value, TimeUnit targetUnit) {
        return (long) convert(value, targetUnit);
    }
}
