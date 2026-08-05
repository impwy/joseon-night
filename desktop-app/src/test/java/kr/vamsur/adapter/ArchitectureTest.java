package kr.vamsur.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kr.vamsur", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule DOMAIN_AND_APPLICATION_DO_NOT_DEPEND_ON_ADAPTERS_OR_FRAMEWORKS = noClasses()
            .that().resideInAnyPackage("kr.vamsur.domain..", "kr.vamsur.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javafx..",
                    "org.springframework..",
                    "com.linecorp.armeria..",
                    "kr.vamsur.adapter..");

    @ArchTest
    static final ArchRule DRIVING_ADAPTERS_DO_NOT_USE_GAME_SERVICE_IMPLEMENTATION = noClasses()
            .that().resideInAnyPackage("kr.vamsur.adapter.javafx..", "kr.vamsur.adapter.webapi..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("kr.vamsur.application.gameplay.GameService");

    @ArchTest
    static final ArchRule JAVAFX_ADAPTER_DOES_NOT_DEPEND_ON_WEB_ADAPTER = noClasses()
            .that().resideInAPackage("kr.vamsur.adapter.javafx..")
            .should().dependOnClassesThat().resideInAPackage("kr.vamsur.adapter.webapi..");

    @ArchTest
    static final ArchRule WEB_ADAPTER_DOES_NOT_DEPEND_ON_JAVAFX_ADAPTER = noClasses()
            .that().resideInAPackage("kr.vamsur.adapter.webapi..")
            .should().dependOnClassesThat().resideInAPackage("kr.vamsur.adapter.javafx..");

    @ArchTest
    static final ArchRule GAME_VIEW_USES_ONLY_PROVIDED_PORTS_AND_DOMAIN_VALUES = classes()
            .that().haveSimpleName("GameView")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "javafx..",
                    "edu.umd.cs.findbugs.annotations..",
                    "kr.vamsur.adapter.javafx..",
                    "kr.vamsur.application.gameplay.provided..",
                    "kr.vamsur.domain.gameplay..");
}
