package pl.szajsjem.elements;

import java.awt.*;

// Base class for all nodes
public class Node {
    public int x;
    public int y;
    public int width = 100;
    public int height = 60;
    String label, type;
    public ConnectionPoint next = new ConnectionPoint(this, "out", false, width, height / 2);
    public ConnectionPoint prev = new ConnectionPoint(this, "in", true, 0, height / 2);

    protected String[] stringParams;
    protected float[] floatParams;

    public Node(String type) {
        this.type = type;
        this.label = type;
        this.stringParams = new String[0];
        this.floatParams = new float[0];
    }

    public static boolean isSpecial(String name) {
        if (name == null) return false;
        return switch (name) {
            case "Stacked", "LayerStacked",
                 "Repetitive", "LayerRepetetive",
                 "Router", "LayerRouter",
                 "LayerPipe", "LayerRNNPipe" -> true;
            default -> false;
        };
    }

    // Add getters/setters for the new fields
    public String[] getStringParams() {
        return stringParams;
    }

    public void setStringParams(String[] params) {
        this.stringParams = params;
    }

    public float[] getFloatParams() {
        return floatParams;
    }

    public void setFloatParams(float[] params) {
        this.floatParams = params;
    }

    public String getType() {
        return type;
    }

    public void paint(Graphics2D g, boolean isSelected) {
        g.setStroke(new BasicStroke(2.0f));
        // Background
        if (isSelected) {
            // Draw selection border
            g.setColor(new Color(180, 200, 255));
            g.fillRect(x - 2, y - 2, width + 4, height + 4);
        }

        // Node body
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);
        g.setColor(isSelected ? new Color(0, 100, 200) : Color.BLACK);
        g.drawRect(x, y, width, height);
        g.drawString(label, x + 10, y + height / 2);

        // Connection points
        next.paint(g, isSelected);
        prev.paint(g, isSelected);
    }

    //returns what connection point it is over
    public ConnectionPoint isOverDot(Point mouseRelease) {
        if (next.isOver(mouseRelease)) return next;
        if (prev.isOver(mouseRelease)) return prev;
        return null;
    }

    public boolean contains(Point p) {
        Rectangle box = new Rectangle(x - 5, y - 5, width + 10, height + 10);
        return box.contains(p);
    }

    public String getLabel() {
        return label;
    }
}

