package com.ftn.sbnz.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.runtime.KieContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.ftn.sbnz.kjar.ResourcePriorityTemplateCompiler;

@SpringBootApplication
@EntityScan(basePackages = "com.ftn.sbnz.model")
@EnableJpaRepositories(basePackages = "com.ftn.sbnz")
@ComponentScan(basePackages = "com.ftn.sbnz")
public class ServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceApplication.class, args);
	}

	@Bean
	public KieContainer kieContainer() throws IOException {
		KieServices services = KieServices.Factory.get();
		var fileSystem = services.newKieFileSystem();
		ReleaseId releaseId = services.newReleaseId(
				"com.ftn.sbnz", "runtime-board-rules", "0.0.1");
		fileSystem.generateAndWritePomXML(releaseId);

		KieModuleModel module = services.newKieModuleModel();
		KieBaseModel base = module.newKieBaseModel("defaultKieBase")
				.setDefault(true)
				.addPackage("rules.board");
		base.newKieSessionModel("boardScoreSession").setDefault(true);
		fileSystem.writeKModuleXML(module.toXML());

		fileSystem.write("src/main/resources/rules/board/node-scoring.drl",
				services.getResources().newClassPathResource(
						"rules/board/node-scoring.drl", ResourcePriorityTemplateCompiler.class));
		fileSystem.write("src/main/resources/rules/board/player-scoring.drl",
				services.getResources().newClassPathResource(
						"rules/board/player-scoring.drl", ResourcePriorityTemplateCompiler.class));
		fileSystem.write("src/main/resources/rules/board/build-actions.drl",
				services.getResources().newClassPathResource(
						"rules/board/build-actions.drl", ResourcePriorityTemplateCompiler.class));
		fileSystem.write("src/main/resources/rules/board/road-connectivity.drl",
				services.getResources().newClassPathResource(
						"rules/board/road-connectivity.drl", ResourcePriorityTemplateCompiler.class));

		String priorityRules;
		try (InputStream template = ResourcePriorityTemplateCompiler.class.getClassLoader()
				.getResourceAsStream("rules/board/resource_priority.drt");
			 InputStream data = ResourcePriorityTemplateCompiler.class.getClassLoader()
				.getResourceAsStream("rules/board/resource_priority.data")) {
			priorityRules = ResourcePriorityTemplateCompiler.compile(template, data);
		}
		fileSystem.write("src/main/resources/rules/board/resource-priority-generated.drl",
				services.getResources().newByteArrayResource(
						priorityRules.getBytes(StandardCharsets.UTF_8)));

		String tradeAdviceRules;
		try (InputStream template = ResourcePriorityTemplateCompiler.class.getClassLoader()
				.getResourceAsStream("rules/board/trade_advice.drt");
			 InputStream data = ResourcePriorityTemplateCompiler.class.getClassLoader()
				.getResourceAsStream("rules/board/trade_advice.data")) {
			tradeAdviceRules = ResourcePriorityTemplateCompiler.compile(template, data);
		}
		fileSystem.write("src/main/resources/rules/board/trade-advice-generated.drl",
				services.getResources().newByteArrayResource(
						tradeAdviceRules.getBytes(StandardCharsets.UTF_8)));

		var builder = services.newKieBuilder(fileSystem).buildAll();
		Results results = builder.getResults();
		if (results.hasMessages(Message.Level.ERROR)) {
			throw new IllegalStateException("Cannot build board rules: "
					+ results.getMessages(Message.Level.ERROR));
		}
		return services.newKieContainer(releaseId);
	}

}
