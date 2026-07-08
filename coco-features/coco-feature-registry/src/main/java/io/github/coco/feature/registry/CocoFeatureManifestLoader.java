package io.github.coco.feature.registry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Coco 功能清单读写器。
 * <p>
 * 统一处理 {@code META-INF/coco/features.json} 的序列化和反序列化，避免构建期与运行期各自解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-registry}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoFeatureManifestLoader {

    public static final String MANIFEST_LOCATION = "META-INF/coco/features.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CocoFeatureManifestLoader() {
    }

    /**
     * <p>
     * 从指定类加载器读取构建期生成的 Coco 功能清单。
     * </p>
     * <p>
     * 当业务应用产物中不存在 {@value #MANIFEST_LOCATION} 时返回空结果，由运行期配置继续兜底解析。
     * </p>
     * @param classLoader 用于查找清单资源的类加载器
     * @return 读取到的功能清单；不存在时返回空结果
     * @throws UncheckedIOException 清单存在但读取失败时抛出
     */
    public static Optional<CocoFeatureManifest> load(ClassLoader classLoader) {
        ClassLoader targetClassLoader = classLoader == null
                ? Thread.currentThread().getContextClassLoader()
                : classLoader;
        if (targetClassLoader == null) {
            targetClassLoader = CocoFeatureManifestLoader.class.getClassLoader();
        }
        try (InputStream inputStream = targetClassLoader.getResourceAsStream(MANIFEST_LOCATION)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            return Optional.of(read(inputStream));
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to read Coco feature manifest", ex);
        }
    }

    /**
     * <p>
     * 从输入流反序列化 Coco 功能清单。
     * </p>
     * @param inputStream 功能清单输入流
     * @return 功能清单
     * @throws UncheckedIOException 清单解析失败时抛出
     */
    public static CocoFeatureManifest read(InputStream inputStream) {
        try {
            return OBJECT_MAPPER.readValue(inputStream, CocoFeatureManifest.class);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse Coco feature manifest", ex);
        }
    }

    /**
     * <p>
     * 将 Coco 功能清单序列化为格式化 JSON 文本。
     * </p>
     * @param manifest 功能清单
     * @return JSON 文本
     * @throws IllegalStateException 清单序列化失败时抛出
     */
    public static String write(CocoFeatureManifest manifest) {
        try {
            return OBJECT_MAPPER.writeValueAsString(manifest);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write Coco feature manifest", ex);
        }
    }
}
