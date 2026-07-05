package io.github.coco.common.i18n.api;

import java.util.Locale;

/**
 * Coco 语言解析器。
 * <p>
 * 为不显式传入语言的消息解析提供当前语言来源。
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
public interface CocoLocaleResolver {

    /**
     * <p>
     * 解析当前消息处理应使用的语言。
     * </p>
     * @return 当前语言
     */
    Locale resolveLocale();
}
