package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Coco CRUD 源码生成 Goal 测试。
 * <p>
 * 验证显式 Goal 的完整生成、dry-run、覆盖保护、非法规格处理和 Java 17 编译结果。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-maven-plugin}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoGenerateMojoTest {

    private static final List<String> PRODUCT_FILES = List.of(
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

    @TempDir
    Path tempDir;

    @Test
    void isAnExplicitGoalWithoutLifecycleBinding() throws Exception {
        Path descriptor = Path.of(System.getProperty("basedir"))
                .resolve("target/classes/META-INF/maven/plugin.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document;
        try (InputStream input = Files.newInputStream(descriptor)) {
            document = documentBuilderFactory.newDocumentBuilder().parse(input);
        }
        Node generateMojo = (Node) XPathFactory.newInstance().newXPath()
                .evaluate("/plugin/mojos/mojo[goal='generate']", document, XPathConstants.NODE);

        assertThat(generateMojo).isNotNull();
        assertThat(XPathFactory.newInstance().newXPath().evaluate("phase", generateMojo)).isBlank();
    }

    @Test
    void generatesCompleteCrudSourcesAndCompilesWithJava17() throws Exception {
        Path spec = writeSpec("coco-codegen.yml", validSpec());
        Path output = this.tempDir.resolve("generated-sources");

        mojo(spec, output).execute();

        assertThat(relativeJavaFiles(output)).containsExactlyInAnyOrderElementsOf(PRODUCT_FILES);
        assertThat(Files.readString(output.resolve(
                "com/example/catalog/interfaces/rest/product/ProductController.java"), StandardCharsets.UTF_8))
                .contains("@RequestMapping(\"/products\")");
        compileGeneratedSources(output);
    }

    @Test
    void compilesFullyQualifiedTypesThatShareTemplateImportNames() throws Exception {
        Path spec = writeSpec("qualified-type.yml", validSpec().replace(
                "      - name: sku\n        column: sku\n        type: String",
                "      - name: sku\n        column: sku\n        type: java.awt.List"));
        Path output = this.tempDir.resolve("qualified-type-output");

        mojo(spec, output).execute();

        compileGeneratedSources(output);
    }

    @Test
    void dryRunDoesNotCreateOutputDirectory() throws Exception {
        Path spec = writeSpec("dry-run.yml", validSpec());
        Path output = this.tempDir.resolve("dry-run-output");
        CocoGenerateMojo mojo = mojo(spec, output);
        set(mojo, "dryRun", true);

        mojo.execute();

        assertThat(output).doesNotExist();
    }

    @Test
    void honorsCustomTemplateLocationAndEncoding() throws Exception {
        Charset charset = StandardCharsets.UTF_16LE;
        Path spec = this.tempDir.resolve("custom-encoding.yml");
        Files.writeString(spec, validSpec(), charset);
        Path templateRoot = this.tempDir.resolve("custom-templates");
        Path templateGroup = Files.createDirectories(templateRoot.resolve("crud"));
        Files.writeString(templateGroup.resolve("manifest.properties"), """
                group=crud
                template.count=1
                template.0.source=summary.txt.ftl
                template.0.output=summary/${crud.resourceName}.txt
                """, charset);
        Files.writeString(templateGroup.resolve("summary.txt.ftl"),
                "${crud.basePackage}|${crud.apiPath}", charset);
        Path output = this.tempDir.resolve("custom-output");
        CocoGenerateMojo mojo = mojo(spec, output);
        set(mojo, "templateLocation", templateRoot.toString());
        set(mojo, "encoding", charset.name());

        mojo.execute();

        assertThat(Files.readString(output.resolve("summary/Product.txt"), charset))
                .isEqualTo("com.example.catalog|/products");
    }

    @Test
    void refusesExistingFilesBeforeWritingAnyOutput() throws Exception {
        Path spec = writeSpec("collision.yml", validSpec());
        Path output = this.tempDir.resolve("collision-output");
        Path existing = output.resolve(PRODUCT_FILES.get(0));
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "business-owned", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> mojo(spec, output).execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Coco CRUD source generation failed")
                .hasRootCauseMessage("generated file collision: [" + existing.toAbsolutePath() + "]");
        assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("business-owned");
        assertThat(output.resolve(PRODUCT_FILES.get(1))).doesNotExist();
    }

    @Test
    void overwritesExistingFilesOnlyWhenExplicitlyEnabled() throws Exception {
        Path spec = writeSpec("overwrite.yml", validSpec());
        Path output = this.tempDir.resolve("overwrite-output");
        Path existing = output.resolve(PRODUCT_FILES.get(0));
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "business-owned", StandardCharsets.UTF_8);
        CocoGenerateMojo mojo = mojo(spec, output);
        set(mojo, "overwrite", true);

        mojo.execute();

        assertThat(Files.readString(existing, StandardCharsets.UTF_8))
                .contains("public record Product(")
                .doesNotContain("business-owned");
        assertThat(relativeJavaFiles(output)).containsExactlyInAnyOrderElementsOf(PRODUCT_FILES);
    }

    @Test
    void rejectsInvalidYamlThroughTheMojoBoundary() throws Exception {
        Path spec = writeSpec("invalid.yml", validSpec().replace(
                "    table: catalog_product", "    unknown-table-key: catalog_product"));
        Path output = this.tempDir.resolve("invalid-output");

        assertThatThrownBy(() -> mojo(spec, output).execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Coco CRUD source generation failed")
                .hasMessageContaining("Invalid Coco codegen YAML '" + spec.toAbsolutePath())
                .hasMessageContaining("$.resources[0] contains unknown key 'unknown-table-key'");
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsResourceNamesThatConflictWithGeneratedTypes() throws Exception {
        Path spec = writeSpec("conflicting-resource-name.yml", validSpec().replace(
                "  - name: Product", "  - name: List"));
        Path output = this.tempDir.resolve("conflicting-resource-name-output");

        assertThatThrownBy(() -> mojo(spec, output).execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Coco CRUD source generation failed")
                .hasMessageContaining("List")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(output).doesNotExist();
    }

    private CocoGenerateMojo mojo(Path spec, Path output) throws Exception {
        CocoGenerateMojo mojo = new CocoGenerateMojo();
        set(mojo, "spec", spec.toFile());
        set(mojo, "outputDirectory", output.toFile());
        return mojo;
    }

    private Path writeSpec(String name, String content) throws Exception {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private List<String> relativeJavaFiles(Path output) throws Exception {
        try (Stream<Path> paths = Files.walk(output)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(output::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }

    private void compileGeneratedSources(Path sourceDirectory) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();
        Path classesDirectory = Files.createDirectories(this.tempDir.resolve("compiled-generated-sources"));
        List<String> arguments = new ArrayList<>();
        arguments.add("--release");
        arguments.add("17");
        arguments.add("-classpath");
        arguments.add(System.getProperty("java.class.path"));
        arguments.add("-d");
        arguments.add(classesDirectory.toString());
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toString)
                    .forEach(arguments::add);
        }
        ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();

        int result = compiler.run(null, compilerOutput, compilerOutput, arguments.toArray(String[]::new));

        assertThat(result)
                .withFailMessage(() -> compilerOutput.toString(StandardCharsets.UTF_8))
                .isZero();
    }

    private String validSpec() {
        return """
                base-package: com.example.catalog
                resources:
                  - name: Product
                    table: catalog_product
                    id:
                      name: id
                      column: id
                      type: Long
                      strategy: AUTO
                    fields:
                      - name: sku
                        column: sku
                        type: String
                        required: true
                      - name: name
                        column: name
                        type: String
                        required: true
                      - name: unitPrice
                        column: unit_price
                        type: BigDecimal
                        required: true
                """;
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
