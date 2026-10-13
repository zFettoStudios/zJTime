package com.zfettostudios.zjtime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Единица измерения времени.
 *
 * @since 0.1
 * @version 1.2
 * @author RandomShel
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TimeUnit {
    /**
     * Наносекунды {@code (1 / 1 000 000 000 секунды)}.
     */
    public static final TimeUnit NANOSECONDS = create(1L, "NANOSECONDS", "ns");

    /**
     * Микросекунды {@code (1 / 1 000 000 секунды)}.
     */
    public static final TimeUnit MICROSECONDS = create(1_000L, "MICROSECONDS", "us");

    /**
     * Миллисекунды {@code (1 / 1 000 секунды)}.
     */
    public static final TimeUnit MILLISECONDS = create(1_000L, MICROSECONDS, "MILLISECONDS", "ms");

    /**
     * Секунды.
     */
    public static final TimeUnit SECONDS = create(1_000L, MILLISECONDS, "SECONDS", "s");

    /**
     * Минуты.
     */
    public static final TimeUnit MINUTES = create(60L, SECONDS, "MINUTES", "m");

    /**
     * Часы.
     */
    public static final TimeUnit HOURS = create(60L, MINUTES, "HOURS", "h");

    /**
     * Дни.
     */
    public static final TimeUnit DAYS = create(24L, HOURS, "DAYS", "d");

    /**
     * Количество наносекунд в одной единице.
     */
    long nanoseconds;

    /**
     * Обратное значение наносекунд {@code (1.0 / nanoseconds)} для быстрой конвертации умножением вместо деления.
     */
    double inverseNanoseconds;

    /**
     * Текстовый суффикс для парсинга.
     *
     * @since 1.1
     */
    CharSequence suffix;

    /**
     * Название единицы измерения.
     *
     * @since 1.1.2
     */
    CharSequence name;

    private TimeUnit(long nanoseconds, CharSequence name, CharSequence suffix) {
        this.nanoseconds = nanoseconds;
        this.inverseNanoseconds = 1.0 / nanoseconds;
        this.suffix = suffix;
        this.name = name;
    }

    /**
     * Создает и автоматически регистрирует новую единицу измерения времени.
     *
     * @param nanoseconds сколько наносекунд в одной единице.
     * @param name текстовое название.
     * @param suffix суффикс для парсера строк.
     * @return созданный объект {@link TimeUnit}.
     *
     * @since 1.2
     */
    public static TimeUnit create(long nanoseconds, CharSequence name, CharSequence suffix) {
        return Time.addTimeUnit(new TimeUnit(nanoseconds, name, suffix));
    }

    /**
     * Создает и автоматически регистрирует новую единицу измерения времени.
     *
     * @param value сколько значения в одной единице.
     * @param timeUnit единица измерения.
     * @param name текстовое название.
     * @param suffix суффикс для парсера строк.
     * @return созданный объект {@link TimeUnit}.
     *
     * @since 1.2
     * @see create(long nanoseconds, CharSequence name, CharSequence suffix)
     */
    public static TimeUnit create(Number value, TimeUnit timeUnit, CharSequence name, CharSequence suffix) {
        return create(timeUnit.toNanoseconds(value), name, suffix);
    }

    /**
     * Конвертирует наносекунды в текущую единицу измерения.
     */
    public double convertNanoseconds(long nanoseconds) {
        return nanoseconds * inverseNanoseconds;
    }

    /**
     * Переводит указанное значение текущей единицы в наносекунды.
     *
     * @since 1.2
     */
    public long toNanoseconds(Number value) {
        if (value == null || value.doubleValue() == 0) return 0L;
        return Math.round(value.doubleValue() * nanoseconds);
    }

    /**
     * Конвертирует числовое значение из сторонней единицы измерения в текущую с приведением к нужному числовому типу.
     *
     * @param value числовое значение.
     * @param targetUnit исходная единица измерения значения.
     * @param target целевой класс (например, {@link Double#getClass()}, {@link Long#getClass()}).
     * @param <T> тип результата.
     * @return число в целевом формате.
     */
    public <T> T convert(Number value, TimeUnit targetUnit, Class<T> target) {
        Utils.OptionalException.of()
            .objects(value, targetUnit, target)
            .arguments("value", "targetUnit", "target")
            .isNull();

        Number result;
        if (this == targetUnit) result = value;
        else result = (value.doubleValue() * targetUnit.nanoseconds) * inverseNanoseconds;

        return Utils.getObjectNumber(result, target);
    }
}