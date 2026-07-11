package io.github.coco.common.logging.access;

/**
 * Coco 接口访问日志记录器。
 * <p>
 * 由 Web 等入口模块发布访问日志事件，由日志模块默认输出器、审计模块或业务侧自定义实现决定写入日志、数据库、消息队列或其他存储。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoAccessLogRecorder {

    /**
     * <p>
     * 记录一次接口访问日志。
     * </p>
     * @param accessLog 接口访问日志
     */
    void record(CocoAccessLog accessLog);
}
