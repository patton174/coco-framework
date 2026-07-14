package io.github.coco.feature.codegen.core;

import java.nio.file.Files;
import java.nio.file.Path;

final class CocoOutputRootLockProcess {

    private CocoOutputRootLockProcess() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Path.of(arguments[0]);
        Path ready = Path.of(arguments[1]);
        Path release = Path.of(arguments[2]);
        try (CocoOutputRootLock ignored = CocoOutputRootLock.acquire(root)) {
            Files.writeString(ready, "locked");
            while (!Files.exists(release)) {
                Thread.sleep(25);
            }
        }
    }
}
