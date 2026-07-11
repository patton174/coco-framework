package io.github.coco.exception.type;

import io.github.coco.exception.CocoBusinessCode;
import io.github.coco.exception.CocoErrorCode;
import io.github.coco.exception.CocoException;

/**
 * Coco 请求异常。
 * <p>
 * 表示调用方提交的请求参数、请求内容或请求状态不满足框架约束，Web 场景默认映射为请求错误响应。
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
public class CocoRequestException extends CocoException {

    /**
     * <p>
     * 使用异常编码契约创建请求异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param args 消息格式化参数
     */
    public CocoRequestException(CocoErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * <p>
     * 使用异常编码契约和异常原因创建请求异常。
     * </p>
     * @param errorCode 异常编码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoRequestException(CocoErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }

    /**
     * <p>
     * 使用业务码契约创建请求异常。
     * </p>
     * @param businessCode 业务码契约
     * @param args 消息格式化参数
     */
    public CocoRequestException(CocoBusinessCode businessCode, Object... args) {
        super(businessCode, args);
    }

    /**
     * <p>
     * 使用业务码契约和异常原因创建请求异常。
     * </p>
     * @param businessCode 业务码契约
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoRequestException(CocoBusinessCode businessCode, Throwable cause, Object... args) {
        super(businessCode, cause, args);
    }

    /**
     * <p>
     * 使用消息编码、默认文本和格式化参数创建请求异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param args 消息格式化参数
     */
    public CocoRequestException(String code, String defaultMessage, Object... args) {
        super(code, defaultMessage, args);
    }

    /**
     * <p>
     * 使用消息编码、默认文本、异常原因和格式化参数创建请求异常。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param cause 异常原因
     * @param args 消息格式化参数
     */
    public CocoRequestException(String code, String defaultMessage, Throwable cause, Object... args) {
        super(code, defaultMessage, cause, args);
    }
}
