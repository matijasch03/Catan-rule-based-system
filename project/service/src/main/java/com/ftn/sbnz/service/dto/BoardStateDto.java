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
    private List<AdviceDto> advices;
    private List<String> availableActions;
    private List<Integer> legalRoadEdgeIds;
    private List<Integer> legalVillageNodeIds;
    private List<Integer> legalTownNodeIds;
    private String turnMessage;

    public BoardStateDto() {
    }

    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase, int lastDiceSum,
                         List<DiceRollDto> diceRolls, List<AdviceDto> advices) {
        this(nodes, edges, players, currentPlayerId, phase, lastDiceSum, diceRolls, advices, List.of());
    }

    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase, int lastDiceSum,
                         List<DiceRollDto> diceRolls, List<AdviceDto> advices,
                         List<String> availableActions) {
        this(nodes, edges, players, currentPlayerId, phase, lastDiceSum, diceRolls, advices,
                availableActions, List.of(), List.of(), List.of());
    }

    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase, int lastDiceSum,
                         List<DiceRollDto> diceRolls, List<AdviceDto> advices,
                         List<String> availableActions, List<Integer> legalRoadEdgeIds,
                         List<Integer> legalVillageNodeIds, List<Integer> legalTownNodeIds) {
        this.nodes = nodes;
        this.edges = edges;
        this.players = players;
        this.currentPlayerId = currentPlayerId;
        this.phase = phase;
        this.lastDiceSum = lastDiceSum;
        this.diceRolls = diceRolls;
        this.advices = advices;
        this.availableActions = availableActions;
        this.legalRoadEdgeIds = legalRoadEdgeIds;
        this.legalVillageNodeIds = legalVillageNodeIds;
        this.legalTownNodeIds = legalTownNodeIds;
        this.turnMessage = "";
    }

    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase, int lastDiceSum,
                         List<DiceRollDto> diceRolls, List<AdviceDto> advices,
                         List<String> availableActions, List<Integer> legalRoadEdgeIds,
                         List<Integer> legalVillageNodeIds, List<Integer> legalTownNodeIds,
                         String turnMessage) {
        this(nodes, edges, players, currentPlayerId, phase, lastDiceSum, diceRolls, advices,
                availableActions, legalRoadEdgeIds, legalVillageNodeIds, legalTownNodeIds);
        this.turnMessage = turnMessage;
    }
    
    // Overload for backward compatibility
    public BoardStateDto(List<NodeDto> nodes, List<EdgeDto> edges, List<PlayerDto> players,
                         Integer currentPlayerId, String phase) {
        this(nodes, edges, players, currentPlayerId, phase, 0, List.of(), List.of());
    }

    public List<NodeDto> getNodes() { return nodes; }
    public List<EdgeDto> getEdges() { return edges; }
    public List<PlayerDto> getPlayers() { return players; }
    public Integer getCurrentPlayerId() { return currentPlayerId; }
    public String getPhase() { return phase; }
    public int getLastDiceSum() { return lastDiceSum; }
    public List<DiceRollDto> getDiceRolls() { return diceRolls; }
    public List<AdviceDto> getAdvices() { return advices; }
    public List<String> getAvailableActions() { return availableActions; }
    public List<Integer> getLegalRoadEdgeIds() { return legalRoadEdgeIds; }
    public List<Integer> getLegalVillageNodeIds() { return legalVillageNodeIds; }
    public List<Integer> getLegalTownNodeIds() { return legalTownNodeIds; }
    public String getTurnMessage() { return turnMessage; }
}
