package io.github.coco.logging.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 SLF4J 的 Coco 异步日志丢弃监听器。
 * <p>
 * 监听器直接使用独立 SLF4J logger 输出诊断信息，不经过 Coco 日志输出链路。
 * </p>
 * <p>
 * 输出内容是非本地化运维诊断，不属于面向用户的消息资源。
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
public final class Slf4jCocoAsyncLogDropListener implements CocoAsyncLogDropListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Slf4jCocoAsyncLogDropListener.class);

    /**
     * <p>
     * 在首次丢弃及累计数达到 2 的幂次时输出聚合告警。
     * </p>
     *
     * @param level 被丢弃日志的级别
     * @param handleName 被丢弃日志的句柄名称
     * @param totalDropped 当前异步输出器的唯一累计丢弃序号
     */
    @Override
    public void onDropped(CocoLogLevel level, String handleName, long totalDropped) {
        if (totalDropped > 0 && (totalDropped & (totalDropped - 1)) == 0) {
            LOGGER.warn("Coco async log queue overflow: totalDropped={}, level={}, handleName={}",
                    totalDropped, level, handleName);
        }
    }
}
