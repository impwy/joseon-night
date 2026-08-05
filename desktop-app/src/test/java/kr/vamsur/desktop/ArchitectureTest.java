package kr.vamsur.desktop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AnalyzeClasses(packages = "kr.vamsur", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule SPRING_BOOT_ENTRYPOINT_STAYS_AT_ROOT = classes()
            .that().areAnnotatedWith(SpringBootApplication.class)
            .should().resideInAPackage("kr.vamsur.desktop");

    @ArchTest
    static final ArchRule DESKTOP_HAS_NO_SERVER_OR_PERSISTENCE_DEPENDENCY = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.linecorp.armeria.server..",
                    "jakarta.persistence..",
                    "org.springframework.data..",
                    "org.apache.kafka..");

    @ArchTest
    static final ArchRule JAVAFX_USES_ONLY_DESKTOP_CLIENT_AND_VIEW_MODELS = classes()
            .that().resideInAPackage("kr.vamsur.desktop.view..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "javafx..",
                    "edu.umd.cs.findbugs.annotations..",
                    "kr.vamsur.desktop.view..",
                    "kr.vamsur.desktop.client..",
                    "kr.vamsur.desktop.gameplay..");

    @ArchTest
    static final ArchRule DESKTOP_API_CLIENT_DOES_NOT_DEPEND_ON_JAVAFX_OR_SPRING = noClasses()
            .that().resideInAPackage("kr.vamsur.desktop.client..")
            .should().dependOnClassesThat().resideInAnyPackage("javafx..", "org.springframework..");

    @ArchTest
    static final ArchRule VIEW_MODELS_STAY_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("kr.vamsur.desktop.gameplay..")
            .should().dependOnClassesThat().resideOutsideOfPackages(
                    "java..", "kr.vamsur.desktop.gameplay..");
}
