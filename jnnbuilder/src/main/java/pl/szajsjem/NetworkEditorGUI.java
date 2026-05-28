package pl.szajsjem;

import com.snnl.Net;
import pl.szajsjem.data.CSVLoaderDialog;
import pl.szajsjem.data.DataManager;
import pl.szajsjem.elements.Node;
import pl.szajsjem.snnl.SnnlMetadata;
import pl.szajsjem.snnl.SnnlTrainer;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NetworkEditorGUI extends JFrame {
    private final JPanel canvas;
    private final NodeManager nodeManager;
    private final StatusBar statusBar;
    private final ToolPanel toolPanel;
    private Point dragStart;
    private float zoomLevel = 1.0f;
    private final Point2D.Float panOffset = new Point2D.Float(0, 0);
    private AffineTransform canvasTransform = new AffineTransform();
    private DataManager dataManager;
    private CSVLoaderDialog.LoadedData currentData;
    private JFrame dataFrame;
    JMenu dataMenu = new JMenu("Data");
    private SnnlTrainer netTrain;
    private Net trainedNetwork = null;
    private File currentFile = null;
    private boolean hasUnsavedChanges = false;

    public NetworkEditorGUI() {
        setTitle("Neural Network Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);

        // Main layout
        setLayout(new BorderLayout());

        netTrain = new SnnlTrainer();

        // Create components
        canvas = new CanvasPanel();
        nodeManager = new NodeManager(canvas, this);
        toolPanel = new ToolPanel(this);
        statusBar = new StatusBar();

        // Setup main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(toolPanel);
        splitPane.setRightComponent(new JScrollPane(canvas));
        splitPane.setDividerLocation(250);

        // Add components
        add(createMenuBar(), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setupCanvasListeners();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new NetworkEditorGUI().setVisible(true);
        });
    }

    private void setupCanvasListeners() {
        // Add key listener
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                nodeManager.handleKeyPressed(e);
                canvas.repaint();
            }
        });
        InputMap inputMap = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = canvas.getActionMap();

        // Undo
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.undo();
                canvas.repaint();
            }
        });

        // Redo
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.redo();
                canvas.repaint();
            }
        });

        // Cut
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        actionMap.put("cut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.cut();
            }
        });

        // Copy
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        actionMap.put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.copy();
            }
        });

        // Paste
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        actionMap.put("paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nodeManager.paste();
            }
        });

        // Mouse wheel for zooming
        canvas.addMouseWheelListener(e -> {
            Point2D p = transformPoint(e.getPoint());
            float oldZoom = zoomLevel;

            // Calculate zoom
            if (e.getWheelRotation() < 0) {
                zoomLevel *= 1.1f;
            } else {
                zoomLevel /= 1.1f;
            }
            zoomLevel = Math.max(0.1f, Math.min(5.0f, zoomLevel));

            // Adjust pan to keep mouse position fixed
            double scale = zoomLevel / oldZoom;
            panOffset.x = e.getX() - (float) ((e.getX() - panOffset.x) * scale);
            panOffset.y = e.getY() - (float) ((e.getY() - panOffset.y) * scale);

            canvas.repaint();
        });

        // Mouse listeners
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Request focus for key events
                canvas.requestFocusInWindow();

                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragStart = e.getPoint();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // Right click
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    showContextMenu(e.getPoint());
                } else {
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMousePressed(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY()),
                            e.getModifiersEx()
                    );
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    dragStart = null;
                } else {
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMouseReleased(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                    );
                }
                canvas.repaint();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    // Panning
                    panOffset.x += e.getX() - dragStart.x;
                    panOffset.y += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                } else {
                    // Node dragging
                    Point2D transformedPoint = transformPoint(e.getPoint());
                    nodeManager.handleMouseDragged(
                            new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                    );
                }
                canvas.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Point2D transformedPoint = transformPoint(e.getPoint());
                nodeManager.handleMouseMoved(
                        new Point((int) transformedPoint.getX(), (int) transformedPoint.getY())
                );

                // Update status bar coordinates
                statusBar.setCoordinates(
                        (int) transformedPoint.getX(),
                        (int) transformedPoint.getY()
                );

                canvas.repaint();
            }
        });
    }

    Point2D transformPoint(Point screenPoint) {
        try {
            Point2D.Float point = new Point2D.Float(screenPoint.x, screenPoint.y);
            AffineTransform inverse = canvasTransform.createInverse();
            Point2D.Float transformed = new Point2D.Float();
            inverse.transform(point, transformed);
            return transformed;
        } catch (NoninvertibleTransformException e) {
            return screenPoint;
        }
    }

    private void zoomIn() {
        Point center = new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        float oldZoom = zoomLevel;

        // Increase zoom by 20%
        zoomLevel *= 1.2f;
        zoomLevel = Math.min(5.0f, zoomLevel);

        // Adjust pan to keep center point fixed
        double scale = zoomLevel / oldZoom;
        panOffset.x = center.x - (float) ((center.x - panOffset.x) * scale);
        panOffset.y = center.y - (float) ((center.y - panOffset.y) * scale);

        canvas.repaint();
    }

    private void zoomOut() {
        Point center = new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        float oldZoom = zoomLevel;

        // Decrease zoom by 20%
        zoomLevel /= 1.2f;
        zoomLevel = Math.max(0.1f, zoomLevel);

        // Adjust pan to keep center point fixed
        double scale = zoomLevel / oldZoom;
        panOffset.x = center.x - (float) ((center.x - panOffset.x) * scale);
        panOffset.y = center.y - (float) ((center.y - panOffset.y) * scale);

        canvas.repaint();
    }

    private void fitToWindow() {
        if (nodeManager.getAllNodes().isEmpty()) {
            // Reset to default view if no nodes
            zoomLevel = 1.0f;
            panOffset.x = 0;
            panOffset.y = 0;
            canvas.repaint();
            return;
        }

        // Find bounds of all nodes
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Node node : nodeManager.getAllNodes()) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + node.width);
            maxY = Math.max(maxY, node.y + node.height);
        }

        // Add padding
        int padding = 50;
        minX -= padding;
        minY -= padding;
        maxX += padding;
        maxY += padding;

        // Calculate required zoom level
        float scaleX = (float) canvas.getWidth() / (maxX - minX);
        float scaleY = (float) canvas.getHeight() / (maxY - minY);
        zoomLevel = Math.min(scaleX, scaleY);
        zoomLevel = Math.max(0.1f, Math.min(5.0f, zoomLevel));

        // Center the content
        panOffset.x = (canvas.getWidth() - (maxX + minX) * zoomLevel) / 2;
        panOffset.y = (canvas.getHeight() - (maxY + minY) * zoomLevel) / 2;

        canvas.repaint();
    }

    private void loadData(JMenuItem manageDataItem) {
        currentData = CSVLoaderDialog.showDialog(this);
        if (currentData != null) {
            // Create data manager if it doesn't exist
            if (dataManager == null) {
                dataManager = new DataManager();
            }

            // Update data in manager
            dataManager.setData(currentData, currentData.inputColumnNames, currentData.outputColumnNames);

            manageDataItem.setEnabled(true); // Enable "Manage Data" item

            // Update status
            statusBar.setStatus("Data loaded: " + currentData.inputs.length + " samples");
        }
    }

    private void showDataManager() {
        if (currentData == null || dataManager == null) {
            JOptionPane.showMessageDialog(this,
                    "No data loaded. Please load data first.",
                    "No Data",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create frame if it doesn't exist or is disposed
        if (dataFrame == null || !dataFrame.isDisplayable()) {
            dataFrame = new JFrame("Data Manager");
            dataFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            dataFrame.add(dataManager);
            dataFrame.setSize(800, 600);

            // Position relative to main window
            Point loc = getLocation();
            loc.translate(50, 50);
            dataFrame.setLocation(loc);
        }

        dataFrame.setVisible(true);
        dataFrame.toFront();
    }

    // Add getter for current data
    public CSVLoaderDialog.LoadedData getCurrentData() {
        return currentData;
    }

    private void createNewNetwork() {
        // Confirm with user if there are existing nodes
        if (!nodeManager.getAllNodes().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Creating a new network will clear the current one. Continue?",
                    "New Network",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Clear all nodes and reset state
        nodeManager.getAllNodes().clear();
        nodeManager.getSelectedNodes().clear();

        // Reset view
        zoomLevel = 1.0f;
        panOffset.x = 0;
        panOffset.y = 0;

        // Clear training settings
        netTrain = new SnnlTrainer();

        // Clear any loaded data
        currentData = null;
        if (dataManager != null) {
            dataManager.setData(null, null, null);
        }

        // Disable "Manage Data" menu item
        for (int i = 0; i < dataMenu.getItemCount(); i++) {
            JMenuItem item = dataMenu.getItem(i);
            if (item != null && item.getText().equals("Manage Data")) {
                item.setEnabled(false);
                break;
            }
        }

        // Update status
        statusBar.setStatus("New network created");

        // Repaint canvas
        canvas.repaint();
        canvas.requestFocusInWindow();
    }

    private static JFileChooser getjFileChooser() {
        JFileChooser fileChooser = new JFileChooser() {
            @Override
            protected void setup(FileSystemView view) {
                putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
                super.setup(view);
            }
        };

        // Use a simple file view that doesn't use system icons
        fileChooser.setFileView(new FileView() {
            @Override
            public String getName(File f) {
                return f.getName();
            }

            @Override
            public String getDescription(File f) {
                return f.getName();
            }

            @Override
            public String getTypeDescription(File f) {
                return f.isDirectory() ? "Folder" : "File";
            }

            @Override
            public Icon getIcon(File f) {
                // Return a simple default icon based on whether it's a directory or file
                return UIManager.getIcon(f.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon");
            }
        });
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".bnn");
            }

            public String getDescription() {
                return "Neural Network Files (*.bnn)";
            }
        });
        return fileChooser;
    }

    private void saveNetwork(boolean saveAs) {
        // If never saved or Save As requested, prompt for file
        if (currentFile == null || saveAs) {
            JFileChooser fileChooser = getjFileChooser();

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                // Add extension if not present
                if (!file.getName().toLowerCase().endsWith(".bnn")) {
                    file = new File(file.getPath() + ".bnn");
                }
                currentFile = file;
            } else {
                return; // User cancelled
            }
        }

        try {
            NetworkSerializer.saveToFile(currentFile.getPath(), nodeManager.getAllNodes(), netTrain);
            statusBar.setStatus("Network saved to " + currentFile.getName());

            setHasUnsavedChanges(false);  // Clear unsaved changes flag
            updateWindowTitle();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error saving network: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exitApplication() {
        if (hasUnsavedChanges) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "There are unsaved changes. Would you like to save before exiting?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }

            if (choice == JOptionPane.YES_OPTION) {
                saveNetwork(false);
                // If save was cancelled, don't exit
                if (hasUnsavedChanges) {
                    return;
                }
            }
        }

        dispose();
        System.exit(0);
    }

    public void setHasUnsavedChanges(boolean hasChanges) {
        this.hasUnsavedChanges = hasChanges;
        // Update window title to show unsaved status
        updateWindowTitle();
    }

    private void updateWindowTitle() {
        String title = "Neural Network Editor";
        if (currentFile != null) {
            title += " - " + currentFile.getName();
        }
        if (hasUnsavedChanges) {
            title += "*";
        }
        setTitle(title);
    }

    private void openNetwork() {
        // Check for unsaved changes first
        if (hasUnsavedChanges) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "There are unsaved changes. Would you like to save before opening another network?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }

            if (choice == JOptionPane.YES_OPTION) {
                saveNetwork(false);
                if (hasUnsavedChanges) {  // Save was cancelled
                    return;
                }
            }
        }

        // Show file chooser
        JFileChooser fileChooser = getjFileChooser();

        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            // Load and validate the network
            NetworkLoader.ValidationResult validation =
                    NetworkLoader.loadAndValidate(fileChooser.getSelectedFile().getPath());

            // Show warnings if any
            if (!validation.warnings.isEmpty()) {
                StringBuilder warningMsg = new StringBuilder("Warnings:\n\n");
                for (String warning : validation.warnings) {
                    warningMsg.append("• ").append(warning).append("\n");
                }

                JOptionPane.showMessageDialog(this,
                        warningMsg.toString(),
                        "Network Load Warnings",
                        JOptionPane.WARNING_MESSAGE);
            }

            // If there are errors, show them and abort
            if (validation.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("Cannot load network due to the following errors:\n\n");
                for (String error : validation.errors) {
                    errorMsg.append("• ").append(error).append("\n");
                }

                JOptionPane.showMessageDialog(this,
                        errorMsg.toString(),
                        "Network Load Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Clear existing network
            nodeManager.getAllNodes().clear();
            nodeManager.getSelectedNodes().clear();

            // Load the new network
            nodeManager.getAllNodes().addAll(validation.networkData.nodes);
            netTrain = validation.networkData.netTrain;

            // Update file reference and UI
            currentFile = fileChooser.getSelectedFile();
            setHasUnsavedChanges(false);
            updateWindowTitle();

            // Reset view to show all nodes
            fitToWindow();

            // Update status
            statusBar.setStatus("Loaded network from " + currentFile.getName());
            canvas.repaint();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading network: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSnnlModel() {
        // Check if there are any nodes
        if (trainedNetwork == null) {
            JOptionPane.showMessageDialog(this,
                    "No network to export, please train it first",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Show file chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".snnl");
            }

            public String getDescription() {
                return "SNNL Model Files (*.snnl)";
            }
        });

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = fileChooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".snnl")) {
            file = new File(file.getPath() + ".snnl");
        }

        try {
            if (!trainedNetwork.save(file.getPath())) {
                throw new IOException("SNNL save returned failure");
            }
            statusBar.setStatus("Model exported to " + file.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error exporting model: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportTrainedJar() {
        // Placeholder for future implementation
        JOptionPane.showMessageDialog(this,
                "Export as executable JAR will be implemented in a future update.",
                "Not Implemented",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void startTraining() {
        // Check if data is loaded
        if (currentData == null) {
            JOptionPane.showMessageDialog(this,
                    "Please load training data first.",
                    "No Training Data",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate network
        List<String> errors = nodeManager.connectionManager.validateNetwork();
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Cannot start training due to the following issues:\n\n");
            for (String error : errors) {
                message.append("• ").append(error).append("\n");
            }
            JOptionPane.showMessageDialog(this,
                    message.toString(),
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            //return;
        }

        try {
            // Create network serializer
            NetworkStructureSerializer serializer = new NetworkStructureSerializer(nodeManager.getAllNodes());

            // Create and show training dialog
            TrainingDialog dialog = new TrainingDialog(this, serializer, currentData, netTrain);
            dialog.setVisible(true);

            // Store trained network if training completed successfully
            trainedNetwork = dialog.getTrainedNetwork();


        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error starting training: " + e.getMessage(),
                    "Training Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem newNetworkItem = new JMenuItem("New Network");
        newNetworkItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newNetworkItem.addActionListener(e -> createNewNetwork());
        fileMenu.add(newNetworkItem);

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openNetwork());
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> saveNetwork(false));
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveNetwork(true));
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();

        JMenu exportMenu = new JMenu("Export");

        JMenuItem exportModelItem = new JMenuItem("SNNL Model...");
        exportModelItem.addActionListener(e -> exportSnnlModel());
        exportMenu.add(exportModelItem);

        JMenuItem exportJarItem = new JMenuItem("Trained Network JAR...");
        exportJarItem.addActionListener(e -> exportTrainedJar());
        exportMenu.add(exportJarItem);

        fileMenu.add(exportMenu);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");

        // Undo/Redo
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> {
            nodeManager.undo();
            canvas.repaint();
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> {
            nodeManager.redo();
            canvas.repaint();
        });

        // Cut/Copy/Paste
        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cutItem.addActionListener(e -> nodeManager.cut());

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> nodeManager.copy());

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        pasteItem.addActionListener(e -> nodeManager.paste());

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteItem.addActionListener(e -> {
            for (Node node : new ArrayList<>(nodeManager.getSelectedNodes())) {
                nodeManager.deleteNode(node);
            }
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(deleteItem);

        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> zoomIn());

        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> zoomOut());

        JMenuItem fitToWindowItem = new JMenuItem("Fit to Window");
        fitToWindowItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        fitToWindowItem.addActionListener(e -> fitToWindow());

        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.add(fitToWindowItem);
        viewMenu.addSeparator();
        viewMenu.add(new JCheckBoxMenuItem("Show Grid"));


        // Data menu
        JMenuItem manageDataItem = new JMenuItem("Manage Data");
        JMenuItem loadDataItem = new JMenuItem("Load Data...");
        loadDataItem.addActionListener(e -> loadData(manageDataItem));

        manageDataItem.addActionListener(e -> showDataManager());
        manageDataItem.setEnabled(false); // Initially disabled until data is loaded

        dataMenu.add(loadDataItem);
        dataMenu.add(manageDataItem);

        // Network menu
        JMenu networkMenu = new JMenu("Network");

        JMenuItem validateItem = new JMenuItem("Validate");
        validateItem.addActionListener(e -> {
            NetworkValidator.ValidationResult validation = NetworkValidator.validate(nodeManager.getAllNodes(), netTrain);

            List<String> errors = nodeManager.connectionManager.validateNetwork();
            if (!validation.warnings.isEmpty()) {
                StringBuilder warningMsg = new StringBuilder("Warnings:\n\n");
                for (String warning : validation.warnings) {
                    warningMsg.append("• ").append(warning).append("\n");
                }
                JOptionPane.showMessageDialog(this,
                        warningMsg.toString(),
                        "Network Validation Warnings",
                        JOptionPane.WARNING_MESSAGE);
            }

            if (validation.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("Validation found the following errors:\n\n");
                for (String error : validation.errors) {
                    errorMsg.append("• ").append(error).append("\n");
                }
                JOptionPane.showMessageDialog(this,
                        errorMsg.toString(),
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
            } else if (validation.warnings.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Network validation successful: No issues found.",
                        "Validation Result",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JMenuItem autoLayoutItem = new JMenuItem("Auto-Layout");
        autoLayoutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        autoLayoutItem.addActionListener(e -> {
            // Call auto-layout
            NetworkLayout.autoLayout(nodeManager.getAllNodes());

            // Reset view to fit all nodes
            fitToWindow();

            // Repaint canvas
            canvas.repaint();
        });

        JMenuItem trainingSettingsItem = new JMenuItem("Training Settings...");
        trainingSettingsItem.addActionListener(e -> {
            if (netTrain == null) netTrain = new SnnlTrainer();
            TrainingSettingsDialog dialog = new TrainingSettingsDialog(this, netTrain);
            dialog.showDialog();
        });

        networkMenu.add(validateItem);
        networkMenu.add(autoLayoutItem);
        networkMenu.add(trainingSettingsItem);
        networkMenu.addSeparator();
        JMenuItem startTrainingItem = new JMenuItem("Start Training");
        startTrainingItem.addActionListener(e -> startTraining());
        networkMenu.add(startTrainingItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(dataMenu);
        menuBar.add(networkMenu);

        return menuBar;
    }

    private void showContextMenu(Point p) {
        JPopupMenu popup = new JPopupMenu();

        Node node = nodeManager.getNodeAt(p);
        if (node != null) {
            popup.add(new JMenuItem("Edit Properties..."));
            popup.add(new JMenuItem("Delete"));
            popup.addSeparator();
            popup.add(new JMenuItem("Bring to Front"));
            popup.add(new JMenuItem("Send to Back"));
        } else {
            popup.add(new JMenuItem("Paste"));
            popup.add(new JMenuItem("Select All"));
        }

        popup.show(canvas, p.x, p.y);
    }

    private class CanvasPanel extends JPanel {
        private static final int GRID_SIZE = 20;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Store original transform
            AffineTransform originalTransform = g2d.getTransform();

            // Apply zoom and pan
            canvasTransform = new AffineTransform();
            canvasTransform.translate(panOffset.x, panOffset.y);
            canvasTransform.scale(zoomLevel, zoomLevel);
            g2d.transform(canvasTransform);

            // Enable antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw grid
            drawGrid(g2d);

            // Draw connections and nodes
            nodeManager.drawConnections(g2d);
            nodeManager.drawNodes(g2d);

            // Restore original transform
            g2d.setTransform(originalTransform);
        }

        private void drawGrid(Graphics2D g2d) {
            g2d.setColor(new Color(240, 240, 240));

            int width = getWidth();
            int height = getHeight();

            // Adjust for zoom and pan
            int startX = (int) (-panOffset.x / zoomLevel);
            int startY = (int) (-panOffset.y / zoomLevel);
            int endX = (int) ((width - panOffset.x) / zoomLevel);
            int endY = (int) ((height - panOffset.y) / zoomLevel);

            // Draw vertical lines
            for (int x = startX - (startX % GRID_SIZE); x <= endX; x += GRID_SIZE) {
                g2d.drawLine(x, startY, x, endY);
            }

            // Draw horizontal lines
            for (int y = startY - (startY % GRID_SIZE); y <= endY; y += GRID_SIZE) {
                g2d.drawLine(startX, y, endX, y);
            }
        }
    }

    private class ToolPanel extends JPanel {
        public ToolPanel(NetworkEditorGUI parent) {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            // Layer types panel
            JPanel layerPanel = new JPanel();
            layerPanel.setLayout(new BoxLayout(layerPanel, BoxLayout.Y_AXIS));
            layerPanel.setBorder(BorderFactory.createTitledBorder("Available Layers"));

            // Add layer buttons
            for (String layerType : SnnlMetadata.getAvailableLayers()) {
                JButton btn = new JButton(layerType);
                btn.setAlignmentX(Component.LEFT_ALIGNMENT);
                btn.addActionListener(e -> {
                    nodeManager.createNode(layerType);
                    canvas.repaint();  // Add this as a backup
                });
                layerPanel.add(btn);
                layerPanel.add(Box.createVerticalStrut(5));
            }

            JScrollPane scrollPane = new JScrollPane(layerPanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Increase from default 1
            scrollPane.getVerticalScrollBar().setBlockIncrement(64); // Increase from default 10

            PropertiesPanel propsPanel = new PropertiesPanel(nodeManager);
            add(scrollPane, BorderLayout.CENTER);
            add(propsPanel, BorderLayout.SOUTH);
        }
    }

    private class StatusBar extends JPanel {
        private final JLabel statusLabel;
        private final JLabel coordsLabel;

        public StatusBar() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEtchedBorder());

            statusLabel = new JLabel("Ready");
            coordsLabel = new JLabel("0, 0");

            add(statusLabel, BorderLayout.WEST);
            add(coordsLabel, BorderLayout.EAST);
        }

        public void setStatus(String status) {
            statusLabel.setText(status);
        }

        public void setCoordinates(int x, int y) {
            coordsLabel.setText(String.format("%d, %d", x, y));
        }
    }
}
