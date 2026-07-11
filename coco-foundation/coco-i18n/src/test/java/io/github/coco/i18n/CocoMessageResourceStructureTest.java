package io.github.coco.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco 消息资源结构测试。
 * <p>
 * 验证框架内置消息资源按模块分段维护，避免不同模块的消息键无边界混放。
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
class CocoMessageResourceStructureTest {

    @Test
    void frameworkMessagesAreGroupedByOwningModule() {
        String content = readResource("coco-messages_zh_CN.properties");

        assertTrue(content.contains("# coco-exception"));
        assertTrue(content.contains("coco.error.unknown=未知错误"));
    }

    private static String readResource(String name) {
        URL resource = CocoMessageResourceStructureTest.class.getClassLoader().getResource(name);
        assertTrue(resource != null, () -> "resource not found: " + name);
        try {
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
