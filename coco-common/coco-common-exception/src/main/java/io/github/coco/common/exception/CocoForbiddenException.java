package io.github.coco.common.exception;

import java.util.Objects;

/**
 * Coco 无权限异常。
 * <p>
 * 表示调用方已经完成身份识别，但没有访问当前资源或执行当前动作的权限。
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
public class CocoForbiddenException extends CocoException {

    /**
     * <p>
     * 使用异常编码契约创建无权限异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     */
    public CocoForbiddenException(CocoErrorCode errorCode, Object... args) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null"), args);
    }

    /**
     * <p>
     * 使用异常编码契约和异常原因创建无权限异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoForbiddenException(CocoErrorCode errorCode, Throwable cause, Object... args) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null"), cause, args);
    }

    /**
     * <p>
     * 使用消息编码、默认文本和格式化参数创建无权限异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param args 消息格式化参数
     */
    public CocoForbiddenException(String code, String defaultMessage, Object... args) {
        super(code, defaultMessage, args);
    }

    /**
     * <p>
     * 使用消息编码、默认文本、异常原因和格式化参数创建无权限异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoForbiddenException(String code, String defaultMessage, Throwable cause, Object... args) {
        super(code, defaultMessage, cause, args);
    }
}
