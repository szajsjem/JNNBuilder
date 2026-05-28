/**
 * editor.js - Canvas-based editor renderer and interaction handler
 */

export class Editor {
    constructor(canvas, nodeManager) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.nodeManager = nodeManager;

        this.zoom = 1.0;
        this.offsetX = 0;
        this.offsetY = 0;

        this.isDragging = false;
        this.draggedNodes = [];
        this.lastMouseX = 0;
        this.lastMouseY = 0;

        this.isConnecting = false;
        this.connectionSource = null;
        this.connectionMouseX = 0;
        this.connectionMouseY = 0;

        this.setupListeners();
        this.resize();
        this.render();
    }

    setupListeners() {
        window.addEventListener('resize', () => this.resize());

        this.canvas.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        this.canvas.addEventListener('wheel', (e) => this.handleWheel(e), { passive: false });

        // Context menu suppression
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    }

    resize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        this.canvas.width = rect.width;
        this.canvas.height = rect.height;
        this.render();
    }

    getTransformedPoint(clientX, clientY) {
        const rect = this.canvas.getBoundingClientRect();
        const x = (clientX - rect.left - this.offsetX) / this.zoom;
        const y = (clientY - rect.top - this.offsetY) / this.zoom;
        return { x, y };
    }

    handleMouseDown(e) {
        const { x, y } = this.getTransformedPoint(e.clientX, e.clientY);
        this.lastMouseX = e.clientX;
        this.lastMouseY = e.clientY;

        // Check for connection points first
        for (const node of this.nodeManager.nodes) {
            if (node.outPoint.contains(x, y, this.zoom)) {
                this.isConnecting = true;
                this.connectionSource = node.outPoint;
                this.connectionMouseX = x;
                this.connectionMouseY = y;
                return;
            }
            if (node.inPoint.contains(x, y, this.zoom)) {
                // Connecting to 'in' is not standard start, but we could allow it in reverse
                // For now, only out -> in
                return;
            }
        }

        // Check for nodes
        const clickedNode = this.nodeManager.nodes.find(n => n.contains(x, y));
        if (clickedNode) {
            if (e.button === 0) { // Left click
                if (!e.shiftKey && !clickedNode.selected) {
                    this.nodeManager.setSelected(clickedNode);
                } else if (e.shiftKey) {
                    this.nodeManager.setSelected(clickedNode, true);
                }
                this.isDragging = true;
                this.draggedNodes = Array.from(this.nodeManager.selection);
            }
        } else {
            if (e.button === 0) { // Click on empty space
                this.nodeManager.clearSelection();
                this.isDragging = true; // Canvas panning
                this.draggedNodes = [];
            }
        }
        this.render();
    }

    handleMouseMove(e) {
        const { x, y } = this.getTransformedPoint(e.clientX, e.clientY);

        // Update status coordinates
        document.getElementById('coordinates').innerText = `X: ${Math.round(x)}, Y: ${Math.round(y)}`;

        if (this.isConnecting) {
            this.connectionMouseX = x;
            this.connectionMouseY = y;

            // Highlight potential targets
            this.nodeManager.nodes.forEach(node => {
                node.inPoint.highlighted = node.inPoint.contains(x, y, this.zoom);
            });
            this.render();
            return;
        }

        if (this.isDragging) {
            const dx = (e.clientX - this.lastMouseX) / this.zoom;
            const dy = (e.clientY - this.lastMouseY) / this.zoom;

            if (this.draggedNodes.length > 0) {
                this.draggedNodes.forEach(node => {
                    node.x += dx;
                    node.y += dy;
                });
            } else {
                // Panning
                this.offsetX += e.clientX - this.lastMouseX;
                this.offsetY += e.clientY - this.lastMouseY;
            }

            this.lastMouseX = e.clientX;
            this.lastMouseY = e.clientY;
            this.render();
        } else {
            // Hover states
            let changed = false;
            this.nodeManager.nodes.forEach(node => {
                const isOver = node.contains(x, y);
                if (node.highlighted !== isOver) {
                    node.highlighted = isOver;
                    changed = true;
                }
            });
            if (changed) this.render();
        }
    }

    handleMouseUp(e) {
        if (this.isConnecting) {
            const { x, y } = this.getTransformedPoint(e.clientX, e.clientY);
            const targetNode = this.nodeManager.nodes.find(n => n.inPoint.contains(x, y, this.zoom));

            if (targetNode && targetNode.inPoint !== this.connectionSource) {
                this.nodeManager.addConnection(this.connectionSource, targetNode.inPoint);
            }

            this.nodeManager.nodes.forEach(n => n.inPoint.highlighted = false);
            this.isConnecting = false;
            this.connectionSource = null;
        }

        this.isDragging = false;
        this.draggedNodes = [];
        this.render();
    }

    handleWheel(e) {
        e.preventDefault();
        const delta = -e.deltaY;
        const factor = delta > 0 ? 1.1 : 0.9;

        const rect = this.canvas.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const mouseY = e.clientY - rect.top;

        // Zoom around mouse point
        const wx = (mouseX - this.offsetX) / this.zoom;
        const wy = (mouseY - this.offsetY) / this.zoom;

        const newZoom = Math.min(Math.max(this.zoom * factor, 0.1), 5.0);
        this.zoom = newZoom;

        this.offsetX = mouseX - wx * this.zoom;
        this.offsetY = mouseY - wy * this.zoom;

        document.getElementById('zoom-level').innerText = `${Math.round(this.zoom * 100)}%`;
        this.render();
    }

    drawGrid() {
        const size = 50 * this.zoom;
        const xOffset = this.offsetX % size;
        const yOffset = this.offsetY % size;

        this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
        this.ctx.lineWidth = 1;

        this.ctx.beginPath();
        for (let x = xOffset; x < this.canvas.width; x += size) {
            this.ctx.moveTo(x, 0);
            this.ctx.lineTo(x, this.canvas.height);
        }
        for (let y = yOffset; y < this.canvas.height; y += size) {
            this.ctx.moveTo(0, y);
            this.ctx.lineTo(this.canvas.width, y);
        }
        this.ctx.stroke();
    }

    render() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        this.drawGrid();

        this.ctx.save();
        this.ctx.translate(this.offsetX, this.offsetY);
        this.ctx.scale(this.zoom, this.zoom);

        // Draw connections
        this.nodeManager.connections.forEach(([src, tar]) => {
            this.drawBezier(src.x, src.y, tar.x, tar.y, '#3b82f6');
        });

        // Draw active connection
        if (this.isConnecting && this.connectionSource) {
            this.drawBezier(this.connectionSource.x, this.connectionSource.y,
                this.connectionMouseX, this.connectionMouseY, '#60a5fa', true);
        }

        // Draw nodes
        this.nodeManager.nodes.forEach(node => {
            this.drawNode(node);
        });

        this.ctx.restore();
    }

    drawBezier(x1, y1, x2, y2, color, dashed = false) {
        this.ctx.strokeStyle = color;
        this.ctx.lineWidth = 2;
        if (dashed) this.ctx.setLineDash([5, 5]);
        else this.ctx.setLineDash([]);

        const cp1x = x1 + (x2 - x1) / 2;
        const cp2x = x1 + (x2 - x1) / 2;

        this.ctx.beginPath();
        this.ctx.moveTo(x1, y1);
        this.ctx.bezierCurveTo(cp1x, y1, cp2x, y2, x2, y2);
        this.ctx.stroke();
        this.ctx.setLineDash([]);
    }

    drawNode(node) {
        const { x, y, width, height, selected, highlighted, label } = node;

        // Selection shadow
        if (selected) {
            this.ctx.shadowBlur = 15;
            this.ctx.shadowColor = 'rgba(59, 130, 246, 0.5)';
        }

        // Body
        this.ctx.fillStyle = selected ? 'rgba(30, 41, 59, 0.95)' : 'rgba(15, 23, 42, 0.8)';
        this.ctx.strokeStyle = selected ? '#3b82f6' : (highlighted ? '#64748b' : 'rgba(255, 255, 255, 0.2)');
        this.ctx.lineWidth = selected ? 2 : 1;

        this.roundRect(x, y, width, height, 8);
        this.ctx.fill();
        this.ctx.stroke();

        this.ctx.shadowBlur = 0;

        // Label
        this.ctx.fillStyle = 'white';
        this.ctx.font = '500 12px Inter';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.ctx.fillText(label, x + width / 2, y + height / 2);

        // Connection dots
        this.drawPoint(node.inPoint);
        this.drawPoint(node.outPoint);
    }

    drawPoint(point) {
        this.ctx.fillStyle = point.highlighted ? '#10b981' : 'rgba(255, 255, 255, 0.3)';
        this.ctx.beginPath();
        this.ctx.arc(point.x, point.y, point.radius, 0, Math.PI * 2);
        this.ctx.fill();
        if (point.highlighted) {
            this.ctx.strokeStyle = 'white';
            this.ctx.lineWidth = 1;
            this.ctx.stroke();
        }
    }

    roundRect(x, y, w, h, r) {
        if (w < 2 * r) r = w / 2;
        if (h < 2 * r) r = h / 2;
        this.ctx.beginPath();
        this.ctx.moveTo(x + r, y);
        this.ctx.arcTo(x + w, y, x + w, y + h, r);
        this.ctx.arcTo(x + w, y + h, x, y + h, r);
        this.ctx.arcTo(x, y + h, x, y, r);
        this.ctx.arcTo(x, y, x + w, y, r);
        this.ctx.closePath();
    }
}
