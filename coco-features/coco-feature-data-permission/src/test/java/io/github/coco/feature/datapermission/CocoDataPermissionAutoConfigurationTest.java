package io.github.coco.feature.datapermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco 数据权限功能自动配置测试。
 * <p>
 * 验证数据权限功能模块可以通过 Coco 国际化基础设施注册自己的消息资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoDataPermissionAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersDataPermissionMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoDataPermissionMessageBundleRegistrar"));
            assertEquals("Coco 数据权限功能消息资源已就绪。",
                    messageService.getMessage("coco.feature.data-permission.ready"));
        });
    }
}
