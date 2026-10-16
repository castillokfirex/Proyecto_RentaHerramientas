package com.rentatools.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica las reglas de pureza hexagonal de {@code PLAN.md} §1.3 (reglas 1, 2, 3 y 7).
 */
@AnalyzeClasses(packages = "com.rentatools", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalLayersTest {

    /**
     * Regla 1: {@code ..domain..} no depende de frameworks ni de {@code ..application..} o {@code ..infrastructure..}.
     */
    @ArchTest
    static final ArchRule domain_must_be_pure_java_and_independent_of_outer_layers =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "com.fasterxml..",
                            "lombok..",
                            "..application..",
                            "..infrastructure.."
                    )
                    .allowEmptyShould(true);

    /**
     * Regla 2: {@code ..application..} no depende de Spring, Jakarta, Hibernate ni de {@code ..infrastructure..}.
     */
    @ArchTest
    static final ArchRule application_must_be_pure_java_and_independent_of_infrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "lombok..",
                            "..infrastructure.."
                    )
                    .allowEmptyShould(true);

    /**
     * Regla 3a: {@code <ctx>.api..} (y {@code shared.integration..}) no depende de Spring, Jakarta ni Lombok.
     */
    @ArchTest
    static final ArchRule api_and_integration_contracts_must_be_pure_java =
            noClasses()
                    .that().resideInAnyPackage("com.rentatools.*.api..", "com.rentatools.shared.integration..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "lombok..",
                            "..infrastructure.."
                    )
                    .allowEmptyShould(true);

    /**
     * Regla 3b: {@code <ctx>.api..} solo contiene interfaces, records, enums (y {@code package-info}).
     */
    @ArchTest
    static final ArchRule api_packages_must_only_contain_interfaces_records_or_enums =
            classes()
                    .that().resideInAPackage("com.rentatools.*.api..")
                    .and().haveSimpleNameNotEndingWith("package-info")
                    .should().beInterfaces()
                    .orShould().beRecords()
                    .orShould().beEnums()
                    .allowEmptyShould(true);

    /**
     * Regla 7a: {@code @Entity} solo en {@code ..adapter.out.persistence..}.
     */
    @ArchTest
    static final ArchRule jpa_entities_must_reside_in_persistence_adapter =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should().resideInAPackage("..adapter.out.persistence..")
                    .allowEmptyShould(true);

    /**
     * Regla 7b: {@code @RestController} solo en {@code ..adapter.in.web..} (o en {@code ..infrastructure.dev..} para soporte dev).
     */
    @ArchTest
    static final ArchRule rest_controllers_must_reside_in_web_adapter_or_dev =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage("..adapter.in.web..", "..infrastructure.dev..")
                    .allowEmptyShould(true);

    static final DescribedPredicate<JavaClass> IS_PACKAGE_INFO =
            new DescribedPredicate<>("is package-info") {
                @Override
                public boolean test(JavaClass input) {
                    return "package-info".equals(input.getSimpleName());
                }
            };
}
