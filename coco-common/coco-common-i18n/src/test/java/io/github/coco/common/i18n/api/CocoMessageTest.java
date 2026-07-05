package io.github.coco.common.i18n.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Coco 消息描述测试。
 * <p>
 * 验证框架消息提示可以稳定携带消息编码、默认文本和参数。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoMessageTest {

    @Test
    void createsMessageFromMessageCodeContract() {
        CocoMessage message = SampleMessageCode.HELLO.message("Coco");

        assertEquals("sample.hello", message.code());
        assertEquals("你好，{0}", message.defaultMessage());
        assertArrayEquals(new Object[] {"Coco"}, message.args());
    }

    @Test
    void createsMessageByConstructorFromMessageCodeContract() {
        CocoMessage message = new CocoMessage(SampleMessageCode.HELLO, "Coco");

        assertEquals("sample.hello", message.code());
        assertEquals("你好，{0}", message.defaultMessage());
        assertArrayEquals(new Object[] {"Coco"}, message.args());
    }

    @Test
    void usesCodeAsDefaultMessageWhenContractDoesNotOverrideFallback() {
        CocoMessage message = CodeOnlyMessageCode.HELLO.message("Coco");

        assertEquals("sample.code-only", message.code());
        assertEquals("sample.code-only", message.defaultMessage());
    }

    @Test
    void preservesCodeDefaultMessageAndArguments() {
        CocoMessage message = new CocoMessage("sample.hello", "你好，{0}", "Coco");

        assertEquals("sample.hello", message.code());
        assertEquals("你好，{0}", message.defaultMessage());
        assertArrayEquals(new Object[] {"Coco"}, message.args());
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> new CocoMessage(" ", "默认消息"));
    }

    @Test
    void protectsArgumentsFromExternalMutation() {
        Object[] args = new Object[] {"before"};
        CocoMessage message = new CocoMessage("sample.mutable", "默认消息", args);

        args[0] = "after";

        assertArrayEquals(new Object[] {"before"}, message.args());
    }

    private enum SampleMessageCode implements CocoMessageCode {

        HELLO("sample.hello", "你好，{0}");

        private final String code;

        private final String defaultMessage;

        SampleMessageCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        @Override
        public String code() {
            return this.code;
        }

        @Override
        public String defaultMessage() {
            return this.defaultMessage;
        }
    }

    private enum CodeOnlyMessageCode implements CocoMessageCode {

        HELLO("sample.code-only");

        private final String code;

        CodeOnlyMessageCode(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return this.code;
        }
    }
}
