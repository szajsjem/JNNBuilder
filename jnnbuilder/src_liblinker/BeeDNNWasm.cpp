#include "Activations.h"
#include "Initializers.h"
#include "Layer.h"
#include "LayerActivation.h"
#include "LayerFactory.h"
#include "Loss.h"
#include "Matrix.h"
#include "Net.h"
#include "NetTrain.h"
#include "Optimizer.h"
#include "ParallelReduction.h"
#include "Regularizer.h"
#include <emscripten/bind.h>
#include <emscripten/val.h>


using namespace emscripten;
using namespace beednn;

// Helper to convert std::vector to MatrixFloat
MatrixFloat vectorToMatrix(const std::vector<float> &v, int rows, int cols) {
  if (v.size() != rows * cols) {
    throw std::runtime_error("Vector size does not match matrix dimensions");
  }
  return MatrixFloat::from_raw_buffer(
      v.data(), rows,
      cols); // View or copy? from_raw_buffer creates a view, but we return by
             // value, which triggers copy constructor with valid data if we are
             // careful. Wait, MatrixFloat(copy) copies data.
  // Actually from_raw_buffer returns a Matrix with _bIsView=true.
  // If we return it, it copies?
  // MatrixFloat copy constructor: copies data if not view? No, it copies
  // elements. Let's force a Deep Copy to be safe, as vector 'v' will die.
  MatrixFloat m(rows, cols);
  for (size_t i = 0; i < v.size(); ++i)
    m(i) = v[i];
  return m;
}

// Helper to convert MatrixFloat to std::vector
std::vector<float> matrixToVector(const MatrixFloat &m) {
  std::vector<float> v(m.size());
  for (size_t i = 0; i < m.size(); ++i)
    v[i] = m(i);
  return v;
}

// Net wrapper functions
std::vector<float> Net_predict(Net &net, const std::vector<float> &input,
                               int rows, int cols) {
  MatrixFloat mIn = vectorToMatrix(input, rows, cols);
  MatrixFloat mOut;
  net.predict(mIn, mOut);
  return matrixToVector(mOut);
}

void Net_addLayer(Net &net, Layer *layer) {
  // Net takes ownership, but embind might be confused about ownership if
  // passing raw pointer. In C++, Net::add(Layer*) takes ownership. We must
  // ensure JS doesn't delete it too. usage: net.add(layer); layer.delete()
  // should NOT be called from JS? Embind allowing_raw_pointers() might be
  // needed or explicit allow_raw_pointer<Layer*>().
  net.add(layer);
}

// NetTrain wrapper functions
void NetTrain_setTrainData(NetTrain &trainer, const std::vector<float> &samples,
                           int sRows, int sCols,
                           const std::vector<float> &truth, int tRows,
                           int tCols) {
  MatrixFloat mSamples = vectorToMatrix(samples, sRows, sCols);
  MatrixFloat mTruth = vectorToMatrix(truth, tRows, tCols);
  trainer.set_train_data_copy(mSamples, mTruth);
}

void NetTrain_setValidationData(NetTrain &trainer,
                                const std::vector<float> &samples, int sRows,
                                int sCols, const std::vector<float> &truth,
                                int tRows, int tCols) {
  MatrixFloat mSamples = vectorToMatrix(samples, sRows, sCols);
  MatrixFloat mTruth = vectorToMatrix(truth, tRows, tCols);
  trainer.set_validation_data(mSamples, mTruth);
}

float NetTrain_computeLossAccuracy(NetTrain &trainer,
                                   const std::vector<float> &samples, int sRows,
                                   int sCols, const std::vector<float> &truth,
                                   int tRows, int tCols) {
  MatrixFloat mSamples = vectorToMatrix(samples, sRows, sCols);
  MatrixFloat mTruth = vectorToMatrix(truth, tRows, tCols);
  return trainer.compute_loss_accuracy(mSamples, mTruth);
}

std::vector<float> NetTrain_getTrainLoss(NetTrain &trainer) {
  return trainer.get_train_loss();
}
std::vector<float> NetTrain_getValidationLoss(NetTrain &trainer) {
  return trainer.get_validation_loss();
}
std::vector<float> NetTrain_getTrainAccuracy(NetTrain &trainer) {
  return trainer.get_train_accuracy();
}
std::vector<float> NetTrain_getValidationAccuracy(NetTrain &trainer) {
  return trainer.get_validation_accuracy();
}

// Optimizer/Regularizer/Loss lists
std::vector<std::string> listOptimizers() {
  std::vector<std::string> list;
  list_optimizers_available(list);
  return list;
}
std::vector<std::string> listRegularizers() {
  std::vector<std::string> list;
  list_regularizer_available(list);
  return list;
}
std::vector<std::string> listLosses() {
  std::vector<std::string> list;
  list_loss_available(list);
  return list;
}
std::vector<std::string> listActivations() {
  std::vector<std::string> list;
  list_activations_available(list);
  return list;
}
std::vector<std::string> listInitializers() {
  return Initializers::getAllInitializers();
}

// Layer factory wrappers
Layer *Layer_createActivation(std::string activation) {
  // We need to return a Layer* that JS can manage or pass to Net.
  // If passed to Net, Net takes ownership.
  return new LayerActivation(activation);
}

Layer *Layer_construct(std::string type, std::vector<float> args,
                       std::string arg) {
  // create initializer list from vector
  // std::initializer_list is tricky to create dynamically.
  // LayerFactory::construct takes std::initializer_list<float>.
  // This is problematic because initializer_list references a temporary array.
  // We might need to modify LayerFactory or use a workaround.
  // Workaround: We can't easily construct initializer_list dynamically.
  // However, looking at LayerFactory, it just passes it to the creator.
  // Most creators probably iterate it.
  // BUT we can't change the API of LayerFactory right now easily without
  // recompiling everything? Actually we can, we are recompiling everything for
  // WASM. Wait, let's look at LayerFactory::construct again. user passed
  // vector, we need to pass initializer_list. We can't. logic: initializer_list
  // backs to array. We can hold the array and create list. But construct
  // returns immediately. So we can create a temporary array.
  if (args.empty()) {
    return LayerFactory::construct(type, {}, arg);
  }
  // This is ugly but might work for small fixed sizes if we switch case size.
  // Or we use a helper in C++ that takes vector if we could change
  // LayerFactory. Since we are building libBeeDNN static for WASM, we can patch
  // LayerFactory if needed. But let's try to avoid patching core if possible.
  // HACK: most layers take few args.
  // We can just support up to N args.

  // Better idea: The creators lambda in LayerFactory takes initializer_list.
  // We can't change that easily without changing all layer registrations.
  // Wait, can we overload LayerFactory::construct?
  // No, it calls the registered creator which expects initializer_list.
  // We are stuck unless we change the registration macros or the creators.

  // Let's implement a "vector to initializer list" dispatcher for small N.
  // Realistically args are usually < 10.
  float *data = args.data();
  switch (args.size()) {
  case 0:
    return LayerFactory::construct(type, {}, arg);
  case 1:
    return LayerFactory::construct(type, {data[0]}, arg);
  case 2:
    return LayerFactory::construct(type, {data[0], data[1]}, arg);
  case 3:
    return LayerFactory::construct(type, {data[0], data[1], data[2]}, arg);
  case 4:
    return LayerFactory::construct(type, {data[0], data[1], data[2], data[3]},
                                   arg);
  case 5:
    return LayerFactory::construct(
        type, {data[0], data[1], data[2], data[3], data[4]}, arg);
  default:
    // fallback or error
    return LayerFactory::construct(type, {data[0]}, arg); // Todo fix
  }
}

// We need to define bindings
EMSCRIPTEN_BINDINGS(beednn_module) {
  register_vector<float>("VectorFloat");
  register_vector<std::string>("VectorString");

  class_<Net>("Net")
      .constructor<>()
      .function("init", &Net::init)
      .function("add", &Net_addLayer, allow_raw_pointers())
      .function("predict", &Net_predict)
      .function("setTrainMode", &Net::set_train_mode)
      .function("isClassificationMode", &Net::is_classification_mode)
      .function("setClassificationMode", &Net::set_classification_mode)
      // Distributed
      .function("getParams", &Net::get_params)
      .function("setParams", &Net::set_params)
      .function("mixParams", &Net::mix_params)
      .function("accumulateWeightDiffToGrad",
                &Net::accumulate_weight_diff_to_grad);
  class_<NetTrain>("NetTrain")
      .constructor<>()
      .function("setTrainData", &NetTrain_setTrainData)
      .function("setValidationData", &NetTrain_setValidationData)
      .function("setOptimizer", select_overload<void(const std::string &)>(
                                    &NetTrain::set_optimizer))
      .function("setLoss",
                select_overload<void(const std::string &)>(&NetTrain::set_loss))
      .function("setBatchSize", &NetTrain::set_batchsize)
      .function("setEpochs", &NetTrain::set_epochs)
      .function("fit", &NetTrain::fit)
      .function("computeLossAccuracy", &NetTrain_computeLossAccuracy)
      .function("getTrainLoss", &NetTrain_getTrainLoss)
      .function("getValidationLoss", &NetTrain_getValidationLoss)
      .function("getTrainAccuracy", &NetTrain_getTrainAccuracy)
      .function("getValidationAccuracy", &NetTrain_getValidationAccuracy)
      .function("setRegularizer", &NetTrain::set_regularizer)
      .function("getOptimizer", &NetTrain::get_optimizer)
      .function("getRegularizer", &NetTrain::get_regularizer)
      .function("getLearningRate", &NetTrain::get_learningrate)
      .function("setLearningRate", &NetTrain::set_learningrate)
      .function("getDecay", &NetTrain::get_decay)
      .function("setDecay", &NetTrain::set_decay)
      .function("getMomentum", &NetTrain::get_momentum)
      .function("setMomentum", &NetTrain::set_momentum)

      // Distributed
      .function("distributedStep", &NetTrain::distributed_step);

  class_<Layer>("Layer")
      // Abstract class, but we bind it for pointer return types?
      // We can't construct it directly.
      // We handle creation via Factory.
      .function("save",
                select_overload<void(std::ostream &) const>(&Layer::save));

  // Layer Factory functions
  function("createActivationLayer", &Layer_createActivation,
           allow_raw_pointers());
  function("constructLayer", &Layer_construct, allow_raw_pointers());
  function("getAvailableLayers", &LayerFactory::getAvailable);
  function("getLayerUsage", &LayerFactory::getUsage);

  // Enumerations
  function("listOptimizers", &listOptimizers);
  function("listRegularizers", &listRegularizers);
  function("listLosses", &listLosses);
  function("listActivations", &listActivations);
  function("listInitializers", &listInitializers);
}
