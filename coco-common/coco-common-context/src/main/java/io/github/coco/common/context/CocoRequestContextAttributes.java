package io.github.coco.common.context;

import java.util.Locale;

/**
 * Coco 请求上下文标准属性键。
 * <p>
 * 统一定义框架内部共享的请求上下文字段，避免 Web、日志、审计、租户和数据权限等模块各自约定属性名称。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoRequestContextAttributes {

    /**
     * 客户端 IP 属性键。
     */
    public static final String CLIENT_IP = "clientIp";

    /**
     * User-Agent 属性键。
     */
    public static final String USER_AGENT = "userAgent";

    /**
     * 查询字符串属性键。
     */
    public static final String QUERY_STRING = "queryString";

    /**
     * 请求语言属性键。
     */
    public static final String LOCALE = "locale";

    /**
     * 请求协议属性键。
     */
    public static final String SCHEME = "scheme";

    /**
     * 请求主机属性键。
     */
    public static final String HOST = "host";

    /**
     * 请求端口属性键。
     */
    public static final String PORT = "port";

    /**
     * 请求内容类型属性键。
     */
    public static final String CONTENT_TYPE = "contentType";

    /**
     * 浏览器指纹属性键。
     */
    public static final String BROWSER_FINGERPRINT = "browserFingerprint";

    /**
     * 请求头属性键前缀。
     */
    public static final String HEADER_PREFIX = "header.";

    /**
     * 请求参数属性键前缀。
     */
    public static final String PARAMETER_PREFIX = "parameter.";

    private CocoRequestContextAttributes() {
    }

    /**
     * <p>
     * 创建请求头上下文属性键。
     * </p>
     * @param name 请求头名称
     * @return 请求头上下文属性键
     */
    public static String header(String name) {
        return HEADER_PREFIX + normalizeHeaderName(name);
    }

    /**
     * <p>
     * 创建请求参数上下文属性键。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数上下文属性键
     */
    public static String parameter(String name) {
        return PARAMETER_PREFIX + normalizeSegment(name);
    }

    private static String normalizeHeaderName(String name) {
        return normalizeSegment(name).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSegment(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute segment must not be blank");
        }
        return name.trim();
    }
}
