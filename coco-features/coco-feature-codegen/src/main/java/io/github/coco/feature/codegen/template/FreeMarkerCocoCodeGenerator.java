package io.github.coco.feature.codegen.template;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freemarker.cache.TemplateLoader;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.coco.feature.codegen.core.CocoCodeGenerator;
import io.github.coco.feature.codegen.core.CocoCodegenException;
import io.github.coco.feature.codegen.core.CocoCodegenRequest;
import io.github.coco.feature.codegen.core.CocoCodegenResult;
import io.github.coco.feature.codegen.core.CocoGeneratedFile;
import io.github.coco.feature.codegen.internal.CocoGeneratedPathValidator;

/**
 * 基于 FreeMarker 的 Coco 模板代码生成器。
 * <p>
 * 模板根目录中的每个模板组通过 {@code <group>/manifest.properties} 声明模板资源和输出路径。
 * 生成器只返回内存文件结果，不会隐式创建目录或写入磁盘。
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
public final class FreeMarkerCocoCodeGenerator implements CocoCodeGenerator {

    /** 框架为模板上下文保留的属性。 */
    public static final Set<String> RESERVED_ATTRIBUTES = Set.of("_coco", "templateGroup", "targetPackage");

    private static final Pattern TEMPLATE_GROUP = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    private static final Pattern TEMPLATE_KEY = Pattern.compile("template\\.(\\d+)\\.(source|output)");

    private final String templateLocation;

    private final Charset encoding;

    private final TemplateResourceRoot resourceRoot;

    private final Configuration configuration;

    /**
     * <p>
     * 使用 UTF-8 创建模板生成器。
     * </p>
     * @param templateLocation 模板根位置，支持 {@code classpath:}、{@code file:} 和普通文件路径
     */
    public FreeMarkerCocoCodeGenerator(String templateLocation) {
        this(templateLocation, StandardCharsets.UTF_8);
    }

    /**
     * <p>
     * 使用指定编码名称创建模板生成器。
     * </p>
     * @param templateLocation 模板根位置
     * @param encoding 模板编码名称
     */
    public FreeMarkerCocoCodeGenerator(String templateLocation, String encoding) {
        this(templateLocation, requireCharset(encoding));
    }

    /**
     * <p>
     * 使用指定字符集创建模板生成器。
     * </p>
     * @param templateLocation 模板根位置
     * @param encoding 模板字符集
     */
    public FreeMarkerCocoCodeGenerator(String templateLocation, Charset encoding) {
        this.templateLocation = requireText(templateLocation, "templateLocation");
        this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
        this.resourceRoot = TemplateResourceRoot.create(this.templateLocation);
        this.configuration = createConfiguration(this.resourceRoot, this.encoding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoCodegenResult generate(CocoCodegenRequest request) {
        CocoCodegenRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String group = requireTemplateGroup(checkedRequest.templateGroup());
        validateReservedAttributes(checkedRequest.attributes());
        TemplateManifest manifest = loadManifest(group);
        Map<String, Object> model = createModel(checkedRequest);

        List<CocoGeneratedFile> files = new ArrayList<>(manifest.templates.size());
        Set<String> outputPaths = new LinkedHashSet<>();
        for (TemplateEntry entry : manifest.templates) {
            String outputPath = renderOutputPath(group, entry, model);
            if (!outputPaths.add(outputPath)) {
                throw new CocoCodegenException("template group '" + group + "' produces duplicate output: " + outputPath);
            }
            files.add(new CocoGeneratedFile(outputPath, renderTemplate(group, entry, model)));
        }
        return CocoCodegenResult.of(files);
    }

    private TemplateManifest loadManifest(String group) {
        String manifestPath = group + "/manifest.properties";
        byte[] content;
        try {
            content = this.resourceRoot.read(manifestPath);
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to read template manifest at " + location(manifestPath), ex);
        }
        if (content == null) {
            throw new CocoCodegenException("unknown template group '" + group + "' at " + this.templateLocation);
        }

        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), this.encoding)) {
            properties.load(reader);
        }
        catch (IOException | IllegalArgumentException ex) {
            throw new CocoCodegenException("invalid template manifest at " + location(manifestPath), ex);
        }
        return parseManifest(group, manifestPath, properties);
    }

    private TemplateManifest parseManifest(String group, String manifestPath, Properties properties) {
        Set<String> allowedKeys = new LinkedHashSet<>();
        allowedKeys.add("group");
        allowedKeys.add("template.count");

        String manifestGroup = requireManifestValue(properties, "group", manifestPath);
        if (!group.equals(manifestGroup)) {
            throw invalidManifest(manifestPath, "group must equal '" + group + "'");
        }
        int templateCount;
        try {
            templateCount = Integer.parseInt(requireManifestValue(properties, "template.count", manifestPath));
        }
        catch (NumberFormatException ex) {
            throw invalidManifest(manifestPath, "template.count must be a positive integer", ex);
        }
        if (templateCount <= 0) {
            throw invalidManifest(manifestPath, "template.count must be a positive integer");
        }

        List<TemplateEntry> templates = new ArrayList<>(templateCount);
        for (int index = 0; index < templateCount; index++) {
            String sourceKey = "template." + index + ".source";
            String outputKey = "template." + index + ".output";
            allowedKeys.add(sourceKey);
            allowedKeys.add(outputKey);
            String source = requireSafeTemplatePath(
                    requireManifestValue(properties, sourceKey, manifestPath), manifestPath, sourceKey);
            String output = requireManifestValue(properties, outputKey, manifestPath);
            templates.add(new TemplateEntry(source, output, index));
        }

        for (String propertyName : properties.stringPropertyNames()) {
            if (!allowedKeys.contains(propertyName)) {
                Matcher matcher = TEMPLATE_KEY.matcher(propertyName);
                String detail = matcher.matches() ? "template index is outside template.count" : "unknown key";
                throw invalidManifest(manifestPath, detail + ": " + propertyName);
            }
        }
        return new TemplateManifest(List.copyOf(templates));
    }

    private String renderOutputPath(String group, TemplateEntry entry, Map<String, Object> model) {
        String templateName = group + "/manifest.properties#template." + entry.index + ".output";
        try {
            Template outputTemplate = new Template(templateName, new StringReader(entry.output), this.configuration);
            String rendered = process(outputTemplate, model);
            return CocoGeneratedPathValidator.normalizeRelativePath(rendered);
        }
        catch (IOException | TemplateException ex) {
            throw new CocoCodegenException("failed to render output path " + templateName, ex);
        }
    }

    private String renderTemplate(String group, TemplateEntry entry, Map<String, Object> model) {
        String templatePath = group + "/" + entry.source;
        try {
            Template template = this.configuration.getTemplate(templatePath, this.encoding.name());
            return process(template, model);
        }
        catch (IOException ex) {
            throw new CocoCodegenException("missing or unreadable template at " + location(templatePath), ex);
        }
        catch (TemplateException ex) {
            throw new CocoCodegenException("failed to render template at " + location(templatePath), ex);
        }
    }

    private String process(Template template, Map<String, Object> model) throws TemplateException, IOException {
        StringWriter writer = new StringWriter();
        Environment environment = template.createProcessingEnvironment(model, writer);
        environment.setOutputEncoding(this.encoding.name());
        environment.process();
        return writer.toString();
    }

    private static Map<String, Object> createModel(CocoCodegenRequest request) {
        Map<String, Object> model = new LinkedHashMap<>(request.attributes());
        model.put("templateGroup", request.templateGroup());
        model.put("targetPackage", request.targetPackage() == null ? "" : request.targetPackage());
        model.put("_coco", Map.of(
                "templateGroup", request.templateGroup(),
                "targetPackage", request.targetPackage() == null ? "" : request.targetPackage()));
        return Collections.unmodifiableMap(model);
    }

    private static void validateReservedAttributes(Map<String, Object> attributes) {
        for (String reservedAttribute : RESERVED_ATTRIBUTES) {
            if (attributes.containsKey(reservedAttribute)) {
                throw new CocoCodegenException("request attribute is reserved: " + reservedAttribute);
            }
        }
    }

    private static Configuration createConfiguration(TemplateResourceRoot resourceRoot, Charset encoding) {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setTemplateLoader(new ResourceTemplateLoader(resourceRoot));
        configuration.setDefaultEncoding(encoding.name());
        configuration.setLocalizedLookup(false);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }

    private String location(String relativePath) {
        return this.templateLocation + (this.templateLocation.endsWith("/") ? "" : "/") + relativePath;
    }

    private static String requireTemplateGroup(String value) {
        String normalized = requireText(value, "templateGroup");
        if (!TEMPLATE_GROUP.matcher(normalized).matches()) {
            throw new CocoCodegenException("templateGroup contains unsafe characters: " + value);
        }
        return normalized;
    }

    private static String requireManifestValue(Properties properties, String key, String manifestPath) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw invalidManifest(manifestPath, "missing or blank key: " + key);
        }
        return value.trim();
    }

    private static String requireSafeTemplatePath(String value, String manifestPath, String key) {
        String normalized;
        try {
            normalized = normalizeTemplatePath(value);
        }
        catch (CocoCodegenException ex) {
            throw invalidManifest(manifestPath, key + " is unsafe: " + value, ex);
        }
        return normalized;
    }

    private static String normalizeTemplatePath(String value) {
        String normalized = requireText(value, "template path").replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new CocoCodegenException("template path must be relative: " + value);
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || segment.indexOf(':') >= 0) {
                throw new CocoCodegenException("template path must be normalized and relative: " + value);
            }
        }
        return normalized;
    }

    private static Charset requireCharset(String encoding) {
        String normalized = requireText(encoding, "encoding");
        try {
            return Charset.forName(normalized);
        }
        catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            throw new CocoCodegenException("unsupported codegen encoding: " + normalized, ex);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static CocoCodegenException invalidManifest(String manifestPath, String detail) {
        return new CocoCodegenException("invalid template manifest at " + manifestPath + ": " + detail);
    }

    private static CocoCodegenException invalidManifest(String manifestPath, String detail, Throwable cause) {
        return new CocoCodegenException("invalid template manifest at " + manifestPath + ": " + detail, cause);
    }

    private record TemplateManifest(List<TemplateEntry> templates) {
    }

    private record TemplateEntry(String source, String output, int index) {
    }

    private interface TemplateResourceRoot {

        byte[] read(String relativePath) throws IOException;

        static TemplateResourceRoot create(String location) {
            if (location.startsWith("classpath:")) {
                String rootPath = location.substring("classpath:".length()).replace('\\', '/');
                while (rootPath.startsWith("/")) {
                    rootPath = rootPath.substring(1);
                }
                while (rootPath.endsWith("/")) {
                    rootPath = rootPath.substring(0, rootPath.length() - 1);
                }
                String checkedRootPath = rootPath;
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                ClassLoader classLoader = contextClassLoader == null
                        ? FreeMarkerCocoCodeGenerator.class.getClassLoader() : contextClassLoader;
                return relativePath -> {
                    String resourcePath = checkedRootPath.isEmpty()
                            ? relativePath : checkedRootPath + "/" + relativePath;
                    try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
                        return input == null ? null : input.readAllBytes();
                    }
                };
            }

            Path rootPath;
            try {
                if (location.startsWith("file:")) {
                    rootPath = Path.of(URI.create(location));
                }
                else {
                    if (location.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")
                            && !location.matches("^[A-Za-z]:[\\\\/].*")) {
                        throw new CocoCodegenException("unsupported template location: " + location);
                    }
                    rootPath = Path.of(location);
                }
            }
            catch (IllegalArgumentException ex) {
                throw new CocoCodegenException("invalid template location: " + location, ex);
            }
            Path normalizedRoot = rootPath.toAbsolutePath().normalize();
            return relativePath -> {
                Path target = normalizedRoot.resolve(relativePath).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    throw new CocoCodegenException("template path escapes template location: " + relativePath);
                }
                return Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
            };
        }
    }

    private static final class ResourceTemplateLoader implements TemplateLoader {

        private final TemplateResourceRoot resourceRoot;

        private ResourceTemplateLoader(TemplateResourceRoot resourceRoot) {
            this.resourceRoot = resourceRoot;
        }

        @Override
        public Object findTemplateSource(String name) throws IOException {
            String templatePath = normalizeTemplatePath(name);
            byte[] content = this.resourceRoot.read(templatePath);
            return content == null ? null : new TemplateSource(templatePath, content);
        }

        @Override
        public long getLastModified(Object templateSource) {
            return -1;
        }

        @Override
        public Reader getReader(Object templateSource, String encoding) {
            TemplateSource source = (TemplateSource) templateSource;
            return new InputStreamReader(new ByteArrayInputStream(source.content), Charset.forName(encoding));
        }

        @Override
        public void closeTemplateSource(Object templateSource) {
        }
    }

    private record TemplateSource(String path, byte[] content) {
    }
}
