package io.github.coco.web.i18n;

import java.util.Locale;
import java.util.Objects;

import io.github.coco.i18n.CocoI18nProperties;
import io.github.coco.i18n.CocoLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Coco Web 请求语言解析器。
 * <p>
 * 在 Servlet 请求中优先根据 {@code Accept-Language} 请求头解析语言；当请求未声明语言时，回退到
 * Coco 国际化配置的默认语言，避免受服务器或 CI 运行环境默认语言影响。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoWebLocaleResolver implements CocoLocaleResolver {

    private final CocoI18nProperties properties;

    /**
     * <p>
     * 创建 Coco Web 请求语言解析器。
     * </p>
     * @param properties Coco 国际化配置
     */
    public CocoWebLocaleResolver(CocoI18nProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale resolveLocale() {
        Locale requestLocale = resolveRequestLocale();
        return requestLocale == null ? this.properties.getDefaultLocale() : requestLocale;
    }

    /**
     * <p>
     * 从当前 Servlet 请求解析语言。
     * </p>
     * @return 请求声明的语言；非 Servlet 请求或未声明语言时返回 {@code null}
     */
    private static Locale resolveRequestLocale() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        return request.getLocale();
    }
}
