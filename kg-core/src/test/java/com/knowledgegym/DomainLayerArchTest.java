package com.knowledgegym;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture dependency rule (kg-core):
 * Domain model/port package KHÔNG import Spring/JPA.
 * Phù hợp cho clean module enforce — classpath chỉ chứa domain.
 */
class DomainLayerArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.knowledgegym");
    }

    @Test
    void domain_model_khong_import_spring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..")
                .because("domain layer must be framework-independent (Clean Architecture)")
                .check(classes);
    }

    @Test
    void domain_port_khong_import_spring() {
        noClasses()
                .that().resideInAPackage("..domain.port..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..")
                .because("ports define contracts, implementations live in infrastructure")
                .check(classes);
    }

    @Test
    void domain_khong_extend_jpa_or_spring_base_class() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAssignableTo("org.springframework.data.jpa.repository.JpaRepository")
                .because("repository interfaces là port thuần Java, KHÔNG extends JpaRepository")
                .check(classes);
    }
}