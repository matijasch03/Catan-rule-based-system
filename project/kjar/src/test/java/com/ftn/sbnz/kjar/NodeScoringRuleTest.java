package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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

        assertEquals(22, node.getScore());
    }

    @Test
    void keepsBaseScoreWhenNodeTouchesTwoResourcesOrLess() {
        Node node = createNode(
                new Hexagon(0, 0, Resource.WOOD, 6, null),
                new Hexagon(1, 0, Resource.WOOD, 8, null),
                new Hexagon(0, 1, Resource.GRAIN, 5, null)
        );

        fireRules(node);

        assertEquals(18, node.getScore());
    }

    private static Node createNode(Hexagon... hexagons) {
        Node node = new Node();
        node.setAdjacentHexagons(List.of(hexagons));
        node.setScore(0);
        return node;
    }

    private static void fireRules(Node node) {
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
                kieSession.insert(node);
                kieSession.fireAllRules();
            } finally {
                kieSession.dispose();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
