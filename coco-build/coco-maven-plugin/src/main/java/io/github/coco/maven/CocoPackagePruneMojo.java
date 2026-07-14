package io.github.coco.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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
@Mojo(name = "prune-package", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class CocoPackagePruneMojo extends AbstractMojo {

    private static final String DEFAULT_FEATURE_GROUP_ID = "io.github.patton174";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

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
            return new PrunePlan(null, Set.of());
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
            return new PrunePlan(manifest, Set.copyOf(artifactIds));
        }
        catch (IOException | RuntimeException ex) {
            throw new MojoExecutionException("Failed to read Coco feature manifest: " + manifestPath, ex);
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
        try {
            int removed = 0;
            try (JarFile source = new JarFile(archivePath.toFile())) {
                Set<String> pruneEntryNames = CocoBootArchivePreflight.inspect(source, prunePlan.manifest(),
                        effectiveFeatureGroupId(), this.featureVersion, prunePlan.artifactIds()).pruneEntryNames();
                if (pruneEntryNames.isEmpty()) {
                    return 0;
                }
                temporaryPath = Files.createTempFile(archivePath.getParent(),
                        archivePath.getFileName().toString(), ".tmp");
                try (JarOutputStream target = new JarOutputStream(Files.newOutputStream(temporaryPath))) {
                    var entries = source.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (pruneEntryNames.contains(entry.getName())) {
                            removed++;
                            continue;
                        }
                        boolean bootIndex = isBootIndex(entry);
                        target.putNextEntry(targetEntry(entry, !bootIndex));
                        if (bootIndex) {
                            writeFilteredBootIndex(source, entry, pruneEntryNames, target);
                        }
                        else if (!entry.isDirectory()) {
                            try (InputStream inputStream = source.getInputStream(entry)) {
                                copy(inputStream, target);
                            }
                        }
                        target.closeEntry();
                    }
                }
            }
            backupOriginalArchive(archivePath);
            Files.move(temporaryPath, archivePath, StandardCopyOption.REPLACE_EXISTING);
            return removed;
        }
        finally {
            if (temporaryPath != null) {
                Files.deleteIfExists(temporaryPath);
            }
        }
    }

    private String effectiveFeatureGroupId() {
        return this.featureGroupId == null || this.featureGroupId.isBlank()
                ? DEFAULT_FEATURE_GROUP_ID
                : this.featureGroupId.trim();
    }

    /**
     * <p>
     * 在覆盖主产物前保存原始 Spring Boot jar，便于排查裁剪结果或后续签名流程显式选择产物。
     * </p>
     * @param archivePath Spring Boot 可执行 jar 路径
     * @throws IOException 备份失败时抛出
     */
    private void backupOriginalArchive(Path archivePath) throws IOException {
        Files.copy(archivePath, originalArchivePath(archivePath), StandardCopyOption.REPLACE_EXISTING);
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
    private void writeFilteredBootIndex(JarFile source, JarEntry entry, Set<String> pruneEntryNames,
            JarOutputStream target) throws IOException {
        String content;
        try (InputStream inputStream = source.getInputStream(entry)) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String filtered = content.lines()
                .filter(line -> !containsPrunedEntry(line, pruneEntryNames))
                .collect(Collectors.joining("\n"));
        if (content.endsWith("\n") && !filtered.isEmpty()) {
            filtered = filtered + "\n";
        }
        target.write(filtered.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>
     * 判断索引行是否引用被禁用功能对应的可裁剪 artifactId。
     * </p>
     * @param line 索引行
     * @param pruneEntryNames 已通过 Maven 坐标证明属于 Coco 的完整归档条目名
     * @return 引用禁用功能模块时返回 {@code true}
     */
    private boolean containsPrunedEntry(String line, Set<String> pruneEntryNames) {
        int index = line.indexOf("BOOT-INF/lib/");
        if (index < 0) {
            return false;
        }
        int endIndex = line.indexOf(".jar", index);
        if (endIndex < 0) {
            return false;
        }
        return pruneEntryNames.contains(line.substring(index, endIndex + ".jar".length()));
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

    /**
     * <p>
     * 复制流内容。
     * </p>
     * @param inputStream 输入流
     * @param outputStream 输出流
     * @throws IOException 复制失败时抛出
     */
    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, read);
        }
    }

    record PrunePlan(CocoFeatureManifest manifest, Set<String> artifactIds) {
    }
}
