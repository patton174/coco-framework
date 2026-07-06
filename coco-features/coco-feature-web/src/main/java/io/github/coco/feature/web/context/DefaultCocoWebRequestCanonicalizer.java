package io.github.coco.feature.web.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestCanonicalForm canonicalize(CocoWebRequestSecurityInput input) {
        CocoWebRequestSecurityInput checkedInput = Objects.requireNonNull(input, "input must not be null");
        String text = canonicalText(checkedInput);
        return new CocoWebRequestCanonicalForm(text, sha256(text));
    }

    private static String canonicalText(CocoWebRequestSecurityInput input) {
        StringBuilder builder = new StringBuilder();
        builder.append("method=").append(value(input.method())).append('\n');
        builder.append("path=").append(value(input.path())).append('\n');
        builder.append("query=").append(value(input.queryString())).append('\n');
        appendHeaders(builder, input.canonicalHeaders());
        appendParameters(builder, input.parameters());
        builder.append("bodySha256=").append(value(input.bodySha256())).append('\n');
        return builder.toString();
    }

    private static void appendHeaders(StringBuilder builder, Map<String, String> headers) {
        builder.append("headers").append('\n');
        new TreeMap<>(headers).forEach((name, value) ->
                builder.append(name).append(':').append(value(value)).append('\n'));
    }

    private static void appendParameters(StringBuilder builder, Map<String, List<String>> parameters) {
        builder.append("parameters").append('\n');
        new TreeMap<>(parameters).forEach((name, values) ->
                builder.append(name).append('=').append(String.join(",", values.stream().sorted().toList()))
                        .append('\n'));
    }

    private static String value(String value) {
        return value == null ? "" : value;
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
