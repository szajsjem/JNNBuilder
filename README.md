# Neural Network Editor

A Java-based graphical editor for designing, training, and managing neural networks. This tool provides an intuitive interface for creating and experimenting with various neural network architectures.

## Features

- Visual network design with drag-and-drop interface
- Support for various layer types and architectures
- Real-time network validation
- Data management and preprocessing tools
- Training configuration and monitoring
- Import/Export capabilities for network models
- Automatic network layout
- Undo/Redo functionality
- Copy/Paste support for network components

## Requirements

- Java 21 (the Maven build is configured for Java 21)
- Maven (to build the Java editor)
- Minimum 4GB RAM recommended
- Graphics card supporting Java2D

## Dependencies

- JSON Library (org.json)
- org.jfree jfreechart library
- Swing/AWT for GUI components

Training in the Java editor additionally requires the SNNL Java native binding, see [Native training binding](#native-training-binding).
The web editor (`index.html`) uses BeeDNN compiled to WebAssembly (`Release-wasm/beednn.wasm`) and needs no native build.

## Installation

1. Clone the repository:
```bash
git clone https://github.com/szajsjem/JNNBuilder.git
```

2. The Java project lives in the `jnnbuilder/` subdirectory (Maven manifest: `jnnbuilder/pom.xml`, artifact `jnnbuilder`). Build from that directory:
```bash
cd jnnbuilder
mvn clean package
```

3. Run the application. The entry point is `pl.szajsjem.NetworkEditorGUI`. The generated jar (`target/jnnbuilder-1.0-SNAPSHOT.jar`) has no Main-Class manifest, so run it with the main class explicitly, e.g. via Maven:
```bash
mvn exec:java -Dexec.mainClass=pl.szajsjem.NetworkEditorGUI
```
   or with a plain `java` command after copying the dependencies:
```bash
mvn dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target/jnnbuilder-1.0-SNAPSHOT.jar:target/lib/*" pl.szajsjem.NetworkEditorGUI
```

### Web editor

Serve the `jnnbuilder/` directory with any static file server and open `index.html` in a browser, e.g.:
```bash
cd jnnbuilder
python3 -m http.server 8000
```

Its test suite (Node.js, `node:test`):
```bash
cd jnnbuilder
node --test tests/*.test.mjs
```

### Native training binding

The Java editor loads a native SNNL binding at runtime when training starts. It is located via the `SNNL_JAVA_BINDING` environment variable (path to the library), the `SNNL_HOME` environment variable or a sibling `SNNL` checkout (expected as `libsnnl_java_binding.so` on Linux, `snnl_java_binding.dll` on Windows, `libsnnl_java_binding.dylib` on macOS), or the regular `java.library.path` lookup of the library name `snnl_java_binding`. The GUI itself starts without the binding; training requires it. Note that the prebuilt native binaries checked into this repository (`Release-Java/BeeDNNJava.dll`, `java_binding/`) are Windows x64 DLLs left over from an older `com.beednn` integration and are not the SNNL binding; no Linux build of the SNNL binding is included in this repository, and its availability on Linux has not been verified.

## Usage

### Basic Network Creation

1. Launch the application
2. Use the layer palette on the left to add layers by clicking
3. Connect layers by dragging from output (right) to input (left) dots
4. Configure layer properties in the properties panel

### Working with Data

1. Go to Data → Load Data to import your CSV dataset
2. Use the Data Manager to preprocess and normalize your data
3. Configure input/output mappings for your network

### Training Configuration

1. Access Network → Training Settings to configure:
   - Optimizer settings
   - Learning rate
   - Batch size
   - Regularization
   - Loss function

### Saving and Loading

- Save your network design: File → Save (Ctrl+S)
- Load existing network: File → Open (Ctrl+O)
- Export trained model: File → Export → BeeDNN Model
- Export executable jar: File → Export → Trained Network JAR

## File Formats

- `.bnn` - Network design files
- `.beednn` - Exported trained models
- `.csv` - Data files for training/testing

## Keyboard Shortcuts

- Ctrl+N: New network
- Ctrl+S: Save
- Ctrl+O: Open
- Ctrl+Z: Undo
- Ctrl+Y: Redo
- Ctrl+C: Copy
- Ctrl+V: Paste
- Ctrl+X: Cut
- Delete: Remove selected
- Ctrl+L: Auto-layout
- Ctrl+0: Fit to window

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the Apache-2.0 license.

## Author

Szajsjem 2024-2025

## Acknowledgments

- BeeDNN library team for the neural network implementation
- Contributors to the project
- Java Swing/AWT for the GUI framework

## Support

For support, please open an issue on the project's GitHub page or contact the development team at szajsjem@gmail.pl

## Version History

- 1.0.0 (2025) future release
  - Initial release
  - Basic network editor functionality
  - Data management tools
  - Training interface