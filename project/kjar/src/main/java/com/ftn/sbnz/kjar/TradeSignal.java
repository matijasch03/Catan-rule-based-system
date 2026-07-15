package com.ftn.sbnz.kjar;

import com.ftn.sbnz.model.Resource;

public class TradeSignal {

    private int playerId;
    private Resource resource;
    private Resource offeredResource;
    private int turn;
    private boolean successful;
    private double weightedScore;

    public TradeSignal() {
    }

    public TradeSignal(int playerId, Resource resource, int turn, boolean successful, double weightedScore) {
        this.playerId = playerId;
        this.resource = resource;
        this.turn = turn;
        this.successful = successful;
        this.weightedScore = weightedScore;
    }

    public TradeSignal(int playerId, Resource resource, Resource offeredResource,
                       int turn, boolean successful, double weightedScore) {
        this(playerId, resource, turn, successful, weightedScore);
        this.offeredResource = offeredResource;
    }

    public int getPlayerId() { return playerId; }
    public Resource getResource() { return resource; }
    public Resource getOfferedResource() { return offeredResource; }
    public int getTurn() { return turn; }
    public boolean isSuccessful() { return successful; }
    public double getWeightedScore() { return weightedScore; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setResource(Resource resource) { this.resource = resource; }
    public void setOfferedResource(Resource offeredResource) { this.offeredResource = offeredResource; }
    public void setTurn(int turn) { this.turn = turn; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    public void setWeightedScore(double weightedScore) { this.weightedScore = weightedScore; }
}
