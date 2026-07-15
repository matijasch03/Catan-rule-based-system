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
import com.ftn.sbnz.kjar.RoadLink;
import com.ftn.sbnz.kjar.SettlementBuilding;
import com.ftn.sbnz.model.Advice;
import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.Resource;
import com.ftn.sbnz.model.SynergyPair;
import com.ftn.sbnz.service.dto.AdviceDto;

@Service
public class PlacementAdviceService {

    private final KieContainer kieContainer;
    private final NodeService nodeService;
    private final EdgeService edgeService;
    private final PlayerService playerService;
    private final SynergyPairService synergyPairService;
    private final AdviceService adviceService;

    public PlacementAdviceService(KieContainer kieContainer, NodeService nodeService,
                                  EdgeService edgeService, PlayerService playerService,
                                  SynergyPairService synergyPairService, AdviceService adviceService) {
        this.kieContainer = kieContainer;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.playerService = playerService;
        this.synergyPairService = synergyPairService;
        this.adviceService = adviceService;
    }

    @Transactional
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

        boolean openingPlacement = ownedSettlements(mePlayerId, nodes).isEmpty();
        if (!openingPlacement) {
            updatePersistedRoutes(mePlayerId);
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

            List<SynergyPair> ranked = openingPlacement
                    ? fallbackOpeningSynergyPairs(nodes, edges, mePlayerId)
                    : actualSettlementSynergyPairs(mePlayerId, nodes, edges).stream()
                            .sorted(pairComparator())
                            .limit(2)
                            .toList();

            if (openingPlacement) {
                return saveAdvice(me, ranked, nodeById, true);
            }
            return saveAdvice(me, ranked, nodeById, false);
        } finally {
            session.dispose();
        }
    }

    @Transactional
    public void updatePersistedRoutes(int playerId) {
        List<Edge> edges = edgeService.getAll();
        for (SynergyPair pair : synergyPairService.getAll()) {
            if (!belongsToPlayerAdvice(pair, playerId)) {
                continue;
            }
            List<Node> route = new ArrayList<>(pair.getRouteNodes());
            boolean changed = trimOwnedSegments(route, edges, playerId);
            if (changed) {
                pair.setRouteNodes(route);
                pair.setDistance(Math.max(0, route.size() - 1));
                synergyPairService.save(pair);
            }
        }
    }

    private List<AdviceDto> saveAdvice(Player me, List<SynergyPair> ranked, Map<Integer, Node> nodeById,
                                       boolean openingPlacement) {
        adviceService.deleteAll();
        synergyPairService.deleteAll();

        List<AdviceDto> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            SynergyPair pair = ranked.get(i);
            Node target = pair.getNode1().getOwner() == null ? pair.getNode1() : pair.getNode2();
            target = nodeById.get(target.getId());
            if (target == null || pair.getNode2() == null) {
                continue;
            }
            SynergyPair savedPair = synergyPairService.save(pair);
            Advice advice = new Advice();
            advice.setPlayer(me);
            advice.setTargetNode(target);
            advice.setLongestRoad(savedPair);
            advice.setSuccess(savedPair.getScore());
            advice.setDescription(description(savedPair, i + 1, openingPlacement));
            Advice savedAdvice = adviceService.create(advice);
            result.add(new AdviceDto(savedAdvice, i + 1, savedPair.getScore(),
                    pairTags(savedPair), routeNodeIds(savedPair), checkpointNodeIds(savedPair)));
        }
        return result;
    }


    private boolean belongsToPlayerAdvice(SynergyPair pair, int playerId) {
        return adviceService.getAll().stream()
                .anyMatch(advice -> advice.getPlayer() != null
                        && advice.getPlayer().getId() == playerId
                        && advice.getLongestRoad() != null
                        && advice.getLongestRoad().getId() == pair.getId());
    }

    private boolean trimOwnedSegments(List<Node> route, List<Edge> edges, int playerId) {
        boolean changed = false;
        while (route.size() > 1 && ownedEdge(route.get(0), route.get(1), edges, playerId)) {
            route.remove(0);
            changed = true;
        }
        while (route.size() > 1 && ownedEdge(route.get(route.size() - 1), route.get(route.size() - 2), edges, playerId)) {
            route.remove(route.size() - 1);
            changed = true;
        }
        return changed;
    }

    private boolean ownedEdge(Node first, Node second, List<Edge> edges, int playerId) {
        return edges.stream().anyMatch(edge -> edge.getOwner() != null
                && edge.getOwner().getId() == playerId
                && ((edge.getNode1().getId() == first.getId() && edge.getNode2().getId() == second.getId())
                || (edge.getNode1().getId() == second.getId() && edge.getNode2().getId() == first.getId())));
    }

    private Comparator<SynergyPair> pairComparator() {
        return Comparator.comparingInt(SynergyPair::getScore).reversed()
                .thenComparingInt(SynergyPair::getDistance)
                .thenComparingInt(pair -> pair.getNode1().getId())
                .thenComparingInt(pair -> pair.getNode2().getId());
    }

    private List<SynergyPair> fallbackOpeningSynergyPairs(List<Node> nodes, List<Edge> edges, int mePlayerId) {
        Map<Integer, List<Edge>> edgesByNode = edgesByNode(edges);
        Set<Integer> unavailableForSettlement = unavailableForSettlement(nodes, edges);
        List<Node> legalNodes = nodes.stream()
                .filter(node -> node.getOwner() == null)
                .filter(node -> !unavailableForSettlement.contains(node.getId()))
                .peek(node -> node.setScore(Math.max(1, Math.max(node.getScore(), fallbackNodeScore(node)))))
                .sorted(Comparator.comparingInt(Node::getScore).reversed()
                        .thenComparingInt(Node::getId))
                .toList();

        List<SynergyPair> pairs = new ArrayList<>();
        List<SynergyPair> preferred = new ArrayList<>();
        if (legalNodes.size() == 1) {
            preferred.add(singleNodeOpeningPair(legalNodes.get(0)));
        } else if (legalNodes.size() >= 2) {
            SynergyPair topPair = bestOpeningPairFor(legalNodes.get(0), legalNodes, edgesByNode,
                    unavailableForSettlement, mePlayerId);
            if (topPair != null) {
                preferred.add(topPair);
            }
        }
        if (!preferred.isEmpty()) {
            return preferred;
        }

        for (int i = 0; i < legalNodes.size(); i++) {
            Node first = legalNodes.get(i);
            for (int j = i + 1; j < legalNodes.size(); j++) {
                Node second = legalNodes.get(j);
                SynergyPair pair = openingPairBetween(first, second, edgesByNode, unavailableForSettlement, mePlayerId);
                if (pair != null) {
                    pairs.add(pair);
                }
            }
        }

        List<SynergyPair> ranked = new ArrayList<>(preferred);
        pairs.stream()
                .sorted(pairComparator())
                .filter(pair -> ranked.stream().noneMatch(existing -> sameEndpoints(existing, pair)))
                .limit(Math.max(0, 2 - ranked.size()))
                .forEach(ranked::add);
        return ranked;
    }

    private SynergyPair bestOpeningPairFor(Node anchor, List<Node> legalNodes, Map<Integer, List<Edge>> edgesByNode,
                                           Set<Integer> unavailableForSettlement, int mePlayerId) {
        return legalNodes.stream()
                .filter(candidate -> candidate.getId() != anchor.getId())
                .map(candidate -> openingPairBetween(anchor, candidate, edgesByNode, unavailableForSettlement, mePlayerId))
                .filter(Objects::nonNull)
                .sorted(openingSecondNodeComparator(anchor))
                .findFirst()
                .orElse(null);
    }

    private Comparator<SynergyPair> openingSecondNodeComparator(Node anchor) {
        return Comparator.comparingInt((SynergyPair pair) -> missingResourceGain(anchor, pair.getNode2()))
                .reversed()
                .thenComparing(Comparator.comparingInt((SynergyPair pair) -> pair.getNode2().getScore())
                        .reversed())
                .thenComparing(Comparator.comparingInt(SynergyPair::getScore).reversed())
                .thenComparingInt(SynergyPair::getDistance)
                .thenComparingInt(pair -> pair.getNode2().getId());
    }

    private int missingResourceGain(Node first, Node second) {
        Set<Resource> firstResources = resourcesFor(first);
        Set<Resource> secondResources = resourcesFor(second);
        secondResources.removeAll(firstResources);
        return secondResources.size();
    }

    private SynergyPair openingPairBetween(Node first, Node second, Map<Integer, List<Edge>> edgesByNode,
                                           Set<Integer> unavailableForSettlement, int mePlayerId) {
        if (adjacent(first, second, edgesByNode)) {
            return null;
        }
        List<Node> route = shortestPath(first, second, edgesByNode, true, mePlayerId);
        if (route.isEmpty()) {
            route = shortestPath(first, second, edgesByNode, false, mePlayerId);
        }
        if (route.isEmpty() || route.size() > 7) {
            return null;
        }
        List<Node> checkpoints = legalCheckpoints(route, unavailableForSettlement, edgesByNode);
        int checkpointScore = checkpoints.stream()
                .mapToInt(node -> Math.max(node.getScore(), fallbackNodeScore(node)))
                .sum();
        SynergyPair pair = new SynergyPair(0, first, second, route.size() - 1,
                first.getScore() + second.getScore() + checkpointScore + 200);
        pair.setRouteNodes(route);
        pair.setCheckPoints(checkpoints);
        pair.addTag("OpeningPair");
        pair.addTag("LongestRoadPlan");
        pair.addTag("ResourcePlan");
        pair.setScore(pair.getScore() + openingResourceBonus(pair));
        return pair;
    }

    private SynergyPair singleNodeOpeningPair(Node node) {
        SynergyPair pair = new SynergyPair(0, node, node, 0, node.getScore());
        pair.setRouteNodes(List.of(node));
        pair.addTag("OpeningPair");
        pair.addTag("BestNode");
        pair.addTag("LongestRoadPlan");
        return pair;
    }

    private boolean sameEndpoints(SynergyPair first, SynergyPair second) {
        int firstA = first.getNode1().getId();
        int firstB = first.getNode2().getId();
        int secondA = second.getNode1().getId();
        int secondB = second.getNode2().getId();
        return (firstA == secondA && firstB == secondB) || (firstA == secondB && firstB == secondA);
    }

    private int fallbackNodeScore(Node node) {
        int score = 0;
        Set<Resource> resources = new HashSet<>();
        for (Hexagon hex : node.getAdjacentHexagons()) {
            Resource resource = hex.getField();
            if (resource == null || resource == Resource.DESERT) {
                continue;
            }
            resources.add(resource);
            score += diceWeight(hex.getDots());
            if (resource == Resource.WOOD || resource == Resource.BRICK) {
                score += 5;
            } else if (resource == Resource.GRAIN) {
                score += 3;
            } else if (resource == Resource.WOOL || resource == Resource.ORE) {
                score += 2;
            }
        }
        score += resources.size() * 4;
        if (resources.contains(Resource.WOOD) && resources.contains(Resource.BRICK)) {
            score += 12;
        }
        return score;
    }

    private int diceWeight(int dots) {
        return switch (dots) {
            case 2, 12 -> 1;
            case 3, 11 -> 2;
            case 4, 10 -> 3;
            case 5, 9 -> 4;
            case 6, 8 -> 5;
            default -> 0;
        };
    }

    private List<SynergyPair> actualSettlementSynergyPairs(int playerId, List<Node> nodes, List<Edge> edges) {
        List<SynergyPair> pairs = new ArrayList<>();
        pairs.addAll(ownedSettlementConnectionPairs(playerId, nodes, edges));
        pairs.addAll(ownedNodeSynergyPairs(playerId, nodes, edges));
        return pairs;
    }

    private List<SynergyPair> ownedSettlementConnectionPairs(int playerId, List<Node> nodes, List<Edge> edges) {
        Map<Integer, List<Edge>> edgesByNode = edgesByNode(edges);
        Set<Integer> unavailableForSettlement = unavailableForSettlement(nodes, edges);
        List<Node> starts = ownedSettlements(playerId, nodes);
        List<SynergyPair> pairs = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            Node first = starts.get(i);
            for (int j = i + 1; j < starts.size(); j++) {
                Node second = starts.get(j);
                List<Node> route = shortestPath(first, second, edgesByNode, true, playerId);
                if (route.isEmpty() || route.size() > 7) {
                    continue;
                }
                List<Node> checkpoints = legalCheckpoints(route, unavailableForSettlement, edgesByNode);
                int checkpointScore = checkpoints.stream()
                        .mapToInt(node -> Math.max(node.getScore(), fallbackNodeScore(node)))
                        .sum();
                SynergyPair pair = new SynergyPair(0, first, second, route.size() - 1,
                        Math.max(first.getScore(), fallbackNodeScore(first))
                                + Math.max(second.getScore(), fallbackNodeScore(second))
                                + checkpointScore + 220);
                pair.setRouteNodes(route);
                pair.setCheckPoints(checkpoints);
                pair.addTag("LongestRoadPlan");
                pair.addTag("ActualSettlements");
                pairs.add(pair);
            }
        }
        return pairs;
    }

    private List<SynergyPair> ownedNodeSynergyPairs(int playerId, List<Node> nodes, List<Edge> edges) {
        Map<Integer, List<Edge>> edgesByNode = edgesByNode(edges);
        Set<Integer> unavailableForSettlement = unavailableForSettlement(nodes, edges);
        List<Node> starts = ownedSettlements(playerId, nodes);
        List<SynergyPair> pairs = new ArrayList<>();
        for (Node start : starts) {
            for (Node target : nodes) {
                if (target.getOwner() != null || !target.isAvailable() || target.getScore() <= 0) {
                    continue;
                }
                List<Node> route = shortestPath(start, target, edgesByNode, true, playerId);
                if (route.isEmpty() || route.size() > 7) {
                    continue;
                }
                List<Node> checkpoints = legalCheckpoints(route, unavailableForSettlement, edgesByNode);
                int checkpointScore = checkpoints.stream()
                        .mapToInt(node -> Math.max(node.getScore(), fallbackNodeScore(node)))
                        .sum();
                SynergyPair pair = new SynergyPair(0, start, target, route.size() - 1,
                        Math.max(start.getScore(), fallbackNodeScore(start))
                                + target.getScore() + checkpointScore + 200);
                pair.setRouteNodes(route);
                pair.setCheckPoints(checkpoints);
                pair.addTag("LongestRoadPlan");
                pairs.add(pair);
            }
        }
        return pairs;
    }

    private List<Node> ownedSettlements(int playerId, List<Node> nodes) {
        return nodes.stream()
                .filter(node -> node.getOwner() != null
                        && node.getOwner().getId() == playerId
                        && node.getSettlement() != null)
                .toList();
    }

    private String description(SynergyPair pair, int rank, boolean openingPlacement) {
        String position = rank == 1 ? "Best" : "Second best";
        String checkpoints = pair.getCheckPoints().isEmpty()
                ? " No checkpoints."
                : " Checkpoints: " + pair.getCheckPoints().stream()
                        .map(node -> String.valueOf(node.getId()))
                        .toList() + ".";
        if (openingPlacement) {
            return position + " opening: red " + pair.getNode1().getId()
                    + " first, blue " + pair.getNode2().getId()
                    + " second. Put second village on the more diverse node; it gives starting resources. Cover "
                    + resourceNames(resourcesFor(pair.getNode1(), pair.getNode2()))
                    + ". Wood/Brick help roads."
                    + checkpoints;
        }
        return position + " route: link nodes " + pair.getNode1().getId()
                + " and " + pair.getNode2().getId() + " within " + pair.getDistance()
                + " roads. Score includes both nodes, free path bonus and future settlements."
                + checkpoints;
    }

    private int openingResourceBonus(SynergyPair pair) {
        Set<Resource> resources = resourcesFor(pair.getNode1(), pair.getNode2());
        int bonus = resources.size() * 18;
        if (resources.contains(Resource.WOOD)) {
            bonus += 35;
        }
        if (resources.contains(Resource.BRICK)) {
            bonus += 35;
        }
        if (resources.contains(Resource.GRAIN)) {
            bonus += 14;
        }
        if (resources.contains(Resource.WOOL)) {
            bonus += 10;
        }
        if (resources.contains(Resource.ORE)) {
            bonus += 8;
        }
        if (resources.size() == 5) {
            bonus += 30;
        }
        return bonus;
    }

    private Set<Resource> resourcesFor(Node first, Node second) {
        Set<Resource> resources = new HashSet<>();
        addResources(resources, first);
        addResources(resources, second);
        resources.remove(Resource.DESERT);
        return resources;
    }

    private Set<Resource> resourcesFor(Node node) {
        Set<Resource> resources = new HashSet<>();
        addResources(resources, node);
        resources.remove(Resource.DESERT);
        return resources;
    }

    private void addResources(Set<Resource> resources, Node node) {
        if (node == null || node.getAdjacentHexagons() == null) {
            return;
        }
        for (Hexagon hex : node.getAdjacentHexagons()) {
            if (hex.getField() != null && hex.getField() != Resource.DESERT) {
                resources.add(hex.getField());
            }
        }
    }

    private String resourceNames(Set<Resource> resources) {
        if (resources.isEmpty()) {
            return "no production resources";
        }
        return resources.stream()
                .sorted(Comparator.comparing(Resource::getDisplayName))
                .map(Resource::getDisplayName)
                .toList()
                .toString();
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
                if (shortestPath.isEmpty() || shortestPath.size() > 7) {
                    continue;
                }
                List<Node> freePath = shortestPath(first, second, edgesByNode, true, mePlayerId);
                boolean hasFreePath = !freePath.isEmpty() && freePath.size() <= 7;
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
        if (!canConnectBackward(start, target, edgesByNode, requireFreeEdges, mePlayerId)) {
            return List.of();
        }

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
                if (edge.getOwner() != null && edge.getOwner().getId() != mePlayerId) {
                    continue;
                }
                Node next = otherNode(edge, current);
                if (blocksRoute(next, target, mePlayerId)) {
                    continue;
                }
                if (visited.add(next.getId())) {
                    previous.put(next.getId(), current);
                    frontier.add(next);
                }
            }
        }
        return List.of();
    }

    private boolean canConnectBackward(Node start, Node target, Map<Integer, List<Edge>> edgesByNode,
                                       boolean requireFreeEdges, int mePlayerId) {
        if (start.getId() == target.getId()) {
            return true;
        }
        Map<Integer, Integer> distanceToTarget = roadDistancesToTarget(target, edgesByNode,
                requireFreeEdges, mePlayerId);
        if (!distanceToTarget.containsKey(start.getId())) {
            return false;
        }

        KieSession session = kieContainer.newKieSession("boardScoreSession");
        try {
            for (Map.Entry<Integer, Integer> entry : distanceToTarget.entrySet()) {
                int fromNodeId = entry.getKey();
                int fromDistance = entry.getValue();
                for (Edge edge : edgesByNode.getOrDefault(fromNodeId, List.of())) {
                    if (!roadEdgeAllowed(edge, requireFreeEdges, mePlayerId)) {
                        continue;
                    }
                    Node next = otherNode(edge, fromNodeId);
                    if (blocksRoute(next, target, mePlayerId)) {
                        continue;
                    }
                    Integer nextDistance = distanceToTarget.get(next.getId());
                    if (nextDistance != null && nextDistance < fromDistance) {
                        session.insert(new RoadLink(fromNodeId, next.getId(), target.getId()));
                    }
                }
            }
            return session.getQueryResults("canConnect", start.getId(), target.getId()).size() > 0;
        } finally {
            session.dispose();
        }
    }

    private Map<Integer, Integer> roadDistancesToTarget(Node target, Map<Integer, List<Edge>> edgesByNode,
                                                        boolean requireFreeEdges, int mePlayerId) {
        Map<Integer, Integer> distance = new HashMap<>();
        ArrayDeque<Node> frontier = new ArrayDeque<>();
        distance.put(target.getId(), 0);
        frontier.add(target);

        while (!frontier.isEmpty()) {
            Node current = frontier.remove();
            int nextDistance = distance.get(current.getId()) + 1;
            for (Edge edge : edgesByNode.getOrDefault(current.getId(), List.of())) {
                if (!roadEdgeAllowed(edge, requireFreeEdges, mePlayerId)) {
                    continue;
                }
                Node next = otherNode(edge, current);
                if (blocksRoute(next, target, mePlayerId) || distance.containsKey(next.getId())) {
                    continue;
                }
                distance.put(next.getId(), nextDistance);
                frontier.add(next);
            }
        }
        return distance;
    }

    private boolean roadEdgeAllowed(Edge edge, boolean requireFreeEdges, int mePlayerId) {
        if (edge.getOwner() == null) {
            return true;
        }
        return edge.getOwner().getId() == mePlayerId;
    }

    private boolean blocksRoute(Node node, Node target, int mePlayerId) {
        if (node == null || node.getId() == target.getId()
                || node.getOwner() == null || node.getSettlement() == null) {
            return false;
        }
        return node.getOwner().getId() != mePlayerId;
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

    private Node otherNode(Edge edge, int nodeId) {
        return edge.getNode1().getId() == nodeId ? edge.getNode2() : edge.getNode1();
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
