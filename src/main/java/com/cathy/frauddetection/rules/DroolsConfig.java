package com.cathy.frauddetection.rules;

import java.nio.charset.StandardCharsets;

import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "fraud.rules.engine", havingValue = "drools")
@Configuration
class DroolsConfig {

    private static final String DRL_CLASSPATH = "rules/fraud-rules.drl";

    // KieFileSystem is a virtual in-memory project with a Maven-style layout.
    // This path is internal to that virtual project and has nothing to do with
    // where the file actually lives on disk.
    private static final String DRL_VIRTUAL_PATH = "src/main/resources/com/cathy/frauddetection/rules/fraud-rules.drl";

    @Bean
    KieContainer kieContainer() {
        KieServices kieServices = KieServices.get();

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        // Explicit charset: the single-argument overload uses the platform
        // default, so the same DRL could parse differently on Windows and CI.
        kieFileSystem.write(DRL_VIRTUAL_PATH,
                kieServices.getResources().newClassPathResource(DRL_CLASSPATH, StandardCharsets.UTF_8.name()));

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem).buildAll(ExecutableModelProject.class);

        // DRL compiles at runtime, so a typo is not a compile error. Failing here
        // converts it into a startup failure. Without this the application starts
        // clean and dies inside the Kafka consumer, which has no dead letter queue
        // and would retry the same poisoned record forever.
        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("DRL compilation failed: " + results.getMessages());
        }

        // KieContainer, not KieSession: the container is thread safe and shared,
        // a session holds working memory and must be created per evaluation.
        return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    }
}