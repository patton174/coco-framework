package io.github.coco.feature.audit.core;

/**
 * Coco 审计事件格式化器。
 * <p>
 * 默认日志记录器通过该 SPI 把结构化审计事件转换为单条日志正文，业务侧可以提供自定义 Bean 替换默认格式。
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
@FunctionalInterface
public interface CocoAuditFormatter {

    /**
     * <p>
     * 格式化审计事件。
     * </p>
     * @param event 审计事件
     * @return 日志正文；返回空白内容时默认记录器跳过输出
     */
    String format(CocoAuditEvent event);
}
