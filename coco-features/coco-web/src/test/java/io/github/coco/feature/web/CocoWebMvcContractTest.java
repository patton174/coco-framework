package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.CocoBusinessCode;
import io.github.coco.exception.CocoBusinessExceptions;
import io.github.coco.exception.CocoCommonErrorCode;
import io.github.coco.exception.CocoException;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.feature.web.response.CocoIgnoreResponseWrap;
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoResponsePayload;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class CocoWebMvcContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class))
            .withUserConfiguration(MvcTestConfiguration.class)
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void localizesBusinessExceptionAndDoesNotWrapErrorResponseTwice() {
        this.contextRunner.run(context -> {
            MockMvc mockMvc = mockMvc(context);

            MvcResult result = mockMvc.perform(get("/contract/business-error")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(status().isNotFound())
                    .andReturn();
            Map<?, ?> body = readBody(result);

            assertEquals(Boolean.FALSE, body.get("success"));
            assertEquals(200404, body.get("code"));
            assertEquals("Resource not found: order-42", body.get("message"));
            assertNull(body.get("data"));
            assertFalse(body.containsKey("result"));
        });
    }

    @Test
    void fallsBackToCocoDefaultLocaleWhenAcceptLanguageIsMissing() {
        this.contextRunner.run(context -> {
            MvcResult result = mockMvc(context).perform(get("/contract/business-error"))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertEquals("资源不存在：order-42", readBody(result).get("message"));
        });
    }

    @Test
    void mapsRealMvcValidationExceptionToUnifiedBadRequest() {
        this.contextRunner.run(context -> {
            MvcResult result = mockMvc(context).perform(get("/contract/validation")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            Map<?, ?> body = readBody(result);
            String expectedMessage = context.getBean(CocoMessageService.class)
                    .getMessage("coco.web.error.bad-request", Locale.US);

            assertEquals(Boolean.FALSE, body.get("success"));
            assertEquals(400, body.get("code"));
            assertEquals(expectedMessage, body.get("message"));
            assertFalse(body.get("message").toString().contains("name"));
        });
    }

    @Test
    void writesFilterExceptionWithTheSameLocalizedResponseContract() {
        this.contextRunner.run(context -> {
            Filter filter = new ContractExceptionFilter(context.getBean(CocoFilterExceptionResponseWriter.class));
            MvcResult result = mockMvc(context, filter).perform(get("/contract/plain")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            Map<?, ?> body = readBody(result);
            String expectedMessage = context.getBean(CocoMessageService.class)
                    .getMessage(CocoCommonErrorCode.UNAUTHORIZED, Locale.US);

            assertEquals(Boolean.FALSE, body.get("success"));
            assertEquals(401, body.get("code"));
            assertEquals(expectedMessage, body.get("message"));
        });
    }

    @Test
    void hidesUnknownExceptionDetailsBehindUnifiedInternalError() {
        this.contextRunner.run(context -> {
            MvcResult result = mockMvc(context).perform(get("/contract/unknown-error")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(status().isInternalServerError())
                    .andReturn();
            Map<?, ?> body = readBody(result);
            String expectedMessage = context.getBean(CocoMessageService.class)
                    .getMessage(CocoCommonErrorCode.INTERNAL_ERROR, Locale.US);

            assertEquals(Boolean.FALSE, body.get("success"));
            assertEquals(500, body.get("code"));
            assertEquals(expectedMessage, body.get("message"));
            assertFalse(body.get("message").toString().contains("boom-sensitive"));
        });
    }

    @Test
    void carriesTraceMetadataThroughSuccessAndErrorResponses() {
        this.contextRunner
                .withPropertyValues("coco.web.response.metadata-mode=debug")
                .run(context -> {
                    FilterRegistrationBean<?> registration = context.getBean(
                            "cocoTraceFilterRegistration", FilterRegistrationBean.class);
                    Filter traceFilter = registration.getFilter();
                    MockMvc mockMvc = mockMvc(context, traceFilter);

                    MvcResult success = mockMvc.perform(get("/contract/plain")
                                    .header("X-Trace-Id", "contract-trace-success"))
                            .andExpect(status().isOk())
                            .andReturn();
                    Map<?, ?> successBody = readBody(success);
                    assertEquals("contract-trace-success", successBody.get("traceId"));
                    assertEquals("/contract/plain", successBody.get("path"));
                    assertEquals("contract-trace-success", success.getResponse().getHeader("X-Trace-Id"));

                    MvcResult error = mockMvc.perform(get("/contract/business-error")
                                    .header("X-Trace-Id", "contract-trace-error"))
                            .andExpect(status().isNotFound())
                            .andReturn();
                    Map<?, ?> errorBody = readBody(error);
                    assertEquals("contract-trace-error", errorBody.get("traceId"));
                    assertEquals("/contract/business-error", errorBody.get("path"));
                    assertEquals("contract-trace-error", error.getResponse().getHeader("X-Trace-Id"));
                });
    }

    @Test
    void preservesAlreadyWrappedStreamingFileAndNoBodyResponses() {
        this.contextRunner.run(context -> {
            MockMvc mockMvc = mockMvc(context);

            MvcResult wrapped = mockMvc.perform(get("/contract/already-wrapped"))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<?, ?> wrappedBody = readBody(wrapped);
            assertEquals(Boolean.TRUE, wrappedBody.get("success"));
            assertEquals(209, wrappedBody.get("code"));
            assertEquals("already wrapped", wrappedBody.get("message"));

            MvcResult streaming = mockMvc.perform(get("/contract/stream"))
                    .andReturn();
            assertTrue(streaming.getRequest().isAsyncStarted());
            MvcResult streamed = mockMvc.perform(asyncDispatch(streaming))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals("stream-body", streamed.getResponse().getContentAsString());

            MvcResult file = mockMvc.perform(get("/contract/file"))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals("file-body", file.getResponse().getContentAsString());

            MvcResult noBody = mockMvc.perform(get("/contract/no-body"))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertEquals(0, noBody.getResponse().getContentAsByteArray().length);
        });
    }

    @Test
    void honorsMethodLevelResponseWrapOptOut() {
        this.contextRunner.run(context -> {
            MvcResult result = mockMvc(context).perform(get("/contract/raw"))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<?, ?> body = readBody(result);

            assertEquals(Boolean.TRUE, body.get("raw"));
            assertFalse(body.containsKey("success"));
        });
    }

    @Test
    void mapsTypedExceptionsToTheirHttpStatusesThroughMvc() {
        this.contextRunner.run(context -> {
            MockMvc mockMvc = mockMvc(context);
            List<StatusCase> cases = List.of(
                    new StatusCase("request", 400),
                    new StatusCase("unauthorized", 401),
                    new StatusCase("forbidden", 403),
                    new StatusCase("not-found", 404),
                    new StatusCase("conflict", 409),
                    new StatusCase("system", 500));

            for (StatusCase statusCase : cases) {
                MvcResult result = mockMvc.perform(get("/contract/status/{kind}", statusCase.kind()))
                        .andReturn();

                assertEquals(statusCase.status(), result.getResponse().getStatus());
                assertEquals(statusCase.status(), readBody(result).get("code"));
            }
        });
    }

    @Test
    void applicationMessageBundleOverridesFrameworkMessages() {
        this.contextRunner
                .withPropertyValues("coco.common.i18n.basename=coco-web-contract-messages")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    MvcResult success = mockMvc.perform(get("/contract/plain")
                                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                            .andExpect(status().isOk())
                            .andReturn();
                    assertEquals("Application success.", readBody(success).get("message"));

                    MvcResult error = mockMvc.perform(get("/contract/business-error")
                                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                            .andExpect(status().isNotFound())
                            .andReturn();
                    assertEquals("Application missing: order-42", readBody(error).get("message"));
                });
    }

    @Test
    void userCanReplaceExceptionResolverAndResponseFactory() {
        this.contextRunner
                .withBean(io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver.class,
                        () -> exception -> HttpStatus.CONFLICT)
                .withBean(CocoResponseBodyFactory.class, ContractResponseBodyFactory::new)
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    MvcResult success = mockMvc.perform(get("/contract/plain"))
                            .andReturn();
                    assertEquals(200, success.getResponse().getStatus(), () ->
                            "resolvedException=" + success.getResolvedException()
                                    + ", body=" + new String(success.getResponse().getContentAsByteArray(),
                                    StandardCharsets.UTF_8));
                    Map<?, ?> successBody = readBody(success);
                    assertEquals("success", successBody.get("kind"));
                    assertEquals(200, successBody.get("status"));
                    assertEquals(Map.of("name", "Coco"), successBody.get("payload"));

                    MvcResult error = mockMvc.perform(get("/contract/custom-error"))
                            .andExpect(status().isConflict())
                            .andReturn();
                    Map<?, ?> errorBody = readBody(error);
                    assertEquals("error", errorBody.get("kind"));
                    assertEquals(409, errorBody.get("status"));
                    assertEquals("Custom contract error", errorBody.get("message"));
                    assertFalse(errorBody.containsKey("success"));
                });
    }

    @AfterEach
    void clearRequestContexts() {
        CocoTraceContext.clear();
        LocaleContextHolder.resetLocaleContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockMvc mockMvc(WebApplicationContext context, Filter... filters) {
        var builder = MockMvcBuilders.webAppContextSetup(context);
        if (filters.length > 0) {
            builder.addFilters(filters);
        }
        return builder.build();
    }

    private static Map<?, ?> readBody(MvcResult result) throws IOException {
        return OBJECT_MAPPER.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcTestConfiguration {

        @Bean
        ContractController contractController() {
            return new ContractController();
        }
    }

    @RestController
    static final class ContractController {

        @GetMapping("/contract/business-error")
        Object businessError() {
            throw CocoBusinessExceptions.notFound(ContractBusinessCode.ORDER_NOT_FOUND, "order-42");
        }

        @GetMapping("/contract/plain")
        Map<String, String> plain() {
            return Map.of("name", "Coco");
        }

        @GetMapping("/contract/validation")
        Map<String, String> validation(@RequestParam("name") String name) {
            return Map.of("name", name);
        }

        @GetMapping("/contract/unknown-error")
        Object unknownError() {
            throw new IllegalStateException("boom-sensitive");
        }

        @GetMapping("/contract/already-wrapped")
        CocoApiResponse<Map<String, String>> alreadyWrapped() {
            return CocoApiResponse.success(209, "already wrapped", Map.of("name", "Coco"));
        }

        @GetMapping(value = "/contract/stream", produces = MediaType.TEXT_PLAIN_VALUE)
        StreamingResponseBody stream() {
            return outputStream -> outputStream.write("stream-body".getBytes(StandardCharsets.UTF_8));
        }

        @GetMapping(value = "/contract/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        Resource file() {
            return new ByteArrayResource("file-body".getBytes(StandardCharsets.UTF_8));
        }

        @GetMapping("/contract/no-body")
        ResponseEntity<Void> noBody() {
            return ResponseEntity.noContent().build();
        }

        @CocoIgnoreResponseWrap
        @GetMapping("/contract/raw")
        Map<String, Boolean> raw() {
            return Map.of("raw", true);
        }

        @GetMapping("/contract/status/{kind}")
        Object status(@PathVariable String kind) {
            CocoException exception = switch (kind) {
                case "request" -> CocoCommonErrorCode.INVALID_ARGUMENT.request("value");
                case "unauthorized" -> CocoCommonErrorCode.UNAUTHORIZED.unauthorized();
                case "forbidden" -> CocoCommonErrorCode.FORBIDDEN.forbidden();
                case "not-found" -> CocoCommonErrorCode.NOT_FOUND.notFound("value");
                case "conflict" -> CocoCommonErrorCode.CONFLICT.conflict("value");
                case "system" -> CocoCommonErrorCode.INTERNAL_ERROR.system();
                default -> CocoCommonErrorCode.UNKNOWN.exception();
            };
            throw exception;
        }

        @GetMapping("/contract/custom-error")
        Object customError() {
            throw new CocoException("contract.custom-error", "Custom contract error");
        }
    }

    private record StatusCase(String kind, int status) {
    }

    private enum ContractBusinessCode implements CocoBusinessCode {

        ORDER_NOT_FOUND(200404, "coco.error.not-found");

        private final int code;

        private final String messageCode;

        ContractBusinessCode(int code, String messageCode) {
            this.code = code;
            this.messageCode = messageCode;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public String messageCode() {
            return this.messageCode;
        }
    }

    private static final class ContractExceptionFilter implements Filter {

        private final CocoFilterExceptionResponseWriter responseWriter;

        private ContractExceptionFilter(CocoFilterExceptionResponseWriter responseWriter) {
            this.responseWriter = responseWriter;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
            try {
                throw CocoCommonErrorCode.UNAUTHORIZED.unauthorized();
            }
            catch (CocoException exception) {
                this.responseWriter.write(exception, (HttpServletRequest) request, (HttpServletResponse) response);
            }
        }
    }

    private static final class ContractResponseBodyFactory implements CocoResponseBodyFactory {

        @Override
        public Object success(CocoResponsePayload<?> payload) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("kind", "success");
            response.put("status", payload.code());
            response.put("message", payload.message());
            response.put("payload", payload.data());
            return response;
        }

        @Override
        public Object error(CocoResponsePayload<?> payload) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("kind", "error");
            response.put("status", payload.code());
            response.put("message", payload.message());
            return response;
        }
    }
}
