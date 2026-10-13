package com.zfettostudios.zjtime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Tolerate;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Класс утилит.
 *
 * @since 1.2
 * @version 1.2
 * @author RandomShel
 */
@UtilityClass
public class Utils {
    /**
     * Реестр конверторов из {@link Number} в нужный класс.
     *
     * @since 1.2
     */
    public static final Map<Class<?>, Function<Number, ?>> NUMBER_CONVERTERS = new ConcurrentHashMap<>();

    static {
        NUMBER_CONVERTERS.put(Integer.class, Number::intValue);
        NUMBER_CONVERTERS.put(Long.class, Number::longValue);
        NUMBER_CONVERTERS.put(Double.class, Number::doubleValue);
        NUMBER_CONVERTERS.put(Float.class, Number::floatValue);
        NUMBER_CONVERTERS.put(Short.class, Number::shortValue);
        NUMBER_CONVERTERS.put(Byte.class, Number::byteValue);

        NUMBER_CONVERTERS.put(BigDecimal.class, number -> number instanceof BigDecimal bigDecimal ? bigDecimal : new BigDecimal(number.toString()));
        NUMBER_CONVERTERS.put(BigInteger.class, number -> number instanceof BigInteger bigInteger ? bigInteger : new BigInteger(number.toString()));

        NUMBER_CONVERTERS.put(Number.class, number -> number);
    }

    /**
     * Пребразует переданное числовое значение в заданный класс (тип {@link T}).
     * <p>
     * Все поддерживаемые классы хранятся в {@link NUMBER_CONVERTERS}.
     *
     * @param value исходное числовое значение.
     * @param target целевой класс, в который необходимо привести значение.
     * @param <T> тип класса в который необходимо конвертировать.
     * @return приведенное к целевому типу значение.
     * @throws IllegalArgumentException если переданный класс {@code target} не поддерживается.
     * @throws NullPointerException если {@code value} или {@code target} равно {@code null}.
     * @since 1.2
     */
    public static <T> T getObjectNumber(Number value, Class<T> target) {
        OptionalException.of()
            .objects(value, target)
            .arguments("value", "target")
            .isNull();

        if (value == null) throw new NullPointerException("value не может быть равно null");
        if (target == null) throw new NullPointerException("target не может быть равно null");

        return target.cast(Optional.ofNullable(NUMBER_CONVERTERS.get(target))
            .orElseThrow(() -> new IllegalArgumentException("Неожиданный тип: " + target.getName())));
    }

    /**
     * Позволяет удобно проверить каждый аргумент и если один из них равно {@code null}, выкинуть исключение с названием аргумента.
     * <p>
     * Пример использования:
     * <pre>{@code
     *     public void someMethod(Number value, Class<?> target) {
     *         OptionalException.of()
     *             .objects(value, target)
     *             .arguments("value", "target")
     *             .isNull();
     *     }
     * }</pre>
     *
     * @since 1.2
     */
    @Getter
    @Accessors(fluent = true)
    @NoArgsConstructor(staticName = "of")
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OptionalException {
        /** Имена проверяемых аргументов для формирования текста исключения. */
        String[] arguments;

        /** Значения проверяемых аргументов. */
        Object[] objects;

        /**
         * Устанавливает массив имен аргументов для проверки.
         * <p>
         * Количество имен должно строго соответствовать количеству переданных объектов в {@link #objects(Object...)}.
         *
         * @param arguments вариативный массив строковых названий аргументов.
         * @return текущий экземпляр {@link OptionalException} для построения цепочки вызовов (Fluent API).
         */
        @Tolerate
        public OptionalException arguments(String... arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * Устанавливает массив объектов (значений аргументов) для проверки на {@code null}.
         *
         * @param objects вариативный массив проверяемых объектов.
         * @return текущий экземпляр {@link OptionalException} для построения цепочки вызовов (Fluent API).
         */
        @Tolerate
        public OptionalException objects(Object... objects) {
            this.objects = objects;
            return this;
        }

        /**
         * Выполняет последовательную проверку всех переданных объектов на {@code null}.
         *
         * @throws IllegalArgumentException если массивы названий аргументов и объектов имеют разную длину.
         * @throws NullPointerException если хотя бы один из переданных объектов равен {@code null}. В сообщении ошибки указывается имя соответствующего аргумента.
         */
        public void isNull() {
            if (arguments == null || objects == null) throw new IllegalArgumentException("Массивы аргументов или объектов не были инициализированы!");
            if (arguments.length != objects.length) throw new IllegalArgumentException("arguments не столько же сколько objects!");

            for (int i = 0; i < objects.length; i++) if (objects[i] == null) throw new NullPointerException(arguments[i] + " не может быть равно null");
        }
    }
}