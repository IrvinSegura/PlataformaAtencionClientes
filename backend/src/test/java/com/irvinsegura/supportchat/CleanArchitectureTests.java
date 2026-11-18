package com.irvinsegura.supportchat;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the dependency rule inside every module: web -> application -> domain <- infrastructure.
 */
@AnalyzeClasses(packagesOf = SupportChatApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTests {

    // Layers are matched only inside the application, so e.g. an HTTP client from org.springframework.web is not
    // mistaken for a web adapter
    private static final String APP = "com.irvinsegura.supportchat";

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(APP + "..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
                    "tools.jackson..", "com.fasterxml.jackson..", "java.sql..", "java.net.http..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage(APP + "..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(APP + "..application..", APP + "..infrastructure..", APP + "..web..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage(APP + "..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(APP + "..infrastructure..", APP + "..web..");

    @ArchTest
    static final ArchRule webDoesNotUseInfrastructure = noClasses()
            .that().resideInAPackage(APP + "..web..")
            .and().resideOutsideOfPackage(APP + ".shared..")
            .should().dependOnClassesThat()
            .resideInAPackage(APP + "..infrastructure..");

    @ArchTest
    static final ArchRule infrastructureDoesNotUseWeb = noClasses()
            .that().resideInAPackage(APP + "..infrastructure..")
            .should().dependOnClassesThat()
            .resideInAPackage(APP + "..web..");

    @ArchTest
    static final ArchRule webNeverTouchesTheDatabase = noClasses()
            .that().resideInAPackage(APP + "..web..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..", "java.sql..");
}
