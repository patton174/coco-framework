package io.github.coco.feature.web.trace;

/**
 * Coco TraceId 校验器。
 * <p>
 * 用于约束从外部请求头接收的 TraceId，避免控制字符、超长文本或不受控字符进入响应头、Cookie、日志 MDC 和请求上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoTraceIdValidator {

    /**
     * <p>
     * 判断 TraceId 是否可被框架接受。
     * </p>
     * @param traceId TraceId
     * @return 可接受时返回 {@code true}
     */
    boolean isValid(String traceId);
}
