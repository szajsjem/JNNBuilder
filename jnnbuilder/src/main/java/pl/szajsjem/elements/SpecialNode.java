package pl.szajsjem.elements;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class SpecialNode extends Node {

    public List<ConnectionPoint> specialPoints = new ArrayList<>();
    public boolean feedbackHighlighted = false;

    public SpecialNode(String label) {
        super(label);
        switch (label) {
            case "LayerPipe":
            case "LayerRNNPipe":
            case "Stacked":
            case "LayerStacked":
            case "Repetitive":
            case "LayerRepetetive":
                specialPoints.add(new ConnectionPoint(this, "IN mirror", false, width / 3, 0));
                specialPoints.add(new ConnectionPoint(this, "OUT pass", true, (2 * width) / 3, 0));
                break;
            case "Router":
            case "LayerRouter":
                specialPoints.add(new ConnectionPoint(this, "IN mirror", false, width / 4, 0));
                specialPoints.add(new ConnectionPoint(this, "experts weight", true, (2 * width) / 4, 0));
                specialPoints.add(new ConnectionPoint(this, "experts", true, (3 * width) / 4, 0));
                break;
        }
    }

    public ConnectionPoint isOverSpecialDot(Point p) {
        for (var sp : specialPoints) {
            if (sp.isOver(p)) return sp;
        }
        return null;
    }

    @Override
    public ConnectionPoint isOverDot(Point mouseRelease) {
        ConnectionPoint result = super.isOverDot(mouseRelease);
        if (result != null) return result;
        return isOverSpecialDot(mouseRelease); // New case for feedback connection
    }

    @Override
    public void paint(Graphics2D g, boolean isSelected) {
        super.paint(g, isSelected);
        for (var sp : specialPoints)
            sp.paint(g, isSelected);
    }
}
