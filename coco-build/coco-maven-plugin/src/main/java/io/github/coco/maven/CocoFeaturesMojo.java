package io.github.coco.maven;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeatureDefinition;
import io.github.coco.feature.registry.CocoFeatureManifestLoader;
import io.github.coco.feature.registry.CocoFeaturePlan;
import io.github.coco.feature.registry.CocoFeatureSelection;
import io.github.coco.feature.registry.StandardCocoFeatures;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Coco 功能装配 Maven Goal。
 * <p>
 * 根据业务项目的配置文件、插件参数和 {@code @CocoFeatures} 注解计算最终启用功能，生成构建清单，并注入对应功能模块依赖。
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
@Mojo(name = "features", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public final class CocoFeaturesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(property = "coco.features.enabled")
    private String enabled;

    @Parameter(property = "coco.features.disabled")
    private String disabled;

    @Parameter(property = "coco.features.featureGroupId", defaultValue = "io.github.patton174")
    private String featureGroupId;

    @Parameter(property = "coco.features.featureVersion", defaultValue = "${project.version}")
    private String featureVersion;

    @Parameter(property = "coco.features.skip", defaultValue = "false")
    private boolean skip;

    /**
     * <p>
     * 执行 Coco 功能装配流程。
     * </p>
     * <p>
     * 该流程会读取配置文件、扫描 {@code @CocoFeatures} 注解、生成构建期功能清单，并将启用的功能模块注入 Maven 模型。
     * </p>
     * @throws MojoExecutionException 功能装配失败时抛出
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            getLog().info("Coco feature assembly skipped.");
            return;
        }
        if (this.project == null) {
            throw new MojoExecutionException("Maven project is required for Coco feature assembly.");
        }
        if ("pom".equals(this.project.getPackaging())) {
            getLog().info("Coco feature assembly skipped for pom packaging.");
            return;
        }

        CocoFeaturePlan plan = resolveFeaturePlan();
        writeManifest(plan);
        applyFeatureDependencies(plan);
        pruneDisabledFeatureArtifacts(plan);
        getLog().info("Coco feature manifest generated with " + plan.enabledFeatures().size() + " enabled features.");
    }

    private CocoFeaturePlan resolveFeaturePlan() throws MojoExecutionException {
        try {
            CocoFeatureSelection applicationSelection = new CocoBuildFeatureConfigurationLoader()
                    .load(this.project.getBasedir().toPath().resolve("src/main/resources"));
            CocoFeatureSelection parameterSelection = CocoFeatureSelection.of(
                    parseFeatures(this.enabled, "Maven parameter coco.features.enabled"),
                    parseFeatures(this.disabled, "Maven parameter coco.features.disabled"));
            CocoFeatureSelection annotationSelection = new CocoAnnotatedFeatureScanner()
                    .scan(this.classesDirectory.toPath(), classpathUrls());

            CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                    applicationSelection.merge(parameterSelection).merge(annotationSelection));
            logResolvedFeaturePlan(plan, applicationSelection, parameterSelection, annotationSelection);
            return plan;
        }
        catch (IllegalArgumentException | UncheckedIOException ex) {
            throw new MojoExecutionException("Failed to resolve Coco feature selection.", ex);
        }
    }

    /**
     * <p>
     * 将最终启用的功能模块依赖注入当前 Maven 项目模型。
     * </p>
     * @param plan 最终功能启用计划
     */
    void applyFeatureDependencies(CocoFeaturePlan plan) {
        Set<String> existingDependencies = this.project.getDependencies().stream()
                .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (CocoFeatureDefinition definition : plan.definitions()) {
            if (!plan.isEnabled(definition.feature())) {
                continue;
            }
            String coordinate = this.featureGroupId + ":" + definition.artifactId();
            if (existingDependencies.contains(coordinate)) {
                continue;
            }
            Dependency dependency = new Dependency();
            dependency.setGroupId(this.featureGroupId);
            dependency.setArtifactId(definition.artifactId());
            dependency.setVersion(this.featureVersion);
            dependency.setScope(Artifact.SCOPE_RUNTIME);
            this.project.getModel().addDependency(dependency);
            resolveRuntimeArtifact(dependency).ifPresent(this.project.getArtifacts()::add);
            existingDependencies.add(coordinate);
        }
    }

    /**
     * <p>
     * 从 Maven 已解析 artifact 集合中移除被禁用功能声明的可裁剪 artifact，避免后续打包插件把禁用能力写入业务应用产物。
     * </p>
     * @param plan 最终功能启用计划
     */
    void pruneDisabledFeatureArtifacts(CocoFeaturePlan plan) {
        Set<String> disabledArtifactIds = plan.definitions().stream()
                .filter(definition -> !plan.isEnabled(definition.feature()))
                .flatMap(definition -> definition.pruneArtifactIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        if (disabledArtifactIds.isEmpty()) {
            return;
        }
        this.project.setArtifacts(pruneArtifacts(this.project.getArtifacts(), disabledArtifactIds));
        if (this.project.getDependencyArtifacts() != null) {
            this.project.setDependencyArtifacts(pruneArtifacts(this.project.getDependencyArtifacts(), disabledArtifactIds));
        }
    }

    /**
     * <p>
     * 从 artifact 集合中过滤指定 artifactId。
     * </p>
     * @param artifacts 原始 artifact 集合
     * @param excludedArtifactIds 需要过滤的 artifactId 集合
     * @return 过滤后的 artifact 集合
     */
    private Set<Artifact> pruneArtifacts(Set<Artifact> artifacts, Set<String> excludedArtifactIds) {
        if (artifacts == null || artifacts.isEmpty()) {
            return Set.of();
        }
        return artifacts.stream()
                .filter(artifact -> !isPrunableArtifact(artifact, excludedArtifactIds))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * <p>
     * 判断已解析 artifact 是否属于 Coco 禁用功能可裁剪范围。
     * </p>
     * @param artifact 已解析 Maven artifact
     * @param excludedArtifactIds 功能清单声明的可裁剪 artifactId 集合
     * @return 需要裁剪时返回 {@code true}
     */
    private boolean isPrunableArtifact(Artifact artifact, Set<String> excludedArtifactIds) {
        String artifactId = artifact.getArtifactId();
        if (!excludedArtifactIds.contains(artifactId)) {
            return false;
        }
        String groupId = artifact.getGroupId();
        if (artifactId.startsWith("coco-feature-")) {
            return this.featureGroupId.equals(groupId);
        }
        if (artifactId.startsWith("mybatis-plus")) {
            return "com.baomidou".equals(groupId);
        }
        return ("mybatis".equals(artifactId) || "mybatis-spring".equals(artifactId))
                && "org.mybatis".equals(groupId);
    }

    /**
     * <p>
     * 尝试解析已注入的运行期功能模块 artifact。
     * </p>
     * @param dependency 功能模块依赖
     * @return 解析到的 Maven artifact；解析器不可用或解析失败时返回空结果
     */
    private Optional<Artifact> resolveRuntimeArtifact(Dependency dependency) {
        if (this.repositorySystem == null || this.repositorySystemSession == null) {
            return Optional.empty();
        }
        try {
            org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    dependency.getGroupId(), dependency.getArtifactId(), "jar", dependency.getVersion());
            ArtifactRequest request = new ArtifactRequest(artifact, this.remoteRepositories, null);
            File file = this.repositorySystem.resolveArtifact(this.repositorySystemSession, request)
                    .getArtifact()
                    .getFile();
            DefaultArtifact mavenArtifact = new DefaultArtifact(
                    dependency.getGroupId(),
                    dependency.getArtifactId(),
                    dependency.getVersion(),
                    Artifact.SCOPE_RUNTIME,
                    "jar",
                    null,
                    new DefaultArtifactHandler("jar"));
            mavenArtifact.setFile(file);
            return Optional.of(mavenArtifact);
        }
        catch (ArtifactResolutionException ex) {
            getLog().warn("Unable to resolve Coco feature artifact "
                    + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion()
                    + ". The dependency was still added to the Maven model.", ex);
            return Optional.empty();
        }
    }

    /**
     * <p>
     * 将最终功能启用计划写入业务应用的构建输出目录。
     * </p>
     * @param plan 最终功能启用计划
     * @throws MojoExecutionException 清单写入失败时抛出
     */
    private void writeManifest(CocoFeaturePlan plan) throws MojoExecutionException {
        Path manifestPath = this.outputDirectory.toPath()
                .resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        try {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath,
                    CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "coco-maven-plugin")),
                    StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new MojoExecutionException("Failed to write Coco feature manifest.", ex);
        }
    }

    /**
     * <p>
     * 构建用于扫描业务应用 class 的 classpath URL 集合。
     * </p>
     * @return classpath URL 集合
     * @throws MojoExecutionException classpath 条目无法转换为 URL 时抛出
     */
    private Collection<URL> classpathUrls() throws MojoExecutionException {
        LinkedHashSet<URL> urls = new LinkedHashSet<>();
        addUrl(urls, this.classesDirectory);
        for (Artifact artifact : this.project.getArtifacts()) {
            File file = artifact.getFile();
            if (file != null && file.exists()) {
                addUrl(urls, file);
            }
        }
        return urls;
    }

    /**
     * <p>
     * 向 classpath URL 集合添加一个文件或目录。
     * </p>
     * @param urls classpath URL 集合
     * @param file 文件或目录
     * @throws MojoExecutionException 文件路径无法转换为 URL 时抛出
     */
    private void addUrl(Set<URL> urls, File file) throws MojoExecutionException {
        try {
            urls.add(file.toURI().toURL());
        }
        catch (MalformedURLException ex) {
            throw new MojoExecutionException("Invalid Coco feature classpath entry: " + file, ex);
        }
    }

    /**
     * <p>
     * 将逗号分隔的功能标识文本解析为功能集合。
     * </p>
     * @param value 功能标识文本
     * @return 功能集合
     */
    private static Set<CocoFeature> parseFeatures(String value, String source) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return CocoFeatureIdParser.parse(value, source);
    }

    private void logResolvedFeaturePlan(CocoFeaturePlan plan, CocoFeatureSelection applicationSelection,
            CocoFeatureSelection parameterSelection, CocoFeatureSelection annotationSelection) {
        if (!getLog().isInfoEnabled()) {
            return;
        }
        getLog().info("Coco features resolved from sources {application=" + describeSelection(applicationSelection)
                + ", parameters=" + describeSelection(parameterSelection)
                + ", annotations=" + describeSelection(annotationSelection)
                + "}: enabled=" + featureIds(plan.enabledFeatures())
                + ", disabled=" + featureIds(plan.disabledFeatures())
                + ", disabledByDependency=" + featureIds(plan.disabledByDependencyFeatures()) + ".");
    }

    private static String describeSelection(CocoFeatureSelection selection) {
        CocoFeatureSelection target = selection == null ? CocoFeatureSelection.empty() : selection;
        return "{enabled=" + featureIds(target.enabled()) + ", disabled=" + featureIds(target.disabled()) + "}";
    }

    private static String featureIds(Set<CocoFeature> features) {
        if (features == null || features.isEmpty()) {
            return "[]";
        }
        return features.stream()
                .map(CocoFeature::id)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", ", "[", "]"));
    }

}
