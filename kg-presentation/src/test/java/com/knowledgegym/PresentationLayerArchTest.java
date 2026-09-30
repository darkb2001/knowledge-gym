package com.knowledgegym;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture dependency rule (kg-presentation):
 * Presentation không đụng persistence / content adapters — chỉ gọi use case + port.
 * (infrastructure.security cookie/IP vẫn được phép: đó là adapter HTTP session.)
 */
class PresentationLayerArchTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.knowledgegym");
    }

    @Test
    void presentation_khong_import_infrastructure_persistence() {
        noClasses()
                .that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence..")
                .because("presentation calls use cases, not persistence adapters directly")
                .check(classes);
    }

    @Test
    void presentation_khong_import_infrastructure_content() {
        noClasses()
                .that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.content..")
                .because("content import đi qua ContentImportJob port, không inject adapter")
                .check(classes);
    }

    @Test
    void presentation_khong_import_jpa_repository_directly() {
        noClasses()
                .that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.jpa.repository..",
                        "jakarta.persistence..")
                .because("controllers chỉ gọi application use case, không JPA")
                .check(classes);
    }
}