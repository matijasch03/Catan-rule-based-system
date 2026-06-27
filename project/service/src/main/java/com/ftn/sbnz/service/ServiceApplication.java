package com.ftn.sbnz.service;

import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.runtime.KieContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EntityScan("com.ftn.sbnz.model")
public class ServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceApplication.class, args);
	}

	@Bean
	public KieContainer kieContainer() {
		KieServices ks = KieServices.Factory.get();
		try {
			KieContainer kContainer = ks
			.newKieContainer(ks.newReleaseId("com.ftn.sbnz", "skjar", "0.0.1-SNAPSHOT"));
			KieScanner kScanner = ks.newKieScanner(kContainer);
			kScanner.start(1000);
			return kContainer;
		} catch (RuntimeException e) {
			// Fallback: return default container if kjar not found
			System.out.println("KieModule not found, using default container: " + e.getMessage());
			return ks.getKieClasspathContainer();
		}
	}

}
