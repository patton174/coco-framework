package io.github.coco.web.encryption;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.coco.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.web.context.CocoWebRequestMatcherProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 请求加密配置属性�? * <p>
 * 控制 AES 解密设施的启用策略、请求头名称、密钥编码、IV 编码、密文编码和本地密钥映射�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoEncryptionProperties {

    private boolean enabled = true;

    private boolean required = false;

    private CocoWebSecurityMetadataSource metadataSource = CocoWebSecurityMetadataSource.HEADER;

    private String encryptedHeaderName = "X-Coco-Encrypted";

    private String encryptedParameterName = "encrypted";

    private String appIdHeaderName = "X-Coco-App-Id";

    private String appIdParameterName = "appId";

    private String keyIdHeaderName = "X-Coco-Key-Id";

    private String keyIdParameterName = "keyId";

    private String ivHeaderName = "X-Coco-IV";

    private String ivParameterName = "iv";

    private String algorithmHeaderName = "X-Coco-Algorithm";

    private String algorithmParameterName = "algorithm";

    private String payloadParameterName = "payload";

    private String defaultAlgorithm = "AES-GCM";

    private CocoCryptoTextEncoding keyEncoding = CocoCryptoTextEncoding.BASE64;

    private CocoCryptoTextEncoding ivEncoding = CocoCryptoTextEncoding.BASE64;

    private CocoCryptoTextEncoding payloadEncoding = CocoCryptoTextEncoding.BASE64;

    private int gcmTagLengthBits = 128;

    @NestedConfigurationProperty
    private CocoWebRequestMatcherProperties matcher = new CocoWebRequestMatcherProperties();

    private Map<String, String> keys = Map.of();

    /**
     * <p>
     * 返回是否启用 AES 解密设施�?     * </p>
     * @return 启用时返�?{@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 AES 解密设施�?     * </p>
     * @param enabled 是否启用 AES 解密设施
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回是否要求所有请求都必须加密�?     * </p>
     * @return 要求加密时返�?{@code true}
     */
    public boolean isRequired() {
        return this.required;
    }

    /**
     * <p>
     * 设置是否要求所有请求都必须加密�?     * </p>
     * @param required 是否要求加密
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * <p>
     * 返回加密协议材料解析来源�?     * </p>
     * @return 加密协议材料解析来源
     */
    public CocoWebSecurityMetadataSource getMetadataSource() {
        return this.metadataSource;
    }

    /**
     * <p>
     * 设置加密协议材料解析来源�?     * </p>
     * @param metadataSource 加密协议材料解析来源
     */
    public void setMetadataSource(CocoWebSecurityMetadataSource metadataSource) {
        this.metadataSource = metadataSource == null ? CocoWebSecurityMetadataSource.HEADER : metadataSource;
    }

    /**
     * <p>
     * 返回加密标记请求头名称�?     * </p>
     * @return 加密标记请求头名�?     */
    public String getEncryptedHeaderName() {
        return this.encryptedHeaderName;
    }

    /**
     * <p>
     * 设置加密标记请求头名称�?     * </p>
     * @param encryptedHeaderName 加密标记请求头名�?     */
    public void setEncryptedHeaderName(String encryptedHeaderName) {
        this.encryptedHeaderName = normalizeHeaderName(encryptedHeaderName, "X-Coco-Encrypted");
    }

    /**
     * <p>
     * 返回加密标记请求参数名称�?     * </p>
     * @return 加密标记请求参数名称
     */
    public String getEncryptedParameterName() {
        return this.encryptedParameterName;
    }

    /**
     * <p>
     * 设置加密标记请求参数名称�?     * </p>
     * @param encryptedParameterName 加密标记请求参数名称
     */
    public void setEncryptedParameterName(String encryptedParameterName) {
        this.encryptedParameterName = normalizeParameterName(encryptedParameterName, "encrypted");
    }

    /**
     * <p>
     * 返回 AppId 请求头名称�?     * </p>
     * @return AppId 请求头名�?     */
    public String getAppIdHeaderName() {
        return this.appIdHeaderName;
    }

    /**
     * <p>
     * 设置 AppId 请求头名称�?     * </p>
     * @param appIdHeaderName AppId 请求头名�?     */
    public void setAppIdHeaderName(String appIdHeaderName) {
        this.appIdHeaderName = normalizeHeaderName(appIdHeaderName, "X-Coco-App-Id");
    }

    /**
     * <p>
     * 返回 AppId 请求参数名称�?     * </p>
     * @return AppId 请求参数名称
     */
    public String getAppIdParameterName() {
        return this.appIdParameterName;
    }

    /**
     * <p>
     * 设置 AppId 请求参数名称�?     * </p>
     * @param appIdParameterName AppId 请求参数名称
     */
    public void setAppIdParameterName(String appIdParameterName) {
        this.appIdParameterName = normalizeParameterName(appIdParameterName, "appId");
    }

    /**
     * <p>
     * 返回 KeyId 请求头名称�?     * </p>
     * @return KeyId 请求头名�?     */
    public String getKeyIdHeaderName() {
        return this.keyIdHeaderName;
    }

    /**
     * <p>
     * 设置 KeyId 请求头名称�?     * </p>
     * @param keyIdHeaderName KeyId 请求头名�?     */
    public void setKeyIdHeaderName(String keyIdHeaderName) {
        this.keyIdHeaderName = normalizeHeaderName(keyIdHeaderName, "X-Coco-Key-Id");
    }

    /**
     * <p>
     * 返回 KeyId 请求参数名称�?     * </p>
     * @return KeyId 请求参数名称
     */
    public String getKeyIdParameterName() {
        return this.keyIdParameterName;
    }

    /**
     * <p>
     * 设置 KeyId 请求参数名称�?     * </p>
     * @param keyIdParameterName KeyId 请求参数名称
     */
    public void setKeyIdParameterName(String keyIdParameterName) {
        this.keyIdParameterName = normalizeParameterName(keyIdParameterName, "keyId");
    }

    /**
     * <p>
     * 返回 IV 请求头名称�?     * </p>
     * @return IV 请求头名�?     */
    public String getIvHeaderName() {
        return this.ivHeaderName;
    }

    /**
     * <p>
     * 设置 IV 请求头名称�?     * </p>
     * @param ivHeaderName IV 请求头名�?     */
    public void setIvHeaderName(String ivHeaderName) {
        this.ivHeaderName = normalizeHeaderName(ivHeaderName, "X-Coco-IV");
    }

    /**
     * <p>
     * 返回 IV 请求参数名称�?     * </p>
     * @return IV 请求参数名称
     */
    public String getIvParameterName() {
        return this.ivParameterName;
    }

    /**
     * <p>
     * 设置 IV 请求参数名称�?     * </p>
     * @param ivParameterName IV 请求参数名称
     */
    public void setIvParameterName(String ivParameterName) {
        this.ivParameterName = normalizeParameterName(ivParameterName, "iv");
    }

    /**
     * <p>
     * 返回算法请求头名称�?     * </p>
     * @return 算法请求头名�?     */
    public String getAlgorithmHeaderName() {
        return this.algorithmHeaderName;
    }

    /**
     * <p>
     * 设置算法请求头名称�?     * </p>
     * @param algorithmHeaderName 算法请求头名�?     */
    public void setAlgorithmHeaderName(String algorithmHeaderName) {
        this.algorithmHeaderName = normalizeHeaderName(algorithmHeaderName, "X-Coco-Algorithm");
    }

    /**
     * <p>
     * 返回算法请求参数名称�?     * </p>
     * @return 算法请求参数名称
     */
    public String getAlgorithmParameterName() {
        return this.algorithmParameterName;
    }

    /**
     * <p>
     * 设置算法请求参数名称�?     * </p>
     * @param algorithmParameterName 算法请求参数名称
     */
    public void setAlgorithmParameterName(String algorithmParameterName) {
        this.algorithmParameterName = normalizeParameterName(algorithmParameterName, "algorithm");
    }

    /**
     * <p>
     * 返回加密信封中的密文字段参数名称�?     * </p>
     * @return 加密信封中的密文字段参数名称
     */
    public String getPayloadParameterName() {
        return this.payloadParameterName;
    }

    /**
     * <p>
     * 设置加密信封中的密文字段参数名称�?     * </p>
     * @param payloadParameterName 加密信封中的密文字段参数名称
     */
    public void setPayloadParameterName(String payloadParameterName) {
        this.payloadParameterName = normalizeParameterName(payloadParameterName, "payload");
    }

    /**
     * <p>
     * 返回默认解密算法�?     * </p>
     * @return 默认解密算法
     */
    public String getDefaultAlgorithm() {
        return this.defaultAlgorithm;
    }

    /**
     * <p>
     * 设置默认解密算法�?     * </p>
     * @param defaultAlgorithm 默认解密算法
     */
    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm == null || defaultAlgorithm.isBlank()
                ? "AES-GCM"
                : defaultAlgorithm.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * <p>
     * 返回密钥文本编码�?     * </p>
     * @return 密钥文本编码
     */
    public CocoCryptoTextEncoding getKeyEncoding() {
        return this.keyEncoding;
    }

    /**
     * <p>
     * 设置密钥文本编码�?     * </p>
     * @param keyEncoding 密钥文本编码
     */
    public void setKeyEncoding(CocoCryptoTextEncoding keyEncoding) {
        this.keyEncoding = keyEncoding == null ? CocoCryptoTextEncoding.BASE64 : keyEncoding;
    }

    /**
     * <p>
     * 返回 IV 文本编码�?     * </p>
     * @return IV 文本编码
     */
    public CocoCryptoTextEncoding getIvEncoding() {
        return this.ivEncoding;
    }

    /**
     * <p>
     * 设置 IV 文本编码�?     * </p>
     * @param ivEncoding IV 文本编码
     */
    public void setIvEncoding(CocoCryptoTextEncoding ivEncoding) {
        this.ivEncoding = ivEncoding == null ? CocoCryptoTextEncoding.BASE64 : ivEncoding;
    }

    /**
     * <p>
     * 返回密文请求体文本编码�?     * </p>
     * @return 密文请求体文本编�?     */
    public CocoCryptoTextEncoding getPayloadEncoding() {
        return this.payloadEncoding;
    }

    /**
     * <p>
     * 设置密文请求体文本编码�?     * </p>
     * @param payloadEncoding 密文请求体文本编�?     */
    public void setPayloadEncoding(CocoCryptoTextEncoding payloadEncoding) {
        this.payloadEncoding = payloadEncoding == null ? CocoCryptoTextEncoding.BASE64 : payloadEncoding;
    }

    /**
     * <p>
     * 返回 GCM 认证标签长度，单�?bit�?     * </p>
     * @return GCM 认证标签长度
     */
    public int getGcmTagLengthBits() {
        return this.gcmTagLengthBits;
    }

    /**
     * <p>
     * 设置 GCM 认证标签长度，单�?bit�?     * </p>
     * @param gcmTagLengthBits GCM 认证标签长度
     */
    public void setGcmTagLengthBits(int gcmTagLengthBits) {
        this.gcmTagLengthBits = gcmTagLengthBits <= 0 ? 128 : gcmTagLengthBits;
    }

    /**
     * <p>
     * 返回请求加密路径和方法匹配配置�?     * </p>
     * @return 请求匹配配置
     */
    public CocoWebRequestMatcherProperties getMatcher() {
        return this.matcher;
    }

    /**
     * <p>
     * 设置请求加密路径和方法匹配配置�?     * </p>
     * @param matcher 请求匹配配置
     */
    public void setMatcher(CocoWebRequestMatcherProperties matcher) {
        this.matcher = matcher == null ? new CocoWebRequestMatcherProperties() : matcher;
    }

    /**
     * <p>
     * 返回本地 AES 密钥映射�?     * </p>
     * <p>
     * 键可以是 {@code appId} �?{@code appId:keyId}，后者优先级更高�?     * </p>
     * @return 本地 AES 密钥映射
     */
    public Map<String, String> getKeys() {
        return this.keys;
    }

    /**
     * <p>
     * 设置本地 AES 密钥映射�?     * </p>
     * @param keys 本地 AES 密钥映射
     */
    public void setKeys(Map<String, String> keys) {
        this.keys = normalizeKeys(keys);
    }

    private static String normalizeHeaderName(String headerName, String defaultValue) {
        return headerName == null || headerName.isBlank() ? defaultValue : headerName.trim();
    }

    private static String normalizeParameterName(String parameterName, String defaultValue) {
        return parameterName == null || parameterName.isBlank() ? defaultValue : parameterName.trim();
    }

    private static Map<String, String> normalizeKeys(Map<String, String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalizedKeys = new LinkedHashMap<>();
        keys.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalizedKeys.put(key.trim(), value.trim());
            }
        });
        return normalizedKeys.isEmpty() ? Map.of() : Map.copyOf(normalizedKeys);
    }
}
