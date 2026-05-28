package pl.szajsjem;

import com.snnl.Net;
import pl.szajsjem.elements.Node;
import pl.szajsjem.snnl.SnnlMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NetworkStructureSerializer {
    private final List<Node> nodes;
    private final Map<Node, CompositeLayer> processedNodes = new HashMap<>();
    private final Map<String, String> leafSerializationCache = new HashMap<>();

    public NetworkStructureSerializer(List<Node> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public Net buildNetwork() {
        // First validate the network structure
        ConnectionManager connectionManager = new ConnectionManager(new ArrayList<>(nodes));
        List<String> errors = connectionManager.validateNetwork();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid network structure: " + String.join(", ", errors));
        }

        // Get the list of layers in correct order using serializeNetwork
        List<CompositeLayer> layers = serializeNetwork(connectionManager);
        if (layers.isEmpty()) {
            throw new IllegalStateException("Network must contain at least one layer");
        }

        return loadSerializedNetwork(layers);
    }

    private Net loadSerializedNetwork(List<CompositeLayer> layers) {
        RuntimeException lastFailure = null;
        for (int[] inputShape : candidateInputShapes(layers)) {
            try {
                Net network = tryLoadNetwork(layers, inputShape);
                if (network != null) {
                    return network;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("Could not construct an SNNL model from the current graph");
    }

    private Net tryLoadNetwork(List<CompositeLayer> layers, int[] inputShape) {
        try {
            Path provisionalModel = writeModelFile(layers, inputShape, new float[0]);
            Net network = new Net();
            if (network.load(provisionalModel.toString())) {
                return network;
            }

            float[] params = network.getParams();
            if (params.length == 0) {
                network.close();
                return null;
            }

            Path populatedModel = writeModelFile(layers, inputShape, params);
            if (network.load(populatedModel.toString())) {
                return network;
            }

            network.close();
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare temporary SNNL model: " + exception.getMessage(), exception);
        }
    }

    private Path writeModelFile(List<CompositeLayer> layers, int[] inputShape, float[] params) throws IOException {
        Path file = Files.createTempFile("jnnbuilder-snnl-", ".snnl");
        file.toFile().deleteOnExit();

        StringBuilder builder = new StringBuilder();
        builder.append("SNNL_SERIALIZED\n");
        builder.append("1\n");
        builder.append("0\n");
        appendShape(builder, inputShape);
        appendShape(builder, inputShape);
        builder.append(layers.size()).append('\n');
        for (CompositeLayer layer : layers) {
            builder.append(serializeCompositeLayer(layer));
        }
        builder.append(params.length).append('\n');
        for (float param : params) {
            builder.append(param).append(' ');
        }
        builder.append('\n');

        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private void appendShape(StringBuilder builder, int[] shape) {
        builder.append(shape.length);
        for (int dim : shape) {
            builder.append(' ').append(dim);
        }
        builder.append('\n');
    }

    public List<CompositeLayer> serializeNetwork(ConnectionManager cm) {
        // Find input nodes (nodes with no inputs except in special subgraphs)
        List<Node> inputNodes = nodes.stream()
                .filter(n -> n.prev.connected.isEmpty() && !cm.isInSpecialSubgraph(n))
                .toList();

        if (inputNodes.isEmpty()) {
            throw new IllegalStateException("Network must have at least one input node");
        }

        List<CompositeLayer> topLevelLayers = new ArrayList<>();

        // If there's only one input node, process it normally
        if (inputNodes.size() == 1) {
            topLevelLayers.add(processNode(inputNodes.get(0)));
        } else {
            // For multiple input nodes, find where they converge
            Map<Node, List<Node>> convergencePoints = findInputConvergencePoints(inputNodes);

            if (convergencePoints.isEmpty()) {
                // No convergence - use default parallel structure
                CompositeLayer parallel = new CompositeLayer("parallel", null, "concat");
                for (Node inputNode : inputNodes) {
                    parallel.children.add(processNode(inputNode));
                }
                topLevelLayers.add(parallel);
            } else {
                // Process paths to each convergence point
                for (Map.Entry<Node, List<Node>> entry : convergencePoints.entrySet()) {
                    Node convergenceNode = entry.getKey();
                    List<Node> pathNodes = entry.getValue();

                    CompositeLayer parallel;
                    if (isLayerParallel(convergenceNode)) {
                        // Use layer's native parallel processing
                        parallel = new CompositeLayer("parallel", convergenceNode,
                                getLayerReductionType(convergenceNode));
                    } else {
                        // Default to concat for non-parallel layers
                        parallel = new CompositeLayer("parallel", null, "concat");
                    }

                    for (Node pathNode : pathNodes) {
                        parallel.children.add(processPath(pathNode, convergenceNode));
                    }
                    topLevelLayers.add(parallel);
                }
            }
        }

        return topLevelLayers;
    }

    private CompositeLayer processNode(Node node) {
        // Check if we've already processed this node
        if (processedNodes.containsKey(node)) {
            return processedNodes.get(node);
        }

        // Create layer for current node
        CompositeLayer layer = new CompositeLayer(node.getType(), node);
        processedNodes.put(node, layer);

        // Process outputs
        List<Node> nextNodes = node.next.connected.stream()
                .map(cp -> cp.parent)
                .toList();

        if (nextNodes.size() == 1) {
            // Single output - series connection
            layer.children.add(processNode(nextNodes.get(0)));
        } else if (nextNodes.size() > 1) {
            // Multiple outputs - check for paths that converge
            Map<Node, List<Node>> convergencePoints = findConvergencePoints(node, nextNodes);

            if (!convergencePoints.isEmpty()) {
                // Create parallel paths up to convergence points
                for (Map.Entry<Node, List<Node>> entry : convergencePoints.entrySet()) {
                    Node convergencePoint = entry.getKey();
                    List<Node> pathNodes = entry.getValue();

                    if (pathNodes.size() == 1) {
                        // Single path to convergence point
                        layer.children.add(processPath(pathNodes.get(0), convergencePoint));
                    } else {
                        // Multiple paths to convergence point - create parallel structure
                        CompositeLayer parallel = new CompositeLayer("parallel", null, "sum");
                        for (Node pathNode : pathNodes) {
                            parallel.children.add(processPath(pathNode, convergencePoint));
                        }
                        layer.children.add(parallel);
                    }
                }
            }
        }

        return layer;
    }

    private CompositeLayer processPath(Node start, Node end) {
        // If start and end are the same, just return the node
        if (start == end) {
            return processNode(start);
        }

        // Create series of layers from start to end
        CompositeLayer current = processNode(start);
        Node currentNode = start;

        while (currentNode != end) {
            List<Node> nextNodes = currentNode.next.connected.stream()
                    .map(cp -> cp.parent)
                    .filter(n -> canReachNode(n, end, new HashSet<>()))
                    .toList();

            if (nextNodes.isEmpty()) {
                break;
            }

            currentNode = nextNodes.get(0);
            current.children.add(processNode(currentNode));
        }

        return current;
    }

    private String serializeCompositeLayer(CompositeLayer compositeLayer) {
        if ("parallel".equals(compositeLayer.type)) {
            List<String> childSerializations = new ArrayList<>();
            for (CompositeLayer child : compositeLayer.children) {
                childSerializations.add(serializeCompositeLayer(child));
            }
            return serializeParallelLayer(compositeLayer.reduction, childSerializations);
        }

        if (compositeLayer.sourceNode == null) {
            List<String> childSerializations = new ArrayList<>();
            for (CompositeLayer child : compositeLayer.children) {
                childSerializations.add(serializeCompositeLayer(child));
            }
            return serializeSequentialLayer(childSerializations);
        }

        List<String> serializations = new ArrayList<>();
        serializations.add(serializeLeafLayer(compositeLayer.sourceNode));
        for (CompositeLayer child : compositeLayer.children) {
            serializations.add(serializeCompositeLayer(child));
        }
        return serializeSequentialLayer(serializations);
    }

    private String serializeSequentialLayer(List<String> childSerializations) {
        if (childSerializations.isEmpty()) {
            throw new IllegalStateException("Sequential container cannot be empty");
        }
        if (childSerializations.size() == 1) {
            return childSerializations.getFirst();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Sequential\n");
        builder.append(childSerializations.size()).append('\n');
        for (String child : childSerializations) {
            builder.append(child);
        }
        return builder.toString();
    }

    private String serializeParallelLayer(String reduction, List<String> childSerializations) {
        if (childSerializations.isEmpty()) {
            throw new IllegalStateException("Parallel container cannot be empty");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Parallel\n");
        builder.append(normalizeReduction(reduction)).append('\n');
        builder.append("mt:0\n");
        builder.append(childSerializations.size()).append('\n');
        for (String child : childSerializations) {
            builder.append(child);
        }
        return builder.toString();
    }

    private String serializeLeafLayer(Node node) {
        String normalizedType = SnnlMetadata.normalizeLayerType(node.getType());
        float[] floatParams = node.getFloatParams();
        String stringArgs = SnnlMetadata.resolveStringArgs(normalizedType, floatParams, node.getStringParams());
        String cacheKey = normalizedType + "|" + Arrays.toString(floatParams) + "|" + stringArgs;
        String cached = leafSerializationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try (Net layerNet = new Net()) {
            if (!layerNet.addLayer(normalizedType, floatParams, stringArgs)) {
                throw new IllegalStateException("SNNL rejected layer '" + normalizedType + "'");
            }

            Path modelFile = Files.createTempFile("jnnbuilder-snnl-layer-", ".snnl");
            modelFile.toFile().deleteOnExit();
            if (!layerNet.save(modelFile.toString())) {
                for (int[] shape : candidateInputShapes(node)) {
                    if (layerNet.init(shape) && layerNet.save(modelFile.toString())) {
                        String serialized = extractLayerSerialization(modelFile);
                        leafSerializationCache.put(cacheKey, serialized);
                        return serialized;
                    }
                }
                throw new IllegalStateException("Could not serialize SNNL layer '" + normalizedType + "'");
            }

            String serialized = extractLayerSerialization(modelFile);
            leafSerializationCache.put(cacheKey, serialized);
            return serialized;
        } catch (IOException exception) {
            throw new IllegalStateException("Error serializing SNNL layer '" + normalizedType + "': " + exception.getMessage(), exception);
        }
    }

    private String extractLayerSerialization(Path modelFile) throws IOException {
        List<String> lines = Files.readAllLines(modelFile, StandardCharsets.UTF_8);
        if (lines.size() < 8) {
            throw new IOException("Serialized SNNL model is missing layer content");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 6; i < lines.size() - 2; i++) {
            builder.append(lines.get(i)).append('\n');
        }
        return builder.toString();
    }

    private List<int[]> candidateInputShapes(List<CompositeLayer> layers) {
        List<int[]> candidates = new ArrayList<>();
        Node firstNode = firstSourceNode(layers);
        if (firstNode != null) {
            addCandidateShape(candidates, guessInputShape(firstNode));
        }
        addCandidateShape(candidates, new int[]{1, 1});
        addCandidateShape(candidates, new int[]{1, 1, 1});
        addCandidateShape(candidates, new int[]{1, 1, 1, 1});
        return candidates;
    }

    private List<int[]> candidateInputShapes(Node node) {
        List<int[]> candidates = new ArrayList<>();
        addCandidateShape(candidates, guessInputShape(node));
        addCandidateShape(candidates, new int[]{1, 1});
        addCandidateShape(candidates, new int[]{1, 1, 1});
        addCandidateShape(candidates, new int[]{1, 1, 1, 1});
        return candidates;
    }

    private void addCandidateShape(List<int[]> candidates, int[] candidate) {
        if (candidate == null || candidate.length == 0) {
            return;
        }
        for (int[] existing : candidates) {
            if (Arrays.equals(existing, candidate)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private Node firstSourceNode(List<CompositeLayer> layers) {
        for (CompositeLayer layer : layers) {
            Node node = firstSourceNode(layer);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private Node firstSourceNode(CompositeLayer layer) {
        if (layer.sourceNode != null) {
            return layer.sourceNode;
        }
        for (CompositeLayer child : layer.children) {
            Node node = firstSourceNode(child);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private int[] guessInputShape(Node node) {
        String[] usageLines = SnnlMetadata.getLayerUsage(node.getType()).split("\n", -1);
        String[] numericDescriptions = usageLines.length > 2 ? usageLines[2].split(";") : new String[0];
        float[] floatParams = node.getFloatParams();

        if (numericDescriptions.length >= 3 &&
                containsToken(numericDescriptions[0], "rows") &&
                containsToken(numericDescriptions[1], "cols") &&
                containsToken(numericDescriptions[2], "channels")) {
            return positiveShape(
                    1,
                    floatParam(floatParams, 0, 1),
                    floatParam(floatParams, 1, 1),
                    floatParam(floatParams, 2, 1)
            );
        }

        if (numericDescriptions.length >= 1 && containsToken(numericDescriptions[0], "inputsize")) {
            return positiveShape(1, floatParam(floatParams, 0, 1));
        }

        if (numericDescriptions.length >= 1 && containsToken(numericDescriptions[0], "shape")) {
            int[] dynamicShape = new int[Math.max(1, floatParams.length)];
            for (int i = 0; i < dynamicShape.length; i++) {
                dynamicShape[i] = floatParam(floatParams, i, 1);
            }
            return positiveShape(dynamicShape);
        }

        if (floatParams.length > 0) {
            return positiveShape(1, floatParam(floatParams, 0, 1));
        }

        return new int[]{1, 1};
    }

    private int[] positiveShape(int... dims) {
        int[] shape = dims.clone();
        for (int i = 0; i < shape.length; i++) {
            shape[i] = Math.max(1, shape[i]);
        }
        return shape;
    }

    private int floatParam(float[] params, int index, int fallback) {
        if (index >= params.length) {
            return fallback;
        }
        return Math.max(1, Math.round(params[index]));
    }

    private boolean containsToken(String value, String token) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains(token);
    }

    private String normalizeReduction(String reduction) {
        String normalized = SnnlMetadata.normalizeReduction(reduction == null ? "Sum" : reduction);
        return normalized == null || normalized.isBlank() ? "Sum" : normalized;
    }

    private Map<Node, List<Node>> findConvergencePoints(Node start, List<Node> nextNodes) {
        Map<Node, List<Node>> convergencePoints = new HashMap<>();

        // Find all nodes reachable from each next node
        for (Node nextNode : nextNodes) {
            Set<Node> reachableNodes = findReachableNodes(nextNode);

            // Check which nodes are reachable from other paths
            for (Node other : nextNodes) {
                if (other != nextNode) {
                    Set<Node> otherReachable = findReachableNodes(other);

                    // Find common reachable nodes (convergence points)
                    Set<Node> common = new HashSet<>(reachableNodes);
                    common.retainAll(otherReachable);

                    for (Node convergence : common) {
                        convergencePoints.computeIfAbsent(convergence, k -> new ArrayList<>())
                                .add(nextNode);
                    }
                }
            }
        }

        return convergencePoints;
    }

    private Set<Node> findReachableNodes(Node start) {
        Set<Node> reachable = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (reachable.add(current)) {
                current.next.connected.stream()
                        .map(cp -> cp.parent)
                        .forEach(queue::add);
            }
        }

        return reachable;
    }

    private boolean canReachNode(Node start, Node target, Set<Node> visited) {
        if (start == target) {
            return true;
        }

        if (!visited.add(start)) {
            return false;
        }

        return start.next.connected.stream()
                .map(cp -> cp.parent)
                .anyMatch(n -> canReachNode(n, target, visited));
    }

    private Map<Node, List<Node>> findInputConvergencePoints(List<Node> inputNodes) {
        Map<Node, List<Node>> convergencePoints = new HashMap<>();

        // Find all nodes reachable from each input node
        Map<Node, Set<Node>> reachableNodesMap = new HashMap<>();
        for (Node inputNode : inputNodes) {
            reachableNodesMap.put(inputNode, findReachableNodes(inputNode));
        }

        // Find nodes that are reachable from multiple inputs
        Set<Node> allReachableNodes = new HashSet<>();
        reachableNodesMap.values().forEach(allReachableNodes::addAll);

        for (Node reachable : allReachableNodes) {
            List<Node> converging = new ArrayList<>();
            for (Node inputNode : inputNodes) {
                if (reachableNodesMap.get(inputNode).contains(reachable)) {
                    converging.add(inputNode);
                }
            }

            if (converging.size() > 1) {
                // Check if this is the first convergence point in each path
                boolean isFirstConvergence = true;
                for (Node input : converging) {
                    if (hasEarlierConvergence(input, reachable, reachableNodesMap)) {
                        isFirstConvergence = false;
                        break;
                    }
                }

                if (isFirstConvergence) {
                    convergencePoints.put(reachable, converging);
                }
            }
        }

        return convergencePoints;
    }

    private boolean hasEarlierConvergence(Node start, Node target,
                                          Map<Node, Set<Node>> reachableNodesMap) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current == target) {
                return false;
            }

            if (visited.add(current)) {
                // Check if current node is a convergence point
                if (current != start && reachableNodesMap.entrySet().stream()
                        .filter(e -> e.getKey() != start)
                        .anyMatch(e -> e.getValue().contains(current))) {
                    return true;
                }

                // Add next nodes
                current.next.connected.stream()
                        .map(cp -> cp.parent)
                        .forEach(queue::add);
            }
        }

        return false;
    }

    private boolean isLayerParallel(Node node) {
        String type = node.getType().toLowerCase();
        return type.contains("parallel") ||
                type.contains("concat") ||
                type.contains("sum") ||
                type.contains("average");
    }

    private String getLayerReductionType(Node node) {
        String type = node.getType().toLowerCase();
        if (type.contains("sum")) return "sum";
        if (type.contains("average")) return "average";
        if (type.contains("concat")) return "concat";
        // Default reduction for parallel layers
        return "sum";
    }

    public static class CompositeLayer {
        public final String type;
        public final List<CompositeLayer> children = new ArrayList<>();
        public final Node sourceNode;
        public final String reduction;

        public CompositeLayer(String type, Node sourceNode) {
            this(type, sourceNode, null);
        }

        public CompositeLayer(String type, Node sourceNode, String reduction) {
            this.type = type;
            this.sourceNode = sourceNode;
            this.reduction = reduction;
        }
    }
}
