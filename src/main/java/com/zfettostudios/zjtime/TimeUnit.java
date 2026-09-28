package com.zfettostudios.zjtime;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Перечисление поддерживаемых единиц измерения времени.
 * <p>
 * Используется для внешнего представления, конвертации и создания объектов {@link Time}.
 *
 * @since 0.1
 * @version 1.0
 * @author RandomShel
 */
@Getter
public enum TimeUnit {
    /**
     * Наносекунды (1 / 1 000 000 000 секунды).
     */
    NANOSECONDS(1L),

    /**
     * Микросекунды (1 / 1 000 000 секунды).
     */
    MICROSECONDS(1_000L),

    /**
     * Миллисекунды (1 / 1 000 секунды).
     */
    MILLISECONDS(1_000_000L),

    /**
     * Секунды.
     */
    SECONDS(1_000_000_000L),

    /**
     * Минуты.
     */
    MINUTES(60_000_000_000L),

    /**
     * Часы.
     */
    HOURS(3_600_000_000_000L),

    /**
     * Дни.
     */
    DAYS(86_400_000_000_000L),

    /**
     * Игровые тики Minecraft (1 тик = 50 миллисекунд = 1 / 20 секунды).
     */
    MINECRAFT_TICKS(50_000_000L);

    /**
     * Количество наносекунд, содержащихся в одной единице времени.
     */
    private final long nanoseconds;

    /**
     * Обратное значение наносекунд для быстрой конвертации без операций деления.
     */
    private final double inverseNanoseconds;

    /**
     * Кешированная таблица коэффициентов конвертации из различных {@link StorageUnit}.
     */
    private final Map<StorageUnit, Double> scaleFactors = new EnumMap<>(StorageUnit.class);

    /**
     * Конструктор единицы измерения времени.
     *
     * @param nanoseconds эквивалент единицы времени в наносекундах.
     */
    TimeUnit(long nanoseconds) {
        this.nanoseconds = nanoseconds;
        this.inverseNanoseconds = 1.0 / nanoseconds;
    }

    static {
        for (TimeUnit timeUnit : values()) {
            for (StorageUnit storageUnit : StorageUnit.values()) {
                timeUnit.scaleFactors.put(
                    storageUnit,
                    (double) storageUnit.getNanoseconds() * timeUnit.inverseNanoseconds
                );
            }
        }
    }

    /**
     * Конвертирует внутреннее хранимое значение из {@link StorageUnit} в значение данной единицы измерения.
     *
     * @param value       хранимое числовое значение.
     * @param storageUnit единица хранения исходного значения.
     * @return дробное значение времени в текущей единице измерения.
     */
    public double convertFromStorage(long value, StorageUnit storageUnit) {
        return value * scaleFactors.get(storageUnit);
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
}
