package io.github.coco.feature.audit.core;

import java.util.Objects;

/**
 * 空操作 Coco 审计记录器。
 * <p>
 * 仅校验审计事件不为空。业务项目需要显式丢弃审计事件，同时保留审计发布链路时，可以把该实现注册为
 * {@link CocoAuditRecorder} Bean。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class NoOpCocoAuditRecorder implements CocoAuditRecorder {

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
    }
}
