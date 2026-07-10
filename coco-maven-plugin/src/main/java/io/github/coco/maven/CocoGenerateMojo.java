package io.github.coco.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.coco.feature.codegen.core.CocoCodegenException;
import io.github.coco.feature.codegen.core.CocoCodegenResult;
import io.github.coco.feature.codegen.core.CocoGeneratedFile;
import io.github.coco.feature.codegen.core.CocoGeneratedFileWriteOptions;
import io.github.coco.feature.codegen.core.CocoGeneratedFileWriter;
import io.github.coco.feature.codegen.crud.CocoCrudSpec;
import io.github.coco.feature.codegen.template.FreeMarkerCocoCodeGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Coco CRUD 源码显式生成 Maven Goal。
 * <p>
 * 从业务项目的 YAML 规格生成可编辑 Java 源码。该 Goal 不绑定 Maven 默认生命周期，只在开发者主动调用时执行。
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
@Mojo(name = "generate", threadSafe = true)
public final class CocoGenerateMojo extends AbstractMojo {

    private static final String DEFAULT_TEMPLATE_LOCATION = "classpath:/coco/codegen/templates";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "coco.codegen.spec", defaultValue = "${project.basedir}/coco-codegen.yml", required = true)
    private File spec;

    @Parameter(property = "coco.codegen.outputDirectory",
            defaultValue = "${project.build.sourceDirectory}", required = true)
    private File outputDirectory;

    @Parameter(property = "coco.codegen.overwrite", defaultValue = "false")
    private boolean overwrite;

    @Parameter(property = "coco.codegen.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "coco.codegen.templateLocation", defaultValue = DEFAULT_TEMPLATE_LOCATION)
    private String templateLocation;

    @Parameter(property = "coco.codegen.encoding", defaultValue = "UTF-8")
    private String encoding;

    /**
     * <p>
     * 解析 CRUD 规格、渲染全部资源，并在整批预检通过后写入目标目录。
     * </p>
     * @throws MojoExecutionException 规格、模板或文件写入失败时抛出
     */
    @Override
    public void execute() throws MojoExecutionException {
        Path specPath = specPath();
        Path outputPath = outputPath();
        Charset charset = charset();
        if (!Files.isRegularFile(specPath)) {
            throw new MojoExecutionException("Coco codegen specification is not a regular file: " + specPath);
        }

        try {
            CocoCodegenYamlSpec yamlSpec = new CocoCodegenYamlParser().parse(specPath, charset);
            List<CocoCrudSpec> specifications = new CocoCrudSpecMapper().map(yamlSpec);
            FreeMarkerCocoCodeGenerator generator = new FreeMarkerCocoCodeGenerator(
                    templateLocation(), charset);
            CocoCodegenResult result = generateAll(generator, specifications);
            List<Path> targets = new CocoGeneratedFileWriter(charset).write(
                    outputPath,
                    result,
                    new CocoGeneratedFileWriteOptions(this.overwrite, this.dryRun));
            logTargets(targets);
        }
        catch (IOException ex) {
            throw new MojoExecutionException("Failed to read Coco codegen specification: " + specPath, ex);
        }
        catch (IllegalArgumentException | CocoCodegenException ex) {
            throw new MojoExecutionException("Coco CRUD source generation failed: " + ex.getMessage(), ex);
        }
    }

    private CocoCodegenResult generateAll(FreeMarkerCocoCodeGenerator generator, List<CocoCrudSpec> specifications) {
        List<CocoGeneratedFile> files = new ArrayList<>();
        for (CocoCrudSpec specification : specifications) {
            files.addAll(generator.generate(specification.toRequest()).files());
        }
        return CocoCodegenResult.of(files);
    }

    private Path specPath() throws MojoExecutionException {
        if (this.spec != null) {
            return this.spec.toPath().toAbsolutePath().normalize();
        }
        if (this.project != null && this.project.getBasedir() != null) {
            return this.project.getBasedir().toPath().resolve("coco-codegen.yml").toAbsolutePath().normalize();
        }
        throw new MojoExecutionException("Coco codegen specification path is required.");
    }

    private Path outputPath() throws MojoExecutionException {
        if (this.outputDirectory != null) {
            return this.outputDirectory.toPath().toAbsolutePath().normalize();
        }
        if (this.project != null && this.project.getBuild() != null
                && this.project.getBuild().getSourceDirectory() != null) {
            return Path.of(this.project.getBuild().getSourceDirectory()).toAbsolutePath().normalize();
        }
        throw new MojoExecutionException("Coco codegen output directory is required.");
    }

    private Charset charset() throws MojoExecutionException {
        String value = this.encoding == null || this.encoding.isBlank()
                ? StandardCharsets.UTF_8.name() : this.encoding.trim();
        try {
            return Charset.forName(value);
        }
        catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            throw new MojoExecutionException("Unsupported Coco codegen encoding: " + value, ex);
        }
    }

    private String templateLocation() {
        return this.templateLocation == null || this.templateLocation.isBlank()
                ? DEFAULT_TEMPLATE_LOCATION : this.templateLocation.trim();
    }

    private void logTargets(List<Path> targets) {
        String action = this.dryRun ? "Would generate " : "Generated ";
        for (Path target : targets) {
            getLog().info(action + target);
        }
        getLog().info((this.dryRun ? "Coco codegen dry-run planned " : "Coco codegen generated ")
                + targets.size() + " files.");
    }
}
