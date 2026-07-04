package io.github.coco.common.exception;

import io.github.coco.common.i18n.CocoMessageCode;

/**
 * Coco 异常编码契约。
 * <p>
 * 异常编码也是一种消息编码，额外提供创建 {@link CocoException} 的统一入口，便于框架内部和业务侧用枚举表达异常类型。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoErrorCode extends CocoMessageCode {

    /**
     * <p>
     * 使用当前异常编码创建 Coco 异常。
     * </p>
     * @param args 消息格式化参数
     * @return Coco 异常
     */
    default CocoException exception(Object... args) {
        return new CocoException(this, args);
    }

    /**
     * <p>
     * 使用当前异常编码和异常原因创建 Coco 异常。
     * </p>
     * @param cause 异常原因
     * @param args 消息格式化参数
     * @return Coco 异常
     */
    default CocoException exception(Throwable cause, Object... args) {
        return new CocoException(this, cause, args);
    }
}
