package io.github.coco.common.logging.core;

/**
 * Coco 日志输出器。
 * <p>
 * 接收日志模块标准记录并完成最终输出，业务系统可以自定义实现来接管打印、落库或发送日志平台。
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
public interface CocoLogSink {

    /**
     * <p>
     * 输出日志记录。
     * </p>
     * @param record 日志记录
     */
    void log(CocoLogRecord record);
}
