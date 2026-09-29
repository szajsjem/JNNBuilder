/**
 * node-manager.js - Manages the state of nodes and connections
 */

export class ConnectionPoint {
    constructor(node, type, isIn, offsetX, offsetY) {
        this.node = node;
        this.type = type; // e.g., "in", "out"
        this.isIn = isIn;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.radius = 6;
        this.highlighted = false;
    }

    get x() { return this.node.x + this.offsetX; }
    get y() { return this.node.y + this.offsetY; }

    contains(px, py, zoom) {
        const dx = this.x - px;
        const dy = this.y - py;
        return (dx * dx + dy * dy) <= (this.radius * this.radius) / (zoom * zoom);
    }
}

export class Node {
    constructor(id, type, x, y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = 120;
        this.height = 60;
        this.label = type;
        this.selected = false;
        this.highlighted = false;

        this.stringParams = [];
        this.floatParams = [];

        this.inPoint = new ConnectionPoint(this, "in", true, 0, this.height / 2);
        this.outPoint = new ConnectionPoint(this, "out", false, this.width, this.height / 2);
    }

    contains(px, py) {
        return px >= this.x && px <= this.x + this.width &&
            py >= this.y && py <= this.y + this.height;
    }

    // Parameters management
    getParamValue(type, index) {
        if (type === 'string') return this.stringParams[index] || "";
        if (type === 'float') return this.floatParams[index] || 0;
        return null;
    }

    setParamValue(type, index, value) {
        if (type === 'string') this.stringParams[index] = value;
        if (type === 'float') this.floatParams[index] = parseFloat(value) || 0;
    }
}

export class NodeManager {
    constructor() {
        this.nodes = [];
        this.connections = []; // Array of [sourcePoint, targetPoint]
        this.selection = new Set();
        this.nextNodeId = 1;
        this.listeners = [];
    }

    createNode(type, x = 100, y = 100) {
        const node = new Node(this.nextNodeId++, type, x, y);
        this.nodes.push(node);
        this.notifyListeners();
        return node;
    }

    deleteNode(node) {
        this.nodes = this.nodes.filter(n => n !== node);
        this.connections = this.connections.filter(c => c[0].node !== node && c[1].node !== node);
        this.selection.delete(node);
        this.notifyListeners();
    }

    addConnection(source, target) {
        // Prevent duplicate connections
        if (this.connections.some(c => c[0] === source && c[1] === target)) return;

        // Basic validation: out to in
        if (source.isIn || !target.isIn) return;

        this.connections.push([source, target]);
        this.notifyListeners();
    }

    removeConnection(source, target) {
        this.connections = this.connections.filter(c => !(c[0] === source && c[1] === target));
        this.notifyListeners();
    }

    setSelected(node, multi = false) {
        if (!multi) this.selection.clear();
        if (node) {
            this.selection.add(node);
            node.selected = true;
        }
        this.nodes.forEach(n => n.selected = this.selection.has(n));
        this.notifyListeners();
    }

    clearSelection() {
        this.selection.clear();
        this.nodes.forEach(n => n.selected = false);
        this.notifyListeners();
    }

    addListener(callback) {
        this.listeners.push(callback);
    }

    notifyListeners() {
        this.listeners.forEach(cb => cb());
    }

    // Serialization (BNN format simulation)
    serialize() {
        return JSON.stringify({
            nodes: this.nodes.map(n => ({
                id: n.id,
                type: n.type,
                x: n.x,
                y: n.y,
                stringParams: n.stringParams,
                floatParams: n.floatParams
            })),
            connections: this.connections.map(c => ({
                sourceNodeId: c[0].node.id,
                targetNodeId: c[1].node.id
            }))
        });
    }

    deserialize(data) {
        const obj = JSON.parse(data);
        if (!obj || !Array.isArray(obj.nodes) || !Array.isArray(obj.connections)) {
            throw new Error('Invalid network: nodes and connections must be arrays');
        }

        const nodeMap = new Map();
        const nodes = [];
        obj.nodes.forEach(nd => {
            if (!nd || !Number.isSafeInteger(nd.id) || nd.id < 1 ||
                nd.id === Number.MAX_SAFE_INTEGER || nodeMap.has(nd.id) ||
                typeof nd.type !== 'string' || !nd.type.trim() ||
                !Number.isFinite(nd.x) || !Number.isFinite(nd.y) ||
                (nd.stringParams !== undefined && (!Array.isArray(nd.stringParams) ||
                    !nd.stringParams.every(value => typeof value === 'string'))) ||
                (nd.floatParams !== undefined && (!Array.isArray(nd.floatParams) ||
                    !nd.floatParams.every(Number.isFinite)))) {
                throw new Error('Invalid network: malformed or duplicate node');
            }
            const node = new Node(nd.id, nd.type, nd.x, nd.y);
            node.stringParams = nd.stringParams || [];
            node.floatParams = nd.floatParams || [];
            nodes.push(node);
            nodeMap.set(nd.id, node);
        });

        const connections = [];
        obj.connections.forEach(conn => {
            const source = conn && nodeMap.get(conn.sourceNodeId);
            const target = conn && nodeMap.get(conn.targetNodeId);
            if (!source || !target) {
                throw new Error('Invalid network: connection references a missing node');
            }
            connections.push([source.outPoint, target.inPoint]);
        });

        this.nodes = nodes;
        this.connections = connections;
        this.selection.clear();
        this.nextNodeId = nodes.reduce((next, node) => Math.max(next, node.id + 1), 1);
        this.notifyListeners();
    }
}
