package com.beednn;

public class NetTrain {
    private long nativePtr;
    
    static {
        System.loadLibrary("BeeDNNJava");
    }
    
    public NetTrain() {
        nativePtr = createNetTrain();
    }
    
    @Override
    protected void finalize() throws Throwable {
        deleteNetTrain(nativePtr);
        super.finalize();
    }
    
    public void setTrainData(float[] samples, int sampleRows, int sampleCols, 
                           float[] truth, int truthRows, int truthCols) {
        setTrainData(nativePtr, samples, sampleRows, sampleCols, truth, truthRows, truthCols);
    }
    
    public void setValidationData(float[] samples, int sampleRows, int sampleCols,
                                float[] truth, int truthRows, int truthCols) {
        setValidationData(nativePtr, samples, sampleRows, sampleCols, truth, truthRows, truthCols);
    }
    
    public void setBatchSize(int batchSize) {
        setBatchSize(nativePtr, batchSize);
    }
    
    public void setEpochs(int epochs) {
        setEpochs(nativePtr, epochs);
    }
    
    public void fit(Net net) {
        fit(nativePtr, net.getNativePtr());
    }

    /**
     * Saves the current training configuration to a string.
     * @return String representation of the training configuration
     */
    public String save() {
        return save(nativePtr);
    }
    
    /**
     * Loads training configuration from a saved string.
     * @param data The string containing the saved configuration
     */
    public void load(String data) {
        load(nativePtr, data);
    }
    
    /**
     * Gets the training loss history.
     * @return Array of training loss values for each epoch
     */
    public float[] getTrainLoss() {
        return getTrainLoss(nativePtr);
    }
    
    /**
     * Gets the validation loss history.
     * @return Array of validation loss values for each epoch
     */
    public float[] getValidationLoss() {
        return getValidationLoss(nativePtr);
    }
    
    /**
     * Gets the training accuracy history.
     * @return Array of training accuracy values for each epoch
     */
    public float[] getTrainAccuracy() {
        return getTrainAccuracy(nativePtr);
    }
    
    /**
     * Gets the validation accuracy history.
     * @return Array of validation accuracy values for each epoch
     */
    public float[] getValidationAccuracy() {
        return getValidationAccuracy(nativePtr);
    }
    
    /**
     * Gets a list of all available regularizers.
     * @return Array of regularizer names that can be used with setRegularizer
     */
    public static String[] getAvailableRegularizers() {
        return listRegularizersAvailable();
    }
    
    /**
     * Sets the regularizer with specified parameter.
     * @param regularizer The name of the regularizer to use (e.g., "L1", "L2")
     * @param parameter The regularization parameter value
     */
    public void setRegularizer(String regularizer, float parameter) {
        createAndSetRegularizer(nativePtr, regularizer, parameter);
    }
    
    /**
     * Gets a list of all available optimizers.
     * @return Array of optimizer names that can be used with setOptimizer
     */
    public static String[] getAvailableOptimizers() {
        return listOptimizersAvailable();
    }
    
    /**
     * Sets the optimizer with specified parameters.
     * 
     * @param optimizer The name of the optimizer to use (e.g., "SGD", "Adam", "RMSprop")
     * @param learningRate The learning rate (-1 for default)
     * @param decay The decay rate (-1 for default)
     * @param momentum The momentum value (-1 for default)
     */
    public void setOptimizer(String optimizer, float learningRate, float decay, float momentum) {
        createAndSetOptimizer(nativePtr, optimizer, learningRate, decay, momentum);
    }
    
    /**
     * Sets the optimizer with default parameters.
     * 
     * @param optimizer The name of the optimizer to use
     */
    public void setOptimizer(String optimizer) {
        createAndSetOptimizer(nativePtr, optimizer, -1, -1, -1);
    }


    /**
     * Gets a list of all available loss functions.
     * @return Array of loss function names that can be used with setLoss
     */
    public static String[] getAvailableLosses() {
        return listLossAvailable();
    }
    
    /**
     * Sets the loss function to use during training.
     * @param loss The name of the loss function (e.g., "MSE", "CrossEntropy")
     */
    public void setLoss(String loss) {
        createAndSetLoss(nativePtr, loss);
    }
    
    /**
     * Computes the loss and accuracy for given samples and truth values.
     * The accuracy can be retrieved using getCurrentAccuracy().
     * 
     * @param samples Input samples matrix as flattened array
     * @param sampleRows Number of samples
     * @param sampleCols Number of features per sample
     * @param truth Truth values matrix as flattened array
     * @param truthRows Number of truth rows (should match sampleRows)
     * @param truthCols Number of truth values per sample
     * @return The computed loss value
     */
    public float computeLossAccuracy(float[] samples, int sampleRows, int sampleCols,
                                   float[] truth, int truthRows, int truthCols) {
        return computeLossAccuracy(nativePtr, samples, sampleRows, sampleCols,
                                 truth, truthRows, truthCols);
    }



    private static native String[] listLossAvailable();
    private native void createAndSetLoss(long ptr, String loss);
    private native float computeLossAccuracy(long ptr, 
                                           float[] samples, int sampleRows, int sampleCols,
                                           float[] truth, int truthRows, int truthCols);

    private static native String[] listOptimizersAvailable();
    private native void createAndSetOptimizer(long ptr, String optimizer, float learningRate, 
                                            float decay, float momentum);

    private static native String[] listRegularizersAvailable();
    private native void createAndSetRegularizer(long ptr, String regularizer, float parameter);

    private native String save(long ptr);
    private native void load(long ptr, String data);
    private native float[] getTrainLoss(long ptr);
    private native float[] getValidationLoss(long ptr);
    private native float[] getTrainAccuracy(long ptr);
    private native float[] getValidationAccuracy(long ptr);
    private native long createNetTrain();
    private native void deleteNetTrain(long ptr);
    private native void setTrainData(long ptr, float[] samples, int sampleRows, int sampleCols,
                                   float[] truth, int truthRows, int truthCols);
    private native void setValidationData(long ptr, float[] samples, int sampleRows, int sampleCols,
                                        float[] truth, int truthRows, int truthCols);
    private native void setOptimizer(long ptr, String optimizer);
    private native void setLoss(long ptr, String loss);
    private native void setBatchSize(long ptr, int batchSize);
    private native void setEpochs(long ptr, int epochs);
    private native void fit(long ptr, long netPtr);
}