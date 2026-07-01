package com.ftn.sbnz.service.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ftn.sbnz.kjar.BestNode;
import com.ftn.sbnz.kjar.RankingRequest;
import com.ftn.sbnz.kjar.SettlementBuilding;
import com.ftn.sbnz.model.Advice;
import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
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
            for (Node node : nodes) {
                session.insert(node);
                if (node.getOwner() != null) {
                    boolean opponent = node.getOwner().getId() != mePlayerId;
                    session.insert(new SettlementBuilding(
                            node.getOwner().getId(), mePlayerId, node.getId(),
                            nodesWithinEdges(node.getId(), edges, opponent ? 2 : 1)));
                }
            }
            session.insert(new RankingRequest());
            session.fireAllRules();

            List<BestNode> ranked = session.getObjects(o -> o instanceof BestNode).stream()
                    .map(BestNode.class::cast)
                    .sorted(Comparator.comparingInt(BestNode::getRank))
                    .limit(2)
                    .toList();

            List<AdviceDto> result = new ArrayList<>();
            for (BestNode best : ranked) {
                Node target = nodeById.get(best.getNodeId());
                if (target == null) {
                    continue;
                }
                Advice advice = new Advice();
                advice.setPlayer(me);
                advice.setTargetNode(target);
                advice.setSuccess(best.getScore());
                advice.setDescription(description(best, target));
                result.add(new AdviceDto(advice, best.getRank(), best.getScore(),
                        target.getTags().stream().sorted().toList()));
            }
            return result;
        } finally {
            session.dispose();
        }
    }

    private String description(BestNode best, Node node) {
        String position = best.getRank() == 1 ? "Best" : "Second best";
        String bonuses = node.getTags().contains("BigYield")
                ? " Includes a 6/8 BigYield bonus." : "";
        return position + " available opening position: node " + node.getId()
                + " with score " + best.getScore() + "." + bonuses;
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
}
