package com.cardbilling.collections;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal rules from {@code card-billing-modernization/ARCHITECTURE.md}, enforced rather
 * than asserted in a README. A build with a broken layer boundary fails the same way a broken test
 * does, which is the whole point: a reader can verify "this service follows hexagonal
 * architecture" by looking at a green build instead of taking a claim on faith.
 */
@AnalyzeClasses(
        packages = "com.cardbilling.collections",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_plain_java = noClasses()
            .that()
            .resideInAPackage("..collections.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "com.fasterxml..",
                    "io.github.resilience4j..",
                    "io.swagger..",
                    "org.slf4j..")
            .because("the domain holds the business rules; a framework import there is a rule "
                    + "that can only be tested with that framework running");

    @ArchTest
    static final ArchRule domain_depends_on_nothing_above_it = noClasses()
            .that()
            .resideInAPackage("..collections.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..collections.application..", "..collections.infrastructure..")
            .because("dependencies point inwards: the domain is the centre");

    @ArchTest
    static final ArchRule application_reaches_infrastructure_only_through_its_ports = noClasses()
            .that()
            .resideInAPackage("..collections.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..collections.infrastructure..")
            .because("a use case that names a REST client or a Redis template can no longer be "
                    + "tested without one");

    @ArchTest
    static final ArchRule web_does_not_call_downstream_services_directly = noClasses()
            .that()
            .resideInAPackage("..collections.infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..collections.infrastructure.client..", "..collections.infrastructure.cache..")
            .because("controllers call into the application layer; an adapter calling another "
                    + "adapter routes around every rule the use case enforces");

    @ArchTest
    static final ArchRule scheduling_does_not_call_downstream_services_directly = noClasses()
            .that()
            .resideInAPackage("..collections.infrastructure.scheduling..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..collections.infrastructure.client..", "..collections.infrastructure.cache..")
            .because("the daily schedule is a second trigger for the same use case, not a second "
                    + "implementation of the run");

    @ArchTest
    static final ArchRule ports_are_interfaces = classes()
            .that()
            .resideInAPackage("..collections.application.port..")
            .should()
            .beInterfaces()
            .because("a port is a contract the infrastructure implements, not a class it extends");

    /**
     * ARCHITECTURE.md's fourth rule pairs {@code *Entity} names with {@code @jakarta.persistence.
     * Entity}. This service has no persistence layer at all — it owns no database — so the rule
     * that catches drift here is the stricter one: any class named {@code *Entity} appearing in
     * this service means a database has crept in where the design says there is none.
     */
    @ArchTest
    static final ArchRule no_class_is_named_like_a_persistence_entity = noClasses()
            .should()
            .haveSimpleNameEndingWith("Entity")
            .because("collections-service owns no database; an *Entity class here means invoice "
                    + "state has started being stored somewhere other than billing-service");

    @ArchTest
    static final ArchRule no_class_depends_on_a_jpa_api = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..")
            .because("the same rule, from the other direction");
}
