package io.github.coco.feature.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.response.CocoResponseMetadataMode;
import io.github.coco.feature.web.response.CocoResponseProperties;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.feature.web.response.DefaultCocoResponseBodyFactory;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoWebErrorResponseWriterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearTrace() { CocoTraceContext.clear(); }

    @Test
    void writesChineseErrorWithTraceAndDebugMetadata() throws Exception {
        CocoTraceContext.setTraceId("trace-123");
        MockHttpServletRequest request = request();
        MockHttpServletResponse trace = new MockHttpServletResponse();
        writer(CocoResponseMetadataMode.TRACE).write(HttpStatus.CONFLICT, 40910, "error", request, trace);
        assertThat(this.mapper.readTree(trace.getContentAsByteArray()).path("message").asText()).isEqualTo("中文错误");
        assertThat(this.mapper.readTree(trace.getContentAsByteArray()).path("traceId").asText()).isEqualTo("trace-123");
        assertThat(this.mapper.readTree(trace.getContentAsByteArray()).has("path")).isFalse();

        MockHttpServletResponse debug = new MockHttpServletResponse();
        writer(CocoResponseMetadataMode.DEBUG).write(HttpStatus.CONFLICT, 40910, "error", request, debug);
        assertThat(this.mapper.readTree(debug.getContentAsByteArray()).path("path").asText()).isEqualTo("/orders/42");
    }

    @Test
    void writesTraceCookieWithExistingWebCookieSemantics() throws Exception {
        CocoTraceContext.setTraceId("trace-cookie");
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer(CocoResponseMetadataMode.COOKIE).write(HttpStatus.SERVICE_UNAVAILABLE, 50310, "error", request(), response);
        assertThat(response.getHeader("Set-Cookie")).contains("trace-cookie");
        assertThat(this.mapper.readTree(response.getContentAsByteArray()).has("traceId")).isFalse();
    }

    private CocoWebErrorResponseWriter writer(CocoResponseMetadataMode mode) {
        CocoResponseProperties properties = new CocoResponseProperties();
        properties.setMetadataMode(mode);
        CocoWebExceptionHandler handler = new CocoWebExceptionHandler(new ChineseMessageService(), exception -> HttpStatus.INTERNAL_SERVER_ERROR,
                CocoSystemCodes.defaults(), properties, new CocoTraceProperties(), new DefaultCocoResponseBodyFactory());
        return new CocoWebErrorResponseWriter(handler, this.mapper);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/42");
        request.addHeader("Accept-Language", "zh-CN");
        request.setPreferredLocales(java.util.List.of(Locale.SIMPLIFIED_CHINESE));
        return request;
    }

    private static final class ChineseMessageService implements CocoMessageService {
        @Override public String getMessage(String code, Object... args) { return "error".equals(code) ? "中文错误" : code; }
        @Override public String getMessage(String code, Locale locale, Object... args) { return getMessage(code, args); }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Object... args) { return getMessage(code, args); }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) { return getMessage(code, args); }
        @Override public String resolve(CocoMessage message) { return getMessage(message.code()); }
        @Override public String resolve(CocoMessage message, Locale locale) { return resolve(message); }
    }
}
