package io.github.coco.exception;

import io.github.coco.exception.type.CocoConflictException;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoNotFoundException;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.exception.type.CocoSystemException;
import io.github.coco.exception.type.CocoUnauthorizedException;

/**
 * Coco 业务异常静态工厂。
 * <p>
 * 为业务侧提供只暴露业务码契约或消息编码的异常创建入口；指定 {@link CocoBusinessCode} 时统一响应使用业务码，
 * 只指定消息编码时统一响应使用框架系统默认码。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoBusinessExceptions {

    private CocoBusinessExceptions() {
    }

    /**
     * <p>
     * 使用消息编码创建请求异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 请求异常
     */
    public static CocoRequestException request(String messageCode, Object... args) {
        return new CocoRequestException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建请求异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 请求异常
     */
    public static CocoRequestException request(CocoBusinessCode businessCode, Object... args) {
        return new CocoRequestException(requireBusinessCode(businessCode), args);
    }

    /**
     * <p>
     * 使用消息编码创建未认证异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    public static CocoUnauthorizedException unauthorized(String messageCode, Object... args) {
        return new CocoUnauthorizedException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建未认证异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    public static CocoUnauthorizedException unauthorized(CocoBusinessCode businessCode, Object... args) {
        return new CocoUnauthorizedException(requireBusinessCode(businessCode), args);
    }

    /**
     * <p>
     * 使用消息编码创建无权限异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    public static CocoForbiddenException forbidden(String messageCode, Object... args) {
        return new CocoForbiddenException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建无权限异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    public static CocoForbiddenException forbidden(CocoBusinessCode businessCode, Object... args) {
        return new CocoForbiddenException(requireBusinessCode(businessCode), args);
    }

    /**
     * <p>
     * 使用消息编码创建资源不存在异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    public static CocoNotFoundException notFound(String messageCode, Object... args) {
        return new CocoNotFoundException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建资源不存在异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    public static CocoNotFoundException notFound(CocoBusinessCode businessCode, Object... args) {
        return new CocoNotFoundException(requireBusinessCode(businessCode), args);
    }

    /**
     * <p>
     * 使用消息编码创建资源冲突异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    public static CocoConflictException conflict(String messageCode, Object... args) {
        return new CocoConflictException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建资源冲突异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    public static CocoConflictException conflict(CocoBusinessCode businessCode, Object... args) {
        return new CocoConflictException(requireBusinessCode(businessCode), args);
    }

    /**
     * <p>
     * 使用消息编码创建系统异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     * @return 系统异常
     */
    public static CocoSystemException system(String messageCode, Object... args) {
        return new CocoSystemException(messageCode, messageCode, args);
    }

    /**
     * <p>
     * 使用业务码创建系统异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     * @return 系统异常
     */
    public static CocoSystemException system(CocoBusinessCode businessCode, Object... args) {
        return new CocoSystemException(requireBusinessCode(businessCode), args);
    }

    private static CocoBusinessCode requireBusinessCode(CocoBusinessCode businessCode) {
        if (businessCode == null) {
            throw CocoCommonErrorCode.MISSING_ERROR_CODE.request();
        }
        return businessCode;
    }
}
