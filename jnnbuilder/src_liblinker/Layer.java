package com.beednn;

public class Layer {
    private long nativePtr;
    
    static {
        System.loadLibrary("BeeDNNJava");
    }
    
    // Existing constructors
    public Layer(String activation) {
        nativePtr = createActivationLayer(activation);
    }
    
    // New constructor using construct method
    public Layer(String type, float[] args, String arg) {
        nativePtr = construct(type, args, arg);
    }
    
    // Static factory method using loadLayer
    public static Layer fromString(String data) {
        Layer layer = new Layer();
        layer.nativePtr = loadLayer(data);
        return layer;
    }
    
    // Private constructor for factory methods
    private Layer() {}
    
    @Override
    protected void finalize() throws Throwable {
        deleteLayer(nativePtr);
        super.finalize();
    }
    
    public long getNativePtr() {
        return nativePtr;
    }
    
    // New static methods
    public static String[] getAvailableLayers() {
        return getAvailable();
    }
    
    public static String getLayerUsage(String type) {
        return getUsage(type);
    }

    public String save() {
        return save(nativePtr);
    }

    
    /**
     * Get all available reduction types
     * @return Array of reduction type names
     */
    public static native String[] getAvailableReductions();

    /**
     * Get all available activation functions
     * @return Array of activation function names
     */
    public static native String[] getAvailableActivations();
    /**
     * Get all available weight initializers
     * @return Array of initializer names
     */
    public static native String[] getAvailableInitializers();
    
    private native String save(long ptr);
    private native long createActivationLayer(String activation);
    private native void deleteLayer(long ptr);
    private static native String[] getAvailable();
    private static native String getUsage(String type);
    private static native long construct(String type, float[] args, String arg);
    private static native long loadLayer(String data);
}