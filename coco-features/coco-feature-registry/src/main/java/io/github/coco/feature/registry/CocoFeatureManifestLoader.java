package io.github.coco.feature.registry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
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
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CocoFeatureManifestLoader() {
    }

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

    public static CocoFeatureManifest read(InputStream inputStream) {
        try {
            return OBJECT_MAPPER.readValue(inputStream, CocoFeatureManifest.class);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse Coco feature manifest", ex);
        }
    }

    public static String write(CocoFeatureManifest manifest) {
        try {
            return OBJECT_MAPPER.writeValueAsString(manifest);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write Coco feature manifest", ex);
        }
    }
}
