package pl.szajsjem;

import com.snnl.Net;
import pl.szajsjem.data.CSVLoaderDialog;
import pl.szajsjem.data.CategoricalMapping;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TestInputPanel extends JPanel {
    private final List<JTextField> inputFields = new ArrayList<>();
    private final JPanel outputPanel;
    private final CSVLoaderDialog.LoadedData trainingData;
    private final TrainingDialog trainingDialog;

    public TestInputPanel(CSVLoaderDialog.LoadedData data, TrainingDialog trainingDialog) {
        super(new BorderLayout(10, 10));
        this.trainingData = data;
        this.trainingDialog = trainingDialog;
        setBorder(BorderFactory.createTitledBorder("Test Input"));

        // Create input fields panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 5, 2, 5);

        // Add input fields
        for (int i = 0; i < data.inputColumnNames.length; i++) {
            JLabel label = new JLabel(data.inputColumnNames[i] + ":");
            JTextField field = new JTextField(10);
            inputFields.add(field);

            gbc.gridx = 0;
            inputPanel.add(label, gbc);
            gbc.gridx = 1;
            inputPanel.add(field, gbc);
            gbc.gridy++;
        }

        // Test button
        JButton testButton = new JButton("Test");
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        inputPanel.add(testButton, gbc);

        // Output panel
        outputPanel = new JPanel();
        outputPanel.setLayout(new BoxLayout(outputPanel, BoxLayout.Y_AXIS));
        outputPanel.setBorder(BorderFactory.createTitledBorder("Output"));

        // Add to main panel
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(inputPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, new JScrollPane(outputPanel));
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);

        // Button action
        testButton.addActionListener(e -> runTest());
    }

    private void runTest() {
        Net network = trainingDialog.getTrainedNetwork();

        try {
            // Collect input values
            float[] inputs = new float[inputFields.size()];
            for (int i = 0; i < inputFields.size(); i++) {
                String text = inputFields.get(i).getText().trim();

                // Check for categorical values
                if (trainingData.categoricalMappings.containsKey(i)) {
                    CategoricalMapping mapping = trainingData.categoricalMappings.get(i);
                    inputs[i] = mapping.getOrCreateIndex(text);
                } else {
                    inputs[i] = Float.parseFloat(text);
                }
            }

            // Run network forward pass
            float[] output = new float[trainingData.outputs[0].length];
            network.predict(inputs, 1, inputs.length, output);

            // Update output display
            outputPanel.removeAll();
            for (int i = 0; i < output.length; i++) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JLabel label = new JLabel(trainingData.outputColumnNames[i] + ": ");
                JLabel value = new JLabel();

                // Format output value
                if (trainingData.categoricalMappings.containsKey(trainingData.inputColumnNames.length + i)) {
                    CategoricalMapping mapping = trainingData.categoricalMappings.get(trainingData.inputColumnNames.length + i);
                    int index = Math.round(output[i]);
                    value.setText(String.format("%s (%.2f)", mapping.getValue(index), output[i]));
                } else {
                    value.setText(String.format("%.4f", output[i]));
                }

                row.add(label);
                row.add(value);
                outputPanel.add(row);
            }

            outputPanel.revalidate();
            outputPanel.repaint();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid input value. Please enter valid numbers.",
                    "Input Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error running test: " + e.getMessage(),
                    "Test Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
