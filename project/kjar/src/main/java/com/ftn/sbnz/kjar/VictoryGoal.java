package com.ftn.sbnz.kjar;

public class VictoryGoal {

    private int playerId;
    private int targetScore = 10;
    private int currentScore;
    private int longestRoadLength;
    private boolean longestRoadAwarded;
    private boolean hasVillageToUpgrade;
    private boolean hasLegalVillageNode;
    private boolean canBuildRoad;
    private boolean canBuildVillage;
    private boolean canBuildTown;
    private boolean hasOpenRoadEdge;
    private boolean hasOreProducer;
    private boolean hasGrainProducer;
    private boolean tradeAttempted;
    private boolean tradeRefused;
    private int wood;
    private int wool;
    private int grain;
    private int brick;
    private int ore;
    private int totalResources;
    private int roadsMissingForPlannedRoute;
    private int myRoadCards;
    private int opponentRoadCards;
    private Integer bestOreNodeId;
    private int bestOreNodeScore;
    private Integer bestGrainNodeId;
    private int bestGrainNodeScore;
    private Integer bestTownNodeId;
    private int bestTownNodeScore;

    public VictoryGoal() {
    }

    public VictoryGoal(int playerId, int currentScore) {
        this.playerId = playerId;
        this.currentScore = currentScore;
    }

    public int getPlayerId() { return playerId; }
    public int getTargetScore() { return targetScore; }
    public int getCurrentScore() { return currentScore; }
    public int getLongestRoadLength() { return longestRoadLength; }
    public boolean isLongestRoadAwarded() { return longestRoadAwarded; }
    public boolean isHasVillageToUpgrade() { return hasVillageToUpgrade; }
    public boolean isHasLegalVillageNode() { return hasLegalVillageNode; }
    public boolean isCanBuildRoad() { return canBuildRoad; }
    public boolean isCanBuildVillage() { return canBuildVillage; }
    public boolean isCanBuildTown() { return canBuildTown; }
    public boolean isHasOpenRoadEdge() { return hasOpenRoadEdge; }
    public boolean isHasOreProducer() { return hasOreProducer; }
    public boolean isHasGrainProducer() { return hasGrainProducer; }
    public boolean isTradeAttempted() { return tradeAttempted; }
    public boolean isTradeRefused() { return tradeRefused; }
    public int getWood() { return wood; }
    public int getWool() { return wool; }
    public int getGrain() { return grain; }
    public int getBrick() { return brick; }
    public int getOre() { return ore; }
    public int getTotalResources() { return totalResources; }
    public int getRoadsMissingForPlannedRoute() { return roadsMissingForPlannedRoute; }
    public int getMyRoadCards() { return myRoadCards; }
    public int getOpponentRoadCards() { return opponentRoadCards; }
    public Integer getBestOreNodeId() { return bestOreNodeId; }
    public int getBestOreNodeScore() { return bestOreNodeScore; }
    public Integer getBestGrainNodeId() { return bestGrainNodeId; }
    public int getBestGrainNodeScore() { return bestGrainNodeScore; }
    public Integer getBestTownNodeId() { return bestTownNodeId; }
    public int getBestTownNodeScore() { return bestTownNodeScore; }

    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setTargetScore(int targetScore) { this.targetScore = targetScore; }
    public void setCurrentScore(int currentScore) { this.currentScore = currentScore; }
    public void setLongestRoadLength(int longestRoadLength) { this.longestRoadLength = longestRoadLength; }
    public void setLongestRoadAwarded(boolean longestRoadAwarded) { this.longestRoadAwarded = longestRoadAwarded; }
    public void setHasVillageToUpgrade(boolean hasVillageToUpgrade) { this.hasVillageToUpgrade = hasVillageToUpgrade; }
    public void setHasLegalVillageNode(boolean hasLegalVillageNode) { this.hasLegalVillageNode = hasLegalVillageNode; }
    public void setCanBuildRoad(boolean canBuildRoad) { this.canBuildRoad = canBuildRoad; }
    public void setCanBuildVillage(boolean canBuildVillage) { this.canBuildVillage = canBuildVillage; }
    public void setCanBuildTown(boolean canBuildTown) { this.canBuildTown = canBuildTown; }
    public void setHasOpenRoadEdge(boolean hasOpenRoadEdge) { this.hasOpenRoadEdge = hasOpenRoadEdge; }
    public void setHasOreProducer(boolean hasOreProducer) { this.hasOreProducer = hasOreProducer; }
    public void setHasGrainProducer(boolean hasGrainProducer) { this.hasGrainProducer = hasGrainProducer; }
    public void setTradeAttempted(boolean tradeAttempted) { this.tradeAttempted = tradeAttempted; }
    public void setTradeRefused(boolean tradeRefused) { this.tradeRefused = tradeRefused; }
    public void setWood(int wood) { this.wood = wood; }
    public void setWool(int wool) { this.wool = wool; }
    public void setGrain(int grain) { this.grain = grain; }
    public void setBrick(int brick) { this.brick = brick; }
    public void setOre(int ore) { this.ore = ore; }
    public void setTotalResources(int totalResources) { this.totalResources = totalResources; }
    public void setRoadsMissingForPlannedRoute(int roadsMissingForPlannedRoute) { this.roadsMissingForPlannedRoute = roadsMissingForPlannedRoute; }
    public void setMyRoadCards(int myRoadCards) { this.myRoadCards = myRoadCards; }
    public void setOpponentRoadCards(int opponentRoadCards) { this.opponentRoadCards = opponentRoadCards; }
    public void setBestOreNodeId(Integer bestOreNodeId) { this.bestOreNodeId = bestOreNodeId; }
    public void setBestOreNodeScore(int bestOreNodeScore) { this.bestOreNodeScore = bestOreNodeScore; }
    public void setBestGrainNodeId(Integer bestGrainNodeId) { this.bestGrainNodeId = bestGrainNodeId; }
    public void setBestGrainNodeScore(int bestGrainNodeScore) { this.bestGrainNodeScore = bestGrainNodeScore; }
    public void setBestTownNodeId(Integer bestTownNodeId) { this.bestTownNodeId = bestTownNodeId; }
    public void setBestTownNodeScore(int bestTownNodeScore) { this.bestTownNodeScore = bestTownNodeScore; }
}
