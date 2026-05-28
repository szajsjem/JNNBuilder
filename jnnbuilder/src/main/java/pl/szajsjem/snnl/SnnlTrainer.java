package pl.szajsjem.snnl;

import com.snnl.Net;
import com.snnl.Trainer;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Locale;

public class SnnlTrainer {
    private int epochs = 100;
    private int batchSize = 32;
    private int validationBatch = 32;
    private boolean randomBatchOrder = true;
    private boolean classBalancing = true;
    private boolean keepBest = true;
    private String loss = firstOrDefault(SnnlMetadata.getAvailableLosses(), "MSE");
    private String optimizer = firstOrDefault(SnnlMetadata.getAvailableOptimizers(), "Adam");
    private float learningRate = 0.001f;
    private float momentum = 0.9f;
    private float decay = 0.0f;
    private String regularizer = firstOrDefault(SnnlMetadata.getAvailableRegularizers(), "None");
    private float regularizerParam = 0.01f;

    private float[] trainInputs = new float[0];
    private int trainInputRows;
    private int trainInputCols;
    private float[] trainOutputs = new float[0];
    private int trainOutputRows;
    private int trainOutputCols;

    private float[] validationInputs = new float[0];
    private int validationInputRows;
    private int validationInputCols;
    private float[] validationOutputs = new float[0];
    private int validationOutputRows;
    private int validationOutputCols;

    private float[] trainLoss = new float[0];
    private float[] validationLoss = new float[0];

    public static String[] getAvailableLosses() {
        return SnnlMetadata.getAvailableLosses();
    }

    public static String[] getAvailableOptimizers() {
        return SnnlMetadata.getAvailableOptimizers();
    }

    public static String[] getAvailableRegularizers() {
        return SnnlMetadata.getAvailableRegularizers();
    }

    public void setEpochs(int epochs) {
        this.epochs = Math.max(1, epochs);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public void setLoss(String loss) {
        this.loss = SnnlMetadata.normalizeLoss(loss);
    }

    public void setOptimizer(String optimizer, float learningRate, float decay, float momentum) {
        this.optimizer = SnnlMetadata.normalizeOptimizer(optimizer);
        this.learningRate = learningRate;
        this.decay = decay;
        this.momentum = momentum;
    }

    public void setRegularizer(String regularizer, float parameter) {
        this.regularizer = SnnlMetadata.normalizeRegularizer(regularizer);
        this.regularizerParam = parameter;
    }

    public void setTrainData(float[] samples, int sampleRows, int sampleCols,
                             float[] truth, int truthRows, int truthCols) {
        this.trainInputs = Arrays.copyOf(samples, samples.length);
        this.trainInputRows = sampleRows;
        this.trainInputCols = sampleCols;
        this.trainOutputs = Arrays.copyOf(truth, truth.length);
        this.trainOutputRows = truthRows;
        this.trainOutputCols = truthCols;
    }

    public void setValidationData(float[] samples, int sampleRows, int sampleCols,
                                  float[] truth, int truthRows, int truthCols) {
        this.validationInputs = Arrays.copyOf(samples, samples.length);
        this.validationInputRows = sampleRows;
        this.validationInputCols = sampleCols;
        this.validationOutputs = Arrays.copyOf(truth, truth.length);
        this.validationOutputRows = truthRows;
        this.validationOutputCols = truthCols;
    }

    public void fit(Net net) {
        if (trainInputs.length == 0 || trainOutputs.length == 0) {
            throw new IllegalStateException("Training data has not been configured");
        }

        try (Trainer trainer = new Trainer()) {
            if (!trainer.setTrainData(
                    trainInputs, trainInputRows, trainInputCols,
                    trainOutputs, trainOutputRows, trainOutputCols)) {
                throw new IllegalStateException("Could not set SNNL training data");
            }

            if (hasValidationData() && !trainer.setValidationData(
                    validationInputs, validationInputRows, validationInputCols,
                    validationOutputs, validationOutputRows, validationOutputCols)) {
                throw new IllegalStateException("Could not set SNNL validation data");
            }

            trainer.setBatchSize(batchSize);
            trainer.setEpochs(epochs);
            trainer.setLoss(loss);
            trainer.setOptimizer(optimizer);
            trainer.setLearningRate(learningRate);
            trainer.setRegularizer(regularizer, regularizerParam);

            if (!trainer.fit(net)) {
                throw new IllegalStateException("SNNL training failed");
            }

            trainLoss = trainer.getTrainLoss();
            validationLoss = trainer.getValidationLoss();
        }
    }

    public float[] getTrainLoss() {
        return Arrays.copyOf(trainLoss, trainLoss.length);
    }

    public float[] getValidationLoss() {
        return Arrays.copyOf(validationLoss, validationLoss.length);
    }

    public String save() {
        JSONObject json = new JSONObject();
        json.put("backend", "SNNL");
        json.put("epochs", epochs);
        json.put("batchSize", batchSize);
        json.put("validationBatch", validationBatch);
        json.put("randomBatchOrder", randomBatchOrder);
        json.put("classBalancing", classBalancing);
        json.put("keepBest", keepBest);
        json.put("loss", loss);
        json.put("optimizer", optimizer);
        json.put("learningRate", learningRate);
        json.put("momentum", momentum);
        json.put("decay", decay);
        json.put("regularizer", regularizer);
        json.put("regularizerParam", regularizerParam);
        return json.toString();
    }

    public void load(String serialized) {
        JSONObject json = new JSONObject(serialized);
        setEpochs(json.optInt("epochs", epochs));
        setBatchSize(json.optInt("batchSize", batchSize));
        validationBatch = Math.max(1, json.optInt("validationBatch", validationBatch));
        randomBatchOrder = json.optBoolean("randomBatchOrder", randomBatchOrder);
        classBalancing = json.optBoolean("classBalancing", classBalancing);
        keepBest = json.optBoolean("keepBest", keepBest);
        setLoss(json.optString("loss", loss));
        setOptimizer(
                json.optString("optimizer", optimizer),
                (float) json.optDouble("learningRate", learningRate),
                (float) json.optDouble("decay", decay),
                (float) json.optDouble("momentum", momentum)
        );
        setRegularizer(
                json.optString("regularizer", regularizer),
                (float) json.optDouble("regularizerParam", regularizerParam)
        );
    }

    public boolean isClassificationMode() {
        if (loss == null) {
            return false;
        }
        String normalized = loss.toLowerCase(Locale.ROOT);
        return normalized.contains("crossentropy");
    }

    private boolean hasValidationData() {
        return validationInputs.length > 0 && validationOutputs.length > 0;
    }

    private static String firstOrDefault(String[] values, String fallback) {
        return values.length == 0 ? fallback : values[0];
    }
}
