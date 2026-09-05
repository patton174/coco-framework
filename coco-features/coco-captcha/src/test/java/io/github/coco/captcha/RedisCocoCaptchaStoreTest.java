package io.github.coco.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisCocoCaptchaStoreTest {

    @Test
    void storeUsesPxTtlAndDigestKeyWithoutExposingCaptchaId() throws Exception {
        CapturingExecutor executor = new CapturingExecutor("code-42");
        RedisCocoCaptchaStore store = new RedisCocoCaptchaStore(executor, "coco:captcha:");

        store.store("session-7:image", "code-42", Duration.ofMinutes(2));

        assertThat(executor.script.getScriptAsString()).contains("SET", "PX");
        assertThat(executor.keys).containsExactly("coco:captcha:" + sha256("session-7:image"));
        assertThat(executor.keys.get(0)).doesNotContain("session-7:image");
        assertThat(executor.arguments).containsExactly("code-42", "120000");
    }

    @Test
    void consumeDeletesInsideLuaSoTheAnswerIsSingleUse() {
        CapturingExecutor executor = new CapturingExecutor("code-42");
        RedisCocoCaptchaStore store = new RedisCocoCaptchaStore(executor, "coco:captcha:");

        assertThat(store.consume("session-7:image")).isEqualTo("code-42");
        // GET and DEL must both live in the script; a read-then-delete round trip would let two
        // concurrent verifications consume the same answer.
        assertThat(executor.script.getScriptAsString()).contains("GET", "DEL");
        assertThat(executor.arguments).isEmpty();
    }

    @Test
    void consumeReturnsNullWhenTheAnswerExpiredOrWasAlreadyUsed() {
        RedisCocoCaptchaStore store = new RedisCocoCaptchaStore(new CapturingExecutor(null), "coco:captcha:");

        assertThat(store.consume("missing")).isNull();
    }

    @Test
    void redisFailuresPropagateInsteadOfLookingLikeAWrongCaptcha() {
        RedisCocoCaptchaStore store = new RedisCocoCaptchaStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "coco:captcha:");

        assertThatThrownBy(() -> store.consume("session-7"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
    }

    @Test
    void rejectsInvalidArguments() {
        CapturingExecutor executor = new CapturingExecutor("code-42");
        RedisCocoCaptchaStore store = new RedisCocoCaptchaStore(executor, "coco:captcha:");

        assertThatThrownBy(() -> store.store("id", "answer", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.store("id", "answer", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCocoCaptchaStore(executor, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesStringResultTypeForRedisExecution() {
        assertThat(RedisCocoCaptchaStore.storeScript().getResultType()).isEqualTo(String.class);
        assertThat(RedisCocoCaptchaStore.consumeScript().getResultType()).isEqualTo(String.class);
    }


    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class CapturingExecutor implements RedisCocoCaptchaStore.ScriptExecutor {

        private final String result;

        private DefaultRedisScript<String> script;

        private List<String> keys;

        private List<String> arguments;

        private CapturingExecutor(String result) {
            this.result = result;
        }

        @Override
        public String execute(DefaultRedisScript<String> script, List<String> keys, String... arguments) {
            this.script = script;
            this.keys = List.copyOf(keys);
            this.arguments = new ArrayList<>(List.of(arguments));
            return this.result;
        }
    }
}
