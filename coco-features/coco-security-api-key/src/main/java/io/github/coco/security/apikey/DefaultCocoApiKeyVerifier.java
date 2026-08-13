package io.github.coco.security.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;

/**
 * 基于 SHA-256 摘要的默认 API Key 校验器。
 * <p>
 * 初始化阶段只保留固定长度摘要字节和安全主体。每次校验会完整遍历所有凭据，并通过
 * {@link MessageDigest#isEqual(byte[], byte[])} 完成比较，避免根据 Key 内容提前暴露匹配位置。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoApiKeyVerifier implements CocoApiKeyVerifier {

    private final List<Credential> credentials;

    /**
     * 创建默认 API Key 校验器。
     * @param configuredCredentials 已验证的凭据配置
     */
    public DefaultCocoApiKeyVerifier(Map<String, CocoApiKeyProperties.Credential> configuredCredentials) {
        Objects.requireNonNull(configuredCredentials, "configuredCredentials must not be null");
        List<Credential> resolved = new ArrayList<>(configuredCredentials.size());
        for (CocoApiKeyProperties.Credential configuredCredential : configuredCredentials.values()) {
            byte[] digest = HexFormat.of().parseHex(configuredCredential.getSha256());
            CocoSecurityPrincipal principal = new CocoSecurityPrincipal(configuredCredential.getPrincipalId(),
                    configuredCredential.getPrincipalName(), configuredCredential.getRoles(),
                    configuredCredential.getPermissions(), configuredCredential.getAttributes());
            resolved.add(new Credential(digest, principal));
        }
        this.credentials = List.copyOf(resolved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityPrincipal> verify(String key) {
        Objects.requireNonNull(key, "key must not be null");
        byte[] candidateDigest = sha256(key);
        CocoSecurityPrincipal matched = null;
        for (Credential credential : this.credentials) {
            boolean currentMatch = MessageDigest.isEqual(credential.digest(), candidateDigest);
            if (currentMatch && matched == null) {
                matched = credential.principal();
            }
        }
        return Optional.ofNullable(matched);
    }

    private static byte[] sha256(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Credential(byte[] digest, CocoSecurityPrincipal principal) {

        private Credential {
            digest = digest.clone();
            principal = Objects.requireNonNull(principal, "principal must not be null");
        }
    }
}
