package com.ftn.sbnz.service.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import com.ftn.sbnz.model.Edge;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.PlayerScoreFact;
import com.ftn.sbnz.model.Settlement;
import com.ftn.sbnz.service.repository.EdgeRepository;
import com.ftn.sbnz.service.repository.NodeRepository;
import com.ftn.sbnz.service.repository.PlayerRepository;

@Service
public class ScoringService {

    private final KieContainer kieContainer;
    private final EdgeRepository edgeRepository;
    private final NodeRepository nodeRepository;
    private final PlayerRepository playerRepository;

    // player -> (road length -> order in which that length was first reached)
    private final Map<Integer, Map<Integer, Long>> roadAchievementOrder = new HashMap<>();
    private long nextRoadAchievement = 1;

    public ScoringService(KieContainer kieContainer, EdgeRepository edgeRepository,
                          NodeRepository nodeRepository, PlayerRepository playerRepository) {
        this.kieContainer = kieContainer;
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
        this.playerRepository = playerRepository;
    }

    public synchronized void reset() {
        roadAchievementOrder.clear();
        nextRoadAchievement = 1;
    }

    /** Call immediately after a road is persisted so ties have an exact chronology. */
    public synchronized void recordRoadBuilt(int playerId) {
        int length = longestRoad(playerId, edgeRepository.findAll(), nodeRepository.findAll());
        achievementOrder(playerId, length);
    }

    public synchronized List<PlayerScoreFact> calculate(List<Player> players) {
        List<Edge> edges = edgeRepository.findAll();
        List<Node> nodes = nodeRepository.findAll();
        List<PlayerScoreFact> facts = new ArrayList<>();

        for (Player player : players) {
            int villages = 0;
            int towns = 0;
            for (Node node : nodes) {
                if (node.getOwner() == null || node.getOwner().getId() != player.getId()) {
                    continue;
                }
                if (node.getSettlement() == Settlement.VILLAGE) villages++;
                if (node.getSettlement() == Settlement.TOWN) towns++;
            }

            int roadLength = longestRoad(player.getId(), edges, nodes);
            facts.add(new PlayerScoreFact(player.getId(), villages, towns, roadLength,
                    achievementOrder(player.getId(), roadLength)));
        }

        KieSession session = kieContainer.newKieSession();
        try {
            facts.forEach(session::insert);
            session.fireAllRules();
        } finally {
            session.dispose();
        }

        Map<Integer, Player> playerById = new HashMap<>();
        players.forEach(player -> playerById.put(player.getId(), player));
        for (PlayerScoreFact fact : facts) {
            Player player = playerById.get(fact.getPlayerId());
            if (player != null && player.getScore() != fact.getScore()) {
                player.setScore(fact.getScore());
                playerRepository.save(player);
            }
        }
        return facts;
    }

    private long achievementOrder(int playerId, int length) {
        if (length <= 0) return Long.MAX_VALUE;
        return roadAchievementOrder
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(length, ignored -> nextRoadAchievement++);
    }

    /** Longest trail: an owned edge may be used once; an opponent building cuts it. */
    private int longestRoad(int playerId, List<Edge> allEdges, List<Node> nodes) {
        Map<Integer, List<Edge>> adjacent = new HashMap<>();
        for (Edge edge : allEdges) {
            if (edge.getOwner() == null || edge.getOwner().getId() != playerId) continue;
            adjacent.computeIfAbsent(edge.getNode1().getId(), ignored -> new ArrayList<>()).add(edge);
            adjacent.computeIfAbsent(edge.getNode2().getId(), ignored -> new ArrayList<>()).add(edge);
        }
        Map<Integer, Integer> nodeOwner = new HashMap<>();
        for (Node node : nodes) {
            if (node.getOwner() != null && node.getSettlement() != null) {
                nodeOwner.put(node.getId(), node.getOwner().getId());
            }
        }

        int longest = 0;
        for (Integer nodeId : adjacent.keySet()) {
            longest = Math.max(longest,
                    longestFrom(nodeId, playerId, adjacent, nodeOwner, new HashSet<>()));
        }
        return longest;
    }

    private int longestFrom(int nodeId, int playerId, Map<Integer, List<Edge>> adjacent,
                            Map<Integer, Integer> nodeOwner, Set<Integer> usedEdges) {
        Integer owner = nodeOwner.get(nodeId);
        if (!usedEdges.isEmpty() && owner != null && owner != playerId) return 0;

        int longest = 0;
        for (Edge edge : adjacent.getOrDefault(nodeId, List.of())) {
            if (!usedEdges.add(edge.getId())) continue;
            int nextNode = edge.getNode1().getId() == nodeId
                    ? edge.getNode2().getId() : edge.getNode1().getId();
            longest = Math.max(longest,
                    1 + longestFrom(nextNode, playerId, adjacent, nodeOwner, usedEdges));
            usedEdges.remove(edge.getId());
        }
        return longest;
    }
}
