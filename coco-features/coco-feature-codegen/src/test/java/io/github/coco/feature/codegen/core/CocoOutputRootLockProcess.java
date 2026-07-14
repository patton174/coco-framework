package io.github.coco.feature.codegen.core;

import java.nio.file.Files;
import java.nio.file.Path;

final class CocoOutputRootLockProcess {

    private CocoOutputRootLockProcess() {
    }

    public static void main(String[] arguments) throws Exception {
        String command = arguments[0];
        Path root = Path.of(arguments[1]);
        try {
            if ("hold".equals(command)) {
                Path ready = Path.of(arguments[2]);
                Path release = Path.of(arguments[3]);
                try (CocoOutputRootLock ignored = CocoOutputRootLock.acquire(root)) {
                    Files.writeString(ready, "locked");
                    while (!Files.exists(release)) {
                        Thread.sleep(25);
                    }
                }
                return;
            }
            try (CocoOutputRootLock lock = CocoOutputRootLock.acquire(root)) {
                if ("check-marker".equals(command)) {
                    lock.requireNoRecoveryMarker();
                }
                System.out.println("acquired");
            }
        }
        catch (CocoCodegenException ex) {
            System.out.println(ex.getMessage());
            System.exit("check-marker".equals(command) ? 3 : 2);
        }
    }
}
