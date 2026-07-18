package io.github.coco.i18n;

import java.util.Locale;

/**
 * Coco 请求语言回退策略。
 * <p>
 * 仅用于没有显式传入 {@link Locale} 的消息解析路径。显式 locale 仍按 Spring
 * {@code ResourceBundle} 的标准精确匹配和候选回退规则处理。
 * </p>
 *
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * 自定义策略会完整替换默认语言过滤行为。
 *
 * @author patton174
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoLocaleFallbackPolicy {

    /**
     * <p>
     * 规范化请求语言，或在语言不受支持时返回默认语言。
     * </p>
     * @param locale 请求或上下文语言；可能为空
     * @param properties Coco 国际化配置
     * @return 用于非显式消息解析的语言
     */
    Locale resolveLocale(Locale locale, CocoI18nProperties properties);
}
