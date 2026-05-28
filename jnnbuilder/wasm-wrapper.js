/**
 * wasm-wrapper.js - Interop layer between JS and BeeDNN WASM
 */

let Module = null;

export async function initWasm() {
    if (Module) return Module;

    return new Promise((resolve, reject) => {
        // BeeDNNModule is defined in beednn.js
        if (typeof BeeDNNModule === 'undefined') {
            reject(new Error('BeeDNNModule not found. Ensure beednn.js is loaded.'));
            return;
        }

        BeeDNNModule({
            locateFile: (path) => `Release-wasm/${path}`
        }).then((module) => {
            Module = module;
            console.log('BeeDNN WASM initialized successfully');
            resolve(Module);
        }).catch(reject);
    });
}

export function getModule() {
    if (!Module) throw new Error('WASM Module not initialized. Call initWasm() first.');
    return Module;
}

/**
 * Utility to convert JS array to WASM VectorFloat
 */
export function jsArrayToVectorFloat(arr) {
    const vec = new Module.VectorFloat();
    for (const val of arr) {
        vec.push_back(val);
    }
    return vec;
}

/**
 * Utility to convert WASM VectorFloat to JS array
 */
export function vectorFloatToJsArray(vec) {
    const arr = [];
    for (let i = 0; i < vec.size(); i++) {
        arr.push(vec.get(i));
    }
    return arr;
}

/**
 * Utility to convert WASM VectorString to JS array
 */
export function vectorStringToJsArray(vec) {
    const arr = [];
    for (let i = 0; i < vec.size(); i++) {
        arr.push(vec.get(i));
    }
    return arr;
}

/**
 * High-level wrapper for creating a layer
 * @param {string} type 
 * @param {number[]} floatArgs 
 * @param {string} stringArg 
 */
export function createLayer(type, floatArgs = [], stringArg = "") {
    const vec = jsArrayToVectorFloat(floatArgs);
    const layer = Module.constructLayer(type, vec, stringArg);
    vec.delete(); // Free WASM vector memory
    return layer;
}

/**
 * Wraps Module.getAvailableLayers()
 */
export function getAvailableLayers() {
    const vec = Module.getAvailableLayers();
    const list = vectorStringToJsArray(vec);
    vec.delete();
    return list;
}

/**
 * Wraps Module.getLayerUsage(type)
 */
export function getLayerUsage(type) {
    return Module.getLayerUsage(type);
}
