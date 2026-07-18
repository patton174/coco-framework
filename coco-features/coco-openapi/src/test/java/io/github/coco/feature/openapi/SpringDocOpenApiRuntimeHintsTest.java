package io.github.coco.feature.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
import io.github.coco.feature.openapi.springdoc.SpringDocOpenApiRuntimeHints;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * SpringDoc AOT 运行时提示测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class SpringDocOpenApiRuntimeHintsTest {

    @Test
    void registersReflectionAndProxyHintsForCompatibleSpringDoc() {
        RuntimeHints hints = new RuntimeHints();

        new SpringDocOpenApiRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().getTypeHint(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS))).isNotNull();
        assertThat(hints.reflection().getTypeHint(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.INFO_CLASS))).isNotNull();
        assertThat(hints.proxies().jdkProxyHints())
                .anySatisfy(hint -> assertThat(hint.getProxiedInterfaces())
                        .contains(TypeReference.of(CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS)));
    }

    @Test
    void doesNotRegisterHintsForAnIncompatibleSameNameSpringDocApi(@TempDir Path temporaryDirectory)
            throws Exception {
        Path classesDirectory = temporaryDirectory.resolve("classes");
        compile(classesDirectory, classPathFor(OpenAPI.class), List.of(source(
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS, """
                        package org.springdoc.core.customizers;
                        import io.swagger.v3.oas.models.OpenAPI;
                        public interface OpenApiCustomizer {
                            boolean customise(OpenAPI openApi);
                        }
                        """)));
        RuntimeHints hints = new RuntimeHints();

        try (URLClassLoader classLoader = new ChildFirstClassLoader(
                new URL[] { classesDirectory.toUri().toURL() }, getClass().getClassLoader(),
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS)) {
            new SpringDocOpenApiRuntimeHints().registerHints(hints, classLoader);
        }

        assertThat(hints.reflection().getTypeHint(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS))).isNull();
        assertThat(hints.proxies().jdkProxyHints()).isEmpty();
    }

    private static void compile(Path classesDirectory, String classPath, List<JavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler.getTask(null, fileManager, null,
                    List.of("-classpath", classPath, "-d", classesDirectory.toString()), null, sources).call();
            assertThat(compiled).isTrue();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Unable to compile incompatible SpringDoc fixture", ex);
        }
    }

    private static JavaFileObject source(String className, String source) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }

    private static String classPathFor(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        private final String childFirstClassName;

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent, String childFirstClassName) {
            super(urls, parent);
            this.childFirstClassName = childFirstClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!this.childFirstClassName.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
