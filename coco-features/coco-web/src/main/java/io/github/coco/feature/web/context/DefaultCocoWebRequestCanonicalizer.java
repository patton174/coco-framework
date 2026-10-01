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

import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;

/**
 * Coco Web 默认请求规范化器�? * <p>
 * 按固定顺序输�?method、path、query、canonical headers、parameters �?body 摘要，保证同一请求输入生成稳定文本�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoWebRequestCanonicalizer implements CocoWebRequestCanonicalizer {

    private final CocoWebRequestCanonicalizationProperties properties;

    /**
     * <p>
     * 创建默认请求规范化器�?     * </p>
     */
    public DefaultCocoWebRequestCanonicalizer() {
        this(null);
    }

    /**
     * <p>
     * 创建默认请求规范化器�?     * </p>
     * @param properties 请求规范化配置属�?     */
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
        appendCookies(builder, input.canonicalCookies(), signature);
        appendParameters(builder, input);
        appendLine(builder, "bodySha256", input.bodySha256(), signature || this.properties.isIncludeBodySha256());
        appendLine(builder, "bodyLength", input.bodyLength(), signature || this.properties.isIncludeBodyLength());
        appendBrowserFingerprint(builder, context.browserFingerprint());
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

    private void appendCookies(StringBuilder builder, Map<String, String> cookies, boolean forced) {
        if (cookies == null || cookies.isEmpty() || (!forced && !this.properties.isIncludeCookies())) {
            return;
        }
        builder.append("cookies").append('\n');
        new TreeMap<>(cookies).forEach((name, cookieValue) -> builder.append(value(name))
                .append('=')
                .append(framedValue(cookieValue))
                .append('\n'));
    }

    private void appendBrowserFingerprint(StringBuilder builder, CocoBrowserFingerprint browserFingerprint) {
        if (browserFingerprint == null) {
            return;
        }
        appendLine(builder, "browserFingerprint", browserFingerprint.value(),
                this.properties.isIncludeBrowserFingerprint());
        if (!this.properties.isIncludeBrowserFingerprintSignals() || browserFingerprint.signals().isEmpty()) {
            return;
        }
        builder.append("browserFingerprintSignals").append('\n');
        new TreeMap<>(browserFingerprint.signals()).forEach((name, signalValue) -> builder.append(value(name))
                .append('=')
                .append(framedValue(signalValue))
                .append('\n'));
    }

    private void appendParameters(StringBuilder builder, CocoWebRequestSecurityInput input) {
        if (!this.properties.isIncludeParameters()) {
            return;
        }
        if (usesParameterSources(input)) {
            appendParameterSection(builder, "queryParameters", input.queryParameters());
            appendParameterSection(builder, "payloadParameters", input.payloadParameters());
            return;
        }
        appendParameterSection(builder, "parameters", input.parameters());
    }

    private boolean usesParameterSources(CocoWebRequestSecurityInput input) {
        return usesFramedParameterValues() && this.properties.isIncludeParameterSources()
                && (!input.queryParameters().isEmpty() || !input.payloadParameters().isEmpty());
    }

    private void appendParameterSection(StringBuilder builder, String sectionName,
            Map<String, List<String>> parameters) {
        builder.append(sectionName).append('\n');
        if (usesFramedParameterValues()) {
            new TreeMap<>(parameters).forEach((name, values) -> appendFramedParameterValues(builder, name, values));
            return;
        }
        new TreeMap<>(parameters).forEach((name, values) -> appendDelimitedParameterValues(builder, name, values));
    }

    private void appendDelimitedParameterValues(StringBuilder builder, String name, List<String> values) {
        builder.append(value(name)).append('=')
                .append(String.join(this.properties.getParameterValueSeparator(), parameterValues(values)))
                .append('\n');
    }

    private void appendFramedParameterValues(StringBuilder builder, String name, List<String> values) {
        List<String> safeValues = orderedParameterValues(values);
        String parameterName = value(name);
        builder.append(parameterName).append('#').append(safeValues.size()).append('\n');
        for (int index = 0; index < safeValues.size(); index++) {
            builder.append(parameterName)
                    .append('[')
                    .append(index)
                    .append("]=")
                    .append(framedValue(safeValues.get(index)))
                    .append('\n');
        }
    }

    private List<String> parameterValues(List<String> values) {
        return orderedParameterValues(values).stream()
                .map(DefaultCocoWebRequestCanonicalizer::value)
                .toList();
    }

    private List<String> orderedParameterValues(List<String> values) {
        List<String> safeValues = values == null || values.isEmpty()
                ? List.of("")
                : values.stream().map(current -> current == null ? "" : current).toList();
        Stream<String> stream = safeValues.stream();
        if (this.properties.isSortParameterValues()) {
            stream = stream.sorted();
        }
        return stream.toList();
    }

    private boolean usesFramedParameterValues() {
        return true;
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
