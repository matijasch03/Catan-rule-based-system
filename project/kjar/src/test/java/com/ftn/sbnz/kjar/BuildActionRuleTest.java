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
import com.ftn.sbnz.model.Resource;

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

    @Test
    void cepRaisesHighBlockadeThreatWhenOpponentApproachesWithRoadResources() {
        VictoryGoal goal = new VictoryGoal(1, 6);
        goal.setRoadsMissingForPlannedRoute(3);
        goal.setMyRoadCards(0);
        goal.setOpponentRoadCards(2);

        RoadBuildEvent road = new RoadBuildEvent(2, 1, 44, 7, 1, 3, "east");
        ResourceProductionSignal wood = new ResourceProductionSignal(2, Resource.WOOD, 15.0);
        ResourceProductionSignal brick = new ResourceProductionSignal(2, Resource.BRICK, 12.0);
        TradeSignal trade = new TradeSignal(2, Resource.BRICK, 7, true, 3.0);

        List<GoalAdvice> advice = fireRules(goal, road, wood, brick, trade).stream()
                .filter(GoalAdvice.class::isInstance)
                .map(GoalAdvice.class::cast)
                .toList();

        assertTrue(advice.stream()
                .anyMatch(item -> item.getTitle().equals("Blockade threat")
                        && item.getDescription().contains("moved 2 road(s) closer")
                        && item.getDescription().contains("Threat score")));
    }

    @Test
    void cepRaisesMediumWarningForNearbyOpponentWithLightResourceSignals() {
        VictoryGoal goal = new VictoryGoal(1, 6);
        goal.setRoadsMissingForPlannedRoute(2);
        goal.setMyRoadCards(0);
        goal.setOpponentRoadCards(0);

        RoadBuildEvent road = new RoadBuildEvent(2, 1, 45, 7, 2, 2, "north");
        ResourceProductionSignal wood = new ResourceProductionSignal(2, Resource.WOOD, 9.0);

        List<GoalAdvice> advice = fireRules(goal, road, wood).stream()
                .filter(GoalAdvice.class::isInstance)
                .map(GoalAdvice.class::cast)
                .toList();

        assertTrue(advice.stream()
                .anyMatch(item -> item.getTitle().equals("Watch blockade route")
                        && item.getDescription().contains("from north")));
        assertFalse(advice.stream()
                .anyMatch(item -> item.getTitle().equals("Blockade threat")));
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
