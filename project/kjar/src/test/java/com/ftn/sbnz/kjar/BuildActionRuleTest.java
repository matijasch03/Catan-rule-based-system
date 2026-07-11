package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieSession;

import com.ftn.sbnz.model.BuildActionFact;

class BuildActionRuleTest {

    @Test
    void enablesAffordableBuildActionsWhenBoardStateAllowsThem() {
        BuildActionFact action = new BuildActionFact(1);
        action.setWood(1);
        action.setBrick(1);
        action.setWool(1);
        action.setGrain(2);
        action.setOre(3);
        action.setConnectedRoadsFromVillage(2);
        action.setHasOpenRoadEdge(true);
        action.setHasLegalVillageNode(true);
        action.setHasVillageToUpgrade(true);

        fireRules(action);

        assertTrue(action.isCanBuildRoad());
        assertTrue(action.isCanBuildVillage());
        assertTrue(action.isCanBuildTown());
    }

    @Test
    void keepsActionsDisabledWhenResourcesAreMissing() {
        BuildActionFact action = new BuildActionFact(1);
        action.setWood(1);
        action.setHasOpenRoadEdge(true);
        action.setHasLegalVillageNode(true);
        action.setHasVillageToUpgrade(true);

        fireRules(action);

        assertFalse(action.isCanBuildRoad());
        assertFalse(action.isCanBuildVillage());
        assertFalse(action.isCanBuildTown());
    }

    @Test
    void backwardGoalSuggestsTradeWhenTownResourcesAreMissing() {
        BuildActionFact action = new BuildActionFact(1);
        action.setHasVillageToUpgrade(true);
        action.setHasOpenRoadEdge(true);
        action.setWood(2);
        action.setBrick(1);
        action.setWool(1);
        action.setGrain(1);
        action.setOre(1);

        VictoryGoal goal = new VictoryGoal(1, 6);
        goal.setHasVillageToUpgrade(true);
        goal.setHasOpenRoadEdge(true);
        goal.setWood(2);
        goal.setBrick(1);
        goal.setWool(1);
        goal.setGrain(1);
        goal.setOre(1);
        goal.setTotalResources(6);

        List<GoalAdvice> advice = fireRules(action, goal).stream()
                .filter(GoalAdvice.class::isInstance)
                .map(GoalAdvice.class::cast)
                .toList();

        assertTrue(advice.stream().anyMatch(item -> item.getTitle().equals("Trade for town ore")));
        assertTrue(advice.stream().anyMatch(item -> item.getTitle().equals("Trade for town grain")));
    }

    @Test
    void goalSuggestsSpecificVillageResourceTrades() {
        BuildActionFact action = new BuildActionFact(1);
        action.setWood(1);
        action.setGrain(4);
        action.setHasLegalVillageNode(true);

        VictoryGoal goal = new VictoryGoal(1, 6);
        goal.setWood(1);
        goal.setGrain(4);
        goal.setHasLegalVillageNode(true);
        goal.setTotalResources(5);

        List<GoalAdvice> advice = fireRules(action, goal).stream()
                .filter(GoalAdvice.class::isInstance)
                .map(GoalAdvice.class::cast)
                .toList();

        assertTrue(advice.stream().anyMatch(item -> item.getTitle().equals("Trade for village brick")));
        assertTrue(advice.stream().anyMatch(item -> item.getTitle().equals("Trade for village wool")));
    }

    @Test
    void goalSuggestsBestTownUpgradeNodeWhenTownCanBeBuilt() {
        VictoryGoal goal = new VictoryGoal(1, 6);
        goal.setCanBuildTown(true);
        goal.setBestTownNodeId(12);
        goal.setBestTownNodeScore(17);

        List<GoalAdvice> advice = fireRules(goal).stream()
                .filter(GoalAdvice.class::isInstance)
                .map(GoalAdvice.class::cast)
                .toList();

        assertTrue(advice.stream()
                .anyMatch(item -> item.getTitle().equals("Upgrade a village to town")
                        && item.getDescription().contains("node 12")
                        && item.getDescription().contains("score 17")));
    }

    private static List<Object> fireRules(Object... facts) {
        KieServices services = KieServices.Factory.get();
        var fileSystem = services.newKieFileSystem();
        fileSystem.write("src/main/resources/rules/board/build-actions.drl",
                services.getResources().newClassPathResource(
                        "rules/board/build-actions.drl", BuildActionRuleTest.class));
        try (InputStream template = BuildActionRuleTest.class.getClassLoader()
                .getResourceAsStream("rules/board/trade_advice.drt");
             InputStream data = BuildActionRuleTest.class.getClassLoader()
                .getResourceAsStream("rules/board/trade_advice.data")) {
            String generated = ResourcePriorityTemplateCompiler.compile(template, data);
            fileSystem.write("src/main/resources/rules/board/trade-advice-generated.drl",
                    services.getResources().newByteArrayResource(
                            generated.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate trade advice rules.", ex);
        }
        var builder = services.newKieBuilder(fileSystem).buildAll();
        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(builder.getResults().getMessages().toString());
        }

        KieSession session = services
                .newKieContainer(services.getRepository().getDefaultReleaseId())
                .newKieSession();
        try {
            for (Object fact : facts) session.insert(fact);
            session.fireAllRules();
            return List.copyOf(session.getObjects());
        } finally {
            session.dispose();
        }
    }
}
