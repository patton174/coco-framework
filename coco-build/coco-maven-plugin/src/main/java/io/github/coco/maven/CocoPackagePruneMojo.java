package io.github.coco.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import io.github.coco.feature.model.CocoFeatureDefinition;
import io.github.coco.feature.model.CocoFeatureManifest;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.StandardCocoFeatures;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Coco 业务应用打包裁剪 Maven Goal。
 * <p>
 * 在 Spring Boot 可执行包生成后，根据 Coco 功能清单移除被禁用的功能模块依赖，保证业务应用最终产物只携带启用能力。
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
@Mojo(name = "prune-package", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public final class CocoPackagePruneMojo extends AbstractMojo {

    private static final String DEFAULT_FEATURE_GROUP_ID = "io.github.patton174";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private ProjectDependenciesResolver projectDependenciesResolver;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File buildDirectory;

    @Parameter(defaultValue = "${project.build.finalName}", required = true)
    private String finalName;

    @Parameter(property = "coco.features.featureGroupId", defaultValue = DEFAULT_FEATURE_GROUP_ID)
    private String featureGroupId = DEFAULT_FEATURE_GROUP_ID;

    @Parameter(property = "coco.features.featureVersion")
    private String featureVersion;

    @Parameter(property = "coco.features.skip", defaultValue = "false")
    private boolean skip;

    private CocoArchiveLimits archiveLimits = CocoArchiveLimits.DEFAULT;

    private ArchiveFileOperations archiveFileOperations = (source, target) -> Files.move(source, target,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

    private ArchiveReadBudgetFactory archiveReadBudgetFactory = limit -> new CocoArchiveIo.CumulativeBudget(
            limit, "Archive cumulative read bytes");

    private ArchivePathInspector archivePathInspector = new ArchivePathInspector() {
        @Override
        public BasicFileAttributes readAttributes(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public boolean isSameFile(Path first, Path second) throws IOException {
            return Files.isSameFile(first, second);
        }
    };

    /**
     * <p>
     * 执行 Spring Boot 产物裁剪。
     * </p>
     * @throws MojoExecutionException 裁剪失败时抛出
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            getLog().info("Coco package pruning skipped.");
            return;
        }
        if (this.project == null || "pom".equals(this.project.getPackaging())) {
            getLog().info("Coco package pruning skipped for pom packaging.");
            return;
        }
        PrunePlan prunePlan = loadPrunePlan();
        if (prunePlan.artifactIds().isEmpty()) {
            getLog().info("Coco package pruning skipped because no feature is disabled.");
            return;
        }
        Path archivePath = archivePath();
        if (!Files.isRegularFile(archivePath)) {
            getLog().info("Coco package pruning skipped because archive does not exist: " + archivePath);
            return;
        }
        try {
            int removed = pruneBootArchive(archivePath, prunePlan);
            getLog().info("Coco package pruning removed " + removed + " disabled feature artifact(s).");
        }
        catch (IOException ex) {
            throw new MojoExecutionException("Failed to prune Coco disabled feature artifacts.", ex);
        }
    }

    /**
     * <p>
     * 从功能清单中读取被禁用功能对应的可裁剪 artifactId。
     * </p>
     * @return 被禁用功能对应的可裁剪 artifactId 集合
     * @throws MojoExecutionException 功能清单读取失败时抛出
     */
    private PrunePlan loadPrunePlan() throws MojoExecutionException {
        Path manifestPath = this.classesDirectory.toPath().resolve(CocoFeatureManifestLoader.MANIFEST_LOCATION);
        if (!Files.isRegularFile(manifestPath)) {
            return new PrunePlan(null, Set.of(), Set.of());
        }
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(inputStream);
            StandardCocoFeatures.validateManifest(manifest);
            Map<String, CocoFeatureDefinition> currentDefinitions = StandardCocoFeatures.all().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            definition -> definition.feature().id(),
                            definition -> definition));
            Set<String> cocoArtifactIds = currentDefinitions.values().stream()
                    .flatMap(definition -> StandardCocoFeatures.equivalentArtifactIds(definition).stream())
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> artifactIds = new LinkedHashSet<>();
            manifest.features().stream()
                    .filter(entry -> !entry.enabled())
                    .forEach(entry -> {
                        entry.pruneArtifactIds().stream()
                                .filter(cocoArtifactIds::contains)
                                .forEach(artifactIds::add);
                        CocoFeatureDefinition currentDefinition = currentDefinitions.get(entry.id());
                        if (currentDefinition != null) {
                            artifactIds.addAll(StandardCocoFeatures.equivalentArtifactIds(currentDefinition));
                        }
                    });
            CocoArchiveIo.CumulativeBudget resolvedArtifactBudget = new CocoArchiveIo.CumulativeBudget(
                    this.archiveLimits.resolvedArtifactsBytes(), "Resolved artifact cumulative SHA-256 bytes");
            return new PrunePlan(manifest, Set.copyOf(artifactIds),
                    resolveDisabledFeatureClosure(artifactIds, resolvedArtifactBudget));
        }
        catch (IOException | RuntimeException ex) {
            throw new MojoExecutionException("Failed to read Coco feature manifest: " + manifestPath, ex);
        }
    }

    private Set<CocoBootArchivePreflight.PrunableArtifact> resolveDisabledFeatureClosure(
            Set<String> disabledFeatureArtifactIds, CocoArchiveIo.CumulativeBudget resolvedArtifactBudget)
            throws MojoExecutionException {
        if (disabledFeatureArtifactIds.isEmpty()) {
            return Set.of();
        }
        if (this.projectDependenciesResolver == null || this.repositorySystemSession == null) {
            throw new MojoExecutionException("Maven project dependency resolver is required to prune the "
                    + "disabled Coco feature dependency closure.");
        }
        Set<Artifact> originalArtifacts = currentRuntimeArtifacts();
        MavenProject resolutionProject = resolutionProject();
        excludeDisabledFeatureRoots(resolutionProject, disabledFeatureArtifactIds);
        Set<Artifact> survivingArtifacts = resolveRuntimeArtifacts(
                resolutionProject, "project without disabled Coco features");

        Map<String, Artifact> survivingByConflictId = new LinkedHashMap<>();
        for (Artifact artifact : survivingArtifacts) {
            survivingByConflictId.put(artifact.getDependencyConflictId(), artifact);
        }

        Set<CocoBootArchivePreflight.PrunableArtifact> prunable = new LinkedHashSet<>();
        for (Artifact artifact : originalArtifacts) {
            Artifact survivor = survivingByConflictId.get(artifact.getDependencyConflictId());
            if (survivor == null) {
                prunable.add(prunableArtifact(artifact, resolvedArtifactBudget));
            }
            else if (!artifact.getBaseVersion().equals(survivor.getBaseVersion())) {
                throw new MojoExecutionException("Cannot safely prune disabled Coco feature dependencies "
                        + "because Maven mediation changed " + artifact.getDependencyConflictId() + " from "
                        + artifact.getBaseVersion() + " to " + survivor.getBaseVersion() + ".");
            }
        }
        return Set.copyOf(prunable);
    }

    private Set<Artifact> currentRuntimeArtifacts() throws MojoExecutionException {
        if (this.project.getArtifacts() != null && !this.project.getArtifacts().isEmpty()) {
            return this.project.getArtifacts().stream()
                    .filter(this::isRuntimeArtifact)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return resolveRuntimeArtifacts(resolutionProject(), "current project");
    }

    private MavenProject resolutionProject() {
        MavenProject resolutionProject = new MavenProject(this.project);
        resolutionProject.setModel(this.project.getModel().clone());
        resolutionProject.setDependencyArtifacts(null);
        return resolutionProject;
    }

    private Set<Artifact> resolveRuntimeArtifacts(MavenProject resolutionProject, String description)
            throws MojoExecutionException {
        DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(
                resolutionProject, this.repositorySystemSession);
        Set<String> directOptionalRoots = directOptionalRoots(resolutionProject);
        request.setResolutionFilter((node, parents) -> {
            org.eclipse.aether.graph.Dependency candidate = node.getDependency();
            if (candidate == null) {
                return true;
            }
            String scope = candidate.getScope();
            boolean directOptionalRoot = candidate.isOptional() && candidate.getArtifact() != null
                    && directOptionalRoots.contains(dependencyKey(candidate.getArtifact()));
            return (!candidate.isOptional() || directOptionalRoot)
                    && (Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_RUNTIME.equals(scope));
        });
        try {
            DependencyResolutionResult result = this.projectDependenciesResolver.resolve(request);
            validateResolution(result, description);
            Set<Artifact> resolved = new LinkedHashSet<>();
            for (org.eclipse.aether.graph.Dependency dependency : result.getResolvedDependencies()) {
                Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
                artifact.setScope(dependency.getScope());
                resolved.add(artifact);
            }
            return resolved;
        }
        catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Failed to resolve " + description + ".", ex);
        }
    }

    private Set<String> directOptionalRoots(MavenProject resolutionProject)
            throws MojoExecutionException {
        Set<String> roots = new LinkedHashSet<>();
        for (Dependency dependency : resolutionProject.getModel().getDependencies()) {
            String scope = dependency.getScope();
            if (dependency.isOptional() && (scope == null || Artifact.SCOPE_COMPILE.equals(scope)
                    || Artifact.SCOPE_RUNTIME.equals(scope))) {
                roots.add(dependencyKey(dependency));
            }
        }
        return Set.copyOf(roots);
    }

    private String dependencyKey(Dependency dependency) throws MojoExecutionException {
        if (this.artifactHandlerManager == null) {
            throw new MojoExecutionException("Maven artifact handler manager is required to resolve direct "
                    + "optional dependency identities.");
        }
        String type = dependency.getType() == null ? "jar" : dependency.getType();
        ArtifactHandler handler = this.artifactHandlerManager.getArtifactHandler(type);
        if (handler == null) {
            throw new MojoExecutionException("Maven artifact handler is unavailable for direct optional dependency "
                    + "type '" + type + "'.");
        }
        String classifier = dependency.getClassifier();
        if (classifier == null || classifier.isEmpty()) {
            classifier = handler.getClassifier();
        }
        String extension = handler.getExtension();
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                + (extension == null || extension.isEmpty() ? "jar" : extension) + ":"
                + (classifier == null ? "" : classifier);
    }

    private static String dependencyKey(org.eclipse.aether.artifact.Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                + (artifact.getExtension() == null || artifact.getExtension().isEmpty()
                        ? "jar" : artifact.getExtension()) + ":"
                + (artifact.getClassifier() == null ? "" : artifact.getClassifier());
    }

    private void excludeDisabledFeatureRoots(MavenProject resolutionProject,
            Set<String> disabledFeatureArtifactIds) {
        String groupId = effectiveFeatureGroupId();
        List<Dependency> dependencies = resolutionProject.getModel().getDependencies();
        dependencies.removeIf(dependency -> groupId.equals(dependency.getGroupId())
                && disabledFeatureArtifactIds.contains(dependency.getArtifactId()));
        for (Dependency dependency : dependencies) {
            Set<String> existingExclusions = dependency.getExclusions().stream()
                    .map(exclusion -> exclusion.getGroupId() + ":" + exclusion.getArtifactId())
                    .collect(Collectors.toSet());
            for (String artifactId : disabledFeatureArtifactIds) {
                if (existingExclusions.add(groupId + ":" + artifactId)) {
                    Exclusion exclusion = new Exclusion();
                    exclusion.setGroupId(groupId);
                    exclusion.setArtifactId(artifactId);
                    dependency.addExclusion(exclusion);
                }
            }
        }
    }

    private void validateResolution(DependencyResolutionResult result, String description)
            throws MojoExecutionException {
        if (!result.getCollectionErrors().isEmpty()) {
            throw new MojoExecutionException("Failed to collect " + description + ".",
                    result.getCollectionErrors().get(0));
        }
        if (!result.getUnresolvedDependencies().isEmpty()) {
            org.eclipse.aether.graph.Dependency unresolved = result.getUnresolvedDependencies().get(0);
            List<Exception> errors = result.getResolutionErrors(unresolved);
            Throwable cause = errors == null || errors.isEmpty() ? null : errors.get(0);
            throw new MojoExecutionException("Failed to resolve " + description + ": "
                    + unresolved + ".", cause);
        }
    }

    private boolean isRuntimeArtifact(Artifact artifact) {
        String scope = artifact.getScope();
        return scope == null || Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_RUNTIME.equals(scope);
    }

    private CocoBootArchivePreflight.PrunableArtifact prunableArtifact(Artifact artifact,
            CocoArchiveIo.CumulativeBudget resolvedArtifactBudget)
            throws MojoExecutionException {
        if (artifact.getFile() == null || !artifact.getFile().isFile()) {
            throw new MojoExecutionException("Resolved dependency artifact is not a readable file: "
                    + artifact + ".");
        }
        return new CocoBootArchivePreflight.PrunableArtifact(
                "BOOT-INF/lib/" + artifact.getFile().getName(),
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(),
                sha256(artifact.getFile().toPath(), resolvedArtifactBudget),
                hasMavenRepositoryLayout(artifact));
    }

    private boolean hasMavenRepositoryLayout(Artifact artifact) {
        Path parent = artifact.getFile().toPath().toAbsolutePath().normalize().getParent();
        Path coordinatePath = Path.of(artifact.getGroupId().replace('.', '/'),
                artifact.getArtifactId(), artifact.getBaseVersion());
        return parent != null && parent.endsWith(coordinatePath);
    }

    private String sha256(Path path, CocoArchiveIo.CumulativeBudget resolvedArtifactBudget)
            throws MojoExecutionException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return CocoArchiveIo.sha256Bounded(inputStream, this.archiveLimits.outerEntryBytes(),
                    "Resolved dependency artifact '" + path + "' SHA-256 input", resolvedArtifactBudget);
        }
        catch (IOException ex) {
            throw new MojoExecutionException("Failed to fingerprint resolved dependency artifact: " + path, ex);
        }
    }

    /**
     * <p>
     * 定位当前项目的主 jar 产物。
     * </p>
     * @return 主 jar 产物路径
     */
    private Path archivePath() {
        if (this.project.getArtifact() != null && this.project.getArtifact().getFile() != null) {
            return this.project.getArtifact().getFile().toPath();
        }
        return this.buildDirectory.toPath().resolve(this.finalName + ".jar");
    }

    /**
     * <p>
     * 重写 Spring Boot 可执行 jar，移除禁用功能模块对应的嵌套依赖。
     * </p>
     * @param archivePath Spring Boot 可执行 jar 路径
     * @param pruneArtifactIds 被禁用功能对应的可裁剪 artifactId 集合
     * @return 实际移除的嵌套依赖数量
     * @throws IOException jar 读写失败时抛出
     */
    int pruneBootArchive(Path archivePath, PrunePlan prunePlan) throws IOException {
        Path temporaryPath = null;
        Throwable failure = null;
        try {
            CocoArchiveIo.CumulativeBudget readBudget = newArchiveReadBudget();
            PublicationSnapshot publicationSnapshot = inspectPublicationPaths(archivePath);
            int removed = 0;
            BootArchiveView sourceView;
            Set<String> pruneEntryNames;
            try (JarFile source = new JarFile(archivePath.toFile())) {
                sourceView = inspectBootArchiveView(source, readBudget);
                pruneEntryNames = CocoBootArchivePreflight.inspect(source, prunePlan.manifest(),
                        effectiveFeatureGroupId(), this.featureVersion, prunePlan.artifactIds(),
                        prunePlan.resolvedArtifacts(), this.archiveLimits, readBudget).pruneEntryNames();
                if (pruneEntryNames.isEmpty()) {
                    return 0;
                }
                byte[] prefix = CocoExecutableArchive.readPrefix(archivePath,
                        this.archiveLimits.executablePrefixBytes(), readBudget);
                temporaryPath = Files.createTempFile(publicationSnapshot.archiveParent(),
                        archivePath.getFileName().toString(), ".tmp");
                try (OutputStream outputStream = Files.newOutputStream(temporaryPath)) {
                    outputStream.write(prefix);
                    try (JarOutputStream target = new JarOutputStream(outputStream)) {
                        long rewrittenBytes = 0;
                        var entries = source.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            if (pruneEntryNames.contains(entry.getName())) {
                                removed++;
                                continue;
                            }
                            boolean bootIndex = isBootIndex(entry);
                            target.putNextEntry(targetEntry(entry, !bootIndex));
                            long entryBytes = 0;
                            if (bootIndex) {
                                entryBytes = writeFilteredBootIndex(
                                        source, entry, pruneEntryNames, target, readBudget);
                            }
                            else if (!entry.isDirectory()) {
                                try (InputStream inputStream = source.getInputStream(entry)) {
                                    entryBytes = CocoArchiveIo.copyBounded(inputStream, target,
                                            this.archiveLimits.outerEntryBytes(),
                                            "Rewritten ZIP entry '" + entry.getName() + "'", readBudget);
                                }
                            }
                            rewrittenBytes = CocoArchiveIo.addBounded(rewrittenBytes, entryBytes,
                                    this.archiveLimits.outerTotalBytes(), "Rewritten archive bytes");
                            target.closeEntry();
                        }
                    }
                }
                CocoExecutableArchive.relocateOffsets(temporaryPath, prefix.length, readBudget);
            }
            publishRewrittenArchive(publicationSnapshot, temporaryPath,
                    sourceView, pruneEntryNames, readBudget);
            return removed;
        }
        catch (IOException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        }
        finally {
            deleteTemporaryPath(temporaryPath, failure);
        }
    }

    void publishRewrittenArchive(Path archivePath, Path temporaryPath, BootArchiveView sourceView,
            Set<String> pruneEntryNames) throws IOException {
        PublicationSnapshot publicationSnapshot = inspectPublicationPaths(archivePath);
        publishRewrittenArchive(publicationSnapshot, temporaryPath, sourceView,
                pruneEntryNames, newArchiveReadBudget());
    }

    private void publishRewrittenArchive(PublicationSnapshot publicationSnapshot, Path temporaryPath,
            BootArchiveView sourceView, Set<String> pruneEntryNames,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        Throwable failure = null;
        Path stagedBackup = null;
        Path previousBackup = null;
        Path archivePath = publicationSnapshot.archivePath();
        Path backupPath = publicationSnapshot.backupPath();
        boolean backupExisted = publicationSnapshot.backupState().present();
        boolean backupPublished = false;
        boolean archivePublished = false;
        try {
            TemporaryArchiveSnapshot temporarySnapshot = inspectTemporaryArchive(
                    publicationSnapshot, temporaryPath);
            validateRewrittenArchive(temporarySnapshot.path(), sourceView, pruneEntryNames, readBudget);
            verifyTemporarySnapshot(publicationSnapshot, temporarySnapshot);
            applyPermissions(temporarySnapshot.path(), publicationSnapshot.archiveState().permissions());
            temporarySnapshot = inspectTemporaryArchive(publicationSnapshot, temporarySnapshot.path());
            stagedBackup = Files.createTempFile(backupPath.getParent(),
                    backupPath.getFileName().toString(), ".staged.tmp");
            copyArchive(archivePath, stagedBackup, "Original archive backup", readBudget,
                    publicationSnapshot.archiveState().permissions());
            if (backupExisted) {
                previousBackup = Files.createTempFile(backupPath.getParent(),
                        backupPath.getFileName().toString(), ".rollback.tmp");
                copyArchive(backupPath, previousBackup, "Previous original archive backup", readBudget,
                        publicationSnapshot.backupState().permissions());
            }
            verifyPublicationSnapshot(publicationSnapshot);
            verifyTemporarySnapshot(publicationSnapshot, temporarySnapshot);
            this.archiveFileOperations.moveAtomically(stagedBackup, backupPath);
            stagedBackup = null;
            backupPublished = true;
            verifyMainArchiveState(publicationSnapshot);
            verifyTemporarySnapshot(publicationSnapshot, temporarySnapshot);
            this.archiveFileOperations.moveAtomically(temporarySnapshot.path(), archivePath);
            archivePublished = true;
        }
        catch (IOException | RuntimeException | Error ex) {
            failure = ex;
            if (backupPublished && !archivePublished) {
                restorePreviousBackup(backupPath, previousBackup, backupExisted, ex);
                previousBackup = null;
            }
            throw ex;
        }
        finally {
            deleteTemporaryPaths(failure, temporaryPath, stagedBackup, previousBackup);
        }
    }

    private void copyArchive(Path source, Path target, String description,
            CocoArchiveIo.CumulativeBudget readBudget, Set<PosixFilePermission> permissions) throws IOException {
        try (InputStream inputStream = Files.newInputStream(source);
                var outputStream = Files.newOutputStream(target)) {
            CocoArchiveIo.copyBounded(inputStream, outputStream, this.archiveLimits.archiveReadBytes(),
                    description, readBudget);
        }
        applyPermissions(target, permissions);
    }

    private CocoArchiveIo.CumulativeBudget newArchiveReadBudget() {
        return this.archiveReadBudgetFactory.create(this.archiveLimits.archiveReadBytes());
    }

    private PublicationSnapshot inspectPublicationPaths(Path archivePath) throws IOException {
        Path normalizedArchive = archivePath.toAbsolutePath().normalize();
        Path backupPath = originalArchivePath(normalizedArchive).toAbsolutePath().normalize();
        if (normalizedArchive.equals(backupPath)) {
            throw backupCollision(backupPath);
        }
        Path archiveParent = requireParent(normalizedArchive, "main archive");
        Path backupParent = requireParent(backupPath, "original archive backup");
        Path realArchiveParent = this.archivePathInspector.toRealPath(archiveParent);
        Path realBackupParent = this.archivePathInspector.toRealPath(backupParent);
        if (!realArchiveParent.equals(realBackupParent)
                || !this.archivePathInspector.fileStore(realArchiveParent)
                        .equals(this.archivePathInspector.fileStore(realBackupParent))) {
            throw new IOException("The original archive backup must use the main archive's real directory "
                    + "and file store.");
        }
        ArchivePathState archiveState = inspectPath(normalizedArchive, false, "Main archive");
        ArchivePathState backupState = inspectPath(backupPath, true, "Original archive backup");
        if (backupState.present() && this.archivePathInspector.isSameFile(normalizedArchive, backupPath)) {
            throw backupCollision(backupPath);
        }
        rejectReparsePoint(normalizedArchive, archiveState, "Main archive");
        rejectReparsePoint(backupPath, backupState, "Original archive backup");
        if (!archiveState.regularFile()) {
            throw new IOException("Main archive must be a regular file: " + normalizedArchive + ".");
        }
        if (backupState.present() && !backupState.regularFile()) {
            throw new IOException("Original archive backup must be a regular file: "
                    + backupPath + ".");
        }
        return new PublicationSnapshot(normalizedArchive, backupPath, realArchiveParent,
                this.archivePathInspector.fileStore(realArchiveParent), archiveState, backupState);
    }

    private ArchivePathState inspectPath(Path path, boolean allowAbsent, String description) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = this.archivePathInspector.readAttributes(path);
        }
        catch (NoSuchFileException ex) {
            if (allowAbsent) {
                return ArchivePathState.absent();
            }
            throw ex;
        }
        return new ArchivePathState(true, attributes.fileKey(), attributes.size(),
                attributes.lastModifiedTime(), attributes.isRegularFile(), attributes.isDirectory(),
                attributes.isSymbolicLink(), attributes.isOther(),
                this.archivePathInspector.posixPermissions(path));
    }

    private void verifyPublicationSnapshot(PublicationSnapshot expected) throws IOException {
        PublicationSnapshot current = inspectPublicationPaths(expected.archivePath());
        if (!expected.equals(current)) {
            throw new IOException("Main archive or original archive backup changed before publication: "
                    + expected.archivePath() + ".");
        }
    }

    private void verifyMainArchiveState(PublicationSnapshot expected) throws IOException {
        ArchivePathState current = inspectPath(expected.archivePath(), false, "Main archive");
        rejectReparsePoint(expected.archivePath(), current, "Main archive");
        if (!expected.archiveState().equals(current)) {
            throw new IOException("Main archive changed before final publication: "
                    + expected.archivePath() + ".");
        }
    }

    private TemporaryArchiveSnapshot inspectTemporaryArchive(
            PublicationSnapshot publicationSnapshot, Path temporaryPath) throws IOException {
        Path normalizedTemporary = temporaryPath.toAbsolutePath().normalize();
        Path temporaryDirectory = requireParent(normalizedTemporary, "rewritten archive");
        if (!publicationSnapshot.archiveParent().equals(
                this.archivePathInspector.toRealPath(temporaryDirectory))
                || !publicationSnapshot.fileStore().equals(
                        this.archivePathInspector.fileStore(normalizedTemporary))) {
            throw new IOException("The rewritten archive must be staged in the main archive's real directory "
                    + "and file store.");
        }
        ArchivePathState state = inspectPath(normalizedTemporary, false, "Rewritten archive");
        rejectReparsePoint(normalizedTemporary, state, "Rewritten archive");
        if (!state.regularFile()) {
            throw new IOException("Rewritten archive must be a regular file: "
                    + normalizedTemporary + ".");
        }
        return new TemporaryArchiveSnapshot(normalizedTemporary, state);
    }

    private void verifyTemporarySnapshot(PublicationSnapshot publicationSnapshot,
            TemporaryArchiveSnapshot expected) throws IOException {
        TemporaryArchiveSnapshot current = inspectTemporaryArchive(publicationSnapshot, expected.path());
        if (!expected.equals(current)) {
            throw new IOException("Rewritten archive changed before publication: "
                    + expected.path() + ".");
        }
    }

    private static void rejectReparsePoint(Path path, ArchivePathState state, String description)
            throws IOException {
        if (state.present() && (state.symbolicLink() || state.other())) {
            throw new IOException(description + " must not be a symbolic link or reparse point: " + path + ".");
        }
    }

    private static IOException backupCollision(Path backupPath) {
        return new IOException("Original archive backup path collides with the main archive: "
                + backupPath + ".");
    }

    private static Path requireParent(Path path, String description) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("The " + description + " must have a parent directory.");
        }
        return parent;
    }

    private void restorePreviousBackup(Path backupPath, Path previousBackup, boolean backupExisted,
            Throwable primaryFailure) {
        try {
            if (backupExisted) {
                this.archiveFileOperations.moveAtomically(previousBackup, backupPath);
            }
            else {
                Files.deleteIfExists(backupPath);
            }
        }
        catch (IOException | RuntimeException | Error rollbackFailure) {
            Path recoveryPath = backupExisted && previousBackup != null ? previousBackup : backupPath;
            primaryFailure.addSuppressed(new IOException(
                    "Failed to restore the original archive backup; recovery bytes are preserved at "
                            + recoveryPath.toAbsolutePath().normalize() + ".",
                    rollbackFailure));
        }
    }

    private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (permissions != null) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    private static void deleteTemporaryPaths(Throwable failure, Path... temporaryPaths) throws IOException {
        IOException cleanupFailure = null;
        for (Path temporaryPath : temporaryPaths) {
            try {
                deleteTemporaryPath(temporaryPath, failure == null ? cleanupFailure : failure);
            }
            catch (IOException ex) {
                if (cleanupFailure == null) {
                    cleanupFailure = ex;
                }
                else {
                    cleanupFailure.addSuppressed(ex);
                }
            }
        }
        if (failure == null && cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static void deleteTemporaryPath(Path temporaryPath, Throwable failure) throws IOException {
        if (temporaryPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryPath);
        }
        catch (IOException cleanupFailure) {
            if (failure == null) {
                throw cleanupFailure;
            }
            failure.addSuppressed(cleanupFailure);
        }
    }

    private String effectiveFeatureGroupId() {
        return this.featureGroupId == null || this.featureGroupId.isBlank()
                ? DEFAULT_FEATURE_GROUP_ID
                : this.featureGroupId.trim();
    }

    /**
     * <p>
     * 返回原始 jar 备份路径。
     * </p>
     * @param archivePath Spring Boot 可执行 jar 路径
     * @return 原始 jar 备份路径
     */
    private Path originalArchivePath(Path archivePath) {
        if (this.buildDirectory != null) {
            return this.buildDirectory.toPath().resolve("coco-prune.original.jar");
        }
        Path parent = archivePath.getParent();
        return parent == null ? Path.of("coco-prune.original.jar") : parent.resolve("coco-prune.original.jar");
    }

    /**
     * <p>
     * 判断 jar 条目是否为 Spring Boot classpath 或 layer 索引。
     * </p>
     * @param entry jar 条目
     * @return 是索引文件时返回 {@code true}
     */
    private boolean isBootIndex(JarEntry entry) {
        return "BOOT-INF/classpath.idx".equals(entry.getName()) || "BOOT-INF/layers.idx".equals(entry.getName());
    }

    /**
     * <p>
     * 重写 Spring Boot 索引文件，移除被禁用功能对应的嵌套依赖行。
     * </p>
     * @param source 原始 jar
     * @param entry 索引条目
     * @param pruneEntryNames 已通过 Maven 坐标证明属于 Coco 的完整归档条目名
     * @param target 目标 jar 输出流
     * @throws IOException 索引读写失败时抛出
     */
    private long writeFilteredBootIndex(JarFile source, JarEntry entry, Set<String> pruneEntryNames,
            JarOutputStream target, CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        String content = readUtf8(source, entry, this.archiveLimits, readBudget);
        List<String> retainedLines = new java.util.ArrayList<>();
        for (String line : content.lines().toList()) {
            if (!containsPrunedEntry(line, pruneEntryNames)) {
                retainedLines.add(line);
            }
        }
        String filtered = String.join("\n", retainedLines);
        if (content.endsWith("\n") && !filtered.isEmpty()) {
            filtered = filtered + "\n";
        }
        byte[] bytes = filtered.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > this.archiveLimits.indexBytes()) {
            throw new IOException("Rewritten Spring Boot index exceeds byte limit "
                    + this.archiveLimits.indexBytes() + ".");
        }
        target.write(bytes);
        return bytes.length;
    }

    /**
     * <p>
     * 判断索引行是否引用被禁用功能对应的可裁剪 artifactId。
     * </p>
     * @param line 索引行
     * @param pruneEntryNames 已通过 Maven 坐标证明属于 Coco 的完整归档条目名
     * @return 引用禁用功能模块时返回 {@code true}
     */
    private boolean containsPrunedEntry(String line, Set<String> pruneEntryNames) throws IOException {
        String reference = bootLibraryReference(line);
        return reference != null && pruneEntryNames.contains(reference);
    }

    private void validateRewrittenArchive(Path archivePath, BootArchiveView sourceView,
            Set<String> pruneEntryNames, CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        try (JarFile archive = new JarFile(archivePath.toFile())) {
            BootArchiveView targetView = inspectBootArchiveView(archive, readBudget);
            List<String> expectedLibraries = sourceView.libraries().stream()
                    .filter(name -> !pruneEntryNames.contains(name))
                    .toList();
            if (!targetView.libraries().equals(expectedLibraries)) {
                throw new IOException("Rewritten BOOT-INF/lib order does not equal source libraries minus pruned "
                        + "entries: expected=" + expectedLibraries + ", actual=" + targetView.libraries() + ".");
            }
            if (!targetView.indexes().equals(sourceView.indexes())) {
                throw new IOException("Rewritten Spring Boot index presence changed: expected="
                        + sourceView.indexes() + ", actual=" + targetView.indexes() + ".");
            }
        }
    }

    private BootArchiveView inspectBootArchiveView(JarFile archive,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        List<String> libraries = archive.stream()
                .filter(entry -> !entry.isDirectory())
                .map(JarEntry::getName)
                .filter(name -> name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar"))
                .toList();
        Set<String> librarySet = new LinkedHashSet<>();
        for (String library : libraries) {
            if (!librarySet.add(library)) {
                throw new IOException("Duplicate ZIP entry in BOOT-INF/lib: '" + library + "'.");
            }
        }
        Set<String> indexes = new LinkedHashSet<>();
        for (String indexName : List.of("BOOT-INF/classpath.idx", "BOOT-INF/layers.idx")) {
            JarEntry indexEntry = archive.getJarEntry(indexName);
            if (indexEntry != null) {
                if (indexEntry.isDirectory()) {
                    throw new IOException("Spring Boot index is a directory: " + indexName + ".");
                }
                indexes.add(indexName);
                validateBootIndex(archive, indexEntry, librarySet, readBudget);
            }
        }
        return new BootArchiveView(List.copyOf(libraries), Set.copyOf(indexes));
    }

    private void validateBootIndex(JarFile archive, JarEntry indexEntry, Set<String> libraries,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        String indexName = indexEntry.getName();
        String content = readUtf8(archive, indexEntry, this.archiveLimits, readBudget);
        Set<String> references = CocoBootIndexParser.parse(indexName, content, this.archiveLimits);
        if (!references.equals(libraries)) {
            Set<String> missing = new LinkedHashSet<>(libraries);
            missing.removeAll(references);
            Set<String> dangling = new LinkedHashSet<>(references);
            dangling.removeAll(libraries);
            throw new IOException("Rewritten " + indexName + " does not match BOOT-INF/lib: missing="
                    + missing + ", dangling=" + dangling + ".");
        }
    }

    static String readUtf8(JarFile archive, JarEntry entry, CocoArchiveLimits limits) throws IOException {
        return readUtf8(archive, entry, limits,
                new CocoArchiveIo.CumulativeBudget(limits.archiveReadBytes(), "Archive cumulative read bytes"));
    }

    static String readUtf8(JarFile archive, JarEntry entry, CocoArchiveLimits limits,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = archive.getInputStream(entry)) {
            bytes = CocoArchiveIo.readBounded(inputStream, limits.indexBytes(),
                    "Spring Boot index '" + entry.getName() + "'", readBudget);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
        catch (CharacterCodingException ex) {
            throw new IOException("Spring Boot index is not valid UTF-8: " + entry.getName() + ".", ex);
        }
    }

    private String bootLibraryReference(String line) throws IOException {
        String trimmed = line.trim();
        if (!trimmed.contains("BOOT-INF/lib/")) {
            return null;
        }
        if (!trimmed.startsWith("- \"") || !trimmed.endsWith("\"")) {
            throw new IOException("Malformed Spring Boot library index line: '" + line + "'.");
        }
        String reference = trimmed.substring(3, trimmed.length() - 1);
        validateBootLibraryReference(reference);
        return reference;
    }

    private void validateBootLibraryReference(String reference) throws IOException {
        String fileName = reference.startsWith("BOOT-INF/lib/")
                ? reference.substring("BOOT-INF/lib/".length())
                : "";
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")
                || !fileName.endsWith(".jar") || ".jar".equals(fileName)
                || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IOException("Non-canonical Spring Boot library index path: '" + reference + "'.");
        }
    }

    /**
     * <p>
     * 创建目标 jar 条目。
     * <p>
     * 未修改条目保留原始压缩方式；被重写的索引文件仅复制安全元数据，避免沿用 STORED 条目的旧 size 和 CRC。
     * </p>
     * @param source 原始 jar 条目
     * @param preserveStorage 是否保留原始 STORED 元数据
     * @return 目标 jar 条目
     */
    private JarEntry targetEntry(JarEntry source, boolean preserveStorage) {
        JarEntry target = new JarEntry(source.getName());
        if (source.getTime() >= 0) {
            target.setTime(source.getTime());
        }
        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }
        if (source.getExtra() != null) {
            target.setExtra(source.getExtra());
        }
        if (preserveStorage && source.getMethod() == ZipEntry.STORED) {
            target.setMethod(ZipEntry.STORED);
            target.setSize(source.getSize());
            target.setCompressedSize(source.getCompressedSize());
            target.setCrc(source.getCrc());
        }
        return target;
    }

    record PrunePlan(CocoFeatureManifest manifest, Set<String> artifactIds,
            Set<CocoBootArchivePreflight.PrunableArtifact> resolvedArtifacts) {
    }

    record BootArchiveView(List<String> libraries, Set<String> indexes) {
    }

    @FunctionalInterface
    interface ArchiveFileOperations {

        void moveAtomically(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface ArchiveReadBudgetFactory {

        CocoArchiveIo.CumulativeBudget create(long limit);
    }

    interface ArchivePathInspector {

        BasicFileAttributes readAttributes(Path path) throws IOException;

        boolean isSameFile(Path first, Path second) throws IOException;

        default Path toRealPath(Path path) throws IOException {
            return path.toRealPath();
        }

        default FileStore fileStore(Path path) throws IOException {
            return Files.getFileStore(path);
        }

        default Set<PosixFilePermission> posixPermissions(Path path) throws IOException {
            PosixFileAttributeView view = Files.getFileAttributeView(
                    path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            return view == null ? null : Set.copyOf(view.readAttributes().permissions());
        }
    }

    private record PublicationSnapshot(Path archivePath, Path backupPath, Path archiveParent,
            FileStore fileStore, ArchivePathState archiveState, ArchivePathState backupState) {
    }

    private record TemporaryArchiveSnapshot(Path path, ArchivePathState state) {
    }

    private record ArchivePathState(boolean present, Object fileKey, long size,
            FileTime lastModifiedTime, boolean regularFile, boolean directory,
            boolean symbolicLink, boolean other,
            Set<PosixFilePermission> permissions) {

        private ArchivePathState {
            permissions = permissions == null ? null : Set.copyOf(permissions);
        }

        private static ArchivePathState absent() {
            return new ArchivePathState(false, null, 0, null,
                    false, false, false, false, null);
        }
    }
}
