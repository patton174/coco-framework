package io.github.coco.feature.web.request.metadata;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;

/**
 * Coco Web 安全协议请求头名称规则。
 *
 * <p>接收端和出站签名端应使用同一份基集配置；自定义发送端基集时，接收端必须配置相同契约。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoWebSecurityHeaderNames {

    private CocoWebSecurityHeaderNames() {
    }

    /**
     * 返回安全能力需要读取的请求头名称。
     *
     * @param configuredNames 配置的请求头基集
     * @param signatureProperties 签名协议配置
     * @param encryptionProperties 加密协议配置
     * @param replayProperties 防重放协议配置
     * @return 归一化且不可变的请求头名称
     */
    public static Set<String> securityHeaderNames(Set<String> configuredNames,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        LinkedHashSet<String> headerNames = normalized(configuredNames);
        CocoSignatureProperties signature = signature(signatureProperties);
        add(headerNames, signature.getAppIdHeaderName());
        add(headerNames, signature.getKeyIdHeaderName());
        add(headerNames, signature.getTimestampHeaderName());
        add(headerNames, signature.getNonceHeaderName());
        add(headerNames, signature.getSignatureHeaderName());
        add(headerNames, signature.getSignatureFallbackHeaderName());
        add(headerNames, signature.getAlgorithmHeaderName());
        addEncryptionAndReplayHeaders(headerNames, encryptionProperties, replayProperties, true);
        return Set.copyOf(headerNames);
    }

    /**
     * 返回参与规范化的请求头名称。
     *
     * @param configuredNames 配置的规范化请求头基集
     * @param signatureProperties 签名协议配置
     * @param encryptionProperties 加密协议配置
     * @param replayProperties 防重放协议配置
     * @return 归一化且不可变的请求头名称
     */
    public static Set<String> canonicalHeaderNames(Set<String> configuredNames,
            CocoSignatureProperties signatureProperties, CocoEncryptionProperties encryptionProperties,
            CocoReplayProperties replayProperties) {
        LinkedHashSet<String> headerNames = normalized(configuredNames);
        CocoSignatureProperties signature = signature(signatureProperties);
        add(headerNames, signature.getAppIdHeaderName());
        add(headerNames, signature.getKeyIdHeaderName());
        add(headerNames, signature.getTimestampHeaderName());
        add(headerNames, signature.getNonceHeaderName());
        add(headerNames, signature.getAlgorithmHeaderName());
        addEncryptionAndReplayHeaders(headerNames, encryptionProperties, replayProperties, false);
        return Set.copyOf(headerNames);
    }

    private static void addEncryptionAndReplayHeaders(Set<String> headerNames,
            CocoEncryptionProperties encryptionProperties, CocoReplayProperties replayProperties,
            boolean includeEncryptedMarker) {
        CocoEncryptionProperties encryption = encryptionProperties == null
                ? new CocoEncryptionProperties()
                : encryptionProperties;
        CocoReplayProperties replay = replayProperties == null ? new CocoReplayProperties() : replayProperties;
        if (includeEncryptedMarker) {
            add(headerNames, encryption.getEncryptedHeaderName());
        }
        add(headerNames, encryption.getAppIdHeaderName());
        add(headerNames, encryption.getKeyIdHeaderName());
        add(headerNames, encryption.getIvHeaderName());
        add(headerNames, encryption.getAlgorithmHeaderName());
        add(headerNames, replay.getAppIdHeaderName());
        add(headerNames, replay.getKeyIdHeaderName());
        add(headerNames, replay.getTimestampHeaderName());
        add(headerNames, replay.getNonceHeaderName());
    }

    private static CocoSignatureProperties signature(CocoSignatureProperties properties) {
        return properties == null ? new CocoSignatureProperties() : properties;
    }

    private static LinkedHashSet<String> normalized(Set<String> configuredNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (configuredNames != null) {
            configuredNames.forEach(name -> add(names, name));
        }
        return names;
    }

    private static void add(Set<String> headerNames, String headerName) {
        if (headerName != null && !headerName.isBlank()) {
            headerNames.add(headerName.trim().toLowerCase(Locale.ROOT));
        }
    }
}
