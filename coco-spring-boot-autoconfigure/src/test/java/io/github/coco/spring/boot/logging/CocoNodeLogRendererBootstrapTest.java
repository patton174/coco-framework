package io.github.coco.spring.boot.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/**
 * Coco Node 终端日志渲染器启动器测试。
 * <p>
 * 验证 jar 启动自动触发策略、关闭开关、命令参数构造以及内置渲染脚本资源。
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
class CocoNodeLogRendererBootstrapTest {

    @Test
    void skipsNonJarLaunchByDefault() {
        MockEnvironment environment = new MockEnvironment();

        assertFalse(CocoNodeLogRendererBootstrap.shouldInstall(environment,
                "io.github.coco.sample.basic.CocoSampleBasicApplication"));
    }

    @Test
    void installsForJarLaunchByDefault() {
        MockEnvironment environment = new MockEnvironment();

        assertTrue(CocoNodeLogRendererBootstrap.shouldInstall(environment, "coco-sample-basic.jar"));
    }

    @Test
    void honorsDisabledProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coco.logging.node-renderer.enabled", "false");

        assertFalse(CocoNodeLogRendererBootstrap.shouldInstall(environment, "coco-sample-basic.jar"));
    }

    @Test
    void canInstallForNonJarLaunchWhenJarOnlyDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coco.logging.node-renderer.jar-only", "false");

        assertTrue(CocoNodeLogRendererBootstrap.shouldInstall(environment,
                "io.github.coco.sample.basic.CocoSampleBasicApplication"));
    }

    @Test
    void buildsRendererCommandWithConfiguredColorMode() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coco.logging.node-renderer.command", "node-custom")
                .withProperty("coco.logging.node-renderer.color", "never");

        List<String> command = CocoNodeLogRendererBootstrap.rendererCommand(environment, Path.of("renderer.mjs"));

        assertEquals(List.of("node-custom", "renderer.mjs", "--no-color"), command);
    }

    @Test
    void usesDefaultsForBlankRendererCommandProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coco.logging.node-renderer.command", " ")
                .withProperty("coco.logging.node-renderer.color", " ");

        List<String> command = CocoNodeLogRendererBootstrap.rendererCommand(environment, Path.of("renderer.mjs"));

        assertEquals(List.of("node", "renderer.mjs", "--color=always"), command);
    }

    @Test
    void packagesRendererScriptResource() throws Exception {
        try (InputStream input = CocoNodeLogRendererBootstrap.class.getResourceAsStream(
                CocoNodeLogRendererBootstrap.RESOURCE_PATH)) {
            assertNotNull(input);
        }
    }

    @Test
    void keepsPackagedRendererScriptSynchronizedWithWorkspaceCli() throws Exception {
        Path workspaceScript = findWorkspaceCliRendererScript();
        String packagedScript;
        try (InputStream input = CocoNodeLogRendererBootstrap.class.getResourceAsStream(
                CocoNodeLogRendererBootstrap.RESOURCE_PATH)) {
            assertNotNull(input);
            packagedScript = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(Files.readString(workspaceScript), packagedScript);
    }

    @Test
    void locatesRendererScriptFromWorkspaceSourceTree(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("tools/coco-log-renderer/bin/coco-log-renderer.mjs");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('coco');");

        assertEquals(script, CocoNodeLogRendererBootstrap.findLocalRendererScript(tempDir));
    }

    private static Path findWorkspaceCliRendererScript() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (current != null) {
            Path script = current.resolve("tools/coco-log-renderer/bin/coco-log-renderer.mjs");
            if (Files.isRegularFile(script)) {
                return script;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Coco Node log renderer CLI script not found.");
    }
}
