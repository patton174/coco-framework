package io.github.coco.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Coco 测试支持工具。
 * <p>
 * 提供仓库内部测试可复用的轻量工具，避免各模块重复实现配置元数据资源读取和节点查找逻辑。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-test}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CONFIGURATION_METADATA_RESOURCE = "/META-INF/spring-configuration-metadata.json";

    private CocoTestSupport() {
    }

    /**
     * <p>
     * 读取当前测试模块生成的 Spring Boot 配置元数据。
     * </p>
     * @param anchor 用于定位 classpath 的测试类
     * @return 配置元数据 JSON 根节点
     * @throws IOException 读取或解析元数据失败时抛出
     */
    public static JsonNode configurationMetadata(Class<?> anchor) throws IOException {
        return jsonResource(anchor, CONFIGURATION_METADATA_RESOURCE);
    }

    /**
     * <p>
     * 从 classpath 读取 JSON 资源。
     * </p>
     * @param anchor 用于定位 classpath 的测试类
     * @param resourcePath 资源路径
     * @return JSON 根节点
     * @throws IOException 读取或解析资源失败时抛出
     */
    public static JsonNode jsonResource(Class<?> anchor, String resourcePath) throws IOException {
        Class<?> checkedAnchor = Objects.requireNonNull(anchor, "anchor must not be null");
        String checkedResourcePath = requireText(resourcePath, "resourcePath");
        try (InputStream resource = checkedAnchor.getResourceAsStream(checkedResourcePath)) {
            if (resource == null) {
                throw new IllegalArgumentException("classpath resource does not exist: " + checkedResourcePath);
            }
            return OBJECT_MAPPER.readTree(resource);
        }
    }

    /**
     * <p>
     * 在 JSON 数组节点中查找指定 {@code name} 字段的子节点。
     * </p>
     * @param nodes JSON 数组节点
     * @param name 目标名称
     * @return 匹配节点；未找到时返回 {@code null}
     */
    public static JsonNode findNamedNode(JsonNode nodes, String name) {
        if (nodes == null || !nodes.isArray()) {
            return null;
        }
        String checkedName = requireText(name, "name");
        for (JsonNode node : nodes) {
            if (checkedName.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }

    /**
     * <p>
     * 在 JSON 数组节点中查找指定 {@code name} 字段的子节点，未找到时抛出异常。
     * </p>
     * @param nodes JSON 数组节点
     * @param name 目标名称
     * @return 匹配节点
     */
    public static JsonNode requiredNamedNode(JsonNode nodes, String name) {
        JsonNode node = findNamedNode(nodes, name);
        if (node == null) {
            throw new IllegalArgumentException("missing named node: " + name);
        }
        return node;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
