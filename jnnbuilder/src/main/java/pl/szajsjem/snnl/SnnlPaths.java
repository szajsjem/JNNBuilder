package pl.szajsjem.snnl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class SnnlPaths {
    private static volatile Path cachedRoot;

    private SnnlPaths() {
    }

    public static String nativeLibraryBaseName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "snnl_java_binding";
        }
        if (os.contains("mac")) {
            return "snnl_java_binding";
        }
        return "snnl_java_binding";
    }

    public static Path findRoot() {
        Path root = cachedRoot;
        if (root != null && Files.isDirectory(root)) {
            return root;
        }

        String override = System.getenv("SNNL_HOME");
        if (override != null && !override.isBlank()) {
            Path candidate = Path.of(override).toAbsolutePath().normalize();
            if (looksLikeSnnlRoot(candidate)) {
                cachedRoot = candidate;
                return candidate;
            }
        }

        for (Path base : searchBases()) {
            for (String relative : List.of("../SNNL", "SNNL")) {
                Path candidate = base.resolve(relative).normalize().toAbsolutePath();
                if (looksLikeSnnlRoot(candidate)) {
                    cachedRoot = candidate;
                    return candidate;
                }
            }
        }

        return null;
    }

    public static Path findSourceRoot() {
        Path root = findRoot();
        if (root == null) {
            return null;
        }

        Path sourceRoot = root.resolve("src");
        return Files.isDirectory(sourceRoot) ? sourceRoot : null;
    }

    public static Path findBindingLibrary() {
        Path root = findRoot();
        if (root == null) {
            return null;
        }

        for (String relative : bindingLibraryCandidates()) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean looksLikeSnnlRoot(Path candidate) {
        return Files.isDirectory(candidate) &&
                Files.isDirectory(candidate.resolve("src")) &&
                Files.isDirectory(candidate.resolve("binding_java"));
    }

    private static List<Path> searchBases() {
        List<Path> bases = new ArrayList<>();
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            bases.add(current);
            current = current.getParent();
        }
        return bases;
    }

    private static List<String> bindingLibraryCandidates() {
        String fileName = libraryFileName();
        return List.of(
                "build_release/install/bindings/java/" + fileName,
                "build_release/binding_java/Release/" + fileName,
                "binding_java/Release/" + fileName
        );
    }

    private static String libraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "snnl_java_binding.dll";
        }
        if (os.contains("mac")) {
            return "libsnnl_java_binding.dylib";
        }
        return "libsnnl_java_binding.so";
    }
}
