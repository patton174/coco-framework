package io.github.coco.maven;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.feature.registry.CocoFeatureSelection;

/**
 * Coco 注解功能扫描器。
 * <p>
 * 扫描业务项目编译后的 class 文件，读取标注在配置类上的 {@code @CocoFeatures} 声明。
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
public final class CocoAnnotatedFeatureScanner {

    /**
     * <p>
     * 扫描编译输出目录中的 {@link CocoFeatures} 注解声明。
     * </p>
     * <p>
     * 该方法只读取顶层 class，忽略内部类，并使用构建期 classpath 加载应用配置类。
     * </p>
     * @param classesDirectory 编译输出目录
     * @param classpathUrls 构建期 classpath URL 集合
     * @return 注解声明中的功能选择
     */
    public CocoFeatureSelection scan(Path classesDirectory, Collection<URL> classpathUrls) {
        if (classesDirectory == null || !Files.isDirectory(classesDirectory)) {
            return CocoFeatureSelection.empty();
        }
        URL[] urls = classpathUrls == null ? new URL[0] : classpathUrls.toArray(URL[]::new);
        try (URLClassLoader classLoader = new URLClassLoader(urls, CocoFeatures.class.getClassLoader())) {
            EnumSet<CocoFeature> enabled = EnumSet.noneOf(CocoFeature.class);
            EnumSet<CocoFeature> disabled = EnumSet.noneOf(CocoFeature.class);
            try (var paths = Files.walk(classesDirectory)) {
                paths.filter(path -> path.toString().endsWith(".class"))
                        .map(classesDirectory::relativize)
                        .map(this::toClassName)
                        .filter(className -> !className.contains("$"))
                        .forEach(className -> collect(classLoader, className, enabled, disabled));
            }
            return new CocoFeatureSelection(enabled, disabled);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to scan Coco feature annotations.", ex);
        }
    }

    /**
     * <p>
     * 从单个类中收集 {@link CocoFeatures} 声明。
     * </p>
     * @param classLoader 构建期类加载器
     * @param className 待扫描类名
     * @param enabled 启用功能收集目标
     * @param disabled 禁用功能收集目标
     */
    private void collect(ClassLoader classLoader, String className, Set<CocoFeature> enabled, Set<CocoFeature> disabled) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            CocoFeatures features = type.getAnnotation(CocoFeatures.class);
            if (features == null) {
                return;
            }
            addAll(enabled, features.enabled());
            addAll(disabled, features.disabled());
        }
        catch (LinkageError | ClassNotFoundException ex) {
            // Build-time scanning must not fail because an unrelated application class cannot be loaded.
        }
    }

    /**
     * <p>
     * 将 class 文件相对路径转换为 Java 类名。
     * </p>
     * @param path class 文件相对路径
     * @return Java 类名
     */
    private String toClassName(Path path) {
        String fileName = path.toString();
        return fileName.substring(0, fileName.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }

    private static void addAll(Set<CocoFeature> target, CocoFeature[] features) {
        if (features == null) {
            return;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                target.add(feature);
            }
        }
    }
}
