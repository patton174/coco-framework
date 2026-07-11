package io.github.coco.i18n;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Coco I18n 模块结构测试。
 * <p>
 * 验证 I18n 的对外契约以包边界组织在当前模块内，避免为了包分层额外拆出 Maven API 模块。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoI18nModuleStructureTest {

    @Test
    void keepsI18nApiInMainPackageInsteadOfMavenModule() {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path foundationRoot = moduleRoot.getParent();

        assertFalse(Files.exists(foundationRoot.resolve("coco-i18n-api")));
        assertFalse(Files.exists(moduleRoot.resolve("src/main/java/io/github/coco/i18n/api")));
        assertTrue(Files.exists(moduleRoot.resolve(
                "src/main/java/io/github/coco/i18n/CocoMessage.java")));
        assertTrue(Files.exists(moduleRoot.resolve(
                "src/main/java/io/github/coco/i18n/CocoMessageCode.java")));
        assertTrue(Files.exists(moduleRoot.resolve(
                "src/main/java/io/github/coco/i18n/CocoMessageService.java")));
        assertTrue(Files.exists(moduleRoot.resolve(
                "src/main/java/io/github/coco/i18n/CocoMessageBundleRegistrar.java")));
        assertTrue(Files.exists(moduleRoot.resolve(
                "src/main/java/io/github/coco/i18n/internal/DefaultCocoMessageService.java")));
    }
}
