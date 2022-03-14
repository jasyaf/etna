package org.etnaframework.core.util;

/**
 * 计量单位相关处理类
 *
 * @author BlackCat
 * @since 2011-10-15
 */
public class UnitTools {

    /**
     * RMB相关单位
     */
    public enum RmbUnit {
        fen(1),
        jiao(10),
        yuan(100);

        private long value;

        private RmbUnit(long value) {
            this.value = value;
        }

        public long get() {
            return value;
        }
    }

    /**
     * 存储容量大小相关单位
     */
    public enum ByteUnit {
        b(1),
        kb(1024l),
        mb(1024l * 1024),
        gb(1024l * 1024 * 1024),
        tb(1024l * 1024 * 1024 * 1024);

        private long value;

        private ByteUnit(long value) {
            this.value = value;
        }

        public long get() {
            return value;
        }
    }

    /**
     * 获得from数值是to数值的倍数，并可设置是否向上取整
     *
     * @param ceil 是否向上取整
     */
    private static double convert(long from, long to, boolean ceil) {
        if (ceil) {
            return ceil((double) from / to);
        }
        return (from / to);
    }

    /**
     * 获得向上取整的数值
     */
    private static double ceil(double num) {
        if (num < 0) {
            return -Math.ceil(-num);
        }
        return Math.ceil(num);
    }

    /**
     * 将单位为fromUnit的字节数fromValue转换为toUnit单位的字节数
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     *
     * @return 目标数值
     */
    public static long convertByte(long fromValue, ByteUnit fromUnit, ByteUnit toUnit) {
        return (long) convertByte(fromValue, fromUnit, toUnit, false);
    }

    /**
     * 将单位为fromUnit的字节数fromValue转换为toUnit单位的字节数，并向上取整
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     *
     * @return 目标数值
     */
    public static long convertByteCeil(long fromValue, ByteUnit fromUnit, ByteUnit toUnit) {
        return (long) convertByte(fromValue, fromUnit, toUnit, true);
    }

    /**
     * 将单位为fromUnit的字节数fromValue转换为toUnit单位的字节数，并可设置是否向上取整
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     * @param ceil 是否向上取整
     *
     * @return 目标数值
     */
    public static double convertByte(long fromValue, ByteUnit fromUnit, ByteUnit toUnit, boolean ceil) {
        return convert(fromValue * fromUnit.get(), toUnit.get(), ceil);
    }

    /**
     * 将单位为fromUnit的人民币fromValue转换为toUnit单位的人民币
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     *
     * @return 目标数值
     */
    public static long convertRmb(long fromValue, RmbUnit fromUnit, RmbUnit toUnit) {
        return (long) convertRmb(fromValue, fromUnit, toUnit, false);
    }

    /**
     * 将单位为fromUnit的人民币fromValue转换为toUnit单位的人民币，并向上取整
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     *
     * @return 目标数值
     */
    public static long convertTimeCeil(long fromValue, RmbUnit fromUnit, RmbUnit toUnit) {
        return (long) convertRmb(fromValue, fromUnit, toUnit, true);
    }

    /**
     * 将单位为fromUnit的人民币fromValue转换为toUnit单位的人民币，并可设置是否向上取整
     *
     * @param fromValue 原数值
     * @param fromUnit 原单位
     * @param toUnit 目标单位
     * @param ceil 是否向上取整
     *
     * @return 目标数值
     */
    public static double convertRmb(long fromValue, RmbUnit fromUnit, RmbUnit toUnit, boolean ceil) {
        return convert(fromValue * fromUnit.get(), toUnit.get(), ceil);
    }

    private UnitTools() {
    }
}
