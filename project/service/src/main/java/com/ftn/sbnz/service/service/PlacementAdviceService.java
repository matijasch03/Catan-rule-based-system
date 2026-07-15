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
        List<SynergyPair> persistedRoutes = openingPlacement
                ? List.of()
                : persistedAdviceRoutes(mePlayerId);
        if (!openingPlacement) {
            updatePersistedRoutes(mePlayerId);
            persistedRoutes = persistedAdviceRoutes(mePlayerId);
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
                    : stableRouteAdvice(mePlayerId, persistedRoutes,
                            actualSettlementSynergyPairs(mePlayerId, nodes, edges), nodes, edges);

            if (openingPlacement) {
                return saveAdvice(me, ranked, nodeById, true, true);
            }
            return saveAdvice(me, ranked, nodeById, false, false);
        } finally {
            session.dispose();
        }
    }

    @Transactional
    public void updatePersistedRoutes(int playerId) {
        for (SynergyPair pair : synergyPairService.getAll()) {
            if (!belongsToPlayerAdvice(pair, playerId)) {
                continue;
            }
            List<Node> route = pair.getRouteNodes();
            if (route == null || route.isEmpty()) {
                continue;
            }
            int distance = Math.max(0, route.size() - 1);
            if (pair.getDistance() != distance || !pair.getTags().contains("StableRoute")) {
                pair.setDistance(distance);
                pair.addTag("StableRoute");
                pair.addTag("LongestRoadPlan");
                synergyPairService.save(pair);
            }
        }
    }

    private List<AdviceDto> saveAdvice(Player me, List<SynergyPair> ranked, Map<Integer, Node> nodeById,
                                       boolean openingPlacement, boolean resetRoutes) {
        adviceService.deleteAll();
        if (resetRoutes) {
            synergyPairService.deleteAll();
        }

        List<AdviceDto> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            SynergyPair pair = ranked.get(i);
            Node target = adviceTarget(pair);
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

    private Node adviceTarget(SynergyPair pair) {
        if (pair.getNode1().getOwner() == null) {
            return pair.getNode1();
        }
        if (pair.getNode2().getOwner() == null) {
            return pair.getNode2();
        }
        List<Node> route = pair.getRouteNodes();
        if (!route.isEmpty()) {
            return route.get(route.size() / 2);
        }
        return pair.getNode2();
    }

    private List<SynergyPair> persistedAdviceRoutes(int playerId) {
        return adviceService.getAll().stream()
                .filter(advice -> advice.getPlayer() != null && advice.getPlayer().getId() == playerId)
                .map(Advice::getLongestRoad)
                .filter(Objects::nonNull)
                .filter(pair -> pair.getRouteNodes().size() >= 2)
                .toList();
    }


    private boolean belongsToPlayerAdvice(SynergyPair pair, int playerId) {
        return adviceService.getAll().stream()
                .anyMatch(advice -> advice.getPlayer() != null
                        && advice.getPlayer().getId() == playerId
                        && advice.getLongestRoad() != null
                        && advice.getLongestRoad().getId() == pair.getId());
    }

    private Comparator<SynergyPair> pairComparator() {
        return Comparator.comparingInt(SynergyPair::getScore).reversed()
                .thenComparingInt(SynergyPair::getDistance)
                .thenComparingInt(pair -> pair.getNode1().getId())
                .thenComparingInt(pair -> pair.getNode2().getId());
    }

    private List<SynergyPair> stableRouteAdvice(int playerId, List<SynergyPair> persistedRoutes,
                                                List<SynergyPair> candidates, List<Node> nodes, List<Edge> edges) {
        List<SynergyPair> rankedCandidates = candidates.stream()
                .sorted(routeStabilityComparator(playerId, edges))
                .toList();
        Set<Integer> ownedSettlements = ownedSettlements(playerId, nodes).stream()
                .map(Node::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean newSettlementOutsidePlan = !persistedRoutes.isEmpty()
                && ownedSettlements.stream().anyMatch(nodeId -> persistedRoutes.stream()
                        .noneMatch(route -> routeContains(route, nodeId)));

        List<SynergyPair> stable = new ArrayList<>();
        for (SynergyPair persisted : persistedRoutes) {
            if (stable.size() >= 2 || !routeStillUsable(persisted, playerId, edges)) {
                continue;
            }
            SynergyPair bestMatchingCandidate = rankedCandidates.stream()
                    .filter(candidate -> sameEndpoints(candidate, persisted))
                    .findFirst()
                    .orElse(null);
            SynergyPair refreshed = bestMatchingCandidate == null
                    ? refreshPersistedRouteScore(persisted, playerId, edges)
                    : copyPersistentIdentity(persisted, bestMatchingCandidate);
            stable.add(refreshed);
        }

        if (!newSettlementOutsidePlan && stable.size() >= 2) {
            return stable;
        }

        int weakestStableScore = stable.stream()
                .mapToInt(pair -> stablePlanScore(pair, playerId, edges))
                .min()
                .orElse(Integer.MIN_VALUE);
        for (SynergyPair candidate : rankedCandidates) {
            if (stable.size() >= 2) {
                break;
            }
            if (stable.stream().anyMatch(existing -> sameEndpoints(existing, candidate))) {
                continue;
            }
            stable.add(candidate);
        }
        if (newSettlementOutsidePlan || stable.size() < 2) {
            for (SynergyPair candidate : rankedCandidates) {
                if (stable.stream().anyMatch(existing -> sameEndpoints(existing, candidate))) {
                    continue;
                }
                int candidateScore = stablePlanScore(candidate, playerId, edges);
                if (stable.size() < 2 || candidateScore >= weakestStableScore + 35) {
                    if (stable.size() >= 2) {
                        stable.sort(routeStabilityComparator(playerId, edges));
                        stable.remove(stable.size() - 1);
                    }
                    stable.add(candidate);
                    weakestStableScore = stable.stream()
                            .mapToInt(pair -> stablePlanScore(pair, playerId, edges))
                            .min()
                            .orElse(candidateScore);
                }
                if (stable.size() >= 2 && !newSettlementOutsidePlan) {
                    break;
                }
            }
        }
        return stable.stream()
                .sorted(routeStabilityComparator(playerId, edges))
                .limit(2)
                .toList();
    }

    private Comparator<SynergyPair> routeStabilityComparator(int playerId, List<Edge> edges) {
        return Comparator.comparingInt((SynergyPair pair) -> stablePlanScore(pair, playerId, edges)).reversed()
                .thenComparingInt(SynergyPair::getDistance)
                .thenComparingInt(pair -> pair.getNode1().getId())
                .thenComparingInt(pair -> pair.getNode2().getId());
    }

    private int stablePlanScore(SynergyPair pair, int playerId, List<Edge> edges) {
        return pair.getScore()
                + ownedSettlementCount(pair, playerId) * 160
                + ownedRoadCount(pair, edges, playerId) * 120
                - missingRoadCount(pair, edges, playerId) * 10;
    }

    private SynergyPair refreshPersistedRouteScore(SynergyPair pair, int playerId, List<Edge> edges) {
        pair.setDistance(Math.max(0, pair.getRouteNodes().size() - 1));
        pair.addTag("StableRoute");
        pair.addTag("LongestRoadPlan");
        return pair;
    }

    private SynergyPair copyPersistentIdentity(SynergyPair persisted, SynergyPair candidate) {
        persisted.setNode1(candidate.getNode1());
        persisted.setNode2(candidate.getNode2());
        persisted.setDistance(candidate.getDistance());
        persisted.setScore(candidate.getScore());
        persisted.setRouteNodes(candidate.getRouteNodes());
        persisted.setCheckPoints(candidate.getCheckPoints());
        persisted.addTag("StableRoute");
        persisted.addTag("LongestRoadPlan");
        return persisted;
    }

    private boolean routeStillUsable(SynergyPair pair, int playerId, List<Edge> edges) {
        List<Node> route = pair.getRouteNodes();
        if (route.size() < 2) {
            return false;
        }
        for (int i = 0; i + 1 < route.size(); i++) {
            Edge edge = edgeBetween(route.get(i), route.get(i + 1), edges);
            if (edge == null || (edge.getOwner() != null && edge.getOwner().getId() != playerId)) {
                return false;
            }
        }
        return ownedSettlementCount(pair, playerId) >= 1;
    }

    private boolean routeContains(SynergyPair pair, int nodeId) {
        return pair.getRouteNodes().stream().anyMatch(node -> node.getId() == nodeId);
    }

    private int ownedSettlementCount(SynergyPair pair, int playerId) {
        return (int) pair.getRouteNodes().stream()
                .filter(node -> node.getOwner() != null
                        && node.getOwner().getId() == playerId
                        && node.getSettlement() != null)
                .count();
    }

    private int ownedRoadCount(SynergyPair pair, List<Edge> edges, int playerId) {
        List<Node> route = pair.getRouteNodes();
        int count = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
            Edge edge = edgeBetween(route.get(i), route.get(i + 1), edges);
            if (edge != null && edge.getOwner() != null && edge.getOwner().getId() == playerId) {
                count++;
            }
        }
        return count;
    }

    private int missingRoadCount(SynergyPair pair, List<Edge> edges, int playerId) {
        List<Node> route = pair.getRouteNodes();
        int count = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
            Edge edge = edgeBetween(route.get(i), route.get(i + 1), edges);
            if (edge != null && (edge.getOwner() == null || edge.getOwner().getId() != playerId)) {
                count++;
            }
        }
        return count;
    }

    private Edge edgeBetween(Node first, Node second, List<Edge> edges) {
        if (first == null || second == null) {
            return null;
        }
        return edges.stream()
                .filter(edge -> (edge.getNode1().getId() == first.getId()
                        && edge.getNode2().getId() == second.getId())
                        || (edge.getNode1().getId() == second.getId()
                        && edge.getNode2().getId() == first.getId()))
                .findFirst()
                .orElse(null);
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
