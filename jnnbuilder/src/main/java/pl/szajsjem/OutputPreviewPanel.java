package pl.szajsjem;

import com.snnl.Net;
import pl.szajsjem.data.CSVLoaderDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Random;

public class OutputPreviewPanel extends JPanel {
    private final JTable previewTable;
    private final PreviewTableModel tableModel;
    private final CSVLoaderDialog.LoadedData trainingData;
    private final int numPreviewRows = 5;
    private final int[] previewIndices;
    private float[][] currentOutputs;

    public OutputPreviewPanel(CSVLoaderDialog.LoadedData data) {
        super(new BorderLayout());
        this.trainingData = data;
        this.previewIndices = selectRandomIndices(data.inputs.length, numPreviewRows);

        setBorder(BorderFactory.createTitledBorder("Output Preview"));

        // Create table model and table
        tableModel = new PreviewTableModel();
        previewTable = new JTable(tableModel);

        // Center numbers in cells
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < previewTable.getColumnCount(); i++) {
            previewTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        add(new JScrollPane(previewTable), BorderLayout.CENTER);
    }

    private int[] selectRandomIndices(int max, int count) {
        Random rand = new Random();
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = rand.nextInt(max);
        }
        return indices;
    }


    public void updatePreview(Net network) {
        // Get inputs for preview rows
        float[][] previewInputs = new float[numPreviewRows][];
        for (int i = 0; i < numPreviewRows; i++) {
            previewInputs[i] = trainingData.inputs[previewIndices[i]];
        }

        try {
            // Predict outputs
            currentOutputs = new float[numPreviewRows][trainingData.outputs[0].length];
            for (int i = 0; i < numPreviewRows; i++) {
                float[] output = new float[trainingData.outputs[0].length];
                network.predict(previewInputs[i], 1, previewInputs[i].length, output);
                currentOutputs[i] = output;
            }
            tableModel.fireTableDataChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class PreviewTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return numPreviewRows;
        }

        @Override
        public int getColumnCount() {
            return 2 * trainingData.outputColumnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            int outputIdx = column / 2;
            return trainingData.outputColumnNames[outputIdx] +
                    (column % 2 == 0 ? " (Expected)" : " (Predicted)");
        }

        @Override
        public Object getValueAt(int row, int column) {
            int outputIdx = column / 2;
            if (column % 2 == 0) {
                // Expected output
                return String.format("%.4f", trainingData.outputs[previewIndices[row]][outputIdx]);
            } else {
                // Predicted output
                if (currentOutputs != null) {
                    return String.format("%.4f", currentOutputs[row][outputIdx]);
                }
                return "N/A";
            }
        }
    }
}
