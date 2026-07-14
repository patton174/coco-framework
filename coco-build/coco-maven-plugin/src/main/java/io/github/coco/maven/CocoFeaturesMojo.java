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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureDefinition;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

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

    @Parameter(property = "coco.features.featureVersion")
    private String featureVersion;

    @Parameter(defaultValue = "${plugin}", readonly = true)
    private PluginDescriptor pluginDescriptor;

    private String resolvedFeatureVersion;

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
        validateFeatureArtifactVersions();
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
    void applyFeatureDependencies(CocoFeaturePlan plan) throws MojoExecutionException {
        String targetFeatureVersion = effectiveFeatureVersion();
        List<Dependency> dependenciesToAdd = new java.util.ArrayList<>();
        Set<Artifact> resolvedClosure = new LinkedHashSet<>();

        for (CocoFeatureDefinition definition : plan.definitions()) {
            if (!plan.isEnabled(definition.feature())) {
                continue;
            }
            Set<String> equivalentArtifactIds = StandardCocoFeatures.equivalentArtifactIds(definition);
            List<Dependency> declared = this.project.getDependencies().stream()
                    .filter(dependency -> this.featureGroupId.equals(dependency.getGroupId()))
                    .filter(dependency -> equivalentArtifactIds.contains(dependency.getArtifactId()))
                    .toList();
            List<Dependency> runtimeDeclared = declared.stream()
                    .filter(this::isRuntimeDependency)
                    .toList();
            if (!declared.isEmpty() && runtimeDeclared.isEmpty()) {
                String coordinates = declared.stream()
                        .map(this::dependencyDescription)
                        .sorted()
                        .collect(Collectors.joining(", "));
                throw new MojoExecutionException("Coco feature '" + definition.feature().id()
                        + "' is declared only with non-runtime dependencies: " + coordinates
                        + ". Use non-optional compile or runtime scope.");
            }
            Dependency dependency = runtimeDeclared.stream()
                    .filter(candidate -> definition.artifactId().equals(candidate.getArtifactId()))
                    .findFirst()
                    .orElseGet(() -> runtimeDeclared.stream().findFirst()
                            .orElseGet(() -> newRuntimeDependency(definition, targetFeatureVersion)));
            String dependencyVersion = nonBlank(dependency.getVersion());
            if (dependencyVersion == null) {
                dependency.setVersion(targetFeatureVersion);
                dependencyVersion = targetFeatureVersion;
            }
            if (!targetFeatureVersion.equals(dependencyVersion)) {
                throw new MojoExecutionException("Coco feature dependency " + dependencyDescription(dependency)
                        + " does not match Coco version " + targetFeatureVersion + ".");
            }
            if (declared.isEmpty()) {
                dependenciesToAdd.add(dependency);
            }
            resolvedClosure.addAll(resolveRuntimeClosure(dependency, targetFeatureVersion));
        }
        dependenciesToAdd.forEach(this.project.getModel()::addDependency);
        mergeResolvedArtifacts(resolvedClosure);
    }

    private Dependency newRuntimeDependency(CocoFeatureDefinition definition, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(this.featureGroupId);
        dependency.setArtifactId(definition.artifactId());
        dependency.setVersion(version);
        dependency.setScope(Artifact.SCOPE_RUNTIME);
        return dependency;
    }

    private boolean isRuntimeDependency(Dependency dependency) {
        String scope = nonBlank(dependency.getScope());
        return !dependency.isOptional() && (scope == null
                || Artifact.SCOPE_COMPILE.equals(scope)
                || Artifact.SCOPE_RUNTIME.equals(scope));
    }

    private String dependencyDescription(Dependency dependency) {
        String scope = nonBlank(dependency.getScope());
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion()
                + " (scope=" + (scope == null ? Artifact.SCOPE_COMPILE : scope)
                + ", optional=" + dependency.isOptional() + ")";
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
        this.project.getModel().getDependencies()
                .removeIf(dependency -> isPrunableCoordinate(
                        dependency.getGroupId(), dependency.getArtifactId(), disabledArtifactIds));
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
        return isPrunableCoordinate(artifact.getGroupId(), artifact.getArtifactId(), excludedArtifactIds);
    }

    private boolean isPrunableCoordinate(String groupId, String artifactId, Set<String> excludedArtifactIds) {
        return this.featureGroupId.equals(groupId) && excludedArtifactIds.contains(artifactId);
    }

    void validateFeatureArtifactVersions() throws MojoExecutionException {
        this.resolvedFeatureVersion = resolveFeatureVersion();
    }

    private String effectiveFeatureVersion() throws MojoExecutionException {
        if (this.resolvedFeatureVersion == null) {
            this.resolvedFeatureVersion = resolveFeatureVersion();
        }
        return this.resolvedFeatureVersion;
    }

    private String resolveFeatureVersion() throws MojoExecutionException {
        String configuredVersion = nonBlank(this.featureVersion);
        String pluginVersion = pluginVersion();
        List<Artifact> cocoArtifacts = resolvedCocoArtifacts();

        if (configuredVersion != null) {
            validateCocoArtifactVersions(configuredVersion, cocoArtifacts);
            return configuredVersion;
        }

        if (pluginVersion != null) {
            validateCocoArtifactVersions(pluginVersion, cocoArtifacts);
            return pluginVersion;
        }

        Map<String, List<String>> artifactsByVersion = new LinkedHashMap<>();
        for (Artifact artifact : cocoArtifacts) {
            String artifactVersion = nonBlank(artifact.getBaseVersion());
            if (artifactVersion == null) {
                continue;
            }
            artifactsByVersion.computeIfAbsent(artifactVersion, ignored -> new java.util.ArrayList<>())
                    .add(coordinate(artifact));
        }
        if (artifactsByVersion.size() > 1) {
            List<String> mixedArtifacts = artifactsByVersion.values().stream()
                    .flatMap(Collection::stream)
                    .sorted()
                    .toList();
            throw new MojoExecutionException("Coco artifacts must use one version: "
                    + String.join(", ", mixedArtifacts) + ".");
        }
        if (artifactsByVersion.size() == 1) {
            return artifactsByVersion.keySet().iterator().next();
        }
        throw new MojoExecutionException("Unable to determine the Coco feature version. Configure "
                + "coco.features.featureVersion or run the goal from a versioned Coco Maven plugin or project.");
    }

    private void validateCocoArtifactVersions(String expectedVersion, List<Artifact> cocoArtifacts)
            throws MojoExecutionException {
        List<String> misalignedArtifacts = cocoArtifacts.stream()
                .filter(artifact -> !expectedVersion.equals(artifact.getBaseVersion()))
                .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                        + artifact.getBaseVersion())
                .sorted()
                .toList();
        if (!misalignedArtifacts.isEmpty()) {
            throw new MojoExecutionException("Coco feature artifact versions must align with '"
                    + expectedVersion + "': " + String.join(", ", misalignedArtifacts) + ".");
        }
    }

    private List<Artifact> resolvedCocoArtifacts() {
        if (this.project == null || this.project.getArtifacts() == null) {
            return List.of();
        }
        return this.project.getArtifacts().stream()
                .filter(artifact -> this.featureGroupId.equals(artifact.getGroupId()))
                .filter(artifact -> artifact.getArtifactId() != null && artifact.getArtifactId().startsWith("coco-"))
                .sorted(Comparator.comparing(this::coordinate))
                .toList();
    }

    private String pluginVersion() {
        String descriptorVersion = this.pluginDescriptor == null ? null : nonBlank(this.pluginDescriptor.getVersion());
        if (descriptorVersion != null) {
            return descriptorVersion;
        }
        return nonBlank(CocoFeaturesMojo.class.getPackage().getImplementationVersion());
    }

    private String coordinate(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Set<Artifact> resolveRuntimeClosure(Dependency dependency, String expectedCocoVersion)
            throws MojoExecutionException {
        if (this.repositorySystem == null || this.repositorySystemSession == null) {
            throw new MojoExecutionException("Maven Resolver is required to resolve the runtime dependency closure "
                    + "for " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ".");
        }
        try {
            String type = nonBlank(dependency.getType()) == null ? "jar" : dependency.getType().trim();
            String classifier = nonBlank(dependency.getClassifier()) == null ? "" : dependency.getClassifier().trim();
            org.eclipse.aether.artifact.Artifact rootArtifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    dependency.getGroupId(), dependency.getArtifactId(), classifier, type, dependency.getVersion());
            List<Exclusion> exclusions = dependency.getExclusions().stream()
                    .map(exclusion -> new Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*"))
                    .toList();
            org.eclipse.aether.graph.Dependency rootDependency = new org.eclipse.aether.graph.Dependency(
                    rootArtifact, Artifact.SCOPE_RUNTIME, false, exclusions);
            CollectRequest collectRequest = new CollectRequest(rootDependency,
                    this.remoteRepositories == null ? List.of() : this.remoteRepositories);
            DependencyRequest request = new DependencyRequest(collectRequest, (node, parents) -> {
                org.eclipse.aether.graph.Dependency candidate = node.getDependency();
                if (candidate == null) {
                    return true;
                }
                String scope = candidate.getScope();
                return !candidate.isOptional()
                        && (Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_RUNTIME.equals(scope));
            });
            DependencyResult result = this.repositorySystem.resolveDependencies(
                    this.repositorySystemSession, request);
            if (!result.getCollectExceptions().isEmpty()) {
                throw new MojoExecutionException("Failed to collect runtime dependency closure for "
                        + dependencyDescription(dependency) + ".", result.getCollectExceptions().get(0));
            }
            Set<Artifact> resolved = new LinkedHashSet<>();
            for (ArtifactResult artifactResult : result.getArtifactResults()) {
                resolved.add(toMavenArtifact(requireResolvedArtifact(artifactResult)));
            }
            boolean directResolved = resolved.stream()
                    .anyMatch(artifact -> dependency.getGroupId().equals(artifact.getGroupId())
                            && dependency.getArtifactId().equals(artifact.getArtifactId())
                            && dependency.getVersion().equals(artifact.getBaseVersion()));
            if (!directResolved) {
                throw new MojoExecutionException("Resolved runtime closure is missing direct feature dependency "
                        + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                        + dependency.getVersion() + ".");
            }
            validateCocoArtifactVersions(expectedCocoVersion, resolved.stream()
                    .filter(artifact -> this.featureGroupId.equals(artifact.getGroupId()))
                    .filter(artifact -> artifact.getArtifactId().startsWith("coco-"))
                    .toList());
            return Set.copyOf(resolved);
        }
        catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve runtime dependency closure for "
                    + dependencyDescription(dependency) + ".", ex);
        }
    }

    private org.eclipse.aether.artifact.Artifact requireResolvedArtifact(ArtifactResult result)
            throws MojoExecutionException {
        org.eclipse.aether.artifact.Artifact artifact = result.getArtifact();
        if (artifact == null || artifact.getFile() == null || !artifact.getFile().isFile()
                || !result.getExceptions().isEmpty()) {
            throw new MojoExecutionException("Runtime dependency artifact was not resolved to a readable file: "
                    + result + ".");
        }
        return artifact;
    }

    private Artifact toMavenArtifact(org.eclipse.aether.artifact.Artifact artifact) {
        String classifier = artifact.getClassifier().isBlank() ? null : artifact.getClassifier();
        DefaultArtifact mavenArtifact = new DefaultArtifact(
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(),
                Artifact.SCOPE_RUNTIME, artifact.getExtension(), classifier,
                new DefaultArtifactHandler(artifact.getExtension()));
        mavenArtifact.setFile(artifact.getFile());
        mavenArtifact.setResolved(true);
        return mavenArtifact;
    }

    private void mergeResolvedArtifacts(Set<Artifact> resolvedClosure) {
        Set<Artifact> artifacts = new LinkedHashSet<>();
        if (this.project.getArtifacts() != null) {
            artifacts.addAll(this.project.getArtifacts());
        }
        artifacts.addAll(resolvedClosure);
        this.project.setArtifacts(artifacts);

        Set<Artifact> dependencyArtifacts = new LinkedHashSet<>();
        if (this.project.getDependencyArtifacts() != null) {
            dependencyArtifacts.addAll(this.project.getDependencyArtifacts());
        }
        dependencyArtifacts.addAll(resolvedClosure);
        this.project.setDependencyArtifacts(dependencyArtifacts);
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
