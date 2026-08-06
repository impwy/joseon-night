package kr.joseonnight.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.persistence.Entity;
import java.util.Set;
import kr.joseonnight.support.stereotype.DesktopApiAdapter;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.RestController;

class HexagonalArchitectureTest {

    private static final Set<String> MEMBER_AGGREGATE_ENTITIES = Set.of(
            "Member",
            "OAuthIdentity",
            "MemberCharacter",
            "MemberItem"
    );

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
    void aggregateFieldsDoNotReferenceObjectsFromOtherAggregates() {
        classes()
                .that().resideInAPackage("kr.joseonnight.domain..")
                .should(notHoldObjectReferencesToOtherAggregates())
                .check(productionClasses);
    }

    @Test
    void memberAggregateInternalsDoNotExposePublicCommands() {
        classes()
                .that().resideInAPackage("kr.joseonnight.domain.member")
                .should(exposeOnlyPublicQueries())
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

    private static ArchCondition<JavaClass> notHoldObjectReferencesToOtherAggregates() {
        return new ArchCondition<>("reference other aggregates only by scalar identifiers") {
            @Override
            public void check(JavaClass owner, ConditionEvents events) {
                String ownerSlice = domainSlice(owner);
                if (ownerSlice == null || "shared".equals(ownerSlice)) {
                    return;
                }
                owner.getFields().forEach(field -> field.getAllInvolvedRawTypes().stream()
                        .filter(fieldType -> referencesAnotherAggregate(
                                owner,
                                ownerSlice,
                                fieldType
                        ))
                        .forEach(fieldType -> events.add(SimpleConditionEvent.violated(
                                field,
                                field.getFullName() + " references aggregate object "
                                        + fieldType.getName()
                                        + "; store its identifier instead"
                        ))));
            }
        };
    }

    private static boolean referencesAnotherAggregate(
            JavaClass owner,
            String ownerSlice,
            JavaClass target
    ) {
        String targetSlice = domainSlice(target);
        if (targetSlice == null || "shared".equals(targetSlice)) {
            return false;
        }
        if (!ownerSlice.equals(targetSlice)) {
            return true;
        }
        if (!owner.isAnnotatedWith(Entity.class) || !target.isAnnotatedWith(Entity.class)) {
            return false;
        }
        return !jpaAggregateIdentity(owner).equals(jpaAggregateIdentity(target));
    }

    private static String jpaAggregateIdentity(JavaClass type) {
        if ("kr.joseonnight.domain.member".equals(type.getPackageName())
                && MEMBER_AGGREGATE_ENTITIES.contains(type.getSimpleName())) {
            return "member";
        }
        return type.getName();
    }

    private static ArchCondition<JavaClass> exposeOnlyPublicQueries() {
        return new ArchCondition<>("expose no public mutation outside the Member root") {
            @Override
            public void check(JavaClass component, ConditionEvents events) {
                if (!component.getSimpleName().matches(
                        "OAuthIdentity|MemberCharacter|MemberItem")) {
                    return;
                }
                component.getMethods().stream()
                        .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                        .filter(method -> !method.getName().startsWith("get"))
                        .filter(method -> !method.getName().startsWith("is"))
                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName()
                                        + " exposes an internal aggregate command; route it through Member"
                        )));
            }
        };
    }

    private static String domainSlice(JavaClass type) {
        String prefix = "kr.joseonnight.domain.";
        String packageName = type.getPackageName();
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String remainder = packageName.substring(prefix.length());
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }
}
