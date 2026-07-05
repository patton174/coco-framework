package io.github.coco.common.exception;

import io.github.coco.common.exception.type.CocoConflictException;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.exception.type.CocoNotFoundException;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.exception.type.CocoSystemException;
import io.github.coco.common.exception.type.CocoUnauthorizedException;

/**
 * Coco 业务码契约。
 * <p>
 * 业务系统可以通过枚举实现该接口，集中维护对外响应业务码和国际化消息编码；框架会把业务码输出到统一响应的
 * {@code code} 字段，把消息编码交给国际化基础设施解析为 {@code message}。
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
public interface CocoBusinessCode {

    /**
     * <p>
     * 返回对外响应业务码。
     * </p>
     * @return 业务码
     */
    int code();

    /**
     * <p>
     * 返回国际化消息编码。
     * </p>
     * @return 国际化消息编码
     */
    String messageCode();

    /**
     * <p>
     * 返回消息资源缺失时使用的默认文本。
     * </p>
     * <p>
     * 默认使用消息编码作为兜底值，避免业务码枚举中硬编码具体语言文本。
     * </p>
     * @return 默认消息文本
     */
    default String defaultMessage() {
        return messageCode();
    }

    /**
     * <p>
     * 使用当前业务码创建请求异常。
     * </p>
     * @param args 消息格式化参数
     * @return 请求异常
     */
    default CocoRequestException request(Object... args) {
        return CocoBusinessExceptions.request(this, args);
    }

    /**
     * <p>
     * 使用当前业务码创建未认证异常。
     * </p>
     * @param args 消息格式化参数
     * @return 未认证异常
     */
    default CocoUnauthorizedException unauthorized(Object... args) {
        return CocoBusinessExceptions.unauthorized(this, args);
    }

    /**
     * <p>
     * 使用当前业务码创建无权限异常。
     * </p>
     * @param args 消息格式化参数
     * @return 无权限异常
     */
    default CocoForbiddenException forbidden(Object... args) {
        return CocoBusinessExceptions.forbidden(this, args);
    }

    /**
     * <p>
     * 使用当前业务码创建资源不存在异常。
     * </p>
     * @param args 消息格式化参数
     * @return 资源不存在异常
     */
    default CocoNotFoundException notFound(Object... args) {
        return CocoBusinessExceptions.notFound(this, args);
    }

    /**
     * <p>
     * 使用当前业务码创建资源冲突异常。
     * </p>
     * @param args 消息格式化参数
     * @return 资源冲突异常
     */
    default CocoConflictException conflict(Object... args) {
        return CocoBusinessExceptions.conflict(this, args);
    }

    /**
     * <p>
     * 使用当前业务码创建系统异常。
     * </p>
     * @param args 消息格式化参数
     * @return 系统异常
     */
    default CocoSystemException system(Object... args) {
        return CocoBusinessExceptions.system(this, args);
    }
}
