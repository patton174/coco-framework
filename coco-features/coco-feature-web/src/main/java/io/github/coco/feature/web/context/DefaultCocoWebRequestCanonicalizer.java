package io.github.coco.feature.web.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Coco Web 默认请求规范化器。
 * <p>
 * 按固定顺序输出 method、path、query、canonical headers、parameters 和 body 摘要，保证同一请求输入生成稳定文本。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoWebRequestCanonicalizer implements CocoWebRequestCanonicalizer {

    private final CocoWebRequestCanonicalizationProperties properties;

    /**
     * <p>
     * 创建默认请求规范化器。
     * </p>
     */
    public DefaultCocoWebRequestCanonicalizer() {
        this(null);
    }

    /**
     * <p>
     * 创建默认请求规范化器。
     * </p>
     * @param properties 请求规范化配置属性
     */
    public DefaultCocoWebRequestCanonicalizer(CocoWebRequestCanonicalizationProperties properties) {
        this.properties = properties == null ? new CocoWebRequestCanonicalizationProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestCanonicalForm canonicalize(CocoWebRequestCanonicalizationContext context) {
        CocoWebRequestCanonicalizationContext checkedContext = context == null
                ? CocoWebRequestCanonicalizationContext.of(CocoWebRequestSecurityInput.empty())
                : context;
        String text = canonicalText(checkedContext);
        return new CocoWebRequestCanonicalForm(text, sha256(text));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestCanonicalForm canonicalize(CocoWebRequestSecurityInput input) {
        CocoWebRequestSecurityInput checkedInput = Objects.requireNonNull(input, "input must not be null");
        return canonicalize(CocoWebRequestCanonicalizationContext.of(checkedInput));
    }

    private String canonicalText(CocoWebRequestCanonicalizationContext context) {
        CocoWebRequestSecurityInput input = Objects.requireNonNull(context.securityInput(),
                "securityInput must not be null");
        StringBuilder builder = new StringBuilder();
        boolean signature = context.purpose() == CocoWebRequestCanonicalizationPurpose.SIGNATURE;
        appendLine(builder, "version", this.properties.getVersion(), signature || this.properties.isIncludeVersion());
        appendLine(builder, "purpose", context.purpose().name(), signature || this.properties.isIncludePurpose());
        appendLine(builder, "method", input.method(), signature || this.properties.isIncludeMethod());
        appendLine(builder, "path", input.path(), signature || this.properties.isIncludePath());
        appendLine(builder, "query", input.queryString(), signature || this.properties.isIncludeQueryString());
        appendHeaders(builder, input.canonicalHeaderValues(), signature);
        appendParameters(builder, input.parameters());
        appendLine(builder, "bodySha256", input.bodySha256(), signature || this.properties.isIncludeBodySha256());
        appendLine(builder, "bodyLength", input.bodyLength(), signature || this.properties.isIncludeBodyLength());
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String name, String value, boolean included) {
        if (included) {
            builder.append(name).append('=').append(value(value)).append('\n');
        }
    }

    private static void appendLine(StringBuilder builder, String name, Long value, boolean included) {
        if (included) {
            builder.append(name).append('=').append(value(value)).append('\n');
        }
    }

    private void appendHeaders(StringBuilder builder, Map<String, List<String>> headers, boolean forced) {
        if (!forced && !this.properties.isIncludeHeaders()) {
            return;
        }
        builder.append("headers").append('\n');
        new TreeMap<>(headers).forEach((name, values) -> appendHeaderValues(builder, name, values));
    }

    private static void appendHeaderValues(StringBuilder builder, String name, List<String> values) {
        List<String> safeValues = values == null || values.isEmpty() ? List.of("") : values;
        String headerName = value(name);
        builder.append(headerName).append('#').append(safeValues.size()).append('\n');
        for (int index = 0; index < safeValues.size(); index++) {
            builder.append(headerName)
                    .append('[')
                    .append(index)
                    .append("]=")
                    .append(framedValue(safeValues.get(index)))
                    .append('\n');
        }
    }

    private void appendParameters(StringBuilder builder, Map<String, List<String>> parameters) {
        if (!this.properties.isIncludeParameters()) {
            return;
        }
        builder.append("parameters").append('\n');
        new TreeMap<>(parameters).forEach((name, values) ->
                builder.append(value(name)).append('=')
                        .append(String.join(this.properties.getParameterValueSeparator(), parameterValues(values)))
                        .append('\n'));
    }

    private List<String> parameterValues(List<String> values) {
        Stream<String> stream = values.stream();
        if (this.properties.isSortParameterValues()) {
            stream = stream.sorted();
        }
        return stream.map(DefaultCocoWebRequestCanonicalizer::value).toList();
    }

    private static String value(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case ':' -> builder.append("\\:");
                case '=' -> builder.append("\\=");
                case ',' -> builder.append("\\,");
                case ';' -> builder.append("\\;");
                case '|' -> builder.append("\\|");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }

    private static String value(Long value) {
        return value == null ? "" : value.toString();
    }

    private static String framedValue(String value) {
        String escapedValue = value(value);
        return escapedValue.length() + ":" + escapedValue;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
