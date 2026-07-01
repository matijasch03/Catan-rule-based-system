package com.ftn.sbnz.service.dto;

import java.util.List;

// Full state the board UI renders: vertices, edges, players and whose turn it
// is during the initial placement phase.
public class BoardStateDto {

    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private List<PlayerDto> players;
    private Integer currentPlayerId;
    private String phase;
    private int lastDiceSum;
    private List<DiceRollDto> diceRolls;

    public BoardStateDto() {
    }

    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase, int lastDiceSum,
                         List<DiceRollDto> diceRolls) {
        this.nodes = nodes;
        this.edges = edges;
        this.players = players;
        this.currentPlayerId = currentPlayerId;
        this.phase = phase;
        this.lastDiceSum = lastDiceSum;
        this.diceRolls = diceRolls;
    }
    
    // Overload for backward compatibility
    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase) {
        this(nodes, edges, players, currentPlayerId, phase, 0, List.of());
    }

    public List<NodeDto> getNodes() { return nodes; }
    public List<EdgeDto> getEdges() { return edges; }
    public List<PlayerDto> getPlayers() { return players; }
    public Integer getCurrentPlayerId() { return currentPlayerId; }
    public String getPhase() { return phase; }
    public int getLastDiceSum() { return lastDiceSum; }
    public List<DiceRollDto> getDiceRolls() { return diceRolls; }
}
