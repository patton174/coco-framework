package io.github.coco.sample.basic.accesslog;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import org.springframework.stereotype.Component;

/**
 * Coco 示例接口访问日志记录器。
 * <p>
 * 使用内存快照保存最近一次接口访问日志，便于示例接口展示 Coco 请求基础设施的运行结果。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Component
public final class SampleAccessLogRecorder implements CocoAccessLogRecorder {

    private final AtomicReference<CocoAccessLog> latestAccessLog = new AtomicReference<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAccessLog accessLog) {
        this.latestAccessLog.set(Objects.requireNonNull(accessLog, "accessLog must not be null"));
    }

    /**
     * <p>
     * 返回最近一次接口访问日志。
     * </p>
     * @return 最近一次接口访问日志；尚未记录时为空
     */
    public Optional<CocoAccessLog> latest() {
        return Optional.ofNullable(this.latestAccessLog.get());
    }

    /**
     * <p>
     * 清理最近一次接口访问日志。
     * </p>
     */
    public void clear() {
        this.latestAccessLog.set(null);
    }
}
