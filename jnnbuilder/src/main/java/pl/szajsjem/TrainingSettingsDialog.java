package pl.szajsjem;

import pl.szajsjem.snnl.SnnlTrainer;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class TrainingSettingsDialog extends JDialog {
    private final SnnlTrainer netTrain;
    private final Map<String, JComponent> settingsComponents = new HashMap<>();
    private boolean approved = false;

    public TrainingSettingsDialog(JFrame parent, SnnlTrainer netTrain) {
        super(parent, "Training Settings", true);
        this.netTrain = netTrain;

        setLayout(new BorderLayout(10, 10));

        // Main settings panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add settings components
        addBasicSettings(mainPanel);
        addOptimizerSettings(mainPanel);
        addRegularizerSettings(mainPanel);
        addAdvancedSettings(mainPanel);

        // Add scrolling
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            if (applySettings()) {
                approved = true;
                dispose();
            }
        });

        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Size and position
        setSize(500, 600);
        setLocationRelativeTo(parent);
    }

    private void addBasicSettings(JPanel panel) {
        panel.add(createSectionHeader("Basic Settings"));

        // Epochs
        JSpinner epochsSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
        settingsComponents.put("epochs", epochsSpinner);
        addSettingRow(panel, "Epochs:", epochsSpinner);

        // Batch Size
        JSpinner batchSizeSpinner = new JSpinner(new SpinnerNumberModel(32, 1, 1000, 1));
        settingsComponents.put("batchSize", batchSizeSpinner);
        addSettingRow(panel, "Batch Size:", batchSizeSpinner);

        // Loss Function
        JComboBox<String> lossCombo = new JComboBox<>(SnnlTrainer.getAvailableLosses());
        settingsComponents.put("loss", lossCombo);
        addSettingRow(panel, "Loss Function:", lossCombo);

        panel.add(Box.createVerticalStrut(10));
    }

    private void addOptimizerSettings(JPanel panel) {
        panel.add(createSectionHeader("Optimizer Settings"));

        // Optimizer
        String[] optimizers = SnnlTrainer.getAvailableOptimizers();
        JComboBox<String> optimizerCombo = new JComboBox<>(optimizers);
        settingsComponents.put("optimizer", optimizerCombo);
        addSettingRow(panel, "Optimizer:", optimizerCombo);

        // Learning Rate
        JSpinner learningRateSpinner = new JSpinner(new SpinnerNumberModel(0.001, 0.0001, 1.0, 0.0001));
        settingsComponents.put("learningRate", learningRateSpinner);
        addSettingRow(panel, "Learning Rate:", learningRateSpinner);

        // Momentum
        JSpinner momentumSpinner = new JSpinner(new SpinnerNumberModel(0.9, 0.0, 1.0, 0.1));
        settingsComponents.put("momentum", momentumSpinner);
        addSettingRow(panel, "Momentum:", momentumSpinner);

        // Decay
        JSpinner decaySpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.001));
        settingsComponents.put("decay", decaySpinner);
        addSettingRow(panel, "Decay:", decaySpinner);

        panel.add(Box.createVerticalStrut(10));
    }

    private void addRegularizerSettings(JPanel panel) {
        panel.add(createSectionHeader("Regularization"));

        // Regularizer
        String[] regularizers = SnnlTrainer.getAvailableRegularizers();
        JComboBox<String> regularizerCombo = new JComboBox<>(regularizers);
        settingsComponents.put("regularizer", regularizerCombo);
        addSettingRow(panel, "Regularizer:", regularizerCombo);

        // Regularizer Parameter
        JSpinner regularizerParamSpinner = new JSpinner(new SpinnerNumberModel(0.01, 0.0, 1.0, 0.01));
        settingsComponents.put("regularizerParam", regularizerParamSpinner);
        addSettingRow(panel, "Regularizer Parameter:", regularizerParamSpinner);

        panel.add(Box.createVerticalStrut(10));
    }

    private void addAdvancedSettings(JPanel panel) {
        panel.add(createSectionHeader("Advanced Settings"));

        // Random Batch Order
        JCheckBox randomBatchOrderCheck = new JCheckBox("Random Batch Order", true);
        settingsComponents.put("randomBatchOrder", randomBatchOrderCheck);
        addSettingRow(panel, "", randomBatchOrderCheck);

        // Class Balancing
        JCheckBox classBalancingCheck = new JCheckBox("Class Balancing", true);
        settingsComponents.put("classBalancing", classBalancingCheck);
        addSettingRow(panel, "", classBalancingCheck);

        // Keep Best Model
        JCheckBox keepBestCheck = new JCheckBox("Keep Best Model", true);
        settingsComponents.put("keepBest", keepBestCheck);
        addSettingRow(panel, "", keepBestCheck);

        // Validation Batch Size
        JSpinner validationBatchSpinner = new JSpinner(new SpinnerNumberModel(32, 1, 1000, 1));
        settingsComponents.put("validationBatch", validationBatchSpinner);
        addSettingRow(panel, "Validation Batch Size:", validationBatchSpinner);

        panel.add(Box.createVerticalStrut(10));
    }

    private JLabel createSectionHeader(String text) {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        return header;
    }

    private void addSettingRow(JPanel panel, String label, JComponent component) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (!label.isEmpty()) {
            row.add(new JLabel(label));
        }
        row.add(component);
        panel.add(row);
    }

    private boolean applySettings() {
        try {
            // Basic settings
            netTrain.setEpochs((Integer) ((JSpinner) settingsComponents.get("epochs")).getValue());
            netTrain.setBatchSize((Integer) ((JSpinner) settingsComponents.get("batchSize")).getValue());
            netTrain.setLoss((String) ((JComboBox<?>) settingsComponents.get("loss")).getSelectedItem());

            // Optimizer settings
            String optimizer = (String) ((JComboBox<?>) settingsComponents.get("optimizer")).getSelectedItem();
            float learningRate = ((SpinnerNumberModel) ((JSpinner) settingsComponents.get("learningRate")).getModel()).getNumber().floatValue();
            float momentum = ((SpinnerNumberModel) ((JSpinner) settingsComponents.get("momentum")).getModel()).getNumber().floatValue();
            float decay = ((SpinnerNumberModel) ((JSpinner) settingsComponents.get("decay")).getModel()).getNumber().floatValue();
            netTrain.setOptimizer(optimizer, learningRate, decay, momentum);

            // Regularizer settings
            String regularizer = (String) ((JComboBox<?>) settingsComponents.get("regularizer")).getSelectedItem();
            float regularizerParam = ((SpinnerNumberModel) ((JSpinner) settingsComponents.get("regularizerParam")).getModel()).getNumber().floatValue();
            netTrain.setRegularizer(regularizer, regularizerParam);

            // Advanced settings
            //netTrain.setValidationBatchSize((Integer) ((JSpinner) settingsComponents.get("validationBatch")).getValue());
            //todo

            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error applying settings: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public boolean showDialog() {
        setVisible(true);
        return approved;
    }
}
