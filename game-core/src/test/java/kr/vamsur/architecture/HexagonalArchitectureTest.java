package kr.vamsur.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kr.vamsur");

    @Test
    void domainOnlyDependsOnTheDomainAndJava() {
        classes()
                .that().resideInAPackage("kr.vamsur.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "kr.vamsur.domain.."
                )
                .check(productionClasses);
    }

    @Test
    void applicationOnlyDependsOnTheApplicationDomainAndJava() {
        classes()
                .that().resideInAPackage("kr.vamsur.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "kr.vamsur.application..",
                        "kr.vamsur.domain.."
                )
                .check(productionClasses);
    }

    @Test
    void providedPortsAndDtosDoNotDependOnServiceImplementations() {
        noClasses()
                .that().resideInAPackage("kr.vamsur.application..provided..")
                .should().dependOnClassesThat().resideInAPackage("kr.vamsur.application.gameplay")
                .check(productionClasses);
    }
}
