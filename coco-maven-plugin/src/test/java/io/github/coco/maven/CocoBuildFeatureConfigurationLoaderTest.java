package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeatureSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coco 构建期功能配置加载器测试。
 * <p>
 * 验证 Maven 插件可以从业务项目资源文件读取启用和禁用功能声明。
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
class CocoBuildFeatureConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsYamlFeatureConfiguration() throws Exception {
        Path resources = Files.createDirectories(this.tempDir.resolve("resources"));
        Files.writeString(resources.resolve("application.yml"), """
                coco:
                  features:
                    enabled:
                      - tenant
                    exclude:
                      - data-permission
                """, StandardCharsets.UTF_8);

        CocoFeatureSelection selection = new CocoBuildFeatureConfigurationLoader().load(resources);

        assertThat(selection.enabled()).containsExactly(CocoFeature.TENANT);
        assertThat(selection.disabled()).containsExactly(CocoFeature.DATA_PERMISSION);
    }

    @Test
    void loadsIndexedPropertiesFeatureConfiguration() throws Exception {
        Path resources = Files.createDirectories(this.tempDir.resolve("resources"));
        Files.writeString(resources.resolve("application.properties"), """
                coco.features.enabled[0]=openapi
                coco.features.disabled=tenant,data-permission
                """, StandardCharsets.UTF_8);

        CocoFeatureSelection selection = new CocoBuildFeatureConfigurationLoader().load(resources);

        assertThat(selection.enabled()).containsExactly(CocoFeature.OPENAPI);
        assertThat(selection.disabled()).containsExactlyInAnyOrder(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);
    }
}
