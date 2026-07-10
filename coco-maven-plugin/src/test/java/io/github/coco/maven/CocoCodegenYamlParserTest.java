package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coco 代码生成 YAML 解析器测试。
 * <p>
 * 验证显式代码生成入口会严格解析结构、类型和重复声明，不会静默接受拼写错误。
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
class CocoCodegenYamlParserTest {

    private static final Set<String> RECORD_COMPONENT_FORBIDDEN_NAMES = Set.of(
            "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    @TempDir
    Path tempDir;

    @Test
    void parsesStrictCrudSpecification() throws Exception {
        Path specPath = write("valid.yml", """
                base-package: com.example.catalog
                resources:
                  - name: Product
                    table: catalog_product
                    api-path: /products
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
                      - name: unitPrice
                        column: unit_price
                        type: java.math.BigDecimal
                """);

        CocoCodegenYamlSpec spec = new CocoCodegenYamlParser().parse(specPath, StandardCharsets.UTF_8);

        assertThat(spec.basePackage()).isEqualTo("com.example.catalog");
        assertThat(spec.resources()).singleElement().satisfies(resource -> {
            assertThat(resource.name()).isEqualTo("Product");
            assertThat(resource.table()).isEqualTo("catalog_product");
            assertThat(resource.apiPath()).isEqualTo("/products");
            assertThat(resource.id()).isEqualTo(new CocoCodegenYamlSpec.Id("id", "id", "Long", "AUTO"));
            assertThat(resource.fields())
                    .containsExactly(
                            new CocoCodegenYamlSpec.Field("sku", "sku", "String", true),
                            new CocoCodegenYamlSpec.Field(
                                    "unitPrice", "unit_price", "java.math.BigDecimal", false));
        });
    }

    @Test
    void rejectsUnknownKeysAndWrongScalarTypes() throws Exception {
        Path unknownKey = write("unknown-key.yml", validSpec().replace(
                "    table: catalog_product", "    tabel: catalog_product"));
        Path wrongType = write("wrong-type.yml", validSpec().replace(
                "        required: true", "        required: \"true\""));

        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(unknownKey, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(unknownKey.toString())
                .hasMessageContaining("unknown key 'tabel'");
        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(wrongType, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$.resources[0].fields[0].required must be a boolean");
    }

    @Test
    void rejectsYamlDuplicateKeysAndDuplicateFields() throws Exception {
        Path duplicateKey = write("duplicate-key.yml", validSpec().replace(
                "    table: catalog_product", "    table: catalog_product\n    table: catalog_product_copy"));
        Path duplicateField = write("duplicate-field.yml", """
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
                      - name: sku
                        column: alternate_sku
                        type: String
                """);

        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(duplicateKey, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(duplicateKey.toString())
                .hasMessageContaining("duplicate key");
        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(duplicateField, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates field 'sku'");
    }

    @Test
    void rejectsMissingValuesAndEmptyCollections() throws Exception {
        Path missingId = write("missing-id.yml", """
                base-package: com.example.catalog
                resources:
                  - name: Product
                    table: catalog_product
                    fields:
                      - name: sku
                        column: sku
                        type: String
                """);
        Path emptyFields = write("empty-fields.yml", """
                base-package: com.example.catalog
                resources:
                  - name: Product
                    table: catalog_product
                    id:
                      name: id
                      column: id
                      type: Long
                      strategy: AUTO
                    fields: []
                """);

        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(missingId, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required key 'id'");
        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(emptyFields, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$.resources[0].fields must not be empty");
    }

    @Test
    void rejectsResourcesThatNormalizeToTheSameName() throws Exception {
        Path duplicateResources = write("duplicate-resources.yml", """
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
                  - name: product
                    table: archived_product
                    id:
                      name: id
                      column: id
                      type: Long
                      strategy: AUTO
                    fields:
                      - name: legacyCode
                        column: legacy_code
                        type: String
                """);

        assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(duplicateResources, StandardCharsets.UTF_8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$.resources[1].name duplicates resource 'product'");
    }

    @Test
    void rejectsJava17ForbiddenRecordComponentNames() throws Exception {
        for (String forbiddenName : RECORD_COMPONENT_FORBIDDEN_NAMES) {
            Path invalidId = write("invalid-id-" + forbiddenName + ".yml", validSpec().replace(
                    "      name: id", "      name: " + forbiddenName));
            Path invalidField = write("invalid-field-" + forbiddenName + ".yml", validSpec().replace(
                    "      - name: sku", "      - name: " + forbiddenName));

            assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(invalidId, StandardCharsets.UTF_8))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("$.resources[0].id.name")
                    .hasMessageContaining("must not conflict with a java.lang.Object method")
                    .hasMessageContaining("'" + forbiddenName + "'");
            assertThatThrownBy(() -> new CocoCodegenYamlParser().parse(invalidField, StandardCharsets.UTF_8))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("$.resources[0].fields[0].name")
                    .hasMessageContaining("must not conflict with a java.lang.Object method")
                    .hasMessageContaining("'" + forbiddenName + "'");
        }
    }

    private Path write(String name, String content) throws Exception {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private String validSpec() {
        return """
                base-package: com.example.catalog
                resources:
                  - name: Product
                    table: catalog_product
                    api-path: /products
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
                """;
    }
}
