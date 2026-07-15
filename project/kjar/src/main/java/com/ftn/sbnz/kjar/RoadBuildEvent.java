package com.ftn.sbnz.kjar;

public class RoadBuildEvent {

    private int playerId;
    private int mePlayerId;
    private int edgeId;
    private int turn;
    private int distanceToRoute;
    private int previousDistanceToRoute;
    private String direction;

    public RoadBuildEvent() {
    }

    public RoadBuildEvent(int playerId, int mePlayerId, int edgeId, int turn,
                          int distanceToRoute, int previousDistanceToRoute,
                          String direction) {
        this.playerId = playerId;
        this.mePlayerId = mePlayerId;
        this.edgeId = edgeId;
        this.turn = turn;
        this.distanceToRoute = distanceToRoute;
        this.previousDistanceToRoute = previousDistanceToRoute;
        this.direction = direction;
    }

    public int getPlayerId() { return playerId; }
    public int getMePlayerId() { return mePlayerId; }
    public int getEdgeId() { return edgeId; }
    public int getTurn() { return turn; }
    public int getDistanceToRoute() { return distanceToRoute; }
    public int getPreviousDistanceToRoute() { return previousDistanceToRoute; }
    public String getDirection() { return direction; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setMePlayerId(int mePlayerId) { this.mePlayerId = mePlayerId; }
    public void setEdgeId(int edgeId) { this.edgeId = edgeId; }
    public void setTurn(int turn) { this.turn = turn; }
    public void setDistanceToRoute(int distanceToRoute) { this.distanceToRoute = distanceToRoute; }
    public void setPreviousDistanceToRoute(int previousDistanceToRoute) { this.previousDistanceToRoute = previousDistanceToRoute; }
    public void setDirection(String direction) { this.direction = direction; }
}
