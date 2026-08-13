package io.github.coco.feature.concurrencylimit;

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.internal.DefaultCocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCocoConcurrencyLimitResponseWriterTest {

    @Test
    void reusesUnifiedCocoBodyWithConfiguredHttpStatus() throws Exception {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("coco.concurrency-limit.rejected", Locale.US,
                "Too many in-flight requests");
        DefaultCocoMessageService messageService = new DefaultCocoMessageService(messageSource,
                () -> Locale.US, true);
        CocoWebExceptionHandler exceptionHandler = new CocoWebExceptionHandler(messageService,
                new DefaultCocoExceptionHttpStatusResolver(), CocoSystemCodes.defaults());
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.getResponse().setStatus(503);
        DefaultCocoConcurrencyLimitResponseWriter writer = new DefaultCocoConcurrencyLimitResponseWriter(
                exceptionHandler, new ObjectMapper(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader("Accept-Language", "en-US");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(CocoConcurrencyLimitErrorCode.REJECTED, request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("42910")
                .contains("Too many in-flight requests")
                .contains("\"success\":false");
    }
}
