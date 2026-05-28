package com.snnl;

import java.util.Arrays;

public class Net implements AutoCloseable {
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

    public long handle() {
        return handle;
    }

    public boolean init(int inputSize) {
        return initNative(handle, inputSize) != 0;
    }

    public boolean init(int[] inputShape) {
        return initShapeNative(handle, inputShape) != 0;
    }

    public void setClassificationMode(boolean enabled) {
        setClassificationModeNative(handle, enabled);
    }

    public void setTrainMode(boolean enabled) {
        setTrainModeNative(handle, enabled);
    }

    public boolean addDense(int inputSize, int outputSize, String activation) {
        return addDenseNative(handle, inputSize, outputSize, activation) != 0;
    }

    public boolean addLayer(String type, float[] args, String stringArgs) {
        return addLayerNative(handle, type, args, stringArgs) != 0;
    }

    public float[] predict(float[] values, int rows, int cols) {
        return predictNative(handle, values, rows, cols);
    }

    public boolean predict(float[] values, int rows, int cols, float[] output) {
        float[] prediction = predict(values, rows, cols);
        if (prediction.length == 0 || prediction.length > output.length) {
            return false;
        }
        System.arraycopy(prediction, 0, output, 0, prediction.length);
        if (prediction.length < output.length) {
            Arrays.fill(output, prediction.length, output.length, 0.0f);
        }
        return true;
    }

    public float[] predictTensor(float[] values, int[] shape) {
        return predictTensorNative(handle, values, shape);
    }

    public int[] predictTensorShape(float[] values, int[] shape) {
        return predictTensorShapeNative(handle, values, shape);
    }

    public float[] predictClasses(float[] values, int rows, int cols) {
        return predictClassesNative(handle, values, rows, cols);
    }

    public float[] getParams() {
        return getParamsNative(handle);
    }

    public boolean setParams(float[] values) {
        return setParamsNative(handle, values) != 0;
    }

    public boolean mixParams(float[] values, float theta) {
        return mixParamsNative(handle, values, theta) != 0;
    }

    public boolean save(String path) {
        return saveNative(handle, path) != 0;
    }

    public boolean load(String path) {
        return loadNative(handle, path) != 0;
    }

    private native long createNative();

    private native void destroyNative(long handle);

    private native int initNative(long handle, int inputSize);

    private native int initShapeNative(long handle, int[] inputShape);

    private native void setClassificationModeNative(long handle, boolean enabled);

    private native void setTrainModeNative(long handle, boolean enabled);

    private native int addDenseNative(long handle, int inputSize, int outputSize, String activation);

    private native int addLayerNative(long handle, String type, float[] args, String stringArgs);

    private native float[] predictNative(long handle, float[] values, int rows, int cols);

    private native float[] predictTensorNative(long handle, float[] values, int[] shape);

    private native int[] predictTensorShapeNative(long handle, float[] values, int[] shape);

    private native float[] predictClassesNative(long handle, float[] values, int rows, int cols);

    private native float[] getParamsNative(long handle);

    private native int setParamsNative(long handle, float[] values);

    private native int mixParamsNative(long handle, float[] values, float theta);

    private native int saveNative(long handle, String path);

    private native int loadNative(long handle, String path);
}
