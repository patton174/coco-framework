package io.github.coco.feature.web;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.CocoCommonProperties;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Coco Web 功能自动配置入口。
 * <p>
 * 入口类只负责功能开关、配置属性绑定和子域自动配置导入；具体 Web 基础设施由各子配置类分别注册。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoCommonAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@EnableConfigurationProperties({ CocoWebProperties.class, CocoCommonProperties.class })
@Import({
        CocoWebI18nAutoConfiguration.class,
        CocoWebResponseAutoConfiguration.class,
        CocoWebExceptionAutoConfiguration.class,
        CocoWebRequestBodyAutoConfiguration.class,
        CocoWebContextAutoConfiguration.class,
        CocoWebTraceAutoConfiguration.class,
        CocoWebSignatureAutoConfiguration.class,
        CocoWebEncryptionAutoConfiguration.class,
        CocoWebReplayAutoConfiguration.class
})
public class CocoWebAutoConfiguration {
}
