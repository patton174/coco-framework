package io.github.coco.common.exception;

import io.github.coco.common.exception.type.CocoConflictException;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.exception.type.CocoNotFoundException;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.exception.type.CocoSystemException;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
import io.github.coco.common.exception.support.CocoExceptionGuards;

/**
 * Coco 异常静态工厂。
 * <p>
 * 为框架内部和业务侧提供显式、可读的异常创建入口；当调用方不适合在 {@link CocoErrorCode} 上链式调用时，可以使用本工具类。
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
public final class CocoExceptions {

    private CocoExceptions() {
    }

    /**
     * <p>
     * 创建请求异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 请求异常
     */
    public static CocoRequestException request(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).request(args);
    }

    /**
     * <p>
     * 创建携带原因的请求异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 请求异常
     */
    public static CocoRequestException request(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).request(cause, args);
    }

    /**
     * <p>
     * 创建未认证异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    public static CocoUnauthorizedException unauthorized(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).unauthorized(args);
    }

    /**
     * <p>
     * 创建携带原因的未认证异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    public static CocoUnauthorizedException unauthorized(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).unauthorized(cause, args);
    }

    /**
     * <p>
     * 创建无权限异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    public static CocoForbiddenException forbidden(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).forbidden(args);
    }

    /**
     * <p>
     * 创建携带原因的无权限异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    public static CocoForbiddenException forbidden(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).forbidden(cause, args);
    }

    /**
     * <p>
     * 创建资源不存在异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    public static CocoNotFoundException notFound(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).notFound(args);
    }

    /**
     * <p>
     * 创建携带原因的资源不存在异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    public static CocoNotFoundException notFound(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).notFound(cause, args);
    }

    /**
     * <p>
     * 创建资源冲突异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    public static CocoConflictException conflict(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).conflict(args);
    }

    /**
     * <p>
     * 创建携带原因的资源冲突异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    public static CocoConflictException conflict(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).conflict(cause, args);
    }

    /**
     * <p>
     * 创建系统异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     * @return 系统异常
     */
    public static CocoSystemException system(CocoErrorCode errorCode, Object... args) {
        return requireErrorCode(errorCode).system(args);
    }

    /**
     * <p>
     * 创建携带原因的系统异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 系统异常
     */
    public static CocoSystemException system(CocoErrorCode errorCode, Throwable cause, Object... args) {
        return requireErrorCode(errorCode).system(cause, args);
    }

    private static CocoErrorCode requireErrorCode(CocoErrorCode errorCode) {
        return CocoExceptionGuards.requireErrorCode(errorCode);
    }
}
