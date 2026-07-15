package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import io.github.coco.feature.web.body.CocoRequestBodyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class CocoWebPropertiesCompatibilityTest {

    @Test
    void binderAndJavaBeanAccessorsKeepNestedPropertiesLive() {
        CocoWebProperties properties = new CocoWebProperties();
        CocoRequestBodyProperties requestBody = properties.getRequestBody();

        assertSame(properties.getTrace(), properties.getTrace());
        assertSame(properties.getResponse(), properties.getResponse());
        assertSame(properties.getResponseWrap(), properties.getResponseWrap());
        assertSame(properties.getAccessLog(), properties.getAccessLog());
        assertSame(properties.getRequestBody(), properties.getRequestBody());
        assertSame(properties.getSignature(), properties.getSignature());
        assertSame(properties.getEncryption(), properties.getEncryption());
        assertSame(properties.getReplay(), properties.getReplay());
        assertSame(properties.getContext(), properties.getContext());

        Binder.get(new MockEnvironment()
                .withProperty("coco.web.request-body.enabled", "false")
                .withProperty("coco.web.signature.required", "true")
                .withProperty("coco.web.encryption.required", "true")
                .withProperty("coco.web.replay.required", "true")
                .withProperty("coco.web.context.parameter.include-parameters", "false"))
                .bind("coco.web", Bindable.ofInstance(properties));

        assertSame(requestBody, properties.getRequestBody());
        assertFalse(properties.getRequestBody().isEnabled());
        assertTrue(properties.getSignature().isRequired());
        assertTrue(properties.getEncryption().isRequired());
        assertTrue(properties.getReplay().isRequired());
        assertFalse(properties.getContext().getParameter().isIncludeParameters());

        properties.setRequestBody(null);
        assertTrue(properties.getRequestBody().isEnabled());
    }

    @Test
    void java17ConsumerCompilesAndObservesLiveNestedConfiguration(@TempDir Path temporaryDirectory)
            throws Exception {
        Path sourceFile = temporaryDirectory.resolve("external/LegacyWebPropertiesConsumer.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package external;

                import io.github.coco.feature.web.CocoWebProperties;

                public final class LegacyWebPropertiesConsumer {

                    public static boolean mutatesNestedProperties() {
                        CocoWebProperties properties = new CocoWebProperties();
                        properties.getRequestBody().setEnabled(false);
                        properties.getSignature().setRequired(true);
                        properties.getEncryption().setRequired(true);
                        properties.getReplay().setRequired(true);
                        properties.getContext().getParameter().setIncludeParameters(false);
                        return !properties.getRequestBody().isEnabled()
                                && properties.getSignature().isRequired()
                                && properties.getEncryption().isRequired()
                                && properties.getReplay().isRequired()
                                && !properties.getContext().getParameter().isIncludeParameters();
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
            Class<?> consumer = Class.forName("external.LegacyWebPropertiesConsumer", true, classLoader);
            assertTrue((Boolean) consumer.getMethod("mutatesNestedProperties").invoke(null));
        }
    }
}
