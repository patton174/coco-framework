package io.github.coco.common.logging.access;

/**
 * Coco 接口访问日志格式化器。
 * <p>
 * 业务项目可以声明自己的格式化器 Bean，自定义接口访问日志文本样式。
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
public interface CocoAccessLogFormatter {

    /**
     * <p>
     * 将接口访问日志事件格式化为可打印文本。
     * </p>
     * @param accessLog 接口访问日志事件
     * @param properties 接口访问日志配置
     * @return 可打印文本
     */
    String format(CocoAccessLog accessLog, CocoAccessLogProperties properties);
}
