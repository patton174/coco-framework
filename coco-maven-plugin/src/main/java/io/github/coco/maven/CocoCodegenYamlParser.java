package io.github.coco.maven;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class CocoCodegenYamlParser {

    private static final Set<String> ROOT_KEYS = Set.of("base-package", "resources");

    private static final Set<String> RESOURCE_KEYS = Set.of("name", "table", "api-path", "id", "fields");

    private static final Set<String> ID_KEYS = Set.of("name", "column", "type", "strategy");

    private static final Set<String> FIELD_KEYS = Set.of("name", "column", "type", "required");

    private static final Set<String> ID_STRATEGIES = Set.of(
            "AUTO", "NONE", "INPUT", "ASSIGN_ID", "ASSIGN_UUID");

    private static final Set<String> RECORD_COMPONENT_FORBIDDEN_NAMES = Set.of(
            "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    private static final Set<String> SIMPLE_JAVA_TYPES = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char",
            "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
            "String", "Object", "BigDecimal", "BigInteger", "LocalDate", "LocalTime", "LocalDateTime",
            "OffsetDateTime", "ZonedDateTime", "Instant", "Duration", "UUID", "Date");

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern API_PATH = Pattern.compile(
            "/[A-Za-z0-9][A-Za-z0-9_-]*(?:/[A-Za-z0-9][A-Za-z0-9_-]*)*");

    CocoCodegenYamlSpec parse(Path path, Charset charset) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, charset)) {
            Iterator<Object> documents = yaml().loadAll(reader).iterator();
            if (!documents.hasNext()) {
                throw invalid(path, "document must not be empty", null);
            }
            Object document = documents.next();
            if (documents.hasNext()) {
                throw invalid(path, "multiple YAML documents are not supported", null);
            }
            try {
                return parseDocument(document);
            }
            catch (IllegalArgumentException ex) {
                throw invalid(path, ex.getMessage(), ex);
            }
        }
        catch (YAMLException ex) {
            throw invalid(path, ex.getMessage(), ex);
        }
    }

    private CocoCodegenYamlSpec parseDocument(Object document) {
        Map<?, ?> root = mapping(document, "$", "mapping");
        validateKeys(root, ROOT_KEYS, "$");
        String basePackage = requiredText(root, "base-package", "$");
        validatePackage(basePackage, "$.base-package");

        List<?> resourceValues = requiredList(root, "resources", "$");
        requireNonEmpty(resourceValues, "$.resources");
        List<CocoCodegenYamlSpec.Resource> resources = new ArrayList<>();
        Set<String> resourceNames = new LinkedHashSet<>();
        for (int index = 0; index < resourceValues.size(); index++) {
            String location = "$.resources[" + index + "]";
            CocoCodegenYamlSpec.Resource resource = parseResource(resourceValues.get(index), location);
            String normalizedResourceName = normalizeResourceName(resource.name());
            if (!resourceNames.add(normalizedResourceName)) {
                throw validation(location + ".name duplicates resource '" + resource.name() + "'");
            }
            resources.add(resource);
        }
        return new CocoCodegenYamlSpec(basePackage, resources);
    }

    private CocoCodegenYamlSpec.Resource parseResource(Object value, String location) {
        Map<?, ?> resource = mapping(value, location, "mapping");
        validateKeys(resource, RESOURCE_KEYS, location);
        String name = requiredText(resource, "name", location);
        validateJavaIdentifier(name, location + ".name");
        if (!Character.isLetter(name.charAt(0))) {
            throw validation(location + ".name must start with a letter");
        }
        String table = requiredText(resource, "table", location);
        validateSqlIdentifier(table, location + ".table");
        String apiPath = optionalText(resource, "api-path", location);
        if (apiPath != null && !API_PATH.matcher(apiPath).matches()) {
            throw validation(location + ".api-path must be an absolute API path without query, fragment, or traversal");
        }

        CocoCodegenYamlSpec.Id id = parseId(requiredValue(resource, "id", location), location + ".id");
        List<?> fieldValues = requiredList(resource, "fields", location);
        requireNonEmpty(fieldValues, location + ".fields");
        List<CocoCodegenYamlSpec.Field> fields = new ArrayList<>();
        Set<String> fieldNames = new LinkedHashSet<>();
        Set<String> fieldColumns = new LinkedHashSet<>();
        for (int index = 0; index < fieldValues.size(); index++) {
            String fieldLocation = location + ".fields[" + index + "]";
            CocoCodegenYamlSpec.Field field = parseField(fieldValues.get(index), fieldLocation);
            if (!fieldNames.add(field.name())) {
                throw validation(fieldLocation + ".name duplicates field '" + field.name() + "'");
            }
            if (!fieldColumns.add(field.column())) {
                throw validation(fieldLocation + ".column duplicates column '" + field.column() + "'");
            }
            if (id.name().equals(field.name())) {
                throw validation(fieldLocation + ".name duplicates id field '" + id.name() + "'");
            }
            if (id.column().equals(field.column())) {
                throw validation(fieldLocation + ".column duplicates id column '" + id.column() + "'");
            }
            fields.add(field);
        }
        return new CocoCodegenYamlSpec.Resource(name, table, apiPath, id, fields);
    }

    private CocoCodegenYamlSpec.Id parseId(Object value, String location) {
        Map<?, ?> id = mapping(value, location, "mapping");
        validateKeys(id, ID_KEYS, location);
        String name = requiredText(id, "name", location);
        validateJavaIdentifier(name, location + ".name");
        validateRecordComponentName(name, location + ".name");
        String column = requiredText(id, "column", location);
        validateSqlIdentifier(column, location + ".column");
        String type = requiredText(id, "type", location);
        validateJavaType(type, location + ".type");
        String strategy = requiredText(id, "strategy", location);
        if (!ID_STRATEGIES.contains(strategy)) {
            throw validation(location + ".strategy has unsupported MyBatis-Plus IdType '" + strategy
                    + "'; expected one of " + ID_STRATEGIES);
        }
        return new CocoCodegenYamlSpec.Id(name, column, type, strategy);
    }

    private CocoCodegenYamlSpec.Field parseField(Object value, String location) {
        Map<?, ?> field = mapping(value, location, "mapping");
        validateKeys(field, FIELD_KEYS, location);
        String name = requiredText(field, "name", location);
        validateJavaIdentifier(name, location + ".name");
        validateRecordComponentName(name, location + ".name");
        String column = requiredText(field, "column", location);
        validateSqlIdentifier(column, location + ".column");
        String type = requiredText(field, "type", location);
        validateJavaType(type, location + ".type");
        boolean required = optionalBoolean(field, "required", location, false);
        return new CocoCodegenYamlSpec.Field(name, column, type, required);
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(20);
        return new Yaml(new SafeConstructor(options));
    }

    private static Map<?, ?> mapping(Object value, String location, String expectedType) {
        if (!(value instanceof Map<?, ?> mapping)) {
            throw validation(location + " must be a " + expectedType + ", but was " + typeName(value));
        }
        return mapping;
    }

    private static void validateKeys(Map<?, ?> mapping, Set<String> allowedKeys, String location) {
        for (Object key : mapping.keySet()) {
            if (!(key instanceof String name)) {
                throw validation(location + " contains a non-string key of type " + typeName(key));
            }
            if (!allowedKeys.contains(name)) {
                throw validation(location + " contains unknown key '" + name + "'; allowed keys are " + allowedKeys);
            }
        }
    }

    private static Object requiredValue(Map<?, ?> mapping, String key, String location) {
        if (!mapping.containsKey(key)) {
            throw validation(location + " is missing required key '" + key + "'");
        }
        Object value = mapping.get(key);
        if (value == null) {
            throw validation(location + "." + key + " must not be null");
        }
        return value;
    }

    private static String requiredText(Map<?, ?> mapping, String key, String location) {
        Object value = requiredValue(mapping, key, location);
        if (!(value instanceof String text)) {
            throw validation(location + "." + key + " must be a string, but was " + typeName(value));
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw validation(location + "." + key + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(Map<?, ?> mapping, String key, String location) {
        if (!mapping.containsKey(key)) {
            return null;
        }
        return requiredText(mapping, key, location);
    }

    private static List<?> requiredList(Map<?, ?> mapping, String key, String location) {
        Object value = requiredValue(mapping, key, location);
        if (!(value instanceof List<?> list)) {
            throw validation(location + "." + key + " must be a list, but was " + typeName(value));
        }
        return list;
    }

    private static boolean optionalBoolean(Map<?, ?> mapping, String key, String location, boolean defaultValue) {
        if (!mapping.containsKey(key)) {
            return defaultValue;
        }
        Object value = mapping.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            throw validation(location + "." + key + " must be a boolean, but was " + typeName(value));
        }
        return booleanValue;
    }

    private static void requireNonEmpty(List<?> values, String location) {
        if (values.isEmpty()) {
            throw validation(location + " must not be empty");
        }
    }

    private static void validatePackage(String value, String location) {
        String[] segments = value.split("\\.", -1);
        for (String segment : segments) {
            validateJavaIdentifier(segment, location);
        }
    }

    private static String normalizeResourceName(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void validateJavaIdentifier(String value, String location) {
        if (!SourceVersion.isIdentifier(value) || SourceVersion.isKeyword(value)) {
            throw validation(location + " must be a valid Java identifier, but was '" + value + "'");
        }
    }

    private static void validateRecordComponentName(String value, String location) {
        if (RECORD_COMPONENT_FORBIDDEN_NAMES.contains(value)) {
            throw validation(location + " must not conflict with a java.lang.Object method used by generated records: '"
                    + value + "'");
        }
    }

    private static void validateSqlIdentifier(String value, String location) {
        if (!SQL_IDENTIFIER.matcher(value).matches()) {
            throw validation(location + " must contain only a safe unquoted SQL identifier, but was '" + value + "'");
        }
    }

    private static void validateJavaType(String value, String location) {
        if (SIMPLE_JAVA_TYPES.contains(value)) {
            return;
        }
        String[] segments = value.split("\\.", -1);
        if (segments.length < 2) {
            throw validation(location + " has unsupported simple Java type '" + value + "'");
        }
        for (String segment : segments) {
            validateJavaIdentifier(segment, location);
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static IllegalArgumentException validation(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(Path path, String message, Throwable cause) {
        return new IllegalArgumentException("Invalid Coco codegen YAML '" + path + "': " + message, cause);
    }
}
