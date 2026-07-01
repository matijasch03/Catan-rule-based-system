package com.ftn.sbnz.service.dto;

import java.util.List;

import com.ftn.sbnz.model.Advice;

public class AdviceDto {

    private final int rank;
    private final int nodeId;
    private final int score;
    private final String description;
    private final List<String> tags;

    public AdviceDto(Advice advice, int rank, int score, List<String> tags) {
        this.rank = rank;
        this.nodeId = advice.getTargetNode().getId();
        this.score = score;
        this.description = advice.getDescription();
        this.tags = tags;
    }

    public int getRank() { return rank; }
    public int getNodeId() { return nodeId; }
    public int getScore() { return score; }
    public String getDescription() { return description; }
    public List<String> getTags() { return tags; }
}
