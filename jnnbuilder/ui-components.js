/**
 * ui-components.js - Handles UI updates and dynamic form generation
 */

import { getAvailableLayers, getLayerUsage } from './wasm-wrapper.js';

export function setupSidebar(nodeManager) {
    const palette = document.getElementById('layer-palette');
    const layers = getAvailableLayers();

    layers.forEach(layer => {
        const btn = document.createElement('button');
        btn.className = 'layer-btn';
        btn.innerText = layer;
        btn.onclick = () => {
            const node = nodeManager.createNode(layer, 100, 100);
            nodeManager.setSelected(node);
        };
        palette.appendChild(btn);
    });
}

export function updatePropertiesPanel(nodeManager) {
    const panel = document.getElementById('properties-panel');
    const selection = Array.from(nodeManager.selection);

    panel.innerHTML = '';

    if (selection.length === 0) {
        panel.innerHTML = '<div class="empty-state">No layer selected</div>';
        return;
    }

    if (selection.length > 1) {
        const sameType = selection.every(n => n.type === selection[0].type);
        if (!sameType) {
            panel.innerHTML = '<div class="empty-state">Multiple types selected</div>';
            return;
        }
    }

    const node = selection[0];
    const usage = getLayerUsage(node.type);
    const lines = usage.split('\n');

    // Header
    const title = document.createElement('h3');
    title.innerText = node.type;
    title.style.marginBottom = '10px';
    panel.appendChild(title);

    const desc = document.createElement('p');
    desc.innerText = lines[0];
    desc.style.fontSize = '0.8rem';
    desc.style.color = '#94a3b8';
    desc.style.marginBottom = '15px';
    panel.appendChild(desc);

    // Dynamic fields based on usage string (from java logic)
    if (lines.length > 1 && lines[1].trim()) {
        const stringDescs = lines[1].split(';');
        stringDescs.forEach((d, i) => {
            const label = d.trim();
            if (label.length < 3) return;
            if (label.toLowerCase().includes('layer')) return; // Reference type, handle later

            panel.appendChild(createPropertyField(label, node.getParamValue('string', i), (val) => {
                selection.forEach(n => n.setParamValue('string', i, val));
                nodeManager.notifyListeners();
            }));
        });
    }

    if (lines.length > 2 && lines[2].trim()) {
        const floatDescs = lines[2].split(';');
        floatDescs.forEach((d, i) => {
            const label = d.trim();
            if (label.length < 3) return;

            panel.appendChild(createPropertyField(label, node.getParamValue('float', i), (val) => {
                selection.forEach(n => n.setParamValue('float', i, val));
                nodeManager.notifyListeners();
            }, 'number'));
        });
    }
}

function createPropertyField(label, value, onChange, type = 'text') {
    const group = document.createElement('div');
    group.className = 'property-group';

    const lbl = document.createElement('label');
    lbl.innerText = label;
    group.appendChild(lbl);

    const input = document.createElement('input');
    input.type = type;
    input.value = value;
    input.onchange = (e) => onChange(e.target.value);
    group.appendChild(input);

    return group;
}
