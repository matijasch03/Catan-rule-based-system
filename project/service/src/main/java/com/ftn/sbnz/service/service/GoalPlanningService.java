package com.ftn.sbnz.service.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import com.ftn.sbnz.kjar.GoalAdvice;
import com.ftn.sbnz.kjar.VictoryGoal;
import com.ftn.sbnz.model.BuildActionFact;
import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Player;
import com.ftn.sbnz.model.PlayerScoreFact;
import com.ftn.sbnz.model.Resource;

@Service
public class GoalPlanningService {

    private final KieContainer kieContainer;
    private final BuildActionService buildActionService;
    private final NodeService nodeService;

    public GoalPlanningService(KieContainer kieContainer, BuildActionService buildActionService,
                               NodeService nodeService) {
        this.kieContainer = kieContainer;
        this.buildActionService = buildActionService;
        this.nodeService = nodeService;
    }

    public List<GoalAdvice> advice(Player player, PlayerScoreFact scoreFact,
                                   boolean tradeAttempted, boolean tradeRefused) {
        return advice(player, scoreFact, tradeAttempted, tradeRefused, List.of(), 0, 0);
    }

    public List<GoalAdvice> advice(Player player, PlayerScoreFact scoreFact,
                                   boolean tradeAttempted, boolean tradeRefused,
                                   List<Object> cepFacts, int roadsMissingForPlannedRoute,
                                   int opponentRoadCards) {
        if (player == null || scoreFact == null || scoreFact.isWinner()) {
            return List.of();
        }

        BuildActionFact build = buildActionService.evaluate(player);
        VictoryGoal goal = goal(player, scoreFact, build);
        goal.setTradeAttempted(tradeAttempted);
        goal.setTradeRefused(tradeRefused);
        goal.setRoadsMissingForPlannedRoute(roadsMissingForPlannedRoute);
        goal.setOpponentRoadCards(opponentRoadCards);

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(build);
            session.insert(goal);
            for (Object fact : cepFacts) {
                session.insert(fact);
            }
            session.fireAllRules();
            return session.getObjects(object -> object instanceof GoalAdvice).stream()
                    .map(GoalAdvice.class::cast)
                    .sorted(Comparator.comparingInt(GoalAdvice::getRank)
                            .thenComparing(GoalAdvice::getTitle))
                    .limit(10)
                    .toList();
        } finally {
            session.dispose();
        }
    }

    private VictoryGoal goal(Player player, PlayerScoreFact scoreFact, BuildActionFact build) {
        VictoryGoal goal = new VictoryGoal(player.getId(), scoreFact.getScore());
        goal.setLongestRoadLength(scoreFact.getLongestRoadLength());
        goal.setLongestRoadAwarded(scoreFact.isLongestRoadAwarded());
        goal.setHasVillageToUpgrade(build.isHasVillageToUpgrade());
        goal.setHasLegalVillageNode(build.isHasLegalVillageNode());
        goal.setHasOpenRoadEdge(build.isHasOpenRoadEdge());
        goal.setCanBuildRoad(build.isCanBuildRoad());
        goal.setCanBuildVillage(build.isCanBuildVillage());
        goal.setCanBuildTown(build.isCanBuildTown());
        goal.setWood(build.getWood());
        goal.setWool(build.getWool());
        goal.setGrain(build.getGrain());
        goal.setBrick(build.getBrick());
        goal.setOre(build.getOre());
        goal.setTotalResources(build.getWood() + build.getWool() + build.getGrain()
                + build.getBrick() + build.getOre());
        goal.setMyRoadCards(Math.min(build.getWood(), build.getBrick()));
        goal.setHasOreProducer(hasProducer(player.getId(), Resource.ORE));
        goal.setHasGrainProducer(hasProducer(player.getId(), Resource.GRAIN));
        setBestResourceTargets(goal, player, Resource.ORE);
        setBestResourceTargets(goal, player, Resource.GRAIN);
        setBestTownTarget(goal, player);
        return goal;
    }

    private boolean hasProducer(int playerId, Resource resource) {
        for (Node node : nodeService.getAll()) {
            if (node.getOwner() == null || node.getOwner().getId() != playerId || node.getSettlement() == null) {
                continue;
            }
            for (Hexagon hex : node.getAdjacentHexagons()) {
                if (hex.getField() == resource) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setBestResourceTargets(VictoryGoal goal, Player player, Resource resource) {
        List<Node> candidates = new ArrayList<>(buildActionService.legalVillageTargets(player));
        if (candidates.isEmpty()) {
            candidates = nodeService.getAll().stream()
                    .filter(node -> node.getSettlement() == null)
                    .toList();
        }

        Node bestNode = null;
        int bestScore = 0;
        for (Node node : candidates) {
            int score = resourceScore(node, resource);
            if (score > bestScore || (score == bestScore && bestNode != null && node.getId() < bestNode.getId())) {
                bestNode = node;
                bestScore = score;
            }
        }

        if (bestNode == null || bestScore == 0) {
            return;
        }
        if (resource == Resource.ORE) {
            goal.setBestOreNodeId(bestNode.getId());
            goal.setBestOreNodeScore(bestScore);
        } else if (resource == Resource.GRAIN) {
            goal.setBestGrainNodeId(bestNode.getId());
            goal.setBestGrainNodeScore(bestScore);
        }
    }

    private int resourceScore(Node node, Resource resource) {
        int score = 0;
        for (Hexagon hex : node.getAdjacentHexagons()) {
            if (hex.getField() == resource) {
                score += diceWeight(hex.getDots());
            }
        }
        return score;
    }

    private void setBestTownTarget(VictoryGoal goal, Player player) {
        Node bestNode = null;
        int bestScore = 0;
        for (Node node : buildActionService.legalTownTargets(player)) {
            int score = townUpgradeScore(node);
            if (score > bestScore || (score == bestScore && bestNode != null && node.getId() < bestNode.getId())) {
                bestNode = node;
                bestScore = score;
            }
        }
        if (bestNode != null) {
            goal.setBestTownNodeId(bestNode.getId());
            goal.setBestTownNodeScore(bestScore);
        }
    }

    private int townUpgradeScore(Node node) {
        if (node.getScore() > 0) {
            return node.getScore();
        }
        int score = 0;
        for (Hexagon hex : node.getAdjacentHexagons()) {
            score += diceWeight(hex.getDots());
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
}
