package com.ftn.sbnz.service.dto;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.NodeOrientation;
import com.ftn.sbnz.model.Settlement;

// Flat view of a board vertex for the UI: its pixel position (computed with the
// same geometry the board generator uses), whether it is still free, and any
// settlement placed on it.
public class NodeDto {

    private static final double HALF_WIDTH = Math.sqrt(3.0) / 2.0;

    private int id;
    private double x;
    private double y;
    private boolean free;
    private Settlement settlement;
    private Integer ownerId;

    public NodeDto() {
    }

    public NodeDto(Node node) {
        this.id = node.getId();
        double[] p = position(node);
        this.x = p[0];
        this.y = p[1];
        this.settlement = node.getSettlement();
        this.free = node.getSettlement() == null;
        this.ownerId = node.getOwner() != null ? node.getOwner().getId() : null;
    }

    private static double[] position(Node node) {
        Hexagon hex = node.getPossessiveHexagon();
        double cx = Math.sqrt(3.0) * (hex.getQ() + hex.getR() / 2.0);
        double cy = 1.5 * hex.getR();
        double[] offset = cornerOffset(node.getOrientation());
        return new double[]{cx + offset[0], cy + offset[1]};
    }

    private static double[] cornerOffset(NodeOrientation orientation) {
        return switch (orientation) {
            case N  -> new double[]{ 0.0,        1.0};
            case NE -> new double[]{ HALF_WIDTH,  0.5};
            case SE -> new double[]{ HALF_WIDTH, -0.5};
            case S  -> new double[]{ 0.0,       -1.0};
            case SW -> new double[]{-HALF_WIDTH, -0.5};
            case NW -> new double[]{-HALF_WIDTH,  0.5};
        };
    }

    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isFree() { return free; }
    public Settlement getSettlement() { return settlement; }
    public Integer getOwnerId() { return ownerId; }
}
