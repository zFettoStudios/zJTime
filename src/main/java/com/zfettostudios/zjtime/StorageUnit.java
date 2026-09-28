package com.zfettostudios.zjtime;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Перечисление внутренних единиц хранения времени.
 * <p>
 * Определяет базис, в котором объект {@link Time} хранит свое числовое значение
 * во внутреннем поле для оптимизации памяти и точных математических расчетов.
 *
 * @since 1.0
 * @version 1.0
 * @author RandomShel
 */
@Getter
public enum StorageUnit {
    /**
     * Хранение в наносекундах.
     */
    NANOSECONDS(1L),

    /**
     * Хранение в микросекундах.
     */
    MICROSECONDS(1_000L),

    /**
     * Хранение в миллисекундах.
     */
    MILLISECONDS(1_000_000L);

    /**
     * Количество наносекунд в одной единице данного типа хранения.
     */
    private final long nanoseconds;

    /**
     * Обратное значение наносекунд (1.0 / nanoseconds) для ускоренного умножения вместо деления.
     */
    private final double inverseNanoseconds;

    /**
     * Кешированная таблица коэффициентов масштабирования для перевода из различных {@link TimeUnit}.
     */
    private final Map<TimeUnit, Double> scaleFactors = new EnumMap<>(TimeUnit.class);

    /**
     * Конструктор единицы хранения.
     *
     * @param nanoseconds эквивалент одной единицы в наносекундах.
     */
    StorageUnit(long nanoseconds) {
        this.nanoseconds = nanoseconds;
        this.inverseNanoseconds = 1.0 / nanoseconds;
    }

    static {
        for (StorageUnit storageUnit : values()) {
            for (TimeUnit timeUnit : TimeUnit.values()) {
                storageUnit.scaleFactors.put(
                    timeUnit,
                    (double) timeUnit.getNanoseconds() * storageUnit.inverseNanoseconds
                );
            }
        }
    }

    /**
     * Преобразует числовое значение из указанной единицы измерения {@link TimeUnit}
     * во внутренний формат хранения данной единицы.
     *
     * @param value    числовое значение времени (может быть {@code null}).
     * @param timeUnit единица измерения исходного значения.
     * @return округленное значение времени во внутреннем формате хранения. Если {@code value} равен {@code null}, возвращает {@code 0L}.
     */
    public long convertToStorage(Number value, TimeUnit timeUnit) {
        if (value == null) return 0L;
        if (timeUnit.getNanoseconds() == this.nanoseconds) return value.longValue();

        return Math.round(value.doubleValue() * scaleFactors.get(timeUnit));
    }
}
