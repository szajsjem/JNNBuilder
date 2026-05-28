package pl.szajsjem;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.szajsjem.elements.ConnectionPoint;
import pl.szajsjem.elements.Node;
import pl.szajsjem.elements.SpecialNode;
import pl.szajsjem.snnl.SnnlMetadata;
import pl.szajsjem.snnl.SnnlTrainer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkSerializer {
    public static void saveToFile(String filePath, List<Node> nodes, SnnlTrainer netTrain) throws IOException {
        JSONObject root = new JSONObject();

        // Save nodes
        JSONArray nodesArray = new JSONArray();
        Map<Node, Integer> nodeIndices = new HashMap<>();

        // First pass: save node data and build index map
        int index = 0;
        for (Node node : nodes) {
            JSONObject nodeObj = new JSONObject();
            nodeObj.put("type", node.getType());
            nodeObj.put("x", node.x);
            nodeObj.put("y", node.y);
            nodeObj.put("stringParams", new JSONArray(node.getStringParams()));
            nodeObj.put("floatParams", new JSONArray(node.getFloatParams()));
            nodeObj.put("isSpecial", node instanceof SpecialNode);

            nodesArray.put(nodeObj);
            nodeIndices.put(node, index++);
        }

        // Second pass: save connections
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            JSONObject nodeObj = nodesArray.getJSONObject(i);

            // Save regular connections
            saveConnectionPoints(nodeObj, "prevConnections", node.prev, nodeIndices);
            saveConnectionPoints(nodeObj, "nextConnections", node.next, nodeIndices);

            // Save special connections if applicable
            if (node instanceof SpecialNode specialNode) {
                JSONArray specialPointsArray = new JSONArray();
                for (ConnectionPoint sp : specialNode.specialPoints) {
                    JSONObject spObj = new JSONObject();
                    spObj.put("isInput", sp.isInput());
                    saveConnectionPoints(spObj, "connections", sp, nodeIndices);
                    specialPointsArray.put(spObj);
                }
                nodeObj.put("specialPoints", specialPointsArray);
            }
        }
        root.put("nodes", nodesArray);

        // Save training settings
        if (netTrain != null) {
            root.put("trainSettings", netTrain.save());
        }

        // Write to file
        try (FileWriter writer = new FileWriter(filePath)) {
            root.write(writer);
        }
    }

    public static NetworkData loadFromFile(String filePath) throws IOException {
        String jsonStr = readFile(filePath);
        JSONObject root = new JSONObject(jsonStr);

        List<Node> nodes = loadNodes(root.getJSONArray("nodes"));
        SnnlTrainer netTrain = new SnnlTrainer();

        if (root.has("trainSettings")) {
            netTrain.load(root.getString("trainSettings"));
        }

        return new NetworkData(nodes, netTrain);
    }

    private static void saveConnectionPoints(JSONObject container, String key,
                                             ConnectionPoint point, Map<Node, Integer> nodeIndices) {
        JSONArray connections = new JSONArray();
        for (ConnectionPoint connected : point.connected) {
            connections.put(nodeIndices.get(connected.parent));
        }
        container.put(key, connections);
    }

    private static List<Node> loadNodes(JSONArray nodesArray) {
        List<Node> nodes = new ArrayList<>();
        Map<Integer, Node> nodeMap = new HashMap<>();

        // First pass: create all nodes and restore basic properties
        for (int i = 0; i < nodesArray.length(); i++) {
            JSONObject nodeObj = nodesArray.getJSONObject(i);

            // Create appropriate node type
            Node node;
            String type = SnnlMetadata.normalizeLayerType(nodeObj.getString("type"));
            if (nodeObj.getBoolean("isSpecial")) {
                node = new SpecialNode(type);
            } else {
                node = new Node(type);
            }

            // Restore position
            node.x = nodeObj.getInt("x");
            node.y = nodeObj.getInt("y");

            // Restore parameters
            JSONArray stringParamsArray = nodeObj.getJSONArray("stringParams");
            String[] stringParams = new String[stringParamsArray.length()];
            for (int j = 0; j < stringParamsArray.length(); j++) {
                stringParams[j] = stringParamsArray.getString(j);
            }
            node.setStringParams(stringParams);

            JSONArray floatParamsArray = nodeObj.getJSONArray("floatParams");
            float[] floatParams = new float[floatParamsArray.length()];
            for (int j = 0; j < floatParamsArray.length(); j++) {
                floatParams[j] = (float) floatParamsArray.getDouble(j);
            }
            node.setFloatParams(floatParams);

            nodes.add(node);
            nodeMap.put(i, node);
        }

        // Second pass: restore connections
        for (int i = 0; i < nodesArray.length(); i++) {
            JSONObject nodeObj = nodesArray.getJSONObject(i);
            Node node = nodeMap.get(i);

            // Restore regular connections
            restoreConnections(nodeObj.getJSONArray("prevConnections"), node.prev, nodeMap);
            restoreConnections(nodeObj.getJSONArray("nextConnections"), node.next, nodeMap);

            // Restore special connections if applicable
            if (node instanceof SpecialNode specialNode) {
                JSONArray specialPointsArray = nodeObj.getJSONArray("specialPoints");
                for (int j = 0; j < specialPointsArray.length(); j++) {
                    JSONObject spObj = specialPointsArray.getJSONObject(j);
                    ConnectionPoint sp = specialNode.specialPoints.get(j);
                    restoreConnections(spObj.getJSONArray("connections"), sp, nodeMap);
                }
            }
        }

        return nodes;
    }

    private static void restoreConnections(JSONArray connections, ConnectionPoint point, Map<Integer, Node> nodeMap) {
        for (int i = 0; i < connections.length(); i++) {
            int targetIndex = connections.getInt(i);
            Node targetNode = nodeMap.get(targetIndex);

            // Find the corresponding connection point in the target node
            ConnectionPoint targetPoint = null;
            if (point.isInput()) {
                if (!targetNode.next.connected.contains(point)) {
                    targetNode.next.connected.add(point);
                }
            } else {
                if (point.parent instanceof SpecialNode sourceSpecial) {
                    // For special nodes, find the correct special point that was connected
                    int specialIndex = sourceSpecial.specialPoints.indexOf(point);
                    if (specialIndex >= 0) {
                        targetPoint = targetNode.prev;
                        if (!point.connected.contains(targetPoint)) {
                            point.connected.add(targetPoint);
                        }
                    }
                } else {
                    targetPoint = targetNode.prev;
                    if (!point.connected.contains(targetPoint)) {
                        point.connected.add(targetPoint);
                    }
                }
            }
        }
    }

    private static String readFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    public static class NetworkData {
        public final List<Node> nodes;
        public final SnnlTrainer netTrain;

        public NetworkData(List<Node> nodes, SnnlTrainer netTrain) {
            this.nodes = nodes;
            this.netTrain = netTrain;
        }
    }
}
