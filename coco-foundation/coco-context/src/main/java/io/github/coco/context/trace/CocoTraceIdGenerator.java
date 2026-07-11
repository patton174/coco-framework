package io.github.coco.context.trace;

import java.util.UUID;

/**
 * Coco TraceId 生成器。
 * <p>
 * 生成无分隔符的 32 位小写十六进制 TraceId，作为框架日志、审计和跨模块上下文关联的默认标识。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTraceIdGenerator {

    private CocoTraceIdGenerator() {
    }

    /**
     * <p>
     * 生成新的 TraceId。
     * </p>
     * @return 32 位小写十六进制 TraceId
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
