package com.ftn.sbnz.service.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ftn.sbnz.kjar.NodeDistance;
import com.ftn.sbnz.kjar.RankingRequest;
import com.ftn.sbnz.kjar.SettlementBuilding;
import com.ftn.sbnz.model.Advice;
import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.SynergyPair;
import com.ftn.sbnz.service.dto.AdviceDto;

@Service
public class PlacementAdviceService {

    private final KieContainer kieContainer;
    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;

    public PlacementAdviceService(KieContainer kieContainer, NodeService nodeService,
                                  EdgeService edgeService, PlayerService playerService) {
        this.kieContainer = kieContainer;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
    }

    @Transactional(readOnly = true)
    public List<AdviceDto> openingAdvice(int mePlayerId) {
        List<Node> nodes = nodeService.getAll();
        List<Edge> edges = edgeService.getAll();
        Map<Integer, Node> nodeById = new HashMap<>();
        for (Node node : nodes) {
            node.resetAnalysis();
            nodeById.put(node.getId(), node);
        }

        Player me = playerService.getById(mePlayerId).orElse(null);
        if (me == null) {
            return List.of();
        }

        KieSession session = kieContainer.newKieSession("boardScoreSession");
        try {
            session.insert(me);
            for (Node node : nodes) {
                session.insert(node);
                if (node.getOwner() != null) {
                    boolean opponent = node.getOwner().getId() != mePlayerId;
                    session.insert(new SettlementBuilding(
                            node.getOwner().getId(), mePlayerId, node.getId(),
                            nodesWithinEdges(node.getId(), edges, opponent ? 2 : 1)));
                }
            }
            for (NodeDistance distance : synergyDistances(nodes, edges, mePlayerId)) {
                session.insert(distance);
            }
            session.insert(new RankingRequest());
            session.fireAllRules();

            List<SynergyPair> ranked = session.getObjects(o -> o instanceof SynergyPair).stream()
                    .map(SynergyPair.class::cast)
                    .sorted(Comparator.comparingInt(SynergyPair::getScore).reversed()
                            .thenComparingInt(SynergyPair::getDistance)
                            .thenComparingInt(pair -> pair.getNode1().getId())
                            .thenComparingInt(pair -> pair.getNode2().getId()))
                    .limit(2)
                    .toList();

            List<AdviceDto> result = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                SynergyPair pair = ranked.get(i);
                Node target = nodeById.get(pair.getNode1().getId());
                if (target == null || pair.getNode2() == null) {
                    continue;
                }
                Advice advice = new Advice();
                advice.setPlayer(me);
                advice.setTargetNode(target);
                advice.setLongestRoad(pair);
                advice.setSuccess(pair.getScore());
                advice.setDescription(description(pair, i + 1));
                result.add(new AdviceDto(advice, i + 1, pair.getScore(),
                        pairTags(pair), routeNodeIds(pair), checkpointNodeIds(pair)));
            }
            return result;
        } finally {
            session.dispose();
        }
    }

    private String description(SynergyPair pair, int rank) {
        String position = rank == 1 ? "Best" : "Second best";
        String checkpoints = pair.getCheckPoints().isEmpty()
                ? " No settlement checkpoints on this short route."
                : " Good checkpoints: " + pair.getCheckPoints().stream()
                        .map(node -> String.valueOf(node.getId()))
                        .toList() + ".";
        return position + " connection plan: link nodes " + pair.getNode1().getId()
                + " and " + pair.getNode2().getId() + " within " + pair.getDistance()
                + " roads. The pair combines both node scores, rewards a free route, and values"
                + " legal future settlements along the way." + checkpoints;
    }

    private List<String> pairTags(SynergyPair pair) {
        Set<String> tags = new HashSet<>(pair.getTags());
        tags.addAll(pair.getNode1().getTags());
        tags.addAll(pair.getNode2().getTags());
        return tags.stream().sorted().toList();
    }

    private List<Integer> routeNodeIds(SynergyPair pair) {
        return pair.getRouteNodes().stream()
                .map(Node::getId)
                .toList();
    }

    private List<Integer> checkpointNodeIds(SynergyPair pair) {
        return pair.getCheckPoints().stream()
                .map(Node::getId)
                .toList();
    }

    // Graph traversal belongs in the service; the rule receives only the event and
    // the node ids made unavailable at the requested road distance.
    private Set<Integer> nodesWithinEdges(int startNodeId, List<Edge> edges, int maxDistance) {
        Map<Integer, Set<Integer>> neighbours = new HashMap<>();
        for (Edge edge : edges) {
            int first = edge.getNode1().getId();
            int second = edge.getNode2().getId();
            neighbours.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
            neighbours.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
        }

        Set<Integer> visited = new HashSet<>();
        visited.add(startNodeId);
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        frontier.add(startNodeId);
        for (int distance = 0; distance < maxDistance; distance++) {
            int levelSize = frontier.size();
            for (int i = 0; i < levelSize; i++) {
                int current = frontier.remove();
                for (int next : neighbours.getOrDefault(current, Set.of())) {
                    if (visited.add(next)) {
                        frontier.add(next);
                    }
                }
            }
        }
        visited.remove(startNodeId);
        return visited;
    }

    private List<NodeDistance> synergyDistances(List<Node> nodes, List<Edge> edges, int mePlayerId) {
        Map<Integer, List<Edge>> edgesByNode = edgesByNode(edges);
        Set<Integer> unavailableForSettlement = unavailableForSettlement(nodes, edges);
        List<NodeDistance> distances = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node first = nodes.get(i);
            if (first.getOwner() != null) {
                continue;
            }
            for (int j = i + 1; j < nodes.size(); j++) {
                Node second = nodes.get(j);
                if (second.getOwner() != null) {
                    continue;
                }
                List<Node> shortestPath = shortestPath(first, second, edgesByNode, false, mePlayerId);
                if (shortestPath.isEmpty() || shortestPath.size() > 5) {
                    continue;
                }
                List<Node> freePath = shortestPath(first, second, edgesByNode, true, mePlayerId);
                boolean hasFreePath = !freePath.isEmpty() && freePath.size() <= 5;
                List<Node> checkpointPath = hasFreePath ? freePath : shortestPath;
                distances.add(new NodeDistance(first, second, shortestPath.size() - 1, hasFreePath,
                        checkpointPath,
                        legalCheckpoints(checkpointPath, unavailableForSettlement, edgesByNode)));
            }
        }
        return distances;
    }

    private Map<Integer, List<Edge>> edgesByNode(List<Edge> edges) {
        Map<Integer, List<Edge>> edgesByNode = new HashMap<>();
        for (Edge edge : edges) {
            edgesByNode.computeIfAbsent(edge.getNode1().getId(), ignored -> new ArrayList<>()).add(edge);
            edgesByNode.computeIfAbsent(edge.getNode2().getId(), ignored -> new ArrayList<>()).add(edge);
        }
        return edgesByNode;
    }

    private Set<Integer> unavailableForSettlement(List<Node> nodes, List<Edge> edges) {
        Set<Integer> unavailable = new HashSet<>();
        for (Node node : nodes) {
            if (node.getOwner() != null) {
                unavailable.add(node.getId());
                unavailable.addAll(nodesWithinEdges(node.getId(), edges, 1));
            }
        }
        return unavailable;
    }

    private List<Node> shortestPath(Node start, Node target, Map<Integer, List<Edge>> edgesByNode,
                                    boolean requireFreeEdges, int mePlayerId) {
        Map<Integer, Node> previous = new LinkedHashMap<>();
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Node> frontier = new ArrayDeque<>();
        frontier.add(start);
        visited.add(start.getId());

        while (!frontier.isEmpty()) {
            Node current = frontier.remove();
            if (current.getId() == target.getId()) {
                return reconstructPath(start, target, previous);
            }
            for (Edge edge : edgesByNode.getOrDefault(current.getId(), List.of())) {
                if (requireFreeEdges && edge.getOwner() != null && edge.getOwner().getId() != mePlayerId) {
                    continue;
                }
                Node next = otherNode(edge, current);
                if (visited.add(next.getId())) {
                    previous.put(next.getId(), current);
                    frontier.add(next);
                }
            }
        }
        return List.of();
    }

    private List<Node> reconstructPath(Node start, Node target, Map<Integer, Node> previous) {
        ArrayDeque<Node> path = new ArrayDeque<>();
        Node current = target;
        path.addFirst(current);
        while (current.getId() != start.getId()) {
            current = previous.get(current.getId());
            if (current == null) {
                return List.of();
            }
            path.addFirst(current);
        }
        return new ArrayList<>(path);
    }

    private Node otherNode(Edge edge, Node node) {
        return edge.getNode1().getId() == node.getId() ? edge.getNode2() : edge.getNode1();
    }

    private List<Node> legalCheckpoints(List<Node> path, Set<Integer> unavailableForSettlement,
                                        Map<Integer, List<Edge>> edgesByNode) {
        if (path.size() < 5) {
            return List.of();
        }
        List<Node> checkpoints = new ArrayList<>();
        Node first = path.get(0);
        Node last = path.get(path.size() - 1);
        for (int i = 1; i < path.size() - 1; i++) {
            Node candidate = path.get(i);
            if (unavailableForSettlement.contains(candidate.getId())
                    || adjacent(candidate, first, edgesByNode)
                    || adjacent(candidate, last, edgesByNode)
                    || checkpoints.stream().anyMatch(existing -> adjacent(existing, candidate, edgesByNode))) {
                continue;
            }
            checkpoints.add(candidate);
        }
        return checkpoints;
    }

    private boolean adjacent(Node first, Node second, Map<Integer, List<Edge>> edgesByNode) {
        return edgesByNode.getOrDefault(first.getId(), List.of()).stream()
                .map(edge -> otherNode(edge, first))
                .filter(Objects::nonNull)
                .anyMatch(node -> node.getId() == second.getId());
    }
}
