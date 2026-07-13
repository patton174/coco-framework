package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.coco.feature.web.context.payload.CocoPayloadParameterProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 请求参数配置属性。
 * <p>
 * 控制 Web 请求上下文中的参数采集、脱敏和裁剪策略，不控制访问日志是否最终输出这些参数。
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
public class CocoWebParameterProperties {

    private static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 256;

    private static final Set<String> DEFAULT_MASKED_PARAMETER_NAMES = Set.of(
            "password", "passwd", "pwd", "passcode", "pin",
            "secret", "client_secret", "api_secret", "private_key",
            "token", "access_token", "refresh_token", "id_token", "session_token", "csrf_token",
            "authorization", "authorization_code", "auth_code",
            "otp", "one_time_password", "verification_code", "api_key", "session_id");

    private boolean includeParameters = true;

    private int maxParameterValueLength = DEFAULT_MAX_PARAMETER_VALUE_LENGTH;

    private Set<String> maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;

    private CocoWebParameterValueCaptureMode valueCaptureMode = CocoWebParameterValueCaptureMode.METADATA_ONLY;

    private Set<String> valueAllowedParameterNames = Set.of();

    @NestedConfigurationProperty
    private CocoPayloadParameterProperties payload = new CocoPayloadParameterProperties();

    /**
     * <p>
     * 返回是否采集请求参数到 Web 请求上下文。
     * </p>
     * @return 采集请求参数时返回 {@code true}
     */
    public boolean isIncludeParameters() {
        return this.includeParameters;
    }

    /**
     * <p>
     * 设置是否采集请求参数到 Web 请求上下文。
     * </p>
     * @param includeParameters 是否采集请求参数
     */
    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    /**
     * <p>
     * 返回单个请求参数值最大采集长度。
     * </p>
     * @return 单个请求参数值最大采集长度
     */
    public int getMaxParameterValueLength() {
        return this.maxParameterValueLength;
    }

    /**
     * <p>
     * 设置单个请求参数值最大采集长度。
     * </p>
     * @param maxParameterValueLength 单个请求参数值最大采集长度
     */
    public void setMaxParameterValueLength(int maxParameterValueLength) {
        this.maxParameterValueLength = maxParameterValueLength <= 0
                ? DEFAULT_MAX_PARAMETER_VALUE_LENGTH
                : maxParameterValueLength;
    }

    /**
     * <p>
     * 返回需要掩码的请求参数名集合。
     * </p>
     * @return 需要掩码的请求参数名集合
     */
    public Set<String> getMaskedParameterNames() {
        return this.maskedParameterNames;
    }

    /**
     * <p>
     * 设置需要掩码的请求参数名集合。
     * </p>
     * @param maskedParameterNames 需要掩码的请求参数名集合
     */
    public void setMaskedParameterNames(Set<String> maskedParameterNames) {
        if (maskedParameterNames == null || maskedParameterNames.isEmpty()) {
            this.maskedParameterNames = DEFAULT_MASKED_PARAMETER_NAMES;
            return;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : maskedParameterNames) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.maskedParameterNames = normalizedNames.isEmpty()
                ? DEFAULT_MASKED_PARAMETER_NAMES
                : Set.copyOf(normalizedNames);
    }

    /**
     * <p>
     * 返回普通请求上下文中的参数值采集模式。
     * </p>
     * @return 参数值采集模式
     */
    public CocoWebParameterValueCaptureMode getValueCaptureMode() {
        return this.valueCaptureMode;
    }

    /**
     * <p>
     * 设置普通请求上下文中的参数值采集模式。
     * </p>
     * @param valueCaptureMode 参数值采集模式；为空时恢复安全的仅元数据模式
     */
    public void setValueCaptureMode(CocoWebParameterValueCaptureMode valueCaptureMode) {
        this.valueCaptureMode = valueCaptureMode == null
                ? CocoWebParameterValueCaptureMode.METADATA_ONLY
                : valueCaptureMode;
    }

    /**
     * <p>
     * 返回允许采集值的参数名集合。
     * </p>
     * <p>
     * 仅当参数值采集模式为 {@link CocoWebParameterValueCaptureMode#ALLOW_LIST} 时生效，名称按大小写不敏感的完整参数名匹配。
     * </p>
     * @return 允许采集值的参数名集合
     */
    public Set<String> getValueAllowedParameterNames() {
        return this.valueAllowedParameterNames;
    }

    /**
     * <p>
     * 设置允许采集值的参数名集合。
     * </p>
     * @param valueAllowedParameterNames 允许采集值的参数名集合
     */
    public void setValueAllowedParameterNames(Set<String> valueAllowedParameterNames) {
        this.valueAllowedParameterNames = normalizeNames(valueAllowedParameterNames);
    }

    /**
     * <p>
     * 返回指定参数值是否可以进入普通请求上下文。
     * </p>
     * <p>
     * 敏感名称匹配始终优先于采集模式。敏感名称识别忽略大小写和常见分隔符，并识别 camelCase 及嵌套参数名中的完整名称片段。
     * </p>
     * @param parameterName 参数名
     * @return 允许采集参数值时返回 {@code true}
     */
    public boolean shouldCaptureParameterValue(String parameterName) {
        if (matchesMaskedParameterName(parameterName)) {
            return false;
        }
        return switch (this.valueCaptureMode) {
            case ALL -> true;
            case ALLOW_LIST -> this.valueAllowedParameterNames.contains(normalizeName(parameterName));
            case METADATA_ONLY -> false;
        };
    }

    /**
     * <p>
     * 返回请求体参数解析配置。
     * </p>
     * @return 请求体参数解析配置
     */
    public CocoPayloadParameterProperties getPayload() {
        return this.payload;
    }

    /**
     * <p>
     * 设置请求体参数解析配置。
     * </p>
     * @param payload 请求体参数解析配置
     */
    public void setPayload(CocoPayloadParameterProperties payload) {
        this.payload = payload == null ? new CocoPayloadParameterProperties() : payload;
    }

    private boolean matchesMaskedParameterName(String parameterName) {
        List<String> candidateParts = splitNameParts(parameterName);
        if (candidateParts.isEmpty()) {
            return false;
        }
        Set<String> canonicalMaskedNames = new LinkedHashSet<>();
        for (String maskedName : this.maskedParameterNames) {
            String canonicalName = canonicalName(maskedName);
            if (!canonicalName.isEmpty()) {
                canonicalMaskedNames.add(canonicalName);
            }
        }
        for (int start = 0; start < candidateParts.size(); start++) {
            StringBuilder candidate = new StringBuilder();
            for (int end = start; end < candidateParts.size(); end++) {
                candidate.append(candidateParts.get(end));
                if (canonicalMaskedNames.contains(candidate.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> normalizeNames(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : names) {
            String normalizedName = normalizeName(name);
            if (!normalizedName.isEmpty()) {
                normalizedNames.add(normalizedName);
            }
        }
        return normalizedNames.isEmpty() ? Set.of() : Set.copyOf(normalizedNames);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String canonicalName(String name) {
        return String.join("", splitNameParts(name));
    }

    private static List<String> splitNameParts(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!Character.isLetterOrDigit(character)) {
                addPart(parts, part);
                continue;
            }
            if (startsCamelCasePart(name, index, part)) {
                addPart(parts, part);
            }
            part.append(Character.toLowerCase(character));
        }
        addPart(parts, part);
        return List.copyOf(parts);
    }

    private static boolean startsCamelCasePart(String value, int index, StringBuilder currentPart) {
        if (currentPart.isEmpty() || !Character.isUpperCase(value.charAt(index))) {
            return false;
        }
        char previous = value.charAt(index - 1);
        boolean nextIsLowerCase = index + 1 < value.length() && Character.isLowerCase(value.charAt(index + 1));
        return Character.isLowerCase(previous) || Character.isDigit(previous)
                || (Character.isUpperCase(previous) && nextIsLowerCase);
    }

    private static void addPart(List<String> parts, StringBuilder part) {
        if (!part.isEmpty()) {
            parts.add(part.toString());
            part.setLength(0);
        }
    }
}
