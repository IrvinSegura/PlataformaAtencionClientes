package com.irvinsegura.supportchat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(SupportChatApplication.class);

    @Test
    void modulesOnlyUseEachOthersPublicApiAndHaveNoCycles() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // PlantUML diagrams and module canvases under target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
