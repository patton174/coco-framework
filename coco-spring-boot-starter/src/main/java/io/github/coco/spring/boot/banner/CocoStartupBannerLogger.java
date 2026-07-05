package io.github.coco.spring.boot.banner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Coco 启动 banner 日志监听器。
 * <p>
 * 在 Spring Boot 应用启动后输出 Coco 框架信息；该监听器由 Starter 自动配置注册，业务项目可通过配置关闭。
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
public final class CocoStartupBannerLogger implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoStartupBannerLogger.class);

    private final CocoStartupBanner banner;

    /**
     * <p>
     * 创建 Coco 启动 banner 日志监听器。
     * </p>
     * @param banner 启动 banner 渲染器
     */
    public CocoStartupBannerLogger(CocoStartupBanner banner) {
        this.banner = banner == null ? new CocoStartupBanner(new CocoBannerProperties()) : banner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        LOGGER.info(this.banner.render(resolveVersion()));
    }

    private static String resolveVersion() {
        Package sourcePackage = CocoStartupBannerLogger.class.getPackage();
        String implementationVersion = sourcePackage == null ? null : sourcePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "1.0.0-SNAPSHOT"
                : implementationVersion;
    }
}
