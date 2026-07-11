package com.ftn.sbnz.service.dto;

public class TradeProposalDto {

    private final boolean bankTrade;
    private final Integer opponentId;
    private final String opponentLabel;
    private final String wantedResource;
    private final String offeredResource;
    private final int offeredAmount;
    private final String summary;

    public TradeProposalDto(boolean bankTrade, Integer opponentId, String opponentLabel,
                            String wantedResource, String offeredResource, int offeredAmount,
                            String summary) {
        this.bankTrade = bankTrade;
        this.opponentId = opponentId;
        this.opponentLabel = opponentLabel;
        this.wantedResource = wantedResource;
        this.offeredResource = offeredResource;
        this.offeredAmount = offeredAmount;
        this.summary = summary;
    }

    public boolean isBankTrade() { return bankTrade; }
    public Integer getOpponentId() { return opponentId; }
    public String getOpponentLabel() { return opponentLabel; }
    public String getWantedResource() { return wantedResource; }
    public String getOfferedResource() { return offeredResource; }
    public int getOfferedAmount() { return offeredAmount; }
    public String getSummary() { return summary; }
}
