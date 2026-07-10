package io.github.coco.feature.codegen.crud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.github.coco.feature.codegen.core.CocoCodegenRequest;

/**
 * Coco 默认 CRUD 源码生成规格。
 * <p>
 * 该规格只描述一个业务资源，并负责在进入模板引擎前归一化包名、资源名、数据表、字段和 Java 类型。
 * 生成结果是业务项目可继续维护的普通源码，不会注册运行时动态 CRUD 行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoCrudSpec {

    /** 内置 CRUD 模板组名称。 */
    public static final String TEMPLATE_GROUP = "crud";

    /** 内置 CRUD 模板模型属性名称。 */
    public static final String MODEL_ATTRIBUTE = "crud";

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern API_PATH = Pattern.compile("/(?:[A-Za-z0-9][A-Za-z0-9_-]*)(?:/[A-Za-z0-9][A-Za-z0-9_-]*)*");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null", "_", "record",
            "sealed", "permits", "non-sealed", "var", "yield");

    private static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "long", "int", "short", "byte", "boolean", "double", "float", "char");

    private static final Set<String> FORBIDDEN_RECORD_COMPONENT_NAMES = Set.of(
            "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    // Keep this list aligned with simple type names referenced by the built-in CRUD templates.
    private static final Set<String> TEMPLATE_RESERVED_TYPE_NAMES = Set.of(
            "BaseMapper", "CocoCommonErrorCode", "DeleteMapping", "GetMapping", "HttpStatus", "IdType",
            "IPage", "List", "Mapper", "NotBlank", "NotNull", "Optional", "Override", "Page", "PageResult",
            "PathVariable", "PostMapping", "PutMapping", "Repository", "RequestBody", "RequestMapping",
            "RequestParam", "ResponseStatus", "RestController", "Service", "TableField", "TableId", "TableName",
            "Transactional", "Valid");

    private static final Map<String, String> COMMON_TYPE_NAMES = commonTypeNames();

    private final String basePackage;

    private final String resourceName;

    private final String resourceVariable;

    private final String tableName;

    private final String apiPath;

    private final FieldSpec id;

    private final List<FieldSpec> fields;

    private CocoCrudSpec(Builder builder) {
        this.basePackage = requirePackage(builder.basePackage);
        this.resourceName = normalizeResourceName(builder.resourceName);
        validateResourceTypeNames(this.resourceName);
        this.resourceVariable = decapitalize(this.resourceName);
        this.tableName = requireSqlIdentifier(builder.tableName, "tableName");
        this.apiPath = builder.apiPath == null ? defaultApiPath(this.resourceName) : requireApiPath(builder.apiPath);
        this.id = Objects.requireNonNull(builder.id, "id must be configured");
        if (this.id.javaType.isPrimitive()) {
            throw new IllegalArgumentException("id javaType must not be primitive");
        }
        if (builder.fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        validateFields(this.id, builder.fields);
        this.fields = List.copyOf(builder.fields);
        validateTypeImports(this.id, this.fields);
    }

    /**
     * <p>
     * 创建 CRUD 规格构建器。
     * </p>
     * @param basePackage 业务基础包名
     * @param resourceName 资源类名
     * @param tableName 数据表名
     * @return CRUD 规格构建器
     */
    public static Builder builder(String basePackage, String resourceName, String tableName) {
        return new Builder(basePackage, resourceName, tableName);
    }

    /**
     * <p>
     * 返回归一化后的业务基础包名。
     * </p>
     * @return 业务基础包名
     */
    public String basePackage() {
        return this.basePackage;
    }

    /**
     * <p>
     * 返回归一化后的资源类名。
     * </p>
     * @return 资源类名
     */
    public String resourceName() {
        return this.resourceName;
    }

    /**
     * <p>
     * 返回数据表名。
     * </p>
     * @return 数据表名
     */
    public String tableName() {
        return this.tableName;
    }

    /**
     * <p>
     * 返回 REST API 路径。
     * </p>
     * @return REST API 路径
     */
    public String apiPath() {
        return this.apiPath;
    }

    /**
     * <p>
     * 将当前规格转换为内置 {@code crud} 模板组请求。
     * </p>
     * @return 代码生成请求
     */
    public CocoCodegenRequest toRequest() {
        return CocoCodegenRequest.builder(TEMPLATE_GROUP)
                .targetPackage(this.basePackage)
                .attribute(MODEL_ATTRIBUTE, templateModel())
                .build();
    }

    private Map<String, Object> templateModel() {
        Set<String> conflictingTypeNames = conflictingTypeNames(this.resourceName);
        List<Map<String, Object>> fieldModels = this.fields.stream()
                .map(field -> field.toTemplateModel(conflictingTypeNames))
                .toList();
        List<Map<String, Object>> allFields = new ArrayList<>();
        allFields.add(this.id.toTemplateModel(conflictingTypeNames));
        allFields.addAll(fieldModels);

        Set<String> imports = new TreeSet<>();
        addImport(imports, this.id.javaType.importName(conflictingTypeNames));
        this.fields.forEach(field -> addImport(imports, field.javaType.importName(conflictingTypeNames)));
        Set<String> idImports = new TreeSet<>();
        addImport(idImports, this.id.javaType.importName(conflictingTypeNames));
        Set<String> fieldImports = new TreeSet<>();
        this.fields.forEach(field -> addImport(fieldImports, field.javaType.importName(conflictingTypeNames)));
        Set<String> createImports = new TreeSet<>(fieldImports);
        if (this.id.strategy == CocoCrudIdStrategy.INPUT) {
            createImports.addAll(idImports);
        }

        Map<String, Object> idModel = new LinkedHashMap<>(this.id.toTemplateModel(conflictingTypeNames));
        idModel.put("strategy", this.id.strategy.name());
        idModel.put("input", this.id.strategy == CocoCrudIdStrategy.INPUT);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("basePackage", this.basePackage);
        model.put("basePackagePath", this.basePackage.replace('.', '/'));
        model.put("resourceName", this.resourceName);
        model.put("resourceVariable", this.resourceVariable);
        model.put("resourcePackage", this.resourceVariable.toLowerCase(Locale.ROOT));
        model.put("tableName", this.tableName);
        model.put("apiPath", this.apiPath);
        model.put("id", Collections.unmodifiableMap(idModel));
        model.put("fields", fieldModels);
        model.put("allFields", List.copyOf(allFields));
        model.put("typeImports", List.copyOf(imports));
        model.put("idTypeImports", List.copyOf(idImports));
        model.put("fieldTypeImports", List.copyOf(fieldImports));
        model.put("createTypeImports", List.copyOf(createImports));
        model.put("createNeedsNotBlank", this.fields.stream()
                .anyMatch(field -> field.required && field.javaType.isString()));
        model.put("createNeedsNotNull", this.id.strategy == CocoCrudIdStrategy.INPUT
                || this.fields.stream().anyMatch(field -> field.required
                        && !field.javaType.isPrimitive()
                        && !field.javaType.isString()));
        model.put("updateNeedsNotBlank", this.fields.stream()
                .anyMatch(field -> field.required && field.javaType.isString()));
        model.put("updateNeedsNotNull", this.fields.stream().anyMatch(field -> field.required
                && !field.javaType.isPrimitive()
                && !field.javaType.isString()));
        return Collections.unmodifiableMap(model);
    }

    private static void addImport(Set<String> imports, String importName) {
        if (importName != null) {
            imports.add(importName);
        }
    }

    private static void validateFields(FieldSpec id, List<FieldSpec> fields) {
        Set<String> names = new LinkedHashSet<>();
        Set<String> columns = new LinkedHashSet<>();
        names.add(id.name);
        columns.add(id.columnName);
        for (FieldSpec field : fields) {
            if (!names.add(field.name)) {
                throw new IllegalArgumentException("duplicate field name: " + field.name);
            }
            if (!columns.add(field.columnName)) {
                throw new IllegalArgumentException("duplicate field column: " + field.columnName);
            }
        }
    }

    private static void validateTypeImports(FieldSpec id, List<FieldSpec> fields) {
        Map<String, String> importsBySimpleName = new LinkedHashMap<>();
        List<FieldSpec> allFields = new ArrayList<>();
        allFields.add(id);
        allFields.addAll(fields);
        for (FieldSpec field : allFields) {
            String typeName = field.javaType.qualifiedName == null
                    ? field.javaType.simpleName
                    : field.javaType.qualifiedName;
            String previous = importsBySimpleName.putIfAbsent(field.javaType.simpleName, typeName);
            if (previous != null && !previous.equals(typeName)) {
                throw new IllegalArgumentException("conflicting Java type imports for " + field.javaType.simpleName);
            }
        }
    }

    private static void validateResourceTypeNames(String resourceName) {
        for (String generatedTypeName : generatedTypeNames(resourceName)) {
            if (TEMPLATE_RESERVED_TYPE_NAMES.contains(generatedTypeName)) {
                throw new IllegalArgumentException(
                        "resourceName conflicts with CRUD template type: " + generatedTypeName);
            }
        }
    }

    private static Set<String> conflictingTypeNames(String resourceName) {
        Set<String> conflicts = new LinkedHashSet<>(TEMPLATE_RESERVED_TYPE_NAMES);
        conflicts.addAll(generatedTypeNames(resourceName));
        return conflicts;
    }

    private static List<String> generatedTypeNames(String resourceName) {
        return List.of(
                resourceName,
                resourceName + "Repository",
                resourceName + "ApplicationService",
                resourceName + "Entity",
                resourceName + "Mapper",
                "MybatisPlus" + resourceName + "Repository",
                resourceName + "Controller",
                "Create" + resourceName + "Request",
                "Update" + resourceName + "Request",
                resourceName + "Response");
    }

    private static String requirePackage(String value) {
        String normalized = requireText(value, "basePackage");
        String[] segments = normalized.split("\\.", -1);
        if (segments.length == 0) {
            throw new IllegalArgumentException("basePackage must be a valid Java package");
        }
        for (String segment : segments) {
            requireJavaIdentifier(segment, "basePackage");
        }
        return normalized;
    }

    private static String normalizeResourceName(String value) {
        String normalized = requireJavaIdentifier(requireText(value, "resourceName"), "resourceName");
        if (!Character.isLetter(normalized.charAt(0))) {
            throw new IllegalArgumentException("resourceName must start with a letter");
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String requireJavaIdentifier(String value, String name) {
        String normalized = requireText(value, name);
        if (JAVA_KEYWORDS.contains(normalized) || !Character.isJavaIdentifierStart(normalized.charAt(0))) {
            throw new IllegalArgumentException(name + " must be a valid Java identifier: " + normalized);
        }
        for (int index = 1; index < normalized.length(); index++) {
            if (!Character.isJavaIdentifierPart(normalized.charAt(index))) {
                throw new IllegalArgumentException(name + " must be a valid Java identifier: " + normalized);
            }
        }
        return normalized;
    }

    private static String requireRecordComponentName(String value) {
        String normalized = requireJavaIdentifier(value, "fieldName");
        if (FORBIDDEN_RECORD_COMPONENT_NAMES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "fieldName must not conflict with a java.lang.Object method in a record: " + normalized);
        }
        return normalized;
    }

    private static String requireSqlIdentifier(String value, String name) {
        String normalized = requireText(value, name);
        if (!SQL_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a safe SQL identifier: " + normalized);
        }
        return normalized;
    }

    private static String requireApiPath(String value) {
        String normalized = requireText(value, "apiPath");
        if (!API_PATH.matcher(normalized).matches()) {
            throw new IllegalArgumentException("apiPath must be an absolute path with safe segments: " + normalized);
        }
        return normalized;
    }

    private static String defaultApiPath(String resourceName) {
        String kebab = toKebabCase(resourceName);
        return "/" + pluralize(kebab);
    }

    private static String toKebabCase(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current) && index > 0
                    && (Character.isLowerCase(value.charAt(index - 1))
                    || (index + 1 < value.length() && Character.isLowerCase(value.charAt(index + 1))))) {
                result.append('-');
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }

    private static String pluralize(String value) {
        if (value.endsWith("y") && value.length() > 1 && !isVowel(value.charAt(value.length() - 2))) {
            return value.substring(0, value.length() - 1) + "ies";
        }
        if (value.endsWith("s") || value.endsWith("x") || value.endsWith("z")
                || value.endsWith("ch") || value.endsWith("sh")) {
            return value + "es";
        }
        return value + "s";
    }

    private static boolean isVowel(char value) {
        return value == 'a' || value == 'e' || value == 'i' || value == 'o' || value == 'u';
    }

    private static JavaType requireJavaType(String value) {
        String normalized = requireText(value, "javaType");
        if (normalized.indexOf('.') < 0) {
            if (!COMMON_TYPE_NAMES.containsKey(normalized)) {
                throw new IllegalArgumentException("unsupported simple Java type: " + normalized);
            }
            String qualifiedName = COMMON_TYPE_NAMES.get(normalized);
            return new JavaType(normalized, qualifiedName.isEmpty() ? null : qualifiedName);
        }

        String[] segments = normalized.split("\\.", -1);
        if (segments.length < 2) {
            throw new IllegalArgumentException("javaType must be a simple or fully qualified class name: " + normalized);
        }
        for (String segment : segments) {
            requireJavaIdentifier(segment, "javaType");
        }
        return new JavaType(segments[segments.length - 1], normalized);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<String, String> commonTypeNames() {
        Map<String, String> typeNames = new LinkedHashMap<>();
        for (String javaLangType : List.of("String", "Long", "Integer", "Short", "Byte", "Boolean", "Double",
                "Float", "Character", "Object")) {
            typeNames.put(javaLangType, "java.lang." + javaLangType);
        }
        PRIMITIVE_TYPE_NAMES.forEach(primitive -> typeNames.put(primitive, ""));
        typeNames.put("BigDecimal", "java.math.BigDecimal");
        typeNames.put("BigInteger", "java.math.BigInteger");
        typeNames.put("UUID", "java.util.UUID");
        typeNames.put("Date", "java.util.Date");
        typeNames.put("Instant", "java.time.Instant");
        typeNames.put("LocalDate", "java.time.LocalDate");
        typeNames.put("LocalDateTime", "java.time.LocalDateTime");
        typeNames.put("LocalTime", "java.time.LocalTime");
        typeNames.put("OffsetDateTime", "java.time.OffsetDateTime");
        typeNames.put("ZonedDateTime", "java.time.ZonedDateTime");
        typeNames.put("Duration", "java.time.Duration");
        return Collections.unmodifiableMap(typeNames);
    }

    /**
     * Coco CRUD 规格构建器。
     * <p>
     * 构建器保持输入顺序，使模板输出和测试结果稳定。
     * </p>
     */
    public static final class Builder {

        private final String basePackage;

        private final String resourceName;

        private final String tableName;

        private String apiPath;

        private FieldSpec id;

        private final List<FieldSpec> fields = new ArrayList<>();

        private Builder(String basePackage, String resourceName, String tableName) {
            this.basePackage = basePackage;
            this.resourceName = resourceName;
            this.tableName = tableName;
        }

        /**
         * <p>
         * 设置 REST API 路径。
         * </p>
         * @param apiPath REST API 路径
         * @return 当前构建器
         */
        public Builder apiPath(String apiPath) {
            this.apiPath = apiPath;
            return this;
        }

        /**
         * <p>
         * 设置主键规格。
         * </p>
         * @param name Java 字段名
         * @param columnName 数据库列名
         * @param javaType Java 类型
         * @param strategy 主键策略
         * @return 当前构建器
         */
        public Builder id(String name, String columnName, String javaType, CocoCrudIdStrategy strategy) {
            if (this.id != null) {
                throw new IllegalStateException("id must not be configured more than once");
            }
            this.id = new FieldSpec(name, columnName, javaType, true,
                    Objects.requireNonNull(strategy, "strategy must not be null"));
            return this;
        }

        /**
         * <p>
         * 使用 Java 类设置主键规格。
         * </p>
         * @param name Java 字段名
         * @param columnName 数据库列名
         * @param javaType Java 类
         * @param strategy 主键策略
         * @return 当前构建器
         */
        public Builder id(String name, String columnName, Class<?> javaType, CocoCrudIdStrategy strategy) {
            return id(name, columnName, className(javaType), strategy);
        }

        /**
         * <p>
         * 添加普通字段规格。
         * </p>
         * @param name Java 字段名
         * @param columnName 数据库列名
         * @param javaType Java 类型
         * @param required 是否必填
         * @return 当前构建器
         */
        public Builder field(String name, String columnName, String javaType, boolean required) {
            this.fields.add(new FieldSpec(name, columnName, javaType, required, null));
            return this;
        }

        /**
         * <p>
         * 使用 Java 类添加普通字段规格。
         * </p>
         * @param name Java 字段名
         * @param columnName 数据库列名
         * @param javaType Java 类
         * @param required 是否必填
         * @return 当前构建器
         */
        public Builder field(String name, String columnName, Class<?> javaType, boolean required) {
            return field(name, columnName, className(javaType), required);
        }

        /**
         * <p>
         * 构建并校验 CRUD 规格。
         * </p>
         * @return CRUD 规格
         */
        public CocoCrudSpec build() {
            return new CocoCrudSpec(this);
        }

        private static String className(Class<?> javaType) {
            Class<?> checkedType = Objects.requireNonNull(javaType, "javaType must not be null");
            if (checkedType.isArray() || checkedType.isAnonymousClass() || checkedType.isLocalClass()) {
                throw new IllegalArgumentException("javaType must be a named non-array type");
            }
            return checkedType.getCanonicalName();
        }
    }

    private static final class FieldSpec {

        private final String name;

        private final String columnName;

        private final JavaType javaType;

        private final boolean required;

        private final CocoCrudIdStrategy strategy;

        private FieldSpec(String name, String columnName, String javaType, boolean required,
                CocoCrudIdStrategy strategy) {
            this.name = requireRecordComponentName(name);
            this.columnName = requireSqlIdentifier(columnName, "columnName");
            this.javaType = requireJavaType(javaType);
            this.required = required;
            this.strategy = strategy;
        }

        private Map<String, Object> toTemplateModel(Set<String> conflictingTypeNames) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", this.name);
            model.put("columnName", this.columnName);
            model.put("javaType", this.javaType.declaration(conflictingTypeNames));
            model.put("required", this.required);
            model.put("stringType", this.javaType.isString());
            model.put("primitive", this.javaType.isPrimitive());
            return Collections.unmodifiableMap(model);
        }
    }

    private record JavaType(String simpleName, String qualifiedName) {

        private String declaration(Set<String> conflictingTypeNames) {
            return this.qualifiedName != null && conflictingTypeNames.contains(this.simpleName)
                    ? this.qualifiedName
                    : this.simpleName;
        }

        private String importName(Set<String> conflictingTypeNames) {
            if (this.qualifiedName == null || this.qualifiedName.startsWith("java.lang.")
                    || conflictingTypeNames.contains(this.simpleName)) {
                return null;
            }
            return this.qualifiedName;
        }

        private boolean isPrimitive() {
            return PRIMITIVE_TYPE_NAMES.contains(this.simpleName);
        }

        private boolean isString() {
            return "java.lang.String".equals(this.qualifiedName);
        }
    }
}
