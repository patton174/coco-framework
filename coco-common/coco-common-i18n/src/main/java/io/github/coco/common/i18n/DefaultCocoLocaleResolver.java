package io.github.coco.common.i18n;

import java.util.Locale;
import java.util.Objects;

/**
 * 默认 Coco 语言解析器。
 * <p>
 * 当前阶段只返回配置中的默认语言，避免通用模块依赖 Servlet 请求上下文。
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
public final class DefaultCocoLocaleResolver implements CocoLocaleResolver {

    private final CocoI18nProperties properties;

    /**
     * <p>
     * 创建基于 Coco 国际化配置的语言解析器。
     * </p>
     * @param properties Coco 国际化配置
     */
    public DefaultCocoLocaleResolver(CocoI18nProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale resolveLocale() {
        return this.properties.getDefaultLocale();
    }
}
