import assert from 'node:assert/strict';
import test from 'node:test';
import { NodeManager } from '../node-manager.js';

test('loads a network in one update and resets the next node ID', () => {
    const manager = new NodeManager();
    manager.createNode('Old');
    const selected = manager.nodes[0];
    manager.setSelected(selected);
    let updates = 0;
    manager.addListener(() => updates++);

    manager.deserialize(JSON.stringify({
        nodes: [
            { id: 2, type: 'Input', x: 10, y: 20, stringParams: ['a'], floatParams: [0.5] },
            { id: 4, type: 'Output', x: 30, y: 40, stringParams: [], floatParams: [] }
        ],
        connections: [{ sourceNodeId: 2, targetNodeId: 4 }]
    }));

    assert.equal(updates, 1);
    assert.equal(manager.selection.size, 0);
    assert.equal(manager.connections[0][0].node.id, 2);
    assert.equal(manager.connections[0][1].node.id, 4);
    assert.equal(manager.createNode('Next').id, 5);
});

test('rejects malformed files without changing the current network', () => {
    const manager = new NodeManager();
    const first = manager.createNode('Input');
    const second = manager.createNode('Output');
    manager.addConnection(first.outPoint, second.inPoint);
    manager.setSelected(first);
    const before = manager.serialize();
    let updates = 0;
    manager.addListener(() => updates++);

    const invalid = [
        '{',
        '{}',
        JSON.stringify({ nodes: [{ id: 3, type: 'Valid', x: 0, y: 0 },
            { id: 3, type: 'Duplicate', x: 0, y: 0 }], connections: [] }),
        JSON.stringify({ nodes: [{ id: 3, type: 'Valid', x: 0, y: 0 }],
            connections: [{ sourceNodeId: 3, targetNodeId: 99 }] }),
        JSON.stringify({ nodes: [{ id: Number.MAX_SAFE_INTEGER, type: 'Valid', x: 0, y: 0 }],
            connections: [] }),
        JSON.stringify({ nodes: [{ id: 3, type: 'Valid', x: null, y: 0 }], connections: [] })
    ];

    for (const data of invalid) {
        assert.throws(() => manager.deserialize(data));
        assert.equal(manager.serialize(), before);
        assert.equal(manager.selection.has(first), true);
        assert.equal(updates, 0);
    }
    assert.equal(manager.createNode('Next').id, 3);
});
