package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.feature.security.web.CocoSecurityWebHeaderProperties;
import io.github.coco.feature.security.web.CocoSecurityWebProperties;
import org.junit.jupiter.api.Test;

class CocoSecurityPropertiesTest {

    @Test
    void nestedConfigurationAccessorsReturnIndependentSnapshots() {
        CocoSecurityWebHeaderProperties header = new CocoSecurityWebHeaderProperties();
        header.setEnabled(true);
        CocoSecurityWebProperties web = new CocoSecurityWebProperties();
        web.setEnabled(false);
        web.setHeader(header);
        CocoSecurityProperties properties = new CocoSecurityProperties();
        properties.setWeb(web);

        web.setEnabled(true);
        header.setEnabled(false);
        CocoSecurityWebProperties exposedWeb = properties.getWeb();
        exposedWeb.setEnabled(true);
        exposedWeb.getHeader().setEnabled(false);

        assertFalse(properties.getWeb().isEnabled());
        assertTrue(properties.getWeb().getHeader().isEnabled());
    }
}
