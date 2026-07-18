package io.github.coco.feature.web.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class DefaultCocoRequestParameterResolverBoundaryTest {

    private static final String HIDDEN_VALUE = "******";

    @Test
    void keepsJsonLookingValuesInTheQueryParameterSourceForJsonRequests() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        properties.setValueCaptureMode(CocoWebParameterValueCaptureMode.ALL);
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        MockHttpServletRequest request = jsonRequest(
                "filter=%7B%22role%22%3A%22admin%22%7D&empty=&flag");

        Map<String, List<String>> parameters = resolver.resolveQueryParameters(request);

        assertEquals(List.of("{\"role\":\"admin\"}"), parameters.get("filter"));
        assertEquals(List.of(""), parameters.get("empty"));
        assertEquals(List.of(""), parameters.get("flag"));
    }

    @Test
    void masksSensitiveValuesWhenMalformedPercentEncodingCannotBeDecoded() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        properties.setValueCaptureMode(CocoWebParameterValueCaptureMode.ALLOW_LIST);
        properties.setValueAllowedParameterNames(Set.of("clientSecret"));
        DefaultCocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        MockHttpServletRequest request = jsonRequest("%63lientSecret=%E0%A4%A");

        assertEquals(Map.of("clientSecret", List.of(HIDDEN_VALUE)),
                resolver.resolveQueryParameters(request));
    }

    private static MockHttpServletRequest jsonRequest(String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setQueryString(queryString);
        return request;
    }
}
