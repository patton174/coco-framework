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
import java.util.EnumSet;
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
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;

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
    private ProjectDependenciesResolver projectDependenciesResolver;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

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

            CocoFeatureSelection selection = applicationSelection.merge(parameterSelection).merge(annotationSelection);
            CocoFeaturePlan plan = StandardCocoFeatures.resolve(selection);
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
            if (runtimeDeclared.isEmpty() && hasResolvedFeatureArtifact(equivalentArtifactIds)) {
                // The normal starter already supplies this feature transitively. Re-declaring it here
                // would make Maven mediate the starter's compile closure as a direct dependency.
                continue;
            }
            Dependency dependency = runtimeDeclared.stream()
                    .filter(candidate -> definition.artifactId().equals(candidate.getArtifactId()))
                    .findFirst()
                    .orElseGet(() -> runtimeDeclared.stream().findFirst()
                            .orElseGet(() -> newCompileDependency(definition, targetFeatureVersion)));
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
        }
        ResolvedFeatureDependencies resolvedDependencies = resolveAddedDependencies(
                dependenciesToAdd, targetFeatureVersion);
        dependenciesToAdd.forEach(this.project.getModel()::addDependency);
        mergeResolvedArtifacts(resolvedDependencies);
    }

    private Dependency newCompileDependency(CocoFeatureDefinition definition, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(this.featureGroupId);
        dependency.setArtifactId(definition.artifactId());
        dependency.setVersion(version);
        dependency.setScope(Artifact.SCOPE_COMPILE);
        return dependency;
    }

    private boolean isRuntimeDependency(Dependency dependency) {
        String scope = nonBlank(dependency.getScope());
        return !dependency.isOptional() && (scope == null
                || Artifact.SCOPE_COMPILE.equals(scope)
                || Artifact.SCOPE_RUNTIME.equals(scope));
    }

    private boolean hasResolvedFeatureArtifact(Set<String> equivalentArtifactIds) {
        if (this.project.getArtifacts() == null) {
            return false;
        }
        return this.project.getArtifacts().stream()
                .anyMatch(artifact -> this.featureGroupId.equals(artifact.getGroupId())
                        && equivalentArtifactIds.contains(artifact.getArtifactId()));
    }

    private String dependencyDescription(Dependency dependency) {
        String scope = nonBlank(dependency.getScope());
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion()
                + " (scope=" + (scope == null ? Artifact.SCOPE_COMPILE : scope)
                + ", optional=" + dependency.isOptional() + ")";
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

    private ResolvedFeatureDependencies resolveAddedDependencies(
            List<Dependency> dependenciesToAdd, String expectedCocoVersion)
            throws MojoExecutionException {
        if (dependenciesToAdd.isEmpty()) {
            return ResolvedFeatureDependencies.empty();
        }
        if (this.projectDependenciesResolver == null || this.repositorySystemSession == null) {
            throw new MojoExecutionException("Maven project dependency resolver is required to refresh the "
                    + "classpath for newly added Coco feature dependencies.");
        }
        try {
            MavenProject resolutionProject = new MavenProject(this.project);
            resolutionProject.setModel(this.project.getModel().clone());
            dependenciesToAdd.forEach(resolutionProject.getModel()::addDependency);
            // A copied dependencyArtifacts cache contains only the dependencies resolved before this goal.
            // Clearing it makes Maven collect the complete staged model and mediate all new roots together.
            resolutionProject.setDependencyArtifacts(null);
            DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(
                    resolutionProject, this.repositorySystemSession);
            request.setResolutionFilter((node, parents) -> {
                org.eclipse.aether.graph.Dependency candidate = node.getDependency();
                if (candidate == null) {
                    return true;
                }
                String scope = candidate.getScope();
                return !candidate.isOptional()
                        && (Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_RUNTIME.equals(scope));
            });
            DependencyResolutionResult result = this.projectDependenciesResolver.resolve(request);
            if (!result.getCollectionErrors().isEmpty()) {
                throw new MojoExecutionException("Failed to collect the refreshed Coco feature dependency closure.",
                        result.getCollectionErrors().get(0));
            }
            if (!result.getUnresolvedDependencies().isEmpty()) {
                org.eclipse.aether.graph.Dependency unresolved = result.getUnresolvedDependencies().get(0);
                List<Exception> errors = result.getResolutionErrors(unresolved);
                Throwable cause = errors == null || errors.isEmpty() ? null : errors.get(0);
                throw new MojoExecutionException("Failed to resolve the refreshed Coco feature dependency closure: "
                        + unresolved + ".", cause);
            }
            Set<Artifact> resolved = new LinkedHashSet<>();
            for (org.eclipse.aether.graph.Dependency dependency : result.getResolvedDependencies()) {
                resolved.add(toMavenArtifact(requireResolvedArtifact(dependency), dependency.getScope()));
            }
            Set<Artifact> directArtifacts = new LinkedHashSet<>();
            for (Dependency dependency : dependenciesToAdd) {
                Artifact directArtifact = resolved.stream()
                        .filter(artifact -> dependency.getGroupId().equals(artifact.getGroupId()))
                        .filter(artifact -> dependency.getArtifactId().equals(artifact.getArtifactId()))
                        .filter(artifact -> dependency.getVersion().equals(artifact.getBaseVersion()))
                        .findFirst()
                        .orElse(null);
                if (directArtifact == null) {
                    throw new MojoExecutionException("Resolved compile closure is missing direct feature dependency "
                            + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                            + dependency.getVersion() + ".");
                }
                directArtifacts.add(directArtifact);
            }
            validateCocoArtifactVersions(expectedCocoVersion, resolved.stream()
                    .filter(artifact -> this.featureGroupId.equals(artifact.getGroupId()))
                    .filter(artifact -> artifact.getArtifactId().startsWith("coco-"))
                    .toList());
            return new ResolvedFeatureDependencies(resolved, directArtifacts);
        }
        catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve the refreshed Coco feature dependency closure.", ex);
        }
    }

    private org.eclipse.aether.artifact.Artifact requireResolvedArtifact(
            org.eclipse.aether.graph.Dependency dependency)
            throws MojoExecutionException {
        org.eclipse.aether.artifact.Artifact artifact = dependency.getArtifact();
        if (artifact == null || artifact.getFile() == null || !artifact.getFile().isFile()) {
            throw new MojoExecutionException("Compile dependency artifact was not resolved to a readable file: "
                    + dependency + ".");
        }
        return artifact;
    }

    private Artifact toMavenArtifact(org.eclipse.aether.artifact.Artifact artifact, String scope) {
        Artifact mavenArtifact = RepositoryUtils.toArtifact(artifact);
        mavenArtifact.setScope(scope);
        mavenArtifact.setResolved(true);
        return mavenArtifact;
    }

    private void mergeResolvedArtifacts(ResolvedFeatureDependencies resolvedDependencies) {
        if (resolvedDependencies.artifacts().isEmpty()) {
            return;
        }
        Set<Artifact> mergedArtifacts = unionArtifacts(
                this.project.getArtifacts(), resolvedDependencies.artifacts());
        Set<Artifact> currentDependencyArtifacts = this.project.getDependencyArtifacts();
        Set<Artifact> mergedDependencyArtifacts = currentDependencyArtifacts == null
                ? null
                : unionArtifacts(currentDependencyArtifacts, resolvedDependencies.directArtifacts());

        // Publish only after the staged project has fully resolved. artifacts is the mediated closure,
        // while dependencyArtifacts remains Maven's direct-dependency view. A null direct view is a
        // meaningful uncollected cache state and must not be replaced with an incomplete synthetic set.
        this.project.setArtifacts(mergedArtifacts);
        if (mergedDependencyArtifacts != null) {
            this.project.setDependencyArtifacts(mergedDependencyArtifacts);
        }
    }

    private static Set<Artifact> unionArtifacts(Set<Artifact> existingArtifacts, Set<Artifact> resolvedClosure) {
        Set<Artifact> merged = new LinkedHashSet<>();
        if (existingArtifacts != null) {
            merged.addAll(existingArtifacts);
        }
        merged.addAll(resolvedClosure);
        return merged;
    }

    private record ResolvedFeatureDependencies(Set<Artifact> artifacts, Set<Artifact> directArtifacts) {

        private static ResolvedFeatureDependencies empty() {
            return new ResolvedFeatureDependencies(Set.of(), Set.of());
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
                + ", disabledByDependency=" + featureIds(disabledByDependencyFeatures(plan,
                        applicationSelection.merge(parameterSelection).merge(annotationSelection))) + ".");
    }

    private static String describeSelection(CocoFeatureSelection selection) {
        CocoFeatureSelection target = selection == null ? CocoFeatureSelection.empty() : selection;
        return "{enabled=" + featureIds(target.enabled()) + ", disabled=" + featureIds(target.disabled()) + "}";
    }

    private static Set<CocoFeature> disabledByDependencyFeatures(CocoFeaturePlan plan,
            CocoFeatureSelection selection) {
        EnumSet<CocoFeature> disabledByDependency = EnumSet.noneOf(CocoFeature.class);
        for (CocoFeatureDefinition definition : plan.definitions()) {
            if (plan.disabledFeatures().contains(definition.feature())
                    && !selection.disabled().contains(definition.feature())
                    && !plan.enabledFeatures().containsAll(definition.dependencies())) {
                disabledByDependency.add(definition.feature());
            }
        }
        return disabledByDependency.isEmpty() ? Set.of() : Set.copyOf(disabledByDependency);
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
