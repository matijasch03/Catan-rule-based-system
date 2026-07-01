package com.ftn.sbnz.service.dto;

public class DiceRollDto {

    private final int playerId;
    private final int playerNumber;
    private final int dieOne;
    private final int dieTwo;

    public DiceRollDto(int playerId, int playerNumber, int dieOne, int dieTwo) {
        this.playerId = playerId;
        this.playerNumber = playerNumber;
        this.dieOne = dieOne;
        this.dieTwo = dieTwo;
    }

    public int getPlayerId() { return playerId; }
    public int getPlayerNumber() { return playerNumber; }
    public int getDieOne() { return dieOne; }
    public int getDieTwo() { return dieTwo; }
    public int getSum() { return dieOne + dieTwo; }
}
