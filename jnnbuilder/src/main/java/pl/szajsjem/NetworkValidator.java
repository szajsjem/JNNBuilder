package pl.szajsjem;

import org.json.JSONObject;
import pl.szajsjem.elements.Node;
import pl.szajsjem.snnl.SnnlMetadata;
import pl.szajsjem.snnl.SnnlTrainer;

import java.util.*;

public class NetworkValidator {
    public static ValidationResult validate(List<Node> nodes, SnnlTrainer netTrain) {
        ValidationResult result = new ValidationResult();

        // Get available components from JNI
        Set<String> availableLayerTypes = new HashSet<>(Arrays.asList(SnnlMetadata.getAvailableLayers()));
        Set<String> availableActivations = new HashSet<>(Arrays.asList(SnnlMetadata.getAvailableActivations()));
        Set<String> availableInitializers = new HashSet<>(Arrays.asList(SnnlMetadata.getAvailableInitializers()));
        Set<String> availableReductions = new HashSet<>(Arrays.asList(SnnlMetadata.getAvailableReductions()));
        Set<String> availableLosses = new HashSet<>(Arrays.asList(SnnlTrainer.getAvailableLosses()));
        Set<String> availableOptimizers = new HashSet<>(Arrays.asList(SnnlTrainer.getAvailableOptimizers()));
        Set<String> availableRegularizers = new HashSet<>(Arrays.asList(SnnlTrainer.getAvailableRegularizers()));

        // Validate nodes
        for (Node node : nodes) {
            validateNode(node, availableLayerTypes, availableActivations,
                    availableInitializers, availableReductions, result);
        }

        validateTrainingSettings(netTrain, availableLosses, availableOptimizers, availableRegularizers, result);

        // Validate network structure
        validateNetworkStructure(nodes, result);

        return result;
    }

    private static void validateNode(Node node, Set<String> availableLayerTypes,
                                     Set<String> availableActivations,
                                     Set<String> availableInitializers,
                                     Set<String> availableReductions,
                                     ValidationResult result) {
        // Check if layer type exists
        String normalizedType = SnnlMetadata.normalizeLayerType(node.getType());
        if (!availableLayerTypes.contains(normalizedType)) {
            result.errors.add("Layer type '" + node.getType() + "' is not available");
            return;
        }

        // Get layer usage info to check parameters
        String usage = SnnlMetadata.getLayerUsage(normalizedType);
        String[] usageLines = usage.split("\n");

        // Validate string parameters
        String[] stringParams = node.getStringParams();
        if (usageLines.length > 1) {
            String[] stringDescs = usageLines[1].split(";");
            for (int i = 0; i < Math.min(stringParams.length, stringDescs.length); i++) {
                String desc = stringDescs[i].toLowerCase();
                String param = stringParams[i];

                String normalizedParam = normalizeParam(desc, param);
                if (desc.contains("activation") && !availableActivations.contains(normalizedParam)) {
                    result.errors.add("Activation '" + param + "' is not available for layer '" + node.getType() + "'");
                } else if (desc.contains("initializer") && !availableInitializers.contains(normalizedParam)) {
                    result.errors.add("Initializer '" + param + "' is not available for layer '" + node.getType() + "'");
                } else if (desc.contains("reduction") && !availableReductions.contains(normalizedParam)) {
                    result.errors.add("Reduction '" + param + "' is not available for layer '" + node.getType() + "'");
                }
            }
        }

        // Validate numeric parameters
        float[] floatParams = node.getFloatParams();
        if (usageLines.length > 2) {
            String[] floatDescs = usageLines[2].split(";");
            if (floatParams.length != floatDescs.length) {
                result.warnings.add("Layer '" + node.getType() + "' has " + floatParams.length +
                        " numeric parameters but expects " + floatDescs.length);
            }
        }
    }

    private static void validateTrainingSettings(SnnlTrainer netTrain,
                                                 Set<String> availableLosses,
                                                 Set<String> availableOptimizers,
                                                 Set<String> availableRegularizers,
                                                 ValidationResult result) {
        try {
            String settings = netTrain.save();
            JSONObject settingsJson = new JSONObject(settings);

            if (settingsJson.has("loss")) {
                String loss = SnnlMetadata.normalizeLoss(settingsJson.getString("loss"));
                if (!availableLosses.contains(loss)) {
                    result.errors.add("Loss function '" + loss + "' is not available");
                }
            }

            if (settingsJson.has("optimizer")) {
                String optimizer = SnnlMetadata.normalizeOptimizer(settingsJson.getString("optimizer"));
                if (!availableOptimizers.contains(optimizer)) {
                    result.errors.add("Optimizer '" + optimizer + "' is not available");
                }
            }

            if (settingsJson.has("regularizer")) {
                String regularizer = SnnlMetadata.normalizeRegularizer(settingsJson.getString("regularizer"));
                if (!availableRegularizers.contains(regularizer)) {
                    result.errors.add("Regularizer '" + regularizer + "' is not available");
                }
            }
        } catch (Exception e) {
            result.warnings.add("Could not fully validate training settings: " + e.getMessage());
        }
    }

    private static void validateNetworkStructure(List<Node> nodes, ValidationResult result) {
        ConnectionManager connectionManager = new ConnectionManager(new ArrayList<>(nodes));
        List<String> structureErrors = connectionManager.validateNetwork();
        result.errors.addAll(structureErrors);
    }

    public static class ValidationResult {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private static String normalizeParam(String description, String value) {
        if (description.contains("activation")) {
            return SnnlMetadata.normalizeActivation(value);
        }
        if (description.contains("initializer")) {
            return SnnlMetadata.normalizeInitializer(value);
        }
        if (description.contains("reduction")) {
            return SnnlMetadata.normalizeReduction(value);
        }
        return value;
    }
}
