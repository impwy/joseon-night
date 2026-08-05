package kr.vamsur.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import kr.vamsur.support.stereotype.DesktopApiAdapter;
import kr.vamsur.support.stereotype.ValidatedApplicationService;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kr.vamsur");

    @Test
    void domainDoesNotDependOnApplication() {
        noClasses()
                .that().resideInAPackage("kr.vamsur.domain..")
                .should().dependOnClassesThat().resideInAPackage("kr.vamsur.application..")
                .check(productionClasses);
    }

    @Test
    void validatedApplicationServicesStayAtSliceRoot() {
        classes()
                .that().areAnnotatedWith(ValidatedApplicationService.class)
                .should().resideInAnyPackage("kr.vamsur.application.*")
                .check(productionClasses);
    }

    @Test
    void desktopApiAdaptersStayInTheirAdapterPackage() {
        classes()
                .that().areAnnotatedWith(DesktopApiAdapter.class)
                .should().resideInAPackage("kr.vamsur.adapter.desktopapi")
                .check(productionClasses);
    }

    @Test
    void domainSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("kr.vamsur.domain.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }

    @Test
    void applicationSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("kr.vamsur.application.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }

    @Test
    void providedPortsAndDtosDoNotDependOnServiceImplementations() {
        noClasses()
                .that().resideInAPackage("kr.vamsur.application..provided..")
                .should().dependOnClassesThat().resideInAPackage("kr.vamsur.application.gameplay")
                .check(productionClasses);
    }

    @Test
    void domainAndApplicationDoNotDependOnAdapters() {
        noClasses()
                .that().resideInAnyPackage("kr.vamsur.domain..", "kr.vamsur.application..")
                .should().dependOnClassesThat().resideInAPackage("kr.vamsur.adapter..")
                .check(productionClasses);
    }

    @Test
    void coreDoesNotUseDesktopOrSpringWebTechnology() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..",
                        "org.springframework.web..",
                        "jakarta.servlet.."
                )
                .check(productionClasses);
    }
}
