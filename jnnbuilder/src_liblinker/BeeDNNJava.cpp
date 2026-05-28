#include <jni.h>
#include "Net.h"
#include "NetTrain.h"
#include "Layer.h"
#include "LayerFactory.h"
#include "Regularizer.h"
#include "Optimizer.h"
#include "Loss.h"
#include <vector>
#include <string>
#include <initializer_list>
#include "Activations.h"
#include "Initializers.h"

#include "allLayers.h"

using namespace beednn;

// Helper functions
jfloatArray matrixToJFloatArray(JNIEnv* env, const MatrixFloat& matrix) {
    jfloatArray result = env->NewFloatArray(matrix.size());
    env->SetFloatArrayRegion(result, 0, matrix.size(), matrix.data());
    return result;
}

MatrixFloat jFloatArrayToMatrix(JNIEnv* env, jfloatArray array, jint rows, jint cols) {
    MatrixFloat matrix(rows, cols);
    jfloat* elements = env->GetFloatArrayElements(array, nullptr);
    std::copy(elements, elements + (rows * cols), matrix.data());
    env->ReleaseFloatArrayElements(array, elements, JNI_ABORT);
    return matrix;
}

// Helper function to convert C++ vector<string> to Java String array
jobjectArray vectorStringToJStringArray(JNIEnv* env, const std::vector<std::string>& vec) {
    jobjectArray result = env->NewObjectArray(vec.size(),
        env->FindClass("java/lang/String"),
        env->NewStringUTF(""));

    for (size_t i = 0; i < vec.size(); i++) {
        env->SetObjectArrayElement(result, i,
            env->NewStringUTF(vec[i].c_str()));
    }
    return result;
}

// Helper function to convert Java float array to initializer_list
std::initializer_list<float> jfloatArrayToInitList(JNIEnv* env, jfloatArray array) {
    jsize length = env->GetArrayLength(array);
    jfloat* elements = env->GetFloatArrayElements(array, nullptr);
    std::initializer_list<float> vec(elements, elements + length);
    env->ReleaseFloatArrayElements(array, elements, JNI_ABORT);
    return vec;
}

// Net class methods
extern "C" {

    JNIEXPORT jlong JNICALL Java_com_beednn_Net_createNet(JNIEnv*, jobject) {
        return reinterpret_cast<jlong>(new Net());
    }

    JNIEXPORT void JNICALL Java_com_beednn_Net_deleteNet(JNIEnv*, jobject, jlong ptr) {
        //delete reinterpret_cast<Net*>(ptr);
    }

    JNIEXPORT void JNICALL Java_com_beednn_Net_addLayer(JNIEnv* env, jobject, jlong ptr, jlong layerPtr) {
        Net* net = reinterpret_cast<Net*>(ptr);
        Layer* layer = reinterpret_cast<Layer*>(layerPtr);
        net->add(layer);
    }

    JNIEXPORT void JNICALL Java_com_beednn_Net_initNetwork(JNIEnv* env, jobject, jlong inputDataSize, jlong ptr) {
        Net* net = reinterpret_cast<Net*>(ptr);
        net->init(inputDataSize, false);
    }

    JNIEXPORT void JNICALL Java_com_beednn_Net_predict(JNIEnv* env, jobject, jlong ptr,
        jfloatArray input, jint rows, jint cols, jfloatArray output) {
        Net* net = reinterpret_cast<Net*>(ptr);

        MatrixFloat inMatrix = jFloatArrayToMatrix(env, input, rows, cols);
        MatrixFloat outMatrix;
        net->predict(inMatrix, outMatrix);

        env->SetFloatArrayRegion(output, 0, outMatrix.size(), outMatrix.data());
    }

    JNIEXPORT void JNICALL Java_com_beednn_Net_setTrainMode(JNIEnv*, jobject, jlong ptr, jboolean trainMode) {
        Net* net = reinterpret_cast<Net*>(ptr);
        net->set_train_mode(trainMode);
    }

    // NetTrain class methods
    JNIEXPORT jlong JNICALL Java_com_beednn_NetTrain_createNetTrain(JNIEnv*, jobject) {
        return reinterpret_cast<jlong>(new NetTrain());
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_deleteNetTrain(JNIEnv*, jobject, jlong ptr) {
        //delete reinterpret_cast<NetTrain*>(ptr);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setTrainData(JNIEnv* env, jobject, jlong ptr,
        jfloatArray samples, jint sampleRows, jint sampleCols,
        jfloatArray truth, jint truthRows, jint truthCols) {

        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);

        MatrixFloat samplesMatrix = jFloatArrayToMatrix(env, samples, sampleRows, sampleCols);
        MatrixFloat truthMatrix = jFloatArrayToMatrix(env, truth, truthRows, truthCols);

        trainer->set_train_data_copy(samplesMatrix, truthMatrix);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setValidationData(JNIEnv* env, jobject, jlong ptr,
        jfloatArray samples, jint sampleRows, jint sampleCols,
        jfloatArray truth, jint truthRows, jint truthCols) {

        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);

        MatrixFloat samplesMatrix = jFloatArrayToMatrix(env, samples, sampleRows, sampleCols);
        MatrixFloat truthMatrix = jFloatArrayToMatrix(env, truth, truthRows, truthCols);

        trainer->set_validation_data(samplesMatrix, truthMatrix);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setOptimizer(JNIEnv* env, jobject, jlong ptr, jstring optimizer) {
        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
        const char* optimizerStr = env->GetStringUTFChars(optimizer, nullptr);
        trainer->set_optimizer(optimizerStr);
        env->ReleaseStringUTFChars(optimizer, optimizerStr);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setLoss(JNIEnv* env, jobject, jlong ptr, jstring loss) {
        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
        const char* lossStr = env->GetStringUTFChars(loss, nullptr);
        trainer->set_loss(lossStr);
        env->ReleaseStringUTFChars(loss, lossStr);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setBatchSize(JNIEnv*, jobject, jlong ptr, jint batchSize) {
        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
        trainer->set_batchsize(batchSize);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_setEpochs(JNIEnv*, jobject, jlong ptr, jint epochs) {
        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
        trainer->set_epochs(epochs);
    }

    JNIEXPORT void JNICALL Java_com_beednn_NetTrain_fit(JNIEnv*, jobject, jlong ptr, jlong netPtr) {
        NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
        Net* net = reinterpret_cast<Net*>(netPtr);
        trainer->fit(*net);
    }

    // Layer class methods
    JNIEXPORT jlong JNICALL Java_com_beednn_Layer_createActivationLayer(JNIEnv* env, jobject, jstring activation) {
        const char* activationStr = env->GetStringUTFChars(activation, nullptr);
        Layer* layer = new LayerActivation(activationStr);
        env->ReleaseStringUTFChars(activation, activationStr);
        return reinterpret_cast<jlong>(layer);
    }

    JNIEXPORT void JNICALL Java_com_beednn_Layer_deleteLayer(JNIEnv*, jobject, jlong ptr) {
        //delete reinterpret_cast<Layer*>(ptr);
    }




JNIEXPORT jobjectArray JNICALL Java_com_beednn_Layer_getAvailable(JNIEnv* env, jclass) {
    std::vector<std::string> available = LayerFactory::getAvailable();
    return vectorStringToJStringArray(env, available);
}

JNIEXPORT jstring JNICALL Java_com_beednn_Layer_getUsage(JNIEnv* env, jclass, jstring type) {
    const char* typeStr = env->GetStringUTFChars(type, nullptr);
    std::string usage = LayerFactory::getUsage(typeStr);
    env->ReleaseStringUTFChars(type, typeStr);
    return env->NewStringUTF(usage.c_str());
}

JNIEXPORT jlong JNICALL Java_com_beednn_Layer_construct(JNIEnv* env, jclass,
    jstring type, jfloatArray args, jstring arg) {

    const char* typeStr = env->GetStringUTFChars(type, nullptr);
    const char* argStr = env->GetStringUTFChars(arg, nullptr);

    auto initList = jfloatArrayToInitList(env, args);
    std::string sArg(argStr);

    Layer* layer = LayerFactory::construct(typeStr, initList, sArg);

    env->ReleaseStringUTFChars(type, typeStr);
    env->ReleaseStringUTFChars(arg, argStr);

    return reinterpret_cast<jlong>(layer);
}

JNIEXPORT jlong JNICALL Java_com_beednn_Layer_loadLayer(JNIEnv* env, jclass, jstring data) {
    const char* dataStr = env->GetStringUTFChars(data, nullptr);

    // Create a string stream from the input string
    std::istringstream stream(dataStr);
    Layer* layer = LayerFactory::loadLayer(stream);

    env->ReleaseStringUTFChars(data, dataStr);

    return reinterpret_cast<jlong>(layer);
}

JNIEXPORT jstring JNICALL Java_com_beednn_Layer_save(JNIEnv* env, jobject, jlong ptr) {
    Layer* layer = reinterpret_cast<Layer*>(ptr);

    // Use stringstream to capture the output
    std::ostringstream stream;

    // Save layer state to the stream
    layer->save(stream);

    // Convert the stream content to Java string
    return env->NewStringUTF(stream.str().c_str());
}


JNIEXPORT jstring JNICALL Java_com_beednn_NetTrain_save(JNIEnv* env, jobject, jlong ptr) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    std::ostringstream stream;

    // Save optimizer settings
    stream << trainer->get_optimizer() << "\n";
    stream << trainer->get_learningrate() << "\n";
    stream << trainer->get_decay() << "\n";
    stream << trainer->get_momentum() << "\n";

    // Save training parameters
    stream << trainer->get_epochs() << "\n";
    stream << trainer->get_batchsize() << "\n";
    stream << trainer->get_loss() << "\n";
    stream << trainer->get_patience() << "\n";
    stream << trainer->get_reboost_every_epochs() << "\n";
    stream << trainer->get_classbalancing() << "\n";
    stream << trainer->get_keepbest() << "\n";
    stream << trainer->get_RandomBatchOrder() << "\n";

    // Save regularizer info
    stream << trainer->get_regularizer() << "\n";
    stream << trainer->get_regularizer_parameter() << "\n";

    return env->NewStringUTF(stream.str().c_str());
}

JNIEXPORT void JNICALL Java_com_beednn_NetTrain_load(JNIEnv* env, jobject obj, jlong ptr, jstring data) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const char* dataStr = env->GetStringUTFChars(data, nullptr);
    std::istringstream stream(dataStr);

    std::string optimizer, loss, regularizer;
    float learningRate, decay, momentum, regParam;
    int epochs, batchSize, patience, reboostEpochs;
    bool classBalancing, keepBest, randomBatch;

    // Read all parameters
    std::getline(stream, optimizer);
    stream >> learningRate >> decay >> momentum;
    stream >> epochs >> batchSize;
    std::getline(stream, loss); // Clear newline
    std::getline(stream, loss);
    stream >> patience >> reboostEpochs;
    stream >> classBalancing >> keepBest >> randomBatch;
    std::getline(stream, regularizer); // Clear newline
    std::getline(stream, regularizer);
    stream >> regParam;

    // Apply the settings
    trainer->set_optimizer(optimizer);
    trainer->set_learningrate(learningRate);
    trainer->set_decay(decay);
    trainer->set_momentum(momentum);
    trainer->set_epochs(epochs);
    trainer->set_batchsize(batchSize);
    trainer->set_loss(loss);
    trainer->set_patience(patience);
    trainer->set_reboost_every_epochs(reboostEpochs);
    trainer->set_classbalancing(classBalancing);
    trainer->set_keepbest(keepBest);
    trainer->set_RandomBatchOrder(randomBatch);
    trainer->set_regularizer(regularizer, regParam);

    env->ReleaseStringUTFChars(data, dataStr);
}

JNIEXPORT jfloatArray JNICALL Java_com_beednn_NetTrain_getTrainLoss(JNIEnv* env, jobject, jlong ptr) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const std::vector<float>& trainLoss = trainer->get_train_loss();

    jfloatArray result = env->NewFloatArray(trainLoss.size());
    env->SetFloatArrayRegion(result, 0, trainLoss.size(), trainLoss.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL Java_com_beednn_NetTrain_getValidationLoss(JNIEnv* env, jobject, jlong ptr) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const std::vector<float>& valLoss = trainer->get_validation_loss();

    jfloatArray result = env->NewFloatArray(valLoss.size());
    env->SetFloatArrayRegion(result, 0, valLoss.size(), valLoss.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL Java_com_beednn_NetTrain_getTrainAccuracy(JNIEnv* env, jobject, jlong ptr) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const std::vector<float>& trainAcc = trainer->get_train_accuracy();

    jfloatArray result = env->NewFloatArray(trainAcc.size());
    env->SetFloatArrayRegion(result, 0, trainAcc.size(), trainAcc.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL Java_com_beednn_NetTrain_getValidationAccuracy(JNIEnv* env, jobject, jlong ptr) {
    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const std::vector<float>& valAcc = trainer->get_validation_accuracy();

    jfloatArray result = env->NewFloatArray(valAcc.size());
    env->SetFloatArrayRegion(result, 0, valAcc.size(), valAcc.data());
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_beednn_NetTrain_listRegularizersAvailable(JNIEnv* env, jclass) {
    std::vector<std::string> regularizers;
    list_regularizer_available(regularizers);

    // Create Java String array
    jobjectArray result = env->NewObjectArray(regularizers.size(),
        env->FindClass("java/lang/String"),
        env->NewStringUTF(""));

    // Fill the array
    for (size_t i = 0; i < regularizers.size(); i++) {
        env->SetObjectArrayElement(result, i,
            env->NewStringUTF(regularizers[i].c_str()));
    }

    return result;
}

JNIEXPORT void JNICALL Java_com_beednn_NetTrain_createAndSetRegularizer(JNIEnv* env, jobject obj,
    jlong ptr, jstring regularizer, jfloat parameter) {

    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const char* regStr = env->GetStringUTFChars(regularizer, nullptr);

    // Create and set the regularizer
    trainer->set_regularizer(regStr, parameter);

    env->ReleaseStringUTFChars(regularizer, regStr);
}

JNIEXPORT jobjectArray JNICALL Java_com_beednn_NetTrain_listOptimizersAvailable(JNIEnv* env, jclass) {
    std::vector<std::string> optimizers;
    list_optimizers_available(optimizers);

    // Create Java String array
    jobjectArray result = env->NewObjectArray(optimizers.size(),
        env->FindClass("java/lang/String"),
        env->NewStringUTF(""));

    // Fill the array
    for (size_t i = 0; i < optimizers.size(); i++) {
        env->SetObjectArrayElement(result, i,
            env->NewStringUTF(optimizers[i].c_str()));
    }

    return result;
}

JNIEXPORT void JNICALL Java_com_beednn_NetTrain_createAndSetOptimizer(JNIEnv* env, jobject obj,
    jlong ptr, jstring optimizer, jfloat learningRate, jfloat decay, jfloat momentum) {

    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const char* optStr = env->GetStringUTFChars(optimizer, nullptr);

    // Set the optimizer
    trainer->set_optimizer(optStr);

    // Set optimizer parameters if provided (not -1)
    if (learningRate >= 0) trainer->set_learningrate(learningRate);
    if (decay >= 0) trainer->set_decay(decay);
    if (momentum >= 0) trainer->set_momentum(momentum);

    env->ReleaseStringUTFChars(optimizer, optStr);
}
JNIEXPORT jobjectArray JNICALL Java_com_beednn_NetTrain_listLossAvailable(JNIEnv* env, jclass) {
    std::vector<std::string> losses;
    list_loss_available(losses);

    // Create Java String array
    jobjectArray result = env->NewObjectArray(losses.size(),
        env->FindClass("java/lang/String"),
        env->NewStringUTF(""));

    // Fill the array
    for (size_t i = 0; i < losses.size(); i++) {
        env->SetObjectArrayElement(result, i,
            env->NewStringUTF(losses[i].c_str()));
    }

    return result;
}

JNIEXPORT void JNICALL Java_com_beednn_NetTrain_createAndSetLoss(JNIEnv* env, jobject obj,
    jlong ptr, jstring loss) {

    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);
    const char* lossStr = env->GetStringUTFChars(loss, nullptr);

    // Create and set the loss function
    trainer->set_loss(lossStr);

    env->ReleaseStringUTFChars(loss, lossStr);
}

JNIEXPORT jfloat JNICALL Java_com_beednn_NetTrain_computeLossAccuracy(JNIEnv* env, jobject obj,
    jlong ptr, jfloatArray samples, jint sampleRows, jint sampleCols,
    jfloatArray truth, jint truthRows, jint truthCols) {

    NetTrain* trainer = reinterpret_cast<NetTrain*>(ptr);

    // Convert Java arrays to C++ matrices
    MatrixFloat samplesMatrix = jFloatArrayToMatrix(env, samples, sampleRows, sampleCols);
    MatrixFloat truthMatrix = jFloatArrayToMatrix(env, truth, truthRows, truthCols);

    float accuracy = 0.0f;
    float loss = trainer->compute_loss_accuracy(samplesMatrix, truthMatrix, &accuracy);

    return loss; // Return the loss value, accuracy will be available through getCurrentAccuracy
}

// Get available reductions
JNIEXPORT jobjectArray JNICALL Java_com_beednn_Layer_getAvailableReductions(JNIEnv* env, jclass cls) {
    // Get reduction names
    std::vector<std::string> reductionNames = getAllReductionNames();

    // Create String array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(reductionNames.size(), stringClass, nullptr);

    // Fill array with reduction names
    for (size_t i = 0; i < reductionNames.size(); i++) {
        env->SetObjectArrayElement(
            result,
            i,
            env->NewStringUTF(reductionNames[i].c_str())
        );
    }

    return result;
}

// Get available activations
JNIEXPORT jobjectArray JNICALL Java_com_beednn_Layer_getAvailableActivations(JNIEnv* env, jclass cls) {
    // Get activation names
    std::vector<std::string> activationNames;
    list_activations_available(activationNames);

    // Create String array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(activationNames.size(), stringClass, nullptr);

    // Fill array with activation names 
    for (size_t i = 0; i < activationNames.size(); i++) {
        env->SetObjectArrayElement(
            result,
            i,
            env->NewStringUTF(activationNames[i].c_str())
        );
    }

    return result;
}
JNIEXPORT jobjectArray JNICALL Java_com_beednn_Layer_getAvailableInitializers(JNIEnv* env, jclass cls) {
    // Get initializer names
    std::vector<std::string> initNames = Initializers::getAllInitializers();

    // Create String array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(initNames.size(), stringClass, nullptr);

    // Fill array with initializer names
    for (size_t i = 0; i < initNames.size(); i++) {
        env->SetObjectArrayElement(
            result,
            i,
            env->NewStringUTF(initNames[i].c_str())
        );
    }

    return result;
}

}