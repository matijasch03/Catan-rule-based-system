package com.ftn.sbnz.kjar;

public class RoadLink {

    private int fromNodeId;
    private int toNodeId;
    private int targetNodeId;

    public RoadLink() {
    }

    public RoadLink(int fromNodeId, int toNodeId, int targetNodeId) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.targetNodeId = targetNodeId;
    }

    public int getFromNodeId() { return fromNodeId; }
    public int getToNodeId() { return toNodeId; }
    public int getTargetNodeId() { return targetNodeId; }

    public void setFromNodeId(int fromNodeId) { this.fromNodeId = fromNodeId; }
    public void setToNodeId(int toNodeId) { this.toNodeId = toNodeId; }
    public void setTargetNodeId(int targetNodeId) { this.targetNodeId = targetNodeId; }
}
