package io.github.coco.common.exception;

import java.util.Collection;
import java.util.Map;

/**
 * Coco 静态断言工具。
 * <p>
 * 提供框架内部和业务侧可直接调用的参数、状态校验方法；校验失败时抛出携带 {@link CocoErrorCode} 的 {@link CocoException}。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoAssert {

    private CocoAssert() {
    }

    /**
     * <p>
     * 断言表达式为 {@code true}。
     * </p>
     * @param expression 布尔表达式
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     */
    public static void isTrue(boolean expression, CocoErrorCode errorCode, Object... args) {
        if (!expression) {
            throw exception(errorCode, args);
        }
    }

    /**
     * <p>
     * 断言状态表达式为 {@code true}。
     * </p>
     * @param expression 状态表达式
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     */
    public static void state(boolean expression, CocoErrorCode errorCode, Object... args) {
        if (!expression) {
            throw exception(errorCode, args);
        }
    }

    /**
     * <p>
     * 断言对象不为 {@code null}，并返回原对象。
     * </p>
     * @param value 待校验对象
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     * @param <T> 对象类型
     * @return 原对象
     */
    public static <T> T notNull(T value, CocoErrorCode errorCode, Object... args) {
        if (value == null) {
            throw exception(errorCode, args);
        }
        return value;
    }

    /**
     * <p>
     * 断言文本包含至少一个非空白字符，并返回原文本。
     * </p>
     * @param value 待校验文本
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     * @return 原文本
     */
    public static String hasText(String value, CocoErrorCode errorCode, Object... args) {
        if (value == null || value.isBlank()) {
            throw exception(errorCode, args);
        }
        return value;
    }

    /**
     * <p>
     * 断言集合不为 {@code null} 且不为空，并返回原集合。
     * </p>
     * @param value 待校验集合
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     * @param <T> 集合类型
     * @return 原集合
     */
    public static <T extends Collection<?>> T notEmpty(T value, CocoErrorCode errorCode, Object... args) {
        if (value == null || value.isEmpty()) {
            throw exception(errorCode, args);
        }
        return value;
    }

    /**
     * <p>
     * 断言映射不为 {@code null} 且不为空，并返回原映射。
     * </p>
     * @param value 待校验映射
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     * @param <T> 映射类型
     * @return 原映射
     */
    public static <T extends Map<?, ?>> T notEmpty(T value, CocoErrorCode errorCode, Object... args) {
        if (value == null || value.isEmpty()) {
            throw exception(errorCode, args);
        }
        return value;
    }

    /**
     * <p>
     * 断言数组不为 {@code null} 且不为空，并返回原数组。
     * </p>
     * @param value 待校验数组
     * @param errorCode 校验失败时使用的异常编码
     * @param args 消息格式化参数
     * @param <T> 数组元素类型
     * @return 原数组
     */
    public static <T> T[] notEmpty(T[] value, CocoErrorCode errorCode, Object... args) {
        if (value == null || value.length == 0) {
            throw exception(errorCode, args);
        }
        return value;
    }

    private static CocoException exception(CocoErrorCode errorCode, Object... args) {
        if (errorCode == null) {
            throw CocoCommonErrorCode.MISSING_ERROR_CODE.request();
        }
        return errorCode.exception(args);
    }
}
