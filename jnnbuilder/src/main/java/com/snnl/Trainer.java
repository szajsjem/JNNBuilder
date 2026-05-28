package com.snnl;

public class Trainer implements AutoCloseable {
    static {
        NativeLoader.load();
    }

    private long handle = createNative();

    @Override
    public void close() {
        if (handle != 0L) {
            destroyNative(handle);
            handle = 0L;
        }
    }

    public boolean setTrainData(float[][] samples, float[][] truth) {
        return setTrainDataNative(handle, samples, truth) != 0;
    }

    public boolean setTrainData(float[] samples, int[] sampleShape,
                                float[] truth, int[] truthShape) {
        return setTrainDataShapeNative(handle, samples, sampleShape, truth, truthShape) != 0;
    }

    public boolean setTrainData(float[] samples, int sampleRows, int sampleCols,
                                float[] truth, int truthRows, int truthCols) {
        return setTrainData(samples, new int[]{sampleRows, sampleCols}, truth, new int[]{truthRows, truthCols});
    }

    public boolean setValidationData(float[][] samples, float[][] truth) {
        return setValidationDataNative(handle, samples, truth) != 0;
    }

    public boolean setValidationData(float[] samples, int[] sampleShape,
                                     float[] truth, int[] truthShape) {
        return setValidationDataShapeNative(handle, samples, sampleShape, truth, truthShape) != 0;
    }

    public boolean setValidationData(float[] samples, int sampleRows, int sampleCols,
                                     float[] truth, int truthRows, int truthCols) {
        return setValidationData(samples, new int[]{sampleRows, sampleCols}, truth, new int[]{truthRows, truthCols});
    }

    public void setBatchSize(int batchSize) {
        setBatchSizeNative(handle, batchSize);
    }

    public void setEpochs(int epochs) {
        setEpochsNative(handle, epochs);
    }

    public void setLearningRate(float learningRate) {
        setLearningRateNative(handle, learningRate);
    }

    public void setOptimizer(String optimizer) {
        setOptimizerNative(handle, optimizer);
    }

    public void setLoss(String loss) {
        setLossNative(handle, loss);
    }

    public void setRegularizer(String regularizer, float parameter) {
        setRegularizerNative(handle, regularizer, parameter);
    }

    public boolean fit(Net net) {
        return fitNative(handle, net.handle()) != 0;
    }

    public float[] getTrainLoss() {
        return getTrainLossNative(handle);
    }

    public float[] getValidationLoss() {
        return getValidationLossNative(handle);
    }

    private native long createNative();

    private native void destroyNative(long handle);

    private native int setTrainDataNative(long handle, float[][] samples, float[][] truth);

    private native int setTrainDataShapeNative(
            long handle, float[] samples, int[] sampleShape, float[] truth, int[] truthShape);

    private native int setValidationDataNative(long handle, float[][] samples, float[][] truth);

    private native int setValidationDataShapeNative(
            long handle, float[] samples, int[] sampleShape, float[] truth, int[] truthShape);

    private native void setBatchSizeNative(long handle, int batchSize);

    private native void setEpochsNative(long handle, int epochs);

    private native void setLearningRateNative(long handle, float learningRate);

    private native void setOptimizerNative(long handle, String optimizer);

    private native void setLossNative(long handle, String loss);

    private native void setRegularizerNative(long handle, String regularizer, float parameter);

    private native int fitNative(long handle, long netHandle);

    private native float[] getTrainLossNative(long handle);

    private native float[] getValidationLossNative(long handle);
}
