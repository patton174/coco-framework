package io.github.coco.spring.boot.banner;

/**
 * Coco 启动 banner 渲染器。
 * <p>
 * 负责生成 Coco 框架启动信息文本，展示版本号、作者和项目仓库地址。
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
                + "  _________                         ______            _" + System.lineSeparator()
                + " /  ______/___  _________     _____/  ___/_________(_)___  ____ _" + System.lineSeparator()
                + "/  /     / __ \\/  ___/  _ \\   / ___/\\__ \\/ ___/ ___/ / __ \\/ __ `/" + System.lineSeparator()
                + "\\  \\____/ /_/ / /___/  __/  (__  )___/ / /  / /  / / / / / /_/ /" + System.lineSeparator()
                + " \\_____/\\____/\\___/\\___/  /____//____/_/  /_/  /_/_/ /_/\\__, /" + System.lineSeparator()
                + "                                                       /____/" + System.lineSeparator()
                + System.lineSeparator()
                + " :: " + this.properties.getTitle() + " ::" + System.lineSeparator()
                + " :: Version     : " + version + System.lineSeparator()
                + " :: Spring Boot : " + resolveSpringBootVersion(springBootVersion) + System.lineSeparator()
                + " :: Author      : " + this.properties.getAuthor() + System.lineSeparator()
                + " :: Repository  : " + this.properties.getRepository();
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
}
