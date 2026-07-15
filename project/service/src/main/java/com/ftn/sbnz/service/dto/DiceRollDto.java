package com.ftn.sbnz.service.dto;

import java.util.List;

public class DiceRollDto {

    private final int playerId;
    private final int playerNumber;
    private final int dieOne;
    private final int dieTwo;
    private final List<String> resourceSummary;

    public DiceRollDto(int playerId, int playerNumber, int dieOne, int dieTwo) {
        this(playerId, playerNumber, dieOne, dieTwo, List.of());
    }

    public DiceRollDto(int playerId, int playerNumber, int dieOne, int dieTwo, List<String> resourceSummary) {
        this.playerId = playerId;
        this.playerNumber = playerNumber;
        this.dieOne = dieOne;
        this.dieTwo = dieTwo;
        this.resourceSummary = resourceSummary == null ? List.of() : resourceSummary;
    }

    public int getPlayerId() { return playerId; }
    public int getPlayerNumber() { return playerNumber; }
    public int getDieOne() { return dieOne; }
    public int getDieTwo() { return dieTwo; }
    public int getSum() { return dieOne + dieTwo; }
    public List<String> getResourceSummary() { return resourceSummary; }
}
