package com.snnl;

import pl.szajsjem.snnl.SnnlPaths;

import java.nio.file.Files;
import java.nio.file.Path;

final class NativeLoader {
    private static boolean loaded;

    private NativeLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }

        String override = System.getenv("SNNL_JAVA_BINDING");
        if (override != null && !override.isBlank()) {
            Path candidate = Path.of(override).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                System.load(candidate.toString());
                loaded = true;
                return;
            }
        }

        Path candidate = SnnlPaths.findBindingLibrary();
        if (candidate != null && Files.isRegularFile(candidate)) {
            System.load(candidate.toAbsolutePath().normalize().toString());
            loaded = true;
            return;
        }

        System.loadLibrary(SnnlPaths.nativeLibraryBaseName());
        loaded = true;
    }
}
