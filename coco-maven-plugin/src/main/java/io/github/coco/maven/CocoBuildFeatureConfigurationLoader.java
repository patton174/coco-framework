package io.github.coco.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeatureSelection;
import org.yaml.snakeyaml.Yaml;

/**
 * Coco 构建期功能配置加载器。
 * <p>
 * 从业务项目资源目录读取 {@code application.yml}、{@code application.yaml} 和 {@code application.properties}
 * 中的功能启用声明。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-maven-plugin}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoBuildFeatureConfigurationLoader {

    /**
     * <p>
     * 从业务项目资源目录加载构建期功能选择声明。
     * </p>
     * <p>
     * 加载顺序为 {@code application.yml}、{@code application.yaml}、{@code application.properties}，后加载的配置优先级更高。
     * </p>
     * @param resourcesDirectory 业务项目资源目录
     * @return 构建期功能选择声明
     */
    public CocoFeatureSelection load(Path resourcesDirectory) {
        if (resourcesDirectory == null || !Files.isDirectory(resourcesDirectory)) {
            return CocoFeatureSelection.empty();
        }
        CocoFeatureSelection selection = CocoFeatureSelection.empty();
        selection = selection.merge(loadYaml(resourcesDirectory.resolve("application.yml")));
        selection = selection.merge(loadYaml(resourcesDirectory.resolve("application.yaml")));
        selection = selection.merge(loadProperties(resourcesDirectory.resolve("application.properties")));
        return selection;
    }

    /**
     * <p>
     * 从 YAML 配置文件加载功能选择声明。
     * </p>
     * @param path YAML 配置文件路径
     * @return 功能选择声明
     */
    CocoFeatureSelection loadYaml(Path path) {
        if (!Files.isRegularFile(path)) {
            return CocoFeatureSelection.empty();
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object value = new Yaml().load(inputStream);
            if (!(value instanceof Map<?, ?> root)) {
                return CocoFeatureSelection.empty();
            }
            Object features = nested(root, "coco", "features");
            if (!(features instanceof Map<?, ?> featureMap)) {
                return CocoFeatureSelection.empty();
            }
            return new CocoFeatureSelection(
                    parseFeatureValue(featureMap.get("enabled")),
                    parseFeatureValue(featureMap.get("disabled")));
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to read Coco feature YAML: " + path, ex);
        }
    }

    /**
     * <p>
     * 从 properties 配置文件加载功能选择声明。
     * </p>
     * @param path properties 配置文件路径
     * @return 功能选择声明
     */
    CocoFeatureSelection loadProperties(Path path) {
        if (!Files.isRegularFile(path)) {
            return CocoFeatureSelection.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to read Coco feature properties: " + path, ex);
        }
        return new CocoFeatureSelection(
                parseProperties(properties, "coco.features.enabled"),
                parseProperties(properties, "coco.features.disabled"));
    }

    private static Object nested(Map<?, ?> root, String first, String second) {
        Object firstValue = root.get(first);
        if (!(firstValue instanceof Map<?, ?> firstMap)) {
            return null;
        }
        return firstMap.get(second);
    }

    private static Set<CocoFeature> parseProperties(Properties properties, String key) {
        LinkedHashSet<CocoFeature> features = new LinkedHashSet<>();
        features.addAll(parseFeatureValue(properties.getProperty(key)));
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(key + "[")) {
                features.addAll(parseFeatureValue(properties.getProperty(propertyName)));
            }
        }
        return Set.copyOf(features);
    }

    private static Set<CocoFeature> parseFeatureValue(Object value) {
        LinkedHashSet<CocoFeature> features = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addFeature(features, item);
            }
            return Set.copyOf(features);
        }
        addFeature(features, value);
        return Set.copyOf(features);
    }

    private static void addFeature(Set<CocoFeature> target, Object value) {
        if (value == null) {
            return;
        }
        for (String token : value.toString().split(",")) {
            CocoFeature.fromId(token.trim()).ifPresent(target::add);
        }
    }

}
