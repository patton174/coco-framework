package io.github.coco.common.i18n.internal;

import java.util.Locale;
import java.util.Objects;

import io.github.coco.common.i18n.CocoI18nProperties;
import io.github.coco.common.i18n.api.CocoLocaleResolver;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 默认 Coco 语言解析器。
 * <p>
 * 优先使用 Spring 当前线程绑定的语言上下文；当没有请求语言上下文时，返回配置中的默认语言。
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
        Locale locale = resolveContextLocale();
        return locale == null ? this.properties.getDefaultLocale() : locale;
    }

    /**
     * <p>
     * 解析 Spring 当前线程绑定的语言上下文。
     * </p>
     * <p>
     * Web 请求进入 Spring MVC 后，{@link LocaleContextHolder} 会携带由请求头或应用
     * {@code LocaleResolver} 解析出的语言；非 Web 场景下该上下文为空。
     * </p>
     * @return 当前线程语言；没有绑定语言时返回 {@code null}
     */
    private static Locale resolveContextLocale() {
        LocaleContext localeContext = LocaleContextHolder.getLocaleContext();
        return localeContext == null ? null : localeContext.getLocale();
    }
}
