package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import io.github.coco.feature.security.web.CocoSecurityWebHeaderProperties;
import io.github.coco.feature.security.web.CocoSecurityWebProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CocoSecurityPropertiesTest {

    @Test
    void nestedConfigurationAccessorsRemainLiveForJavaBeanConsumers() {
        CocoSecurityProperties properties = new CocoSecurityProperties();

        CocoSecurityWebProperties web = properties.getWeb();
        CocoSecurityWebHeaderProperties header = web.getHeader();
        web.setEnabled(false);
        header.setEnabled(true);

        assertSame(web, properties.getWeb());
        assertSame(header, properties.getWeb().getHeader());
        assertFalse(properties.getWeb().isEnabled());
        assertTrue(properties.getWeb().getHeader().isEnabled());
    }

    @Test
    void settersCopyCallerSuppliedNestedConfigurationsAndAcceptNull() {
        CocoSecurityWebHeaderProperties header = new CocoSecurityWebHeaderProperties();
        header.setEnabled(true);
        CocoSecurityWebProperties web = new CocoSecurityWebProperties();
        web.setEnabled(false);
        web.setHeader(header);
        CocoSecurityProperties properties = new CocoSecurityProperties();
        properties.setWeb(web);

        web.setEnabled(true);
        header.setEnabled(false);

        assertFalse(properties.getWeb().isEnabled());
        assertTrue(properties.getWeb().getHeader().isEnabled());
        assertNotSame(web, properties.getWeb());
        assertNotSame(header, properties.getWeb().getHeader());

        properties.setWeb(null);
        assertTrue(properties.getWeb().isEnabled());
        assertFalse(properties.getWeb().getHeader().isEnabled());

        CocoSecurityWebHeaderProperties replacementHeader = new CocoSecurityWebHeaderProperties();
        replacementHeader.setEnabled(true);
        properties.getWeb().setHeader(replacementHeader);
        replacementHeader.setEnabled(false);

        assertTrue(properties.getWeb().getHeader().isEnabled());
        assertNotSame(replacementHeader, properties.getWeb().getHeader());
        properties.getWeb().setHeader(null);
        assertFalse(properties.getWeb().getHeader().isEnabled());
    }

    @Test
    void externalJava17ConsumerCompilesAndObservesLiveNestedConfiguration(@TempDir Path temporaryDirectory)
            throws Exception {
        Path sourceFile = temporaryDirectory.resolve("external/LegacySecurityConfigurationConsumer.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package external;

                import io.github.coco.feature.security.CocoSecurityProperties;

                public final class LegacySecurityConfigurationConsumer {

                    public static boolean usesLiveNestedConfiguration() {
                        CocoSecurityProperties properties = new CocoSecurityProperties();
                        properties.getWeb().setEnabled(false);
                        properties.getWeb().getHeader().setEnabled(true);
                        return !properties.getWeb().isEnabled() && properties.getWeb().getHeader().isEnabled();
                    }
                }
                """, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
        int result = compiler.run(null, null, null,
                "--release", "17",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                sourceFile.toString());

        assertEquals(0, result);
        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[] { classesDirectory.toUri().toURL() },
                getClass().getClassLoader())) {
            Class<?> consumer = Class.forName("external.LegacySecurityConfigurationConsumer", true, classLoader);
            assertTrue((Boolean) consumer.getMethod("usesLiveNestedConfiguration").invoke(null));
        }
    }
}
