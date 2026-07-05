package io.github.coco.spring.boot.banner;

/**
 * Coco 启动 banner 渲染器。
 * <p>
 * 负责生成 Coco Spring 启动信息文本，展示框架标识、框架版本和 Spring Boot 版本。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoStartupBanner {

    private final CocoBannerProperties properties;

    /**
     * <p>
     * 创建 Coco 启动 banner 渲染器。
     * </p>
     * @param properties banner 配置属性
     */
    public CocoStartupBanner(CocoBannerProperties properties) {
        this.properties = properties == null ? new CocoBannerProperties() : properties;
    }

    /**
     * <p>
     * 渲染 Coco 启动 banner。
     * </p>
     * @param detectedVersion 自动探测到的版本号
     * @return banner 文本
     */
    public String render(String detectedVersion) {
        return render(detectedVersion, null);
    }

    /**
     * <p>
     * 渲染 Coco 启动 banner，并附带 Spring Boot 版本号。
     * </p>
     * @param detectedVersion 自动探测到的 Coco 版本号
     * @param springBootVersion Spring Boot 版本号
     * @return banner 文本
     */
    public String render(String detectedVersion, String springBootVersion) {
        String version = resolveVersion(detectedVersion);
        return System.lineSeparator()
                + top()
                + row("  ██████╗  ██████╗  ██████╗ ██████╗")
                + row(" ██╔════╝ ██╔═══██╗██╔════╝██╔═══██╗")
                + row(" ██║      ██║   ██║██║     ██║   ██║")
                + row(" ╚██████╗ ╚██████╔╝╚██████╗╚██████╔╝")
                + row("  ╚═════╝  ╚═════╝  ╚═════╝ ╚═════╝")
                + separator()
                + row(" " + this.properties.getTitle())
                + row(" Coco       : " + version)
                + row(" Spring Boot : " + resolveSpringBootVersion(springBootVersion))
                + bottom();
    }

    private String resolveVersion(String detectedVersion) {
        if (this.properties.getVersion() != null) {
            return this.properties.getVersion();
        }
        return detectedVersion == null || detectedVersion.isBlank() ? "unknown" : detectedVersion.trim();
    }

    private static String resolveSpringBootVersion(String springBootVersion) {
        return springBootVersion == null || springBootVersion.isBlank() ? "unknown" : springBootVersion.trim();
    }

    private static String top() {
        return "╭" + "─".repeat(52) + "╮" + System.lineSeparator();
    }

    private static String separator() {
        return "├" + "─".repeat(52) + "┤" + System.lineSeparator();
    }

    private static String bottom() {
        return "╰" + "─".repeat(52) + "╯";
    }

    private static String row(String text) {
        String normalizedText = text == null ? "" : text;
        if (normalizedText.length() >= 50) {
            return "│ " + normalizedText.substring(0, 50) + " │" + System.lineSeparator();
        }
        return "│ " + normalizedText + " ".repeat(50 - normalizedText.length()) + " │"
                + System.lineSeparator();
    }
}
