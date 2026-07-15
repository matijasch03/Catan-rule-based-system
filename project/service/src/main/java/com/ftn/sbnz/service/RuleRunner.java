package com.ftn.sbnz.service;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.ftn.sbnz.model.Hexagon;
import com.ftn.sbnz.model.Node;
import com.ftn.sbnz.model.BoardPrinter;
import com.ftn.sbnz.kjar.RankingRequest;
import com.ftn.sbnz.kjar.ResourcePriorityTemplateCompiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RuleRunner {
    public static void main(String[] args) {
        try {
            KieServices ks = KieServices.Factory.get();
            var kfs = ks.newKieFileSystem();
            kfs.write("src/main/resources/rules/board/node-scoring.drl",
                    ks.getResources().newClassPathResource("rules/board/node-scoring.drl", RuleRunner.class));
            try (InputStream template = RuleRunner.class.getClassLoader()
                    .getResourceAsStream("rules/board/resource_priority.drt");
                 InputStream data = RuleRunner.class.getClassLoader()
                    .getResourceAsStream("rules/board/resource_priority.data")) {
                String generated = ResourcePriorityTemplateCompiler.compile(template, data);
                kfs.write("src/main/resources/rules/board/resource-priority-generated.drl",
                        ks.getResources().newByteArrayResource(
                                generated.getBytes(StandardCharsets.UTF_8)));
            }

            KieBuilder kb = ks.newKieBuilder(kfs);
            kb.buildAll();
            Results results = kb.getResults();
            if (results != null && results.hasMessages(Message.Level.ERROR)) {
                System.err.println("KieBuilder errors: " + results.getMessages());
                return;
            }

            KieContainer kc = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            KieSession ksession = kc.newKieSession();

            List<List<Hexagon>> board = com.ftn.sbnz.model.BoardGenerator.generateBoard();
            // print board for visual verification
            BoardPrinter.printBoard(board);
            List<Node> nodes = com.ftn.sbnz.model.BoardGenerator.generateNodes(board);

            BoardPrinter.printBoard(board);

            for (Node n : nodes) ksession.insert(n);
            ksession.insert(new RankingRequest());
            ksession.fireAllRules();

            BoardPrinter.printBoard(board);

            System.out.println("\nBest candidates:");
            java.util.Collection<?> bests = ksession.getObjects(o -> o instanceof com.ftn.sbnz.kjar.BestNode);
            for (Object b : bests) System.out.println(b);

            ksession.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
