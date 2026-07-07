package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieSession;

import com.ftn.sbnz.model.PlayerScoreFact;

class PlayerScoringRuleTest {

    @Test
    void scoresBuildingsLongestRoadAndVictory() {
        PlayerScoreFact player = new PlayerScoreFact(1, 4, 2, 5, 1);

        fireRules(player);

        assertEquals(10, player.getScore());
        assertTrue(player.isLongestRoadAwarded());
        assertTrue(player.isWinner());
    }

    @Test
    void equalLongestRoadGoesToPlayerWhoReachedLengthFirst() {
        PlayerScoreFact first = new PlayerScoreFact(1, 2, 0, 6, 10);
        PlayerScoreFact second = new PlayerScoreFact(2, 2, 0, 6, 11);

        fireRules(first, second);

        assertEquals(4, first.getScore());
        assertTrue(first.isLongestRoadAwarded());
        assertEquals(2, second.getScore());
        assertFalse(second.isLongestRoadAwarded());
    }

    private static void fireRules(Object... facts) {
        KieServices services = KieServices.Factory.get();
        var fileSystem = services.newKieFileSystem();
        fileSystem.write("src/main/resources/rules/board/player-scoring.drl",
                services.getResources().newClassPathResource(
                        "rules/board/player-scoring.drl", PlayerScoringRuleTest.class));
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
