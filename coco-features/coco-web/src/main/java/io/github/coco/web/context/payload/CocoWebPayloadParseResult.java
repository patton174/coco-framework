package io.github.coco.web.context.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.coco.web.context.CocoWebParameterSource;

/**
 * Coco Web 请求体参数解析结果。
 * <p>
 * 统一描述请求体参数解析后的参数视图、解析状态与参数来源，避免上层只能从空参数集合推断
 * 是解析成功但无参数，还是被密文传输态、缓存缺失、格式错误或限制截断所阻断。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param parameters 请求体参数快照
 * @param status 请求体参数解析状态
 * @param source 请求体参数来源
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebPayloadParseResult(Map<String, List<String>> parameters, CocoWebPayloadParseStatus status,
        CocoWebParameterSource source) {

    /**
     * <p>
     * 创建请求体参数解析结果，并归一化集合字段与来源字段。
     * </p>
     * @param parameters 请求体参数快照
     * @param status 请求体参数解析状态
     * @param source 请求体参数来源
     */
    public CocoWebPayloadParseResult {
        parameters = copyParameters(parameters);
        status = status == null ? CocoWebPayloadParseStatus.DISABLED : status;
        source = normalizeSource(source, parameters, status);
    }

    /**
     * <p>
     * 创建空请求体参数解析结果。
     * </p>
     * @param status 请求体参数解析状态
     * @param source 请求体参数来源
     * @return 空请求体参数解析结果
     */
    public static CocoWebPayloadParseResult empty(CocoWebPayloadParseStatus status, CocoWebParameterSource source) {
        return new CocoWebPayloadParseResult(Map.of(), status, source);
    }

    /**
     * <p>
     * 判断当前解析结果是否处于可用状态。
     * </p>
     * @return 当前解析结果可被业务或安全能力消费时返回 {@code true}
     */
    public boolean available() {
        return this.status.available();
    }

    /**
     * <p>
     * 判断当前解析结果是否发生截断。
     * </p>
     * @return 解析结果被限制条件截断时返回 {@code true}
     */
    public boolean truncated() {
        return this.status.truncated();
    }

    private static CocoWebParameterSource normalizeSource(CocoWebParameterSource source,
            Map<String, List<String>> parameters, CocoWebPayloadParseStatus status) {
        if (source != null && source.payload()) {
            return source;
        }
        if (parameters != null && !parameters.isEmpty()) {
            return CocoWebParameterSource.PAYLOAD;
        }
        if (status != null && status.available()) {
            return CocoWebParameterSource.PAYLOAD;
        }
        return CocoWebParameterSource.NONE;
    }

    private static Map<String, List<String>> copyParameters(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            if (name == null || name.isBlank()) {
                return;
            }
            copied.put(name.trim(), copyValues(values));
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static List<String> copyValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        List<String> copied = new ArrayList<>(values.size());
        for (String value : values) {
            copied.add(value == null ? "" : value);
        }
        return List.copyOf(copied);
    }
}
