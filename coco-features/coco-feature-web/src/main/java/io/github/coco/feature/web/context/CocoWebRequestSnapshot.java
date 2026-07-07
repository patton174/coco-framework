package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextAttributes;
import io.github.coco.common.context.CocoRequestContextValueCodec;
import io.github.coco.feature.web.body.CocoRequestBodyMetadata;
import io.github.coco.feature.web.context.payload.CocoWebPayloadParseStatus;
import io.github.coco.feature.web.context.target.CocoWebRequestTarget;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolution;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityMetadata;

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
 * @param cookies Cookie 快照
 * @param parameters 请求参数快照
 * @param queryParameters 查询参数快照
 * @param payloadParameters 请求体参数快照
 * @param securityInput 请求安全输入
 * @param requestBody 请求体元数据
 * @param securityMetadata 请求安全元数据
 * @param browserFingerprint 浏览器指纹
 * @param clientIpResolution 客户端 IP 解析结果
 * @param targetResolution 请求目标解析结果
 * @param payloadSource 请求体参数来源
 * @param contextPhase 请求上下文阶段
 * @param payloadParseStatus 请求体参数解析状态
 * @param contextAttributes 请求上下文扩展属性
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
        String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
        String contentType, Map<String, String> headers, Map<String, String> cookies,
        Map<String, List<String>> parameters, Map<String, List<String>> queryParameters,
        Map<String, List<String>> payloadParameters,
        CocoWebRequestSecurityInput securityInput, CocoRequestBodyMetadata requestBody,
        CocoWebRequestSecurityMetadata securityMetadata, CocoBrowserFingerprint browserFingerprint,
        CocoClientIpResolution clientIpResolution, CocoWebRequestTargetResolution targetResolution,
        CocoWebParameterSource payloadSource,
        CocoWebRequestContextPhase contextPhase, CocoWebPayloadParseStatus payloadParseStatus,
        Map<String, String> contextAttributes) {

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
                contentType, headers, Map.of(), parameters, Map.of(), Map.of(),
                new CocoWebRequestSecurityInput(method, path, null, Map.of(), Map.of(), Map.of(), null),
                CocoRequestBodyMetadata.empty(), CocoWebRequestSecurityMetadata.empty(), CocoBrowserFingerprint.empty(),
                CocoClientIpResolution.custom(clientIp), null, null, null, null, Map.of());
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
                contentType, headers, Map.of(), parameters, Map.of(), Map.of(), securityInput, null,
                CocoWebRequestSecurityMetadata.empty(),
                browserFingerprint,
                CocoClientIpResolution.custom(clientIp), null, null, null, null, Map.of());
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
                contentType, headers, Map.of(), parameters, Map.of(), Map.of(), securityInput, null,
                CocoWebRequestSecurityMetadata.empty(),
                browserFingerprint, clientIpResolution, null, null, null, null, Map.of());
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
                contentType, headers, Map.of(), parameters, Map.of(), Map.of(), securityInput, null, securityMetadata,
                browserFingerprint,
                clientIpResolution, null, null, null, null, Map.of());
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
     * @param cookies Cookie 快照
     * @param parameters 请求参数快照
     * @param queryParameters 查询参数快照
     * @param payloadParameters 请求体参数快照
     * @param securityInput 请求安全输入
     * @param requestBody 请求体元数据
     * @param securityMetadata 请求安全元数据
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, String> cookies,
            Map<String, List<String>> parameters, Map<String, List<String>> queryParameters,
            Map<String, List<String>> payloadParameters,
            CocoWebRequestSecurityInput securityInput, CocoRequestBodyMetadata requestBody,
            CocoWebRequestSecurityMetadata securityMetadata, CocoBrowserFingerprint browserFingerprint,
            CocoClientIpResolution clientIpResolution) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, cookies, parameters, queryParameters, payloadParameters, securityInput,
                requestBody, securityMetadata, browserFingerprint, clientIpResolution, null, null, null, null,
                Map.of());
    }

    /**
     * <p>
     * 创建 Web 请求快照，并携带请求目标、请求体来源和阶段信息。
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
     * @param cookies Cookie 快照
     * @param parameters 请求参数快照
     * @param queryParameters 查询参数快照
     * @param payloadParameters 请求体参数快照
     * @param securityInput 请求安全输入
     * @param requestBody 请求体元数据
     * @param securityMetadata 请求安全元数据
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     * @param targetResolution 请求目标解析结果
     * @param payloadSource 请求体参数来源
     * @param contextPhase 请求上下文阶段
     * @param payloadParseStatus 请求体参数解析状态
     */
    public CocoWebRequestSnapshot(String traceId, String method, String path, String queryString,
            String clientIp, String userAgent, String locale, String scheme, String host, Integer port,
            String contentType, Map<String, String> headers, Map<String, String> cookies,
            Map<String, List<String>> parameters, Map<String, List<String>> queryParameters,
            Map<String, List<String>> payloadParameters,
            CocoWebRequestSecurityInput securityInput, CocoRequestBodyMetadata requestBody,
            CocoWebRequestSecurityMetadata securityMetadata, CocoBrowserFingerprint browserFingerprint,
            CocoClientIpResolution clientIpResolution, CocoWebRequestTargetResolution targetResolution,
            CocoWebParameterSource payloadSource, CocoWebRequestContextPhase contextPhase,
            CocoWebPayloadParseStatus payloadParseStatus) {
        this(traceId, method, path, queryString, clientIp, userAgent, locale, scheme, host, port,
                contentType, headers, cookies, parameters, queryParameters, payloadParameters, securityInput,
                requestBody, securityMetadata, browserFingerprint, clientIpResolution, targetResolution,
                payloadSource, contextPhase, payloadParseStatus, Map.of());
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
     * @param cookies Cookie 快照
     * @param parameters 请求参数快照
     * @param queryParameters 查询参数快照
     * @param payloadParameters 请求体参数快照
     * @param securityInput 请求安全输入
     * @param requestBody 请求体元数据
     * @param securityMetadata 请求安全元数据
     * @param browserFingerprint 浏览器指纹
     * @param clientIpResolution 客户端 IP 解析结果
     * @param targetResolution 请求目标解析结果
     * @param payloadSource 请求体参数来源
     * @param contextPhase 请求上下文阶段
     * @param payloadParseStatus 请求体参数解析状态
     * @param contextAttributes 请求上下文扩展属性
     */
    public CocoWebRequestSnapshot {
        traceId = requireTraceId(traceId);
        method = normalizeMethod(method);
        CocoWebRequestTarget normalizedTarget = new CocoWebRequestTarget(scheme, host, port, path);
        path = normalizedTarget.path();
        queryString = normalizeOptional(queryString);
        clientIpResolution = clientIpResolution == null ? CocoClientIpResolution.custom(clientIp) : clientIpResolution;
        clientIp = firstNonBlank(clientIp, clientIpResolution.clientIp());
        userAgent = normalizeOptional(userAgent);
        locale = normalizeOptional(locale);
        scheme = normalizedTarget.scheme();
        host = normalizedTarget.host();
        port = normalizedTarget.port();
        contentType = normalizeOptional(contentType);
        headers = copyHeaders(headers);
        cookies = copyCookies(cookies);
        parameters = copyParameters(parameters);
        queryParameters = copyParameters(queryParameters);
        payloadParameters = copyParameters(payloadParameters);
        payloadSource = normalizePayloadSource(payloadSource, payloadParameters, securityInput);
        securityInput = securityInput == null ? CocoWebRequestSecurityInput.empty() : securityInput;
        requestBody = requestBody == null
                ? CocoRequestBodyMetadata.fromEffective(securityInput.bodySha256(), securityInput.bodyLength(),
                        securityInput.bodyCached())
                : requestBody;
        securityMetadata = securityMetadata == null ? CocoWebRequestSecurityMetadata.empty() : securityMetadata;
        browserFingerprint = browserFingerprint == null ? CocoBrowserFingerprint.empty() : browserFingerprint;
        targetResolution = targetResolution == null
                ? CocoWebRequestTargetResolution.custom(normalizedTarget)
                : targetResolution.withTarget(normalizedTarget);
        contextPhase = contextPhase == null ? resolveContextPhase(requestBody) : contextPhase;
        payloadParseStatus = payloadParseStatus == null
                ? resolvePayloadParseStatus(payloadParameters)
                : payloadParseStatus;
        contextAttributes = copyContextAttributes(contextAttributes);
    }

    /**
     * <p>
     * 返回当前请求快照中的参数快照。
     * </p>
     * @return 参数快照
     */
    public CocoWebRequestParameters parameterSnapshot() {
        return new CocoWebRequestParameters(this.queryString, this.parameters, this.queryParameters,
                this.payloadParameters, this.payloadSource);
    }

    /**
     * <p>
     * 返回更新请求上下文阶段后的请求快照副本。
     * </p>
     * @param phase 目标阶段
     * @return 更新后的请求快照
     */
    public CocoWebRequestSnapshot withContextPhase(CocoWebRequestContextPhase phase) {
        CocoWebRequestContextPhase nextPhase = this.contextPhase.merge(phase);
        if (nextPhase == this.contextPhase) {
            return this;
        }
        return new CocoWebRequestSnapshot(this.traceId, this.method, this.path, this.queryString,
                this.clientIp, this.userAgent, this.locale, this.scheme, this.host, this.port, this.contentType,
                this.headers, this.cookies, this.parameters, this.queryParameters, this.payloadParameters,
                this.securityInput, this.requestBody, this.securityMetadata, this.browserFingerprint,
                this.clientIpResolution, this.targetResolution, this.payloadSource, nextPhase,
                this.payloadParseStatus, this.contextAttributes);
    }

    /**
     * <p>
     * 返回携带已解析安全元数据的请求快照副本。
     * </p>
     * @param metadata 已解析的安全元数据
     * @return 更新后的请求快照
     */
    public CocoWebRequestSnapshot withSecurityMetadata(CocoWebRequestSecurityMetadata metadata) {
        CocoWebRequestSecurityMetadata nextMetadata = metadata == null
                ? CocoWebRequestSecurityMetadata.empty()
                : metadata;
        if (nextMetadata.equals(this.securityMetadata)) {
            return this;
        }
        return new CocoWebRequestSnapshot(this.traceId, this.method, this.path, this.queryString,
                this.clientIp, this.userAgent, this.locale, this.scheme, this.host, this.port, this.contentType,
                this.headers, this.cookies, this.parameters, this.queryParameters, this.payloadParameters,
                this.securityInput, this.requestBody, nextMetadata, this.browserFingerprint,
                this.clientIpResolution, this.targetResolution, this.payloadSource, this.contextPhase,
                this.payloadParseStatus, this.contextAttributes);
    }

    /**
     * <p>
     * 返回合并请求上下文扩展属性后的请求快照副本。
     * </p>
     * @param attributes 请求上下文扩展属性
     * @return 更新后的请求快照
     */
    public CocoWebRequestSnapshot withContextAttributes(Map<String, String> attributes) {
        Map<String, String> normalizedAttributes = copyContextAttributes(attributes);
        if (normalizedAttributes.isEmpty()) {
            return this;
        }
        Map<String, String> mergedAttributes = new LinkedHashMap<>(this.contextAttributes);
        mergedAttributes.putAll(normalizedAttributes);
        Map<String, String> nextAttributes = copyContextAttributes(mergedAttributes);
        if (nextAttributes.equals(this.contextAttributes)) {
            return this;
        }
        return new CocoWebRequestSnapshot(this.traceId, this.method, this.path, this.queryString,
                this.clientIp, this.userAgent, this.locale, this.scheme, this.host, this.port, this.contentType,
                this.headers, this.cookies, this.parameters, this.queryParameters, this.payloadParameters,
                this.securityInput, this.requestBody, this.securityMetadata, this.browserFingerprint,
                this.clientIpResolution, this.targetResolution, this.payloadSource, this.contextPhase,
                this.payloadParseStatus, nextAttributes);
    }

    /**
     * <p>
     * 返回追加单个请求上下文扩展属性后的请求快照副本。
     * </p>
     * @param name 属性名称
     * @param value 属性值
     * @return 更新后的请求快照
     */
    public CocoWebRequestSnapshot withContextAttribute(String name, String value) {
        String normalizedName = normalizeOptional(name);
        String normalizedValue = normalizeOptional(value);
        if (normalizedName == null || normalizedValue == null) {
            return this;
        }
        return withContextAttributes(Map.of(normalizedName, normalizedValue));
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
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER_VALUE,
                this.clientIpResolution.sourceHeaderValue());
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS,
                this.clientIpResolution.remoteAddress());
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY,
                Boolean.toString(this.clientIpResolution.trustedProxy()));
        putIfPresent(attributes, CocoRequestContextAttributes.CLIENT_IP_CHAIN,
                CocoRequestContextValueCodec.encodeList(this.clientIpResolution.sourceChain()));
        if (this.clientIpResolution.resolvedChainIndex() != null) {
            attributes.put(CocoRequestContextAttributes.CLIENT_IP_RESOLVED_CHAIN_INDEX,
                    Integer.toString(this.clientIpResolution.resolvedChainIndex()));
        }
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_TARGET_SOURCE, this.targetResolution.source().name());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_TARGET_REMOTE_ADDRESS,
                this.targetResolution.remoteAddress());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_TARGET_TRUSTED_PROXY,
                Boolean.toString(this.targetResolution.trustedProxy()));
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_TARGET_SOURCE_HEADERS,
                CocoRequestContextValueCodec.encodeList(this.targetResolution.sourceHeaders()));
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_TARGET_FORWARDED_PREFIX,
                this.targetResolution.forwardedPrefix());
        putIfPresent(attributes, CocoRequestContextAttributes.USER_AGENT, this.userAgent);
        putIfPresent(attributes, CocoRequestContextAttributes.QUERY_STRING, this.queryString);
        putIfPresent(attributes, CocoRequestContextAttributes.LOCALE, this.locale);
        putIfPresent(attributes, CocoRequestContextAttributes.SCHEME, this.scheme);
        putIfPresent(attributes, CocoRequestContextAttributes.HOST, this.host);
        putIfPresent(attributes, CocoRequestContextAttributes.PORT, this.port == null ? null : this.port.toString());
        putIfPresent(attributes, CocoRequestContextAttributes.CONTENT_TYPE, this.contentType);
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_CONTEXT_PHASE, this.contextPhase.id());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_PAYLOAD_SOURCE,
                this.payloadSource == null ? null : this.payloadSource.name());
        putIfPresent(attributes, CocoRequestContextAttributes.REQUEST_PAYLOAD_PARSE_STATUS,
                this.payloadParseStatus.id());
        putIfPresent(attributes, CocoRequestContextAttributes.BROWSER_FINGERPRINT, this.browserFingerprint.value());
        this.browserFingerprint.signals().forEach((name, value) ->
                putIfPresent(attributes, CocoRequestContextAttributes.browserFingerprintSignal(name), value));
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
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_APP_ID, this.securityMetadata.signatureAppId());
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_KEY_ID, this.securityMetadata.signatureKeyId());
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_TIMESTAMP,
                this.securityMetadata.signatureTimestamp());
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_NONCE, this.securityMetadata.signatureNonce());
        putIfPresent(attributes, CocoRequestContextAttributes.SIGNATURE_VALUE, this.securityMetadata.signature());
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
        putIfPresent(attributes, CocoRequestContextAttributes.ENCRYPTION_APP_ID, this.securityMetadata.encryptionAppId());
        putIfPresent(attributes, CocoRequestContextAttributes.ENCRYPTION_KEY_ID, this.securityMetadata.encryptionKeyId());
        putIfPresent(attributes, CocoRequestContextAttributes.ENCRYPTION_IV, this.securityMetadata.encryptionIv());
        putIfPresent(attributes, CocoRequestContextAttributes.REPLAY_APP_ID, this.securityMetadata.replayAppId());
        putIfPresent(attributes, CocoRequestContextAttributes.REPLAY_KEY_ID, this.securityMetadata.replayKeyId());
        putIfPresent(attributes, CocoRequestContextAttributes.REPLAY_TIMESTAMP,
                this.securityMetadata.replayTimestamp());
        putIfPresent(attributes, CocoRequestContextAttributes.REPLAY_NONCE, this.securityMetadata.replayNonce());
        this.headers.forEach((name, value) ->
                putIfPresent(attributes, CocoRequestContextAttributes.header(name), value));
        this.cookies.forEach((name, value) ->
                putIfPresent(attributes, CocoRequestContextAttributes.cookie(name), value));
        this.parameters.forEach((name, values) ->
                putIfPresent(attributes, CocoRequestContextAttributes.parameter(name),
                        CocoRequestContextValueCodec.encodeList(values)));
        this.queryParameters.forEach((name, values) ->
                putIfPresent(attributes, CocoRequestContextAttributes.queryParameter(name),
                        CocoRequestContextValueCodec.encodeList(values)));
        this.payloadParameters.forEach((name, values) ->
                putIfPresent(attributes, CocoRequestContextAttributes.payloadParameter(name),
                        CocoRequestContextValueCodec.encodeList(values)));
        this.contextAttributes.forEach((name, value) -> putIfPresent(attributes, name, value));
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

    private static Map<String, String> copyCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        cookies.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                copied.put(normalizedName, normalizedValue);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<String, String> copyContextAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        attributes.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                copied.put(normalizedName, normalizedValue);
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
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

    private static CocoWebParameterSource normalizePayloadSource(CocoWebParameterSource payloadSource,
            Map<String, List<String>> payloadParameters, CocoWebRequestSecurityInput securityInput) {
        if (payloadSource != null && payloadSource.payload()) {
            return payloadSource;
        }
        if (securityInput != null && securityInput.payloadSource().payload()) {
            return securityInput.payloadSource();
        }
        if (payloadParameters == null || payloadParameters.isEmpty()) {
            return CocoWebParameterSource.NONE;
        }
        return CocoWebParameterSource.PAYLOAD;
    }

    private static CocoWebRequestContextPhase resolveContextPhase(CocoRequestBodyMetadata requestBody) {
        if (requestBody != null && requestBody.stage() == io.github.coco.feature.web.body.CocoRequestBodyStage.DECRYPTED) {
            return CocoWebRequestContextPhase.DECRYPTED;
        }
        return CocoWebRequestContextPhase.TRANSPORT_CAPTURED;
    }

    private static CocoWebPayloadParseStatus resolvePayloadParseStatus(Map<String, List<String>> payloadParameters) {
        return payloadParameters == null || payloadParameters.isEmpty()
                ? CocoWebPayloadParseStatus.NO_BODY
                : CocoWebPayloadParseStatus.PARSED;
    }
}
