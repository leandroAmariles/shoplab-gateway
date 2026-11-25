package com.shoplab.gateway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** A proxy has no domain layer (specs/LAB-107, plan: complexity tracking); these are the boundaries it does have. */
@AnalyzeClasses(packages = "com.shoplab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule SHARED_DOES_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..gateway.shared..")
            .should().dependOnClassesThat().resideInAPackage("..gateway.infrastructure..");

    @ArchTest
    static final ArchRule NOTHING_DEPENDS_ON_THE_MONOLITH = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.shoplab.helpdesk..")
            .because("a service repository only talks to others over the network (LAB-103)");
}
