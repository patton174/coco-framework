package io.github.coco.feature.web.exception;

import io.github.coco.exception.type.CocoRequestException;

/**
 * Coco Web 请求体过大异常。
 * <p>
 * 表示 Web 请求体超过框架允许缓存或处理的最大字节数，默认映射为 HTTP 413。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoPayloadTooLargeException extends CocoRequestException {

    /**
     * <p>
     * 创建请求体过大异常。
     * </p>
     * @param messageCode 国际化消息编码
     * @param args 消息格式化参数
     */
    public CocoPayloadTooLargeException(String messageCode, Object... args) {
        super(messageCode, messageCode, args);
    }
}
