package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static void fireRules(Object... facts) {
        KieServices services = KieServices.Factory.get();
        var fileSystem = services.newKieFileSystem();
        fileSystem.write("src/main/resources/rules/board/build-actions.drl",
                services.getResources().newClassPathResource(
                        "rules/board/build-actions.drl", BuildActionRuleTest.class));
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
        } finally {
            session.dispose();
        }
    }
}
