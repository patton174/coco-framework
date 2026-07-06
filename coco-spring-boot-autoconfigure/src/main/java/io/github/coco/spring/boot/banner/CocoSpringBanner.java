package io.github.coco.spring.boot.banner;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;

/**
 * Coco Spring 启动 banner。
 * <p>
 * 在 Spring Boot 打印原始 banner 的阶段输出 Coco Spring 标识，避免应用启动后再次追加独立 banner 日志。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSpringBanner implements Banner {

    private static final String DEFAULT_VERSION = "1.0.0-SNAPSHOT";

    /**
     * {@inheritDoc}
     */
    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        if (!environment.getProperty("coco.banner.enabled", Boolean.class, true)) {
            return;
        }
        CocoBannerProperties properties = new CocoBannerProperties();
        properties.setTitle(environment.getProperty("coco.banner.title"));
        properties.setVersion(environment.getProperty("coco.banner.version"));

        CocoStartupBanner banner = new CocoStartupBanner(properties);
        printUtf8(out, banner.render(resolveVersion(environment), SpringBootVersion.getVersion()));
    }

    /**
     * <p>
     * 使用 UTF-8 字节写出 banner，避免 Windows 控制台默认编码导致 Unicode 标识乱码。
     * </p>
     * @param out Spring Boot 提供的 banner 输出流
     * @param text banner 文本
     */
    private static void printUtf8(PrintStream out, String text) {
        byte[] content = (text + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        out.write(content, 0, content.length);
        out.flush();
    }

    private static String resolveVersion(Environment environment) {
        String configuredVersion = environment.getProperty("coco.banner.version");
        if (configuredVersion != null && !configuredVersion.isBlank()) {
            return configuredVersion;
        }
        String applicationVersion = environment.getProperty("application.version");
        if (applicationVersion != null && !applicationVersion.isBlank()) {
            return applicationVersion;
        }
        Package sourcePackage = CocoSpringBanner.class.getPackage();
        String implementationVersion = sourcePackage == null ? null : sourcePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? DEFAULT_VERSION
                : implementationVersion;
    }
}
