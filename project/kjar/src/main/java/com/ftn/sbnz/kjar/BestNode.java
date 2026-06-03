package com.ftn.sbnz.kjar;

public class BestNode {
    private int nodeId;
    private int score;
    private int rank;

    public BestNode(int nodeId, int score, int rank) {
        this.nodeId = nodeId;
        this.score = score;
        this.rank = rank;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return "BestNode{id=" + nodeId + ", score=" + score + ", rank=" + rank + "}";
    }
}
