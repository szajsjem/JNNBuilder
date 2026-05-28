package pl.szajsjem;

import com.snnl.Net;
import pl.szajsjem.data.CSVLoaderDialog;
import pl.szajsjem.snnl.SnnlTrainer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TrainingDialog extends JDialog {
    private final NetworkStructureSerializer networkSerializer;
    private final CSVLoaderDialog.LoadedData trainingData;
    private final SnnlTrainer netTrain;
    private final JButton startButton;
    private final JButton trainMoreButton;
    private final JButton stopButton;
    private final List<Float> trainLosses = new ArrayList<>();
    private final List<Float> validationLosses = new ArrayList<>();
    private final LossGraph lossGraph;
    private final OutputPreviewPanel outputPreview;
    private final TestInputPanel testInput;
    private Net network;
    private volatile boolean isTraining = false;
    private final boolean initialTrainingDone = false;
    private Timer updateTimer;

    public TrainingDialog(JFrame parent, NetworkStructureSerializer serializer,
                          CSVLoaderDialog.LoadedData data, SnnlTrainer netTrain) {
        super(parent, "Network Training", true);
        this.networkSerializer = serializer;
        this.trainingData = data;
        this.netTrain = netTrain;

        setLayout(new BorderLayout(10, 10));
        setSize(800, 600);
        setLocationRelativeTo(parent);

        // Create components
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startButton = new JButton("Train network");
        trainMoreButton = new JButton("Train More");
        stopButton = new JButton("Stop Training");
        stopButton.setEnabled(false);
        trainMoreButton.setEnabled(false);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        topPanel.add(buttonPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // Create main content panel with loss graph and output preview
        JPanel mainContent = new JPanel(new GridLayout(2, 1, 10, 10));

        // Loss graph
        lossGraph = new LossGraph();
        mainContent.add(new JScrollPane(lossGraph));

        // Output preview
        outputPreview = new OutputPreviewPanel(trainingData);
        mainContent.add(new JScrollPane(outputPreview));

        add(mainContent, BorderLayout.CENTER);

        // Test input panel
        testInput = new TestInputPanel(trainingData, this);
        add(testInput, BorderLayout.SOUTH);

        // Button actions
        startButton.addActionListener(e -> startTraining());
        stopButton.addActionListener(e -> stopTraining());
    }

    private void startTraining() {
        try {
            // Build network if not already built
            if (network == null) {
                network = networkSerializer.buildNetwork();
                network.setClassificationMode(netTrain.isClassificationMode());
                network.init(trainingData.inputs[0].length);
            }

            // Setup training
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            isTraining = true;

            // Clear previous training data
            trainLosses.clear();
            validationLosses.clear();
            lossGraph.reset();

            // Start update timer
            updateTimer = new Timer();
            updateTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        updateOutputPreview();
                    });
                }
            }, 0, 1000); // Update every second

            // Start training in background thread
            var th = new Thread(this::trainNetwork);
            th.setDaemon(true);
            th.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error starting training: " + e.getMessage(),
                    "Training Error",
                    JOptionPane.ERROR_MESSAGE);
            stopTraining();
        }
    }

    private void stopTraining() {
        isTraining = false;
        if (updateTimer != null) {
            updateTimer.cancel();
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void trainNetwork() {
        try {
            // Convert 2D arrays to 1D arrays for JNI
            float[] flatInputs = flattenArray(trainingData.inputs);
            float[] flatOutputs = flattenArray(trainingData.outputs);

            // Set training data
            netTrain.setTrainData(
                    flatInputs, trainingData.inputs.length, trainingData.inputs[0].length,
                    flatOutputs, trainingData.outputs.length, trainingData.outputs[0].length
            );

            netTrain.fit(network);
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Training error: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
        updateGraph();
        stopTraining();
        updateOutputPreview();
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Finished training",
                    "Message",
                    JOptionPane.PLAIN_MESSAGE);
        });
    }

    private float[] flattenArray(float[][] array2D) {
        int rows = array2D.length;
        int cols = array2D[0].length;
        float[] flat = new float[rows * cols];

        for (int i = 0; i < rows; i++) {
            System.arraycopy(array2D[i], 0, flat, i * cols, cols);
        }

        return flat;
    }

    private void updateGraph() {
        // Get the latest loss values from the active SNNL trainer
        float[] trainLoss = netTrain.getTrainLoss();
        float[] validationLoss = netTrain.getValidationLoss();

        for (int i = 0; i < trainLoss.length; i++)
            trainLosses.add(trainLoss[i]);
        for (int i = 0; i < validationLoss.length; i++)
            validationLosses.add(validationLoss[i]);

        lossGraph.updateData(trainLosses, validationLosses);
    }

    private void updateOutputPreview() {
        if (network != null) {
            outputPreview.updatePreview(network);
        }
    }

    public Net getTrainedNetwork() {
        return network;
    }
}
