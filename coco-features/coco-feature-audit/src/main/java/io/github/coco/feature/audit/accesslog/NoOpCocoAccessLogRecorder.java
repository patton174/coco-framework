package io.github.coco.feature.audit.accesslog;

import java.util.Objects;

import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;

/**
 * 空操作 Coco 接口访问日志记录器。
 * <p>
 * 作为审计模块当前阶段的默认实现，仅校验访问日志事件不为空；后续可替换为日志文件、数据库或消息队列实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class NoOpCocoAccessLogRecorder implements CocoAccessLogRecorder {

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAccessLog accessLog) {
        Objects.requireNonNull(accessLog, "accessLog must not be null");
    }
}
