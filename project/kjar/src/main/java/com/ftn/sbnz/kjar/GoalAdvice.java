package com.ftn.sbnz.kjar;

public class GoalAdvice {

    private int rank;
    private String title;
    private String description;
    private Integer nodeId;

    public GoalAdvice() {
    }

    public GoalAdvice(int rank, String title, String description) {
        this.rank = rank;
        this.title = title;
        this.description = description;
    }

    public GoalAdvice(int rank, String title, String description, Integer nodeId) {
        this(rank, title, description);
        this.nodeId = nodeId;
    }

    public int getRank() { return rank; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getNodeId() { return nodeId; }
    public void setRank(int rank) { this.rank = rank; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setNodeId(Integer nodeId) { this.nodeId = nodeId; }
}
