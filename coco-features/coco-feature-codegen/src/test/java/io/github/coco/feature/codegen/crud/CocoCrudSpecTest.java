package io.github.coco.feature.codegen.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.github.coco.feature.codegen.core.CocoCodegenRequest;
import org.junit.jupiter.api.Test;

class CocoCrudSpecTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildsNormalizedCrudRequestModel() {
        CocoCrudSpec spec = CocoCrudSpec.builder(" com.example.catalog ", "product", "catalog_product")
                .id("id", "id", Long.class, CocoCrudIdStrategy.AUTO)
                .field("sku", "sku", String.class, true)
                .field("unitPrice", "unit_price", BigDecimal.class, true)
                .build();

        CocoCodegenRequest request = spec.toRequest();
        Map<String, Object> model = (Map<String, Object>) request.attributes().get(CocoCrudSpec.MODEL_ATTRIBUTE);

        assertThat(spec.basePackage()).isEqualTo("com.example.catalog");
        assertThat(spec.resourceName()).isEqualTo("Product");
        assertThat(spec.apiPath()).isEqualTo("/products");
        assertThat(request.templateGroup()).isEqualTo("crud");
        assertThat(request.targetPackage()).isEqualTo("com.example.catalog");
        assertThat(model)
                .containsEntry("basePackagePath", "com/example/catalog")
                .containsEntry("resourcePackage", "product")
                .containsEntry("tableName", "catalog_product")
                .containsEntry("apiPath", "/products");
        assertThat((Iterable<String>) model.get("typeImports"))
                .containsExactly("java.math.BigDecimal");
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportsExplicitApiPathAndInputId() {
        CocoCodegenRequest request = CocoCrudSpec.builder("com.example", "ExternalOrder", "external_order")
                .apiPath("/v1/external-orders")
                .id("orderId", "order_id", "java.util.UUID", CocoCrudIdStrategy.INPUT)
                .field("name", "name", "String", true)
                .build()
                .toRequest();

        Map<String, Object> model = (Map<String, Object>) request.attributes().get("crud");
        Map<String, Object> id = (Map<String, Object>) model.get("id");

        assertThat(model).containsEntry("apiPath", "/v1/external-orders");
        assertThat(id).containsEntry("strategy", "INPUT").containsEntry("input", true);
        assertThat((Iterable<String>) model.get("createTypeImports"))
                .containsExactly("java.util.UUID");
    }

    @Test
    @SuppressWarnings("unchecked")
    void qualifiesFqcnTypesThatConflictWithBuiltInTemplateTypes() {
        CocoCodegenRequest request = CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("items", "items", "java.awt.List", false)
                .build()
                .toRequest();

        Map<String, Object> model = (Map<String, Object>) request.attributes().get(CocoCrudSpec.MODEL_ATTRIBUTE);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");

        assertThat(fields).singleElement()
                .satisfies(field -> assertThat(field).containsEntry("javaType", "java.awt.List"));
        assertThat((Iterable<String>) model.get("typeImports")).doesNotContain("java.awt.List");
    }

    @Test
    void rejectsResourceNamesThatConflictWithTemplateTypes() {
        assertThatThrownBy(() -> validSpecBuilder("Rest").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RestController");

        assertThatThrownBy(() -> validSpecBuilder("PageResult").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PageResult");
    }

    @Test
    void rejectsObjectMethodNamesAsRecordComponents() {
        for (String fieldName : List.of(
                "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait")) {
            assertThatThrownBy(() -> validSpecBuilder("Product")
                    .field(fieldName, fieldName, "String", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("record")
                    .hasMessageContaining(fieldName);
        }
    }

    @Test
    void rejectsInvalidOrAmbiguousSpecifications() {
        assertThatThrownBy(() -> CocoCrudSpec.builder("bad-package", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basePackage");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product;drop")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .apiPath("products?all=true")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiPath");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("id", "external_id", "String", true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate field name");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true)
                .field("displayName", "name", "String", false)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate field column");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be primitive");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("metadata", "metadata", "Map<String, String>", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported simple Java type");
    }

    @Test
    void requiresExactlyOneIdAndAtLeastOneField() {
        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .field("name", "name", "String", true)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must be configured");

        assertThatThrownBy(() -> CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fields must not be empty");

        CocoCrudSpec.Builder builder = CocoCrudSpec.builder("com.example", "Product", "product")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO);
        assertThatThrownBy(() -> builder.id("otherId", "other_id", "Long", CocoCrudIdStrategy.INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than once");
    }

    private static CocoCrudSpec.Builder validSpecBuilder(String resourceName) {
        return CocoCrudSpec.builder("com.example", resourceName, "sample")
                .id("id", "id", "Long", CocoCrudIdStrategy.AUTO)
                .field("name", "name", "String", true);
    }
}
