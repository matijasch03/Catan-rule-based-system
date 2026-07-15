package com.ftn.sbnz.service;

import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServiceApplicationTests {
	@Autowired
	private KieContainer kieContainer;

	@Test
	void contextLoads() {
		KieSession session = kieContainer.newKieSession("boardScoreSession");
		try {
			session.fireAllRules();
		} finally {
			session.dispose();
		}
	}

}
