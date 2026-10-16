package com.rentatools.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica las fronteras entre módulos acotados y ausencia de ciclos según {@code PLAN.md} §1.3 (reglas 4, 5 y 6).
 */
@AnalyzeClasses(packages = "com.rentatools", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final List<String> BOUNDED_CONTEXTS = List.of("iam", "inventory", "rental", "billing");

    /**
     * Regla 4: Una clase de {@code com.rentatools.X..} solo puede usar {@code com.rentatools.Y..} (con Y != X, shared)
     * si está en {@code Y.api..}, y solo desde {@code X.infrastructure..}.
     */
    @ArchTest
    static void bounded_contexts_only_communicate_via_api_and_only_from_infrastructure(JavaClasses classes) {
        for (String sourceCtx : BOUNDED_CONTEXTS) {
            for (String targetCtx : BOUNDED_CONTEXTS) {
                if (sourceCtx.equals(targetCtx)) {
                    continue;
                }

                // Ninguna clase de sourceCtx puede acceder a domain, application o infrastructure de targetCtx
                ArchRule noInternalAccess = noClasses()
                        .that().resideInAPackage("com.rentatools." + sourceCtx + "..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(
                                "com.rentatools." + targetCtx + ".domain..",
                                "com.rentatools." + targetCtx + ".application..",
                                "com.rentatools." + targetCtx + ".infrastructure.."
                        )
                        .allowEmptyShould(true);
                noInternalAccess.check(classes);

                // Solo sourceCtx.infrastructure.. puede usar targetCtx.api..
                ArchRule apiOnlyFromInfrastructure = noClasses()
                        .that().resideInAnyPackage(
                                "com.rentatools." + sourceCtx + ".domain..",
                                "com.rentatools." + sourceCtx + ".application..",
                                "com.rentatools." + sourceCtx + ".api.."
                        )
                        .should().dependOnClassesThat()
                        .resideInAPackage("com.rentatools." + targetCtx + ".api..")
                        .allowEmptyShould(true);
                apiOnlyFromInfrastructure.check(classes);
            }
        }
    }

    /**
     * Regla 5: {@code shared.infrastructure} solo puede usarse desde capas de infraestructura (o el bootstrap de la aplicación).
     */
    @ArchTest
    static final ArchRule shared_infrastructure_only_used_from_infrastructure =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain..",
                            "..application..",
                            "com.rentatools.*.api..",
                            "com.rentatools.shared.integration.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.rentatools.shared.infrastructure..")
                    .allowEmptyShould(true);

    /**
     * Regla 6: Sin ciclos entre los slices de primer nivel {@code com.rentatools.(*)..}.
     */
    @ArchTest
    static final ArchRule no_cycles_between_modules =
            SlicesRuleDefinition.slices()
                    .matching("com.rentatools.(*)..")
                    .should().beFreeOfCycles()
                    .allowEmptyShould(true);
}
