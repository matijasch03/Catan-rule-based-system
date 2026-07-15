package com.ftn.sbnz.kjar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieSession;

class RoadConnectivityRuleTest {

    @Test
    void recursiveQueryProvesReachableTarget() {
        KieSession session = session();
        try {
            session.insert(new RoadLink(1, 2, 4));
            session.insert(new RoadLink(2, 3, 4));
            session.insert(new RoadLink(3, 4, 4));

            assertEquals(1, session.getQueryResults("canConnect", 1, 4).size());
            assertEquals(0, session.getQueryResults("canConnect", 4, 1).size());
        } finally {
            session.dispose();
        }
    }

    private static KieSession session() {
        KieServices services = KieServices.Factory.get();
        var fileSystem = services.newKieFileSystem();
        fileSystem.write("src/main/resources/rules/board/road-connectivity.drl",
                services.getResources().newClassPathResource(
                        "rules/board/road-connectivity.drl", RoadConnectivityRuleTest.class));
        var builder = services.newKieBuilder(fileSystem).buildAll();
        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(builder.getResults().getMessages().toString());
        }
        return services
                .newKieContainer(services.getRepository().getDefaultReleaseId())
                .newKieSession();
    }
}
