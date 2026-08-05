package kr.joseonnight.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import kr.joseonnight.support.stereotype.DesktopApiAdapter;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.RestController;

class HexagonalArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kr.joseonnight");

    @Test
    void domainDoesNotDependOnApplication() {
        noClasses()
                .that().resideInAPackage("kr.joseonnight.domain..")
                .should().dependOnClassesThat().resideInAPackage("kr.joseonnight.application..")
                .check(productionClasses);
    }

    @Test
    void validatedApplicationServicesStayAtSliceRoot() {
        classes()
                .that().areAnnotatedWith(ValidatedApplicationService.class)
                .should().resideInAnyPackage("kr.joseonnight.application.*")
                .check(productionClasses);
    }

    @Test
    void desktopApiAdaptersStayInTheirAdapterPackage() {
        classes()
                .that().areAnnotatedWith(DesktopApiAdapter.class)
                .should().resideInAPackage("kr.joseonnight.adapter.desktopapi")
                .check(productionClasses);
    }

    @Test
    void desktopApiAdaptersUseProvidedPortsInsteadOfRequiredPorts() {
        noClasses()
                .that().resideInAPackage("kr.joseonnight.adapter.desktopapi")
                .should().dependOnClassesThat().resideInAPackage("kr.joseonnight.application..required")
                .check(productionClasses);
    }

    @Test
    void domainSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("kr.joseonnight.domain.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }

    @Test
    void applicationSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("kr.joseonnight.application.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }

    @Test
    void providedPortsAndDtosDoNotDependOnServiceImplementations() {
        noClasses()
                .that().resideInAPackage("kr.joseonnight.application..provided..")
                .should().dependOnClassesThat().resideInAPackage("kr.joseonnight.application.gameplay")
                .check(productionClasses);
    }

    @Test
    void domainAndApplicationDoNotDependOnAdapters() {
        noClasses()
                .that().resideInAnyPackage("kr.joseonnight.domain..", "kr.joseonnight.application..")
                .should().dependOnClassesThat().resideInAPackage("kr.joseonnight.adapter..")
                .check(productionClasses);
    }

    @Test
    void domainAndApplicationDoNotUsePresentationTechnology() {
        noClasses()
                .that().resideInAnyPackage("kr.joseonnight.domain..", "kr.joseonnight.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..",
                        "org.springframework.web..",
                        "jakarta.servlet.."
                )
                .check(productionClasses);
    }

    @Test
    void coreDoesNotUseJavaFx() {
        noClasses()
                .should().dependOnClassesThat().resideInAPackage("javafx..")
                .check(productionClasses);
    }

    @Test
    void jpaRepositoryPortsStayInApplicationRequiredPackages() {
        classes()
                .that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage("kr.joseonnight.application..required")
                .andShould().beInterfaces()
                .check(productionClasses);
    }

    @Test
    void springDataJpaAndPaginationTypesStayInRequiredRepositoryPackages() {
        noClasses()
                .that().resideOutsideOfPackage("kr.joseonnight.application..required")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.jpa..",
                        "org.springframework.data.domain.."
                )
                .check(productionClasses);
    }

    @Test
    void springMvcTypesStayInWebAndSecurityAdapters() {
        noClasses()
                .that().resideOutsideOfPackages(
                        "kr.joseonnight.adapter.webapi..",
                        "kr.joseonnight.adapter.security.authweb..",
                        "kr.joseonnight.adapter.security.jwt.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.bind.annotation..",
                        "jakarta.servlet.."
                )
                .check(productionClasses);
    }

    @Test
    void googleSpecificClassesStayInGoogleOAuthAdapter() {
        classes()
                .that().haveSimpleNameStartingWith("Google")
                .should().resideInAPackage("kr.joseonnight.adapter.security.googleoauth")
                .check(productionClasses);
    }

    @Test
    void googleOAuthAdapterDoesNotExchangeCodesWithRestClient() {
        noClasses()
                .that().resideInAPackage("kr.joseonnight.adapter.security.googleoauth")
                .should().dependOnClassesThat().resideInAPackage(
                        "org.springframework.web.client..")
                .check(productionClasses);
    }

    @Test
    void mvcControllersStayInWebApiOrAuthenticationWebAdapters() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAnyPackage(
                        "kr.joseonnight.adapter.webapi..",
                        "kr.joseonnight.adapter.security.authweb.."
                )
                .check(productionClasses);
    }

    @Test
    void kafkaListenersStayInMessagingIntegrationAdapter() {
        methods()
                .that().areAnnotatedWith(KafkaListener.class)
                .should().beDeclaredInClassesThat().resideInAPackage(
                        "kr.joseonnight.adapter.integration.messaging..")
                .check(productionClasses);
    }

    @Test
    void springKafkaDependenciesStayInMessagingIntegrationAdapter() {
        noClasses()
                .that().resideOutsideOfPackage("kr.joseonnight.adapter.integration.messaging..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.kafka..")
                .check(productionClasses);
    }

    @Test
    void obsoleteMessagingAdapterPackageIsForbidden() {
        noClasses()
                .should().resideInAPackage("kr.joseonnight.adapter.messaging..")
                .check(productionClasses);
    }

    @Test
    void oauthClientAndOidcDependenciesStayInAuthenticationSecurityAdapters() {
        noClasses()
                .that().resideOutsideOfPackages(
                        "kr.joseonnight.adapter.security.authweb..",
                        "kr.joseonnight.adapter.security.googleoauth.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.security.oauth2.client..",
                        "org.springframework.security.oauth2.core.oidc.."
                )
                .check(productionClasses);
    }
}
