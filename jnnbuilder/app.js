/**
 * app.js - Main entry point for JNNBuilder Web
 */

import { initWasm } from './wasm-wrapper.js';
import { NodeManager } from './node-manager.js';
import { Editor } from './editor.js';
import { setupSidebar, updatePropertiesPanel } from './ui-components.js';

async function start() {
    console.log('Starting JNNBuilder...');

    // 1. Initialize WASM
    try {
        document.getElementById('status-text').innerText = 'Initializing BeeDNN WASM...';
        await initWasm();
        document.getElementById('status-text').innerText = 'Ready';
    } catch (err) {
        console.error('Failed to initialize WASM:', err);
        document.getElementById('status-text').innerText = 'Error: WASM failed to load';
        alert('Failed to load WASM library. See console for details.');
        return;
    }

    // 2. Initialize Managers
    const nodeManager = new NodeManager();
    const canvas = document.getElementById('editor-canvas');
    const editor = new Editor(canvas, nodeManager);

    // 3. Setup UI
    setupSidebar(nodeManager);

    // 4. Register Listeners
    nodeManager.addListener(() => {
        updatePropertiesPanel(nodeManager);
        editor.render();
    });

    // 5. Global Actions
    document.getElementById('btn-new').onclick = () => {
        if (confirm('Create new network? Current work will be lost.')) {
            location.reload();
        }
    };

    document.getElementById('btn-save').onclick = () => {
        const data = nodeManager.serialize();
        const blob = new Blob([data], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'network.bnn';
        a.click();
    };

    document.getElementById('btn-load').onclick = () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.bnn';
        input.onchange = (e) => {
            const file = e.target.files[0];
            const reader = new FileReader();
            reader.onload = (re) => {
                nodeManager.deserialize(re.target.result);
                document.getElementById('status-text').innerText = `Loaded: ${file.name}`;
            };
            reader.readAsText(file);
        };
        input.click();
    };

    document.getElementById('btn-zoom-in').onclick = () => {
        editor.zoom *= 1.1;
        document.getElementById('zoom-level').innerText = `${Math.round(editor.zoom * 100)}%`;
        editor.render();
    };

    document.getElementById('btn-zoom-out').onclick = () => {
        editor.zoom *= 0.9;
        document.getElementById('zoom-level').innerText = `${Math.round(editor.zoom * 100)}%`;
        editor.render();
    };

    document.getElementById('btn-fit').onclick = () => {
        // Simple zoom reset for now
        editor.zoom = 1.0;
        editor.offsetX = 0;
        editor.offsetY = 0;
        document.getElementById('zoom-level').innerText = `100%`;
        editor.render();
    };

    document.getElementById('btn-export').onclick = () => {
        alert('Export to BeeDNN model coming soon!');
    };

    document.getElementById('btn-train').onclick = () => {
        alert('Training interface coming soon!');
    };

    // Keyboard shortcuts
    window.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 's') {
            e.preventDefault();
            document.getElementById('btn-save').click();
        }
        if (e.ctrlKey && e.key === 'o') {
            e.preventDefault();
            document.getElementById('btn-load').click();
        }
        if (e.key === 'Delete' || e.key === 'Backspace') {
            const selection = Array.from(nodeManager.selection);
            selection.forEach(node => nodeManager.deleteNode(node));
        }
    });

    console.log('JNNBuilder initialized');
}

// Start the app
start();
