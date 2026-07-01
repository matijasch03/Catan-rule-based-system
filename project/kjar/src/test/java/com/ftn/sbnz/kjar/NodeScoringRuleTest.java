package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.Resource;

class NodeScoringRuleTest {

    @Test
    void appliesProbabilityBasedScoreWithDiversityBonus() {
        Node node = createNode(
                new Hexagon(0, 0, Resource.WOOD, 6, null),
                new Hexagon(1, 0, Resource.WOOL, 8, null),
                new Hexagon(0, 1, Resource.GRAIN, 5, null)
        );

        fireRules(node);

        // (5 + 5 + 4) * 1.20, rounded, plus the fixed BigYield bonus 2.
        assertEquals(19, node.getScore());
    }

    @Test
    void keepsBaseScoreWhenNodeTouchesTwoResourcesOrLess() {
        Node node = createNode(
                new Hexagon(0, 0, Resource.WOOD, 6, null),
                new Hexagon(1, 0, Resource.WOOD, 8, null),
                new Hexagon(0, 1, Resource.GRAIN, 5, null)
        );

        fireRules(node);

        // No diversity bonus: (5 + 5 + 4) plus the fixed BigYield bonus 2.
        assertEquals(16, node.getScore());
    }

    @Test
    void blocksOpponentAreaAndReturnsTwoRankedCandidates() {
        Node first = createNode(new Hexagon(0, 0, Resource.WOOD, 8, null));
        Node blockedSecond = createNode(new Hexagon(1, 0, Resource.BRICK, 6, null));
        Node third = createNode(new Hexagon(0, 1, Resource.GRAIN, 5, null));
        setId(first, 1);
        setId(blockedSecond, 2);
        setId(third, 3);

        List<BestNode> ranked = fireRules(
                first, blockedSecond, third,
                new SettlementBuilding(1, 3, 10, Set.of(2)),
                new RankingRequest());

        assertFalse(blockedSecond.isAvailable());
        assertEquals(List.of(1, 3), ranked.stream().map(BestNode::getNodeId).toList());
        assertEquals(List.of(1, 2), ranked.stream().map(BestNode::getRank).toList());
    }

    private static Node createNode(Hexagon... hexagons) {
        Node node = new Node();
        node.setAdjacentHexagons(List.of(hexagons));
        node.setScore(0);
        return node;
    }

    private static List<BestNode> fireRules(Object... facts) {
        try {
            KieServices ks = KieServices.Factory.get();
            org.kie.api.builder.KieFileSystem kfs = ks.newKieFileSystem();
            kfs.write("src/main/resources/rules/board/node-scoring.drl",
                    ks.getResources().newClassPathResource("rules/board/node-scoring.drl", NodeScoringRuleTest.class));
            org.kie.api.builder.KieBuilder kb = ks.newKieBuilder(kfs);
            kb.buildAll();
            org.kie.api.builder.Results results = kb.getResults();
            if (results != null && results.hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
                throw new RuntimeException("KieBuilder errors: " + results.getMessages());
            }
            KieContainer kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            KieSession kieSession = kieContainer.newKieSession();
            try {
                for (Object fact : facts) {
                    kieSession.insert(fact);
                }
                kieSession.fireAllRules();
                List<BestNode> ranked = new ArrayList<>();
                for (Object fact : kieSession.getObjects(o -> o instanceof BestNode)) {
                    ranked.add((BestNode) fact);
                }
                ranked.sort(Comparator.comparingInt(BestNode::getRank));
                return ranked;
            } finally {
                kieSession.dispose();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setId(Node node, int id) {
        try {
            Field field = Node.class.getDeclaredField("id");
            field.setAccessible(true);
            field.setInt(node, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
