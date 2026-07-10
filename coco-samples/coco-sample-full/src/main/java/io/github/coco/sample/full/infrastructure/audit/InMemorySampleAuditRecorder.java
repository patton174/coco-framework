package io.github.coco.sample.full.infrastructure.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import org.springframework.stereotype.Component;

/**
 * 示例使用的内存审计记录器。
 * <p>
 * 生产应用应将 {@link CocoAuditRecorder} 替换为数据库、消息队列或审计平台实现。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@Component
public final class InMemorySampleAuditRecorder implements CocoAuditRecorder {

    private final CopyOnWriteArrayList<CocoAuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(CocoAuditEvent event) {
        if (event != null && event.type().startsWith("sample.")) {
            this.events.add(event);
        }
    }

    public List<CocoAuditEvent> events() {
        return List.copyOf(this.events);
    }

    public void clear() {
        this.events.clear();
    }
}
