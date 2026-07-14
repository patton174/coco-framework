package io.github.coco.feature.web.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Coco Web 参数日志安全策略测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoWebParameterSecurityTest {

    private static final String HIDDEN_VALUE = "******";

    @Test
    void masksCommonSensitiveNamesAcrossCaseAndSeparatorVariants() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("client-secret", "secret-1");
        parameters.put("clientSecret", "secret-2");
        parameters.put("CLIENT_SECRET", "secret-3");
        parameters.put("id-token", "token-1");
        parameters.put("idToken", "token-2");
        parameters.put("authorization.code", "code-1");
        parameters.put("authorizationCode", "code-2");
        parameters.put("OTP", "123456");
        MockHttpServletRequest request = queryRequest(parameters);

        Map<String, List<String>> sanitized = resolver.resolveParameters(request);

        assertTrue(properties.getMaskedParameterNames().contains("client_secret"));
        assertTrue(properties.getMaskedParameterNames().contains("id_token"));
        assertTrue(properties.getMaskedParameterNames().contains("authorization_code"));
        assertTrue(properties.getMaskedParameterNames().contains("otp"));
        parameters.keySet().forEach(name -> assertEquals(List.of(HIDDEN_VALUE), sanitized.get(name), name));
    }

    @Test
    void hidesUnknownValuesByDefaultButKeepsRawSecurityView() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("displayName", "Coconut");
        parameters.put("region", "eu-west-1");
        MockHttpServletRequest request = queryRequest(parameters);

        assertEquals(List.of(HIDDEN_VALUE), resolver.resolveParameters(request).get("displayName"));
        assertEquals(List.of(HIDDEN_VALUE), resolver.resolveParameters(request).get("region"));
        assertEquals("displayName=******&region=******", resolver.resolveQueryString(request));
        assertEquals(List.of("Coconut"), resolver.resolveRawParameters(request).get("displayName"));
        assertEquals(List.of("eu-west-1"), resolver.resolveRawParameters(request).get("region"));
        assertEquals("displayName=Coconut&region=eu-west-1", resolver.resolveRawQueryString(request));
    }

    @Test
    void appliesSafeDefaultToCachedJsonPayloadWithoutChangingRawPayload() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        DefaultCocoPayloadParameterResolver resolver = new DefaultCocoPayloadParameterResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/token");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] body = ("{\"clientSecret\":\"client-value\",\"id.token\":\"id-value\","
                + "\"authorization-code\":\"code-value\",\"otp\":\"123456\","
                + "\"displayName\":\"Coconut\"}").getBytes(StandardCharsets.UTF_8);
        CocoCachedBodyHttpServletRequest cachedRequest = new CocoCachedBodyHttpServletRequest(request,
                CocoCachedRequestBody.cached(body));

        Map<String, List<String>> sanitized = resolver.resolvePayloadParameters(cachedRequest);
        Map<String, List<String>> raw = resolver.resolveRawPayloadParameters(cachedRequest);

        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("clientSecret"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("id.token"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("authorization-code"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("otp"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("displayName"));
        assertEquals(List.of("client-value"), raw.get("clientSecret"));
        assertEquals(List.of("Coconut"), raw.get("displayName"));
    }

    @Test
    void allowListExposesOnlyExplicitNonSensitiveParameterValues() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        properties.setValueCaptureMode(CocoWebParameterValueCaptureMode.ALLOW_LIST);
        properties.setValueAllowedParameterNames(Set.of("displayname", "clientSecret"));
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("displayName", "Coconut");
        parameters.put("region", "eu-west-1");
        parameters.put("clientSecret", "must-not-leak");
        MockHttpServletRequest request = queryRequest(parameters);

        Map<String, List<String>> sanitized = resolver.resolveParameters(request);

        assertEquals(List.of("Coconut"), sanitized.get("displayName"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("region"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("clientSecret"));
    }

    @Test
    void allModeExplicitlyRestoresLegacyValuesWhileCustomMasksStillWin() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        properties.setValueCaptureMode(CocoWebParameterValueCaptureMode.ALL);
        properties.setMaskedParameterNames(Set.of("api_credential"));
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("displayName", "Coconut");
        parameters.put("apiCredential", "secret-1");
        parameters.put("API-CREDENTIAL", "secret-2");
        MockHttpServletRequest request = queryRequest(parameters);

        Map<String, List<String>> sanitized = resolver.resolveParameters(request);

        assertEquals(List.of("Coconut"), sanitized.get("displayName"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("apiCredential"));
        assertEquals(List.of(HIDDEN_VALUE), sanitized.get("API-CREDENTIAL"));

        properties.setValueCaptureMode(null);
        assertEquals(CocoWebParameterValueCaptureMode.METADATA_ONLY, properties.getValueCaptureMode());
    }

    private static MockHttpServletRequest queryRequest(Map<String, String> parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setQueryString(parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse(""));
        parameters.forEach(request::addParameter);
        return request;
    }
}
