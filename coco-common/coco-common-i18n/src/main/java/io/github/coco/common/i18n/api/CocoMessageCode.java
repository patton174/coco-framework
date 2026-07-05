package io.github.coco.common.i18n.api;

/**
 * Coco 消息编码契约。
 * <p>
 * 框架内部和业务侧可以通过枚举实现该接口，集中维护消息编码，避免在业务逻辑中散落字符串编码。
 * </p>
 * <p>
 * 该契约只负责携带消息编码。框架内置消息的人类可读文本统一由 {@code coco-common-i18n} 的消息资源解析，
 * 当资源缺失时默认回退为消息编码本身。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoMessageCode {

    /**
     * <p>
     * 返回国际化消息编码。
     * </p>
     * @return 消息编码
     */
    String code();

    /**
     * <p>
     * 返回消息资源缺失时使用的默认文本。
     * </p>
     * <p>
     * 默认使用消息编码作为兜底值，避免框架模块在枚举或异常中硬编码具体语言文本。
     * </p>
     * @return 默认消息文本
     */
    default String defaultMessage() {
        return code();
    }

    /**
     * <p>
     * 使用当前消息编码创建消息描述。
     * </p>
     * @param args 消息格式化参数
     * @return 消息描述
     */
    default CocoMessage message(Object... args) {
        return new CocoMessage(this, args);
    }
}
