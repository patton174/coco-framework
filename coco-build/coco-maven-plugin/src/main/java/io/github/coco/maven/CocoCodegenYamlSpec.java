package io.github.coco.maven;

import java.util.List;

record CocoCodegenYamlSpec(String basePackage, List<Resource> resources) {

    CocoCodegenYamlSpec {
        resources = List.copyOf(resources);
    }

    record Resource(String name, String table, String apiPath, Id id, List<Field> fields) {

        Resource {
            fields = List.copyOf(fields);
        }
    }

    record Id(String name, String column, String type, String strategy) {
    }

    record Field(String name, String column, String type, boolean required) {
    }
}
