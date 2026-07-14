package io.github.coco.logging.context;

import java.util.Map;

import io.github.coco.context.CocoContextSnapshot;
import org.slf4j.MDC;

/**
 * Coco MDC 上下文工具。
 * <p>
 * 捕获当前线程的完整 MDC，并通过 Coco 上下文快照在异步线程中临时恢复；作用域关闭后恢复工作线程原有 MDC，避免线程池复用导致日志上下文泄漏。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMdcContext {

    private CocoMdcContext() {
    }

    /**
     * <p>
     * 捕获当前线程的完整 MDC。
     * </p>
     * @return 可跨线程恢复的 MDC 快照
     */
    public static CocoContextSnapshot capture() {
        Map<String, String> captured = currentContextMap();
        return () -> {
            Map<String, String> previous = currentContextMap();
            restore(captured);
            return () -> restore(previous);
        };
    }

    private static Map<String, String> currentContextMap() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap == null || contextMap.isEmpty() ? Map.of() : Map.copyOf(contextMap);
    }

    private static void restore(Map<String, String> contextMap) {
        MDC.clear();
        if (!contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }
}
