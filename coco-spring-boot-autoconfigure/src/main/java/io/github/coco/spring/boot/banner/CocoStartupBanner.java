package io.github.coco.spring.boot.banner;

/**
 * Coco 启动 banner 渲染器。
 * <p>
 * 负责生成简洁、无边框、低噪音的 Coco Spring 启动标识，避免在进程启动阶段输出厚重阻塞的视觉块。
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
        return String.join(System.lineSeparator(),
                "   _|_|_|                _|_|_|                    _|_|_|                         _|                        ",
                " _|           _|_|     _|           _|_|         _|         _|_|_|     _|  _|_|        _|_|_|       _|_|_|  ",
                " _|         _|    _|   _|         _|    _|         _|_|     _|    _|   _|_|       _|   _|    _|   _|    _|  ",
                " _|         _|    _|   _|         _|    _|             _|   _|    _|   _|         _|   _|    _|   _|    _|  ",
                "   _|_|_|     _|_|       _|_|_|     _|_|         _|_|_|     _|_|_|     _|         _|   _|    _|     _|_|_|  ",
                "                                                            _|                                          _|  ",
                "                                                            _|                                      _|_|    ",
                "",
                "     " + this.properties.getTitle(),
                "     fast web framework",
                "     version     " + resolveVersion(detectedVersion),
                "     spring boot " + resolveSpringBootVersion(springBootVersion));
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
