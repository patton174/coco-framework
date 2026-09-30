package io.github.coco.feature.web.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CocoSensitiveRequestHeaderContributorTest {
    @Test
    void contributedConfiguredHeaderIsCapturedOnlyAsMaskedValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Submission-Key", "must-not-appear");
        DefaultCocoRequestHeaderResolver resolver = new DefaultCocoRequestHeaderResolver(new CocoWebContextProperties(),
                List.of(() -> Set.of("X-Submission-Key")));
        assertThat(resolver.resolveIncludedHeaders(request)).containsEntry("x-submission-key", "******")
                .doesNotContainValue("must-not-appear");
    }
}
