package com.ftn.sbnz.kjar;

public class BlockadeThreat {

    private int playerId;
    private int edgeId;
    private String direction;
    private int distanceToRoute;
    private int previousDistanceToRoute;
    private int myDistanceToPlan;
    private int opponentRoadCards;
    private int myRoadCards;
    private double score;
    private boolean scoringDone;

    public BlockadeThreat() {
    }

    public BlockadeThreat(int playerId, int edgeId, String direction, int distanceToRoute,
                          int previousDistanceToRoute, int myDistanceToPlan,
                          int opponentRoadCards, int myRoadCards) {
        this.playerId = playerId;
        this.edgeId = edgeId;
        this.direction = direction;
        this.distanceToRoute = distanceToRoute;
        this.previousDistanceToRoute = previousDistanceToRoute;
        this.myDistanceToPlan = myDistanceToPlan;
        this.opponentRoadCards = opponentRoadCards;
        this.myRoadCards = myRoadCards;
    }

    public int getPlayerId() { return playerId; }
    public int getEdgeId() { return edgeId; }
    public String getDirection() { return direction; }
    public int getDistanceToRoute() { return distanceToRoute; }
    public int getPreviousDistanceToRoute() { return previousDistanceToRoute; }
    public int getMyDistanceToPlan() { return myDistanceToPlan; }
    public int getOpponentRoadCards() { return opponentRoadCards; }
    public int getMyRoadCards() { return myRoadCards; }
    public double getScore() { return score; }
    public boolean isScoringDone() { return scoringDone; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setEdgeId(int edgeId) { this.edgeId = edgeId; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setDistanceToRoute(int distanceToRoute) { this.distanceToRoute = distanceToRoute; }
    public void setPreviousDistanceToRoute(int previousDistanceToRoute) { this.previousDistanceToRoute = previousDistanceToRoute; }
    public void setMyDistanceToPlan(int myDistanceToPlan) { this.myDistanceToPlan = myDistanceToPlan; }
    public void setOpponentRoadCards(int opponentRoadCards) { this.opponentRoadCards = opponentRoadCards; }
    public void setMyRoadCards(int myRoadCards) { this.myRoadCards = myRoadCards; }
    public void setScore(double score) { this.score = score; }
    public void setScoringDone(boolean scoringDone) { this.scoringDone = scoringDone; }
}
