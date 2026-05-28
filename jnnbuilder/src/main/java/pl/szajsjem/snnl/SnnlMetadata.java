package pl.szajsjem.snnl;

import com.snnl.Net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnnlMetadata {
    private static final Pattern ADD_IMPL_PATTERN = Pattern.compile(
            "addImpl\\(\"([^\"]+)\",\\s*&([A-Za-z0-9_]+)::load",
            Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z0-9_]+)\\b");
    private static final Pattern QUALIFIED_USAGE_PATTERN = Pattern.compile(
            "([A-Za-z0-9_]+)::constructUsage\\(\\)\\s*\\{(.*?)\\}",
            Pattern.DOTALL);
    private static final Pattern INLINE_USAGE_PATTERN = Pattern.compile(
            "static\\s+std::string\\s+constructUsage\\(\\)\\s*\\{(.*?)\\}",
            Pattern.DOTALL);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ACTIVATION_LIST_PATTERN = Pattern.compile(
            "vsActivations\\s*=\\s*\\{(.*?)\\}",
            Pattern.DOTALL);
    private static final Pattern INITIALIZER_LIST_PATTERN = Pattern.compile(
            "getAllInitializers\\(\\)\\s*\\{\\s*return\\s*\\{(.*?)\\};",
            Pattern.DOTALL);

    private static final List<String> REDUCTIONS = List.of(
            "Sum", "Average", "ColStack", "RowStack", "MatMul", "Multiply"
    );
    private static volatile Snapshot snapshot;

    private SnnlMetadata() {
    }

    public static String[] getAvailableLayers() {
        return snapshot().layers();
    }

    public static String getLayerUsage(String type) {
        String usage = snapshot().layerUsages().get(normalizeLayerType(type));
        return usage != null ? usage : "\n\n";
    }

    public static String[] getAvailableActivations() {
        return snapshot().activations();
    }

    public static String[] getAvailableInitializers() {
        return snapshot().initializers();
    }

    public static String[] getAvailableReductions() {
        return REDUCTIONS.toArray(String[]::new);
    }

    public static String[] getAvailableLosses() {
        return snapshot().losses();
    }

    public static String[] getAvailableOptimizers() {
        return snapshot().optimizers();
    }

    public static String[] getAvailableRegularizers() {
        return snapshot().regularizers();
    }

    public static String normalizeLayerType(String type) {
        if (type == null || type.isBlank()) {
            return type;
        }

        Snapshot snapshot = snapshot();
        if (snapshot.layerUsages().containsKey(type)) {
            return type;
        }

        String trimmed = type.trim();
        if ("LayerRepetetive".equals(trimmed)) {
            return "Repetitive";
        }
        if (trimmed.startsWith("Layer") && trimmed.length() > "Layer".length()) {
            String candidate = trimmed.substring("Layer".length());
            if (snapshot.layerUsages().containsKey(candidate)) {
                return candidate;
            }
        }

        return normalizeAgainstCollection(trimmed, snapshot.layerUsages().keySet());
    }

    public static String normalizeActivation(String activation) {
        return normalizeAgainstCollection(activation, Arrays.asList(getAvailableActivations()));
    }

    public static String normalizeInitializer(String initializer) {
        return normalizeAgainstCollection(initializer, Arrays.asList(getAvailableInitializers()));
    }

    public static String normalizeReduction(String reduction) {
        if (reduction == null || reduction.isBlank()) {
            return reduction;
        }

        return switch (reduction.trim().toLowerCase(Locale.ROOT)) {
            case "sum" -> "Sum";
            case "average", "avg" -> "Average";
            case "concat", "colstack", "columnstack" -> "ColStack";
            case "rowstack" -> "RowStack";
            case "matmul", "dot" -> "MatMul";
            case "multiply", "mul" -> "Multiply";
            default -> normalizeAgainstCollection(reduction, REDUCTIONS);
        };
    }

    public static String normalizeLoss(String loss) {
        return normalizeAgainstCollection(loss, Arrays.asList(getAvailableLosses()));
    }

    public static String normalizeOptimizer(String optimizer) {
        return normalizeAgainstCollection(optimizer, Arrays.asList(getAvailableOptimizers()));
    }

    public static String normalizeRegularizer(String regularizer) {
        return normalizeAgainstCollection(regularizer, Arrays.asList(getAvailableRegularizers()));
    }

    public static String[] normalizeStringParams(String layerType, String[] stringParams) {
        String[] normalized = stringParams == null ? new String[0] : stringParams.clone();
        if (normalized.length == 0) {
            return normalized;
        }

        String[] usageLines = getLayerUsage(layerType).split("\n", -1);
        String[] descriptions = usageLines.length > 1 ? usageLines[1].split(";") : new String[0];
        for (int i = 0; i < normalized.length && i < descriptions.length; i++) {
            String description = descriptions[i].trim().toLowerCase(Locale.ROOT);
            if (description.contains("activation")) {
                normalized[i] = normalizeActivation(normalized[i]);
            } else if (description.contains("initializer")) {
                normalized[i] = normalizeInitializer(normalized[i]);
            } else if (description.contains("reduction")) {
                normalized[i] = normalizeReduction(normalized[i]);
            }
        }

        return normalized;
    }

    public static String resolveStringArgs(String layerType, float[] floatParams, String[] stringParams) {
        String[] normalized = normalizeStringParams(layerType, stringParams);
        if (normalized.length == 0) {
            return "";
        }
        if (normalized.length == 1) {
            return normalized[0];
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(String.join("|", normalized));
        String semicolonJoined = String.join(";", normalized);
        if (!semicolonJoined.equals(candidates.getFirst())) {
            candidates.add(semicolonJoined);
        }

        String normalizedType = normalizeLayerType(layerType);
        for (String candidate : candidates) {
            if (canConstructLayer(normalizedType, floatParams, candidate)) {
                return candidate;
            }
        }

        return candidates.getFirst();
    }

    public static boolean supportsLayerType(String layerType) {
        return snapshot().layerUsages().containsKey(normalizeLayerType(layerType));
    }

    private static boolean canConstructLayer(String layerType, float[] floatParams, String stringArgs) {
        try (Net net = new Net()) {
            return net.addLayer(layerType, floatParams, stringArgs);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Snapshot snapshot() {
        Snapshot cached = snapshot;
        if (cached != null) {
            return cached;
        }

        synchronized (SnnlMetadata.class) {
            if (snapshot == null) {
                snapshot = loadSnapshot();
            }
            return snapshot;
        }
    }

    private static Snapshot loadSnapshot() {
        Path sourceRoot = SnnlPaths.findSourceRoot();
        if (sourceRoot == null) {
            return Snapshot.empty();
        }

        try {
            Map<String, String> constructUsages = scanConstructUsages(sourceRoot);

            Map<String, String> layerUsages = parseFactoryFile(
                    sourceRoot.resolve("layers").resolve("LayerFactoryRegistry.cpp"),
                    constructUsages);
            Map<String, String> lossUsages = parseFactoryFile(
                    sourceRoot.resolve("loss").resolve("loss.cpp"),
                    constructUsages);
            Map<String, String> optimizerUsages = parseFactoryFile(
                    sourceRoot.resolve("optimizer").resolve("Optimizer.cpp"),
                    constructUsages);
            Map<String, String> regularizerUsages = parseFactoryFile(
                    sourceRoot.resolve("regularizer").resolve("Regularizer.cpp"),
                    constructUsages);

            String[] activations = parseListFromFile(
                    sourceRoot.resolve("layers").resolve("unary").resolve("Activation.h"),
                    ACTIVATION_LIST_PATTERN);
            String[] initializers = parseListFromFile(
                    sourceRoot.resolve("initializers").resolve("Initializers.h"),
                    INITIALIZER_LIST_PATTERN);

            return new Snapshot(
                    sortedKeys(layerUsages),
                    Collections.unmodifiableMap(layerUsages),
                    activations,
                    initializers,
                    sortedKeys(lossUsages),
                    sortedKeys(optimizerUsages),
                    sortedKeys(regularizerUsages)
            );
        } catch (IOException exception) {
            return Snapshot.empty();
        }
    }

    private static Map<String, String> scanConstructUsages(Path sourceRoot) throws IOException {
        Map<String, String> usages = new HashMap<>();
        List<Path> files;
        try (var stream = Files.walk(sourceRoot)) {
            files = stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(".h") || fileName.endsWith(".cpp");
                    })
                    .toList();
        }

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            parseQualifiedUsages(content, usages);
            parseInlineUsages(content, usages);
        }

        return usages;
    }

    private static void parseQualifiedUsages(String content, Map<String, String> usages) {
        Matcher matcher = QUALIFIED_USAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            usages.putIfAbsent(matcher.group(1), decodeStringLiterals(matcher.group(2)));
        }
    }

    private static void parseInlineUsages(String content, Map<String, String> usages) {
        List<ClassMarker> classes = new ArrayList<>();
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            classes.add(new ClassMarker(classMatcher.group(1), classMatcher.start()));
        }

        Matcher matcher = INLINE_USAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            String className = classNameBefore(classes, matcher.start());
            if (className != null) {
                usages.putIfAbsent(className, decodeStringLiterals(matcher.group(1)));
            }
        }
    }

    private static String classNameBefore(List<ClassMarker> classes, int index) {
        String className = null;
        for (ClassMarker marker : classes) {
            if (marker.index() >= index) {
                break;
            }
            className = marker.name();
        }
        return className;
    }

    private static Map<String, String> parseFactoryFile(Path path,
                                                        Map<String, String> constructUsages) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Matcher matcher = ADD_IMPL_PATTERN.matcher(content);
        Map<String, String> usages = new LinkedHashMap<>();
        while (matcher.find()) {
            String externalName = matcher.group(1);
            String className = matcher.group(2);
            usages.put(externalName, constructUsages.getOrDefault(className, "\n\n"));
        }
        return usages;
    }

    private static String[] parseListFromFile(Path path, Pattern pattern) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return new String[0];
        }

        Set<String> values = new LinkedHashSet<>();
        Matcher literalMatcher = STRING_LITERAL_PATTERN.matcher(matcher.group(1));
        while (literalMatcher.find()) {
            values.add(unescapeCppString(literalMatcher.group(1)));
        }
        return values.toArray(String[]::new);
    }

    private static String decodeStringLiterals(String body) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = STRING_LITERAL_PATTERN.matcher(body);
        while (matcher.find()) {
            builder.append(unescapeCppString(matcher.group(1)));
        }
        return builder.toString();
    }

    private static String unescapeCppString(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaping) {
                builder.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '"' -> '"';
                    default -> current;
                });
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else {
                builder.append(current);
            }
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static String[] sortedKeys(Map<String, String> values) {
        return values.keySet().stream().sorted().toArray(String[]::new);
    }

    private static String normalizeAgainstCollection(String value, Collection<String> candidates) {
        if (value == null || value.isBlank()) {
            return value;
        }
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return candidate;
            }
            if (candidate.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return value;
    }

    private record Snapshot(String[] layers,
                            Map<String, String> layerUsages,
                            String[] activations,
                            String[] initializers,
                            String[] losses,
                            String[] optimizers,
                            String[] regularizers) {
        static Snapshot empty() {
            return new Snapshot(
                    new String[0],
                    Collections.emptyMap(),
                    new String[0],
                    new String[0],
                    new String[0],
                    new String[0],
                    new String[0]
            );
        }
    }

    private record ClassMarker(String name, int index) {
    }
}
