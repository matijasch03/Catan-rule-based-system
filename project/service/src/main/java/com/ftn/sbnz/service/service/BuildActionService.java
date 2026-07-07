package com.ftn.sbnz.service.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.BuildActionFact;
import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.model.Settlement;

@Service
public class BuildActionService {

    public static final String ROAD = "ROAD";
    public static final String VILLAGE = "VILLAGE";
    public static final String TOWN = "TOWN";

    private final KieContainer kieContainer;
    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final ScoringService scoringService;

    public BuildActionService(KieContainer kieContainer, NodeService nodeService, EdgeService edgeService,
                              PlayerService playerService, ScoringService scoringService) {
        this.kieContainer = kieContainer;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
        this.scoringService = scoringService;
    }

    public List<String> availableActions(Player player) {
        BuildActionFact fact = evaluate(player);
        List<String> actions = new ArrayList<>();
        if (fact.isCanBuildRoad()) actions.add(ROAD);
        if (fact.isCanBuildVillage()) actions.add(VILLAGE);
        if (fact.isCanBuildTown()) actions.add(TOWN);
        return actions;
    }

    public List<Integer> legalRoadEdgeIds(Player player) {
        if (!evaluate(player).isCanBuildRoad()) {
            return List.of();
        }
        return legalRoadEdges(player.getId()).stream().map(Edge::getId).toList();
    }

    public List<Integer> legalVillageNodeIds(Player player) {
        if (!evaluate(player).isCanBuildVillage()) {
            return List.of();
        }
        return legalVillageNodes(player.getId()).stream().map(Node::getId).toList();
    }

    public List<Integer> legalTownNodeIds(Player player) {
        if (!evaluate(player).isCanBuildTown()) {
            return List.of();
        }
        return villagesToUpgrade(player.getId()).stream().map(Node::getId).toList();
    }

    public void build(Player player, String action, Integer nodeId, Integer edgeId) {
        switch (action == null ? "" : action.toUpperCase()) {
            case ROAD -> buildRoad(player, edgeId);
            case VILLAGE -> buildVillage(player, nodeId);
            case TOWN -> buildTown(player, nodeId);
            default -> throw new GameActionException(HttpStatus.BAD_REQUEST, "Unknown build action.");
        }
    }

    private BuildActionFact evaluate(Player player) {
        BuildActionFact fact = new BuildActionFact(player.getId());
        fact.setWood(resourceCount(player, Resource.WOOD));
        fact.setWool(resourceCount(player, Resource.WOOL));
        fact.setGrain(resourceCount(player, Resource.GRAIN));
        fact.setBrick(resourceCount(player, Resource.BRICK));
        fact.setOre(resourceCount(player, Resource.ORE));
        fact.setConnectedRoadsFromVillage(maxRoadDistanceFromVillage(player.getId()));
        fact.setHasOpenRoadEdge(!legalRoadEdges(player.getId()).isEmpty());
        fact.setHasLegalVillageNode(!legalVillageNodes(player.getId()).isEmpty());
        fact.setHasVillageToUpgrade(!villagesToUpgrade(player.getId()).isEmpty());

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(fact);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return fact;
    }

    private void buildRoad(Player player, Integer edgeId) {
        if (!evaluate(player).isCanBuildRoad()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You cannot build a road right now.");
        }
        if (edgeId == null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "Choose a road location first.");
        }
        Edge edge = legalRoadEdges(player.getId()).stream()
                .filter(candidate -> candidate.getId() == edgeId)
                .findFirst()
                .orElseThrow(() -> new GameActionException(HttpStatus.BAD_REQUEST, "That road location is not legal."));
        spend(player, Map.of(Resource.WOOD, 1, Resource.BRICK, 1));
        edge.setOwner(player);
        edgeService.updateById(edge.getId(), edge);
        scoringService.recordRoadBuilt(player.getId());
    }

    private void buildVillage(Player player, Integer nodeId) {
        if (!evaluate(player).isCanBuildVillage()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You cannot build a village right now.");
        }
        if (nodeId == null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "Choose a village location first.");
        }
        Node node = legalVillageNodes(player.getId()).stream()
                .filter(candidate -> candidate.getId() == nodeId)
                .findFirst()
                .orElseThrow(() -> new GameActionException(HttpStatus.BAD_REQUEST, "That village location is not legal."));
        spend(player, Map.of(Resource.WOOD, 1, Resource.BRICK, 1, Resource.GRAIN, 1, Resource.WOOL, 1));
        node.setOwner(player);
        node.setSettlement(Settlement.VILLAGE);
        nodeService.updateById(node.getId(), node);
    }

    private void buildTown(Player player, Integer nodeId) {
        if (!evaluate(player).isCanBuildTown()) {
            throw new GameActionException(HttpStatus.CONFLICT, "You cannot build a town right now.");
        }
        if (nodeId == null) {
            throw new GameActionException(HttpStatus.BAD_REQUEST, "Choose a village to upgrade first.");
        }
        Node node = villagesToUpgrade(player.getId()).stream()
                .filter(candidate -> candidate.getId() == nodeId)
                .findFirst()
                .orElseThrow(() -> new GameActionException(HttpStatus.BAD_REQUEST, "That village cannot be upgraded."));
        spend(player, Map.of(Resource.GRAIN, 2, Resource.ORE, 3));
        node.setSettlement(Settlement.TOWN);
        nodeService.updateById(node.getId(), node);
    }

    private List<Edge> legalRoadEdges(int playerId) {
        return edgeService.getAll().stream()
                .filter(edge -> edge.getOwner() == null)
                .filter(edge -> touchesPlayerNetwork(edge, playerId))
                .toList();
    }

    private boolean touchesPlayerNetwork(Edge edge, int playerId) {
        return isOwnedNode(edge.getNode1(), playerId)
                || isOwnedNode(edge.getNode2(), playerId)
                || hasOwnedIncidentRoad(edge.getNode1().getId(), playerId)
                || hasOwnedIncidentRoad(edge.getNode2().getId(), playerId);
    }

    private boolean hasOwnedIncidentRoad(int nodeId, int playerId) {
        return edgeService.getAll().stream()
                .anyMatch(edge -> edge.getOwner() != null
                        && edge.getOwner().getId() == playerId
                        && touches(edge, nodeId));
    }

    private List<Node> legalVillageNodes(int playerId) {
        List<Edge> edges = edgeService.getAll();
        return nodeService.getAll().stream()
                .filter(node -> node.getSettlement() == null)
                .filter(node -> isFarEnoughFromSettlements(node, edges))
                .filter(node -> roadDistanceFromOwnedVillage(node.getId(), playerId) >= 2)
                .toList();
    }

    private boolean isFarEnoughFromSettlements(Node node, List<Edge> edges) {
        Set<Integer> neighbours = new HashSet<>();
        for (Edge edge : edges) {
            if (edge.getNode1().getId() == node.getId()) {
                neighbours.add(edge.getNode2().getId());
            } else if (edge.getNode2().getId() == node.getId()) {
                neighbours.add(edge.getNode1().getId());
            }
        }
        for (Node other : nodeService.getAll()) {
            if (neighbours.contains(other.getId()) && other.getSettlement() != null) {
                return false;
            }
        }
        return true;
    }

    private List<Node> villagesToUpgrade(int playerId) {
        return nodeService.getAll().stream()
                .filter(node -> isOwnedNode(node, playerId))
                .filter(node -> node.getSettlement() == Settlement.VILLAGE)
                .toList();
    }

    private int maxRoadDistanceFromVillage(int playerId) {
        int max = 0;
        for (Node node : nodeService.getAll()) {
            if (isOwnedNode(node, playerId) && node.getSettlement() != null) {
                max = Math.max(max, longestOwnedRoadFrom(node.getId(), playerId, new HashSet<>()));
            }
        }
        return max;
    }

    private int roadDistanceFromOwnedVillage(int targetNodeId, int playerId) {
        int max = 0;
        for (Node node : nodeService.getAll()) {
            if (isOwnedNode(node, playerId) && node.getSettlement() != null) {
                max = Math.max(max, distanceToTarget(node.getId(), targetNodeId, playerId, new HashSet<>()));
            }
        }
        return max;
    }

    private int distanceToTarget(int nodeId, int targetNodeId, int playerId, Set<Integer> usedEdges) {
        if (nodeId == targetNodeId) return 0;
        int best = -1;
        for (Edge edge : ownedAdjacentEdges(nodeId, playerId)) {
            if (!usedEdges.add(edge.getId())) continue;
            int nextNodeId = otherNode(edge, nodeId);
            int distance = distanceToTarget(nextNodeId, targetNodeId, playerId, usedEdges);
            if (distance >= 0) {
                best = Math.max(best, 1 + distance);
            }
            usedEdges.remove(edge.getId());
        }
        return best;
    }

    private int longestOwnedRoadFrom(int nodeId, int playerId, Set<Integer> usedEdges) {
        int longest = 0;
        for (Edge edge : ownedAdjacentEdges(nodeId, playerId)) {
            if (!usedEdges.add(edge.getId())) continue;
            longest = Math.max(longest, 1 + longestOwnedRoadFrom(otherNode(edge, nodeId), playerId, usedEdges));
            usedEdges.remove(edge.getId());
        }
        return longest;
    }

    private List<Edge> ownedAdjacentEdges(int nodeId, int playerId) {
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : edgeService.getAll()) {
            if (edge.getOwner() != null && edge.getOwner().getId() == playerId && touches(edge, nodeId)) {
                edges.add(edge);
            }
        }
        return edges;
    }

    private int otherNode(Edge edge, int nodeId) {
        return edge.getNode1().getId() == nodeId ? edge.getNode2().getId() : edge.getNode1().getId();
    }

    private boolean touches(Edge edge, int nodeId) {
        return edge.getNode1().getId() == nodeId || edge.getNode2().getId() == nodeId;
    }

    private boolean isOwnedNode(Node node, int playerId) {
        return node.getOwner() != null && node.getOwner().getId() == playerId && node.getSettlement() != null;
    }

    private void spend(Player player, Map<Resource, Integer> cost) {
        for (Map.Entry<Resource, Integer> entry : cost.entrySet()) {
            player.removeResource(entry.getKey(), entry.getValue());
        }
        playerService.create(player);
    }

    private int resourceCount(Player player, Resource resource) {
        if (player.getResources() == null) {
            return 0;
        }
        return player.getResources().getOrDefault(resource, 0);
    }
}
