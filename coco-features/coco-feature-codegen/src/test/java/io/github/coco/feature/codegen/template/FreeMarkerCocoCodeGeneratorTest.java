package io.github.coco.feature.codegen.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.github.coco.feature.codegen.core.CocoCodegenException;
import io.github.coco.feature.codegen.core.CocoCodegenRequest;
import io.github.coco.feature.codegen.core.CocoCodegenResult;
import io.github.coco.feature.codegen.core.CocoGeneratedFile;
import io.github.coco.feature.codegen.core.CocoGeneratedFileWriter;
import io.github.coco.feature.codegen.crud.CocoCrudIdStrategy;
import io.github.coco.feature.codegen.crud.CocoCrudSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreeMarkerCocoCodeGeneratorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void generatesCompleteBuiltInCrudSourceSet() {
        CocoCodegenResult result = generateProductCrud();
        Map<String, String> files = result.files().stream()
                .collect(Collectors.toMap(CocoGeneratedFile::path, CocoGeneratedFile::content));

        assertThat(files).hasSize(10).containsKeys(
                "com/example/catalog/domain/product/Product.java",
                "com/example/catalog/domain/product/ProductRepository.java",
                "com/example/catalog/application/product/ProductApplicationService.java",
                "com/example/catalog/infrastructure/product/ProductEntity.java",
                "com/example/catalog/infrastructure/product/ProductMapper.java",
                "com/example/catalog/infrastructure/product/MybatisPlusProductRepository.java",
                "com/example/catalog/interfaces/rest/product/ProductController.java",
                "com/example/catalog/interfaces/rest/product/dto/CreateProductRequest.java",
                "com/example/catalog/interfaces/rest/product/dto/UpdateProductRequest.java",
                "com/example/catalog/interfaces/rest/product/dto/ProductResponse.java");
        assertThat(files.get("com/example/catalog/interfaces/rest/product/ProductController.java"))
                .contains("CreateProductRequest", "UpdateProductRequest", "ProductResponse")
                .doesNotContain("ProductEntity");
        assertThat(files.get("com/example/catalog/application/product/ProductApplicationService.java"))
                .contains("@Transactional(readOnly = true)", "@Transactional",
                        "CocoCommonErrorCode.NOT_FOUND.notFound", "size > 100");
        assertThat(files.get("com/example/catalog/domain/product/ProductRepository.java"))
                .contains("PageResult findPage(long page, long size)")
                .doesNotContain("mybatis", "BaseMapper");
        assertThat(files.get("com/example/catalog/infrastructure/product/ProductEntity.java"))
                .contains("@TableName(\"catalog_product\")", "IdType.AUTO");
        assertThat(files.get("com/example/catalog/infrastructure/product/MybatisPlusProductRepository.java"))
                .contains("Page.of(page, size)");
    }

    @Test
    void generatedCrudSourcesCompileWithJava17() throws IOException {
        assertCompilesWithJava17(generateProductCrud());
    }

    @Test
    void generatedCrudSourcesCompileWhenFqcnConflictsWithFixedImport() throws IOException {
        CocoCodegenResult result = builtInGenerator().generate(CocoCrudSpec.builder(
                        "com.example.catalog", "Product", "catalog_product")
                .id("id", "id", Long.class, CocoCrudIdStrategy.AUTO)
                .field("items", "items", "java.awt.List", false)
                .build()
                .toRequest());

        assertThat(result.files().stream().map(CocoGeneratedFile::content).collect(Collectors.joining("\n")))
                .contains("java.awt.List items")
                .doesNotContain("import java.awt.List;");
        assertCompilesWithJava17(result);
    }

    private void assertCompilesWithJava17(CocoCodegenResult result) throws IOException {
        Path sourceDirectory = this.tempDirectory.resolve("sources");
        Path classesDirectory = this.tempDirectory.resolve("classes");
        new CocoGeneratedFileWriter().write(sourceDirectory, result);
        Files.createDirectories(classesDirectory);

        List<Path> sources;
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            boolean compiled = compiler.getTask(null, fileManager, diagnostics,
                    List.of("--release", "17", "-classpath", classpath, "-d", classesDirectory.toString()),
                    null, units).call();

            assertThat(compiled)
                    .withFailMessage(() -> diagnostics.getDiagnostics().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(System.lineSeparator())))
                    .isTrue();
        }
    }

    @Test
    void usesCustomTemplateLocationAndEncoding() throws IOException {
        Path root = this.tempDirectory.resolve("utf16-templates");
        Path group = root.resolve("custom");
        Files.createDirectories(group);
        Charset encoding = StandardCharsets.UTF_16LE;
        Files.writeString(group.resolve("manifest.properties"), """
                group=custom
                template.count=1
                template.0.source=sample.ftl
                template.0.output=generated/${name}.txt
                """, encoding);
        Files.writeString(group.resolve("sample.ftl"), "Hello ${name}", encoding);

        FreeMarkerCocoCodeGenerator generator = new FreeMarkerCocoCodeGenerator(root.toUri().toString(), encoding);
        CocoCodegenResult result = generator.generate(CocoCodegenRequest.builder("custom")
                .attribute("name", "Order")
                .build());

        assertThat(result.files()).containsExactly(new CocoGeneratedFile("generated/Order.txt", "Hello Order"));
    }

    @Test
    void rejectsUnknownGroupsReservedAttributesAndDuplicateOutputs() throws IOException {
        FreeMarkerCocoCodeGenerator builtIn = builtInGenerator();
        assertThatThrownBy(() -> builtIn.generate(CocoCodegenRequest.builder("missing").build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("unknown template group");

        assertThatThrownBy(() -> builtIn.generate(CocoCodegenRequest.builder("crud")
                .attribute("targetPackage", "override")
                .build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("reserved");

        Path root = this.tempDirectory.resolve("duplicate-templates");
        Path group = root.resolve("duplicate");
        Files.createDirectories(group);
        Files.writeString(group.resolve("manifest.properties"), """
                group=duplicate
                template.count=2
                template.0.source=one.ftl
                template.0.output=same.txt
                template.1.source=two.ftl
                template.1.output=same.txt
                """);
        Files.writeString(group.resolve("one.ftl"), "one");
        Files.writeString(group.resolve("two.ftl"), "two");

        assertThatThrownBy(() -> new FreeMarkerCocoCodeGenerator(root.toString())
                .generate(CocoCodegenRequest.builder("duplicate").build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("duplicate output");
    }

    @Test
    void reportsInvalidManifestMissingTemplateAndUnsafeOutput() throws IOException {
        Path root = this.tempDirectory.resolve("invalid-templates");
        writeGroup(root, "invalid", """
                group=invalid
                template.count=1
                template.0.source=sample.ftl
                template.0.output=sample.txt
                unexpected=true
                """, Map.of("sample.ftl", "sample"));
        writeGroup(root, "missing", """
                group=missing
                template.count=1
                template.0.source=absent.ftl
                template.0.output=sample.txt
                """, Map.of());
        writeGroup(root, "unsafe", """
                group=unsafe
                template.count=1
                template.0.source=sample.ftl
                template.0.output=../escape.txt
                """, Map.of("sample.ftl", "sample"));
        FreeMarkerCocoCodeGenerator generator = new FreeMarkerCocoCodeGenerator(root.toString());

        assertThatThrownBy(() -> generator.generate(CocoCodegenRequest.builder("invalid").build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("invalid template manifest");
        assertThatThrownBy(() -> generator.generate(CocoCodegenRequest.builder("missing").build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("missing or unreadable template");
        assertThatThrownBy(() -> generator.generate(CocoCodegenRequest.builder("unsafe").build()))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("must not traverse");
        assertThatThrownBy(() -> new FreeMarkerCocoCodeGenerator(root.toString(), "not a charset"))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("unsupported codegen encoding");
    }

    private CocoCodegenResult generateProductCrud() {
        return builtInGenerator().generate(CocoCrudSpec.builder(
                        "com.example.catalog", "Product", "catalog_product")
                .apiPath("/products")
                .id("id", "id", Long.class, CocoCrudIdStrategy.AUTO)
                .field("sku", "sku", String.class, true)
                .field("name", "name", String.class, true)
                .field("unitPrice", "unit_price", BigDecimal.class, true)
                .build()
                .toRequest());
    }

    private static FreeMarkerCocoCodeGenerator builtInGenerator() {
        return new FreeMarkerCocoCodeGenerator("classpath:/coco/codegen/templates", StandardCharsets.UTF_8);
    }

    private static void writeGroup(Path root, String name, String manifest, Map<String, String> templates)
            throws IOException {
        Path group = root.resolve(name);
        Files.createDirectories(group);
        Files.writeString(group.resolve("manifest.properties"), manifest);
        for (Map.Entry<String, String> template : templates.entrySet()) {
            Files.writeString(group.resolve(template.getKey()), template.getValue());
        }
    }
}
