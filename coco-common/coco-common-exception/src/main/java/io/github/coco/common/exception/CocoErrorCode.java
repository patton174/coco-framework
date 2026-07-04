package io.github.coco.common.exception;

import io.github.coco.common.i18n.CocoMessageCode;

/**
 * Coco 异常编码契约。
 * <p>
 * 异常编码也是一种消息编码，额外提供创建 {@link CocoException} 的统一入口，便于框架内部和业务侧用枚举表达异常类型。
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
public interface CocoErrorCode extends CocoMessageCode {

    /**
     * <p>
     * 使用当前异常编码创建 Coco 异常。
     * </p>
     * @param args 消息格式化参数
     * @return Coco 异常
     */
    default CocoException exception(Object... args) {
        return new CocoException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建 Coco 异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return Coco 异常
     */
    default CocoException exception(Throwable cause, Object... args) {
        return new CocoException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建请求异常。
     * </p>
     * @param args 消息格式化参数
     * @return 请求异常
     */
    default CocoRequestException request(Object... args) {
        return new CocoRequestException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建请求异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 请求异常
     */
    default CocoRequestException request(Throwable cause, Object... args) {
        return new CocoRequestException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建未认证异常。
     * </p>
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    default CocoUnauthorizedException unauthorized(Object... args) {
        return new CocoUnauthorizedException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建未认证异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    default CocoUnauthorizedException unauthorized(Throwable cause, Object... args) {
        return new CocoUnauthorizedException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建无权限异常。
     * </p>
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    default CocoForbiddenException forbidden(Object... args) {
        return new CocoForbiddenException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建无权限异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    default CocoForbiddenException forbidden(Throwable cause, Object... args) {
        return new CocoForbiddenException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建资源不存在异常。
     * </p>
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    default CocoNotFoundException notFound(Object... args) {
        return new CocoNotFoundException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建资源不存在异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    default CocoNotFoundException notFound(Throwable cause, Object... args) {
        return new CocoNotFoundException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建资源冲突异常。
     * </p>
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    default CocoConflictException conflict(Object... args) {
        return new CocoConflictException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建资源冲突异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    default CocoConflictException conflict(Throwable cause, Object... args) {
        return new CocoConflictException(this, cause, args);
    }

    /**
     * <p>
     * 使用当前异常编码创建系统异常。
     * </p>
     * @param args 消息格式化参数
     * @return 系统异常
     */
    default CocoSystemException system(Object... args) {
        return new CocoSystemException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建系统异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return 系统异常
     */
    default CocoSystemException system(Throwable cause, Object... args) {
        return new CocoSystemException(this, cause, args);
    }
}
