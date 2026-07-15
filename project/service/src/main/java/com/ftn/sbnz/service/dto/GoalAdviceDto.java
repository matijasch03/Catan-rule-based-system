package com.ftn.sbnz.service.dto;

import com.ftn.sbnz.kjar.GoalAdvice;

public class GoalAdviceDto {

    private final int rank;
    private final String title;
    private final String description;
    private final Integer nodeId;
    private final boolean tradeAction;
    private final TradeProposalDto tradeProposal;

    public GoalAdviceDto(GoalAdvice advice) {
        this(advice, null);
    }

    public GoalAdviceDto(GoalAdvice advice, TradeProposalDto tradeProposal) {
        this.rank = advice.getRank();
        this.title = advice.getTitle();
        this.description = tradeProposal == null
                ? advice.getDescription()
                : advice.getDescription() + " " + tradeProposal.getSummary();
        this.nodeId = advice.getNodeId();
        this.tradeAction = advice.getTitle().contains("Trade")
                || advice.getTitle().contains("Offer")
                || advice.getTitle().contains("bank trade");
        this.tradeProposal = tradeProposal;
    }

    public GoalAdviceDto(int rank, String title, String description, Integer nodeId,
                         boolean tradeAction, TradeProposalDto tradeProposal) {
        this.rank = rank;
        this.title = title;
        this.description = description;
        this.nodeId = nodeId;
        this.tradeAction = tradeAction;
        this.tradeProposal = tradeProposal;
    }

    public int getRank() { return rank; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getNodeId() { return nodeId; }
    public boolean isTradeAction() { return tradeAction; }
    public TradeProposalDto getTradeProposal() { return tradeProposal; }
}
