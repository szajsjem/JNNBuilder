package com.beednn;

public class Net {
    private long nativePtr;
    
    static {
        System.loadLibrary("BeeDNNJava");
    }
    
    public Net() {
        nativePtr = createNet();
    }
    
    @Override
    protected void finalize() throws Throwable {
        deleteNet(nativePtr);
        super.finalize();
    }
    
    public void addLayer(Layer layer) {
        addLayer(nativePtr, layer.getNativePtr());
    }
    
    public void predict(float[] input, int rows, int cols, float[] output) {
        predict(nativePtr, input, rows, cols, output);
    }
    
    public void setTrainMode(boolean trainMode) {
        setTrainMode(nativePtr, trainMode);
    }

    public long getNativePtr(){
        return nativePtr;
    }

    public void init(long inputDataSize){
        initNetwork(inputDataSize,nativePtr);
    }
    
    // Native method declarations
    private native long createNet();
    private native void deleteNet(long ptr);
    private native void initNetwork(long inputDataSize, long ptr);
    private native void addLayer(long ptr, long layerPtr);
    private native void predict(long ptr, float[] input, int rows, int cols, float[] output);
    private native void setTrainMode(long ptr, boolean trainMode);
}