package com.ftn.sbnz.kjar;

import com.ftn.sbnz.model.Resource;

public class ResourceProductionSignal {

    private int playerId;
    private Resource resource;
    private double weightedScore;

    public ResourceProductionSignal() {
    }

    public ResourceProductionSignal(int playerId, Resource resource, double weightedScore) {
        this.playerId = playerId;
        this.resource = resource;
        this.weightedScore = weightedScore;
    }

    public int getPlayerId() { return playerId; }
    public Resource getResource() { return resource; }
    public double getWeightedScore() { return weightedScore; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setResource(Resource resource) { this.resource = resource; }
    public void setWeightedScore(double weightedScore) { this.weightedScore = weightedScore; }
}
