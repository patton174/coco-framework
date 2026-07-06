package io.github.coco.spring.boot.logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.env.Environment;

/**
 * Coco Node 终端日志渲染器启动器。
 * <p>
 * 在 jar 启动场景中自动启动内置 Node.js 渲染脚本，并将 JVM 控制台输出交给渲染器统一展示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoNodeLogRendererBootstrap {

    static final String RESOURCE_PATH = "/META-INF/coco/coco-log-renderer.mjs";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private CocoNodeLogRendererBootstrap() {
    }

    /**
     * <p>
     * 根据环境配置尝试安装 Node 终端日志渲染器。
     * </p>
     * @param environment Spring 环境
     */
    public static void install(Environment environment) {
        if (!shouldInstall(environment, System.getProperty("sun.java.command"))) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        PrintStream originalOut = System.out;
        try {
            Path rendererScript = extractRendererScript();
            Process process = startRenderer(environment, rendererScript);
            pipe(process.getInputStream(), originalOut, "coco-node-log-renderer-out");
            pipe(process.getErrorStream(), System.err, "coco-node-log-renderer-err");

            PrintStream proxy = new PrintStream(new FallbackOutputStream(process.getOutputStream(), originalOut),
                    true, StandardCharsets.UTF_8);
            System.setOut(proxy);
            System.setErr(proxy);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(process), "coco-node-log-renderer-stop"));
        }
        catch (RuntimeException | IOException ex) {
            INSTALLED.set(false);
        }
    }

    static boolean shouldInstall(Environment environment, String javaCommand) {
        boolean enabled = environment.getProperty("coco.logging.node-renderer.enabled", Boolean.class, true);
        if (!enabled) {
            return false;
        }
        boolean jarOnly = environment.getProperty("coco.logging.node-renderer.jar-only", Boolean.class, true);
        return !jarOnly || isJarLaunch(javaCommand);
    }

    static boolean isJarLaunch(String javaCommand) {
        if (javaCommand == null || javaCommand.isBlank()) {
            return false;
        }
        return javaCommand.trim().toLowerCase(Locale.ROOT).matches(".*\\.jar(\\s.*)?");
    }

    static List<String> rendererCommand(Environment environment, Path rendererScript) {
        return rendererCommand(environment, rendererScript, nodeCommand(environment));
    }

    private static List<String> rendererCommand(Environment environment, Path rendererScript, String nodeCommand) {
        List<String> command = new ArrayList<>();
        command.add(nodeCommand);
        command.add(rendererScript.toString());

        String color = colorMode(environment);
        if ("never".equals(color)) {
            command.add("--no-color");
        }
        else if (!"auto".equals(color)) {
            command.add("--color=always");
        }
        return command;
    }

    private static String nodeCommand(Environment environment) {
        String command = environment.getProperty("coco.logging.node-renderer.command", "node");
        return command == null || command.isBlank() ? "node" : command.trim();
    }

    private static String colorMode(Environment environment) {
        String color = environment.getProperty("coco.logging.node-renderer.color", "always");
        if (color == null || color.isBlank()) {
            return "always";
        }
        return color.trim().toLowerCase(Locale.ROOT);
    }

    private static Path extractRendererScript() throws IOException {
        try (InputStream input = CocoNodeLogRendererBootstrap.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Coco Node log renderer resource not found: " + RESOURCE_PATH);
            }
            Path script = Files.createTempFile("coco-log-renderer-", ".mjs");
            Files.copy(input, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            script.toFile().deleteOnExit();
            return script;
        }
    }

    private static Process startRenderer(Environment environment, Path rendererScript) throws IOException {
        List<String> command = rendererCommand(environment, rendererScript);
        try {
            return new ProcessBuilder(command).start();
        }
        catch (IOException ex) {
            String fallbackNodeCommand = fallbackNodeCommand(environment);
            if (fallbackNodeCommand == null) {
                throw ex;
            }
            try {
                return new ProcessBuilder(rendererCommand(environment, rendererScript, fallbackNodeCommand)).start();
            }
            catch (IOException fallbackEx) {
                ex.addSuppressed(fallbackEx);
                throw ex;
            }
        }
    }

    private static String fallbackNodeCommand(Environment environment) {
        String configuredCommand = nodeCommand(environment);
        if (!"node".equalsIgnoreCase(configuredCommand)) {
            return null;
        }
        return findExistingNodeExecutable();
    }

    private static String findExistingNodeExecutable() {
        List<Path> candidates = new ArrayList<>();
        addNodeCandidate(candidates, System.getenv("COCO_NODE_COMMAND"));
        addNodeCandidate(candidates, System.getenv("ProgramFiles") + "\\nodejs");
        addNodeCandidate(candidates, System.getenv("ProgramFiles(x86)") + "\\nodejs");
        addNodeCandidate(candidates, System.getenv("LOCALAPPDATA") + "\\Programs\\nodejs");
        addNodeCandidate(candidates, "D:\\Program Files\\nodejs");
        addNodeCandidate(candidates, "D:\\Programs\\nodejs");
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static void addNodeCandidate(List<Path> candidates, String value) {
        if (value == null || value.isBlank() || value.contains("null")) {
            return;
        }
        try {
            Path candidate = Path.of(value.trim());
            if (Files.isDirectory(candidate)) {
                candidate = candidate.resolve(isWindows() ? "node.exe" : "node");
            }
            candidates.add(candidate);
        }
        catch (InvalidPathException ex) {
            // Node 渲染器是可选增强，忽略不可解析的候选路径。
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void pipe(InputStream input, OutputStream output, String threadName) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(output);
                output.flush();
            }
            catch (IOException ex) {
                // 终端渲染器是可选增强，管道结束时不向业务进程追加噪音。
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private static void stop(Process process) {
        try {
            process.getOutputStream().close();
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroy();
            }
        }
        catch (IOException ex) {
            process.destroy();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroy();
        }
    }

    private static final class FallbackOutputStream extends OutputStream {

        private final OutputStream delegate;

        private final PrintStream fallback;

        private boolean delegateAvailable = true;

        private FallbackOutputStream(OutputStream delegate, PrintStream fallback) {
            this.delegate = delegate;
            this.fallback = fallback;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (this.delegateAvailable) {
                try {
                    this.delegate.write(value);
                    return;
                }
                catch (IOException ex) {
                    this.delegateAvailable = false;
                }
            }
            this.fallback.write(value);
        }

        @Override
        public synchronized void write(byte[] content, int offset, int length) throws IOException {
            if (this.delegateAvailable) {
                try {
                    this.delegate.write(content, offset, length);
                    return;
                }
                catch (IOException ex) {
                    this.delegateAvailable = false;
                }
            }
            this.fallback.write(content, offset, length);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (this.delegateAvailable) {
                try {
                    this.delegate.flush();
                    return;
                }
                catch (IOException ex) {
                    this.delegateAvailable = false;
                }
            }
            this.fallback.flush();
        }
    }
}
