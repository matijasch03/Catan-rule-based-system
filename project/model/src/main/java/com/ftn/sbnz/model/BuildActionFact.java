package com.ftn.sbnz.model;

public class BuildActionFact {

    private int playerId;
    private int wood;
    private int wool;
    private int grain;
    private int brick;
    private int ore;
    private int connectedRoadsFromVillage;
    private boolean hasOpenRoadEdge;
    private boolean hasLegalVillageNode;
    private boolean hasVillageToUpgrade;
    private boolean canBuildRoad;
    private boolean canBuildVillage;
    private boolean canBuildTown;

    public BuildActionFact() {
    }

    public BuildActionFact(int playerId) {
        this.playerId = playerId;
    }

    public int getPlayerId() { return playerId; }
    public int getWood() { return wood; }
    public int getWool() { return wool; }
    public int getGrain() { return grain; }
    public int getBrick() { return brick; }
    public int getOre() { return ore; }
    public int getConnectedRoadsFromVillage() { return connectedRoadsFromVillage; }
    public boolean isHasOpenRoadEdge() { return hasOpenRoadEdge; }
    public boolean isHasLegalVillageNode() { return hasLegalVillageNode; }
    public boolean isHasVillageToUpgrade() { return hasVillageToUpgrade; }
    public boolean isCanBuildRoad() { return canBuildRoad; }
    public boolean isCanBuildVillage() { return canBuildVillage; }
    public boolean isCanBuildTown() { return canBuildTown; }

    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public void setWood(int wood) { this.wood = wood; }
    public void setWool(int wool) { this.wool = wool; }
    public void setGrain(int grain) { this.grain = grain; }
    public void setBrick(int brick) { this.brick = brick; }
    public void setOre(int ore) { this.ore = ore; }
    public void setConnectedRoadsFromVillage(int connectedRoadsFromVillage) {
        this.connectedRoadsFromVillage = connectedRoadsFromVillage;
    }
    public void setHasOpenRoadEdge(boolean hasOpenRoadEdge) { this.hasOpenRoadEdge = hasOpenRoadEdge; }
    public void setHasLegalVillageNode(boolean hasLegalVillageNode) { this.hasLegalVillageNode = hasLegalVillageNode; }
    public void setHasVillageToUpgrade(boolean hasVillageToUpgrade) {
        this.hasVillageToUpgrade = hasVillageToUpgrade;
    }
    public void setCanBuildRoad(boolean canBuildRoad) { this.canBuildRoad = canBuildRoad; }
    public void setCanBuildVillage(boolean canBuildVillage) { this.canBuildVillage = canBuildVillage; }
    public void setCanBuildTown(boolean canBuildTown) { this.canBuildTown = canBuildTown; }
}
