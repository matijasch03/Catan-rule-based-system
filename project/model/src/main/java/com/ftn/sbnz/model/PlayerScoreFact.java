package com.ftn.sbnz.model;

/**
 * A transient scoring snapshot consumed by the Drools scoring rules.
 * Board traversal remains outside the rules; the rules decide how many victory
 * points the resulting buildings and longest road are worth.
 */
public class PlayerScoreFact {

    private int playerId;
    private int villageCount;
    private int townCount;
    private int longestRoadLength;
    private long longestRoadReachedAt;
    private int score;
    private boolean villagesScored;
    private boolean townsScored;
    private boolean longestRoadAwarded;
    private boolean winner;

    public PlayerScoreFact() {
    }

    public PlayerScoreFact(int playerId, int villageCount, int townCount,
                           int longestRoadLength, long longestRoadReachedAt) {
        this.playerId = playerId;
        this.villageCount = villageCount;
        this.townCount = townCount;
        this.longestRoadLength = longestRoadLength;
        this.longestRoadReachedAt = longestRoadReachedAt;
    }

    public int getPlayerId() { return playerId; }
    public int getVillageCount() { return villageCount; }
    public int getTownCount() { return townCount; }
    public int getLongestRoadLength() { return longestRoadLength; }
    public long getLongestRoadReachedAt() { return longestRoadReachedAt; }
    public int getScore() { return score; }
    public boolean isVillagesScored() { return villagesScored; }
    public boolean isTownsScored() { return townsScored; }
    public boolean isLongestRoadAwarded() { return longestRoadAwarded; }
    public boolean isWinner() { return winner; }

    public void setScore(int score) { this.score = score; }
    public void setVillagesScored(boolean villagesScored) { this.villagesScored = villagesScored; }
    public void setTownsScored(boolean townsScored) { this.townsScored = townsScored; }
    public void setLongestRoadAwarded(boolean longestRoadAwarded) { this.longestRoadAwarded = longestRoadAwarded; }
    public void setWinner(boolean winner) { this.winner = winner; }
}
