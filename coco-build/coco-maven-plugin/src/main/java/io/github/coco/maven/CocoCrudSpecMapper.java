package io.github.coco.maven;

import java.util.ArrayList;
import java.util.List;

import io.github.coco.feature.codegen.crud.CocoCrudIdStrategy;
import io.github.coco.feature.codegen.crud.CocoCrudSpec;

final class CocoCrudSpecMapper {

    List<CocoCrudSpec> map(CocoCodegenYamlSpec source) {
        List<CocoCrudSpec> specifications = new ArrayList<>(source.resources().size());
        for (CocoCodegenYamlSpec.Resource resource : source.resources()) {
            CocoCrudSpec.Builder builder = CocoCrudSpec.builder(
                    source.basePackage(), resource.name(), resource.table());
            if (resource.apiPath() != null) {
                builder.apiPath(resource.apiPath());
            }
            CocoCodegenYamlSpec.Id id = resource.id();
            builder.id(id.name(), id.column(), id.type(), CocoCrudIdStrategy.valueOf(id.strategy()));
            for (CocoCodegenYamlSpec.Field field : resource.fields()) {
                builder.field(field.name(), field.column(), field.type(), field.required());
            }
            specifications.add(builder.build());
        }
        return List.copyOf(specifications);
    }
}
