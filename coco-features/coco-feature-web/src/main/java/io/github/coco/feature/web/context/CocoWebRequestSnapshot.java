package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextAttributes;
import io.github.coco.feature.web.body.CocoRequestBodyMetadata;

/**
 * Coco Web 请求快照。
 * <p>
 * 保存一次 Servlet 请求中解析出的稳定上下文字段，供请求上下文和访问日志复用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param traceId TraceId
 * @param method HTTP 方法
 * @param path 请求路径
 * @param queryString 查询字符串
 * @param clientIp 客户端 IP
 * @param userAgent User-Agent
 * @param locale 请求语言
 * @param scheme 请求协议
 * @param host 请求主机
 * @param port 请求端口
 * @param contentType 请求内容类型
 * @param headers 请求头快照
 * @param parameters 请求参数快照
 * @param securityInput 请求安全输入
 * @param requestBody 请求体元数据
 * @param securityMetadata 请求安全元数据
 * @param browserFingerprint 浏览器指纹
 * @param clientIpResolution 客户端 IP 解析结果
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
        String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
        String contentType, Map<String, String> headers, Map<String, List<String>> parameters,
        CocoWebRequestSecurityInput securityInput, CocoRequestBodyMetadata requestBody,
        CocoWebRequestSecurityMetadata securityMetadata, CocoBrowserFingerprint browserFingerprint,
        CocoClientIpResolution clientIpResolution) {

    /**
     * <p>
     * 创建 Web 请求快照。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 查询字符串
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param locale 请求语言
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param contentType 请求内容类型
     * @param headers 请求头快照
     * @param parameters 请求参数快照
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, List<String>> parameters) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, parameters,
                new CocoWebRequestSecurityInput(method, path, null, Map.of(), Map.of(), Map.of(), null),
                CocoRequestBodyMetadata.empty(), CocoWebRequestSecurityMetadata.empty(), CocoBrowserFingerprint.empty(),
                CocoClientIpResolution.custom(clientIp));
    }

    /**
     * <p>
     * 创建 Web 请求快照。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 查询字符串
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param locale 请求语言
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param contentType 请求内容类型
     * @param headers 请求头快照
     * @param parameters 请求参数快照
     * @param securityInput 请求安全输入
     * @param browserFingerprint 浏览器指纹
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, List<String>> parameters,
            CocoWebRequestSecurityInput securityInput, CocoBrowserFingerprint browserFingerprint) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, parameters, securityInput, null, CocoWebRequestSecurityMetadata.empty(),
                browserFingerprint,
                CocoClientIpResolution.custom(clientIp));
    }

    /**
     * <p>
     * 创建 Web 请求快照。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 查询字符串
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param locale 请求语言
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param contentType 请求内容类型
     * @param headers 请求头快照
     * @param parameters 请求参数快照
     * @param securityInput 请求安全输入
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, List<String>> parameters,
            CocoWebRequestSecurityInput securityInput, CocoBrowserFingerprint browserFingerprint,
            CocoClientIpResolution clientIpResolution) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, parameters, securityInput, null, CocoWebRequestSecurityMetadata.empty(),
                browserFingerprint, clientIpResolution);
    }

    /**
     * <p>
     * 创建 Web 请求快照。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 查询字符串
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param locale 请求语言
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param contentType 请求内容类型
     * @param headers 请求头快照
     * @param parameters 请求参数快照
     * @param securityInput 请求安全输入
     * @param securityMetadata 请求安全元数据
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, List<String>> parameters,
            CocoWebRequestSecurityInput securityInput, CocoWebRequestSecurityMetadata securityMetadata,
            CocoBrowserFingerprint browserFingerprint, CocoClientIpResolution clientIpResolution) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, parameters, securityInput, null, securityMetadata, browserFingerprint,
                clientIpResolution);
    }

    /**
     * <p>
     * 创建 Web 请求快照，并归一化空白字段和集合字段。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 查询字符串
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param locale 请求语言
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param contentType 请求内容类型
     * @param headers 请求头快照
     * @param parameters 请求参数快照
     * @param securityInput 请求安全输入
     * @param requestBody 请求体元数据
     * @param securityMetadata 请求安全元数据
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     */
    public CocoWebRequestSnapshot {
        traceId = requireTraceId(traceId);
        method = normalizeMethod(method);
        path = normalizeOptional(path);
        queryString = normalizeOptional(queryString);
        clientIpResolution = clientIpResolution == null ? CocoClientIpResolution.custom(clientIp) : clientIpResolution;
        clientIp = firstNonBlank(clientIp, clientIpResolution.clientIp());
        userAgent = normalizeOptional(userAgent);
        locale = normalizeOptional(locale);
        scheme = normalizeOptional(scheme);
        host = normalizeOptional(host);
        contentType = normalizeOptional(contentType);
        headers = copyHeaders(headers);
        parameters = copyParameters(parameters);
        securityInput = securityInput == null ? CocoWebRequestSecurityInput.empty() : securityInput;
        requestBody = requestBody == null
                ? CocoRequestBodyMetadata.fromEffective(securityInput.bodySha256(), securityInput.bodyLength(),
                        securityInput.bodyCached())
                : requestBody;
        securityMetadata = securityMetadata == null ? CocoWebRequestSecurityMetadata.empty() : securityMetadata;
        browserFingerprint = browserFingerprint == null ? CocoBrowserFingerprint.empty() : browserFingerprint;
    }

    /**
     * <p>
     * 转换为公共请求上下文。
     * </p>
     * @return 公共请求上下文
     */
    public CocoRequestContext toRequestContext() {
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP, this.clientIp);
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_SOURCE, this.clientIpResolution.source().name());
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER,
                this.clientIpResolution.sourceHeaderName());
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS,
                this.clientIpResolution.remoteAddress());
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY,
                Boolean.toString(this.clientIpResolution.trustedProxy()));
        putIfPresent(attributes, CocoRequestContextAttributes.USER_AGENT, this.userAgent);
        putIfPresent(attributes, CocoRequestContextAttributes.QUERY_STRING, this.queryString);
        putIfPresent(attributes, CocoRequestContextAttributes.LOCALE, this.locale);
        putIfPresent(attributes, CocoRequestContextAttributes.SCHEME, this.scheme);
        putIfPresent(attributes, CocoRequestContextAttributes.HOST, this.host);
        putIfPresent(attributes, CocoRequestContextAttributes.PORT, this.port == null ? null : this.port.toString());
        putIfPresent(attributes, CocoRequestContextAttributes.CONTENT_TYPE, this.contentType);
        putIfPresent(attributes, CocoRequestContextAttributes.BROWSER_FINGERPRINT, this.browserFingerprint.value());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_SHA256, this.requestBody.effectiveSha256());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_SHA256,
                this.requestBody.effectiveSha256());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_SHA256,
                this.requestBody.transportSha256());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH,
                this.requestBody.effectiveLength() == null ? null : this.requestBody.effectiveLength().toString());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH,
                this.requestBody.transportLength() == null ? null : this.requestBody.transportLength().toString());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_BODY_STAGE, this.requestBody.stage().id());
        this.securityMetadata.primaryAppId()
                .ifPresent(appId -> putIfPresent(attributes, CocoRequestContextAttributes.SECURITY_APP_ID, appId));
        this.securityMetadata.primaryKeyId()
                .ifPresent(keyId -> putIfPresent(attributes, CocoRequestContextAttributes.SECURITY_KEY_ID, keyId));
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_SIGNED,
                Boolean.toString(this.securityMetadata.signed()));
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_ENCRYPTED,
                Boolean.toString(this.securityMetadata.encrypted()));
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_REPLAY_PROTECTED,
                Boolean.toString(this.securityMetadata.replayProtected()));
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_ALGORITHM,
                this.securityMetadata.signatureAlgorithm());
        putIfPresent(attributes, CocoRequestContextAttributes.ENCRYPTION_ALGORITHM,
                this.securityMetadata.encryptionAlgorithm());
        this.headers.forEach((name, value) ->
                putIfPresent(attributes, CocoRequestContextAttributes.header(name), value));
        this.parameters.forEach((name, values) ->
                putIfPresent(attributes, CocoRequestContextAttributes.parameter(name), String.join(",", values)));
        return CocoRequestContext.of(this.traceId, this.method, this.path, attributes);
    }

    private static void putIfPresent(Map<String, String> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(name, value);
        }
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return traceId.trim();
    }

    private static String normalizeMethod(String method) {
        String normalized = normalizeOptional(method);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeOptional(first);
        return normalizedFirst == null ? normalizeOptional(second) : normalizedFirst;
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                copied.put(normalizedName.toLowerCase(Locale.ROOT), normalizedValue);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<String, List<String>> copyParameters(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName != null) {
                copied.put(normalizedName, copyParameterValues(values));
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static List<String> copyParameterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        List<String> copied = new ArrayList<>(values.size());
        for (String value : values) {
            copied.add(value == null || value.isBlank() ? "" : value.trim());
        }
        return List.copyOf(copied);
    }
}
